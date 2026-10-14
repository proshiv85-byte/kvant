// SPDX-License-Identifier: GPL-3.0-or-later
// Big play/pause button: a circle while paused, morphs to a rounded square while playing.
// Breathes up on hover and squishes on press.
import QtQuick
import QtQuick.Templates as T
import Kvant

T.AbstractButton {
    id: control

    property bool playing: false
    readonly property bool lit: hovered || visualFocus

    implicitWidth: 80
    implicitHeight: 80
    hoverEnabled: true
    opacity: enabled ? 1 : 0.38
    Accessible.name: playing ? qsTr("Pause") : qsTr("Play")

    scale: pressed ? 0.9 : lit ? 1.06 : 1
    Behavior on scale { SpringAnimation { spring: 4.5; damping: 0.25; epsilon: 0.002 } }

    background: Rectangle {
        // Hover hints at the other shape: the circle squares a bit, the square rounds a bit.
        radius: control.pressed ? height * 0.2
              : control.playing ? (control.lit ? height * 0.38 : height * 0.3)
              : (control.lit ? height * 0.4 : height / 2)
        color: Theme.primary
        Behavior on radius { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.05 } }

        Rectangle {
            anchors.fill: parent
            radius: parent.radius
            color: Theme.onPrimary
            opacity: control.pressed ? 0.14 : control.lit ? 0.08 : 0
            Behavior on opacity { NumberAnimation { duration: 150 } }
        }
    }

    contentItem: Item {
        Icon {
            anchors.centerIn: parent
            name: "play"
            size: 36
            color: Theme.onPrimary
            opacity: control.playing ? 0 : 1
            scale: control.playing ? 0.4 : 1
            rotation: control.playing ? -90 : 0
            Behavior on opacity { NumberAnimation { duration: 150 } }
            Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutBack } }
            Behavior on rotation { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
        }
        Icon {
            anchors.centerIn: parent
            name: "pause"
            size: 36
            color: Theme.onPrimary
            opacity: control.playing ? 1 : 0
            scale: control.playing ? 1 : 0.4
            rotation: control.playing ? 0 : 90
            Behavior on opacity { NumberAnimation { duration: 150 } }
            Behavior on scale { NumberAnimation { duration: 260; easing.type: Easing.OutBack } }
            Behavior on rotation { NumberAnimation { duration: 260; easing.type: Easing.OutCubic } }
        }
    }
}
