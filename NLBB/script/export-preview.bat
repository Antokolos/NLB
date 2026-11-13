@echo off
:: Проверка экспортов ChoiceScript, jsIQ2, QSP, URQ и AXMA в браузере (com.nlbhub.nlb.web.preview.ExportPreviewServer).
:: Использование: export-preview.bat [каталог] [--port 8112] [--host localhost]
:: Каталог по умолчанию — export-preview рядом с этим файлом:
::   export-preview\choicescript — файлы экспорта в ChoiceScript (startup.txt и images)
::   export-preview\jsiq         — файл экспорта в jsIQ2 (.xml)
::   export-preview\qsp          — экспорт в QSP (book.txt, images, sound)
::   export-preview\urq          — экспорт в URQ (book.qst, images, sound)
::   export-preview\axma         — экспорт в AXMA (book.sm, images, sound)
:: Затем — http://localhost:8112/preview/ . Движки скачиваются при первом открытии игры своего формата
:: в export-preview\runtime (в NLB они не входят: лицензии). То же умеет кнопка
:: «Проверить экспорт в браузере» в редакторе: она сама экспортирует открытую книгу.
setlocal
set "DIST=%~dp0"
chcp 65001 >nul

:: Java из дистрибутива; если её нет — из JAVA_HOME или из PATH
if exist "%DIST%runtime\bin\java.exe" (
    set "JAVA=%DIST%runtime\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

"%JAVA%" -Dstdout.encoding=UTF-8 "-Dnlb.exportPreview=%DIST%export-preview" -classpath "%DIST%lib\*" com.nlbhub.nlb.web.preview.ExportPreviewServer %*
