// SPDX-License-Identifier: GPL-3.0-or-later
#include "coverprovider.h"

CoverProvider::CoverProvider()
    : QQuickImageProvider(QQuickImageProvider::Image)
{
}

void CoverProvider::setImage(const QImage &img)
{
    QMutexLocker lock(&m_mutex);
    m_image = img;
}

QImage CoverProvider::requestImage(const QString &, QSize *size, const QSize &requestedSize)
{
    QMutexLocker lock(&m_mutex);
    QImage img = m_image;
    lock.unlock();
    if (img.isNull())
        return img;
    if (requestedSize.isValid() && (img.width() > requestedSize.width() || img.height() > requestedSize.height()))
        img = img.scaled(requestedSize, Qt::KeepAspectRatioByExpanding, Qt::SmoothTransformation);
    if (size)
        *size = img.size();
    return img;
}
