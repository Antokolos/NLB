#!/bin/sh
# Пересобирает эталон вычислителя выражений: NLBL/src/test/resources/player-expressions/
# cparse-reference.tsv — ответы cparse (NLBNavigator) на выражения из expressions.txt.
# Нужны g++ и исходники NLBNavigator: $NLB_REFERENCE_CACHE/NLBNavigator (их скачивает
# ../make-reference.sh) или путь в $NLBNAVIGATOR.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../../.." && pwd)
CACHE=${NLB_REFERENCE_CACHE:-$HOME/.cache/nlb-player-reference}
NAV=${NLBNAVIGATOR:-$CACHE/NLBNavigator}
C=$NAV/Source/thirdparty/cparse
BUILD=$CACHE/cparse-harness
mkdir -p "$BUILD"
g++ -std=c++17 -O1 -w -I"$C" -o "$BUILD/harness" "$DIR/harness.cpp" \
    "$C/shunting-yard.cpp" "$C/packToken.cpp" "$C/functions.cpp" "$C/containers.cpp" "$C/builtin-features.cpp"
OUT=$ROOT/NLBL/src/test/resources/player-expressions
mkdir -p "$OUT"
"$BUILD/harness" < "$DIR/expressions.txt" > "$BUILD/expected.txt"
paste -d '\t' "$DIR/expressions.txt" "$BUILD/expected.txt" > "$OUT/cparse-reference.tsv"
echo "Готово: $OUT/cparse-reference.tsv"
