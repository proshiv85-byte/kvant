// SPDX-License-Identifier: GPL-3.0-or-later
#include "wavyprogress.h"

#include <QPainter>
#include <QPainterPath>

#include <algorithm>
#include <cmath>

WavyProgress::WavyProgress(QQuickItem *parent)
    : QQuickPaintedItem(parent)
{
    setAntialiasing(true);
}

template<typename T>
void WavyProgress::set(T &field, const T &v, void (WavyProgress::*notify)())
{
    if (field == v)
        return;
    field = v;
    emit (this->*notify)();
    update();
}

void WavyProgress::setValue(double v) { set(m_value, std::clamp(v, 0.0, 1.0), &WavyProgress::valueChanged); }
void WavyProgress::setAmplitude(double v) { set(m_amplitude, std::max(0.0, v), &WavyProgress::amplitudeChanged); }
void WavyProgress::setWavelength(double v) { set(m_wavelength, std::max(4.0, v), &WavyProgress::wavelengthChanged); }
void WavyProgress::setPhase(double v) { set(m_phase, v, &WavyProgress::phaseChanged); }
void WavyProgress::setStrokeWidth(double v) { set(m_stroke, v, &WavyProgress::strokeWidthChanged); }
void WavyProgress::setThumbWidth(double v) { set(m_thumbWidth, v, &WavyProgress::thumbWidthChanged); }
void WavyProgress::setThumbHeight(double v) { set(m_thumbHeight, v, &WavyProgress::thumbHeightChanged); }
void WavyProgress::setActiveColor(const QColor &c) { set(m_active, c, &WavyProgress::activeColorChanged); }
void WavyProgress::setInactiveColor(const QColor &c) { set(m_inactive, c, &WavyProgress::inactiveColorChanged); }

void WavyProgress::paint(QPainter *p)
{
    const double w = width();
    const double cy = height() / 2.0;
    const double half = m_stroke / 2.0;
    const double gap = 6.0;
    const double start = half;
    const double end = w - half;
    const double thumbX = start + (end - start) * m_value;

    p->setRenderHint(QPainter::Antialiasing);
    QPen pen(m_active, m_stroke, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin);

    // Active (wavy) part. The wave grows in over the first half wavelength so it
    // doesn't start mid-swing at the left edge.
    const double activeEnd = thumbX - m_thumbWidth / 2.0 - gap - half;
    if (activeEnd > start) {
        QPainterPath path;
        const double k = 2.0 * M_PI / m_wavelength;
        const double ramp = m_wavelength / 2.0;
        auto y = [&](double x) {
            const double env = std::min(1.0, (x - start) / ramp);
            return cy + m_amplitude * env * std::sin(k * x - 2.0 * M_PI * m_phase);
        };
        path.moveTo(start, y(start));
        for (double x = start + 1.0; x < activeEnd; x += 1.0)
            path.lineTo(x, y(x));
        path.lineTo(activeEnd, y(activeEnd));
        p->setPen(pen);
        p->drawPath(path);
    }

    // Inactive (flat) part.
    const double inactiveStart = thumbX + m_thumbWidth / 2.0 + gap + half;
    if (inactiveStart < end) {
        pen.setColor(m_inactive);
        p->setPen(pen);
        p->drawLine(QPointF(inactiveStart, cy), QPointF(end, cy));

        // Stop indicator.
        if (end - inactiveStart > m_stroke * 2) {
            p->setPen(Qt::NoPen);
            p->setBrush(m_active);
            const double r = m_stroke / 4.0 + 0.5;
            p->drawEllipse(QPointF(end - half, cy), r, r);
        }
    }

    // Thumb: a vertical pill.
    p->setPen(Qt::NoPen);
    p->setBrush(m_active);
    const QRectF thumb(thumbX - m_thumbWidth / 2.0, cy - m_thumbHeight / 2.0, m_thumbWidth, m_thumbHeight);
    p->drawRoundedRect(thumb, m_thumbWidth / 2.0, m_thumbWidth / 2.0);
}
