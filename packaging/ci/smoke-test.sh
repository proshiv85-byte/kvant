#!/bin/sh
# Starts Kvant (on an X server, e.g. under xvfb-run) and passes if it is still
# running after a few seconds, i.e. the UI loaded without missing QML modules.
# Usage: smoke-test.sh [command...]   (default: kvant)
[ $# -eq 0 ] && set -- kvant
QT_FORCE_STDERR_LOGGING=1 timeout 6 "$@"
status=$?
if [ "$status" -eq 124 ]; then
    echo "Smoke test passed: Kvant started and kept running."
else
    echo "Smoke test FAILED: Kvant exited with status $status."
    exit 1
fi
