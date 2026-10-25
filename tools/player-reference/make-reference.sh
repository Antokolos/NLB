#!/bin/sh
# Строит эталонные прохождения тестовых книг консольным плеером NLBNavigator (nlb_main).
#
# 1. Скачивает NLBNavigator на зафиксированном коммите и собирает nlb_main (без SDL).
# 2. Скачивает книги из books.txt на зафиксированных коммитах (с подмодулями).
# 3. Для каждой книги делает несколько случайных прохождений (walk.py) и кладёт пары
#    «ввод + журнал» в NLBL/src/test/resources/player-reference/<книга>/. Если есть
#    scripts/<книга>/walk-N.script, прохождение N начинается по этому сценарию.
#
# Нужны: git, cmake, ninja, C++-компилятор, python3.
# Каталог для скачанного: $NLB_REFERENCE_CACHE (по умолчанию ~/.cache/nlb-player-reference).
# Книги тесты Java ищут там же: $NLB_TEST_BOOKS (по умолчанию <кэш>/books).
set -e

NAVIGATOR_REPO=https://github.com/Antokolos/NLBNavigator
NAVIGATOR_COMMIT=5685efa

TOOLS_DIR=$(cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(cd "$TOOLS_DIR/../.." && pwd)
CACHE=${NLB_REFERENCE_CACHE:-$HOME/.cache/nlb-player-reference}
BOOKS=${NLB_TEST_BOOKS:-$CACHE/books}
OUT=$ROOT_DIR/NLBL/src/test/resources/player-reference
mkdir -p "$CACHE" "$BOOKS"

# Клонирует репозиторий (если ещё нет) и переключает на нужный коммит
checkout() {
    url=$1; dir=$2; commit=$3
    if [ ! -d "$dir/.git" ]; then
        GIT_LFS_SKIP_SMUDGE=1 git clone -q "$url" "$dir"
    fi
    if ! git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null; then
        git -C "$dir" fetch -q origin
    fi
    git -C "$dir" -c advice.detachedHead=false checkout -q "$commit"
    GIT_LFS_SKIP_SMUDGE=1 git -C "$dir" submodule update -q --init --recursive
}

echo "== NLBNavigator $NAVIGATOR_COMMIT"
checkout "$NAVIGATOR_REPO" "$CACHE/NLBNavigator" "$NAVIGATOR_COMMIT"
cmake -S "$CACHE/NLBNavigator" -B "$CACHE/navigator-build" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release -DNLB_WITH_SDL3=OFF > /dev/null
cmake --build "$CACHE/navigator-build" --target nlb_main > /dev/null
PLAYER=$CACHE/navigator-build/nlb_main
CONFIG=$CACHE/NLBNavigator/nlb.json

rm -rf "$OUT"
mkdir -p "$OUT"
{
    echo "# Эталонные прохождения. Не править вручную: tools/player-reference/make-reference.sh"
    echo "navigator.commit=$NAVIGATOR_COMMIT"
} > "$OUT/manifest.properties"

grep -v '^#' "$TOOLS_DIR/books.txt" | while read -r name url commit mode walks steps; do
    [ -z "$name" ] && continue
    echo "== $name $commit"
    checkout "$url" "$BOOKS/$name" "$commit"
    mkdir -p "$OUT/$name"
    seed=1
    while [ "$seed" -le "$walks" ]; do
        # Сценарий начала прохождения, если есть (для книг, где случайный игрок не доходит
        # до нужных мест): scripts/<книга>/walk-N.script
        script=$TOOLS_DIR/scripts/$name/walk-$seed.script
        set -- --player "$PLAYER" --book "$BOOKS/$name" --mode "$mode" \
            --config "$CONFIG" --seed "$seed" --max-steps "$steps" \
            --log "$OUT/$name/walk-$seed.log" --input "$OUT/$name/walk-$seed.input"
        [ -f "$script" ] && set -- "$@" --script "$script"
        python3 "$TOOLS_DIR/walk.py" "$@"
        seed=$((seed + 1))
    done
    {
        echo "$name.commit=$commit"
        echo "$name.mode=$mode"
        echo "$name.walks=$walks"
    } >> "$OUT/manifest.properties"
done
echo "Готово: $OUT"
