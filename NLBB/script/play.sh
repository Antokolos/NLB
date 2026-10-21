#!/bin/sh
# Консольный плеер книг NLB (com.nlbhub.nlb.player.ConsolePlayer).
# Использование: play.sh <каталог книги> [vn|standard] [--deterministic[=<n>]] [--config <config.xml>]
# Путь к книге — относительно текущего каталога, поэтому, в отличие от run.sh, каталог не меняется
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
exec "$JAVA" -classpath "$DIST/lib/*" com.nlbhub.nlb.player.ConsolePlayer --config "$DIST/cfg/config.xml" "$@"
