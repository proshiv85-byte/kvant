// SPDX-License-Identifier: GPL-3.0-or-later
pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Layouts
import Kvant

Item {
    id: root

    required property var prefs
    property bool queueShown: false

    signal toggleQueue()
    signal openFiles()
    signal openFolder()
    signal openSettings()

    component QueueToggle: IconButton {
        iconName: "queue"
        hoverNudge: root.prefs.queueSide === "right" ? 3 : -3
        tip: qsTr("Queue")
        variant: root.queueShown ? "tonal" : "standard"
        onClicked: root.toggleQueue()
    }

    RowLayout {
        id: topBar

        anchors.left: parent.left
        anchors.right: parent.right
        anchors.top: parent.top
        spacing: 4

        // The queue button sits on the same side as the queue.
        QueueToggle { visible: root.prefs.queueSide !== "right" }
        Item { Layout.fillWidth: true }
        IconButton { iconName: "file"; hoverRotation: -10; tip: qsTr("Open files (Ctrl+O)"); onClicked: root.openFiles() }
        IconButton { iconName: "folder"; hoverRotation: 10; tip: qsTr("Open folder (Ctrl+Shift+O)"); onClicked: root.openFolder() }
        IconButton { iconName: "settings"; hoverRotation: 90; tip: qsTr("Settings"); onClicked: root.openSettings() }
        QueueToggle { visible: root.prefs.queueSide === "right" }
    }

    ColumnLayout {
        anchors.top: topBar.bottom
        anchors.bottom: parent.bottom
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 8
        anchors.bottomMargin: 16
        width: Math.min(parent.width - 32, 520)
        spacing: 0

        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.minimumHeight: 120

            Cover {
                anchors.centerIn: parent
                width: Math.min(parent.width, parent.height)
                height: width
                radius: Math.max(16, width * 0.08)
                source: Player.coverSource
                scale: Player.playing || !Player.hasTrack ? 1 : 0.92
                Behavior on scale { SpringAnimation { spring: 3; damping: 0.3 } }
            }
        }

        Text {
            Layout.fillWidth: true
            Layout.topMargin: 24
            horizontalAlignment: Text.AlignHCenter
            elide: Text.ElideRight
            text: Player.hasTrack ? Player.title : qsTr("Nothing playing")
            color: Theme.onSurface
            font.pixelSize: 24
            font.weight: Font.Medium
        }

        Text {
            Layout.fillWidth: true
            Layout.topMargin: 4
            horizontalAlignment: Text.AlignHCenter
            elide: Text.ElideRight
            text: Player.hasTrack ? [Player.artist, Player.album].filter(s => s !== "").join("  ·  ")
                                  : qsTr("Open files or a folder, or drop music here")
            color: Theme.onSurfaceVariant
            font.pixelSize: 16
        }

        // Empty state
        RowLayout {
            Layout.alignment: Qt.AlignHCenter
            Layout.topMargin: 24
            Layout.bottomMargin: 24
            visible: !Player.hasTrack
            spacing: 8

            TextButton { iconName: "file"; text: qsTr("Open files"); variant: "filled"; onClicked: root.openFiles() }
            TextButton { iconName: "folder"; text: qsTr("Open folder"); onClicked: root.openFolder() }
        }

        // Player controls
        ColumnLayout {
            Layout.fillWidth: true
            Layout.topMargin: 16
            visible: Player.hasTrack
            spacing: 0

            SeekBar {
                id: seekBar
                Layout.fillWidth: true
                enabled: Player.seekable
                value: Player.duration > 0 ? Player.position / Player.duration : 0
                playing: Player.playing
                squiggle: root.prefs.squiggle
                amplitude: root.prefs.amplitude
                wavelength: root.prefs.wavelength
                speed: root.prefs.speed
                onSeekRequested: v => Player.seek(v * Player.duration)
            }

            RowLayout {
                Layout.fillWidth: true
                Text {
                    text: Player.formatTime(seekBar.pressed ? seekBar.shownValue * Player.duration : Player.position)
                    color: Theme.onSurfaceVariant
                    font.pixelSize: 13
                    font.features: { "tnum": 1 }
                }
                Item { Layout.fillWidth: true }
                Text {
                    text: Player.formatTime(Player.duration)
                    color: Theme.onSurfaceVariant
                    font.pixelSize: 13
                    font.features: { "tnum": 1 }
                }
            }

            RowLayout {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 12
                spacing: parent.width >= 360 ? 16 : 6

                IconButton {
                    iconName: "shuffle"
                    hoverNudge: -2
                    variant: Player.shuffle ? "tonal" : "standard"
                    tip: Player.shuffle ? qsTr("Shuffle on (Ctrl+S)") : qsTr("Shuffle off (Ctrl+S)")
                    onClicked: Player.shuffle = !Player.shuffle
                }
                IconButton {
                    iconName: "previous"
                    hoverNudge: -4
                    variant: "tonal"
                    size: 56
                    iconSize: 28
                    tip: qsTr("Previous")
                    enabled: Player.canPrevious
                    onClicked: Player.previous()
                }
                PlayButton {
                    playing: Player.playing
                    onClicked: Player.toggle()
                }
                IconButton {
                    iconName: "next"
                    hoverNudge: 4
                    variant: "tonal"
                    size: 56
                    iconSize: 28
                    tip: qsTr("Next")
                    enabled: Player.canNext
                    onClicked: Player.next()
                }
                IconButton {
                    iconName: Player.repeat === Player.RepeatOne ? "repeatOne" : "repeat"
                    hoverRotation: 180
                    variant: Player.repeat === Player.RepeatOff ? "standard" : "tonal"
                    tip: Player.repeat === Player.RepeatAll ? qsTr("Repeat all (Ctrl+R)")
                       : Player.repeat === Player.RepeatOne ? qsTr("Repeat one (Ctrl+R)")
                       : qsTr("Repeat off (Ctrl+R)")
                    onClicked: Player.repeat = (Player.repeat + 1) % 3 // off → all → one
                }
            }

            RowLayout {
                Layout.alignment: Qt.AlignHCenter
                Layout.topMargin: 16
                Layout.preferredWidth: Math.min(parent.width, 280)
                spacing: 4

                property real lastVolume: 1

                IconButton {
                    iconName: Player.volume > 0 ? "volume" : "mute"
                    hoverRotation: -12
                    tip: Player.volume > 0 ? qsTr("Mute") : qsTr("Unmute")
                    onClicked: {
                        if (Player.volume > 0) {
                            parent.lastVolume = Player.volume
                            Player.volume = 0
                        } else {
                            Player.volume = parent.lastVolume > 0 ? parent.lastVolume : 1
                        }
                    }
                }
                M3Slider {
                    Layout.fillWidth: true
                    trackHeight: 12
                    implicitHeight: 32
                    from: 0
                    to: 1
                    value: Player.volume
                    onMoved: Player.volume = value
                }
            }
        }
    }
}
