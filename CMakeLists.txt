cmake_minimum_required(VERSION 3.21)

project(kvant VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(Qt6 6.8 REQUIRED COMPONENTS Core Gui Quick QuickControls2 Multimedia DBus Concurrent)
find_package(PkgConfig REQUIRED)
pkg_check_modules(TAGLIB REQUIRED IMPORTED_TARGET taglib>=2.0)

qt_standard_project_setup(REQUIRES 6.8)

# Name of the desktop entry, icon and AppStream file. Distro packages keep "kvant";
# the Flatpak build sets its reverse-DNS app ID.
set(KVANT_APP_ID "kvant" CACHE STRING "Desktop entry / icon / AppStream ID")

qt_add_executable(kvant
    src/main.cpp
    src/theme.h src/theme.cpp
    src/trackinfo.h src/trackinfo.cpp
    src/queuemodel.h src/queuemodel.cpp
    src/player.h src/player.cpp
    src/coverprovider.h src/coverprovider.cpp
    src/wavyprogress.h src/wavyprogress.cpp
    src/mpris.h src/mpris.cpp
)

qt_add_qml_module(kvant
    URI Kvant
    VERSION 1.0
    # Build-time QML compilation (qmlcachegen) uses Qt's private API, which would tie
    # the binary to the exact Qt release it was built against and break distro packages
    # on Qt updates. The engine still caches compiled QML on disk at runtime.
    NO_CACHEGEN
    QML_FILES
        qml/Main.qml
        qml/NowPlaying.qml
        qml/QueuePanel.qml
        qml/SettingsSheet.qml
        qml/components/Icon.qml
        qml/components/IconButton.qml
        qml/components/PlayButton.qml
        qml/components/SeekBar.qml
        qml/components/M3Slider.qml
        qml/components/M3Switch.qml
        qml/components/SegmentedButtons.qml
        qml/components/Cover.qml
        qml/components/TextButton.qml
)

target_include_directories(kvant PRIVATE src)
target_compile_definitions(kvant PRIVATE KVANT_VERSION="${PROJECT_VERSION}" KVANT_APP_ID="${KVANT_APP_ID}")

target_link_libraries(kvant PRIVATE
    Qt6::Core Qt6::Gui Qt6::Quick Qt6::QuickControls2 Qt6::Multimedia Qt6::DBus Qt6::Concurrent
    PkgConfig::TAGLIB
)

include(GNUInstallDirs)
install(TARGETS kvant RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
configure_file(data/kvant.desktop.in ${KVANT_APP_ID}.desktop @ONLY)
configure_file(data/kvant.metainfo.xml.in ${KVANT_APP_ID}.metainfo.xml @ONLY)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/${KVANT_APP_ID}.desktop DESTINATION ${CMAKE_INSTALL_DATADIR}/applications)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/${KVANT_APP_ID}.metainfo.xml DESTINATION ${CMAKE_INSTALL_DATADIR}/metainfo)
install(FILES data/kvant.svg DESTINATION ${CMAKE_INSTALL_DATADIR}/icons/hicolor/scalable/apps
        RENAME ${KVANT_APP_ID}.svg)
install(FILES LICENSE DESTINATION ${CMAKE_INSTALL_DATADIR}/licenses/kvant)

include(packaging/cpack.cmake)
