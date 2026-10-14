// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <QDBusAbstractAdaptor>
#include <QDBusObjectPath>
#include <QStringList>
#include <QVariantMap>

class Player;
class MprisRoot;

namespace Mpris {

inline constexpr auto Service = "org.mpris.MediaPlayer2.kvant";

// Registers the MPRIS service. Returns nullptr if the bus or the name is unavailable.
MprisRoot *registerService(Player *player, QObject *parent);

// Hands files to an already running instance. Returns true if one answered.
bool forwardToRunningInstance(const QStringList &paths);

} // namespace Mpris

// org.mpris.MediaPlayer2
class MprisRoot : public QDBusAbstractAdaptor
{
    Q_OBJECT
    Q_CLASSINFO("D-Bus Interface", "org.mpris.MediaPlayer2")
    Q_PROPERTY(bool CanQuit READ canQuit CONSTANT)
    Q_PROPERTY(bool CanRaise READ canRaise CONSTANT)
    Q_PROPERTY(bool HasTrackList READ hasTrackList CONSTANT)
    Q_PROPERTY(QString Identity READ identity CONSTANT)
    Q_PROPERTY(QString DesktopEntry READ desktopEntry CONSTANT)
    Q_PROPERTY(QStringList SupportedUriSchemes READ uriSchemes CONSTANT)
    Q_PROPERTY(QStringList SupportedMimeTypes READ mimeTypes CONSTANT)

public:
    explicit MprisRoot(QObject *parent);

    bool canQuit() const { return true; }
    bool canRaise() const { return true; }
    bool hasTrackList() const { return false; }
    QString identity() const { return QStringLiteral("Kvant"); }
    QString desktopEntry() const { return QStringLiteral(KVANT_APP_ID); }
    QStringList uriSchemes() const { return {QStringLiteral("file")}; }
    QStringList mimeTypes() const;

public slots:
    void Raise();
    void Quit();

signals:
    void raiseRequested();
};

// org.mpris.MediaPlayer2.Player
class MprisPlayer : public QDBusAbstractAdaptor
{
    Q_OBJECT
    Q_CLASSINFO("D-Bus Interface", "org.mpris.MediaPlayer2.Player")
    Q_PROPERTY(QString PlaybackStatus READ playbackStatus)
    Q_PROPERTY(double Rate READ rate WRITE setRate)
    Q_PROPERTY(double MinimumRate READ rate)
    Q_PROPERTY(double MaximumRate READ rate)
    Q_PROPERTY(QVariantMap Metadata READ metadata)
    Q_PROPERTY(double Volume READ volume WRITE setVolume)
    Q_PROPERTY(qlonglong Position READ position)
    Q_PROPERTY(bool CanGoNext READ canGoNext)
    Q_PROPERTY(bool CanGoPrevious READ canGoPrevious)
    Q_PROPERTY(bool CanPlay READ canPlay)
    Q_PROPERTY(bool CanPause READ canPlay)
    Q_PROPERTY(bool CanSeek READ canSeek)
    Q_PROPERTY(bool CanControl READ canControl)

public:
    MprisPlayer(Player *player, QObject *parent);

    QString playbackStatus() const;
    double rate() const { return 1.0; }
    void setRate(double) {}
    QVariantMap metadata() const;
    double volume() const;
    void setVolume(double v);
    qlonglong position() const;
    bool canGoNext() const;
    bool canGoPrevious() const;
    bool canPlay() const;
    bool canSeek() const;
    bool canControl() const { return true; }

public slots:
    void Next();
    void Previous();
    void Pause();
    void PlayPause();
    void Stop();
    void Play();
    void Seek(qlonglong offsetUs);
    void SetPosition(const QDBusObjectPath &trackId, qlonglong positionUs);
    void OpenUri(const QString &uri);

signals:
    void Seeked(qlonglong positionUs);

private:
    QDBusObjectPath trackId() const;
    void notify(const QStringList &props);

    Player *m_player;
};

// Private interface used for single-instance "open with".
class KvantApp : public QDBusAbstractAdaptor
{
    Q_OBJECT
    Q_CLASSINFO("D-Bus Interface", "org.kvant.App")

public:
    KvantApp(Player *player, MprisRoot *root, QObject *parent);

public slots:
    void Open(const QStringList &paths);

private:
    Player *m_player;
    MprisRoot *m_root;
};
