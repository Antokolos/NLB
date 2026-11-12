/**
 * @(#)JSIQ2ExportManager.java
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
 * Copyright (c) 2013 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export.xml;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.*;
import com.nlbhub.nlb.domain.export.xml.beans.jsiq2.*;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.JaxbMarshaller;
import com.nlbhub.nlb.util.StringHelper;

import java.util.Date;
import java.util.List;

/**
 * The JSIQ2ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/01/14
 */
public class JSIQ2ExportManager extends XMLExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    public JSIQ2ExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String getCDataSectionElements() {
        return "script text action cssdata css style";
    }

    @Override
    protected JaxbMarshaller createMarshaller() {
        return new JaxbMarshaller(
                Action.class,
                Article.class,
                Book.class,
                Metadata.class,
                Script.class,
                Css.class,
                CssData.class,
                Style.class
        );
    }

    @Override
    protected Object createRootObject(NLBBuildingBlocks nlbBlocks) {
        Book book = new Book();
        String lastSaveString = (new Date()).toString();
        boolean first = true;
        for (PageBuildingBlocks pageBuildingBlocks : nlbBlocks.getPagesBuildingBlocks()) {
            book.addArticle(
                    createArticle(pageBuildingBlocks, lastSaveString, first ? createRuntimeScript(nlbBlocks) : null)
            );
            first = false;
        }
        book.addArticle(createCharsheetArticle(lastSaveString));
        CssData cssData = new CssData();
        Css css = new Css();
        css.setName("default");
        css.setValue(".atril-game td{border: 2px solid gray; border-radius: 8px;}");
        Style style = new Style();
        style.setName("default");
        style.setValue("td {border: 2px solid gray; border-radius: 8px;}");
        cssData.setCss(css);
        cssData.setStyle(style);
        book.setCssData(cssData);
        return book;
    }

    /**
     * Статья страницы. Кроме текста страницы — тексты-места, которые заполняет общий код NLB
     * (см. RUNTIME): объекты сцены, ответ на действие и инвентарь.
     */
    private Article createArticle(PageBuildingBlocks pageBlocks, String lastSaveString, Script runtimeScript) {
        Article article = new Article();
        Metadata metadata = new Metadata();
        metadata.setLastSave(lastSaveString);
        article.setId(pageBlocks.getPageNumber());
        article.setMetadata(metadata);
        // картинка страницы — место, которое заполняет общий код NLB при показе (тег мог измениться)
        article.setText((pageBlocks.getPageImages().isEmpty() ? "" : "<div class=\"nlb_pic\"></div>") + html(pageBlocks.getPageTextStart()));
        if (runtimeScript != null) {
            article.addScript(runtimeScript);
        }
        boolean hasPageModifications = !StringHelper.isEmpty(pageBlocks.getPageModifications());
        if (hasPageModifications) {
            Script pageModificationsScript = new Script();
            pageModificationsScript.setType("pmod");
            pageModificationsScript.setValue(pageBlocks.getPageModifications());
            article.addScript(pageModificationsScript);
        }
        boolean hasPageVariable = !StringHelper.isEmpty(pageBlocks.getPageVariable());
        if (hasPageVariable) {
            Script pageVariableScript = new Script();
            pageVariableScript.setType("pvar");
            pageVariableScript.setValue(pageBlocks.getPageVariable());
            article.addScript(pageVariableScript);
        }
        boolean hasAutoLinks = false;
        StringBuilder autosStringBuilder = new StringBuilder();
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (int i = 0; i < linksBlocks.size(); i++) {
            final LinkBuildingBlocks linkBlocks = linksBlocks.get(i);
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            // Модификации и переменные ссылки — отдельными скриптами; их вызывает и действие (do),
            // и автоссылка (раньше автоссылка сразу показывала цель и их не выполняла, поэтому,
            // например, не ставилась отметка vl_…, по которой работает возврат из модуля)
            StringBuilder linkCalls = new StringBuilder();
            addLinkScript(article, "mod_" + i, linkBlocks.getLinkModifications(), linkCalls);
            addLinkScript(article, "var_" + i, linkBlocks.getLinkVariable(), linkCalls);
            addLinkScript(article, "lvs_var_" + i, linkBlocks.getLinkVisitStateVariable(), linkCalls);
            if (linkBlocks.isAuto()) {
                hasAutoLinks = true;
                String condition = hasConstraint ? "(" + linkBlocks.getLinkConstraint() + ")" : Constants.EMPTY_STRING;
                if (linkBlocks.isNeedsAction()) {
                    // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий с
                    // последнего сброса счётчиков (COUNTRST или такой же автоссылки), как в плеере
                    String hasAction = "(NLB.countGet(vars, 0) >= " + getNeedsActionCount() + ")";
                    condition = condition.isEmpty() ? hasAction : condition + " && " + hasAction;
                }
                if (!condition.isEmpty()) {
                    autosStringBuilder.append("if (").append(condition).append(") { ");
                }
                if (linkBlocks.isNeedsAction()) {
                    autosStringBuilder.append("NLB.countReset(vars); ");
                }
                // See decorateLinkGoTo() to understand why -1 is needed.
                // Функции go() в окружении скриптов jsIQ нет: переход — jsIQ.showArticle, после
                // того как плеер закончит показ текущей статьи; GOTO в модификациях ссылки важнее
                // её цели; return — первая подходящая автоссылка уводит со страницы
                autosStringBuilder.append(linkCalls)
                        .append("var to = (vars.").append(GOTO_VAR).append(" != null) ? vars.").append(GOTO_VAR)
                        .append(" : '").append(linkBlocks.getTargetPageNumber() - 1).append("'; vars.")
                        .append(GOTO_VAR).append(" = null; ")
                        .append(showArticleLater("to"))
                        .append("return true; ");
                if (!condition.isEmpty()) {
                    autosStringBuilder.append("} ");
                }
            } else {
                Action action = new Action();
                if (hasConstraint) {
                    Script actionConditionScript = new Script();
                    actionConditionScript.setType("condition_" + i);
                    actionConditionScript.setValue(
                            //"<![CDATA[return (" + linkBlocks.getLinkConstraint() + ");]]>"
                            "return (" + linkBlocks.getLinkConstraint() + ");"
                    );
                    article.addScript(actionConditionScript);
                    action.setIf("condition_" + i);
                    if (linkBlocks.hasShownAltText()) {
                        // альтернативный текст недоступной ссылки (#135): текст статьи, который
                        // плеер показывает, когда то же условие ложно (jsIQ.createText, ifnot)
                        Text altText = new Text(html(linkBlocks.getLinkAltText()));
                        altText.setIfnot("condition_" + i);
                        article.addText(altText);
                    }
                }
                action.setGoto(linkBlocks.getLinkGoTo());
                action.setValue(html(linkBlocks.getLinkStart()));
                // Переход по ссылке: ответ на прошлое действие и выбранный предмет сбрасываются;
                // обычная (не техническая) ссылка — действие игрока (для автоссылок «needs action»
                // и COUNTGET). GOTO в модификациях ссылки (NLB.go) меняет цель самого действия:
                // env.from — нажатое действие, плеер переходит по его goto после скрипта do
                linkCalls.insert(0, "NLB.walk(vars, " + !linkBlocks.isTechnical() + "); ");
                linkCalls.append("if (vars.").append(GOTO_VAR).append(" != null) { env.from.goto = vars.")
                        .append(GOTO_VAR).append("; vars.").append(GOTO_VAR).append(" = null; } ");
                Script actionDoScript = new Script();
                actionDoScript.setType("do_" + i);
                actionDoScript.setValue(linkCalls.toString());
                article.addScript(actionDoScript);
                action.setDo("do_" + i);
                article.addAction(action);
            }
        }
        if (hasAutoLinks) {
            Script autoLinkScript = new Script();
            autoLinkScript.setType("autos");
            autoLinkScript.setValue(autosStringBuilder.append("return false;").toString());
            article.addScript(autoLinkScript);
        }
        // Модификации страницы — в preload: плеер выполняет его до вывода текста и действий,
        // поэтому условия ссылок и текстов видят уже изменённые переменные (как в INSTEAD).
        // Действие с объектом показывает статью заново (NLB.click, vars.nlb_redraw) — уже без
        // модификаций входа. nlbInit — общий код NLB (глобальный скрипт первой статьи).
        // Таймер страницы (tvarid): обнуляется при входе после модификаций (initf), +1 за ход
        // игрока на странице — повторный показ после действия; переход на страницу — не тик
        boolean hasTimer = pageBlocks.isHasPageTimer();
        Script preload = new Script();
        preload.setType("preload");
        preload.setValue(
                "nlbInit(); if (vars." + REDRAW_VAR + ") { vars." + REDRAW_VAR + " = false; "
                        + (hasTimer ? pageBlocks.getPageTimerVariable() : Constants.EMPTY_STRING)
                        + "} else { "
                        + (hasPageModifications ? "pmod(); " : Constants.EMPTY_STRING)
                        + (hasPageVariable ? "pvar(); " : Constants.EMPTY_STRING)
                        + (hasTimer ? pageBlocks.getPageTimerVariableInit() : Constants.EMPTY_STRING)
                        // initf(): звук страницы — при входе, после модификаций
                        + (pageBlocks.getPageSounds().isEmpty() ? Constants.EMPTY_STRING : "NLB.snd(vars, '" + pageBlocks.getPageId() + "'); ")
                        + "}"
        );
        article.addScript(preload);
        // Переход (GOTO в модификациях, автоссылки) — в onload, после показа статьи: так текст
        // страницы, которую сразу покидают, успевает появиться, и не начинается показ новой
        // статьи посреди показа текущей. Автоссылки проверяются и после действий с объектами.
        // Перед ними — конец хода (NLB.turn): callback-объекты, если игрок с прошлого хода что-то
        // сделал (update_action_count в INSTEAD)
        Script onLoadScript = new Script();
        onLoadScript.setType("onload");
        onLoadScript.setValue(
                "NLB.turn(vars); if (vars." + GOTO_VAR + " != null) { var to = vars." + GOTO_VAR + "; vars." + GOTO_VAR
                        + " = null; " + showArticleLater("to") + "return; } "
                        + (hasAutoLinks ? "if (autos()) { return; } " : Constants.EMPTY_STRING)
                        + timerWait(pageBlocks)
        );
        article.addScript(onLoadScript);
        article.addText(new Text("<div class=\"nlb_scene\"></div>"));
        article.addText(new Text("<div class=\"nlb_reply\"></div>"));
        article.addText(new Text("<div class=\"nlb_inv\"></div>"));
        return article;
    }

    /**
     * Выбирать нечего (ни одна обычная ссылка не доступна), а автоссылки ждут таймера — время
     * проматывается, как в плеере (PlayerEngine.autoWaitTicks): тик за тиком, пока не сработает
     * автоссылка
     */
    private String timerWait(PageBuildingBlocks pageBlocks) {
        if (!pageBlocks.isHasPageTimer() || pageBlocks.getTimerWaitTicks() <= 0) {
            return Constants.EMPTY_STRING;
        }
        boolean hasAuto = false;
        StringBuilder any = new StringBuilder();
        for (LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            if (linkBlocks.isAuto()) {
                hasAuto = true;
            } else if (StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                return Constants.EMPTY_STRING;
            } else {
                any.append(any.length() > 0 ? " || " : "").append("(").append(linkBlocks.getLinkConstraint()).append(")");
            }
        }
        if (!hasAuto) {
            return Constants.EMPTY_STRING;
        }
        return (any.length() > 0 ? "if (!(" + any + ")) " : Constants.EMPTY_STRING)
                + "{ for (var nlb_w = 0; nlb_w < " + pageBlocks.getTimerWaitTicks() + "; nlb_w++) { "
                + pageBlocks.getPageTimerVariable() + "if (autos()) { return; } } } ";
    }

    /** Переменная jsIQ, в которой модификации оставляют статью для перехода (GOTO) */
    private static final String GOTO_VAR = "nlb_goto";
    /** Переменная jsIQ: статья показывается заново после действия с объектом (без модификаций входа) */
    private static final String REDRAW_VAR = "nlb_redraw";
    /** Пустая метка {} в описании объекта */
    private static final java.util.regex.Pattern EMPTY_LINK = java.util.regex.Pattern.compile("\\{\\s*\\}");
    /** Подпись ссылки, которую заменит подпись объекта (createObjCode) */
    private static final String OBJ_LABEL_MARK = "\u0004";
    /** Метка переменной в тексте до вывода: в HTML — span, в строке JavaScript — значение */
    private static final String VAR_MARK = "\u0003";
    private static final java.util.regex.Pattern VAR_PATTERN =
            java.util.regex.Pattern.compile(VAR_MARK + "([^" + VAR_MARK + "]*)" + VAR_MARK);

    /** Скрипт статьи с кодом ссылки (если код есть) и его вызов в calls */
    private static void addLinkScript(Article article, String type, String code, StringBuilder calls) {
        if (!StringHelper.isEmpty(code)) {
            Script script = new Script();
            script.setType(type);
            script.setValue(code);
            article.addScript(script);
            calls.append(type).append("(); ");
        }
    }

    /** Показ статьи после того, как плеер закончит показ текущей */
    private static String showArticleLater(String articleIdExpression) {
        return "setTimeout(function() { jsIQ.showArticle(" + articleIdExpression + "); }, 0); ";
    }

    /** Номер статьи jsIQ для страницы: статьи нумеруются с 0 (см. decoratePageNumber) */
    private String getJsiqArticleId(String pageId) {
        int pageNumber = getExportPageNumber(pageId);
        return pageNumber > 0 ? Integer.toString(pageNumber - 1) : pageId;
    }

    /**
     * Текст для HTML статьи: переменная — span, который общий код NLB заполняет значением при
     * каждом показе (раньше в тексте оставалось «$имя$»)
     */
    private static String html(String text) {
        if (text == null) {
            return null;
        }
        return VAR_PATTERN.matcher(text).replaceAll("<span class=\"nlb_v\" data-v=\"$1\"></span>");
    }

    /** Текст как выражение JavaScript (строка в апострофах; переменные — значением во время игры) */
    private static String jsText(String text) {
        return "'" + VAR_PATTERN.matcher(jsString(text)).replaceAll("' + NLB.v(vars, '$1') + '") + "'";
    }

    private Article createCharsheetArticle(String lastSaveString) {
        Article article = new Article();
        Metadata metadata = new Metadata();
        metadata.setLastSave(lastSaveString);
        article.setId("charsheet");
        article.setMetadata(metadata);
        article.setText("CHARACTER SHEET<br/> <div class=\"nlb_inv\"></div>");
        return article;
    }

    /**
     * Общий код NLB — глобальный скрипт nlbInit первой статьи; preload каждой статьи вызывает его,
     * а он создаёт window.NLB один раз. В jsIQ нет объектов и инвентаря, поэтому они — здесь, как
     * в экспорте QSP: место каждого объекта — vars.nlb_in[id] (id страницы или объекта-контейнера,
     * NLB.INV — инвентарь, '' — нигде; в vars — значит, и в сохранениях плеера), объекты сцены —
     * ссылками в тексте статьи (каждое описание — с новой строки, как в INSTEAD), предметы
     * инвентаря — ссылками под ним, в порядке, в котором их брали (vars.nlb_inv_order). Щелчок (NLB.click): по
     * объекту — его действие (act), по предмету — применение к себе (inv) и выбор, затем по
     * объекту или другому предмету — применение (use), по тому же — отмена выбора. После действия
     * статья показывается заново (ответ — после текста и объектов), а GOTO уводит на новую. В
     * конце хода (onload) — callback-объекты: их действие, если игрок с прошлого хода что-то сделал. Тексты
     * с переменными заполняются при показе (обработчики on_text_render и on_action_render).
     */
    private static final String RUNTIME = String.join("\n",
            "if (window.NLB) { return; }",
            "var N = window.NLB = {};",
            "N.INV = 'nlb_inv';",
            "N.objs = {};",
            "N.printed = false;",
            "N.vars = function () { return jsIQ.createEnv('nlb').vars(); };",
            "N.state = function (vars) {",
            "    if (!vars.nlb_in) {",
            "        vars.nlb_in = {};",
            "        for (var id in N.start) { vars.nlb_in[id] = N.start[id]; if (N.start[id] === N.INV) { N.invOrder(vars).push(id); } }",
            "    }",
            "    return vars.nlb_in;",
            "};",
            "N.v = function (vars, name) { var v = vars[name]; return (v === undefined || v === null) ? '' : String(v); };",
            "N.say = function (vars, text) { vars.nlb_reply = (vars.nlb_reply || '') + text; };",
            "N.sayp = function (vars, text) {",
            "    if (!text) { return; }",
            "    if (vars.nlb_reply) { vars.nlb_reply += '<br/>'; }",
            "    N.say(vars, text);",
            "};",
            "N.go = function (vars, to) { vars.nlb_goto = String(to); };",
            "N.here = function () { var a = jsIQ.current_article; return a ? (N.pages[a.title] || '') : ''; };",
            "N.place = function (vars, id, where) {",
            "    if (!N.objs[id]) { return; }",
            "    var st = N.state(vars), inv = N.invOrder(vars), i = inv.indexOf(id);",
            "    if (where === N.INV && i < 0) { inv.push(id); }",
            "    if (where !== N.INV && i >= 0) { inv.splice(i, 1); }",
            "    st[id] = where;",
            "};",
            // Списки (nlb.lua: push/pop — начало, inject/eject — конец; опустевший список исчезает,
            // pop из пустого — false), теги объектов и страниц (без явного — тег по умолчанию)
            "N.list = function (vars, name) { if (!vars.nlb_lists) { vars.nlb_lists = {}; } if (!vars.nlb_lists[name]) { vars.nlb_lists[name] = []; } return vars.nlb_lists[name]; };",
            "N.lend = function (vars, name) { if (vars.nlb_lists && vars.nlb_lists[name] && !vars.nlb_lists[name].length) { delete vars.nlb_lists[name]; } };",
            "N.push = function (vars, name, v) { N.list(vars, name).unshift(v); };",
            "N.inject = function (vars, name, v) { N.list(vars, name).push(v); };",
            "N.pop = function (vars, name) { var l = N.list(vars, name), v = l.length ? l.shift() : false; N.lend(vars, name); return v; };",
            "N.eject = function (vars, name) { var l = N.list(vars, name), v = l.length ? l.pop() : false; N.lend(vars, name); return v; };",
            "N.size = function (vars, name) { var v = N.list(vars, name).length; N.lend(vars, name); return v; };",
            "N.lclear = function (vars, name) { if (vars.nlb_lists) { delete vars.nlb_lists[name]; } };",
            "N.rmv = function (vars, name, v) { var l = N.list(vars, name), i = l.indexOf(v); if (i >= 0) { l.splice(i, 1); } N.lend(vars, name); };",
            "N.shuffle = function (vars, name) {",
            "    var l = N.list(vars, name);",
            "    for (var i = l.length - 1; i > 0; i--) { var j = Math.floor(Math.random() * (i + 1)), t = l[i]; l[i] = l[j]; l[j] = t; }",
            "    N.lend(vars, name);",
            "};",
            "N.objsTo = function (vars, name, cont) { for (var i = 0; i < N.order.length; i++) { if (N.where(vars, N.order[i]) === cont) { N.push(vars, name, N.order[i]); } } };",
            "N.addAll = function (vars, src, cont, dst) {",
            "    var l = N.list(vars, src).slice(); N.lend(vars, src);",
            "    for (var i = 0; i < l.length; i++) { if (dst) { N.push(vars, dst, l[i]); } else { N.place(vars, String(l[i]), cont); } }",
            "};",
            "N.clear = function (vars, cont) { for (var i = 0; i < N.order.length; i++) { if (N.where(vars, N.order[i]) === cont) { N.place(vars, N.order[i], ''); } } };",
            "N.setTag = function (vars, id, tag) { if (!vars.nlb_tags) { vars.nlb_tags = {}; } vars.nlb_tags[id] = tag; };",
            "N.tag = function (vars, id) { var t = vars.nlb_tags && vars.nlb_tags[id]; return (t !== undefined && t !== null) ? t : (N.deftag[id] || ''); };",
            // Достижения (PlayerContext.achieve, nlb.lua setAchievement) — в localStorage под ключом
            // книги, между прохождениями сохраняются (как prefs в INSTEAD): «id:имя|место» — место
            // ACHIEVE засчитано, «n:имя» — число мест (ACHIEVED), «max:имя» — порог (ACHMAX), «got:имя»
            // — объявлено. Без localStorage (запрет браузера) — только на эту игру
            "N.achLoad = function () {",
            "    if (!N.achs) { try { N.achs = JSON.parse(window.localStorage.getItem(N.achKey) || '{}') || {}; } catch (e) { N.achs = {}; } }",
            "    return N.achs;",
            "};",
            "N.achSave = function () { try { window.localStorage.setItem(N.achKey, JSON.stringify(N.achs)); } catch (e) { } };",
            "N.achMax = function (name, max) { N.achLoad()['max:' + name] = max; N.achSave(); };",
            "N.ach = function (name) { return N.achLoad()['n:' + name] || 0; };",
            "N.achMark = function (a, name, id) { if (!a['id:' + name + '|' + id]) { a['id:' + name + '|' + id] = 1; a['n:' + name] = (a['n:' + name] || 0) + 1; } };",
            "N.achieve = function (vars, name, id) {",
            "    var a = N.achLoad(), m = a['max:' + name];",
            "    N.achMark(a, name, id);",
            "    if (!a['got:' + name] && (!m || a['n:' + name] >= m)) { a['got:' + name] = 1; N.sayp(vars, N.achText + name); }",
            "    if (N.perfect) {",
            "        var ok = true;",
            "        for (var i = 0; i < N.achNames.length; i++) {",
            "            var k = N.achNames[i];",
            "            if (k !== N.perfect && (a['n:' + k] || 0) < Math.max(a['max:' + k] || 0, 1)) { ok = false; }",
            "        }",
            "        if (ok) { N.achMark(a, N.perfect, id); if (!a['got:' + N.perfect]) { a['got:' + N.perfect] = 1; N.sayp(vars, N.achText + N.perfect); } }",
            "    }",
            "    N.achSave();",
            "};",
            // Картинки (ModificationInterpreter.selectImage, pic в INSTEAD): N.imgs[id] — [условие,
            // путь]; первая, чьё условие совпало с тегом (без условия — всегда). Звук (playSound,
            // snd): N.snds[id] — [условие, путь, вид]: без условия — всегда, подряд идущие с
            // условием — цепочка по тегу; вид: m — музыка (повторяется, тот же файл не
            // перезапускается), x — стоп музыки (void), s — эффект, v — фраза озвучки страницы
            // (фразы звучат по очереди, в порядке поля; новая страница со звуком прерывает очередь)
            "N.img = function (vars, id) {",
            "    var l = N.imgs[id] || [], t = N.tag(vars, id);",
            "    for (var i = 0; i < l.length; i++) { if (!l[i][0] || l[i][0] === t) { return l[i][1]; } }",
            "    return '';",
            "};",
            "N.imgTag = function (path) { return path ? '<img src=\"' + path + '\"/>' : ''; };",
            "N.play = function (path) { try { var a = new Audio(path); var p = a.play(); if (p && p.catch) { p.catch(function () {}); } return a; } catch (e) { return null; } };",
            "N.musicPlay = function (path) {",
            "    if (N.musicPath === path) { return; }",
            "    if (N.music) { N.music.pause(); N.music = null; }",
            "    N.musicPath = path;",
            "    if (path) { N.music = N.play(path); if (N.music) { N.music.loop = true; } }",
            "};",
            "N.voiceStop = function () { N.voiceQueue = []; if (N.voice) { N.voice.pause(); N.voice = null; } };",
            "N.voiceNext = function () {",
            "    N.voice = null;",
            "    var path = N.voiceQueue && N.voiceQueue.shift();",
            "    if (path) { N.voice = N.play(path); if (N.voice) { N.voice.onended = N.voiceNext; } else { N.voiceNext(); } }",
            "};",
            "N.snd = function (vars, id) {",
            "    var l = N.snds[id] || [], t = N.tag(vars, id), chain = false, voices = [];",
            "    for (var i = 0; i < l.length; i++) {",
            "        var c = l[i][0];",
            "        if (c) { if (chain || c !== t) { continue; } chain = true; } else { chain = false; }",
            "        var kind = l[i][2], path = l[i][1];",
            "        if (kind === 'x') { N.musicPlay(''); } else if (kind === 'm') { N.musicPlay(path); }",
            "        else if (kind === 'v') { voices.push(path); } else { N.play(path); }",
            "    }",
            "    if (voices.length) { N.voiceStop(); N.voiceQueue = voices; N.voiceNext(); }",
            "};",
            "N.invOrder = function (vars) { if (!vars.nlb_inv_order) { vars.nlb_inv_order = []; } return vars.nlb_inv_order; };",
            "N.where = function (vars, id) { return N.state(vars)[id] || ''; };",
            "N.has = function (vars, id) { return N.where(vars, id) === N.INV; };",
            "N.text = function (vars, id) { var o = N.objs[id]; return o ? o.text(vars) : ''; };",
            "N.disp = function (vars, id) { var o = N.objs[id]; return o ? o.disp(vars) : ''; };",
            "N.dsc = function (vars, id) {",
            "    var o = N.objs[id];",
            "    if (!o || (o.visible && !o.visible(vars))) { return ''; }",
            "    var text = o.text(vars), inner = N.showIn(vars, id);",
            // картинка в сцене — в начале ссылки на объект, как imgv в INSTEAD; объект без
            // описания — одна картинка (с действием — ссылкой)
            "    if (o.imgScene) {",
            "        var im = N.imgTag(N.img(vars, id));",
            "        if (!text) { text = (im && o.act) ? '<a href=\"#\" class=\"nlb_obj\" onclick=\"return NLB.click(\\'act\\', \\'' + id + '\\')\">' + im + '</a>' : im; }",
            "        else if (text.indexOf('class=\"nlb_obj\"') >= 0) { text = text.replace(/(<a [^>]*class=\"nlb_obj\"[^>]*>)/, function (m) { return m + im; }); }",
            "        else { text = im + text; }",
            "    }",
            "    return text + ((text && inner) ? '<br/>' : '') + inner;",
            "};",
            "N.showIn = function (vars, where) {",
            "    var st = N.state(vars), out = [];",
            "    for (var i = 0; i < N.order.length; i++) {",
            "        if (st[N.order[i]] === where) { var d = N.dsc(vars, N.order[i]); if (d) { out.push(d); } }",
            "    }",
            "    return out.join('<br/>');",
            "};",
            "N.inventory = function (vars) {",
            "    var inv = N.invOrder(vars), out = [];",
            "    for (var i = 0; i < inv.length; i++) {",
            "        var id = inv[i];",
            "        var d = N.disp(vars, id);",
            "        if (N.objs[id] && N.objs[id].imgInv) { d = N.imgTag(N.img(vars, id)) + d; }",
            "        if (vars.nlb_sel === id) { d = '<b>' + d + '</b>'; }",
            "        out.push('<a href=\"#\" class=\"nlb_item\" onclick=\"return NLB.click(\\'inv\\', \\'' + id + '\\')\">' + d + '</a>');",
            "    }",
            "    return out.length ? '<hr/>' + out.join('<br/>') : '';",
            "};",
            "N.count = function (vars, type) { vars['nlb_cnt_' + type] = (vars['nlb_cnt_' + type] || 0) + 1; };",
            "N.TYPES = ['inv', 'act', 'use', 'walk'];",
            "N.countGet = function (vars, mask) {",
            "    var sum = 0;",
            "    for (var i = 0; i < N.TYPES.length; i++) {",
            "        var t = N.TYPES[i];",
            "        if (mask === 0 || (mask & (1 << i))) { sum += (vars['nlb_cnt_' + t] || 0) - (vars['nlb_cntr_' + t] || 0); }",
            "    }",
            "    return sum;",
            "};",
            "N.countReset = function (vars) {",
            "    for (var i = 0; i < N.TYPES.length; i++) { var t = N.TYPES[i]; vars['nlb_cntr_' + t] = vars['nlb_cnt_' + t] || 0; }",
            "};",
            "N.walk = function (vars, counted) {",
            "    if (counted) { N.count(vars, 'walk'); }",
            "    vars.nlb_reply = '';",
            "    vars.nlb_sel = '';",
            "};",
            "N.act = function (vars, id) {",
            "    N.actA(vars, id);",
            "    var o = N.objs[id];",
            "    if (o && o.take && vars.nlb_goto == null) { N.place(vars, o.take, N.INV); }",
            "};",
            "N.actA = function (vars, id) {",
            "    var o = N.objs[id];",
            "    if (!o) { return; }",
            "    var c = o.common ? N.objs[o.common] : null;",
            "    if (o.acttext) { N.sayp(vars, o.acttext(vars)); }",
            "    else if (c && c.acttext) { N.sayp(vars, c.acttext(vars)); }",
            "    o.mods(vars);",
            "    if (vars.nlb_goto != null) { return; }",
            "    if (c) { c.mods(vars); }",
            "};",
            "N.turn = function (vars) {",
            "    var diff = N.countGet(vars, 0), changed = false;",
            "    if (diff > (vars.nlb_cnt_prev || 0)) {",
            "        for (var i = 0; i < N.order.length && vars.nlb_goto == null; i++) {",
            "            var o = N.objs[N.order[i]];",
            "            if (o.callback && (!o.visible || o.visible(vars))) { N.actA(vars, o.id); changed = true; }",
            "        }",
            "    }",
            "    vars.nlb_cnt_prev = diff;",
            "    if (changed) { N.fill(jsIQ.containers.text); }",
            "};",
            "N.useOn = function (vars, src, target) {",
            "    var o = N.objs[src];",
            "    if (!o) { return false; }",
            "    N.printed = false;",
            "    o.use(vars, target);",
            "    if (!N.printed && o.common && N.objs[o.common]) { N.objs[o.common].use(vars, target); }",
            "    return N.printed;",
            "};",
            "N.use = function (vars, src, target) {",
            "    N.count(vars, 'use');",
            "    if (!N.useOn(vars, src, target)) {",
            "        var o = N.objs[src];",
            "        N.sayp(vars, (o && o.nouse) ? o.nouse(vars) : N.gameNouse);",
            "    }",
            "    vars.nlb_sel = '';",
            "};",
            "N.click = function (kind, id) {",
            "    var vars = N.vars();",
            "    vars.nlb_reply = '';",
            "    if (kind === 'inv' && !vars.nlb_sel) {",
            "        N.count(vars, 'inv');",
            "        if (!N.useOn(vars, id, id)) { N.sayp(vars, N.gameInv); }",
            "        vars.nlb_sel = id;",
            "    } else if (kind === 'inv' && vars.nlb_sel === id) {",
            "        vars.nlb_sel = '';",
            "    } else if (vars.nlb_sel) {",
            "        N.use(vars, vars.nlb_sel, id);",
            "    } else {",
            "        N.count(vars, 'act');",
            "        N.act(vars, id);",
            "    }",
            "    var a = jsIQ.current_article;",
            "    if (vars.nlb_goto != null) {",
            "        var to = vars.nlb_goto;",
            "        vars.nlb_goto = null;",
            "        jsIQ.showArticle(to);",
            "    } else if (a) {",
            "        vars.nlb_redraw = true;",
            "        jsIQ.showArticle(a.title);",
            "    }",
            "    return false;",
            "};",
            "N.fill = function (c) {",
            "    if (!c) { return; }",
            "    var vars = N.vars();",
            "    c.find('.nlb_pic').each(function () { $(this).html(N.imgTag(N.img(vars, N.here()))); });",
            "    c.find('.nlb_scene').each(function () { $(this).html(N.scene(vars)); });",
            "    c.find('.nlb_reply').each(function () { $(this).html(vars.nlb_reply || ''); });",
            "    c.find('.nlb_inv').each(function () { $(this).html(N.inventory(vars)); });",
            "    c.find('.nlb_v').each(function () { $(this).text(N.v(vars, $(this).attr('data-v'))); });",
            "};",
            "N.scene = function (vars) { return N.showIn(vars, N.here()); };",
            "jsIQ.addEventHandler('on_text_render', function (params, vars, process) { N.fill(params.container); process.next(); });",
            "jsIQ.addEventHandler('on_action_render', function (params, vars, process) { N.fill(params.container); process.next(); });",
            "");

    private Script createRuntimeScript(NLBBuildingBlocks nlbBlocks) {
        final List<ObjBuildingBlocks> objs = orderedObjects(nlbBlocks);
        java.util.Map<String, Boolean> takable = new java.util.HashMap<>();
        for (ObjBuildingBlocks obj : objs) {
            takable.put(obj.getObjId(), obj.isTakable());
        }
        StringBuilder sb = new StringBuilder(RUNTIME);
        sb.append("N.gameInv = ").append(jsText(nlbBlocks.getGameInvText())).append(";\n");
        sb.append("N.gameNouse = ").append(jsText(nlbBlocks.getGameNouseText())).append(";\n");
        // достижения: ключ книги в localStorage, объявление, все достижения книги и perfect game
        sb.append("N.achKey = ").append(jsText("nlb_ach:" + (StringHelper.notEmpty(nlbBlocks.getTitle()) ? nlbBlocks.getTitle() : "book"))).append(";\n");
        sb.append("N.achText = ").append(jsText(achievementAnnouncement(nlbBlocks.getLang()))).append(";\n");
        StringBuilder achNames = new StringBuilder();
        for (String name : nlbBlocks.getAchievements()) {
            achNames.append(achNames.length() == 0 ? "" : ", ").append(jsText(name));
        }
        sb.append("N.achNames = [").append(achNames).append("];\n");
        sb.append("N.perfect = ").append(jsText(StringHelper.notEmpty(nlbBlocks.getAchievementNamePerfectGame())
                ? nlbBlocks.getAchievementNamePerfectGame() : "")).append(";\n");
        sb.append("N.pages = {");
        String separator = "";
        for (PageBuildingBlocks page : nlbBlocks.getPagesBuildingBlocks()) {
            sb.append(separator).append("'").append(page.getPageNumber()).append("': '").append(page.getPageId()).append("'");
            separator = ", ";
        }
        sb.append("};\n");
        StringBuilder order = new StringBuilder();
        StringBuilder start = new StringBuilder();
        StringBuilder names = new StringBuilder();
        for (ObjBuildingBlocks obj : objs) {
            String id = obj.getObjId();
            String sep = order.length() == 0 ? "" : ", ";
            order.append(sep).append("'").append(id).append("'");
            start.append(sep).append("'").append(id).append("': '").append(obj.getContainerId()).append("'");
            if (StringHelper.notEmpty(obj.getObjName())) {
                names.append(names.length() == 0 ? "" : ", ").append("'").append(jsString(obj.getObjName()))
                        .append("': '").append(id).append("'");
            }
        }
        sb.append("N.order = [").append(order).append("];\n");
        sb.append("N.start = {").append(start).append("};\n");
        sb.append("N.names = {").append(names).append("};\n");
        // теги по умолчанию (var { tag = … } в INSTEAD)
        StringBuilder deftags = new StringBuilder();
        for (ObjBuildingBlocks obj : objs) {
            if (StringHelper.notEmpty(obj.getObjDefaultTagVariable())) {
                deftags.append(deftags.length() == 0 ? "" : ", ").append("'").append(obj.getObjId()).append("': ")
                        .append(jsText(obj.getObjDefaultTagVariable()));
            }
        }
        for (PageBuildingBlocks page : nlbBlocks.getPagesBuildingBlocks()) {
            if (StringHelper.notEmpty(page.getPageDefaultTag())) {
                deftags.append(deftags.length() == 0 ? "" : ", ").append("'").append(page.getPageId()).append("': ")
                        .append(jsText(page.getPageDefaultTag()));
            }
        }
        sb.append("N.deftag = {").append(deftags).append("};\n");
        // картинки и звуки страниц и объектов (MediaChoice)
        StringBuilder imgs = new StringBuilder();
        StringBuilder snds = new StringBuilder();
        for (PageBuildingBlocks page : nlbBlocks.getPagesBuildingBlocks()) {
            appendMedia(imgs, snds, page.getPageId(), page.getPageImages(), page.getPageSounds(), true);
        }
        for (ObjBuildingBlocks obj : objs) {
            appendMedia(imgs, snds, obj.getObjId(), obj.isGraphical() ? java.util.Collections.emptyList() : obj.getObjImages(),
                    obj.getObjSounds(), false);
        }
        sb.append("N.imgs = {").append(imgs).append("};\n");
        sb.append("N.snds = {").append(snds).append("};\n");
        for (ObjBuildingBlocks obj : objs) {
            sb.append(createObjCode(obj, takable));
        }
        Script script = new Script();
        script.setInfo("NLB: objects, inventory, replies");
        script.setType("nlbInit");
        script.setIsGlobal("true");
        script.setValue(sb.toString());
        return script;
    }

    private static void appendMedia(StringBuilder imgs, StringBuilder snds, String id, List<MediaChoice> images,
                                    List<MediaChoice> sounds, boolean page) {
        if (!images.isEmpty()) {
            imgs.append(imgs.length() == 0 ? "" : ",\n").append("'").append(id).append("': [");
            for (int i = 0; i < images.size(); i++) {
                imgs.append(i == 0 ? "" : ", ").append("[").append(jsText(images.get(i).getConstraint())).append(", ")
                        .append(jsText(images.get(i).getPath())).append("]");
            }
            imgs.append("]");
        }
        if (!sounds.isEmpty()) {
            snds.append(snds.length() == 0 ? "" : ",\n").append("'").append(id).append("': [");
            for (int i = 0; i < sounds.size(); i++) {
                MediaChoice sound = sounds.get(i);
                String kind = sound.isStop() ? "x" : !sound.isSfx() ? "m" : page ? "v" : "s";
                snds.append(i == 0 ? "" : ", ").append("[").append(jsText(sound.getConstraint())).append(", ")
                        .append(jsText(sound.getPath())).append(", '").append(kind).append("']");
            }
            snds.append("]");
        }
    }

    /**
     * Объект: подпись (disp), описание в сцене (text, часть в {фигурных скобках} — ссылка),
     * текст действия, модификации, применения (use: тексты успеха/неудачи и модификации; ответ —
     * NLB.printed), текст «не применяется», условие видимости, общий объект, что взять
     */
    private String createObjCode(ObjBuildingBlocks obj, java.util.Map<String, Boolean> takable) {
        final String id = obj.getObjId();
        final String common = obj.getCommonObjId();
        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(StringHelper.notEmpty(obj.getObjName()) ? obj.getObjName().replace("\n", " ") : id).append("\n");
        sb.append("N.objs['").append(id).append("'] = {\n");
        // подпись в инвентаре; без неё — имя объекта (как nam в INSTEAD), без имени — id
        String disp = StringHelper.notEmpty(obj.getObjDisp()) ? obj.getObjDisp()
                : StringHelper.notEmpty(obj.getObjName()) ? obj.getObjName() : id;
        sb.append("    disp: function (vars) { return ").append(jsText(disp)).append("; },\n");
        // графический объект (VN) щёлкают по картинке, а картинок здесь нет: если у него есть
        // действие, ссылкой становится его текст (без {ссылки} в нём), а без текста — подпись
        // (выбор навыков в «Варваре»); подпись без действия — просто текст (надпись-картинка)
        String text = obj.getObjText();
        // подпись для пустой метки {}: disp, иначе первая строка текста действия (карта во
        // «Фронтире»), иначе имя — как PlayerEngine.sceneLabel
        String objLabel = StringHelper.notEmpty(obj.getObjDisp()) ? obj.getObjDisp() : firstLine(obj.getObjActStart());
        if (objLabel.isEmpty()) {
            objLabel = StringHelper.notEmpty(obj.getObjName()) ? obj.getObjName() : id;
        }
        text = text.replace(OBJ_LABEL_MARK, objLabel);
        if (obj.isGraphical()) {
            String label = StringHelper.notEmpty(text) ? text : obj.getObjDisp();
            if (obj.hasAction() && !text.contains("class=\"nlb_obj\"") && StringHelper.notEmpty(label)) {
                text = objectLink(id, label);
            } else if (StringHelper.isEmpty(text) && StringHelper.notEmpty(label)) {
                text = label;
            }
        }
        sb.append("    text: function (vars) { return ").append(jsText(text)).append("; },\n");
        if (StringHelper.notEmpty(obj.getObjActStart())) {
            sb.append("    acttext: function (vars) { return ").append(jsText(obj.getObjActStart())).append("; },\n");
        }
        sb.append("    mods: function (vars) { ").append(obj.getObjModifications()).append(obj.getObjVariable()).append("},\n");
        sb.append("    use: function (vars, target) { ");
        for (UseBuildingBlocks use : obj.getUseBuildingBlocks()) {
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean constrained = StringHelper.notEmpty(use.getUseConstraint());
            sb.append("if (target === '").append(use.getUseTarget()).append("') { ");
            if (constrained) {
                sb.append("if (").append(use.getUseConstraint()).append(") { ");
            }
            if (hasSuccess) {
                sb.append("NLB.sayp(vars, ").append(jsText(use.getUseSuccessText())).append("); NLB.printed = true; ");
            }
            sb.append(use.getUseModifications()).append(use.getUseVariable());
            if (constrained) {
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    sb.append("} else { NLB.sayp(vars, ").append(jsText(use.getUseFailureText())).append("); NLB.printed = true; ");
                }
                sb.append("} ");
            }
            sb.append("} ");
        }
        sb.append("},\n");
        if (StringHelper.notEmpty(obj.getObjNouse())) {
            sb.append("    nouse: function (vars) { return ").append(jsText(obj.getObjNouse())).append("; },\n");
        }
        if (StringHelper.notEmpty(obj.getObjConstraint())) {
            sb.append("    visible: function (vars) { return (").append(obj.getObjConstraint()).append("); },\n");
        }
        if (StringHelper.notEmpty(common)) {
            sb.append("    common: '").append(common).append("',\n");
        }
        if (obj.isCallback()) {
            sb.append("    callback: true,\n");
        }
        if (obj.isImageInScene() && !obj.isGraphical()) {
            sb.append("    imgScene: true,\n");
            if (obj.hasAction()) {
                sb.append("    act: true,\n");
            }
        }
        if (obj.isImageInInventory() && !obj.isGraphical()) {
            sb.append("    imgInv: true,\n");
        }
        if (obj.isTakable()) {
            // decorateObjTak: взять общий объект, если он переносимый, иначе этот
            Boolean commonTakable = StringHelper.notEmpty(common) ? takable.get(common) : null;
            sb.append("    take: '").append(commonTakable != null && commonTakable ? common : id).append("',\n");
        }
        sb.append("    id: '").append(id).append("'\n");
        sb.append("};\n");
        return sb.toString();
    }

    /** Первая непустая строка текста (строки — через <br/>) */
    private static String firstLine(String text) {
        if (text != null) {
            for (String line : text.split("<br/>")) {
                if (!line.replaceAll("<[^>]*>", "").strip().isEmpty()) {
                    return line.strip();
                }
            }
        }
        return EMPTY_STRING;
    }

    /** Ссылка на объект в тексте: щелчок — NLB.click (действие или применение выбранного предмета) */
    private static String objectLink(String objId, String label) {
        return "<a href=\"#\" class=\"nlb_obj\" onclick=\"return NLB.click('act', '" + objId + "')\">" + label + "</a>";
    }

    /**
     * Текст внутри строки JavaScript в апострофах. Подпись предмета может быть многострочной и
     * содержать апострофы — без экранирования скрипт preload не разбирался, и игра не начиналась
     * («Варвар», «Шапочка»)
     */
    private static String jsString(String text) {
        if (text == null) {
            return Constants.EMPTY_STRING;
        }
        return text.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\r", "").replace("\n", "\\n");
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        // картинок в экспорте нет; переменные — метками (см. jsText)
        return dispText == null ? EMPTY_STRING : dispText;
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks): без них ExportManager отдаёт
    // пустые строки, и раньше у объектов jsIQ2 не было ни описаний, ни действий

    /**
     * Описание объекта в сцене: часть в {фигурных скобках} — ссылка на объект (как в INSTEAD).
     * Пустая метка {} в INSTEAD — картинка объекта (флажки настроек «Фронтира»), а картинок здесь
     * нет: ссылкой становится первая непустая строка рядом, а без неё — подпись объекта (как
     * подпись объекта в Java-плеере, PlayerEngine.sceneLabel)
     */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText)) {
            return EMPTY_STRING;
        }
        if (EMPTY_LINK.matcher(objText).find()) {
            String[] lines = EMPTY_LINK.matcher(objText).replaceAll("").split("<br/>", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                // strip, а не trim: trim срезал бы и метки переменных (VAR_MARK — управляющий символ)
                String visible = line.replaceAll("<[^>]*>", "").strip();
                if (!visible.isEmpty()) {
                    String label = line.strip();
                    int start = line.indexOf(label);
                    lines[i] = line.substring(0, start) + objectLink(objId, label)
                            + line.substring(start + label.length());
                    return String.join("<br/>", lines);
                }
            }
            return String.join("<br/>", lines) + objectLink(objId, OBJ_LABEL_MARK);
        }
        return objText.replaceAll("\\{([^}]*)\\}",
                java.util.regex.Matcher.quoteReplacement(objectLink(objId, "\u0002")).replace("\u0002", "$1"));
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        return actTextExpanded == null ? EMPTY_STRING : actTextExpanded;
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return nouseTextExpanded == null ? EMPTY_STRING : nouseTextExpanded;
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
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return containedObjId;
    }

    @Override
    protected String decorateUseTarget(String targetId) {
        return targetId;
    }

    @Override
    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateUseVariable(String variableName) {
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decorateObjLabel(String id) {
        return decorateId(id);
    }

    @Override
    protected String decorateObjName(String name, String id) {
        return name;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + "; ";
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        return "NLB.setTag(vars, " + ref(objId, variable) + ", " + jsText(tag) + "); ";
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        return resultingVariable + " = NLB.tag(vars, " + ref(objId, objVariableName) + "); ";
    }

    @Override
    protected String decorateWhile(final String constraint) {
        return "while (" + constraint + ") { ";
    }

    @Override
    protected String decorateIf(final String constraint) {
        return "if (" + constraint + ") { ";
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        return "if (NLB.has(vars, " + objectKey(objId, objVar) + ")) { ";
    }

    @Override
    protected String decorateElse() {
        return "} else { ";
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return "} else if (" + constraint + ") { ";
    }

    @Override
    protected String decorateEnd() {
        return "} ";
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        return variableName + " = NLB.has(vars, " + objectKey(objId, objVar) + "); ";
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // контейнер объекта (страница или объект); инвентарь и «нигде» — false
        String id = objectKey(objId, objVar);
        return variableName + " = (NLB.where(vars, " + id + ") && NLB.where(vars, " + id + ") !== NLB.INV) ? NLB.where(vars, " + id + ") : false; ";
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // прототип: клонов в экспорте нет — сам объект
        return variableName + " = " + objectKey(objId, objVar) + "; ";
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        String id = objectKey(objectId, objectVar);
        if (destinationName != null) {
            // место — список: nlb:rmv(список, obj), и объект — никуда
            return "NLB.rmv(vars, '" + listName(destinationName) + "', " + id + "); NLB.place(vars, " + id + ", ''); ";
        }
        // без места — из текущей страницы (here() в INSTEAD)
        String destination = destinationId != null ? "'" + destinationId + "'" : "NLB.here()";
        return "if (NLB.where(vars, " + id + ") === " + destination + ") { NLB.place(vars, " + id + ", ''); } ";
    }

    /**
     * Объект (выражение JavaScript): id объекта, а если объект задан переменной (id при экспорте
     * неизвестен) — значение этой переменной во время игры. Раньше второй случай ронял экспорт
     * (NullPointerException, например на «Фронтире»).
     */
    /** Имя списка из переменной (vars.имя -> имя): списки хранятся в vars.nlb_lists */
    private static String listName(String listVariable) {
        return listVariable.startsWith("vars.") ? listVariable.substring("vars.".length()) : listVariable;
    }

    /** Объект операции: известный, из переменной или (без них) сам — «s» в INSTEAD */
    private String ref(String objectId, String objectVar) {
        if (objectId == null && StringHelper.isEmpty(objectVar)) {
            return "'" + selfId() + "'";
        }
        return objectKey(objectId, objectVar);
    }

    private static String objectKey(String objectId, String objectVar) {
        return (objectId != null) ? "'" + objectId + "'" : "String(" + objectVar + ")";
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        String id = objectKey(objectId, objectVar);
        return "if (NLB.has(vars, " + id + ")) { NLB.place(vars, " + id + ", ''); } ";
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // без получателя — в себя (страницу или объект-контейнер; s в INSTEAD)
        String destination = "'" + (destinationId != null ? destinationId : selfId()) + "'";
        return "NLB.place(vars, " + objectKey(objectId, objectVar) + ", " + destination + "); ";
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return "NLB.place(vars, " + objectKey(objectId, objectVar) + ", NLB.INV); ";
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // в контейнер, в список или (без получателя) — в себя
        String container = destinationId != null ? destinationId : selfId();
        return "NLB.addAll(vars, '" + listName(sourceListVariableName) + "', '" + container + "', "
                + (destinationListVariableName != null ? "'" + listName(destinationListVariableName) + "'" : "null") + "); ";
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return "NLB.objsTo(vars, '" + listName(listVariableName) + "', " + ref(srcObjId, objectVar) + "); ";
    }

    @Override
    protected String decorateSSndOperation() {
        return "NLB.snd(vars, '" + selfId() + "'); ";
    }

    @Override
    protected String decorateWSndOperation() {
        // ww — объект, к которому применяют: в use объекта это target; вне применения — ничего
        return inUseLink() ? "NLB.snd(vars, target); " : EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        return "NLB.snd(vars, " + ref(objectId, objectVar) + "); ";
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return "NLB.push(vars, '" + listName(listVariableName) + "', '" + selfId() + "'); ";
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return "NLB.push(vars, '" + listName(listVariableName) + "', " + (objectId != null ? "'" + objectId + "'" : objectVar) + "); ";
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        return variableName + " = NLB.pop(vars, '" + listName(listVariableName) + "'); ";
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return "NLB.inject(vars, '" + listName(listVariableName) + "', '" + selfId() + "'); ";
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return "NLB.inject(vars, '" + listName(listVariableName) + "', " + (objectId != null ? "'" + objectId + "'" : objectVar) + "); ";
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        return variableName + " = NLB.eject(vars, '" + listName(listVariableName) + "'); ";
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "NLB.clear(vars, '" + destinationId + "'); ";
        } else if (destinationVar != null) {
            return "NLB.lclear(vars, '" + listName(destinationVar) + "'); ";  // nlb:clear(список)
        }
        return "NLB.clear(vars, NLB.here()); ";
    }

    @Override
    protected String decorateClearInvOperation() {
        return "NLB.clear(vars, NLB.INV); ";
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return variableName + " = NLB.size(vars, '" + listName(listVariableName) + "'); ";
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n
        return variableName + " = Math.floor(Math.random() * (" + maxValue + ")) + 1; ";
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return "NLB.achMax(" + jsText(achievementName) + ", " + max + "); ";
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        return "NLB.achieve(vars, " + jsText(achievementName) + ", '" + modificationId + "'); ";
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return variableName + " = NLB.ach(" + jsText(achievementName) + "); ";
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // Статья для перехода — в vars.nlb_goto: действие (do_i) делает её своей целью, поэтому GOTO
        // важнее цели ссылки, как в INSTEAD; после модификаций страницы и действия с объектом на
        // неё переходят onload и NLB.click. return прерывает остальные модификации
        return "NLB.go(vars, '" + getJsiqArticleId(locationId) + "'); return; ";
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        // счётчики действий игрока по видам (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8
        return variableName + " = NLB.countGet(vars, " + statType + "); ";
    }

    @Override
    protected String decorateCountResetOperation() {
        return "NLB.countReset(vars); ";
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return "NLB.shuffle(vars, '" + listName(listVariableName) + "'); ";
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // вывод значения выражения в ответ, без перевода строки
        return "NLB.say(vars, String(" + variableName + ")); ";
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        return resultVariableName + " = NLB.text(vars, " + objectKey(dscObjId, dscObjVariable) + "); ";
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // объект задан именем (vars.имя): его id — в NLB.names
        String name = objVariableName.startsWith("vars.") ? objVariableName.substring(5) : objVariableName;
        return "NLB.sayp(vars, NLB.text(vars, NLB.names['" + jsString(name) + "'] || '')); ";
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        if (objId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // описания объектов внутри объекта
        return "NLB.sayp(vars, NLB.showIn(vars, '" + objId + "')); ";
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        return "NLB.act(vars, " + objectKey(actingObjId, actingObjVariable) + "); if (vars." + GOTO_VAR + " != null) { return; } ";
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        if (sourceId == null || targetId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "NLB.useOn(vars, '" + sourceId + "', '" + targetId + "'); if (vars." + GOTO_VAR + " != null) { return; } ";
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
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return "vars." + constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        return linkText;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkSource, int sourcePageNumber, String linkTarget, int targetPageNumber, boolean isTechnical, Theme theme) {
        return Integer.toString(targetPageNumber - 1);
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "vars." + linkVisitStateVariable + " = true; ";
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return "vars." + variableName + " = true; ";
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING : decorateNumberVar(variableName) + " = 0; ";
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING
                : decorateNumberVar(variableName) + " = " + decorateNumberVar(variableName) + " + 1; ";
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return caption;
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        return expandVariables(textChunks);
    }

    /**
     * Переменная в тексте — меткой: в тексте статьи она становится span (html), в тексте объекта
     * — значением во время игры (jsText). Раньше в тексте оставалось «$имя$»
     */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    // '^' — перевод строки INSTEAD: так пишут тексты книг, которые играют в INSTEAD
                    // («Фронтир»), и так же их показывает Java-плеер
                    result.append(textChunk.getText().replace("^", " <br/>" + getLineSeparator()));
                    break;
                case ACTION_TEXT:
                    break;
                case VARIABLE:
                    result.append(VAR_MARK).append(textChunk.getText()).append(VAR_MARK);
                    break;
                case NEWLINE:
                    result.append(" <br/>").append(getLineSeparator());
                    break;
            }
        }
        return result.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return Integer.toString(pageNumber - 1);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return Constants.EMPTY_STRING;
    }
}
