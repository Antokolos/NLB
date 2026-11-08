#!/bin/sh
# Повышает версию проекта во всех pom.xml (mvn versions:set):
#   ./bump-version.sh                 1.0.6-SNAPSHOT -> 1.0.7-SNAPSHOT (последний номер)
#   ./bump-version.sh minor           1.0.6-SNAPSHOT -> 1.1.0-SNAPSHOT
#   ./bump-version.sh major           1.0.6-SNAPSHOT -> 2.0.0-SNAPSHOT
#   ./bump-version.sh 1.2.3-SNAPSHOT  — задать версию явно
# Версия попадает и в nlbversion.properties (NLBL) — по ней книга знает, каким NLBB её сохраняли
set -e
cd "$(dirname "$0")"

current=$(sed -n 's:^    <version>\(.*\)</version>.*:\1:p' pom.xml | head -n 1)
base=${current%%-*}
suffix=${current#"$base"}
old_ifs=$IFS
IFS=.
set -- $base "${1:-patch}"
IFS=$old_ifs
if [ $# -ne 4 ]; then
    echo "Cannot parse the project version '$current' in pom.xml (expected X.Y.Z or X.Y.Z-SNAPSHOT)" >&2
    exit 1
fi
major=$1
minor=$2
patch=$3
mode=$4

case "$mode" in
    patch)
        patch=$((patch + 1))
        new="$major.$minor.$patch$suffix"
        ;;
    minor)
        minor=$((minor + 1))
        new="$major.$minor.0$suffix"
        ;;
    major)
        major=$((major + 1))
        new="$major.0.0$suffix"
        ;;
    [0-9]*)
        new=$mode
        ;;
    *)
        echo "Usage: $0 [patch|minor|major|X.Y.Z[-SNAPSHOT]]" >&2
        exit 1
        ;;
esac

echo "Version: $current -> $new"
mvn versions:set -DnewVersion="$new"
