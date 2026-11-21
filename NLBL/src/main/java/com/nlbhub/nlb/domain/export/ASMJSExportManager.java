/**
 * @(#)ASMJSExportManager.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2014 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 *
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 *
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Экспорт в AXMA Story Maker JS (https://axmajs.ru) — новую версию AXMA: тело абзаца в ней —
 * JavaScript, строка с «\» в начале — текст (docs/export-formats.md, «AXMA Story Maker JS»).
 * Отдельный от {@link ASMExportManager} (классический AXMA, макросы &lt;&lt;…&gt;&gt;): автоперевод
 * старого формата в AXMA JS ломается на кавычках в тексте, на переменных с именами встроенных
 * функций (random, back…), на необъявленных переменных и на ссылках с подписью из переменной.
 *
 * Устройство:
 * - переменные книги — свойства объекта v (v.имя): необъявленная — undefined, а не ошибка, и имена
 *   не сталкиваются со встроенными функциями AXMA JS; служебное состояние — объект nlb;
 * - страница — абзац с её id: модификации входа (если это не повторный показ), текст —
 *   print(выражение) (переменные — значениями), ссылки — print(button(подпись, абзац ссылки, ""));
 * - ссылка — абзац с её id: счётчик действий, модификации ссылки, goto(цель); GOTO из модификаций
 *   — goto() (отменяет остальной код абзаца);
 * - автоссылки — до обычных ссылок и вместо них: на странице с текстом — единственной кнопкой, без
 *   текста — сразу goto() (как в экспорте AXMA, раздел «Автоссылки»).
 *
 * - объекты, инвентарь, контейнеры, клоны, списки, теги — состояние в nlb, код — библиотека nlbf
 *   и объекты nlbo в абзаце nlb_lib (невидимы для сохранения; см. libraryPassages и
 *   generateObjText).
 *
 * AXMA JS склеивает строки абзаца через «;»: каждая строка — законченный оператор.
 * - картинки, звук (медиа — в папке files), достижения (в хранилище браузера, между прохождениями).
 */
