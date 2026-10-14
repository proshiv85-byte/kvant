// SPDX-License-Identifier: GPL-3.0-or-later
pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import QtQuick.Templates as T
import Kvant

Rectangle {
    id: root

    signal addFiles()
    signal addFolder()

    color: Theme.surfaceContainerLow
    radius: 24

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 8
        spacing: 4

        RowLayout {
            Layout.fillWidth: true
            Layout.leftMargin: 12
            Layout.topMargin: 4
            spacing: 0

            ColumnLayout {
                spacing: 0
                Text {
                    text: qsTr("Queue")
                    color: Theme.onSurface
                    font.pixelSize: 22
                }
                Text {
                    text: Player.queue.count === 1 ? qsTr("1 track") : qsTr("%1 tracks").arg(Player.queue.count)
                    color: Theme.onSurfaceVariant
                    font.pixelSize: 13
                }
            }
            Item { Layout.fillWidth: true }
            IconButton { iconName: "add"; hoverRotation: 90; tip: qsTr("Add files"); onClicked: root.addFiles() }
            IconButton { iconName: "folder"; hoverRotation: 10; tip: qsTr("Add folder"); onClicked: root.addFolder() }
            IconButton {
                iconName: "clear"
                hoverNudge: 3
                tip: qsTr("Clear queue")
                enabled: Player.queue.count > 0
                onClicked: Player.queue.clear()
            }
        }

        ListView {
            id: list

            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.topMargin: 4
            clip: true
            spacing: 2
            model: Player.queue
            boundsBehavior: Flickable.StopAtBounds

            // Staggered entrance when a new queue is loaded.
            populate: Transition {
                id: populateTr
                SequentialAnimation {
                    PropertyAction { property: "opacity"; value: 0 }
                    PauseAnimation { duration: Math.min(populateTr.ViewTransition.index, 12) * 35 }
                    ParallelAnimation {
                        NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 280 }
                        NumberAnimation { property: "x"; from: -24; to: 0; duration: 420; easing.type: Easing.OutCubic }
                    }
                }
            }
            add: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 250 }
                    NumberAnimation { property: "scale"; from: 0.85; to: 1; duration: 380; easing.type: Easing.OutBack }
                }
            }
            remove: Transition {
                ParallelAnimation {
                    NumberAnimation { property: "opacity"; to: 0; duration: 200 }
                    NumberAnimation { property: "x"; to: 48; duration: 220; easing.type: Easing.InCubic }
                }
            }
            displaced: Transition {
                NumberAnimation { property: "y"; duration: 320; easing.type: Easing.OutCubic }
                // Make sure items interrupted mid-entrance end up fully visible.
                NumberAnimation { property: "opacity"; to: 1; duration: 200 }
                NumberAnimation { property: "scale"; to: 1; duration: 200 }
                NumberAnimation { property: "x"; to: 0; duration: 200 }
            }

            ScrollBar.vertical: ScrollBar {
                id: scrollBar
                policy: ScrollBar.AsNeeded
                contentItem: Rectangle {
                    implicitWidth: 4
                    radius: 2
                    color: Theme.outlineVariant
                    opacity: scrollBar.active ? 1 : 0
                    Behavior on opacity { NumberAnimation { duration: 200 } }
                }
            }

            delegate: T.AbstractButton {
                id: row

                required property int index
                required property string title
                required property string artist
                required property var duration
                required property bool current

                width: ListView.view.width
                height: 60
                hoverEnabled: true
                onClicked: Player.playIndex(index)

                background: Item {
                    // Highlight for the current track; grows in when a track starts.
                    Rectangle {
                        anchors.fill: parent
                        radius: row.current ? 16 : 30
                        color: Theme.secondaryContainer
                        opacity: row.current ? 1 : 0
                        scale: row.current ? 1 : 0.92
                        Behavior on opacity { NumberAnimation { duration: 250 } }
                        Behavior on scale { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.002 } }
                        Behavior on radius { NumberAnimation { duration: 300; easing.type: Easing.OutCubic } }
                    }
                    // State layer
                    Rectangle {
                        anchors.fill: parent
                        radius: 16
                        color: row.current ? Theme.onSecondaryContainer : Theme.onSurface
                        opacity: row.pressed ? 0.12 : row.hovered ? 0.08 : 0
                        Behavior on opacity { NumberAnimation { duration: 150 } }
                    }
                }

                contentItem: RowLayout {
                    spacing: 12
                    // Lean in a little on hover.
                    transform: Translate {
                        x: row.hovered ? 4 : 0
                        Behavior on x { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.05 } }
                    }

                    Item {
                        Layout.leftMargin: 8
                        Layout.preferredWidth: 28
                        Layout.fillHeight: true

                        Text {
                            anchors.centerIn: parent
                            visible: !row.current
                            text: row.index + 1
                            color: Theme.onSurfaceVariant
                            font.pixelSize: 13
                            font.features: { "tnum": 1 }
                        }

                        // Little equalizer for the current track.
                        Row {
                            anchors.centerIn: parent
                            height: 16
                            visible: row.current
                            spacing: 3
                            Repeater {
                                model: 3
                                Rectangle {
                                    id: bar
                                    required property int index
                                    width: 4
                                    radius: 2
                                    y: 16 - height
                                    color: Theme.onSecondaryContainer
                                    height: 6
                                    SequentialAnimation on height {
                                        running: row.current && Player.playing
                                        loops: Animation.Infinite
                                        onRunningChanged: if (!running) bar.height = 6
                                        NumberAnimation { to: 16 - bar.index * 3; duration: 260 + bar.index * 90; easing.type: Easing.InOutSine }
                                        NumberAnimation { to: 5 + bar.index; duration: 300 + bar.index * 70; easing.type: Easing.InOutSine }
                                    }
                                }
                            }
                        }
                    }

                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 1
                        Text {
                            Layout.fillWidth: true
                            text: row.title
                            elide: Text.ElideRight
                            color: row.current ? Theme.onSecondaryContainer : Theme.onSurface
                            font.pixelSize: 15
                            font.weight: row.current ? Font.DemiBold : Font.Normal
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: row.artist !== ""
                            text: row.artist
                            elide: Text.ElideRight
                            color: row.current ? Theme.onSecondaryContainer : Theme.onSurfaceVariant
                            opacity: row.current ? 0.8 : 1
                            font.pixelSize: 13
                        }
                    }

                    Item {
                        Layout.preferredWidth: 40
                        Layout.rightMargin: 4
                        Layout.fillHeight: true

                        Text {
                            anchors.centerIn: parent
                            visible: row.duration > 0
                            opacity: row.hovered ? 0 : 1
                            Behavior on opacity { NumberAnimation { duration: 150 } }
                            text: Player.formatTime(row.duration)
                            color: row.current ? Theme.onSecondaryContainer : Theme.onSurfaceVariant
                            font.pixelSize: 13
                            font.features: { "tnum": 1 }
                        }
                        IconButton {
                            anchors.centerIn: parent
                            opacity: row.hovered ? 1 : 0
                            visible: opacity > 0
                            Behavior on opacity { NumberAnimation { duration: 150 } }
                            iconName: "close"
                            hoverRotation: 90
                            size: 32
                            iconSize: 18
                            tip: qsTr("Remove from queue")
                            onClicked: Player.queue.remove(row.index)
                        }
                    }
                }
            }

            Text {
                anchors.centerIn: parent
                visible: list.count === 0
                text: qsTr("Queue is empty")
                color: Theme.onSurfaceVariant
                font.pixelSize: 14
            }
        }
    }

    Connections {
        target: Player.queue
        function onCurrentIndexChanged() {
            if (Player.queue.currentIndex >= 0)
                list.positionViewAtIndex(Player.queue.currentIndex, ListView.Contain)
        }
    }
}
