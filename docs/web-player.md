# Веб-плеер NLBW: аудит и план

Цель — плеер на Java, который играет книги так же полно, как экспорт в INSTEAD (см. `CLAUDE.md`).
Этот документ — исходная точка: что есть сейчас, чего нет, что переиспользовать, план по этапам.

## Итог в двух словах

- **Модель книги в NLBL полная**: страницы, ссылки, объекты, модификации всех 57 типов, модули
  (включая внешние — при загрузке они копируются в страницу-модуль), autowired-свойства,
  медиа и их условия/переадресации/пресеты/флаги, многоязычные тексты, достижения. Читать книгу
  заново не нужно.
- **Правил игры в Java почти нет.** Старый плеер NLBW выполняет только `ASSIGN`, не знает
  объектов, инвентаря, autowired, таймеров, достижений, сохранений, картинок и звука. Его
  устройство (перепроигрывание всей истории на каждом ходе, общее `static`-состояние, XML + XSL)
  не подходит как основа — ядро нужно писать заново.
- **Эталон есть и исполняется**: экспорт INSTEAD (`ExportManager` + `STEADExportManager` /
  `VNSTEADExportManager` + `nlb.lua`) и NLBNavigator, где эта семантика уже реализована на C++
  (`PlayerEngine` ≈2150 строк, `PlayerContext` ≈980, `ModificationInterpreter` ≈1250 — все типы
  модификаций, слой экрана `NLBPresentation` ≈3100). Консольный `nlb_main` из NLBNavigator
  **собирается в облачной сессии** (`cmake -DNLB_WITH_SDL3=OFF`) и пишет журнал прохождения
  (`--log`), значит, сверку Java с C++ можно автоматизировать.

## Как устроен старый плеер

`NLBW/.../GetNLBDataService` (730 строк) + `NonLinearBookImpl.createFilteredPage` (NLBL):

- REST: `/{bookId}/start`, `/page/{id}`, `/link/{pageId}/{linkId}`, `/history`; модуль кодируется
  в `bookId` матричными параметрами (`book;1=pageId;2=...`).
- Состояния игры нет: на каждый запрос `createFilteredPage` заново проигрывает весь журнал
  решений (`History` / `DecisionPoint`) и считает переменные. Цена хода растёт с длиной игры.
- Из модификаций выполняется только `ASSIGN` (`updateVisitedVars`: «TODO: Other modification
  types»). Переменная страницы и ссылки ставится в `true`. Таймер — «TODO: page timer???».
- Выражения — движок JavaScript (`ScriptEngineManager`), неизвестная переменная = `false`.
- Ссылки: обычные, `once` (по журналу), ограничения, ограничение модуля, auto (первая
  автоссылка выполняется сразу), служебные Traverse/Return для внутренних модулей, MPL.
- Объекты: текст объектов, лежащих на странице, просто дописывается к тексту страницы.
- Вывод: страница сериализуется JAXB в XML и превращается в HTML через `xsl/page.xsl`
  (заголовок, текст, ссылки) + iframe с историей (`xsl/history.xsl`). Картинок и звука нет.
- Глобальное состояние: `static History s_history`, `static m_nlbCache`, корень библиотеки
  `s_nlbLibraryRoot = "/D:/work/Azartox/NLB/books/"`; XSL и `cfg` ищутся от текущего каталога.
  Один игрок на сервер.
- Используется только из редактора: `MainFrame` кладёт книгу в кэш и открывает `/start`.

## Возможности: что есть и чего нет

Статус относится к **старому плееру** (модель книги в NLBL есть для всех пунктов).
Эталон — где смотреть правильное поведение.

