// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <QImage>
#include <QMutex>
#include <QQuickImageProvider>

// Serves the current track's cover as image://cover/<generation>.
class CoverProvider : public QQuickImageProvider
{
public:
    CoverProvider();

    void setImage(const QImage &img);
    QImage requestImage(const QString &id, QSize *size, const QSize &requestedSize) override;

private:
    QMutex m_mutex;
    QImage m_image;
};
