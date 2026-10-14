// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include "trackinfo.h"

#include <QAbstractListModel>
#include <QList>
#include <QtQml/qqmlregistration.h>

class QueueModel : public QAbstractListModel
{
    Q_OBJECT
    QML_ELEMENT
    QML_UNCREATABLE("Owned by Player")
    Q_PROPERTY(int count READ rowCount NOTIFY countChanged)
    Q_PROPERTY(int currentIndex READ currentIndex NOTIFY currentIndexChanged)

public:
    enum Role { PathRole = Qt::UserRole + 1, TitleRole, ArtistRole, AlbumRole, DurationRole, CurrentRole };

    explicit QueueModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    void replace(const QStringList &files);
    void append(const QStringList &files);
    Q_INVOKABLE void remove(int row);
    Q_INVOKABLE void clear();

    int currentIndex() const { return m_current; }
    void setCurrentIndex(int row);

    QString path(int row) const;
    TrackInfo info(int row) const;
    quint64 id(int row) const;

signals:
    void countChanged();
    void currentIndexChanged();
    // Tags for a row finished loading in the background.
    void infoLoaded(int row);

private:
    struct Item
    {
        quint64 id;
        QString path;
        TrackInfo info;
    };

    void loadInfo(const QList<Item> &items);
    void applyInfo(quint64 id, const TrackInfo &info);

    QList<Item> m_items;
    int m_current = -1;
    quint64 m_nextId = 1;
};
