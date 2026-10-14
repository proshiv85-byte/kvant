// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <QColor>
#include <QObject>
#include <QtQml/qqmlregistration.h>

class QQmlEngine;
class QJSEngine;

// Material 3 color scheme generated from a single seed color.
// Tonal palettes are built in OKLCH; "tone" is matched to CIE L* like HCT does.
class Theme : public QObject
{
    Q_OBJECT
    QML_ELEMENT
    QML_SINGLETON
    Q_PROPERTY(QColor seed READ seed WRITE setSeed NOTIFY seedChanged)
    Q_PROPERTY(bool dark READ dark WRITE setDark NOTIFY darkChanged)
    Q_PROPERTY(QColor primary MEMBER primary NOTIFY schemeChanged)
    Q_PROPERTY(QColor onPrimary MEMBER onPrimary NOTIFY schemeChanged)
    Q_PROPERTY(QColor primaryContainer MEMBER primaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor onPrimaryContainer MEMBER onPrimaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor secondary MEMBER secondary NOTIFY schemeChanged)
    Q_PROPERTY(QColor onSecondary MEMBER onSecondary NOTIFY schemeChanged)
    Q_PROPERTY(QColor secondaryContainer MEMBER secondaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor onSecondaryContainer MEMBER onSecondaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor tertiary MEMBER tertiary NOTIFY schemeChanged)
    Q_PROPERTY(QColor onTertiary MEMBER onTertiary NOTIFY schemeChanged)
    Q_PROPERTY(QColor tertiaryContainer MEMBER tertiaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor onTertiaryContainer MEMBER onTertiaryContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor error MEMBER error NOTIFY schemeChanged)
    Q_PROPERTY(QColor onError MEMBER onError NOTIFY schemeChanged)
    Q_PROPERTY(QColor surface MEMBER surface NOTIFY schemeChanged)
    Q_PROPERTY(QColor onSurface MEMBER onSurface NOTIFY schemeChanged)
    Q_PROPERTY(QColor onSurfaceVariant MEMBER onSurfaceVariant NOTIFY schemeChanged)
    Q_PROPERTY(QColor surfaceContainerLowest MEMBER surfaceContainerLowest NOTIFY schemeChanged)
    Q_PROPERTY(QColor surfaceContainerLow MEMBER surfaceContainerLow NOTIFY schemeChanged)
    Q_PROPERTY(QColor surfaceContainer MEMBER surfaceContainer NOTIFY schemeChanged)
    Q_PROPERTY(QColor surfaceContainerHigh MEMBER surfaceContainerHigh NOTIFY schemeChanged)
    Q_PROPERTY(QColor surfaceContainerHighest MEMBER surfaceContainerHighest NOTIFY schemeChanged)
    Q_PROPERTY(QColor outline MEMBER outline NOTIFY schemeChanged)
    Q_PROPERTY(QColor outlineVariant MEMBER outlineVariant NOTIFY schemeChanged)

public:
    explicit Theme(QObject *parent = nullptr);
    static Theme *create(QQmlEngine *, QJSEngine *);
    static inline Theme *instance = nullptr;

    QColor seed() const { return m_seed; }
    void setSeed(const QColor &c);
    bool dark() const { return m_dark; }
    void setDark(bool d);

signals:
    void seedChanged();
    void darkChanged();
    void schemeChanged();

private:
    void rebuild();

    QColor m_seed = QColor(0x67, 0x50, 0xA4);
    bool m_dark = true;

    QColor primary;
    QColor onPrimary;
    QColor primaryContainer;
    QColor onPrimaryContainer;
    QColor secondary;
    QColor onSecondary;
    QColor secondaryContainer;
    QColor onSecondaryContainer;
    QColor tertiary;
    QColor onTertiary;
    QColor tertiaryContainer;
    QColor onTertiaryContainer;
    QColor error;
    QColor onError;
    QColor surface;
    QColor onSurface;
    QColor onSurfaceVariant;
    QColor surfaceContainerLowest;
    QColor surfaceContainerLow;
    QColor surfaceContainer;
    QColor surfaceContainerHigh;
    QColor surfaceContainerHighest;
    QColor outline;
    QColor outlineVariant;
};
