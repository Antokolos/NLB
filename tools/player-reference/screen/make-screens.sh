#!/bin/sh
# Эталон модели экрана: для каждого эталонного прохождения (NLBL/src/test/resources/
# player-reference/<книга>/walk-N.input) — снимки PlayerEngine::screen() NLBNavigator после
# каждого шага, NLBL/src/test/resources/player-screens/<книга>/walk-N.screens.jsonl.gz.
#
# Нужны собранные make-reference.sh исходники и сборка NLBNavigator ($NLB_REFERENCE_CACHE:
# NLBNavigator, navigator-build) и книги ($NLB_TEST_BOOKS); g++, gzip.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../../.." && pwd)
CACHE=${NLB_REFERENCE_CACHE:-$HOME/.cache/nlb-player-reference}
BOOKS=${NLB_TEST_BOOKS:-$CACHE/books}
NAV=$CACHE/NLBNavigator
BUILD=$CACHE/navigator-build
REF=$ROOT/NLBL/src/test/resources/player-reference
OUT=$ROOT/NLBL/src/test/resources/player-screens
S=$NAV/Source
C=$S/thirdparty/cparse
cmake --build "$BUILD" --target NLBReader > /dev/null
g++ -std=c++17 -O2 -w -I"$S/NLBCore/Public" -I"$C" -I"$S/thirdparty" -I"$NAV" -I"$S/NLBReader/Public" \
    -I"$S/NLBPresentation/Public" -o "$CACHE/screen-harness" "$DIR/harness.cpp" \
    "$C/shunting-yard.cpp" "$C/packToken.cpp" "$C/functions.cpp" "$C/containers.cpp" "$BUILD/libNLBReader.a"
rm -rf "$OUT"
mkdir -p "$OUT"
grep -v '^#' "$DIR/../books.txt" | while read -r name url commit mode walks steps; do
    [ -z "$name" ] && continue
    mkdir -p "$OUT/$name"
    walk=1
    while [ "$walk" -le "$walks" ]; do
        "$CACHE/screen-harness" "$BOOKS/$name" "$mode" "$NAV/nlb.json" "$OUT/$name/walk-$walk.screens.jsonl" \
            < "$REF/$name/walk-$walk.input" > /dev/null 2>&1
        gzip -9n "$OUT/$name/walk-$walk.screens.jsonl"
        walk=$((walk + 1))
    done
    echo "$name: $walks"
done
echo "Готово: $OUT"
