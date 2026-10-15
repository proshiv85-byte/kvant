// SPDX-License-Identifier: GPL-3.0-or-later
#include "player.h"
#include "coverprovider.h"

#include <QAudioOutput>
#include <QDir>
#include <QFile>
#include <QJSEngine>
#include <QPointer>
#include <QRandomGenerator>
#include <QSet>
#include <QStandardPaths>
#include <QTimer>
#include <QtMultimedia/qtaudio.h>
#include <QtConcurrent/QtConcurrentRun>

#include <algorithm>

Player::Player(CoverProvider *covers, QObject *parent)
    : QObject(parent)
    , m_output(new QAudioOutput(this))
    , m_covers(covers)
{
    m_player.setAudioOutput(m_output);

    connect(&m_player, &QMediaPlayer::playbackStateChanged, this, &Player::playingChanged);
    connect(&m_player, &QMediaPlayer::positionChanged, this, &Player::positionChanged);
    connect(&m_player, &QMediaPlayer::durationChanged, this, &Player::durationChanged);
    connect(&m_player, &QMediaPlayer::seekableChanged, this, &Player::seekableChanged);
    connect(&m_player, &QMediaPlayer::mediaStatusChanged, this, &Player::onStatusChanged);
    connect(m_output, &QAudioOutput::volumeChanged, this, &Player::volumeChanged);
    connect(&m_player, &QMediaPlayer::errorOccurred, this, [this](QMediaPlayer::Error, const QString &msg) {
        emit errorOccurred(msg);
        // Skip unplayable files, but never wrap around: a queue of broken files would loop.
        if (const int row = stepFrom(1, false); row >= 0)
            QTimer::singleShot(0, this, [this, row] { playIndex(row); });
    });

    connect(&m_queue, &QueueModel::countChanged, this, [this] {
        syncOrder();
        emit navigationChanged();
    });
    connect(&m_queue, &QueueModel::currentIndexChanged, this, [this] {
        emit navigationChanged();
        emit trackChanged();
        // The current track was removed from the queue (or the queue was cleared).
        if (m_queue.currentIndex() < 0 && !m_player.source().isEmpty()) {
            m_player.stop();
            m_player.setSource({});
            m_info = {};
            emit metadataChanged();
            updateCover();
        }
    });
    connect(&m_queue, &QueueModel::infoLoaded, this, [this](int row) {
        if (row == m_queue.currentIndex()) {
            m_info = m_queue.info(row);
            emit metadataChanged();
            emit durationChanged();
        }
    });
}

Player *Player::create(QQmlEngine *, QJSEngine *)
{
    QJSEngine::setObjectOwnership(instance, QJSEngine::CppOwnership);
    return instance;
}

bool Player::playing() const
{
    return m_player.playbackState() == QMediaPlayer::PlayingState;
}

qint64 Player::position() const
{
    return m_player.position();
}

qint64 Player::duration() const
{
    return m_player.duration() > 0 ? m_player.duration() : m_info.durationMs;
}

bool Player::seekable() const
{
    return m_player.isSeekable();
}

// Volume is exposed on a perceptual (logarithmic) scale so sliders feel even.
double Player::volume() const
{
    return QtAudio::convertVolume(m_output->volume(), QtAudio::LinearVolumeScale, QtAudio::LogarithmicVolumeScale);
}

void Player::setVolume(double v)
{
    m_output->setVolume(float(QtAudio::convertVolume(qBound(0.0, v, 1.0), QtAudio::LogarithmicVolumeScale,
                                                     QtAudio::LinearVolumeScale)));
}

bool Player::canNext() const
{
    return stepFrom(1, m_repeat == RepeatAll) >= 0;
}

bool Player::canPrevious() const
{
    return hasTrack();
}

void Player::setShuffle(bool on)
{
    if (on == m_shuffle)
        return;
    m_shuffle = on;
    if (on)
        reshuffle();
    else
        m_order.clear();
    emit shuffleChanged();
    emit navigationChanged();
}

void Player::setRepeat(RepeatMode mode)
{
    if (mode == m_repeat)
        return;
    m_repeat = mode;
    emit repeatChanged();
    emit navigationChanged();
}

// Like Android's Media3: Next and Previous treat "repeat one" as off, and the
// current track keeps repeating only when it ends on its own.
int Player::stepFrom(int dir, bool wrap) const
{
    const int count = m_queue.rowCount();
    const int current = m_queue.currentIndex();
    if (current < 0 || count == 0)
        return -1;
    if (!m_shuffle) {
        const int row = current + dir;
        if (row >= 0 && row < count)
            return row;
        return wrap ? (row + count) % count : -1;
    }
    const int pos = int(m_order.indexOf(m_queue.id(current)));
    if (pos < 0)
        return -1;
    int next = pos + dir;
    if (next < 0 || next >= m_order.size()) {
        if (!wrap)
            return -1;
        next = int((next + m_order.size()) % m_order.size());
    }
    return m_queue.row(m_order.at(next));
}

// New random order with the current track first, so everything else is still ahead.
void Player::reshuffle()
{
    m_order.clear();
    for (int row = 0; row < m_queue.rowCount(); ++row)
        m_order.append(m_queue.id(row));
    std::shuffle(m_order.begin(), m_order.end(), *QRandomGenerator::global());
    if (hasTrack())
        m_order.move(m_order.indexOf(currentId()), 0);
}

