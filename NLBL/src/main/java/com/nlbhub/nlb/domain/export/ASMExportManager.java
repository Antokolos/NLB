/**
 * @(#)ASMExportManager.java
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
 * Copyright (c) 2014 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ASMExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 5/23/14.
 */
public class ASMExportManager extends TextExportManager {
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    /**
     * Знаки, удвоение которых AXMA Story Maker считает разметкой или кодом: '' жирный, // курсив,
     * __ подчёркнутый, ~~ и ^^ индексы, === и @@@ выравнивание, %%% заголовок, {{{ }}} моноширинный,
     * [[ ]] ссылка, << >> макрос
     */
    private static final String DOUBLED_MARKUP_CHARS = "'/_~^=@%{}[]<>";
    /** Начало строки, которое AXMA считает комментарием (#), пунктом списка или разделителем (*) */
    private static final Pattern LINE_START_MARKUP = Pattern.compile("(?m)^(\\s*)([#*])");
    /**
     * Строка из одних дефисов (----) — горизонтальная линия; дефисы экранируются все, иначе
     * AXMA заменит оставшиеся «--» на тире (в обычном тексте эта замена желательна: «Я -- монстр»)
     */
    private static final Pattern HR_LINE = Pattern.compile("(?m)^(\\s*)(-{4,})(?=\\s*$)");

    /** Открытые блоки модификаций: true — WHILE (<<loop>>), false — IF; END закрывает последний */
    private final Deque<Boolean> m_openBlocks = new ArrayDeque<>();
    /** Язык книги — для подписи кнопки автоссылки без текста */
    private String m_language = "en";

