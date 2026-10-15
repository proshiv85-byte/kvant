// SPDX-License-Identifier: GPL-3.0-or-later
import QtCore
import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Dialogs
import QtQuick.Layouts
import Kvant

ApplicationWindow {
    id: window

    readonly property bool wide: width >= 760
    readonly property bool queueShown: wide ? settings.queueOpen : drawer.opened
    property bool replaceOnOpen: true

    function openFiles(replace) {
        replaceOnOpen = replace
        fileDialog.open()
    }

    function openFolder(replace) {
        replaceOnOpen = replace
        folderDialog.open()
    }

    function toggleQueue() {
        if (wide)
            settings.queueOpen = !settings.queueOpen
        else
            drawer.opened ? drawer.close() : drawer.open()
    }

    width: 1000
    height: 660
    minimumWidth: 360
    minimumHeight: 560
    visible: true
    title: Player.hasTrack ? Player.title + " — Kvant" : "Kvant"
    color: Theme.surface

    Settings {
        id: settings

        property string accent: "#6750A4"
        property int themeMode: 0 // 0 = system, 1 = light, 2 = dark
        property string queueSide: "left"
        property bool queueOpen: true
        property bool squiggle: true
        property real amplitude: 3
        property real wavelength: 36
        property real speed: 1
        property real volume: 1
        property bool shuffle: false
        property int repeat: 0 // Player.RepeatOff
    }

    Binding {
        target: Theme
        property: "seed"
        value: settings.accent
    }
    Binding {
        target: Theme
        property: "dark"
        value: settings.themeMode === 2
               || (settings.themeMode === 0 && Qt.styleHints.colorScheme === Qt.ColorScheme.Dark)
    }

    Component.onCompleted: {
        Player.volume = settings.volume
        Player.shuffle = settings.shuffle
        Player.repeat = settings.repeat
    }
    Connections {
        target: Player
        function onVolumeChanged() { settings.volume = Player.volume }
        function onShuffleChanged() { settings.shuffle = Player.shuffle }
        function onRepeatChanged() { settings.repeat = Player.repeat }
        function onErrorOccurred(message) { snackbar.show(message) }
    }

    onWideChanged: if (wide) drawer.close()

    RowLayout {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 0
        layoutDirection: settings.queueSide === "right" ? Qt.RightToLeft : Qt.LeftToRight

        // Slot that grows/shrinks while the queue slides in from its edge.
        Item {
            id: queueSlot

            readonly property real panelWidth: Math.min(360, window.width * 0.36)
            readonly property real gap: 12
            readonly property bool rightSide: settings.queueSide === "right"
            readonly property bool open: window.wide && settings.queueOpen
            property real progress: open ? 1 : 0

            Layout.fillHeight: true
            Layout.preferredWidth: progress * (panelWidth + gap)
            visible: progress > 0
            clip: true

            Behavior on progress {
                // Opening: M3 emphasized decelerate. Closing: a gentler ease-in-out.
                NumberAnimation {
                    duration: queueSlot.open ? 500 : 380
                    easing.type: Easing.BezierSpline
                    easing.bezierCurve: queueSlot.open ? [0.05, 0.7, 0.1, 1, 1, 1] : [0.65, 0, 0.35, 1, 1, 1]
                }
            }

            QueuePanel {
                width: queueSlot.panelWidth
                height: parent.height
                // Pinned to the outer edge of the slot, so it slides out from the window edge.
                x: queueSlot.rightSide ? queueSlot.gap : queueSlot.width - queueSlot.gap - width
                opacity: Math.min(1, queueSlot.progress * 1.6)
                scale: 0.94 + 0.06 * queueSlot.progress
                transformOrigin: queueSlot.rightSide ? Item.Right : Item.Left
                onAddFiles: window.openFiles(false)
                onAddFolder: window.openFolder(false)
            }
        }

        NowPlaying {
            Layout.fillWidth: true
            Layout.fillHeight: true
            prefs: settings
            queueShown: window.queueShown
            onToggleQueue: window.toggleQueue()
            onOpenFiles: window.openFiles(true)
            onOpenFolder: window.openFolder(true)
            onOpenSettings: settingsSheet.open()
        }
    }

    // Queue as a sheet on narrow windows.
    Drawer {
        id: drawer

        edge: settings.queueSide === "right" ? Qt.RightEdge : Qt.LeftEdge
        width: Math.min(380, window.width * 0.9)
        height: window.height
        interactive: !window.wide
        padding: 8

        Overlay.modal: Rectangle { color: Qt.rgba(0, 0, 0, 0.4) }
        background: Rectangle { color: Theme.surface }

        QueuePanel {
            anchors.fill: parent
            onAddFiles: window.openFiles(false)
            onAddFolder: window.openFolder(false)
        }
    }

    SettingsSheet {
        id: settingsSheet
        prefs: settings
    }

    FileDialog {
        id: fileDialog
        title: window.replaceOnOpen ? qsTr("Open files") : qsTr("Add files to queue")
        fileMode: FileDialog.OpenFiles
        currentFolder: StandardPaths.writableLocation(StandardPaths.MusicLocation)
        nameFilters: [qsTr("Audio files (*.mp3 *.flac *.ogg *.oga *.opus *.m4a *.aac *.alac *.wav *.aif *.aiff *.wma *.ape *.wv *.mka *.mpc)"),
                      qsTr("All files (*)")]
        onAccepted: Player.open(selectedFiles, window.replaceOnOpen)
    }

    FolderDialog {
        id: folderDialog
        title: window.replaceOnOpen ? qsTr("Open folder") : qsTr("Add folder to queue")
        currentFolder: StandardPaths.writableLocation(StandardPaths.MusicLocation)
        onAccepted: Player.open([selectedFolder], window.replaceOnOpen)
    }

    // Drag & drop: dropped files and folders are added to the queue.
    DropArea {
        id: dropArea
        anchors.fill: parent
        keys: ["text/uri-list"]
        onDropped: drop => {
            Player.open(drop.urls, false)
            drop.acceptProposedAction()
        }
    }

    Rectangle {
        anchors.fill: parent
        anchors.margins: 8
        radius: 28
        visible: opacity > 0
        opacity: dropArea.containsDrag ? 1 : 0
        color: Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.12)
        border.width: 2
        border.color: Theme.primary
        Behavior on opacity { NumberAnimation { duration: 150 } }

        Rectangle {
            anchors.centerIn: parent
            width: dropLabel.implicitWidth + 48
            height: 56
            radius: 28
            color: Theme.primary

            Row {
                id: dropLabel
                anchors.centerIn: parent
                spacing: 8
                Icon { name: "add"; color: Theme.onPrimary; anchors.verticalCenter: parent.verticalCenter }
                Text {
                    anchors.verticalCenter: parent.verticalCenter
                    text: qsTr("Drop to add to queue")
                    color: Theme.onPrimary
                    font.pixelSize: 16
                    font.weight: Font.Medium
                }
            }
        }
    }

    // Snackbar for errors.
    Rectangle {
        id: snackbar

        function show(message) {
            snackText.text = message
            opacity = 1
            snackTimer.restart()
        }

        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 24
        width: Math.min(snackText.implicitWidth + 32, parent.width - 32)
        height: 48
        radius: 8
        color: Theme.onSurface
        opacity: 0
        visible: opacity > 0
        Behavior on opacity { NumberAnimation { duration: 200 } }

        Text {
            id: snackText
            anchors.fill: parent
            anchors.leftMargin: 16
            anchors.rightMargin: 16
            verticalAlignment: Text.AlignVCenter
            elide: Text.ElideRight
            color: Theme.surface
            font.pixelSize: 14
        }

        Timer {
            id: snackTimer
            interval: 4000
            onTriggered: snackbar.opacity = 0
        }
    }

    Shortcut { sequence: "Space"; onActivated: Player.toggle() }
    Shortcut { sequence: "Right"; onActivated: Player.seek(Player.position + 5000) }
    Shortcut { sequence: "Left"; onActivated: Player.seek(Player.position - 5000) }
    Shortcut { sequence: "Ctrl+Right"; onActivated: Player.next() }
    Shortcut { sequence: "Ctrl+Left"; onActivated: Player.previous() }
    Shortcut { sequence: "Ctrl+S"; onActivated: Player.shuffle = !Player.shuffle }
    Shortcut { sequence: "Ctrl+R"; onActivated: Player.repeat = (Player.repeat + 1) % 3 } // off → all → one
    Shortcut { sequences: [StandardKey.Open]; onActivated: window.openFiles(true) }
    Shortcut { sequence: "Ctrl+Shift+O"; onActivated: window.openFolder(true) }
    Shortcut { sequence: "Ctrl+,"; onActivated: settingsSheet.open() }
    Shortcut { sequences: [StandardKey.Quit]; onActivated: Qt.quit() }
}
