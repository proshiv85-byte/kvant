// SPDX-License-Identifier: GPL-3.0-or-later
// M3 (expressive) slider: thick track, vertical bar handle with gaps, stop indicator.
import QtQuick
import QtQuick.Templates as T
import Kvant

T.Slider {
    id: control

    property real trackHeight: 16
    readonly property real thumbWidth: pressed ? 2 : 4
    readonly property real gap: 6

    implicitWidth: 200
    implicitHeight: 40
    hoverEnabled: true
    opacity: enabled ? 1 : 0.38

    background: Item {
        id: track

        // Center of the handle in track coordinates.
        readonly property real thumbX: control.visualPosition * (width - control.thumbWidth) + control.thumbWidth / 2

        x: control.leftPadding
        y: control.topPadding
        width: control.availableWidth
        height: control.availableHeight

        Rectangle {
            width: Math.max(0, track.thumbX - control.thumbWidth / 2 - control.gap)
            height: control.trackHeight
            anchors.verticalCenter: parent.verticalCenter
            visible: width > 1
            radius: Math.min(height / 2, width / 2)
            topRightRadius: 2
            bottomRightRadius: 2
            color: Theme.primary
        }

        Rectangle {
            x: track.thumbX + control.thumbWidth / 2 + control.gap
            width: Math.max(0, track.width - x)
            height: control.trackHeight
            anchors.verticalCenter: parent.verticalCenter
            visible: width > 1
            radius: Math.min(height / 2, width / 2)
            topLeftRadius: 2
            bottomLeftRadius: 2
            color: Theme.secondaryContainer

            Rectangle {
                width: 4
                height: 4
                radius: 2
                anchors.verticalCenter: parent.verticalCenter
                anchors.right: parent.right
                anchors.rightMargin: (control.trackHeight - 4) / 2
                visible: parent.width > control.trackHeight
                color: Theme.primary
            }
        }
    }

    handle: Rectangle {
        x: control.leftPadding + control.visualPosition * (control.availableWidth - width)
        y: control.topPadding + (control.availableHeight - height) / 2
        implicitWidth: control.thumbWidth
        implicitHeight: control.availableHeight
        width: control.thumbWidth
        radius: width / 2
        color: Theme.primary
        Behavior on width { NumberAnimation { duration: 120 } }
    }
}
