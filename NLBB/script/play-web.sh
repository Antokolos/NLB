#!/bin/sh
# Веб-плеер книг NLB (com.nlbhub.nlb.web.player.PlayerServer): играть книги в браузере.
# Использование: play-web.sh <каталог с книгами> [--port 8111] [--host localhost] [--config <config.xml>]
# В каталоге — каталоги книг и пакеты книг <книга>.nlbpack; плеер — http://localhost:8111/play/
# (--host 0.0.0.0 — открыть плеер для других компьютеров сети)
DIST="$(cd "$(dirname "$0")" && pwd)"

# Java из дистрибутива; если её нет — из JAVA_HOME или из PATH
if [ -x "$DIST/runtime/bin/java" ]; then
    JAVA="$DIST/runtime/bin/java"
elif [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

# Настройки экспорта (тексты по умолчанию, needs-action-count) — из cfg дистрибутива;
# --config в аргументах указывается позже и поэтому важнее
exec "$JAVA" -classpath "$DIST/lib/*" com.nlbhub.nlb.web.player.PlayerServer --config "$DIST/cfg/config.xml" "$@"
