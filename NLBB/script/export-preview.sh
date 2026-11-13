#!/bin/sh
# Проверка экспортов ChoiceScript, jsIQ2, QSP, URQ, AXMA и INSTEAD в браузере (com.nlbhub.nlb.web.preview.ExportPreviewServer).
# Использование: export-preview.sh [каталог] [--port 8112] [--host localhost]
# Каталог по умолчанию — export-preview рядом с этим скриптом:
#   export-preview/choicescript — файлы экспорта в ChoiceScript (startup.txt и images)
#   export-preview/jsiq         — файл экспорта в jsIQ2 (.xml)
#   export-preview/qsp          — экспорт в QSP (book.txt, images, sound)
#   export-preview/urq          — экспорт в URQ (book.qst, images, sound)
#   export-preview/axma         — экспорт в AXMA (book.sm, images, sound)
#   export-preview/instead      — экспорт в INSTEAD (main.lua, модули, images, sound)
# Затем — http://localhost:8112/preview/ . Движки скачиваются при первом открытии игры своего формата
# в export-preview/runtime (в NLB они не входят: лицензии). То же умеет кнопка
# «Проверить экспорт в браузере» в редакторе: она сама экспортирует открытую книгу.
DIST="$(cd "$(dirname "$0")" && pwd)"

# Java из дистрибутива; если её нет — из JAVA_HOME или из PATH
if [ -x "$DIST/runtime/bin/java" ]; then
    JAVA="$DIST/runtime/bin/java"
elif [ -n "$JAVA_HOME" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi

exec "$JAVA" -Dstdout.encoding=UTF-8 -Dnlb.exportPreview="$DIST/export-preview" -classpath "$DIST/lib/*" \
    com.nlbhub.nlb.web.preview.ExportPreviewServer "$@"
