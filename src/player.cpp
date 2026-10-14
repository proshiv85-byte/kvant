// SPDX-License-Identifier: GPL-3.0-or-later
#include "player.h"
#include "coverprovider.h"

#include <QAudioOutput>
#include <QDir>
#include <QFile>
#include <QJSEngine>
#include <QPointer>
#include <QStandardPaths>
#include <QTimer>
#include <QtMultimedia/qtaudio.h>
#include <QtConcurrent/QtConcurrentRun>

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
        if (canNext())
            QTimer::singleShot(0, this, &Player::next);
    });

    connect(&m_queue, &QueueModel::countChanged, this, &Player::navigationChanged);
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
    return m_queue.currentIndex() + 1 < m_queue.rowCount();
}

bool Player::canPrevious() const
{
    return hasTrack();
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
        playIndex(0);
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
    if (canNext())
        playIndex(m_queue.currentIndex() + 1);
}

void Player::previous()
{
    const int row = m_queue.currentIndex();
    if (m_player.position() > 3000 || row <= 0)
        seek(0);
    else
        playIndex(row - 1);
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
    if (status == QMediaPlayer::EndOfMedia) {
        if (canNext())
            next();
        else
            m_player.setPosition(0);
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
