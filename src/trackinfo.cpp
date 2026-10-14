// SPDX-License-Identifier: GPL-3.0-or-later
#include "trackinfo.h"

#include <QCollator>
#include <QDir>
#include <QDirIterator>
#include <QFileInfo>
#include <QMimeDatabase>

#include <taglib/fileref.h>
#include <taglib/tag.h>
#include <taglib/tpropertymap.h>

#include <algorithm>

namespace {

QString fromTag(const TagLib::String &s)
{
    return QString::fromStdString(s.to8Bit(true)).trimmed();
}

} // namespace

namespace Tracks {

TrackInfo read(const QString &path)
{
    TrackInfo info;
    TagLib::FileRef ref(QFile::encodeName(path).constData(), true, TagLib::AudioProperties::Fast);
    if (!ref.isNull()) {
        if (TagLib::Tag *tag = ref.tag()) {
            info.title = fromTag(tag->title());
            info.artist = fromTag(tag->artist());
            info.album = fromTag(tag->album());
        }
        if (auto *props = ref.audioProperties())
            info.durationMs = props->lengthInMilliseconds();
    }
    if (info.title.isEmpty())
        info.title = QFileInfo(path).completeBaseName();
    return info;
}

QImage cover(const QString &path)
{
    TagLib::FileRef ref(QFile::encodeName(path).constData(), false);
    if (!ref.isNull()) {
        const auto pictures = ref.complexProperties("PICTURE");
        // Prefer the front cover (ID3 picture type 3), else take the first one.
        const TagLib::VariantMap *best = nullptr;
        for (const auto &p : pictures) {
            if (!best)
                best = &p;
            if (p.value("pictureType").toString() == "Front Cover") {
                best = &p;
                break;
            }
        }
        if (best) {
            const TagLib::ByteVector data = best->value("data").toByteVector();
            QImage img;
            if (img.loadFromData(reinterpret_cast<const uchar *>(data.data()), int(data.size())))
                return img;
        }
    }

    const QDir dir = QFileInfo(path).dir();
    static const QStringList names = {"cover", "folder", "front", "album", "albumart"};
    const auto entries = dir.entryInfoList({"*.jpg", "*.jpeg", "*.png", "*.webp"}, QDir::Files);
    for (const QString &name : names) {
        for (const QFileInfo &fi : entries) {
            if (fi.completeBaseName().compare(name, Qt::CaseInsensitive) == 0) {
                QImage img(fi.absoluteFilePath());
                if (!img.isNull())
                    return img;
            }
        }
    }
    return {};
}

bool isAudio(const QString &path)
{
    static const QMimeDatabase db;
    const QMimeType mime = db.mimeTypeForFile(path);
    // Plain .ogg is sometimes reported as application/ogg.
    return mime.name().startsWith(u"audio/") || mime.name() == u"application/ogg";
}

QStringList expand(const QStringList &paths)
{
    QCollator collator;
    collator.setNumericMode(true);
    collator.setCaseSensitivity(Qt::CaseInsensitive);

    QStringList out;
    for (const QString &p : paths) {
        const QFileInfo fi(p);
        if (fi.isDir()) {
            QStringList found;
            QDirIterator it(fi.absoluteFilePath(), QDir::Files, QDirIterator::Subdirectories | QDirIterator::FollowSymlinks);
            while (it.hasNext()) {
                const QString f = it.next();
                if (isAudio(f))
                    found << f;
            }
            std::sort(found.begin(), found.end(), [&](const QString &a, const QString &b) {
                return collator.compare(a, b) < 0;
            });
            out << found;
        } else if (fi.isFile() && isAudio(fi.absoluteFilePath())) {
            out << fi.absoluteFilePath();
        }
    }
    return out;
}

} // namespace Tracks
