#!/bin/sh
# All paths (lib, cfg etc.) are relative to the distribution directory
cd "$(dirname "$0")"

# Use the Java runtime bundled with the distribution; if it is absent, use JAVA_HOME or java from PATH
if [ -x ./runtime/bin/java ]; then
    JAVA=./runtime/bin/java
elif [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

exec "$JAVA" -classpath "./bin:./lib/*" -DconsoleEncoding=UTF-8 com.nlbhub.nlb.builder.NLBBMain "$@"