// Keeps the play order in step with the queue: drops removed tracks and slots new
// ones in at random places after the current track.
void Player::syncOrder()
{
    if (!m_shuffle)
        return;
    QSet<quint64> inQueue;
    for (int row = 0; row < m_queue.rowCount(); ++row)
        inQueue.insert(m_queue.id(row));
    m_order.removeIf([&](quint64 id) { return !inQueue.contains(id); });

    const QSet<quint64> known(m_order.cbegin(), m_order.cend());
    const int after = hasTrack() ? int(m_order.indexOf(currentId())) + 1 : 0;
    auto *rng = QRandomGenerator::global();
    for (int row = 0; row < m_queue.rowCount(); ++row) {
        const quint64 id = m_queue.id(row);
        if (!known.contains(id))
            m_order.insert(after + rng->bounded(int(m_order.size()) - after + 1), id);
    }
}

void Player::open(const QList<QUrl> &urls, bool replace)
{
    QStringList paths;
    for (const QUrl &u : urls) {
        if (u.isLocalFile())
            paths << u.toLocalFile();
    }
    openPaths(paths, replace);
}

void Player::openPaths(const QStringList &paths, bool replace)
{
    const QStringList files = Tracks::expand(paths);
    if (files.isEmpty()) {
        if (!paths.isEmpty())
            emit errorOccurred(tr("No playable audio files found"));
        return;
    }
    if (replace) {
        m_queue.replace(files);
        if (m_shuffle) {
            reshuffle();
            playIndex(m_queue.row(m_order.first()));
        } else {
            playIndex(0);
        }
        return;
    }
    const int first = m_queue.rowCount();
    const bool idle = !hasTrack();
    m_queue.append(files);
    if (idle)
        playIndex(first);
}

void Player::playIndex(int row)
{
    if (row < 0 || row >= m_queue.rowCount())
        return;
    m_queue.setCurrentIndex(row);
    loadCurrent(true);
}

void Player::loadCurrent(bool autoplay)
{
    const int row = m_queue.currentIndex();
    m_info = m_queue.info(row);
    m_player.setSource(QUrl::fromLocalFile(m_queue.path(row)));
    emit metadataChanged();
    emit durationChanged();
    updateCover();
    if (autoplay)
        m_player.play();
}

void Player::play()
{
    if (!hasTrack())
        playIndex(0);
    else
        m_player.play();
}

void Player::pause()
{
    m_player.pause();
}

void Player::toggle()
{
    playing() ? pause() : play();
}

void Player::stop()
{
    m_player.stop();
}

void Player::next()
{
    if (const int row = stepFrom(1, m_repeat == RepeatAll); row >= 0)
        playIndex(row);
}

void Player::previous()
{
    const int row = m_player.position() > 3000 ? -1 : stepFrom(-1, m_repeat == RepeatAll);
    if (row < 0)
        seek(0);
    else
        playIndex(row);
}

void Player::seek(qint64 ms)
{
    if (!hasTrack())
        return;
    m_player.setPosition(qBound<qint64>(0, ms, duration()));
    emit seeked(m_player.position());
}

void Player::onStatusChanged(QMediaPlayer::MediaStatus status)
{
    if (status != QMediaPlayer::EndOfMedia)
        return;
    const int row = m_repeat == RepeatOne ? m_queue.currentIndex() : stepFrom(1, m_repeat == RepeatAll);
    if (row < 0) {
        m_player.setPosition(0); // end of the queue: stop at the start of the last track
    } else if (row == m_queue.currentIndex()) {
        m_player.setPosition(0);
        m_player.play();
    } else {
        playIndex(row);
    }
}

void Player::updateCover()
{
    const int gen = ++m_coverGeneration;
    const QString path = currentPath();
    const QString cacheDir = QStandardPaths::writableLocation(QStandardPaths::CacheLocation);
    const QString oldFile = m_coverFile;

    QPointer<Player> self(this);
    (void)QtConcurrent::run([self, gen, path, cacheDir, oldFile] {
        QImage img = path.isEmpty() ? QImage() : Tracks::cover(path);
        QString file;
        if (!img.isNull()) {
            // Keep a reasonably sized copy on disk for MPRIS clients (mpris:artUrl).
            QDir().mkpath(cacheDir);
            file = cacheDir + QStringLiteral("/cover-%1.jpg").arg(gen);
            img.scaled(512, 512, Qt::KeepAspectRatio, Qt::SmoothTransformation).save(file, "JPG", 90);
        }
        QMetaObject::invokeMethod(self, [self, gen, img, file, oldFile] {
            if (!self || gen != self->m_coverGeneration) {
                if (!file.isEmpty())
                    QFile::remove(file);
                return;
            }
            if (!oldFile.isEmpty())
                QFile::remove(oldFile);
            self->m_covers->setImage(img);
            self->m_coverFile = file;
            self->m_coverSource = img.isNull() ? QString() : QStringLiteral("image://cover/%1").arg(gen);
            emit self->coverChanged();
        }, Qt::QueuedConnection);
    });
}

QString Player::formatTime(qint64 ms)
{
    const qint64 s = qMax<qint64>(0, ms / 1000);
    if (s >= 3600)
        return QStringLiteral("%1:%2:%3").arg(s / 3600).arg(s / 60 % 60, 2, 10, QChar('0')).arg(s % 60, 2, 10, QChar('0'));
    return QStringLiteral("%1:%2").arg(s / 60).arg(s % 60, 2, 10, QChar('0'));
}