public class ASMJSExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = "\n";
    /**
     * Переменная в тексте до сборки выражения: VAR_START имя VAR_END (знаки из области частного
     * использования: trim() их не срезает, как управляющие \u0001 и \u0002)
     */
    private static final String VAR_START = "\uE001";
    private static final String VAR_END = "\uE002";
    private static final Pattern VAR_MARK = Pattern.compile(VAR_START + "([^" + VAR_END + "]*)" + VAR_END);
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};

    private String m_language = "en";

    public ASMJSExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    // --- текст как выражение JavaScript

    /** Строковый литерал JavaScript в двойных кавычках */
    static String jsString(String text) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '<': sb.append("\\u003c"); break;  // «</script>» в тексте не закроет скрипт игры
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Текст (после expandVariables: HTML, переменные — метками) как выражение JavaScript:
     * "…" + v.имя + "…"
     */
    static String js(String text) {
        if (StringHelper.isEmpty(text)) {
            return "\"\"";
        }
        Matcher m = VAR_MARK.matcher(text);
        StringBuilder result = new StringBuilder();
        int at = 0;
        while (m.find()) {
            if (m.start() > at) {
                result.append(result.length() > 0 ? " + " : "").append(jsString(text.substring(at, m.start())));
            }
            result.append(result.length() > 0 ? " + " : "").append("nlb.str(").append(variable(m.group(1))).append(")");
            at = m.end();
        }
        if (at < text.length() || result.length() == 0) {
            result.append(result.length() > 0 ? " + " : "").append(jsString(text.substring(at)));
        }
        return result.toString();
    }

    private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

    /** Переменная книги: v.имя (имя не идентификатор JavaScript — v["имя"]) */
    static String variable(String name) {
        return IDENTIFIER.matcher(name).matches() ? "v." + name : "v[" + jsString(name) + "]";
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String todo(String what) {
        return "/* NLB: " + what + " — в экспорте AXMA JS пока не поддерживается */ ";
    }

    // --- объекты: состояние и ссылки на экземпляры

    /** Контейнер «инвентарь» в nlb.cs */
    private static final String INVENTORY = "nlb_inv";
    /** Пустая метка {} в описании объекта: её заменяет подпись объекта (generateObjText) */
    private static final String EMPTY_LINK_LABEL = "\u0004";

    /** Код объектов (nlbo[id] = {…}) — в абзац nlb_lib, после страниц (generateTrailingText) */
    private final StringBuilder m_objCode = new StringBuilder();
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();
    private final java.util.Map<String, String> m_objNames = new java.util.LinkedHashMap<>();
    private final List<String> m_callbacks = new java.util.ArrayList<>();
    /** Все достижения книги и perfect game */
    private final List<String> m_achievementNames = new java.util.ArrayList<>();
    private String m_perfectGame = "";
    private String m_gameInvText = "";
    private String m_gameNouseText = "";

    /** Имя переменной из variable(): v.имя или v["имя"] -> имя (имя списка) */
    static String rawName(String decorated) {
        if (decorated == null) {
            return "";
        }
        if (decorated.startsWith("v[\"") && decorated.endsWith("\"]")) {
            return decorated.substring(3, decorated.length() - 2).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return decorated.startsWith("v.") ? decorated.substring(2) : decorated;
    }

    /** Имя списка строкой JavaScript (списки — nlb.lists[имя]) */
    private static String listKey(String decorated) {
        return jsString(rawName(decorated));
    }

    /**
     * Экземпляр операции: известный объект — его id, из переменной — её значение (id объекта, клона
     * или страницы), без них — «сам» (s в INSTEAD): экземпляр, от имени которого выполняется код
     * объекта (nlb.me; у клона — клон), иначе страница
     */
    private String objRef(String objId, String objVar) {
        if (objId != null) {
            return jsString(objId);
        }
        if (StringHelper.notEmpty(objVar)) {
            return objVar;
        }
        return selfIsObj() ? "nlb.me" : jsString(selfId());
    }

    /** Контейнер «в себя»: экземпляр-объект (его id — ключ контейнера) или страница */
    private String selfContainer() {
        return objRef(null, null);
    }

    /** Строковый литерал из jsString обратно в текст; не литерал — null */
    static String unJsString(String literal) {
        if (literal == null || literal.length() < 2 || !literal.startsWith("\"") || !literal.endsWith("\"")) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < literal.length() - 1; i++) {
            char c = literal.charAt(i);
            if (c == '"') {
                return null;  // конец литерала раньше конца выражения: это сумма
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            char e = literal.charAt(++i);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 'u': sb.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16)); i += 4; break;
                default: sb.append(e);
            }
        }
        return sb.toString();
    }

    /**
     * Подпись в сцене (PlayerEngine.sceneLabel): первая непустая строка текста действия, если она
     * без переменных («Сила карты 20» у карт «Фронтира»); иначе пусто
     */
    static String firstActLine(String actStart) {
        final String prefix = "nlbf.say(", suffix = ");";
        if (actStart == null || !actStart.strip().startsWith(prefix) || !actStart.strip().endsWith(suffix)) {
            return "";
        }
        String c = actStart.strip();
        String text = unJsString(c.substring(prefix.length(), c.length() - suffix.length()));
        if (text == null) {
            return "";
        }
        for (String line : text.split("<br>")) {
            if (!line.replaceAll("<[^>]*>", "").strip().isEmpty()) {
                return line.strip();
            }
        }
        return "";
    }

    @Override
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks);
    }

    /** Текст — HTML, '^' (перевод строки INSTEAD) и строки — &lt;br&gt;, переменная — меткой */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(html(textChunk.getText()).replace("^", "<br>"));
                    break;
                case ACTION_TEXT:
                    break;  // $^$ — текст действия (VN): как в других экспортах, не выводится
                case VARIABLE:
                    result.append(VAR_START).append(textChunk.getText()).append(VAR_END);
                    break;
                case NEWLINE:
                    result.append("<br>");
                    break;
            }
        }
        return result.toString();
    }

    /** Подпись ссылки — одной строкой */
    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks).replace("<br>", " ").trim();
    }

    // --- файл и служебные абзацы

    private static String passage(String title) {
        return ":: " + title + "[::]10-10-0" + LINE_SEPARATOR;
    }

    /** Текстовый абзац (StoryTitle и др.): только текст, строкой «\» */
    private static String textPassage(String title, String text) {
        return passage(title) + "\\ " + html(text.trim()).replace("\n", " ") + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_language = StringHelper.isEmpty(nlbBuildingBlocks.getLang()) ? "en" : nlbBuildingBlocks.getLang();
        StringBuilder sb = new StringBuilder("ASMJS[::]128[::]0[::]0[::]0[::]mw[::]html").append(LINE_SEPARATOR);
        String title = StringHelper.notEmpty(nlbBuildingBlocks.getTitle()) ? nlbBuildingBlocks.getTitle() : "NLB";
        String author = StringHelper.notEmpty(nlbBuildingBlocks.getAuthor()) ? nlbBuildingBlocks.getAuthor() : "NLB";
        // StoryTitle, StoryAuthor и StorySubtitle обязательны для публикации в библиотеку AXMA JS
        sb.append(textPassage("StoryTitle", title));
        sb.append(textPassage("StoryAuthor", author));
        sb.append(textPassage("StorySubtitle", title));
        m_gameInvText = nlbBuildingBlocks.getGameInvText() == null ? "" : html(nlbBuildingBlocks.getGameInvText());
        m_gameNouseText = nlbBuildingBlocks.getGameNouseText() == null ? "" : html(nlbBuildingBlocks.getGameNouseText());
        m_achievementNames.clear();
        m_achievementNames.addAll(nlbBuildingBlocks.getAchievements());
        m_perfectGame = StringHelper.notEmpty(nlbBuildingBlocks.getAchievementNamePerfectGame())
                ? nlbBuildingBlocks.getAchievementNamePerfectGame() : "";
        m_callbacks.clear();
        for (ObjBuildingBlocks obj : orderedObjects(nlbBuildingBlocks)) {
            if (obj.isCallback()) {
                m_callbacks.add(obj.getObjId());
            }
        }
        sb.append(passage("nlb_init")).append(initCode(nlbBuildingBlocks)).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        return sb.toString();
    }

    /**
     * Начальное состояние: переменные книги (v) и служебное (nlb): счётчики действий, страница,
     * объекты. Контейнер — массив id экземпляров nlb.cs[id страницы, объекта или клона; nlb_inv —
     * инвентарь] в порядке, в каком клали; объект может лежать в нескольких сразу (objs():add),
     * nlb.own[экземпляр] — последний владелец (CNTNR). Клон — «nlb_cN», nlb.proto[клон] — исходный
     * объект. nlb.reply — ответ на действие, nlb.sel — предмет, выбранный для применения
     */
    private String initCode(NLBBuildingBlocks nlbBuildingBlocks) {
        StringBuilder counters = new StringBuilder();
        for (String type : COUNT_TYPES) {
            counters.append(counters.length() > 0 ? ", " : "").append(type).append(": 0");
        }
        StringBuilder sb = new StringBuilder("v = {}; nlb = {page: \"\", redraw: 0, ac: 0, cnt: {" + counters + "}, cntr: {"
                + counters + "}, cprev: 0, cs: {}, own: {}, proto: {}, cc: 0, tags: {}, lists: {}, sel: \"\", o: \"\", o2: \"\", "
                + "reply: \"\", rp: \"\", me: \"\", ms: [], gen: 0, seen: {}, ivn: 0, ivseen: {}, music: \"\"};");
        sb.append(LINE_SEPARATOR)
                // значение переменной в тексте: undefined — пусто, как незаданная в плеере
                .append("nlb.str = function (x) { return x === undefined || x === null ? \"\" : String(x); };")
                .append(LINE_SEPARATOR);
        for (ObjBuildingBlocks obj : orderedObjects(nlbBuildingBlocks)) {
            sb.append("nlb.own[").append(jsString(obj.getObjId())).append("] = ").append(jsString(obj.getContainerId())).append("; ");
            if (StringHelper.notEmpty(obj.getObjDefaultTagVariable())) {
                sb.append("nlb.tags[").append(jsString(obj.getObjId())).append("] = ").append(jsString(obj.getObjDefaultTagVariable())).append("; ");
            }
        }
        // начальное содержимое контейнеров (порядок книги, content)
        for (java.util.Map.Entry<String, List<String>> entry : initialContents(nlbBuildingBlocks).entrySet()) {
            StringBuilder items = new StringBuilder();
            for (String item : entry.getValue()) {
                items.append(items.length() == 0 ? "" : ", ").append(jsString(item));
            }
            sb.append(LINE_SEPARATOR).append("nlb.cs[").append(jsString(entry.getKey())).append("] = [").append(items).append("];");
        }
        // теги по умолчанию страниц (var { tag = … } в INSTEAD)
        for (PageBuildingBlocks page : nlbBuildingBlocks.getPagesBuildingBlocks()) {
            if (StringHelper.notEmpty(page.getPageDefaultTag())) {
                sb.append(LINE_SEPARATOR).append("nlb.tags[").append(jsString(page.getPageId())).append("] = ")
                        .append(jsString(page.getPageDefaultTag())).append(";");
            }
        }
        return sb.toString();
    }

    /** Код абзаца, которому нужна библиотека (после загрузки сохранения в новом окне её ещё нет) */
    private static final String NEED_LIB = "if (!window.nlbf) { display(\"nlb_lib\"); }" + LINE_SEPARATOR;

    /**
     * Библиотека и код объектов — свойства window, невидимые для перебора: сохранение AXMA JS
     * записывает все перечислимые переменные окна (функции — исходным текстом), а код не должен
     * попадать в сохранения. Функции, а не абзацы: код абзаца AXMA JS обёрнут в try/catch, и goto()
     * (исключение) во вложенном display() не прервал бы вызывающий абзац; из функции исключение
     * доходит до абзаца, в котором щёлкнули.
     *
     * Щелчок по объекту — ссылка на абзац nlb_act, функция ссылки ставит nlb.o (экземпляр); по
     * предмету в инвентаре — на nlb_invclick (nlb.o2). Функция ссылки сохраняется исходным текстом
     * (замыкание теряется), поэтому собирается при показе: new Function("nlb.o = \"…\";").
     */
    private String libraryPassages() {
        final String ls = LINE_SEPARATOR;
        StringBuilder sb = new StringBuilder();
        sb.append(passage("nlb_lib"));
        sb.append("Object.defineProperty(window, \"nlbf\", {value: {}, writable: true, configurable: true, enumerable: false});").append(ls);
        sb.append("Object.defineProperty(window, \"nlbo\", {value: {}, writable: true, configurable: true, enumerable: false});").append(ls);
        String[] lib = {
                // прототип экземпляра (у клона — исходный объект) и код объекта
                "nlbf.P = function (x) { return nlb.proto[x] || x; };",
                "nlbf.O = function (x) { return nlbo[nlbf.P(x)] || {}; };",
                // часть part кода объекта obj от имени экземпляра inst (nlb.me — «сам»); стек
                // nlb.ms после goto() не восстанавливается — его сбрасывает каждый абзац-ход
                "nlbf.run = function (inst, obj, part, arg) { var o = nlbo[obj]; if (!o || !o[part]) { return 0; } "
                        + "nlb.ms.push(nlb.me); nlb.me = inst; var r = o[part](arg); nlb.me = nlb.ms.pop(); return r; };",
                "nlbf.call = function (inst, part, arg) { return nlbf.run(inst, nlbf.P(inst), part, arg); };",
                "nlbf.on = function (inst) { var o = nlbf.O(inst); return !o.on || o.on(); };",
                // контейнеры (PlayerContext.containerAdd/Remove/Clear)
                "nlbf.cs = function (c) { return nlb.cs[c] || (nlb.cs[c] = []); };",
                "nlbf.has = function (x) { return nlbf.cs(\"" + INVENTORY + "\").indexOf(x) != -1; };",
                // в контейнер, не убирая из прежних мест (objs():add); уже лежащий без unique — клоном (addf)
                "nlbf.add = function (x, c, multi) { var a = nlbf.cs(c); if (multi && a.indexOf(x) != -1) { x = nlbf.clone(x); } "
                        + "if (a.indexOf(x) == -1) { a.push(x); } if (c != \"" + INVENTORY + "\") { nlb.own[x] = c; } };",
                "nlbf.del = function (x, c) { var a = nlb.cs[c], j = a ? a.indexOf(x) : -1; if (j > -1) { a.splice(j, 1); } "
                        + "if (c != \"" + INVENTORY + "\") { nlb.own[x] = \"\"; } };",
                // взять (take): со страницы, при щелчке по переносимому — и от последнего владельца
                "nlbf.take = function (x, owner) { var c = nlb.page, a = nlb.cs[c], j = a ? a.indexOf(x) : -1; "
                        + "if (j == -1 && owner) { c = nlb.own[x]; a = c ? nlb.cs[c] : null; j = a ? a.indexOf(x) : -1; } "
                        + "if (j > -1) { nlbf.del(x, c); } nlbf.add(x, \"" + INVENTORY + "\"); };",
                "nlbf.addinv = function (x) { if (nlbf.has(x)) { x = nlbf.clone(x); } nlbf.take(x, 0); };",
                // клон (PlayerContext.cloneInstance): прототип и тег
                "nlbf.clone = function (q) { nlb.cc++; var c = \"nlb_c\" + nlb.cc; nlb.proto[c] = nlbf.P(q); "
                        + "if (nlb.tags[q] !== undefined) { nlb.tags[c] = nlb.tags[q]; } nlb.own[c] = \"\"; return c; };",
                "nlbf.clear = function (c) { var a = nlbf.cs(c); for (var i = 0; i < a.length; i++) { "
                        + "if (nlb.own[a[i]] == c) { nlb.own[a[i]] = \"\"; } } nlb.cs[c] = []; };",
                // списки (nlb.lua: push/pop — начало, inject/eject — конец); опустевший исчезает
                "nlbf.list = function (n) { return nlb.lists[n] || (nlb.lists[n] = []); };",
                "nlbf.drop = function (n) { if (nlb.lists[n] && !nlb.lists[n].length) { delete nlb.lists[n]; } };",
                "nlbf.pop = function (n, end) { var a = nlb.lists[n], r = a && a.length ? (end ? a.pop() : a.shift()) : false; nlbf.drop(n); return r; };",
                "nlbf.objs = function (c, n) { var a = nlbf.cs(c).slice(), l = nlbf.list(n); for (var i = 0; i < a.length; i++) { l.unshift(a[i]); } nlbf.drop(n); };",
                "nlbf.addall = function (n, n2, c, multi) { var a = nlbf.list(n).slice(); for (var i = 0; i < a.length; i++) { "
                        + "if (n2) { nlbf.list(n2).unshift(a[i]); } else if (nlb.own[a[i]] !== undefined) { nlbf.add(a[i], c, multi); } } "
                        + "nlbf.drop(n); if (n2) { nlbf.drop(n2); } };",
                "nlbf.shuffle = function (n) { var a = nlbf.list(n); for (var j = a.length - 1; j > 0; j--) { "
                        + "var k = Math.floor(Math.random() * (j + 1)), t = a[j]; a[j] = a[k]; a[k] = t; } nlbf.drop(n); };",
                // из списка — первый того же прототипа (nlb:rmv)
                "nlbf.lrmv = function (n, q) { var a = nlb.lists[n] || [], p = nlbf.P(q); for (var j = 0; j < a.length; j++) { "
                        + "if (nlbf.P(a[j]) == p) { a.splice(j, 1); break; } } nlbf.drop(n); };",
                // ответ на действие: после объектов страницы; после перехода — перед текстом новой
                // страницы (кроме ответа, дословно совпадающего с её текстом)
                "nlbf.say = function (t) { nlb.reply += t + \"<br>\"; };",
                "nlbf.reply = function () { if (nlb.reply) { print(nlb.reply); nlb.reply = \"\"; } };",
                "nlbf.before = function (t) { if (nlb.reply && nlb.rp != nlb.page) { "
                        + "if (nlb.reply.split(\"<br>\").join(\"\") != t.split(\"<br>\").join(\"\")) { print(nlb.reply + \"<br>\"); } nlb.reply = \"\"; } };",
                // объекты контейнера: описание, вложенные — рекурсивно
                "nlbf.show = function (c) { var a = nlbf.cs(c); for (var i = 0; i < a.length; i++) { nlbf.call(a[i], \"dsc\"); } };",
                "nlbf.pdscs = function (c) { var a = nlbf.cs(c); for (var i = 0; i < a.length; i++) { nlbf.call(a[i], \"pdscs\"); } };",
                // PDSC, PDSCS: описание — в ответ на действие (как текст действия в плеере)
                "nlbf.pdsc = function (x) { if (x && nlbf.on(x)) { var t = nlbf.call(x, \"text\"); if (t) { nlbf.say(t); } } };",
                // инвентарь: предметы по взятию, за каждым — содержимое (PlayerEngine.inventoryObjects)
                "nlbf.inv = function () { nlb.ivn++; if (nlbf.cs(\"" + INVENTORY + "\").length) { print(\"* * *<br>\"); } nlbf.invIn(\"" + INVENTORY + "\"); };",
                "nlbf.invIn = function (c) { var a = nlbf.cs(c); for (var i = 0; i < a.length; i++) { var x = a[i]; "
                        + "if (nlbf.on(x) && nlb.ivseen[x] != nlb.ivn) { nlb.ivseen[x] = nlb.ivn; nlbf.call(x, \"item\"); nlbf.invIn(x); } } };",
                // ссылка (кнопка) на абзац p: подпись AXMA JS вставляет и в onclick — в строку в
                // кавычках, где кавычки подписи ломают код; там ей достаточно быть непустой, поэтому
                // ссылка строится с подписью «x», а настоящая ставится только в текст ссылки
                "nlbf.a = function (t, p, code, b) { var h = (b ? button : link)(\"x\", p, \"\", code), k = h.lastIndexOf(\">x</span>\"); "
                        + "return k < 0 ? h : h.substring(0, k) + \">\" + t + \"</span>\"; };",
                "nlbf.lnk = function (t, x) { return nlbf.a(t, \"nlb_act\", new Function(\"nlb.o = \" + JSON.stringify(x) + \";\"), 0); };",
                "nlbf.btn = function (t, x) { return nlbf.a(t, \"nlb_invclick\", new Function(\"nlb.o2 = \" + JSON.stringify(x) + \";\"), 1); };",
                // действие: текст и модификации, переносимый — в инвентарь
                "nlbf.act = function (x) { nlbf.call(x, \"acta\"); var o = nlbf.O(x); if (o.take) { nlbf.take(o.take === 1 ? x : o.take, 1); } };",
                // применение предмета x к t; без ответа — nouse
                "nlbf.use = function (x, t) { nlb.ac++; nlb.cnt.use++; nlb.sel = \"\"; if (!nlbf.call(x, \"use\", t)) { "
                        + "var o = nlbf.O(x); nlbf.say(o.nouse ? o.nouse() : " + jsString(m_gameNouseText) + "); } };",
                // USE в модификациях: цель — список с этим именем (каждый элемент), иначе объект
                "nlbf.useOp = function (x, n, t) { if (nlb.lists.hasOwnProperty(n)) { var a = nlb.lists[n].slice(); "
                        + "for (var i = 0; i < a.length; i++) { nlbf.call(x, \"use\", a[i]); } } else { nlbf.call(x, \"use\", t); } };",
                // медиа — в папке files рядом с историей (так их принимает публикация AXMA JS); в
                // редакторе путь к ним даёт asmGlobal_GetMediaPath
                "nlbf.src = function (p) { p = \"files/\" + p; return window.asmGlobal_GetMediaPath ? asmGlobal_GetMediaPath(p) : p; };",
                // картинка по тегу экземпляра x (ModificationInterpreter.selectImage): первая, чьё
                // условие совпало с тегом (без условия — всегда)
                "nlbf.pick = function (x, a) { var t = nlb.tags[x]; for (var i = 0; i < a.length; i++) { if (!a[i][0] || a[i][0] == t) { return a[i][1]; } } return \"\"; };",
                "nlbf.img = function (p) { return p ? \"<img class='nlb_img' style='max-width:100%' src='\" + String(nlbf.src(p)).split(\"'\").join(\"%27\") + \"'>\" : \"\"; };",
                "nlbf.pimg = function (p) { if (p) { print(nlbf.img(p) + \"<br>\"); } };",
                "nlbf.oimg = function (x) { return nlbf.img(nlbf.call(x, \"img\") || \"\"); };",
                // звук: музыка — фоном (тот же файл не перезапускается, set_music в INSTEAD), эффект —
                // отдельным Audio, если звук не выключен в настройках игры
                // (music() сама переводит путь через asmGlobal_GetMediaPath — ей путь в files как есть)
                "nlbf.music = function (p) { if (p == nlb.music) { return; } nlb.music = p; if (p) { music(\"files/\" + p); } else { music(); } };",
                "nlbf.sfx = function (p) { if (window.asmGlobal && !asmGlobal.audio_enable) { return; } "
                        + "try { var r = new Audio(nlbf.src(p)).play(); if (r && r.catch) { r.catch(function () {}); } } catch (e) {} };",
                "nlbf.snd = function (x) { nlbf.call(x, \"snd\"); };",
                // достижения (PlayerContext.achieve, setAchievement в nlb.lua) — между прохождениями,
                // как в INSTEAD: в хранилище браузера (не в сохранении игры); «id:имя|место» — место
                // засчитано, «n:имя» — число мест, «max:имя» — порог, «got:имя» — объявлено
                "nlbf.achKey = function () { return \"nlb_ach:\" + (window[\"$$storytitle\"] || \"\"); };",
                "nlbf.achs = function () { if (!nlbf.A) { try { nlbf.A = JSON.parse(localStorage.getItem(nlbf.achKey())); } catch (e) {} "
                        + "if (!nlbf.A || typeof nlbf.A != \"object\") { nlbf.A = {}; } } return nlbf.A; };",
                "nlbf.achSave = function () { try { localStorage.setItem(nlbf.achKey(), JSON.stringify(nlbf.achs())); } catch (e) {} };",
                "nlbf.achn = function (n) { return nlbf.achs()[\"n:\" + n] || 0; };",
                "nlbf.achmax = function (n, m) { nlbf.achs()[\"max:\" + n] = m; nlbf.achSave(); };",
                "nlbf.achDone = function (n) { var a = nlbf.achs(); return (a[\"n:\" + n] || 0) >= Math.max(a[\"max:\" + n] || 0, 1); };",
                "nlbf.achGot = function (n) { var a = nlbf.achs(); if (!a[\"got:\" + n] && nlbf.achDone(n)) { a[\"got:\" + n] = 1; nlbf.say(nlbf.achAnn + n); } };",
                "nlbf.achieve = function (n, place) { var a = nlbf.achs(), k = \"id:\" + n + \"|\" + place; "
                        + "if (!a[k]) { a[k] = 1; a[\"n:\" + n] = (a[\"n:\" + n] || 0) + 1; } nlbf.achGot(n); "
                        + "if (nlbf.perfect && !a[\"got:\" + nlbf.perfect]) { var ok = 1; for (var i = 0; i < nlbf.achNames.length; i++) { "
                        + "if (nlbf.achNames[i] != nlbf.perfect && !nlbf.achDone(nlbf.achNames[i])) { ok = 0; } } "
                        + "if (ok) { a[\"n:\" + nlbf.perfect] = Math.max(a[\"n:\" + nlbf.perfect] || 0, 1); nlbf.achGot(nlbf.perfect); } } nlbf.achSave(); };",
                // конец хода: callback-объекты, если игрок с прошлого хода что-то сделал
                "nlbf.turn = function () { var d = " + countSum(0) + "; if (d > nlb.cprev) { nlb.cprev = d; "
                        + "for (var i = 0; i < nlbf.cbs.length; i++) { if (nlbf.on(nlbf.cbs[i])) { nlbf.call(nlbf.cbs[i], \"acta\"); } } } nlb.cprev = d; };",
        };
        for (String line : lib) {
            sb.append(line).append(ls);
        }
        StringBuilder cbs = new StringBuilder();
        for (String id : m_callbacks) {
            cbs.append(cbs.length() > 0 ? ", " : "").append(jsString(id));
        }
        sb.append("nlbf.cbs = [").append(cbs).append("];").append(ls);
        StringBuilder achs = new StringBuilder();
        for (String name : m_achievementNames) {
            achs.append(achs.length() > 0 ? ", " : "").append(jsString(name));
        }
        sb.append("nlbf.achNames = [").append(achs).append("]; nlbf.perfect = ").append(jsString(m_perfectGame))
                .append("; nlbf.achAnn = ").append(jsString(achievementAnnouncement(m_language))).append(";").append(ls);
        StringBuilder names = new StringBuilder();
        for (java.util.Map.Entry<String, String> name : m_objNames.entrySet()) {
            names.append(names.length() > 0 ? ", " : "").append(jsString(name.getKey())).append(": ").append(jsString(name.getValue()));
        }
        sb.append("nlbf.names = {").append(names).append("};").append(ls);
        sb.append(m_objCode).append(ls);

        // щелчок по объекту в тексте: действие или (выбран предмет) применение, затем страница заново
        sb.append(passage("nlb_act")).append(NEED_LIB)
                .append("nlb.ms = []; nlb.me = \"\"; nlb.rp = nlb.page;").append(ls)
                .append("if (nlb.sel) { nlbf.use(nlb.sel, nlb.o); } else { ").append(count("act")).append("nlbf.act(nlb.o); }").append(ls)
                .append("nlb.redraw = 1; goto(nlb.page);").append(ls).append(ls);
        // щелчок по предмету: меню — действие, показатель — ничего; первый — применение к себе или
        // game-inv и выбор; другой предмет — применение выбранного к нему; тот же — отмена выбора
        sb.append(passage("nlb_invclick")).append(NEED_LIB)
                .append("nlb.ms = []; nlb.me = \"\"; nlb.rp = nlb.page;").append(ls)
                .append("var x = nlb.o2, m = nlbf.O(x).menu || 0;").append(ls)
                .append("if (!nlb.sel && m) { ").append(count("inv")).append("if (m == 1) { nlbf.call(x, \"acta\"); } } ")
                .append("else if (!nlb.sel) { ").append(count("inv")).append("nlb.sel = x; if (!nlbf.call(x, \"use\", x)) { nlbf.say(")
                .append(jsString(m_gameInvText)).append("); } } ")
                .append("else if (nlb.sel == x) { nlb.sel = \"\"; } ")
                .append("else { nlbf.use(nlb.sel, x); }").append(ls)
                .append("nlb.redraw = 1; goto(nlb.page);").append(ls).append(ls);
        return sb.toString();
    }

    /** Сумма счётчиков действий с последнего сброса (PlayerContext.countGet): inv 1, act 2, use 4, walk 8 */
    private static String countSum(int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                sum.append(sum.length() > 0 ? " + " : "").append("(nlb.cnt.").append(COUNT_TYPES[i])
                        .append(" - nlb.cntr.").append(COUNT_TYPES[i]).append(")");
            }
        }
        return sum.length() > 0 ? "(" + sum + ")" : "0";
    }

    /**
     * Картинки для nlbf.pick: [ ["тег", "путь"], … ] (тег "" — без условия). Скобки — через пробел:
     * «[[…]]» AXMA JS принимает за ссылку даже в коде
     */
    static String imageArray(List<MediaChoice> images) {
        StringBuilder sb = new StringBuilder("[ ");
        for (MediaChoice image : images) {
            sb.append(sb.length() > 2 ? ", " : "").append("[").append(jsString(image.hasConstraint() ? image.getConstraint() : ""))
                    .append(", ").append(jsString(image.getPath())).append("]");
        }
        return sb.append(" ]").toString();
    }

    /**
     * Звук страницы или объекта (ModificationInterpreter.playSound, snd в INSTEAD) — функция snd:
     * без условия — всегда, подряд идущие с условием — цепочка по тегу экземпляра. Музыка —
     * nlbf.music, эффект — nlbf.sfx; у страницы эффект — фраза озвучки: в STANDARD при входе звучит
     * одна, первая подходящая (как в экспорте QSP)
     */
    static String soundFunction(List<MediaChoice> sounds, boolean page) {
        StringBuilder sb = new StringBuilder("snd = function () { var t = nlb.tags[nlb.me], vo = 0; ");
        for (List<MediaChoice> step : MediaChoice.soundSteps(sounds)) {
            if (step.size() == 1 && !step.get(0).hasConstraint()) {
                sb.append(soundStatement(step.get(0), page));
                continue;
            }
            boolean first = true;
            for (MediaChoice sound : step) {
                sb.append(first ? "if (" : "else if (").append("t == ").append(jsString(sound.getConstraint())).append(") { ")
                        .append(soundStatement(sound, page)).append("} ");
                first = false;
            }
        }
        return sb.append("}").toString();
    }

    private static String soundStatement(MediaChoice sound, boolean page) {
        if (sound.isStop()) {
            return "nlbf.music(\"\"); ";
        }
        if (!sound.isSfx()) {
            return "nlbf.music(" + jsString(sound.getPath()) + "); ";
        }
        return page ? "if (!vo) { nlbf.sfx(" + jsString(sound.getPath()) + "); vo = 1; } " : "nlbf.sfx(" + jsString(sound.getPath()) + "); ";
    }

    /** Медиа страницы (объекта) в nlb_lib: nlbo[id].img — путь картинки по тегу, nlbo[id].snd — звук */
    private static String mediaCode(String id, List<MediaChoice> images, List<MediaChoice> sounds, boolean page) {
        final String o = "nlbo[" + jsString(id) + "]";
        StringBuilder sb = new StringBuilder();
        if (page) {
            sb.append(o).append(" = ").append(o).append(" || {};").append(LINE_SEPARATOR);
        }
        if (!images.isEmpty()) {
            sb.append(o).append(".img = function () { return nlbf.pick(nlb.me, ").append(imageArray(images)).append("); };").append(LINE_SEPARATOR);
        }
        if (!sounds.isEmpty()) {
            sb.append(o).append(".").append(soundFunction(sounds, page)).append(";").append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    @Override
    protected String generateTrailingText() {
        return libraryPassages();
    }

    /** Подпись объекта: disp, без неё — имя (как nam в INSTEAD), без имени — id */
    private static String objLabel(ObjBuildingBlocks obj) {
        if (StringHelper.notEmpty(obj.getObjDisp())) {
            return obj.getObjDisp();
        }
        return StringHelper.notEmpty(obj.getObjName()) ? html(obj.getObjName()) : obj.getObjId();
    }

    /**
     * Объект — запись nlbo[id] с функциями (вызываются от имени экземпляра nlb.me): on (условие),
     * dsc (описание со ссылками и содержимое; условие скрывает и содержимое), text (описание для
     * PDSC), pdscs, acta (текст действия, модификации, общий объект — так же вызывают
     * callback-объекты), acttext и mods (части для объектов, которым он общий), use(t) (применения;
     * 1 — был ответ), nouse, item (предмет в инвентаре), take (переносимый: 1 или id общего
     * объекта), menu (1 — щелчок в инвентаре — действие, 2 — только подпись)
     */
    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        final String ls = LINE_SEPARATOR;
        final String id = objBlocks.getObjId();
        final String common = objBlocks.getCommonObjId();
        final boolean hasCommon = StringHelper.notEmpty(common);
        final String disp = objLabel(objBlocks);
        if (StringHelper.notEmpty(objBlocks.getObjName())) {
            m_objNames.put(objBlocks.getObjName(), id);
        }
        m_takable.put(id, objBlocks.isTakable());
        // подпись в сцене (PlayerEngine.sceneLabel, как в экспортах QSP, URQ и AXMA): disp, иначе
        // первая строка текста действия, иначе имя
        final String firstAct = firstActLine(objBlocks.getObjActStart());
        final String label = StringHelper.notEmpty(objBlocks.getObjDisp()) || firstAct.isEmpty() ? js(disp) : jsString(firstAct);
        String text = objBlocks.getObjText().replace(EMPTY_LINK_LABEL, label);
        if (objBlocks.isGraphical()) {
            // графический объект (VN) щёлкают по картинке, а картинок здесь нет: с действием —
            // ссылкой его текст (одна строка) или подпись; без действия — просто текст
            final String link = "nlbf.lnk(" + label + ", nlb.me)";
            if (objBlocks.hasAction() && !text.contains("nlbf.lnk(")) {
                text = text.isEmpty() ? link
                        : !text.contains("<br>") && !text.contains("\\u003cbr>") ? "nlbf.lnk(" + text + ", nlb.me)"
                        : link + " + \"<br>\" + " + text;
            } else if (text.isEmpty() && StringHelper.notEmpty(objBlocks.getObjDisp())) {
                text = js(objBlocks.getObjDisp());
            }
        }
        if (text.isEmpty() && objBlocks.isImageInScene() && !objBlocks.isGraphical()) {
            // объект без описания, но с картинкой (фон боя, рамка карты во «Фронтире») — одна
            // картинка, как в плеере; с действием — ссылкой
            text = objBlocks.hasAction() ? "nlbf.lnk(nlbf.oimg(nlb.me), nlb.me)" : "nlbf.oimg(nlb.me)";
        }
        final String textF = objBlocks.getObjTextF().replace(EMPTY_LINK_LABEL, label);
        final boolean constrained = StringHelper.notEmpty(objBlocks.getObjConstraint());
        final boolean pic = objBlocks.isGraphical() || objBlocks.isImageInScene();
        // AXMA JS склеивает строки абзаца через «;»: каждая часть — отдельный оператор в одну строку
        final String o = "nlbo[" + jsString(id) + "]";
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (constrained) {
            parts.add("on = function () { return (" + objBlocks.getObjConstraint() + "); }");
        }
        StringBuilder dsc = new StringBuilder("dsc = function () { if (!nlbf.on(nlb.me)) { return; } ");
        if (pic) {
            // объект с картинкой в нескольких контейнерах (держатели карт «Фронтира») — на экране
            // один раз, вместе с содержимым, как сцена плеера (PlayerEngine.sceneObjects)
            dsc.append("if (nlb.seen[nlb.me] == nlb.gen) { return; } nlb.seen[nlb.me] = nlb.gen; ");
        }
        if (!text.isEmpty()) {
            dsc.append("print(").append(text).append(" + \"<br>\"); ");
        }
        parts.add(dsc.append("nlbf.show(nlb.me); }").toString());
        if (!textF.isEmpty()) {
            parts.add("text = function () { return " + textF + "; }");
        }
        if (objBlocks.isSuppressDsc()) {
            // шаг PDSCS: объект с suppressDsc — его содержимое, затем он сам
            parts.add("pdscs = function () { nlbf.pdscs(nlb.me); nlbf.pdsc(nlb.me); }");
        }
        StringBuilder acta = new StringBuilder("acta = function () { ").append(objBlocks.getObjActStart());
        if (hasCommon && StringHelper.isEmpty(objBlocks.getObjActStart())) {
            // общий объект (objActA): его текст — если у объекта своего нет, затем его модификации
            acta.append("nlbf.run(nlb.me, ").append(jsString(common)).append(", \"acttext\"); ");
        }
        acta.append(objBlocks.getObjModifications()).append(objBlocks.getObjVariable());
        if (hasCommon) {
            acta.append("nlbf.run(nlb.me, ").append(jsString(common)).append(", \"mods\"); ");
        }
        parts.add(acta.append("}").toString());
        parts.add("acttext = function () { " + objBlocks.getObjActStart() + "}");
        parts.add("mods = function () { " + objBlocks.getObjModifications() + objBlocks.getObjVariable() + "}");
        // цель применения — по прототипу (у клона — исходный объект)
        StringBuilder useCode = new StringBuilder("use = function (t) { var tp = nlbf.P(t), p = 0; ");
        for (UseBuildingBlocks use : objBlocks.getUseBuildingBlocks()) {
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean useConstrained = StringHelper.notEmpty(use.getUseConstraint());
            useCode.append("if (tp == ").append(use.getUseTarget()).append(") { ");
            if (useConstrained) {
                useCode.append("if (").append(use.getUseConstraint()).append(") { ");
            }
            if (hasSuccess) {
                useCode.append("nlbf.say(").append(js(use.getUseSuccessText())).append("); p = 1; ");
            }
            useCode.append(use.getUseModifications()).append(use.getUseVariable());
            if (useConstrained) {
                useCode.append("}");
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    useCode.append(" else { nlbf.say(").append(js(use.getUseFailureText())).append("); p = 1; }");
                }
                useCode.append(" ");
            }
            useCode.append("} ");
        }
        if (hasCommon) {
            useCode.append("if (!p) { p = nlbf.run(nlb.me, ").append(jsString(common)).append(", \"use\", t); } ");
        }
        parts.add(useCode.append("return p; }").toString());
        String nouse = objBlocks.getObjNouse();
        parts.add("nouse = function () { return " + (StringHelper.notEmpty(nouse) ? js(nouse) : jsString(m_gameNouseText)) + "; }");
        if (objBlocks.isVisibleInInventory()) {
            // предмет без подписи и картинки лежит в инвентаре невидимым (как в плеере и INSTEAD)
            // картинка предмета в инвентаре — перед подписью
            final boolean invImage = objBlocks.isImageInInventory() && !objBlocks.isGraphical() && !objBlocks.getObjImages().isEmpty();
            parts.add("item = function () { var d = " + (invImage ? "nlbf.oimg(nlb.me) + " : "") + js(disp) + "; if (nlb.sel == nlb.me) { d = \"» \" + d; } print(nlbf.btn(d, nlb.me)); }");
        }
        if (objBlocks.isTakable()) {
            // decorateObjTak: взять общий объект, если он переносимый, иначе этот (экземпляр)
            boolean takeCommon = hasCommon && Boolean.TRUE.equals(m_takable.get(common));
            parts.add("take = " + (takeCommon ? jsString(common) : "1"));
        }
        if (objBlocks.getObjType() == ObjType.MENU || objBlocks.getObjType() == ObjType.GMENU) {
            parts.add("menu = 1");
        } else if (objBlocks.getObjType() == ObjType.STAT) {
            parts.add("menu = 2");
        }
        StringBuilder sb = new StringBuilder(o).append(" = {};").append(ls);
        for (String part : parts) {
            sb.append(o).append(".").append(part.replace("\r", "").replace("\n", " ")).append(";").append(ls);
        }
        // картинка (у графического объекта VN её щёлкают, здесь её нет) и звук объекта
        sb.append(mediaCode(id, objBlocks.isGraphical() ? java.util.Collections.emptyList() : objBlocks.getObjImages(),
                objBlocks.getObjSounds(), false));
        m_objCode.append(sb);
        return "";
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjName(String name, String id) {
        return name == null ? "" : name;
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return dispText == null ? "" : dispText.replace("<br>", " ").trim();
    }

    /** Описание объекта — выражение JavaScript: часть в {фигурных скобках} — ссылка на действие */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText) || objText.trim().isEmpty()) {
            return "";
        }
        // картинка объекта в сцене — в начале ссылки, как imgv в INSTEAD ({картинка метка})
        final String image = imageEnabled && !isGraphicalObj ? "nlbf.oimg(nlb.me) + " : "";
        if (EMPTY_LINK.matcher(objText).find()) {
            objText = expandEmptyLink(objText);
        }
        Matcher m = Pattern.compile("\\{([^}]*)\\}").matcher(objText);
        StringBuilder result = new StringBuilder();
        int at = 0;
        while (m.find()) {
            if (m.start() > at) {
                result.append(result.length() > 0 ? " + " : "").append(js(objText.substring(at, m.start())));
            }
            // метка, ставшая пустой, — подпись объекта (generateObjText)
            String label = m.group(1).trim().isEmpty() ? EMPTY_LINK_LABEL : js(m.group(1));
            result.append(result.length() > 0 ? " + " : "").append("nlbf.lnk(").append(image).append(label).append(", nlb.me)");
            at = m.end();
        }
        if (at < objText.length()) {
            result.append(result.length() > 0 ? " + " : "").append(js(objText.substring(at)));
        }
        return result.toString();
    }

    private static final Pattern EMPTY_LINK = Pattern.compile("\\{\\s*\\}");

    /**
     * Пустая метка {} — картинка объекта в INSTEAD; без неё, как в экспорте QSP и в плеере, ссылкой
     * становится первая строка текста с видимыми знаками («{} Офицер по безопасности» во «Фронтире»),
     * а если её нет — подпись объекта
     */
    static String expandEmptyLink(String objText) {
        String rest = EMPTY_LINK.matcher(objText).replaceAll("");
        String[] parts = rest.split("(?<=<br>)", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            String body = part.endsWith("<br>") ? part.substring(0, part.length() - 4) : part;
            if (!body.replaceAll("<[^>]*>", "").isBlank()) {
                String label = body.strip();
                int start = body.indexOf(label);
                parts[i] = body.substring(0, start) + "{" + label + "}" + body.substring(start + label.length()) + part.substring(body.length());
                return String.join("", parts);
            }
        }
        return rest + "{}";
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        return StringHelper.isEmpty(actTextExpanded) || actTextExpanded.trim().isEmpty()
                ? "" : "nlbf.say(" + js(actTextExpanded.trim()) + "); ";
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return nouseTextExpanded == null ? "" : nouseTextExpanded.trim();
    }

    @Override
    protected String decorateObjConstraint(String constraintValue) {
        return constraintValue;
    }

    @Override
    protected String decorateObjModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return containedObjId;
    }

    @Override
    protected String decorateUseTarget(String targetId) {
        return jsString(targetId);
    }

    @Override
    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateUseVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    // --- страница

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        final String pageId = pageBlocks.getPageId();
        StringBuilder sb = new StringBuilder();
        sb.append(pageBlocks.getPageLabel());
        // модификации входа — до текста; повторный показ (nlb.redraw — после действия) — без них,
        // но с тиком таймера страницы
        sb.append(NEED_LIB);
        // goto() прерывает код объектов без возврата: стек экземпляров — заново
        sb.append("nlb.ms = []; nlb.me = \"\"; ");
        sb.append("nlb.page = ").append(jsString(pageId)).append(";").append(LINE_SEPARATOR);
        sb.append("if (nlb.redraw) { nlb.redraw = 0; ").append(pageBlocks.isHasPageTimer() ? pageBlocks.getPageTimerVariable() : "")
                .append("} else { ").append(pageBlocks.getPageModifications()).append(pageBlocks.getPageVariable());
        if (pageBlocks.isHasPageTimer()) {
            sb.append(pageBlocks.getPageTimerVariableInit());
        }
        if (!pageBlocks.getPageSounds().isEmpty()) {
            // звук страницы — при входе, после её модификаций (initf в INSTEAD)
            sb.append("nlbf.snd(").append(jsString(pageId)).append("); ");
            m_objCode.append(mediaCode(pageId, java.util.Collections.emptyList(), pageBlocks.getPageSounds(), true));
        }
        sb.append("}").append(LINE_SEPARATOR);
        sb.append("nlbf.turn();").append(LINE_SEPARATOR);
        sb.append(pageBlocks.getPageCaption());
        String text = pageBlocks.getPageTextStart();
        boolean hasText = StringHelper.notEmpty(text) && !text.trim().isEmpty();
        // ответ на действие, после которого перешли сюда, — перед текстом страницы; объекты
        // страницы, ответ, инвентарь. Страница без текста, которую сразу покидают по автоссылке
        // (goto() стирает экран), их не выводит — ответ остаётся для следующей страницы
        // картинка страницы — при каждом показе (тег мог измениться), строкой перед текстом
        final List<MediaChoice> images = pageBlocks.getPageImages();
        final String pre = (images.isEmpty() ? "" : "nlbf.pimg(nlbf.pick(" + jsString(pageId) + ", " + imageArray(images) + ")); ")
                + "nlbf.before(" + (hasText ? js(text.trim()) : "\"\"") + "); ";
        final String post = "nlb.gen++; nlbf.show(" + jsString(pageId) + "); nlbf.reply(); nlbf.inv();" + LINE_SEPARATOR;
        final String body = pre + post;
        final boolean bodyFirst = pageBlocks.isHasPageText() || pageBlocks.getLinksBuildingBlocks().stream().noneMatch(LinkBuildingBlocks::isAuto);
        if (bodyFirst) {
            sb.append(pre).append(LINE_SEPARATOR);
        }
        if (hasText) {
            // print не переводит строку: текст — абзацем перед ссылками
            sb.append("print(").append(js(text.trim())).append(" + \"<br><br>\");").append(LINE_SEPARATOR);
        }
        if (bodyFirst) {
            sb.append(post);
        }
        List<LinkBuildingBlocks> links = pageBlocks.getLinksBuildingBlocks();
        sb.append(timerWait(pageBlocks));
        // автоссылки — до обычных и вместо них: первая с выполненным условием
        boolean open = false;
        boolean unconditional = false;
        for (LinkBuildingBlocks link : links) {
            if (!link.isAuto()) {
                continue;
            }
            String constraint = autoConstraint(link);
            if (StringHelper.notEmpty(constraint)) {
                sb.append(open ? "} else if (" : "if (").append(constraint).append(") { ");
                open = true;
            } else if (open) {
                sb.append("} else { ");
            }
            sb.append(autoTransition(pageBlocks, link));
            if (StringHelper.isEmpty(constraint)) {
                unconditional = true;
                break;
            }
        }
        if (!unconditional) {
            if (open) {
                sb.append("} else {").append(LINE_SEPARATOR);
            }
            if (!bodyFirst) {
                sb.append(body);
            }
            for (LinkBuildingBlocks link : links) {
                if (link.isAuto()) {
                    continue;
                }
                boolean constrained = StringHelper.notEmpty(link.getLinkConstraint());
                if (constrained) {
                    sb.append("if (").append(link.getLinkConstraint()).append(") { ");
                }
                sb.append(link.getLinkStart());
                if (constrained) {
                    sb.append("}");
                    if (link.hasShownAltText()) {
                        // альтернативный текст недоступной ссылки (#135)
                        sb.append(" else { print(").append(js(link.getLinkAltText())).append(" + \"<br>\"); }");
                    }
                    sb.append(LINE_SEPARATOR);
                }
            }
        }
        if (open) {
            sb.append("}").append(LINE_SEPARATOR);
        }
        sb.append(LINE_SEPARATOR);
        return sb.toString();
    }

    /** Условие автоссылки; у «needs action» — и нужное число действий игрока */
    private String autoConstraint(LinkBuildingBlocks link) {
        String constraint = link.getLinkConstraint();
        if (link.isNeedsAction()) {
            String hasAction = "(nlb.ac >= " + getNeedsActionCount() + ")";
            constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") && " + hasAction;
        }
        return constraint;
    }

    /** Автоссылка: на странице с текстом — кнопкой (goto стёр бы текст), без текста — сразу goto */
    private String autoTransition(PageBuildingBlocks pageBlocks, LinkBuildingBlocks link) {
        if (!pageBlocks.isHasPageText()) {
            return "goto(" + jsString(link.getLinkId()) + "); ";
        }
        return decorateLinkStart(link.getLinkId(), link.getLinkText(), true, true, false, 0, link.getTheme());
    }

    /**
     * Выбирать нечего, а автоссылки ждут таймера — время проматывается тик за тиком, пока условие
     * какой-нибудь автоссылки не станет истинным (PlayerEngine.autoWaitTicks)
     */
    private String timerWait(PageBuildingBlocks pageBlocks) {
        if (!pageBlocks.isHasPageTimer() || pageBlocks.getTimerWaitTicks() <= 0) {
            return "";
        }
        StringBuilder autos = new StringBuilder();
        StringBuilder any = new StringBuilder();
        for (LinkBuildingBlocks link : pageBlocks.getLinksBuildingBlocks()) {
            if (link.isAuto()) {
                String constraint = autoConstraint(link);
                if (StringHelper.isEmpty(constraint)) {
                    return "";
                }
                autos.append(autos.length() > 0 ? " || " : "").append("(").append(constraint).append(")");
            } else if (StringHelper.isEmpty(link.getLinkConstraint())) {
                return "";
            } else {
                any.append(any.length() > 0 ? " || " : "").append("(").append(link.getLinkConstraint()).append(")");
            }
        }
        if (autos.length() == 0) {
            return "";
        }
        String loop = "for (var nlb_t = 0; nlb_t < " + pageBlocks.getTimerWaitTicks() + " && !(" + autos + "); nlb_t++) { "
                + pageBlocks.getPageTimerVariable() + "}";
        return (any.length() > 0 ? "if (!(" + any + ")) { " + loop + " }" : loop) + LINE_SEPARATOR;
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder sb = new StringBuilder();
        for (LinkBuildingBlocks link : pageBlocks.getLinksBuildingBlocks()) {
            sb.append(link.getLinkLabel());
            sb.append(NEED_LIB).append("nlb.ms = []; nlb.me = \"\"; nlb.rp = nlb.page; ");
            if (!link.isTechnical()) {
                // переход по обычной ссылке — действие игрока (needs action, COUNTGET); он же
                // снимает выбор предмета для применения
                sb.append(count("walk")).append("nlb.sel = \"\"; ");
            } else if (link.isAuto() && link.isNeedsAction()) {
                sb.append(decorateCountResetOperation());  // has_action() сбрасывает счётчики
            }
            sb.append(link.getLinkModifications());
            sb.append(link.getLinkVariable());
            sb.append(link.getLinkVisitStateVariable());
            sb.append(link.getLinkGoTo());
            sb.append(LINE_SEPARATOR);
        }
        return sb.toString();
    }

    private static String count(String type) {
        return "nlb.ac++; nlb.cnt." + type + "++; ";
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        String label = passage(labelText);
        if (pageNumber == 1) {
            // Start — первый абзац игры: начальное состояние и переход на первую страницу
            return passage("Start") + "display(\"nlb_init\"); display(\"nlb_lib\");" + LINE_SEPARATOR + "goto(" + jsString(labelText) + ");"
                    + LINE_SEPARATOR + LINE_SEPARATOR + label;
        }
        return label;
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return "print(" + jsString("<b>" + html(caption.toUpperCase()) + "</b><br>") + ");" + LINE_SEPARATOR;
        }
        return "";
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return "";
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "";
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "";
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    // --- ссылки

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return passage(linkId);
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "";
    }

    /** Кнопка ссылки: подпись (с переменными — значениями; пустая — подпись по умолчанию) -> абзац ссылки */
    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        String label = linkText == null || linkText.trim().isEmpty() ? defaultLinkText() : linkText;
        return "print(nlbf.a(" + js(label) + ", " + jsString(linkId) + ", undefined, 1)); " + LINE_SEPARATOR;
    }

    private String defaultLinkText() {
        String text = Link.DEFAULT_TEXT.get(m_language);
        return StringHelper.isEmpty(text) ? Link.DEFAULT_TEXT.get("en") : text;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkSource, int sourcePageNumber,
                                      String linkTarget, int targetPageNumber, boolean isTechnical, Theme theme) {
        return "goto(" + jsString(linkTarget) + ");";
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return variable(linkVisitStateVariable) + " = true; ";
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return variable(variableName) + " = true; ";
    }

    @Override
    protected String decoratePageTimerVariableInit(String variableName) {
        return StringHelper.isEmpty(variableName) ? "" : variable(variableName) + " = 0; ";
    }

    @Override
    protected String decoratePageTimerVariable(String variableName) {
        return StringHelper.isEmpty(variableName) ? "" : variable(variableName) + " = (" + variable(variableName) + " || 0) + 1; ";
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        return "";
    }

    @Override
    protected String decoratePageNotes(String notes) {
        return "";
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, boolean imageBackground, Theme theme) {
        return "";  // медиа — следующий этап
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        return "";
    }

    // --- выражения и управление

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + "; ";
    }

    @Override
    protected String decorateWhile(String constraint) {
        return "while (" + constraint + ") { ";
    }

    @Override
    protected String decorateIf(String constraint) {
        return "if (" + constraint + ") { ";
    }

    @Override
    protected String decorateElse() {
        return "} else { ";
    }

    @Override
    protected String decorateElseIf(String constraint) {
        return "} else if (" + constraint + ") { ";
    }

    @Override
    protected String decorateEnd() {
        return "} ";
    }

    @Override
    protected String decorateReturn(String returnValue) {
        return "";
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n; random(min, max) AXMA JS — включительно
        return variableName + " = random(1, " + maxValue + "); ";
    }

    /** GOTO: goto() отменяет остальной код абзаца — переход из модификаций ссылки важнее её цели */
    @Override
    protected String decorateGoToOperation(String locationId) {
        return "goto(" + jsString(locationId) + "); ";
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                sum.append(sum.length() > 0 ? " + " : "").append("(nlb.cnt.").append(COUNT_TYPES[i])
                        .append(" - nlb.cntr.").append(COUNT_TYPES[i]).append(")");
            }
        }
        return variableName + " = " + (sum.length() > 0 ? sum.toString() : "0") + "; ";
    }

    @Override
    protected String decorateCountResetOperation() {
        StringBuilder sb = new StringBuilder("nlb.ac = 0; ");
        for (String type : COUNT_TYPES) {
            sb.append("nlb.cntr.").append(type).append(" = nlb.cnt.").append(type).append("; ");
        }
        return sb.toString();
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // значение — в ответ на действие, без перевода строки
        return "nlb.reply += nlb.str(" + variableName + "); ";
    }

    @Override
    protected String decorateTrue() {
        return "true";
    }

    @Override
    protected String decorateFalse() {
        return "false";
    }

    @Override
    protected String decorateEq() {
        return "==";
    }

    @Override
    protected String decorateNEq() {
        return "!=";
    }

    @Override
    protected String decorateGt() {
        return ">";
    }

    @Override
    protected String decorateGte() {
        return ">=";
    }

    @Override
    protected String decorateLt() {
        return "<";
    }

    @Override
    protected String decorateLte() {
        return "<=";
    }

    @Override
    protected String decorateNot() {
        return "!";
    }

    @Override
    protected String decorateOr() {
        return "||";
    }

    @Override
    protected String decorateAnd() {
        return "&&";
    }

    @Override
    protected String decorateExistence(String decoratedVariable) {
        return "";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return variable(constraintVar);
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return variable(constraintVar);
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return variable(constraintVar);
    }

    // --- объекты, инвентарь, контейнеры, списки, теги

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        return "nlb.tags[" + objRef(objId, variable) + "] = " + jsString(tag) + "; ";
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        String t = "nlb.tags[" + objRef(objId, objVariableName) + "]";
        return resultingVariable + " = " + t + " !== undefined ? " + t + " : \"\"; ";
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        return "if (nlbf.has(" + objRef(objId, objVar) + ")) { ";
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        return variableName + " = nlbf.has(" + objRef(objId, objVar) + "); ";
    }

    /** Новый экземпляр (PlayerContext.cloneInstance); без объекта — сам («s») */
    @Override
    protected String decorateCloneOperation(String variableName, String objId, String objVar) {
        return variableName + " = nlbf.clone(" + objRef(objId, objVar) + "); ";
    }

    /** Последний владелец объекта (страница или объект); нигде — false */
    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        return variableName + " = nlb.own[" + objRef(objId, objVar) + "] || false; ";
    }

    /** Прототип экземпляра (у клона — исходный объект) */
    @Override
    protected String decorateGetIdOperation(String variableName, String objId, String objVar) {
        return variableName + " = nlbf.P(" + objRef(objId, objVar) + "); ";
    }

    @Override
    protected String decorateDelObj(String destinationId, String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        String ref = objRef(objectId, objectVar);
        if (destinationName != null) {
            // место — список: nlb:rmv(список, obj) — первый того же прототипа — и obj.container = nil
            return "nlbf.lrmv(" + listKey(destinationName) + ", " + ref + "); nlb.own[" + ref + "] = \"\"; ";
        }
        // из получателя, без него — из текущей страницы (here() в INSTEAD); в других местах остаётся
        return "nlbf.del(" + ref + ", " + (destinationId != null ? jsString(destinationId) : "nlb.page") + "); ";
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "nlbf.del(" + objRef(objectId, objectVar) + ", \"" + INVENTORY + "\"); ";
    }

    /** В получатель, без него — в себя; из прежних мест не убирается, уже лежащий без unique — клоном */
    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        String destination = destinationId != null ? jsString(destinationId) : selfContainer();
        return "nlbf.add(" + objRef(objectId, objectVar) + ", " + destination + ", " + !unique + "); ";
    }

    /** take: из текущей страницы — в инвентарь; уже в инвентаре — его клон */
    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "nlbf.addinv(" + objRef(objectId, objectVar) + "); ";
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        String container = destinationId != null ? jsString(destinationId) : selfContainer();
        return "nlbf.addall(" + listKey(sourceListVariableName) + ", "
                + (destinationListVariableName != null ? listKey(destinationListVariableName) : "\"\"") + ", " + container + ", " + !unique + "); ";
    }

    /** OBJS: содержимое контейнера — в начало списка (push каждого — обратный порядок) */
    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return "nlbf.objs(" + (srcObjId != null ? jsString(srcObjId) : objectVar) + ", " + listKey(listVariableName) + "); ";
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return "nlbf.list(" + listKey(listVariableName) + ").unshift(" + objRef(null, null) + "); ";
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // ww — объект, к которому применяют (в коде применения — t); вне применения — ничего
        return inUseLink() ? "nlbf.list(" + listKey(listVariableName) + ").unshift(t); " : "";
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return "nlbf.list(" + listKey(listVariableName) + ").unshift(" + (objectId != null ? jsString(objectId) : objectVar) + "); ";
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        return variableName + " = nlbf.pop(" + listKey(listVariableName) + ", 0); ";
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return "nlbf.list(" + listKey(listVariableName) + ").push(" + objRef(null, null) + "); ";
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return "nlbf.list(" + listKey(listVariableName) + ").push(" + (objectId != null ? jsString(objectId) : objectVar) + "); ";
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        return variableName + " = nlbf.pop(" + listKey(listVariableName) + ", 1); ";
    }

    /** CLEAR: содержимое объекта или страницы — никуда; список — пуст (nlb:clear) */
    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "nlbf.clear(" + jsString(destinationId) + "); ";
        } else if (destinationVar != null) {
            return "delete nlb.lists[" + listKey(destinationVar) + "]; ";
        }
        return "nlbf.clear(nlb.page); ";
    }

    @Override
    protected String decorateClearInvOperation() {
        return "nlbf.clear(\"" + INVENTORY + "\"); ";
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        String l = "nlb.lists[" + listKey(listVariableName) + "]";
        return variableName + " = " + l + " ? " + l + ".length : 0; ";
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return "nlbf.shuffle(" + listKey(listVariableName) + "); ";
    }

    /** PDSC: описание объекта (по имени), если он включён */
    @Override
    protected String decoratePDscOperation(String objVariableName) {
        return "nlbf.pdsc(nlbf.names[" + jsString(rawName(objVariableName)) + "]); ";
    }

    /** PDSCS: описания объектов с suppressDsc внутри объекта, вложенные — раньше своего контейнера */
    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        return "nlbf.pdscs(" + (objId != null ? jsString(objId) : objVar) + "); ";
    }

    /** ACT: действие объекта (текст, модификации; переносимый — в инвентарь) */
    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        return "nlbf.act(" + objRef(actingObjId, actingObjVariable) + "); ";
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        return todo("ACTT");
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        return todo("ACTF");
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        return todo("DSC");
    }

    /** USE: применение объекта к цели (к каждому элементу, если цель — список); без nouse */
    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        String source = sourceId != null ? jsString(sourceId) : sourceVariable;
        if (targetId != null) {
            return "nlbf.call(" + source + ", \"use\", " + jsString(targetId) + "); ";
        }
        return "nlbf.useOp(" + source + ", " + listKey(targetVariable) + ", " + targetVariable + "); ";
    }

    // --- звук и достижения

    @Override
    protected String decorateSSndOperation() {
        return "nlbf.snd(" + objRef(null, null) + "); ";
    }

    @Override
    protected String decorateWSndOperation() {
        // ww — объект, к которому применяют (в коде применения — t); вне применения — ничего
        return inUseLink() ? "nlbf.snd(t); " : "";
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        return "nlbf.snd(" + objRef(objectId, objectVar) + "); ";
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return "nlbf.achmax(" + jsString(achievementName) + ", " + max + "); ";
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        return "nlbf.achieve(" + jsString(achievementName) + ", " + jsString(modificationId) + "); ";
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return variableName + " = nlbf.achn(" + jsString(achievementName) + "); ";
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        return todo("SNAPSHOT");
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        return todo("OPENURL");
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        return todo("WINGEOM");
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        return todo("INVGEOM");
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        return todo("WINCOLOR");
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        return todo("INVCOLOR");
    }
}