| Возможность | Старый NLBW | Эталон |
|---|---|---|
| Страницы, текст, заголовок, `$var$` в тексте | есть | `createPageBuildingBlocks`, `PlayerEngine::renderPage` |
| Обычные ссылки, ограничения, alt-текст недоступной ссылки | частично: alt-текста нет | `createLinkBuildingBlocks`, `PlayerEngine::buildLinks` |
| `once`, auto-ссылки, needs-action | частично: once и первая auto; needs-action нет | `nlb.lua` (autos), `PlayerEngine::runAutos` |
| Переменные страниц и ссылок | частично: только `true` при посещении | `STEADExportManager` (enter/xact) |
| Модификации: присваивание | есть | `ModificationInterpreter` |
| Модификации: IF/ELSE/WHILE, списки (PUSH/POP/INJECT/SHUFFLE…), RND, SIZE, PRN, GOTO, RETURN | нет | `ModificationInterpreter`, `nlb.lua:25-75` |
| Объекты: показ, взять, действие (act/actt/actf), применить (use), «не работает» (nouse) | нет (только текст) | `createObjBuildingBlocks`, `PlayerEngine::actOnObject/use` |
| Инвентарь, контейнеры, CLONE, ID, OBJS, ADD*/REMOVE/CLEAR | нет | `PlayerContext` (inventory/container*) |
| Теги объектов (TAG/GETTAG), тег по умолчанию страницы | нет | `PlayerContext::tag` |
| Модули внутренние и внешние, Traverse/Return, MPL, ограничение модуля | частично: без autowired и модификаций служебных ссылок | `ExportData`, `PlayerEngine::buildLinks` |
| Autowired (вход/выход, глобальный, ограничения, «autos first») | нет | `getAllAutowiredPageIds`, `PlayerEngine` |
| Таймер страницы (тик раз в ход) | нет | `PlayerEngine::settle`, `CLAUDE.md` |
| Достижения (ACHMAX/ACHIEVE/ACHIEVED, perfect game, между прохождениями) | нет | `PlayerContext::achieve`, `nlb.lua` setAchievement |
| Счётчики статистики (COUNTGET/COUNTRST) | нет | `nlb.lua` count_get |
| SNAPSHOT, сохранение/загрузка, `nosave`, «Начать заново» | нет (есть откат по истории) | `PlayerEngine::saveGame/loadGame/restart` |
| Темы VN и STANDARD, порции текста VN | нет | `VNSTEADExportManager.getPreprocessedChunks`, `GameScreen` |
| Картинки страниц и объектов, фон, анимация | нет | `ScreenModel::ImageRef`, `.constrid`/`.redirect` |
| Графические объекты (положение, эффекты, свёртывание) | нет | `decorateObjEffect`, `vn.lua`, `ScreenModel::Placement` |
| Звук, музыка (VOID), озвучка по порциям, флаг SFX | нет | `AudioDirector`, `docs/voiceover.md` (NLBNavigator) |
| Оформление окна (WINGEOM/INVGEOM/WINCOLOR/INVCOLOR), OPENURL | нет | `ScreenModel::WindowStyle` |
| Языки книги (выбор языка игроком) | частично: только язык книги | `MultiLangString`, `docs/localization.md` |
| Тексты game-inv / game-nouse, needs-action-count из `cfg/config.xml` | нет | `Export.getTextByLang` |
| Несколько игроков, состояние на сессию | нет (`static`) | — |
| Пакет `.nlbpack` | нет | `BookPack.cpp`, `docs/pack.md` (NLBNavigator) |
| Отдача медиа по HTTP, клиент HTML/CSS/JS, JSON | нет | — |

## Что переиспользовать

- **Модель и загрузка книги** NLBL — как есть.
- **`ExportManager`**: построение служебных ссылок модулей и autowired, тривиальность ссылок,
  `isInlineLink`, значения переменных по умолчанию (`getInitValuesMap`), карты медиа. Часть этой
  логики `private` и завязана на генерацию текста — вынести общее в доступные методы без
  изменения поведения экспорта.
- **`VNSTEADExportManager.getPreprocessedChunks`** — порции текста VN (сейчас `private`).
- **`PropertyManager` / `cfg/config.xml`** — тексты по умолчанию и needs-action-count.
- **`MultiLangString`** и выбор языка из `LanguageHelper` (NLBB) — для выбора языка игроком.
- **Jetty + Jersey** в NLBW и `Launcher` — оставить как веб-слой.
- Старые `History`, `DecisionPoint`, `createFilteredPage` — **не** использовать в новом ядре.

## Принятые решения

1. **Вычисление выражений** — свой вычислитель для синтаксиса условий NLB (`== != < <= > >=
   && || !`, числа, строки, переменные, `true/false`, скобки, арифметика), поведение — как в
   C++ (cparse). Не движок JavaScript: семантика INSTEAD отличается от JS в мелочах —
   отсутствующая числовая переменная делает условие ложным (`translateExpressionBody`
   добавляет проверки существования), строки и числа сравниваются по правилам Lua.
2. **JSON** — Jackson (`jersey-media-json-jackson`).
3. **Сохранения и достижения** хранятся у игрока: снимок — JSON, API отдаёт и принимает его,
   клиент по умолчанию хранит в `localStorage`, сервер ничего не хранит. Так плеер можно
   выложить для многих игроков без учётных записей.
4. **Режим по умолчанию — VN**, как в C++ (`ExportMode::VN`: тема DEFAULT = VN); STANDARD —
   по выбору при запуске.
5. **Старый плеер**: новый живёт на отдельных адресах (`/play/...`), редактор переключается
   на него после этапа 5, старый удаляется последним этапом.

## План по этапам

Каждый этап — отдельный PR с тестами. Ядро (этапы 1–4) — пакет `com.nlbhub.nlb.player` в NLBL,
без зависимостей от веба.