    /** Место «инвентарь» в $nlb_in_N */
    private static final String INVENTORY = "nlb_inv";
    /** Метка номера объекта до сборки текста: id -> номер (generateNLBText) */
    private static final String KEY_MARK = "\u0001nlb_key:";
    /** Метка объекта по имени (PDSC) до сборки текста */
    private static final String NAME_MARK = "\u0001nlb_name:";
    private static final String END_MARK = "\u0002";
    /** Виды действий игрока (биты 1, 2, 4, 8 — как PlayerContext.COUNT_*) */
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};
    private static final Pattern PRINT_MACRO = Pattern.compile("<<print \\$(\\w+)>>");

    private final java.util.Map<String, Integer> m_keys = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();
    private List<ObjBuildingBlocks> m_orderedObjs = new java.util.ArrayList<>();
    private String m_gameInvText = EMPTY_STRING;
    private String m_gameNouseText = EMPTY_STRING;

    private static String key(String objId) {
        return KEY_MARK + objId + END_MARK;
    }

    public ASMExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_language = StringHelper.isEmpty(nlbBuildingBlocks.getLang()) ? "en" : nlbBuildingBlocks.getLang();
        StringBuilder preamble = new StringBuilder("128[::]0[::]0").append(LINE_SEPARATOR);
        // Служебные абзацы AXMA: название и автор игры (показываются на титульном экране,
        // без них редактор не даёт опубликовать игру)
        appendSystemPassage(preamble, "StoryTitle", nlbBuildingBlocks.getTitle());
        appendSystemPassage(preamble, "StoryAuthor", nlbBuildingBlocks.getAuthor());
        m_gameInvText = escapeMarkup(nlbBuildingBlocks.getGameInvText());
        m_gameNouseText = escapeMarkup(nlbBuildingBlocks.getGameNouseText());
        preamble.append(servicePassages());
        return preamble.toString();
    }

    /**
     * Служебные абзацы объектов. Объектов в AXMA нет, есть переменные, &lt;&lt;display&gt;&gt; (вывести
     * абзац на месте — подпрограмма) и &lt;&lt;goto&gt;&gt; (перейти, прерывая абзац). Объекты книги
     * пронумерованы: место объекта N — $nlb_in_N (id страницы или объекта-контейнера, «nlb_inv» —
     * инвентарь, "" — нигде), порядок предметов инвентаря — массив $nlb_invo (в каком их брали, как
     * в INSTEAD). На странице после текста — объекты (nlb_show_in), ответ на действие (nlb_reply) и
     * инвентарь (nlb_inv). Щелчок по объекту — ссылка [[текст|nlb_act {$nlb_o = N}]]: действие или
     * применение выбранного предмета, затем страница показывается заново (без модификаций входа);
     * GOTO из действия уводит на свою страницу. Абзац и переход по переменной AXMA понимает, только
     * если аргумент начинается с $, поэтому имя абзаца сначала собирается в $nlb_p.
     */
    private String servicePassages() {
        final String ls = LINE_SEPARATOR;
        StringBuilder sb = new StringBuilder();
        // начальные значения: необъявленная переменная в JavaScript — ошибка
        sb.append(":: nlb_init[::]10-10-0").append(ls);
        sb.append("<<set $").append(ACTION_COUNT_VAR).append(" = 0>>");
        for (String type : COUNT_TYPES) {
            sb.append("<<set $nlb_cnt_").append(type).append(" = 0>><<set $nlb_cntr_").append(type).append(" = 0>>");
        }
        sb.append("<<set $nlb_cnt_prev = 0>><<set $nlb_sel = 0>><<set $nlb_o = 0>><<set $nlb_o2 = 0>><<set $nlb_t = 0>>")
                .append("<<set $nlb_printed = 0>><<set $nlb_reply = \"\">><<set $nlb_redraw = 0>><<set $nlb_page = \"\">>")
                .append("<<set $nlb_where = \"\">><<set $nlb_ws = \"\">><<set $nlb_p = \"\">><<set $nlb_d = \"\">>")
                .append("<<set $nlb_i = 0>><<set $nlb_diff = 0>><<set $nlb_invo = []>>");
        for (ObjBuildingBlocks obj : m_orderedObjs) {
            sb.append("<<set $nlb_in_").append(m_keys.get(obj.getObjId())).append(" = \"").append(obj.getContainerId()).append("\">>");
        }
        sb.append(ls).append(ls);
        // объекты места $nlb_where — каждый строкой, в порядке книги
        sb.append(":: nlb_show_in[::]10-10-0").append(ls);
        sb.append("<<set $nlb_ws = $nlb_where>>").append(ls);
        for (ObjBuildingBlocks obj : m_orderedObjs) {
            int n = m_keys.get(obj.getObjId());
            sb.append("<<if $nlb_in_").append(n).append(" eq $nlb_where>><<display 'nlb_o_").append(n).append("_dsc'>><<endif>>").append(ls);
        }
        sb.append(ls);
        // конец хода: callback-объекты, если игрок с прошлого хода что-то сделал (update_action_count)
        sb.append(":: nlb_turn[::]10-10-0").append(ls);
        sb.append("<<set $nlb_diff = ").append(countSum(0)).append(">><<if $nlb_diff gt $nlb_cnt_prev>><<set $nlb_cnt_prev = $nlb_diff>>");
        for (ObjBuildingBlocks obj : m_orderedObjs) {
            if (obj.isCallback()) {
                int n = m_keys.get(obj.getObjId());
                boolean constrained = StringHelper.notEmpty(obj.getObjConstraint());
                sb.append(constrained ? "<<if " + obj.getObjConstraint() + ">>" : "")
                        .append("<<display 'nlb_o_").append(n).append("_acta'>>").append(constrained ? "<<endif>>" : "");
            }
        }
        sb.append("<<endif>><<set $nlb_cnt_prev = $nlb_diff>>").append(ls).append(ls);
        sb.append(":: nlb_reply[::]10-10-0").append(ls);
        sb.append("<<if $nlb_reply neq \"\">><<print $nlb_reply>><<set $nlb_reply = \"\">><<endif>>").append(ls).append(ls);
        // инвентарь: предметы кнопками, выбранный для применения — с «» »
        sb.append(":: nlb_inv[::]10-10-0").append(ls);
        sb.append("<<if $nlb_invo.length gt 0>>").append(ls).append("* * *").append(ls).append("<<endif>>").append(ls);
        sb.append("<<set $nlb_i = 0>><<loop $nlb_invo.length>><<set $nlb_p = \"nlb_o_\" + $nlb_invo[$nlb_i] + \"_item\">>")
                .append("<<display $nlb_p>><<set $nlb_i = $nlb_i + 1>><<endloop>>").append(ls).append(ls);
        // щелчок по объекту в тексте: действие или (выбран предмет) применение
        sb.append(":: nlb_act[::]10-10-0").append(ls);
        sb.append("<<if $nlb_sel neq 0>><<set $nlb_t = $nlb_o>><<set $nlb_o = $nlb_sel>><<display 'nlb_use'>><<else>>")
                .append(count("act")).append("<<set $nlb_p = \"nlb_o_\" + $nlb_o + \"_act\">><<display $nlb_p>><<endif>>")
                .append("<<set $nlb_redraw = 1>><<goto $nlb_page>>").append(ls).append(ls);
        // применение $nlb_o к $nlb_t; без ответа — nouse
        sb.append(":: nlb_use[::]10-10-0").append(ls);
        sb.append(count("use")).append("<<set $nlb_sel = 0>><<set $nlb_printed = 0>>")
                .append("<<set $nlb_p = \"nlb_o_\" + $nlb_o + \"_use\">><<display $nlb_p>>")
                .append("<<if $nlb_printed eq 0>><<set $nlb_p = \"nlb_o_\" + $nlb_o + \"_nouse\">><<display $nlb_p>><<endif>>")
                .append(ls).append(ls);
        // щелчок по предмету: первый — применение к себе (inv) или game-inv и выбор, другой предмет
        // — применение, тот же — отмена выбора
        sb.append(":: nlb_invclick[::]10-10-0").append(ls);
        sb.append("<<if $nlb_sel eq 0>>").append(count("inv"))
                .append("<<set $nlb_printed = 0>><<set $nlb_o = $nlb_o2>><<set $nlb_t = $nlb_o2>><<set $nlb_sel = $nlb_o2>>")
                .append("<<set $nlb_p = \"nlb_o_\" + $nlb_o + \"_use\">><<display $nlb_p>>")
                .append("<<if $nlb_printed eq 0>>").append(reply(m_gameInvText)).append("<<endif>>")
                .append("<<elseif $nlb_sel eq $nlb_o2>><<set $nlb_sel = 0>>")
                .append("<<else>><<set $nlb_o = $nlb_sel>><<set $nlb_t = $nlb_o2>><<display 'nlb_use'>><<endif>>")
                .append("<<set $nlb_redraw = 1>><<goto $nlb_page>>").append(ls).append(ls);
        return sb.toString();
    }

    /** Действие игрока вида type: счётчик для «needs action» и для COUNTGET */
    private static String count(String type) {
        return "<<set $" + ACTION_COUNT_VAR + " = $" + ACTION_COUNT_VAR + " + 1>><<set $nlb_cnt_" + type
                + " = $nlb_cnt_" + type + " + 1>>";
    }

    /** Сумма счётчиков действий с последнего сброса (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8 */
    private static String countSum(int statType) {
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                sum.append(sum.length() > 0 ? " + " : "").append("($nlb_cnt_").append(COUNT_TYPES[i])
                        .append(" - $nlb_cntr_").append(COUNT_TYPES[i]).append(")");
            }
        }
        return sum.length() > 0 ? "(" + sum + ")" : "0";
    }

    /**
     * Текст (после escapeMarkup, с &lt;&lt;print $x&gt;&gt;) как выражение JavaScript в &lt;&lt;set&gt;&gt;:
     * строка в кавычках, переменные — значениями. Проверено в движке AXMA: &lt;&lt;set&gt;&gt; убирает все $,
     * апостроф ломает компиляцию, именованные сущности (&amp;quot;) раскрываются, а числовые — нет, &lt;br&gt;
     * остаётся HTML
     */
    static String jsText(String text) {
        if (StringHelper.isEmpty(text)) {
            return "\"\"";
        }
        StringBuilder result = new StringBuilder("\"");
        Matcher m = PRINT_MACRO.matcher(text);
        int at = 0;
        while (m.find()) {
            result.append(jsChars(text.substring(at, m.start()))).append("\" + $").append(m.group(1)).append(" + \"");
            at = m.end();
        }
        result.append(jsChars(text.substring(at))).append("\"");
        return result.toString();
    }

    private static String jsChars(String text) {
        return text.replace("\r", "").replace("\n", "<br>").replace("\"", "&#34;").replace("'", "&#39;").replace("$", "&#36;");
    }

    /** Добавить к ответу на действие (выводится после объектов страницы) */
    private static String reply(String text) {
        return "<<set $nlb_reply = $nlb_reply + " + jsText(text) + " + \"<br>\">>";
    }

    /**
     * Перенести объект с номером key (метка) в место dest (выражение AXMA: "id", "nlb_inv", "" или
     * $nlb_page), с порядком инвентаря $nlb_invo
     */
    private static String place(String key, String dest) {
        return "<<if ($nlb_in_" + key + " eq \"" + INVENTORY + "\") and (" + dest + " neq \"" + INVENTORY + "\")>>"
                + "<<set $nlb_invo.splice($nlb_invo.indexOf(" + key + "), 1)>><<endif>>"
                + "<<if ($nlb_in_" + key + " neq \"" + INVENTORY + "\") and (" + dest + " eq \"" + INVENTORY + "\")>>"
                + "<<set $nlb_invo.push(" + key + ")>><<endif>>"
                + "<<set $nlb_in_" + key + " = " + dest + ">>";
    }

    private static void appendSystemPassage(StringBuilder builder, String title, String text) {
        if (StringHelper.notEmpty(text) && !text.trim().isEmpty()) {
            builder.append(":: ").append(title).append("[::]10-10-0").append(LINE_SEPARATOR);
            builder.append(escapeMarkup(text.trim())).append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        }
    }

    /**
     * Текст книги для AXMA — как есть: всё, что движок принял бы за разметку, комментарий или
     * макрос, заменяется числовыми HTML-сущностями (движок их сохраняет, браузер показывает знак).
     * Одиночные знаки не трогаются: разметкой они не считаются, а &lt; и &gt; AXMA экранирует сама.
     *
     * @param text текст книги
     * @return текст, который AXMA покажет без изменений
     */
    static String escapeMarkup(final String text) {
        if (StringHelper.isEmpty(text)) {
            return Constants.EMPTY_STRING;
        }
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int end = i + 1;
            if (DOUBLED_MARKUP_CHARS.indexOf(c) >= 0) {
                while (end < text.length() && text.charAt(end) == c) {
                    end++;
                }
            }
            if (end - i > 1) {
                for (int j = i; j < end; j++) {
                    result.append(entity(c));
                }
            } else {
                result.append(c);
            }
            i = end;
        }
        String escaped = replaceFirstChar(LINE_START_MARKUP, result.toString());
        Matcher hr = HR_LINE.matcher(escaped);
        StringBuilder withoutHr = new StringBuilder();
        while (hr.find()) {
            hr.appendReplacement(withoutHr, Matcher.quoteReplacement(
                    hr.group(1) + hr.group(2).replace("-", entity('-'))));
        }
        hr.appendTail(withoutHr);
        return withoutHr.toString();
    }

    private static String entity(char c) {
        return "&#" + (int) c + ";";
    }

    /** Заменяет знак после отступа (группа 1) в каждом совпадении на HTML-сущность */
    private static String replaceFirstChar(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String indent = matcher.group(1);
            char c = text.charAt(matcher.start() + indent.length());
            matcher.appendReplacement(result, Matcher.quoteReplacement(indent + entity(c)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Текст страницы: разметка AXMA экранируется, переменная выводится макросом
     * &lt;&lt;print $var&gt;&gt; (в AXMA подстановки $var$ нет)
     */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    // '^' — перевод строки INSTEAD: так пишут тексты книг, которые играют в INSTEAD, и так
                    // их показывает Java-плеер
                    result.append(escapeMarkup(textChunk.getText().replace("^", getLineSeparator())));
                    break;
                case ACTION_TEXT:
                    // $^$ — текст действия (VN), не переменная: <<print $^>> ломал компиляцию всей
                    // игры («Шапочка», «Фронтир»); как в QSP и jsIQ2 — не выводится
                    break;
                case VARIABLE:
                    result.append("<<print $").append(textChunk.getText()).append(">>");
                    break;
                case NEWLINE:
                    result.append(getLineSeparator());
                    break;
            }
        }
        return result.toString();
    }

    /**
     * Текст ссылки: AXMA превращает [[текст|абзац]] в макрос link, внутри которого макросы не
     * работают, поэтому переменная остаётся именем ($var$, как раньше). Первый знак *, + или -
     * задаёт вид ссылки (всплывающая, вставка, без возврата), поэтому тоже экранируется.
     */
    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(escapeMarkup(textChunk.getText().replace("^", " ")));
                    break;
                case ACTION_TEXT:
                    break;
                case VARIABLE:
                    result.append("$").append(textChunk.getText()).append("$");
                    break;
                case NEWLINE:
                    result.append(" ");
                    break;
            }
        }
        String text = result.toString();
        if (!text.isEmpty() && "*+-".indexOf(text.charAt(0)) >= 0) {
            text = entity(text.charAt(0)) + text.substring(1);
        }
        return text;
    }

    /**
     * Объект N — абзацы-подпрограммы: _dsc (описание со ссылкой и содержимое контейнера; условие
     * объекта скрывает и содержимое), _text (описание, для PDSC), _acta (текст действия, модификации,
     * общий объект — так же вызывают callback-объекты), _act (_acta и, если переносимый, — в
     * инвентарь), _use (применения к $nlb_t; ответ — $nlb_printed), _nouse, _item (предмет в
     * инвентаре: подпись с переменными — выражением в тексте ссылки).
     */
    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        final String ls = LINE_SEPARATOR;
        final String id = objBlocks.getObjId();
        final int n = m_keys.get(id);
        final String prefix = ":: nlb_o_" + n;
        final String common = objBlocks.getCommonObjId();
        final boolean hasCommon = StringHelper.notEmpty(common) && m_keys.containsKey(common);
        final String disp = objLabel(objBlocks);
        StringBuilder sb = new StringBuilder();

        String text = objBlocks.getObjText().replace(EMPTY_LINK_LABEL, disp);
        if (objBlocks.isGraphical()) {
            // графический объект (VN) щёлкают по картинке, а картинок здесь нет: с действием —
            // ссылкой его текст (одна строка без макросов) или подпись; без действия — просто текст
            final String link = "[[" + disp + "|nlb_act {$nlb_o = " + n + "}]]";
            if (objBlocks.hasAction() && !text.contains("|nlb_act {")) {
                text = text.isEmpty() ? link
                        : !text.contains(ls) && !text.contains("<<") ? "[[" + text + "|nlb_act {$nlb_o = " + n + "}]]"
                        : link + ls + text;
            } else if (text.isEmpty() && StringHelper.notEmpty(objBlocks.getObjDisp())) {
                text = objBlocks.getObjDisp();
            }
        }
        sb.append(prefix).append("_dsc[::]10-10-0").append(ls);
        final boolean constrained = StringHelper.notEmpty(objBlocks.getObjConstraint());
        if (constrained) {
            sb.append("<<if ").append(objBlocks.getObjConstraint()).append(">>").append(ls);
        }
        if (!text.isEmpty()) {
            sb.append(text).append(ls);
        }
        sb.append("<<set $nlb_where = \"").append(id).append("\">><<display 'nlb_show_in'>><<set $nlb_where = $nlb_in_")
                .append(n).append(">>").append(ls);
        if (constrained) {
            sb.append("<<endif>>").append(ls);
        }
        sb.append(ls);

        sb.append(prefix).append("_text[::]10-10-0").append(ls);
        sb.append(text.isEmpty() ? "<<set $nlb_d = $nlb_d>>" : text).append(ls).append(ls);

        sb.append(prefix).append("_acta[::]10-10-0").append(ls);
        sb.append(objBlocks.getObjActStart());
        if (hasCommon && StringHelper.isEmpty(objBlocks.getObjActStart())) {
            // общий объект (objActA): его текст — если у объекта своего нет, затем его модификации
            sb.append("<<display 'nlb_o_").append(m_keys.get(common)).append("_acttext'>>").append(ls);
        }
        sb.append(objBlocks.getObjModifications());
        sb.append(objBlocks.getObjVariable());
        if (hasCommon) {
            sb.append("<<display 'nlb_o_").append(m_keys.get(common)).append("_mods'>>").append(ls);
        }
        sb.append("<<set $nlb_d = $nlb_d>>").append(ls).append(ls);
        sb.append(prefix).append("_acttext[::]10-10-0").append(ls);
        sb.append(objBlocks.getObjActStart()).append("<<set $nlb_d = $nlb_d>>").append(ls).append(ls);
        sb.append(prefix).append("_mods[::]10-10-0").append(ls);
        sb.append(objBlocks.getObjModifications()).append(objBlocks.getObjVariable()).append("<<set $nlb_d = $nlb_d>>").append(ls).append(ls);

        sb.append(prefix).append("_act[::]10-10-0").append(ls);
        sb.append("<<display 'nlb_o_").append(n).append("_acta'>>").append(ls);
        if (objBlocks.isTakable()) {
            // decorateObjTak: взять общий объект, если он переносимый, иначе этот
            String toTake = hasCommon && Boolean.TRUE.equals(m_takable.get(common)) ? common : id;
            sb.append(place(String.valueOf(m_keys.get(toTake)), "\"" + INVENTORY + "\"")).append(ls);
        }
        sb.append(ls);

        sb.append(prefix).append("_use[::]10-10-0").append(ls);
        for (UseBuildingBlocks use : objBlocks.getUseBuildingBlocks()) {
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean useConstrained = StringHelper.notEmpty(use.getUseConstraint());
            sb.append("<<if $nlb_t eq ").append(use.getUseTarget()).append(">>").append(ls);
            if (useConstrained) {
                sb.append("<<if ").append(use.getUseConstraint()).append(">>").append(ls);
            }
            if (hasSuccess) {
                sb.append(reply(use.getUseSuccessText())).append("<<set $nlb_printed = 1>>").append(ls);
            }
            sb.append(use.getUseModifications());
            sb.append(use.getUseVariable());
            if (useConstrained) {
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    sb.append("<<else>>").append(ls);
                    sb.append(reply(use.getUseFailureText())).append("<<set $nlb_printed = 1>>").append(ls);
                }
                sb.append("<<endif>>").append(ls);
            }
            sb.append("<<endif>>").append(ls);
        }
        if (hasCommon) {
            sb.append("<<if $nlb_printed eq 0>><<display 'nlb_o_").append(m_keys.get(common)).append("_use'>><<endif>>").append(ls);
        }
        sb.append("<<set $nlb_d = $nlb_d>>").append(ls).append(ls);

        sb.append(prefix).append("_nouse[::]10-10-0").append(ls);
        String nouse = objBlocks.getObjNouse();
        sb.append(reply(StringHelper.notEmpty(nouse) ? nouse : m_gameNouseText)).append(ls).append(ls);

        sb.append(prefix).append("_item[::]10-10-0").append(ls);
        sb.append("<<set $nlb_d = ").append(jsText(disp)).append(">><<if $nlb_sel eq ").append(n)
                .append(">><<set $nlb_d = \"» \" + $nlb_d>><<endif>>").append(ls);
        sb.append("[[$nlb_d|nlb_invclick {$nlb_o2 = ").append(n).append("}]]").append(ls).append(ls);
        return sb.toString();
    }

    /** Подпись объекта: disp, без неё — имя (как nam в INSTEAD), без имени — id */
    private static String objLabel(ObjBuildingBlocks obj) {
        if (StringHelper.notEmpty(obj.getObjDisp())) {
            return obj.getObjDisp();
        }
        return StringHelper.notEmpty(obj.getObjName()) ? escapeMarkup(obj.getObjName()) : obj.getObjId();
    }

    /** Подпись пустой метки {} до сборки объекта: её заменяет подпись объекта (generateObjText) */
    private static final String EMPTY_LINK_LABEL = "\u0004";

    @Override
    protected String generateNLBText(final NLBBuildingBlocks nlbBlocks) throws com.nlbhub.nlb.exception.NLBConsistencyException {
        m_orderedObjs = orderedObjects(nlbBlocks);
        java.util.Map<String, String> idByName = new java.util.HashMap<>();
        for (int i = 0; i < m_orderedObjs.size(); i++) {
            ObjBuildingBlocks obj = m_orderedObjs.get(i);
            m_keys.put(obj.getObjId(), i + 1);
            m_takable.put(obj.getObjId(), obj.isTakable());
            if (StringHelper.notEmpty(obj.getObjName())) {
                idByName.put(obj.getObjName(), obj.getObjId());
            }
        }
        String text = super.generateNLBText(nlbBlocks);
        // метки, известные только теперь: id объекта -> номер, имя объекта -> номер
        Matcher matcher = Pattern.compile("(" + Pattern.quote(KEY_MARK) + "|" + Pattern.quote(NAME_MARK) + ")(.*?)"
                + Pattern.quote(END_MARK)).matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = KEY_MARK.equals(matcher.group(1)) ? matcher.group(2) : idByName.get(matcher.group(2));
            Integer n = id == null ? null : m_keys.get(id);
            matcher.appendReplacement(result, Matcher.quoteReplacement(n == null ? "0" : String.valueOf(n)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjName(String name, String id) {
        return name == null ? EMPTY_STRING : name;
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return dispText == null ? EMPTY_STRING : dispText.replace(LINE_SEPARATOR, " ").trim();
    }

    /** Описание объекта: часть в {фигурных скобках} — ссылка на nlb_act (как ссылка объекта в INSTEAD) */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText)) {
            return EMPTY_STRING;
        }
        Matcher m = Pattern.compile("\\{([^}]*)\\}").matcher(objText);
        StringBuilder result = new StringBuilder();
        while (m.find()) {
            // пустая метка {} в INSTEAD — картинка объекта: ссылкой станет подпись объекта
            String label = m.group(1).trim().isEmpty() ? EMPTY_LINK_LABEL : m.group(1);
            m.appendReplacement(result, Matcher.quoteReplacement("[[" + label + "|nlb_act {$nlb_o = " + key(objId) + "}]]"));
        }
        m.appendTail(result);
        return result.toString();
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        return StringHelper.isEmpty(actTextExpanded) || actTextExpanded.trim().isEmpty()
                ? EMPTY_STRING : reply(actTextExpanded.trim()) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return nouseTextExpanded == null ? EMPTY_STRING : nouseTextExpanded.trim();
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
        return "<<set $" + variableName + " = 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return containedObjId;
    }

    @Override
    protected String decorateUseTarget(String targetId) {
        return key(targetId);
    }

    @Override
    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateUseVariable(String variableName) {
        return "<<set $" + variableName + " = 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        final String pageId = pageBlocks.getPageId();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(pageBlocks.getPageComment());
        stringBuilder.append(pageBlocks.getPageLabel());
        // Модификации страницы — до текста (как enter в INSTEAD: текст показывает уже изменённые
        // переменные; раньше они шли после текста). Действие с объектом показывает страницу заново
        // ($nlb_redraw) — уже без модификаций входа.
        stringBuilder.append("<<set $nlb_page = \"").append(pageId).append("\">><<if $nlb_redraw eq 1>><<set $nlb_redraw = 0>><<else>>")
                .append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageModifications());
        stringBuilder.append(pageBlocks.getPageVariable());
        stringBuilder.append("<<endif>>").append(LINE_SEPARATOR);
        stringBuilder.append("<<display 'nlb_turn'>>").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        // объекты страницы, ответ на действие, инвентарь
        stringBuilder.append("<<set $nlb_where = \"").append(pageId).append("\">><<display 'nlb_show_in'>>").append(LINE_SEPARATOR);
        stringBuilder.append("<<display 'nlb_reply'>>").append(LINE_SEPARATOR);
        stringBuilder.append("<<display 'nlb_inv'>>").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        // Автоссылки — до обычных ссылок и вместо них: первая автоссылка с выполненным условием
        // уводит со страницы, как в INSTEAD. <<goto>> в AXMA очищает экран, поэтому на странице с
        // текстом переход — единственной кнопкой (текст страницы, которую сразу покидают, должен
        // быть виден, как в плеере), а на странице без текста — сразу <<goto>>.
        // Получается <<if а1>>…<<elseif а2>>…<<else>>обычные ссылки<<endif>>.
        int openIfs = 0;
        boolean unconditionalAuto = false;
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!linkBlocks.isAuto()) {
                continue;
            }
            String constraint = linkBlocks.getLinkConstraint();
            if (linkBlocks.isNeedsAction()) {
                // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий
                String hasAction = "($" + ACTION_COUNT_VAR + " >= " + getNeedsActionCount() + ")";
                constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") and " + hasAction;
            }
            final boolean hasConstraint = !StringHelper.isEmpty(constraint);
            if (hasConstraint) {
                stringBuilder.append(openIfs == 0 ? "<<if " : "<<elseif ")
                        .append(constraint).append(">>").append(LINE_SEPARATOR);
                openIfs = 1;
            } else if (openIfs > 0) {
                stringBuilder.append("<<else>>").append(LINE_SEPARATOR);
            }
            stringBuilder.append(autoTransition(pageBlocks, linkBlocks));
            if (!hasConstraint) {
                // дальше ничего не выполнится: остальные ссылки не нужны
                unconditionalAuto = true;
                break;
            }
        }
        if (!unconditionalAuto) {
            if (openIfs > 0) {
                stringBuilder.append("<<else>>").append(LINE_SEPARATOR);
            }
            appendOrdinaryLinks(stringBuilder, linksBlocks);
        }
        if (openIfs > 0) {
            stringBuilder.append("<<endif>>").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
    }

    private String autoTransition(PageBuildingBlocks pageBlocks, LinkBuildingBlocks linkBlocks) {
        if (!pageBlocks.isHasPageText()) {
            return "<<goto '" + linkBlocks.getLinkId() + "'>>" + LINE_SEPARATOR;
        }
        String text = linkBlocks.getLinkText();
        return decorateLinkStart(linkBlocks.getLinkId(), text, true, true, false, 0, linkBlocks.getTheme());
    }

    private void appendOrdinaryLinks(StringBuilder stringBuilder, List<LinkBuildingBlocks> linksBlocks) {
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                continue;
            }
            final boolean hasConstraint = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (hasConstraint) {
                stringBuilder.append("<<if ").append(linkBlocks.getLinkConstraint()).append(">>").append(LINE_SEPARATOR);
            }
            stringBuilder.append(linkBlocks.getLinkStart());
            if (hasConstraint) {
                if (linkBlocks.hasShownAltText()) {
                    // альтернативный текст недоступной ссылки (#135)
                    stringBuilder.append("<<else>>").append(LINE_SEPARATOR);
                    stringBuilder.append(linkBlocks.getLinkAltText()).append(LINE_SEPARATOR);
                }
                stringBuilder.append("<<endif>>").append(LINE_SEPARATOR);
            }
        }
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateNot() {
        // «!», а не «not»: AXMA заменяет « and » на «&&» раньше, чем « not » на «!», и съедает
        // пробел перед not — «a and not b» становился «a&&not b», и условие падало с ошибкой
        // (проверено в движке AXMA на «Слуге Ра»); «!» в условии — JavaScript как есть
        return "!";
    }

    @Override
    protected String decorateOr() {
        return "or";
    }

    @Override
    protected String decorateAnd() {
        return "and";
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        // «==», а не «eq»: AXMA переводит eq в == только в <<if>>, а в <<set>> (присваивание
        // логического выражения, «Шапочка», «Фронтир») «eq» ломал компиляцию абзаца
        return "($" + constraintVar + " == 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return "$" + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return "$" + constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return ":: " + linkId + "[::]" + "10-10-0" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return EMPTY_STRING;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        // ссылку без текста ([[|абзац]], например возврат из модуля во «Фронтире») редактор AXMA
        // не компилирует («Некорректная ссылка»), и игра не собирается: подпись по умолчанию
        return "[[" + (linkText == null || linkText.trim().isEmpty() ? defaultLinkText() : linkText) + "|" + linkId + "]]" + LINE_SEPARATOR;
    }

    private String defaultLinkText() {
        String text = Link.DEFAULT_TEXT.get(m_language);
        return StringHelper.isEmpty(text) ? Link.DEFAULT_TEXT.get("en") : text;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkSource,
            int sourcePageNumber,
            String linkTarget,
            int targetPageNumber,
            boolean isTechnical,
            Theme theme) {
        return "<<goto '" + linkTarget + "'>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return LINE_SEPARATOR;
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder postPage = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            if (!linkBlocks.isTechnical()) {
                // переход по обычной ссылке — действие игрока (для автоссылок «needs action» и
                // COUNTGET); он же снимает выбор предмета для применения
                postPage.append(count("walk")).append("<<set $nlb_sel = 0>>").append(LINE_SEPARATOR);
            } else if (linkBlocks.isAuto() && linkBlocks.isNeedsAction()) {
                // has_action() сбрасывает счётчики действий — те же, что COUNTRST (как в плеере)
                postPage.append(decorateCountResetOperation());
            }
            postPage.append(linkBlocks.getLinkModifications());
            postPage.append(linkBlocks.getLinkVariable());
            postPage.append(linkBlocks.getLinkVisitStateVariable());
            postPage.append(linkBlocks.getLinkGoTo());
        }
        return postPage.toString();
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return "<<set $" + variableName + " = 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "<<set $" + linkVisitStateVariable + " = 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(final String variableName) {
        return "<<set $" + variableName + " = 1" + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
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

    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return "''" + escapeMarkup(caption.toUpperCase()).replace("'", "&#39;") + "''" + LINE_SEPARATOR;
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
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        if (pageNumber == 1) {
            return (
                    ":: Start[::]0-0-0" + LINE_SEPARATOR
                            // начальные значения (счётчики, места объектов): необъявленная
                            // переменная в JavaScript — ошибка
                            + "<<display 'nlb_init'>>" + LINE_SEPARATOR
                            + "<<goto '" + labelText + "'>>" + LINE_SEPARATOR + LINE_SEPARATOR
                            + ":: " + labelText + "[::]" + (pageNumber % 10) * 150 + "-" + (pageNumber / 10) * 150 + "-0" + LINE_SEPARATOR
            );
        } else {
            return ":: " + labelText + "[::]" + (pageNumber % 10) * 150 + "-" + (pageNumber / 10) * 150 + "-0" + LINE_SEPARATOR;
        }

    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return "<<set " + variableName + " = " + variableValue + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWhile(final String constraint) {
        // В AXMA только цикл со счётчиком (<<loop N>> -> for); условие проверяется в начале тела
        m_openBlocks.push(Boolean.TRUE);
        return "<<loop 1000000000>>" + LINE_SEPARATOR
                + "<<if not (" + constraint + ")>>" + LINE_SEPARATOR
                + "<<break>>" + LINE_SEPARATOR
                + "<<endif>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        m_openBlocks.push(Boolean.FALSE);
        return "<<if " + constraint + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // место объекта — $nlb_in_N (объект из переменной экспорт не отслеживает: его нет)
        m_openBlocks.push(Boolean.FALSE);
        return "<<if " + (objId != null ? "$nlb_in_" + key(objId) + " eq \"" + INVENTORY + "\"" : "false") + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElse() {
        return "<<else>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return "<<elseif " + constraint + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        // END в NLB закрывает и IF, и WHILE, а в AXMA для них разные макросы
        boolean loop = !m_openBlocks.isEmpty() && m_openBlocks.pop();
        return (loop ? "<<endloop>>" : "<<endif>>") + LINE_SEPARATOR;
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null || destinationName != null) {
            return EMPTY_STRING;  // объект или место из переменной — не поддерживается
        }
        // без места — из текущей страницы (here() в INSTEAD)
        String destination = destinationId != null ? "\"" + destinationId + "\"" : "$nlb_page";
        return "<<if $nlb_in_" + key(objectId) + " eq " + destination + ">>" + place(key(objectId), "\"\"") + "<<endif>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "<<if $nlb_in_" + key(objectId) + " eq \"" + INVENTORY + "\">>" + place(key(objectId), "\"\"") + "<<endif>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // без места — на текущую страницу (here() в INSTEAD)
        String destination = destinationId != null ? "\"" + destinationId + "\"" : "$nlb_page";
        return place(key(objectId), destination) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return place(key(objectId), "\"" + INVENTORY + "\"") + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWSndOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateClearInvOperation() {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        // rnd(n) в NLB — целое от 1 до n; <<set>> компилируется в JavaScript как есть
        return "<<set " + variableName + " = Math.floor(Math.random() * (" + maxValue + ")) + 1>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // <<goto>> прерывает выполнение абзаца: переход из модификаций ссылки важнее цели ссылки
        return "<<goto '" + locationId + "'>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        // счётчики действий игрока по видам (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8
        return "<<set " + variableName + " = " + countSum(statType) + ">>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateCountResetOperation() {
        // сброс счётчиков действий — и для автоссылок «needs action»: они ждут действий игрока
        // с последнего сброса (как в плеере, PlayerEngine.hasAction)
        StringBuilder result = new StringBuilder("<<set $" + ACTION_COUNT_VAR + " = 0>>");
        for (String type : COUNT_TYPES) {
            result.append("<<set $nlb_cntr_").append(type).append(" = $nlb_cnt_").append(type).append(">>");
        }
        return result.append(LINE_SEPARATOR).toString();
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        // значение выражения — в ответ на действие, без перевода строки
        return "<<set $nlb_reply = $nlb_reply + (" + variableName + ")>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // объект задан именем ($имя): номер известен только при сборке текста (generateNLBText)
        String name = objVariableName.startsWith("$") ? objVariableName.substring(1) : objVariableName;
        return "<<display 'nlb_o_" + NAME_MARK + name + END_MARK + "_text'>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        if (objId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // описания объектов внутри объекта
        return "<<set $nlb_ws = $nlb_where>><<set $nlb_where = \"" + objId + "\">><<display 'nlb_show_in'>><<set $nlb_where = $nlb_ws>>" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        if (actingObjId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "<<display 'nlb_o_" + key(actingObjId) + "_act'>>" + LINE_SEPARATOR;
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
        return "<<set $nlb_o = " + key(sourceId) + ">><<set $nlb_t = " + key(targetId) + ">><<set $nlb_printed = 0>>"
                + "<<display 'nlb_o_" + key(sourceId) + "_use'>>" + LINE_SEPARATOR;
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
        return "<>";
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
}
