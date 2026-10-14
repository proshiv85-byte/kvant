// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <QImage>
#include <QString>
#include <QStringList>

struct TrackInfo
{
    QString title;
    QString artist;
    QString album;
    qint64 durationMs = 0;
};

namespace Tracks {

// Reads tags; falls back to the file name for the title.
TrackInfo read(const QString &path);

// Embedded cover art, or cover/folder/front image next to the file. Null if none.
QImage cover(const QString &path);

// True if the file looks like audio we can play.
bool isAudio(const QString &path);

// Expands files and folders (recursively) into a naturally sorted list of audio files.
QStringList expand(const QStringList &paths);

} // namespace Tracks
