// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQuick.Templates as T
import Kvant

T.Switch {
    id: control

    implicitWidth: 52
    implicitHeight: 32
    hoverEnabled: true
    opacity: enabled ? 1 : 0.38

    indicator: Rectangle {
        width: 52
        height: 32
        radius: 16
        color: control.checked ? Theme.primary : Theme.surfaceContainerHighest
        border.width: control.checked ? 0 : 2
        border.color: Theme.outline
        Behavior on color { ColorAnimation { duration: 150 } }

        Rectangle {
            id: thumb

            readonly property real size: control.pressed ? 28 : control.checked ? 24 : control.hovered ? 18 : 16

            width: size
            height: size
            radius: size / 2
            anchors.verticalCenter: parent.verticalCenter
            x: control.checked ? parent.width - (parent.height - size) / 2 - size : (parent.height - size) / 2
            color: control.checked ? Theme.onPrimary : Theme.outline
            Behavior on x { NumberAnimation { duration: 200; easing.type: Easing.OutBack } }
            Behavior on width { NumberAnimation { duration: 150 } }
            Behavior on height { NumberAnimation { duration: 150 } }

            // Hover / press halo
            Rectangle {
                anchors.centerIn: parent
                width: 40
                height: 40
                radius: 20
                z: -1
                color: control.checked ? Theme.primary : Theme.onSurface
                opacity: control.pressed ? 0.12 : control.hovered ? 0.08 : 0
                scale: control.hovered || control.pressed ? 1 : 0.5
                Behavior on opacity { NumberAnimation { duration: 150 } }
                Behavior on scale { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
            }

            Icon {
                anchors.centerIn: parent
                name: "check"
                size: 16
                color: Theme.onPrimaryContainer
                opacity: control.checked ? 1 : 0
                rotation: control.checked ? 0 : -90
                Behavior on opacity { NumberAnimation { duration: 150 } }
                Behavior on rotation { NumberAnimation { duration: 250; easing.type: Easing.OutBack } }
            }
        }
    }

    contentItem: Item {}
}
