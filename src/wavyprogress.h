// SPDX-License-Identifier: GPL-3.0-or-later
#pragma once

#include <QColor>
#include <QQuickPaintedItem>
#include <QtQml/qqmlregistration.h>

// Material 3 "wavy" progress track: a sine wave up to the thumb,
// a flat track after it and a stop indicator at the end.
class WavyProgress : public QQuickPaintedItem
{
    Q_OBJECT
    QML_ELEMENT
    Q_PROPERTY(double value MEMBER m_value WRITE setValue NOTIFY valueChanged)
    Q_PROPERTY(double amplitude MEMBER m_amplitude WRITE setAmplitude NOTIFY amplitudeChanged)
    Q_PROPERTY(double wavelength MEMBER m_wavelength WRITE setWavelength NOTIFY wavelengthChanged)
    Q_PROPERTY(double phase MEMBER m_phase WRITE setPhase NOTIFY phaseChanged)
    Q_PROPERTY(double strokeWidth MEMBER m_stroke WRITE setStrokeWidth NOTIFY strokeWidthChanged)
    Q_PROPERTY(double thumbWidth MEMBER m_thumbWidth WRITE setThumbWidth NOTIFY thumbWidthChanged)
    Q_PROPERTY(double thumbHeight MEMBER m_thumbHeight WRITE setThumbHeight NOTIFY thumbHeightChanged)
    Q_PROPERTY(QColor activeColor MEMBER m_active WRITE setActiveColor NOTIFY activeColorChanged)
    Q_PROPERTY(QColor inactiveColor MEMBER m_inactive WRITE setInactiveColor NOTIFY inactiveColorChanged)

public:
    explicit WavyProgress(QQuickItem *parent = nullptr);
    void paint(QPainter *painter) override;

    void setValue(double v);
    void setAmplitude(double v);
    void setWavelength(double v);
    void setPhase(double v);
    void setStrokeWidth(double v);
    void setThumbWidth(double v);
    void setThumbHeight(double v);
    void setActiveColor(const QColor &c);
    void setInactiveColor(const QColor &c);

signals:
    void valueChanged();
    void amplitudeChanged();
    void wavelengthChanged();
    void phaseChanged();
    void strokeWidthChanged();
    void thumbWidthChanged();
    void thumbHeightChanged();
    void activeColorChanged();
    void inactiveColorChanged();

private:
    template<typename T> void set(T &field, const T &v, void (WavyProgress::*notify)());

    double m_value = 0;
    double m_amplitude = 3;
    double m_wavelength = 40;
    double m_phase = 0;
    double m_stroke = 4;
    double m_thumbWidth = 4;
    double m_thumbHeight = 24;
    QColor m_active = Qt::white;
    QColor m_inactive = Qt::gray;
};
