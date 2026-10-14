# .deb and .rpm packages via CPack (`cpack -G DEB` / `cpack -G RPM` in the build dir).
# Built in CI on Debian 13 and Fedora; see .github/workflows/packages.yml.

set(CPACK_PACKAGE_NAME "kvant")
set(CPACK_PACKAGE_VENDOR "rDev")
set(CPACK_PACKAGE_CONTACT "rDev <proshiv85@gmail.com>")
set(CPACK_PACKAGE_DESCRIPTION_SUMMARY "A clean Material 3 audio player")
set(CPACK_PACKAGE_HOMEPAGE_URL "https://github.com/proshiv85-byte/kvant")
set(CPACK_RESOURCE_FILE_LICENSE "${CMAKE_SOURCE_DIR}/LICENSE")
set(CPACK_PACKAGING_INSTALL_PREFIX "/usr")
set(CPACK_STRIP_FILES ON)

# Debian / Ubuntu. Shared libraries are detected by dpkg-shlibdeps; QML modules and
# Qt plugins are loaded at runtime, so they are listed by hand.
set(CPACK_DEBIAN_FILE_NAME DEB-DEFAULT)
set(CPACK_DEBIAN_PACKAGE_SECTION "sound")
set(CPACK_DEBIAN_PACKAGE_SHLIBDEPS ON)
set(CPACK_DEBIAN_PACKAGE_DEPENDS
    "qml6-module-qtquick, qml6-module-qtquick-controls, qml6-module-qtquick-templates, \
qml6-module-qtquick-layouts, qml6-module-qtquick-dialogs, qml6-module-qtquick-shapes, \
qml6-module-qtquick-effects, qml6-module-qtquick-window, qml6-module-qtcore, \
qml6-module-qtqml-workerscript, qml6-module-qt-labs-folderlistmodel, \
qt6-qpa-plugins, qt6-wayland, libqt6multimedia6")

# Fedora. Shared libraries are detected by rpm's automatic dependencies.
set(CPACK_RPM_FILE_NAME RPM-DEFAULT)
set(CPACK_RPM_PACKAGE_LICENSE "GPL-3.0-or-later")
set(CPACK_RPM_PACKAGE_GROUP "Applications/Multimedia")
set(CPACK_RPM_PACKAGE_AUTOREQ ON)
set(CPACK_RPM_PACKAGE_REQUIRES "qt6-qtdeclarative, qt6-qtmultimedia, qt6-qtwayland")
# Don't claim ownership of directories that belong to the filesystem package.
list(APPEND CPACK_RPM_EXCLUDE_FROM_AUTO_FILELIST_ADDITION
    /usr/share/applications
    /usr/share/icons
    /usr/share/icons/hicolor
    /usr/share/icons/hicolor/scalable
    /usr/share/icons/hicolor/scalable/apps
    /usr/share/metainfo
    /usr/share/licenses)

include(CPack)
