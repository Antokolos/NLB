#!/bin/sh
# Stop on the first error, otherwise dist can be assembled from stale or partial build results
set -e
# Clean first, otherwise jars from previous builds (e.g. with other version numbers)
# would remain in target/ and be copied to dist/lib
mvn clean generate-sources package
rm -rf dist
mkdir dist
cp NLBB/script/* dist/
mkdir dist/lib
cp NLBB/target/NLBB*.jar dist/lib
cp NLBB/target/lib/* dist/lib
cp NLBB/target/modules/* dist/lib
cp -r NLBW/fonts dist/fonts
cp -r NLBW/cfg dist/cfg
cp -r NLBW/res dist/res
cp -r NLBW/template dist/template
# Bundle a minimal Java runtime, so that Java does not need to be installed on the user's computer.
# The runtime is built for the current OS, so the distribution should be built on the target OS.
# java.se contains all standard Java modules; jdk.dynalink is needed by Nashorn, jdk.crypto.ec by HTTPS (Git),
# jdk.localedata and jdk.charsets by non-English locales and encodings
RUNTIME_MODULES=java.se,jdk.dynalink,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.charsets,jdk.zipfs
JLINK=jlink
if [ -n "$JAVA_HOME" ]; then
    JLINK="$JAVA_HOME/bin/jlink"
fi
"$JLINK" --add-modules "$RUNTIME_MODULES" --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output dist/runtime
mvn clean
