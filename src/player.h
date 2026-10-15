// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include "queuemodel.h"

#include <QMediaPlayer>
#include <QObject>
#include <QUrl>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;

class QAudioOutput;
class CoverProvider;

class Player : public QObject
{
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON
    Q_PROPERTY(QueueModel *queue READ queue CONSTANT)
    Q_PROPERTY(bool playing READ playing NOTIFY playingChanged)
    Q_PROPERTY(bool hasTrack READ hasTrack NOTIFY trackChanged)
    Q_PROPERTY(qint64 position READ position NOTIFY positionChanged)
    Q_PROPERTY(qint64 duration READ duration NOTIFY durationChanged)
    Q_PROPERTY(bool seekable READ seekable NOTIFY seekableChanged)
    Q_PROPERTY(double volume READ volume WRITE setVolume NOTIFY volumeChanged)
    Q_PROPERTY(QString title READ title NOTIFY metadataChanged)
    Q_PROPERTY(QString artist READ artist NOTIFY metadataChanged)
    Q_PROPERTY(QString album READ album NOTIFY metadataChanged)
    Q_PROPERTY(QString coverSource READ coverSource NOTIFY coverChanged)
    Q_PROPERTY(bool canNext READ canNext NOTIFY navigationChanged)
    Q_PROPERTY(bool canPrevious READ canPrevious NOTIFY navigationChanged)
    Q_PROPERTY(bool shuffle READ shuffle WRITE setShuffle NOTIFY shuffleChanged)
    Q_PROPERTY(RepeatMode repeat READ repeat WRITE setRepeat NOTIFY repeatChanged)

public:
    enum RepeatMode { RepeatOff, RepeatAll, RepeatOne };
    Q_ENUM(RepeatMode)

    Player(CoverProvider *covers, QObject *parent = nullptr);
    static Player *create(QQmlEngine *, QJSEngine *);
    static inline Player *instance = nullptr;

    QueueModel *queue() { return &m_queue; }
    bool playing() const;
    bool hasTrack() const { return m_queue.currentIndex() >= 0; }
    qint64 position() const;
    qint64 duration() const;
    bool seekable() const;
    double volume() const;
    void setVolume(double v);
    QString title() const { return m_info.title; }
    QString artist() const { return m_info.artist; }
    QString album() const { return m_info.album; }
    QString coverSource() const { return m_coverSource; }
    QString currentPath() const { return m_queue.path(m_queue.currentIndex()); }
    QString coverFile() const { return m_coverFile; }
    quint64 currentId() const { return m_queue.id(m_queue.currentIndex()); }
    bool canNext() const;
    bool canPrevious() const;
    bool shuffle() const { return m_shuffle; }
    void setShuffle(bool on);
    RepeatMode repeat() const { return m_repeat; }
    void setRepeat(RepeatMode mode);

    // Replace the queue (and start playing) or append to it.
    Q_INVOKABLE void open(const QList<QUrl> &urls, bool replace);
    void openPaths(const QStringList &paths, bool replace);

    Q_INVOKABLE void playIndex(int row);
    Q_INVOKABLE void play();
    Q_INVOKABLE void pause();
    Q_INVOKABLE void toggle();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void next();
    Q_INVOKABLE void previous();
    Q_INVOKABLE void seek(qint64 ms);
    Q_INVOKABLE static QString formatTime(qint64 ms);

signals:
    void playingChanged();
    void trackChanged();
    void positionChanged();
    void durationChanged();
    void seekableChanged();
    void volumeChanged();
    void metadataChanged();
    void coverChanged();
    void navigationChanged();
    void shuffleChanged();
    void repeatChanged();
    // Emitted on user seeks (for MPRIS "Seeked").
    void seeked(qint64 ms);
    void errorOccurred(const QString &message);

private:
    void loadCurrent(bool autoplay);
    void updateCover();
    void onStatusChanged(QMediaPlayer::MediaStatus status);
    // Row of the track `dir` steps away in play order, or -1. `wrap` goes past the ends.
    int stepFrom(int dir, bool wrap) const;
    void reshuffle();
    void syncOrder();

    QMediaPlayer m_player;
    QAudioOutput *m_output;
    QueueModel m_queue;
    CoverProvider *m_covers;
    TrackInfo m_info;
    QString m_coverSource;
    QString m_coverFile;
    int m_coverGeneration = 0;
    bool m_shuffle = false;
    RepeatMode m_repeat = RepeatOff;
    // Play order (queue item IDs) while shuffling. The queue itself keeps its order.
    QList<quint64> m_order;
};
