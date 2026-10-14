// SPDX-License-Identifier: GPL-3.0-or-later
#include "mpris.h"
#include "player.h"

#include <QCoreApplication>
#include <QDBusConnection>
#include <QDBusConnectionInterface>
#include <QDBusMessage>
#include <QUrl>

namespace {

constexpr auto ObjectPath = "/org/mpris/MediaPlayer2";

} // namespace

namespace Mpris {

MprisRoot *registerService(Player *player, QObject *parent)
{
    QDBusConnection bus = QDBusConnection::sessionBus();
    if (!bus.isConnected())
        return nullptr;

    // All adaptors hang off one plain object exported at the MPRIS path.
    auto *holder = new QObject(parent);
    auto *root = new MprisRoot(holder);
    new MprisPlayer(player, holder);
    new KvantApp(player, root, holder);

    if (!bus.registerObject(ObjectPath, holder) || !bus.registerService(Service)) {
        delete holder;
        return nullptr;
    }
    return root;
}

bool forwardToRunningInstance(const QStringList &paths)
{
    QDBusConnection bus = QDBusConnection::sessionBus();
    if (!bus.isConnected() || !bus.interface()->isServiceRegistered(Service))
        return false;
    QDBusMessage msg = QDBusMessage::createMethodCall(Service, ObjectPath, "org.kvant.App", "Open");
    msg << paths;
    return bus.call(msg, QDBus::Block, 3000).type() == QDBusMessage::ReplyMessage;
}

} // namespace Mpris

// ---- org.mpris.MediaPlayer2 ------------------------------------------------

MprisRoot::MprisRoot(QObject *parent)
    : QDBusAbstractAdaptor(parent)
{
}

QStringList MprisRoot::mimeTypes() const
{
    return {"audio/mpeg", "audio/flac", "audio/ogg", "audio/x-vorbis+ogg", "audio/x-opus+ogg",
            "audio/mp4", "audio/aac", "audio/x-wav", "audio/x-aiff", "audio/x-ms-wma", "audio/x-matroska"};
}

void MprisRoot::Raise()
{
    emit raiseRequested();
}

void MprisRoot::Quit()
{
    QCoreApplication::quit();
}

// ---- org.mpris.MediaPlayer2.Player -----------------------------------------

MprisPlayer::MprisPlayer(Player *player, QObject *parent)
    : QDBusAbstractAdaptor(parent)
    , m_player(player)
{
    connect(player, &Player::playingChanged, this, [this] { notify({"PlaybackStatus"}); });
    connect(player, &Player::metadataChanged, this, [this] { notify({"Metadata"}); });
    connect(player, &Player::coverChanged, this, [this] { notify({"Metadata"}); });
    connect(player, &Player::durationChanged, this, [this] { notify({"Metadata"}); });
    connect(player, &Player::volumeChanged, this, [this] { notify({"Volume"}); });
    connect(player, &Player::navigationChanged, this, [this] {
        notify({"CanGoNext", "CanGoPrevious", "CanPlay", "CanPause", "CanSeek", "PlaybackStatus"});
    });
    connect(player, &Player::seekableChanged, this, [this] { notify({"CanSeek"}); });
    connect(player, &Player::seeked, this, [this](qint64 ms) { emit Seeked(ms * 1000); });
}

QString MprisPlayer::playbackStatus() const
{
    if (m_player->playing())
        return QStringLiteral("Playing");
    return m_player->hasTrack() ? QStringLiteral("Paused") : QStringLiteral("Stopped");
}

QDBusObjectPath MprisPlayer::trackId() const
{
    if (!m_player->hasTrack())
        return QDBusObjectPath("/org/mpris/MediaPlayer2/TrackList/NoTrack");
    return QDBusObjectPath(QStringLiteral("/org/kvant/track/%1").arg(m_player->currentId()));
}

QVariantMap MprisPlayer::metadata() const
{
    QVariantMap m;
    m.insert("mpris:trackid", QVariant::fromValue(trackId()));
    if (!m_player->hasTrack())
        return m;
    m.insert("mpris:length", qlonglong(m_player->duration() * 1000));
    m.insert("xesam:title", m_player->title());
    if (!m_player->artist().isEmpty())
        m.insert("xesam:artist", QStringList{m_player->artist()});
    if (!m_player->album().isEmpty())
        m.insert("xesam:album", m_player->album());
    m.insert("xesam:url", QUrl::fromLocalFile(m_player->currentPath()).toString());
    if (!m_player->coverFile().isEmpty())
        m.insert("mpris:artUrl", QUrl::fromLocalFile(m_player->coverFile()).toString());
    return m;
}

double MprisPlayer::volume() const { return m_player->volume(); }
void MprisPlayer::setVolume(double v) { m_player->setVolume(v); }
qlonglong MprisPlayer::position() const { return m_player->position() * 1000; }
bool MprisPlayer::canGoNext() const { return m_player->canNext(); }
bool MprisPlayer::canGoPrevious() const { return m_player->canPrevious(); }
bool MprisPlayer::canPlay() const { return m_player->queue()->rowCount() > 0; }
bool MprisPlayer::canSeek() const { return m_player->seekable(); }

void MprisPlayer::Next() { m_player->next(); }
void MprisPlayer::Previous() { m_player->previous(); }
void MprisPlayer::Pause() { m_player->pause(); }
void MprisPlayer::PlayPause() { m_player->toggle(); }
void MprisPlayer::Stop() { m_player->stop(); }
void MprisPlayer::Play() { m_player->play(); }

void MprisPlayer::Seek(qlonglong offsetUs)
{
    const qint64 target = m_player->position() + offsetUs / 1000;
    if (target >= m_player->duration())
        m_player->next();
    else
        m_player->seek(target);
}

void MprisPlayer::SetPosition(const QDBusObjectPath &id, qlonglong positionUs)
{
    if (id != trackId() || positionUs < 0 || positionUs / 1000 > m_player->duration())
        return;
    m_player->seek(positionUs / 1000);
}

void MprisPlayer::OpenUri(const QString &uri)
{
    m_player->open({QUrl(uri)}, true);
}

void MprisPlayer::notify(const QStringList &props)
{
    QVariantMap changed;
    for (const QString &name : props)
        changed.insert(name, property(name.toLatin1().constData()));
    QDBusMessage msg = QDBusMessage::createSignal(ObjectPath, "org.freedesktop.DBus.Properties", "PropertiesChanged");
    msg << QStringLiteral("org.mpris.MediaPlayer2.Player") << changed << QStringList();
    QDBusConnection::sessionBus().send(msg);
}

// ---- org.kvant.App ------------------------------------------------------------

KvantApp::KvantApp(Player *player, MprisRoot *root, QObject *parent)
    : QDBusAbstractAdaptor(parent)
    , m_player(player)
    , m_root(root)
{
}

void KvantApp::Open(const QStringList &paths)
{
    if (!paths.isEmpty())
        m_player->openPaths(paths, true);
    m_root->Raise();
}