**Этап 0. Стенд для сверки.**
Тестовые книги (ваши каталоги и маленькие синтетические, собранные из Java через
`NonLinearBookFacade`); скрипт: собрать `nlb_main`, пройти книгу по сценарию выборов, записать
журнал. JUnit в NLBL (в зависимостях уже есть JUnit 4).
*Приёмка:* `mvn test` запускает тесты; для каждой тестовой книги есть эталонный журнал C++.
*Сделано:* `tools/player-reference` (см. README там): `make-reference.sh` собирает `nlb_main`
на зафиксированном коммите NLBNavigator, скачивает книги wolfman, barbarian_caves_demo,
chaperonrougehub (с внешним модулем `chaperon_filling_hub` — git-подмодуль), servant-of-ra
(тема STANDARD), Frontier (тема STANDARD, три внешних модуля-подмодуля), wild-island-quest
(картинки по тегу страницы и переадресации) и синтетическую bookU и делает по 5 прохождений `walk.py`
— случайных или начатых по сценарию (`scripts/`); пары «ввод + журнал» — в
`NLBL/src/test/resources/player-reference`. Случайность книг воспроизводима
(`nlb_main --deterministic`: k-й `rnd(n)` = `(k mod n) + 1`, `SHUFFLE` разворачивает список) —
Java-плеер в режиме сверки должен вести себя так же. `ReferenceDataTest` проверяет эталоны и
загрузку книг в Java. В книгах есть модули (вложенные и внешние), объекты, инвентарь,
контейнеры, теги, клоны, списки (PUSH/POP/SIZE/SHUFFLE…), WHILE, RND, таймеры страниц,
достижения, условия и переадресации картинок (выбор картинки по тегу страницы — в
wild-island-quest), анимацию, autowired (все пять страниц «Слуги Ра»
— по сценариям; глобальный и во внешних модулях), needs-action, callback-объекты, COUNTGET,
обе темы; не всё из этого встречается в случайных прохождениях — непокрытое добирается
сценариями и синтетическими книгами в JUnit на этапах 1–2.

**Этап 1. Ядро: навигация и модификации без объектов.**
Состояние сессии (переменные, списки, счётчики), вычислитель выражений, все модификации,
не связанные с объектами, страницы и ссылки (ограничения, once, auto, needs-action, alt-текст,
переменные страниц и ссылок, `$var$`), модули (внутренние, внешние, Traverse/Return, MPL,
ограничение модуля), autowired, таймер страницы, GOTO/RETURN, язык книги. Консольный запуск
ядра с журналом в формате `nlb_main`.
*Приёмка:* журналы Java и C++ совпадают на книгах без объектов; JUnit на каждый тип модификации.

**Этап 2. Объекты и инвентарь.**
Экземпляры и клоны, контейнеры, взять/действие/применить/nouse, инвентарь, теги, OBJS/ADD*/
REMOVE/CLEAR, тексты game-inv/game-nouse, достижения и perfect game, COUNTGET/COUNTRST.
*Приёмка:* журналы совпадают на книгах с объектами.

**Этап 3. Сохранения.**
Снимок состояния в JSON, загрузка (страница показывается заново без модификаций), `nosave`,
SNAPSHOT, «Начать заново», достижения отдельно от сохранения.
*Приёмка:* сохранить → загрузить → продолжить даёт тот же журнал, что игра без перерыва.

**Этап 4. Модель экрана.**
Аналог `ScreenModel`: STANDARD и VN, порции текста VN и листание, картинки страниц и объектов
(с `.constrid`/`.redirect`, анимация), графические объекты (положение, эффекты, свёртывание),
звук, музыка, озвучка по порциям (в порядке поля), оформление окна, OPENURL.
*Приёмка:* JUnit на модель экрана; сверка с `GameScreen`/`ScreenModel` на тестовых книгах.

**Этап 5. Веб-слой и клиент STANDARD.**
REST (JSON) поверх ядра: сессии игроков, команды, снимок экрана, сохранения; отдача медиа
книги; библиотека книг (каталог на сервере) и книга из редактора; чтение `.nlbpack`. Клиент на
HTML/CSS/JS для темы STANDARD. Редактор переключается на новый плеер.
*Приёмка:* книга проходится в браузере; два игрока играют одновременно независимо; кнопка
запуска в редакторе открывает новый плеер.

**Этап 6. Клиент VN и звук.**
Фон, порции текста, экран вариантов, графические объекты с эффектами, инвентарь, музыка,
эффекты и озвучка в браузере.
*Приёмка:* ваши VN-книги проходятся в браузере; проверка вами вручную.

**Этап 7. Уборка.**
Удаление старого плеера (`GetNLBDataService`, XSL, `History`/`DecisionPoint`,
`createFilteredPage`), если они больше нигде не нужны.

## Тестовые книги

Книги — у владельца, в репозиториях github.com/nlbproject (проект NLBB и его экспорт в INSTEAD):
`wolfman` (экспорт `instead-am_i_a_monster_vn`), `barbarian_caves_demo`
(`instead-barbarian_caves_demo`), `chaperonrougehub` (`instead-chaperon_new`; внешний модуль —
подмодуль `chaperon_filling_hub`), `servant-of-ra` (`instead-servant-of-ra`; тема STANDARD,
autowired), `Frontier` (`instead-frontier`; тема STANDARD, таймеры, случайность, списки;
внешние модули — подмодули `waspsnest`, `sabotage`, `frontierwarriors`), `wild-island-quest`
(`instead-wild-island-quest`; картинки по тегу страницы, переадресации), `bookU`
(синтетическая: списковые модификации, OBJS, ADDU/ADDALLU, HAVE, GOTO, достижения, глобальный
autowired, модуль; прохождение от автора — `scripts/bookU/walk-1.script`). Экспорты INSTEAD —
для сверки спорных мест с эталоном семантики.
