// SPDX-License-Identifier: GPL-3.0-or-later
// Squiggly seek bar. The wave calms down to a flat line when paused.
import QtQuick
import Kvant

Item {
    id: root

    property real value: 0          // 0..1
    property bool playing: false
    property bool squiggle: true
    property real amplitude: 3      // px
    property real wavelength: 36    // px
    property real speed: 1          // waves per second
    readonly property bool pressed: area.pressed
    readonly property real shownValue: area.pressed ? area.dragValue : value

    signal seekRequested(real value)

    implicitHeight: 40
    opacity: enabled ? 1 : 0.38

    WavyProgress {
        id: wave
        anchors.fill: parent
        value: root.shownValue
        amplitude: root.squiggle && root.playing ? root.amplitude : 0
        wavelength: root.wavelength
        strokeWidth: 4
        thumbWidth: area.pressed ? 2 : 4
        thumbHeight: area.pressed || area.containsMouse ? 28 : 24
        activeColor: Theme.primary
        inactiveColor: Theme.secondaryContainer

        Behavior on amplitude { NumberAnimation { duration: 450; easing.type: Easing.OutCubic } }
        Behavior on thumbWidth { NumberAnimation { duration: 120 } }
        Behavior on thumbHeight { NumberAnimation { duration: 120 } }
    }

    // Advance the phase per frame, so speed changes apply instantly and smoothly
    // (no restart, no jump back to phase 0).
    FrameAnimation {
        running: wave.amplitude > 0 && root.speed > 0 && root.visible
        onTriggered: wave.phase = (wave.phase + frameTime * root.speed) % 1
    }

    MouseArea {
        id: area

        property real dragValue: 0

        function at(x) { return Math.max(0, Math.min(1, (x - 2) / (width - 4))) }

        anchors.fill: parent
        hoverEnabled: true
        preventStealing: true
        cursorShape: root.enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
        onPressed: event => dragValue = at(event.x)
        onPositionChanged: event => { if (pressed) dragValue = at(event.x) }
        onReleased: root.seekRequested(dragValue)
    }
}
