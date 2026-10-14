// SPDX-License-Identifier: GPL-3.0-or-later
#include "theme.h"

#include <QJSEngine>

#include <algorithm>
#include <cmath>

namespace {

struct Rgb { double r, g, b; }; // linear sRGB

double toLinear(double c)
{
    return c <= 0.04045 ? c / 12.92 : std::pow((c + 0.055) / 1.055, 2.4);
}

double fromLinear(double c)
{
    return c <= 0.0031308 ? c * 12.92 : 1.055 * std::pow(c, 1.0 / 2.4) - 0.055;
}

Rgb oklabToLinear(double L, double a, double b)
{
    const double l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const double m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const double s_ = L - 0.0894841775 * a - 1.2914855480 * b;
    const double l = l_ * l_ * l_, m = m_ * m_ * m_, s = s_ * s_ * s_;
    return {
        +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    };
}

void linearToOklch(Rgb c, double &L, double &C, double &h)
{
    const double l = std::cbrt(0.4122214708 * c.r + 0.5363325363 * c.g + 0.0514459929 * c.b);
    const double m = std::cbrt(0.2119034982 * c.r + 0.6806995451 * c.g + 0.1073969566 * c.b);
    const double s = std::cbrt(0.0883024619 * c.r + 0.2817188376 * c.g + 0.6299787005 * c.b);
    L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
    const double a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
    const double b = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
    C = std::hypot(a, b);
    h = std::atan2(b, a);
}

bool inGamut(Rgb c)
{
    constexpr double e = 1e-4;
    return c.r >= -e && c.r <= 1 + e && c.g >= -e && c.g <= 1 + e && c.b >= -e && c.b <= 1 + e;
}

// CIE L* from relative luminance
double lstar(Rgb c)
{
    const double y = 0.2126 * c.r + 0.7152 * c.g + 0.0722 * c.b;
    return y > 216.0 / 24389.0 ? 116.0 * std::cbrt(y) - 16.0 : y * 24389.0 / 27.0;
}

// Largest in-gamut chroma <= maxC at the given OKLab lightness and hue.
Rgb clampedColor(double L, double maxC, double h)
{
    auto at = [&](double C) { return oklabToLinear(L, C * std::cos(h), C * std::sin(h)); };
    Rgb c = at(maxC);
    if (inGamut(c))
        return c;
    double lo = 0, hi = maxC;
    for (int i = 0; i < 18; ++i) {
        const double mid = (lo + hi) / 2;
        (inGamut(at(mid)) ? lo : hi) = mid;
    }
    return at(lo);
}

// Color at the given hue/chroma whose CIE L* equals `tone`.
QColor toneColor(double h, double chroma, double tone)
{
    if (tone <= 0)
        return QColor(0, 0, 0);
    if (tone >= 100)
        return QColor(255, 255, 255);
    double lo = 0, hi = 1;
    Rgb c{};
    for (int i = 0; i < 22; ++i) {
        const double mid = (lo + hi) / 2;
        c = clampedColor(mid, chroma, h);
        (lstar(c) < tone ? lo : hi) = mid;
    }
    auto ch = [](double v) { return std::clamp(fromLinear(std::clamp(v, 0.0, 1.0)), 0.0, 1.0); };
    return QColor::fromRgbF(ch(c.r), ch(c.g), ch(c.b));
}

} // namespace

Theme::Theme(QObject *parent)
    : QObject(parent)
{
    rebuild();
}

Theme *Theme::create(QQmlEngine *, QJSEngine *)
{
    QJSEngine::setObjectOwnership(instance, QJSEngine::CppOwnership);
    return instance;
}

void Theme::setSeed(const QColor &c)
{
    if (!c.isValid() || c == m_seed)
        return;
    m_seed = c;
    emit seedChanged();
    rebuild();
}

void Theme::setDark(bool d)
{
    if (d == m_dark)
        return;
    m_dark = d;
    emit darkChanged();
    rebuild();
}

void Theme::rebuild()
{
    const QColor s = m_seed.toRgb();
    double L, C, h;
    linearToOklch({toLinear(s.redF()), toLinear(s.greenF()), toLinear(s.blueF())}, L, C, h);

    // "Tonal spot" scheme: chroma per palette, roughly the HCT values of M3 / 300.
    const double cPrimary = std::clamp(C, 0.10, 0.15);
    const double greyish = C < 0.02 ? 0.0 : 1.0; // keep neutral seeds neutral
    const double hTertiary = h + M_PI / 3.0;
    constexpr double hError = 25.0 * M_PI / 180.0;

    auto P = [&](double t) { return toneColor(h, cPrimary * greyish, t); };
    auto S = [&](double t) { return toneColor(h, 0.053 * greyish, t); };
    auto T = [&](double t) { return toneColor(hTertiary, 0.08 * greyish, t); };
    auto N = [&](double t) { return toneColor(h, 0.012 * greyish, t); };
    auto NV = [&](double t) { return toneColor(h, 0.025 * greyish, t); };
    auto E = [&](double t) { return toneColor(hError, 0.2, t); };

    if (m_dark) {
        primary = P(80); onPrimary = P(20); primaryContainer = P(30); onPrimaryContainer = P(90);
        secondary = S(80); onSecondary = S(20); secondaryContainer = S(30); onSecondaryContainer = S(90);
        tertiary = T(80); onTertiary = T(20); tertiaryContainer = T(30); onTertiaryContainer = T(90);
        error = E(80); onError = E(20);
        surface = N(6); onSurface = N(90); onSurfaceVariant = NV(80);
        surfaceContainerLowest = N(4); surfaceContainerLow = N(10); surfaceContainer = N(12);
        surfaceContainerHigh = N(17); surfaceContainerHighest = N(22);
        outline = NV(60); outlineVariant = NV(30);
    } else {
        primary = P(40); onPrimary = P(100); primaryContainer = P(90); onPrimaryContainer = P(10);
        secondary = S(40); onSecondary = S(100); secondaryContainer = S(90); onSecondaryContainer = S(10);
        tertiary = T(40); onTertiary = T(100); tertiaryContainer = T(90); onTertiaryContainer = T(10);
        error = E(40); onError = E(100);
        surface = N(98); onSurface = N(10); onSurfaceVariant = NV(30);
        surfaceContainerLowest = N(100); surfaceContainerLow = N(96); surfaceContainer = N(94);
        surfaceContainerHigh = N(92); surfaceContainerHighest = N(90);
        outline = NV(50); outlineVariant = NV(80);
    }
    emit schemeChanged();
}
