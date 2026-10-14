// SPDX-License-Identifier: GPL-3.0-or-later
// M3 icon button: standard, tonal or filled.
// Swells a little on hover, squishes and squares up while pressed, and the icon
// can react on hover (spin or nudge).
import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Templates as T
import Kvant

T.AbstractButton {
    id: control

    property string iconName
    property string variant: "standard" // standard | tonal | filled
    property real size: 40
    property real iconSize: 24
    property string tip
    property real hoverRotation: 0      // degrees the icon turns on hover
    property real hoverNudge: 0         // px the icon shifts sideways on hover

    readonly property bool lit: hovered || visualFocus
    readonly property color foreground: variant === "filled" ? Theme.onPrimary
                                      : variant === "tonal" ? Theme.onSecondaryContainer
                                      : lit ? Theme.onSurface : Theme.onSurfaceVariant
    readonly property color container: variant === "filled" ? Theme.primary
                                     : variant === "tonal" ? Theme.secondaryContainer
                                     : "transparent"

    implicitWidth: size
    implicitHeight: size
    hoverEnabled: true
    opacity: enabled ? 1 : 0.38
    Accessible.name: tip

    scale: pressed ? 0.88 : lit && enabled ? 1.08 : 1
    Behavior on scale { SpringAnimation { spring: 5; damping: 0.28; epsilon: 0.002 } }

    background: Rectangle {
        radius: control.pressed ? height * 0.28 : control.lit ? height * 0.4 : height / 2
        color: control.container
        Behavior on radius { SpringAnimation { spring: 5; damping: 0.35; epsilon: 0.05 } }
        Behavior on color { ColorAnimation { duration: 200 } }

        // State layer
        Rectangle {
            anchors.fill: parent
            radius: parent.radius
            color: control.foreground
            opacity: control.pressed ? 0.14 : control.lit ? 0.08 : 0
            Behavior on opacity { NumberAnimation { duration: 150 } }
        }
    }

    contentItem: Item {
        Icon {
            id: icon
            anchors.centerIn: parent
            anchors.horizontalCenterOffset: control.lit ? control.hoverNudge : 0
            name: control.iconName
            size: control.iconSize
            color: control.foreground
            rotation: control.lit ? control.hoverRotation : 0
            Behavior on anchors.horizontalCenterOffset { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.05 } }
            Behavior on rotation { SpringAnimation { spring: 2.5; damping: 0.25; epsilon: 0.1 } }
            Behavior on color { ColorAnimation { duration: 150 } }
        }
    }

    // A quick pop of the icon on every click.
    onClicked: popAnim.restart()
    SequentialAnimation {
        id: popAnim
        NumberAnimation { target: icon; property: "scale"; to: 0.75; duration: 70; easing.type: Easing.OutQuad }
        NumberAnimation { target: icon; property: "scale"; to: 1; duration: 320; easing.type: Easing.OutBack; easing.overshoot: 3 }
    }

    ToolTip {
        visible: control.hovered && control.tip !== ""
        delay: 700
        text: control.tip
        enter: Transition {
            NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 150 }
            NumberAnimation { property: "scale"; from: 0.8; to: 1; duration: 200; easing.type: Easing.OutBack }
        }
        exit: Transition { NumberAnimation { property: "opacity"; to: 0; duration: 100 } }
        background: Rectangle { color: Theme.onSurface; radius: 4 }
        contentItem: Text {
            text: control.tip
            color: Theme.surface
            font.pixelSize: 12
        }
    }
}
