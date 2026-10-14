// SPDX-License-Identifier: GPL-3.0-or-later
// M3 common button: filled, tonal or text, with an optional leading icon.
import QtQuick
import QtQuick.Templates as T
import Kvant

T.AbstractButton {
    id: control

    property string iconName
    property string variant: "tonal" // filled | tonal | text

    readonly property color foreground: variant === "filled" ? Theme.onPrimary
                                      : variant === "tonal" ? Theme.onSecondaryContainer
                                      : Theme.primary
    readonly property color container: variant === "filled" ? Theme.primary
                                     : variant === "tonal" ? Theme.secondaryContainer
                                     : "transparent"

    implicitHeight: 40
    implicitWidth: row.implicitWidth + (iconName !== "" ? 40 : 48)
    hoverEnabled: true
    opacity: enabled ? 1 : 0.38

    readonly property bool lit: hovered || visualFocus
    scale: pressed ? 0.94 : lit ? 1.04 : 1
    Behavior on scale { SpringAnimation { spring: 5; damping: 0.3; epsilon: 0.002 } }

    background: Rectangle {
        radius: control.pressed ? 12 : control.lit ? height * 0.36 : height / 2
        color: control.container
        Behavior on radius { SpringAnimation { spring: 6; damping: 0.4 } }

        Rectangle {
            anchors.fill: parent
            radius: parent.radius
            color: control.foreground
            opacity: control.pressed ? 0.12 : control.lit ? 0.08 : 0
            Behavior on opacity { NumberAnimation { duration: 150 } }
        }
    }

    contentItem: Item {
        Row {
            id: row
            anchors.centerIn: parent
            spacing: 8

            Icon {
                anchors.verticalCenter: parent.verticalCenter
                visible: control.iconName !== ""
                name: control.iconName
                size: 18
                color: control.foreground
                rotation: control.lit ? -12 : 0
                scale: control.lit ? 1.15 : 1
                Behavior on rotation { SpringAnimation { spring: 3; damping: 0.25; epsilon: 0.1 } }
                Behavior on scale { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.002 } }
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: control.text
                color: control.foreground
                font.pixelSize: 14
                font.weight: Font.Medium
            }
        }
    }
}
