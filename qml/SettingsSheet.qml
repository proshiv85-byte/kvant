// SPDX-License-Identifier: GPL-3.0-or-later
pragma ComponentBehavior: Bound
import QtQuick
import QtQuick.Controls.Basic
import QtQuick.Layouts
import Kvant

Popup {
    id: root

    required property var prefs

    readonly property var swatches: ["#6750A4", "#0061A4", "#006A6A", "#386A20",
                                     "#7C5800", "#9C4400", "#B3261E", "#984061"]

    component SectionTitle: Text {
        Layout.topMargin: 20
        Layout.bottomMargin: 8
        color: Theme.primary
        font.pixelSize: 14
        font.weight: Font.Medium
    }

    component ValueSlider: ColumnLayout {
        id: vs
        property string label
        property string unit
        property alias from: slider.from
        property alias to: slider.to
        property int decimals: 0
        property real value
        signal moved(real value)

        Layout.fillWidth: true
        spacing: 0

        RowLayout {
            Layout.fillWidth: true
            Text { text: vs.label; color: Theme.onSurface; font.pixelSize: 15 }
            Item { Layout.fillWidth: true }
            Text {
                text: vs.value.toFixed(vs.decimals) + vs.unit
                color: Theme.onSurfaceVariant
                font.pixelSize: 13
                font.features: { "tnum": 1 }
            }
        }
        M3Slider {
            id: slider
            Layout.fillWidth: true
            trackHeight: 12
            implicitHeight: 32
            // No snapping: the handle follows the pointer continuously.
            snapMode: Slider.NoSnap
            value: vs.value
            onMoved: vs.moved(value)
        }
    }

    modal: true
    focus: true
    anchors.centerIn: Overlay.overlay
    width: Math.min(460, (parent ? parent.width : 460) - 32)
    height: Math.min(implicitHeight, (parent ? parent.height : 600) - 32)
    padding: 0

    Overlay.modal: Rectangle { color: Qt.rgba(0, 0, 0, 0.4) }

    enter: Transition {
        NumberAnimation { property: "opacity"; from: 0; to: 1; duration: 150 }
        NumberAnimation { property: "scale"; from: 0.92; to: 1; duration: 250; easing.type: Easing.OutCubic }
    }
    exit: Transition {
        NumberAnimation { property: "opacity"; from: 1; to: 0; duration: 120 }
    }

    background: Rectangle {
        color: Theme.surfaceContainerHigh
        radius: 28
    }

    contentItem: ColumnLayout {
        spacing: 0

        Text {
            Layout.margins: 24
            Layout.bottomMargin: 0
            text: qsTr("Settings")
            color: Theme.onSurface
            font.pixelSize: 24
        }

        Flickable {
            id: flick
            Layout.fillWidth: true
            Layout.fillHeight: true
            Layout.preferredHeight: column.implicitHeight
            contentHeight: column.implicitHeight
            clip: true
            boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar { policy: ScrollBar.AsNeeded }

            ColumnLayout {
                id: column
                x: 24
                width: flick.width - 48
                spacing: 0

                SectionTitle { text: qsTr("Theme") }
                SegmentedButtons {
                    Layout.fillWidth: true
                    options: [qsTr("System"), qsTr("Light"), qsTr("Dark")]
                    currentIndex: root.prefs.themeMode
                    onSelected: i => root.prefs.themeMode = i
                }

                SectionTitle { text: qsTr("Accent color") }
                Flow {
                    Layout.fillWidth: true
                    spacing: 8

                    Repeater {
                        model: root.swatches
                        delegate: Rectangle {
                            id: swatch
                            required property string modelData
                            readonly property bool selected: Qt.colorEqual(root.prefs.accent, modelData)
                            width: 40
                            height: 40
                            radius: selected ? 12 : swatchArea.containsMouse ? 16 : 20
                            color: modelData
                            scale: swatchArea.pressed ? 0.9 : swatchArea.containsMouse ? 1.12 : 1
                            Behavior on radius { SpringAnimation { spring: 4; damping: 0.3; epsilon: 0.05 } }
                            Behavior on scale { SpringAnimation { spring: 5; damping: 0.28; epsilon: 0.002 } }

                            Icon {
                                anchors.centerIn: parent
                                name: "check"
                                size: 20
                                color: "white"
                                opacity: swatch.selected ? 1 : 0
                                scale: swatch.selected ? 1 : 0.3
                                Behavior on opacity { NumberAnimation { duration: 150 } }
                                Behavior on scale { NumberAnimation { duration: 300; easing.type: Easing.OutBack } }
                            }
                            MouseArea {
                                id: swatchArea
                                anchors.fill: parent
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: root.prefs.accent = swatch.modelData
                            }
                        }
                    }
                }

                RowLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 12
                    spacing: 12

                    Rectangle {
                        Layout.preferredWidth: 40
                        Layout.preferredHeight: 40
                        radius: 12
                        color: root.prefs.accent
                    }
                    TextField {
                        id: hexField
                        Layout.fillWidth: true
                        text: root.prefs.accent.toString().toUpperCase()
                        placeholderText: "#RRGGBB"
                        color: Theme.onSurface
                        placeholderTextColor: Theme.onSurfaceVariant
                        selectionColor: Theme.primary
                        selectedTextColor: Theme.onPrimary
                        font.pixelSize: 15
                        font.features: { "tnum": 1 }
                        leftPadding: 16
                        validator: RegularExpressionValidator { regularExpression: /#?[0-9a-fA-F]{0,6}/ }
                        background: Rectangle {
                            implicitHeight: 48
                            radius: 8
                            color: "transparent"
                            border.width: hexField.activeFocus ? 2 : 1
                            border.color: hexField.activeFocus ? Theme.primary : Theme.outline
                        }
                        onEditingFinished: {
                            const hex = text.replace("#", "")
                            if (hex.length === 6)
                                root.prefs.accent = "#" + hex.toUpperCase()
                            else
                                text = root.prefs.accent.toString().toUpperCase()
                        }
                    }
                }

                SectionTitle { text: qsTr("Queue position") }
                SegmentedButtons {
                    Layout.fillWidth: true
                    options: [qsTr("Left"), qsTr("Right")]
                    currentIndex: root.prefs.queueSide === "right" ? 1 : 0
                    onSelected: i => root.prefs.queueSide = i === 1 ? "right" : "left"
                }

                SectionTitle { text: qsTr("Progress bar") }
                RowLayout {
                    Layout.fillWidth: true
                    Text {
                        Layout.fillWidth: true
                        text: qsTr("Squiggly line")
                        color: Theme.onSurface
                        font.pixelSize: 15
                    }
                    M3Switch {
                        checked: root.prefs.squiggle
                        onToggled: root.prefs.squiggle = checked
                    }
                }

                SeekBar {
                    Layout.fillWidth: true
                    Layout.topMargin: 8
                    enabled: false
                    opacity: 1
                    value: 0.6
                    playing: true
                    squiggle: root.prefs.squiggle
                    amplitude: root.prefs.amplitude
                    wavelength: root.prefs.wavelength
                    speed: root.prefs.speed
                    visible: root.visible
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    Layout.topMargin: 4
                    enabled: root.prefs.squiggle
                    opacity: enabled ? 1 : 0.38
                    spacing: 4

                    ValueSlider {
                        label: qsTr("Height")
                        unit: " px"
                        from: 1
                        to: 8
                        decimals: 1
                        value: root.prefs.amplitude
                        onMoved: v => root.prefs.amplitude = v
                    }
                    ValueSlider {
                        label: qsTr("Wavelength")
                        unit: " px"
                        from: 16
                        to: 80
                        value: root.prefs.wavelength
                        onMoved: v => root.prefs.wavelength = v
                    }
                    ValueSlider {
                        label: qsTr("Speed")
                        unit: "×"
                        from: 0
                        to: 3
                        decimals: 1
                        value: root.prefs.speed
                        onMoved: v => root.prefs.speed = v
                    }
                }

                Item { implicitHeight: 8 }
            }
        }

        TextButton {
            Layout.alignment: Qt.AlignRight
            Layout.margins: 16
            Layout.rightMargin: 24
            variant: "text"
            text: qsTr("Done")
            onClicked: root.close()
        }
    }
}
