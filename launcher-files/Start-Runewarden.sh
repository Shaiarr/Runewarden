#!/bin/sh
set -eu
cd -- "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    RW_HUD_JAVA="$JAVA_HOME/bin/java"
else
    RW_HUD_JAVA=java
fi
exec "$RW_HUD_JAVA" @Runewarden-unix.args "$@"
