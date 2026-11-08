#!/bin/sh
# Скачивает тестовые книги из books.txt на зафиксированных коммитах — для тестов NLBL, которым
# нужны книги (сверка Java-плеера с NLBNavigator и др.), локально и в CI (.github/workflows/ci.yml).
#
# Без картинок и звука (каталоги images/ и sound/ на любой глубине, и в подмодулях): тестам они
# не нужны, а с ними книги весят ~2,3 ГБ вместо нескольких мегабайт. Клоны «без содержимого
# файлов» (--filter=blob:none) + sparse checkout: скачивается только то, что попадает в рабочую
# копию. Подмодули (внешние модули книг) — так же, рекурсивно.
#
# --with-media — с картинками и звуком (полные книги).
#
# Каталог: $NLB_TEST_BOOKS (по умолчанию ~/.cache/nlb-player-reference/books) — там их ищут тесты.
# Нужен git 2.27+.
set -e

TOOLS_DIR=$(cd "$(dirname "$0")" && pwd)
CACHE=${NLB_REFERENCE_CACHE:-$HOME/.cache/nlb-player-reference}
BOOKS=${NLB_TEST_BOOKS:-$CACHE/books}
mkdir -p "$BOOKS"
WITH_MEDIA=0
[ "$1" = "--with-media" ] && WITH_MEDIA=1

# Клонирует (если ещё нет) репозиторий без медиа и переключает на коммит; затем подмодули
fetch() {
    url=$1; dir=$2; commit=$3
    if [ ! -d "$dir/.git" ] && [ ! -f "$dir/.git" ]; then
        rm -rf "$dir"
        GIT_LFS_SKIP_SMUDGE=1 git clone -q --filter=blob:none --no-checkout "$url" "$dir"
        if [ "$WITH_MEDIA" = 0 ]; then
            git -C "$dir" sparse-checkout set --no-cone '/*' '!images/' '!sound/'
        fi
    elif [ "$WITH_MEDIA" = 1 ] && [ "$(git -C "$dir" config --bool core.sparseCheckout)" = true ]; then
        git -C "$dir" sparse-checkout disable
    fi
    if ! git -C "$dir" cat-file -e "$commit^{commit}" 2>/dev/null; then
        git -C "$dir" fetch -q --filter=blob:none origin "$commit" 2>/dev/null || git -C "$dir" fetch -q --filter=blob:none origin
    fi
    GIT_LFS_SKIP_SMUDGE=1 git -C "$dir" -c advice.detachedHead=false checkout -q "$commit"
    [ -f "$dir/.gitmodules" ] || return 0
    # подмодули: путь, адрес и коммит из дерева книги
    git -C "$dir" config -f .gitmodules --get-regexp '^submodule\..*\.path$' | while read -r key path; do
        name=${key#submodule.}
        name=${name%.path}
        suburl=$(git -C "$dir" config -f .gitmodules "submodule.$name.url")
        subcommit=$(git -C "$dir" ls-tree HEAD "$path" | awk '{print $3}')
        [ -n "$subcommit" ] || continue
        ( fetch "$suburl" "$dir/$path" "$subcommit" )
    done
}

grep -v '^#' "$TOOLS_DIR/books.txt" | while read -r name url commit rest; do
    [ -z "$name" ] && continue
    echo "== $name $commit"
    ( fetch "$url" "$BOOKS/$name" "$commit" )
done
if [ "$WITH_MEDIA" = 1 ]; then
    echo "Книги: $BOOKS"
else
    echo "Книги (без картинок и звука): $BOOKS"
fi
