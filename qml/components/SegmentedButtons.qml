// SPDX-License-Identifier: GPL-3.0-or-later
pragma ComponentBehavior: Bound
// M3 single-select segmented button.
import QtQuick
import QtQuick.Layouts
import QtQuick.Templates as T
import Kvant

RowLayout {
    id: root

    property var options: []    // labels
    property int currentIndex: 0

    signal selected(int index)

    spacing: -1

    Repeater {
        model: root.options

        delegate: T.AbstractButton {
            id: segment

            required property int index
            required property string modelData
            readonly property bool active: index === root.currentIndex
            readonly property bool first: index === 0
            readonly property bool last: index === root.options.length - 1

            Layout.fillWidth: true
            Layout.preferredWidth: 1 // equal widths
            implicitHeight: 40
            hoverEnabled: true
            onClicked: root.selected(index)

            background: Rectangle {
                color: segment.active ? Theme.secondaryContainer : "transparent"
                border.width: 1
                border.color: Theme.outline
                topLeftRadius: segment.first ? height / 2 : 0
                bottomLeftRadius: segment.first ? height / 2 : 0
                topRightRadius: segment.last ? height / 2 : 0
                bottomRightRadius: segment.last ? height / 2 : 0
                Behavior on color { ColorAnimation { duration: 150 } }

                Rectangle {
                    anchors.fill: parent
                    anchors.margins: 1
                    topLeftRadius: parent.topLeftRadius
                    bottomLeftRadius: parent.bottomLeftRadius
                    topRightRadius: parent.topRightRadius
                    bottomRightRadius: parent.bottomRightRadius
                    color: Theme.onSurface
                    opacity: segment.pressed ? 0.12 : segment.hovered ? 0.08 : 0
                    Behavior on opacity { NumberAnimation { duration: 150 } }
                }
            }

            contentItem: Item {
                implicitWidth: row.implicitWidth + 24
                Row {
                    id: row
                    anchors.centerIn: parent
                    spacing: 0
                    Item {
                        width: segment.active ? 24 : 0
                        height: 18
                        anchors.verticalCenter: parent.verticalCenter
                        clip: true
                        Behavior on width { NumberAnimation { duration: 250; easing.type: Easing.OutCubic } }
                        Icon {
                            name: "check"
                            size: 18
                            color: Theme.onSecondaryContainer
                            opacity: segment.active ? 1 : 0
                            scale: segment.active ? 1 : 0.3
                            Behavior on opacity { NumberAnimation { duration: 200 } }
                            Behavior on scale { NumberAnimation { duration: 300; easing.type: Easing.OutBack } }
                        }
                    }
                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        text: segment.modelData
                        font.pixelSize: 14
                        font.weight: Font.Medium
                        color: segment.active ? Theme.onSecondaryContainer : Theme.onSurface
                    }
                }
            }
        }
    }
}
