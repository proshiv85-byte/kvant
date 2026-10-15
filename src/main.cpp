// SPDX-License-Identifier: GPL-3.0-or-later
#include "coverprovider.h"
#include "mpris.h"
#include "player.h"
#include "theme.h"

#include <QCommandLineParser>
#include <QDir>
#include <QFileInfo>
#include <QFontDatabase>
#include <QGuiApplication>
#include <QIcon>
#include <QLoggingCategory>
#include <QQmlApplicationEngine>
#include <QQuickStyle>
#include <QQuickWindow>
#include <QStandardPaths>
#include <QUrl>

namespace {

// Prefer the Material 3 typefaces when installed; otherwise keep the system font.
void pickFont()
{
    const QStringList families = QFontDatabase::families();
    for (const char *name : {"Google Sans Flex", "Google Sans", "Roboto Flex", "Roboto"}) {
        if (families.contains(QString::fromLatin1(name))) {
            QFont f = QGuiApplication::font();
            f.setFamily(QString::fromLatin1(name));
            QGuiApplication::setFont(f);
            return;
        }
    }
}

// Leftover cover copies from previous runs (written for MPRIS clients).
void cleanCoverCache()
{
    QDir dir(QStandardPaths::writableLocation(QStandardPaths::CacheLocation));
    for (const QString &f : dir.entryList({"cover-*.jpg"}, QDir::Files))
        dir.remove(f);
}

} // namespace

int main(int argc, char *argv[])
{
    // Qt's Wayland text-input-v3 support warns on every focus change on some compositors
    // ("Trying to disable ... but 0x0 is focused"). It's harmless Qt-internal noise.
    // QT_LOGGING_RULES from the environment still takes precedence over this.
    QLoggingCategory::setFilterRules(QStringLiteral("qt.qpa.wayland.textinput.warning=false"));

    QGuiApplication app(argc, argv);
    QGuiApplication::setApplicationName("kvant");
    QGuiApplication::setApplicationDisplayName("Kvant");
    QGuiApplication::setOrganizationName("kvant");
    QGuiApplication::setApplicationVersion(KVANT_VERSION);
    QGuiApplication::setDesktopFileName(KVANT_APP_ID);
    QGuiApplication::setWindowIcon(QIcon::fromTheme(KVANT_APP_ID));

    QCommandLineParser parser;
    parser.setApplicationDescription("Kvant — a clean Material 3 audio player");
    parser.addHelpOption();
    parser.addVersionOption();
    parser.addPositionalArgument("files", "Audio files or folders to play.", "[files...]");
    parser.process(app);

    QStringList paths;
    for (const QString &arg : parser.positionalArguments()) {
        const QUrl url(arg);
        paths << (url.isLocalFile() ? url.toLocalFile() : QFileInfo(arg).absoluteFilePath());
    }

    // Single instance: hand the files to the running Kvant and quit.
    if (Mpris::forwardToRunningInstance(paths))
        return 0;

    QQuickStyle::setStyle("Basic");
    pickFont();
    cleanCoverCache();

    auto *covers = new CoverProvider; // owned by the engine
    Theme theme;
    Player player(covers);
    Theme::instance = &theme;
    Player::instance = &player;

    QQmlApplicationEngine engine;
    engine.addImageProvider("cover", covers);
    QObject::connect(&engine, &QQmlApplicationEngine::objectCreationFailed, &app,
                     [] { QCoreApplication::exit(1); }, Qt::QueuedConnection);
    engine.loadFromModule("Kvant", "Main");
    if (engine.rootObjects().isEmpty())
        return 1;

    if (MprisRoot *root = Mpris::registerService(&player, &app)) {
        auto *window = qobject_cast<QQuickWindow *>(engine.rootObjects().constFirst());
        QObject::connect(root, &MprisRoot::raiseRequested, window, [window] {
            window->show();
            window->raise();
            window->requestActivate();
        });
    }

    if (paths.isEmpty())
        player.restoreSession();
    else
        player.openPaths(paths, true);
    QObject::connect(&app, &QCoreApplication::aboutToQuit, &player, &Player::saveSession);

    return app.exec();
}
