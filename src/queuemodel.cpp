// SPDX-License-Identifier: GPL-3.0-or-later
#include "queuemodel.h"

#include <QFileInfo>
#include <algorithm>
#include <QPointer>
#include <QtConcurrent/QtConcurrentRun>

QueueModel::QueueModel(QObject *parent)
    : QAbstractListModel(parent)
{
}

int QueueModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_items.size());
}

QVariant QueueModel::data(const QModelIndex &index, int role) const
{
    if (!checkIndex(index, CheckIndexOption::IndexIsValid))
        return {};
    const Item &it = m_items.at(index.row());
    switch (role) {
    case PathRole: return it.path;
    case Qt::DisplayRole:
    case TitleRole: return it.info.title;
    case ArtistRole: return it.info.artist;
    case AlbumRole: return it.info.album;
    case DurationRole: return it.info.durationMs;
    case CurrentRole: return index.row() == m_current;
    }
    return {};
}

QHash<int, QByteArray> QueueModel::roleNames() const
{
    return {
        {PathRole, "path"},
        {TitleRole, "title"},
        {ArtistRole, "artist"},
        {AlbumRole, "album"},
        {DurationRole, "duration"},
        {CurrentRole, "current"},
    };
}

void QueueModel::replace(const QStringList &files)
{
    beginResetModel();
    m_items.clear();
    m_current = -1;
    endResetModel();
    emit currentIndexChanged();
    append(files);
}

void QueueModel::append(const QStringList &files)
{
    if (files.isEmpty())
        return;
    QList<Item> added;
    added.reserve(files.size());
    for (const QString &f : files)
        added.append({m_nextId++, f, {QFileInfo(f).completeBaseName(), {}, {}, 0}});

    const int first = int(m_items.size());
    beginInsertRows({}, first, first + int(added.size()) - 1);
    m_items.append(added);
    endInsertRows();
    emit countChanged();
    loadInfo(added);
}

void QueueModel::remove(int row)
{
    if (row < 0 || row >= m_items.size())
        return;
    beginRemoveRows({}, row, row);
    m_items.removeAt(row);
    endRemoveRows();
    emit countChanged();
    if (row < m_current) {
        --m_current;
        emit currentIndexChanged();
    } else if (row == m_current) {
        // The player decides what to do; the current track is gone.
        m_current = -1;
        emit currentIndexChanged();
    }
}

void QueueModel::clear()
{
    beginResetModel();
    m_items.clear();
    m_current = -1;
    endResetModel();
    emit countChanged();
    emit currentIndexChanged();
}

void QueueModel::setCurrentIndex(int row)
{
    if (row == m_current)
        return;
    const int old = m_current;
    m_current = row;
    if (old >= 0 && old < m_items.size())
        emit dataChanged(index(old), index(old), {CurrentRole});
    if (row >= 0 && row < m_items.size())
        emit dataChanged(index(row), index(row), {CurrentRole});
    emit currentIndexChanged();
}

QString QueueModel::path(int row) const
{
    return row >= 0 && row < m_items.size() ? m_items.at(row).path : QString();
}

TrackInfo QueueModel::info(int row) const
{
    return row >= 0 && row < m_items.size() ? m_items.at(row).info : TrackInfo();
}

quint64 QueueModel::id(int row) const
{
    return row >= 0 && row < m_items.size() ? m_items.at(row).id : 0;
}

int QueueModel::row(quint64 id) const
{
    // IDs only grow and rows keep their order, so the list is sorted by ID.
    const auto it = std::lower_bound(m_items.cbegin(), m_items.cend(), id,
                                     [](const Item &item, quint64 v) { return item.id < v; });
    return it != m_items.cend() && it->id == id ? int(it - m_items.cbegin()) : -1;
}

void QueueModel::loadInfo(const QList<Item> &items)
{
    QPointer<QueueModel> self(this);
    (void)QtConcurrent::run([self, items] {
        for (const Item &it : items) {
            if (!self)
                return;
            TrackInfo info = Tracks::read(it.path);
            QMetaObject::invokeMethod(self, [self, id = it.id, info] {
                if (self)
                    self->applyInfo(id, info);
            }, Qt::QueuedConnection);
        }
    });
}

void QueueModel::applyInfo(quint64 id, const TrackInfo &info)
{
    for (int row = 0; row < m_items.size(); ++row) {
        if (m_items[row].id == id) {
            m_items[row].info = info;
            emit dataChanged(index(row), index(row), {TitleRole, ArtistRole, AlbumRole, DurationRole});
            emit infoLoaded(row);
            return;
        }
    }
}
