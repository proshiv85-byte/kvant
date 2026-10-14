// SPDX-License-Identifier: GPL-3.0-or-later
// Album art with rounded corners, or a placeholder when there's none.
import QtQuick
import QtQuick.Effects
import Kvant

Item {
    id: root

    property string source
    property real radius: 28

    Rectangle {
        anchors.fill: parent
        radius: root.radius
        color: Theme.secondaryContainer
        visible: image.status !== Image.Ready

        Icon {
            anchors.centerIn: parent
            name: "music"
            size: Math.min(root.width, root.height) * 0.36
            color: Theme.onSecondaryContainer
        }
    }

    Image {
        id: image
        anchors.fill: parent
        source: root.source
        sourceSize: Qt.size(1024, 1024)
        fillMode: Image.PreserveAspectCrop
        asynchronous: true
        cache: false
        mipmap: true
        visible: false
    }

    Rectangle {
        id: mask
        anchors.fill: parent
        radius: root.radius
        visible: false
        layer.enabled: true
    }

    MultiEffect {
        anchors.fill: image
        source: image
        visible: image.status === Image.Ready
        maskEnabled: true
        maskSource: mask
        maskThresholdMin: 0.5
        maskSpreadAtMin: 1.0
    }
}
