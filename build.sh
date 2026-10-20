#!/bin/sh
# Stop on the first error, otherwise dist can be assembled from stale or partial build results
set -e
# Clean first: each build increments the project version, so jars from previous builds
# (with other version numbers) would otherwise remain in target/ and be copied to dist/lib
mvn clean generate-sources package
rm -rf dist
mkdir dist
cp NLBB/script/* dist/
mkdir dist/lib
cp NLBB/target/NLBB*.jar dist/lib
cp NLBB/target/lib/* dist/lib
cp NLBB/target/modules/* dist/lib
cp -r NLBW/fonts dist/fonts
cp -r NLBW/xsl dist/xsl
cp -r NLBW/cfg dist/cfg
cp -r NLBW/res dist/res
cp -r NLBW/template dist/template
mvn clean
