/**
 * @(#)URQExportManager.java
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
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The URQExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/4/13
 */
public class URQExportManager extends TextExportManager {
    // Windows line separator should be used for URQ format
    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * Блок модификаций (IF … END или WHILE … END). В URQ условие пишется только в одну строку,
     * поэтому блоки собираются из меток и переходов: ложное условие ветки — переход к метке
     * следующей ветки, конец ветки — переход к метке конца блока, цикл — переход к его началу.
     * Метки внутри кода страницы UrqW при выполнении пропускает.
     */
    private final class Block {
        private final int m_id = ++m_blockCount;
        private final boolean m_loop;
        private int m_branch = 1;

        private Block(boolean loop) {
            m_loop = loop;
        }

        private String branchLabel() {
            return "nlb_if_" + m_id + "_" + m_branch;
        }

        private String endLabel() {
            return (m_loop ? "nlb_while_" : "nlb_if_") + m_id + "_end";
        }

        private String loopLabel() {
            return "nlb_while_" + m_id;
        }
    }

    private int m_blockCount = 0;

    private final Deque<Block> m_openBlocks = new ArrayDeque<>();

    public URQExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /** Место «инвентарь» в nlb_in_N */
    private static final String INVENTORY = "nlb_inv";
    /** Метка ключа объекта (номер) до сборки текста: id -> номер (generateNLBText) */
    private static final String KEY_MARK = "\u0001nlb_key:";
    /** Метка объекта по имени (PDSC) до сборки текста */
    private static final String NAME_MARK = "\u0001nlb_name:";
    private static final String END_MARK = "\u0002";
    /** Остаток от деления до сборки текста (в UrqW оператора нет: a-b*int(a/b)) */
    private static final String MOD_MARK = "\u0003";
    private static final java.util.regex.Pattern MOD_OPERANDS = java.util.regex.Pattern.compile(
            "([\\w.]+|\\([^()]*\\))\\s*" + MOD_MARK + "\\s*([\\w.]+|\\([^()]*\\))");

    @Override
    protected String decorateMod() {
        return MOD_MARK;
    }
    private static final String[] COUNT_TYPES = {"inv", "act", "use", "walk"};

    private String m_gameInvText = EMPTY_STRING;
    private String m_gameNouseText = EMPTY_STRING;

    private static String key(String objId) {
        return KEY_MARK + objId + END_MARK;
    }

    /**
     * Начало игры и служебные метки объектов. Объектов в URQ нет, есть инвентарь (inv+/inv-,
     * действия предмета — метки use_…), поэтому объекты книги пронумерованы: место объекта N —
     * строковая переменная nlb_in_N (id страницы или объекта-контейнера, «nlb_inv» — инвентарь,
     * "" — нигде), в инвентаре он — предмет nlbo_N с подписью display_use_nlbo_N, в тексте
     * страницы — ссылка [[текст|nlb_act_N]]. Ответ на действие копится в nlb_reply и выводится
     * после повторного показа страницы (как в INSTEAD: сцена, затем ответ).
     */
    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_gameInvText = urqString(nlbBuildingBlocks.getGameInvText());
        m_gameNouseText = urqString(nlbBuildingBlocks.getGameNouseText());
        final List<PageBuildingBlocks> pages = nlbBuildingBlocks.getPagesBuildingBlocks();
        final List<ObjBuildingBlocks> objs = m_orderedObjs;
        StringBuilder sb = new StringBuilder();
        sb.append("; Generated with Non-Linear Book Builder, http://nlbhub.com").append(LINE_SEPARATOR);
        sb.append(":nlb_start").append(LINE_SEPARATOR);
        sb.append("nlb_sel=0").append(LINE_SEPARATOR);
        sb.append("nlb_reply=\"\"").append(LINE_SEPARATOR);
        for (int i = 0; i < objs.size(); i++) {
            sb.append("nlb_in_").append(i + 1).append("=\"").append(objs.get(i).getContainerId()).append("\"").append(LINE_SEPARATOR);
        }
        if (!pages.isEmpty()) {
            sb.append("goto ").append(pages.get(0).getPageId()).append(LINE_SEPARATOR);
        }
        // объекты места nlb_where (порядок — порядок объектов, как в QSP-экспорте)
        sb.append(":nlb_show_in").append(LINE_SEPARATOR);
        for (int i = 1; i <= objs.size(); i++) {
            sb.append("if nlb_in_").append(i).append("=nlb_where then proc nlb_o_").append(i).append("_dsc").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        // подписи предметов (с переменными — пересчитываются каждый ход)
        sb.append(":nlb_disp_all").append(LINE_SEPARATOR);
        for (int i = 1; i <= objs.size(); i++) {
            sb.append("display_use_nlbo_").append(i).append("=\"").append(objDisp(objs.get(i - 1))).append("\"").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);
        sb.append(String.join(LINE_SEPARATOR,
                // щелчок по объекту в тексте: действие или (выбран предмет) применение
                ":nlb_act",
                "if nlb_sel<>0 then goto nlb_act_use",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_act=nlb_cnt_act+1",
                "proc nlb_o_#nlb_o$_act",
                "goto nlb_redraw",
                ":nlb_act_use",
                "nlb_t=nlb_o",
                "nlb_o=nlb_sel",
                "goto nlb_use",
                // применение nlb_o к nlb_t
                ":nlb_use",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_use=nlb_cnt_use+1",
                "nlb_printed=0",
                "proc nlb_o_#nlb_o$_use",
                "if nlb_printed=0 then proc nlb_o_#nlb_o$_nouse",
                "nlb_sel=0",
                "goto nlb_redraw",
                // щелчок по предмету: первый — inv (применение к себе) и выбор для применения,
                // другой предмет — применение, тот же — отмена выбора
                ":nlb_invclick",
                "if nlb_sel=0 then goto nlb_invfirst",
                "if nlb_sel=nlb_o2 then goto nlb_invcancel",
                "nlb_o=nlb_sel",
                "nlb_t=nlb_o2",
                "goto nlb_use",
                ":nlb_invcancel",
                "nlb_sel=0",
                "goto nlb_redraw",
                ":nlb_invfirst",
                ACTION_COUNT_VAR + "=" + ACTION_COUNT_VAR + "+1",
                "nlb_cnt_inv=nlb_cnt_inv+1",
                "nlb_printed=0",
                "nlb_o=nlb_o2",
                "nlb_t=nlb_o2",
                "proc nlb_o_#nlb_o$_use",
                "if nlb_printed=0 then nlb_reply=nlb_reply+\"" + m_gameInvText + "<br>\"",
                "nlb_sel=nlb_o2",
                "goto nlb_redraw",
                // показать страницу заново: сцена, ответ, кнопки
                ":nlb_redraw",
                "cls",
                "proc #%nlb_page$_show",
                "if nlb_reply<>\"\" then pln #%nlb_reply$",
                "nlb_reply=\"\"",
                "goto #%nlb_page$_turn",
                // перенести объект nlb_po в nlb_pd (с инвентарём UrqW)
                ":nlb_place",
                "if nlb_in_#nlb_po$=\"" + INVENTORY + "\" then inv- nlbo_#nlb_po$",
                "if nlb_pd=\"" + INVENTORY + "\" then goto nlb_place_inv",
                "nlb_in_#nlb_po$=nlb_pd",
                "end",
                ":nlb_place_inv",
                "if nlb_in_#nlb_po$<>\"" + INVENTORY + "\" then inv+ nlbo_#nlb_po$",
                "nlb_in_#nlb_po$=nlb_pd",
                "end",
                ""));
        return sb.toString();
    }

    private String objDisp(ObjBuildingBlocks obj) {
        if (StringHelper.notEmpty(obj.getObjDisp())) {
            return obj.getObjDisp();
        }
        return StringHelper.notEmpty(obj.getObjName()) ? urqString(obj.getObjName()) : obj.getObjId();
    }

    /** Текст для строки URQ в кавычках и для pln: & — разделитель команд, " — конец строки */
    private static String urqString(String text) {
        if (StringHelper.isEmpty(text)) {
            return EMPTY_STRING;
        }
        return text.replace("&", "##38$").replace("\"", "##34$");
    }

    /** Добавить к ответу (выводится после повторного показа страницы) */
    private static String reply(String text) {
        return "nlb_reply=nlb_reply+\"" + text + "<br>\"" + LINE_SEPARATOR;
    }

    /**
     * Объект N — служебные метки (подпрограммы): _dsc (описание со ссылкой и содержимое
     * контейнера; условие объекта скрывает и содержимое), _text (описание — в ответ, для PDSC),
     * _act (действие: текст действия, модификации, общий объект; переносимый — в инвентарь),
     * _acttext, _mods, _use (применения к nlb_t), _nouse; use_nlbo_N — щелчок по предмету.
     */
    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        final String id = objBlocks.getObjId();
        final int n = m_keys.get(id);
        final String prefix = ":nlb_o_" + n;
        final String common = objBlocks.getCommonObjId();
        StringBuilder sb = new StringBuilder();
        sb.append("; Object -- ").append(objBlocks.getObjName()).append(LINE_SEPARATOR);
        sb.append(prefix).append("_dsc").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(objBlocks.getObjConstraint())) {
            sb.append("if not (").append(objBlocks.getObjConstraint()).append(") then end").append(LINE_SEPARATOR);
        }
        // ссылка без текста ({} у графического объекта) — подписью объекта: «[[|метка]]» UrqW
        // ссылкой не считает
        final String label = StringHelper.notEmpty(objBlocks.getObjDisp()) ? objBlocks.getObjDisp()
                : StringHelper.notEmpty(objBlocks.getObjName()) ? urqString(objBlocks.getObjName()) : "…";
        final String text = objBlocks.getObjText().replace("[[|nlb_act_", "[[" + label + "|nlb_act_");
        if (!text.isEmpty()) {
            sb.append("pln ").append(text).append(LINE_SEPARATOR);
        } else if (objBlocks.isGraphical() && StringHelper.notEmpty(objBlocks.getObjDisp())) {
            // графический объект (VN) без текста щёлкают по картинке, а картинок здесь нет
            sb.append("pln [[").append(objBlocks.getObjDisp()).append("|nlb_act_").append(n).append("]]").append(LINE_SEPARATOR);
        }
        sb.append("nlb_where=\"").append(id).append("\"").append(LINE_SEPARATOR);
        sb.append("proc nlb_show_in").append(LINE_SEPARATOR);
        sb.append("nlb_where=nlb_in_").append(n).append(LINE_SEPARATOR);
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_text").append(LINE_SEPARATOR);
        if (!text.isEmpty()) {
            sb.append(reply(text));
        }
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(":nlb_act_").append(n).append(LINE_SEPARATOR);
        sb.append("nlb_o=").append(n).append(LINE_SEPARATOR);
        sb.append("goto nlb_act").append(LINE_SEPARATOR);
        sb.append(":use_nlbo_").append(n).append(LINE_SEPARATOR);
        sb.append("nlb_o2=").append(n).append(LINE_SEPARATOR);
        sb.append("goto nlb_invclick").append(LINE_SEPARATOR);

        sb.append(prefix).append("_acttext").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjActStart());
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_mods").append(LINE_SEPARATOR);
        sb.append(objBlocks.getObjModifications());
        sb.append(objBlocks.getObjVariable());
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_act").append(LINE_SEPARATOR);
        sb.append("proc nlb_o_").append(n).append("_acttext").append(LINE_SEPARATOR);
        sb.append("proc nlb_o_").append(n).append("_mods").append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common) && m_keys.containsKey(common)) {
            int c = m_keys.get(common);
            if (StringHelper.isEmpty(objBlocks.getObjActStart())) {
                sb.append("proc nlb_o_").append(c).append("_acttext").append(LINE_SEPARATOR);
            }
            sb.append("proc nlb_o_").append(c).append("_mods").append(LINE_SEPARATOR);
        }
        if (objBlocks.isTakable()) {
            String toTake = StringHelper.notEmpty(common) && m_keys.containsKey(common) && Boolean.TRUE.equals(m_takable.get(common)) ? common : id;
            sb.append("nlb_po=").append(m_keys.get(toTake)).append(LINE_SEPARATOR);
            sb.append("nlb_pd=\"").append(INVENTORY).append("\"").append(LINE_SEPARATOR);
            sb.append("proc nlb_place").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_use").append(LINE_SEPARATOR);
        int u = 0;
        for (UseBuildingBlocks use : objBlocks.getUseBuildingBlocks()) {
            u++;
            final String next = "nlb_o_" + n + "_use_" + (u + 1);
            final boolean hasSuccess = StringHelper.notEmpty(use.getUseSuccessText());
            final boolean useConstrained = StringHelper.notEmpty(use.getUseConstraint());
            sb.append(":nlb_o_").append(n).append("_use_").append(u).append(LINE_SEPARATOR);
            sb.append("if nlb_t<>").append(use.getUseTarget()).append(" then goto ").append(next).append(LINE_SEPARATOR);
            if (useConstrained) {
                sb.append("if not (").append(use.getUseConstraint()).append(") then goto nlb_o_").append(n).append("_use_").append(u).append("_fail").append(LINE_SEPARATOR);
            }
            if (hasSuccess) {
                sb.append(reply(use.getUseSuccessText()));
                sb.append("nlb_printed=1").append(LINE_SEPARATOR);
            }
            sb.append(use.getUseModifications());
            sb.append(use.getUseVariable());
            sb.append("goto ").append(next).append(LINE_SEPARATOR);
            if (useConstrained) {
                sb.append(":nlb_o_").append(n).append("_use_").append(u).append("_fail").append(LINE_SEPARATOR);
                if (hasSuccess && StringHelper.notEmpty(use.getUseFailureText())) {
                    sb.append(reply(use.getUseFailureText()));
                    sb.append("nlb_printed=1").append(LINE_SEPARATOR);
                }
            }
        }
        sb.append(":nlb_o_").append(n).append("_use_").append(u + 1).append(LINE_SEPARATOR);
        if (StringHelper.notEmpty(common) && m_keys.containsKey(common)) {
            sb.append("if nlb_printed=0 then proc nlb_o_").append(m_keys.get(common)).append("_use").append(LINE_SEPARATOR);
        }
        sb.append("end").append(LINE_SEPARATOR);

        sb.append(prefix).append("_nouse").append(LINE_SEPARATOR);
        String nouse = objBlocks.getObjNouse();
        sb.append(reply(StringHelper.notEmpty(nouse) ? nouse : m_gameNouseText));
        sb.append("end").append(LINE_SEPARATOR);
        return sb.toString();
    }

    private final java.util.Map<String, Integer> m_keys = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> m_takable = new java.util.HashMap<>();
    private List<ObjBuildingBlocks> m_orderedObjs = new java.util.ArrayList<>();

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
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(" + java.util.regex.Pattern.quote(KEY_MARK) + "|" + java.util.regex.Pattern.quote(NAME_MARK) + ")(.*?)"
                        + java.util.regex.Pattern.quote(END_MARK)).matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String id = KEY_MARK.equals(matcher.group(1)) ? matcher.group(2) : idByName.get(matcher.group(2));
            Integer n = id == null ? null : m_keys.get(id);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(n == null ? "0" : String.valueOf(n)));
        }
        matcher.appendTail(result);
        // a % b -> (a-b*int(a/b))
        String withMod = result.toString();
        String previous;
        do {
            previous = withMod;
            withMod = MOD_OPERANDS.matcher(withMod).replaceAll("($1-$2*int($1/$2))");
        } while (!withMod.equals(previous));
        return withMod;
    }

    /** Объекты в порядке вывода: содержимое страниц (с вложенными объектами), затем остальные */
    private static List<ObjBuildingBlocks> orderedObjects(NLBBuildingBlocks nlbBuildingBlocks) {
        java.util.Map<String, ObjBuildingBlocks> byId = new java.util.LinkedHashMap<>();
        for (ObjBuildingBlocks obj : nlbBuildingBlocks.getObjsBuildingBlocks()) {
            byId.put(obj.getObjId(), obj);
        }
        java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>();
        for (PageBuildingBlocks page : nlbBuildingBlocks.getPagesBuildingBlocks()) {
            for (String id : page.getContainedObjIds()) {
                addWithContents(id, byId, order);
            }
        }
        for (String id : byId.keySet()) {
            addWithContents(id, byId, order);
        }
        List<ObjBuildingBlocks> result = new java.util.ArrayList<>();
        for (String id : order) {
            result.add(byId.get(id));
        }
        return result;
    }

    private static void addWithContents(String id, java.util.Map<String, ObjBuildingBlocks> byId, java.util.Set<String> order) {
        ObjBuildingBlocks obj = byId.get(id);
        if (obj == null || !order.add(id)) {
            return;
        }
        for (String contained : obj.getContainedObjIds()) {
            addWithContents(contained, byId, order);
        }
    }

    // --- части объекта (ExportManager.createObjBuildingBlocks)

    @Override
    protected String decorateObjName(String name, String id) {
        return name == null ? EMPTY_STRING : name;
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        return dispText == null ? EMPTY_STRING : dispText.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING);
    }

    /** Описание объекта: часть в {фигурных скобках} — ссылка на nlb_act_N */
    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        if (suppressDsc || StringHelper.isEmpty(objText)) {
            return EMPTY_STRING;
        }
        String text = objText.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING);
        return text.replaceAll("\\{([^}]*)\\}",
                java.util.regex.Matcher.quoteReplacement("[[") + "$1" + java.util.regex.Matcher.quoteReplacement("|nlb_act_" + key(objId) + "]]"));
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        String text = actTextExpanded == null ? EMPTY_STRING : actTextExpanded.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING).trim();
        return text.isEmpty() ? EMPTY_STRING : reply(text);
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        return nouseTextExpanded == null ? EMPTY_STRING : nouseTextExpanded.replace(LINE_SEPARATOR, " ").replace("pln ", EMPTY_STRING).trim();
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
        return variableName + "=1" + LINE_SEPARATOR;
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
        return variableName + "=1" + LINE_SEPARATOR;
    }

    /**
     * Страница — три метки. Вход «:id»: модификации страницы (до текста, как enter в INSTEAD),
     * затем показ и ход. Показ «:id_show» (подпрограмма): заголовок, текст и объекты страницы — его
     * повторяет и действие с объектом (nlb_redraw), уже без модификаций входа. Ход «:id_turn»:
     * подписи предметов, автоссылки, кнопки.
     */
    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        final String pageId = pageBlocks.getPageId();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(pageBlocks.getPageComment());
        stringBuilder.append(pageBlocks.getPageLabel());
        stringBuilder.append(pageBlocks.getPageModifications());
        stringBuilder.append(pageBlocks.getPageVariable());
        stringBuilder.append("nlb_page = \"").append(pageId).append("\"").append(LINE_SEPARATOR);
        stringBuilder.append("proc ").append(pageId).append("_show").append(LINE_SEPARATOR);
        stringBuilder.append("goto ").append(pageId).append("_turn").append(LINE_SEPARATOR);
        stringBuilder.append(":").append(pageId).append("_show").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append("nlb_where = \"").append(pageId).append("\"").append(LINE_SEPARATOR);
        stringBuilder.append("proc nlb_show_in").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);
        stringBuilder.append(":").append(pageId).append("_turn").append(LINE_SEPARATOR);
        stringBuilder.append("proc nlb_disp_all").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        // Автоссылки — до кнопок: переход срабатывает сам, как в INSTEAD, и кнопки этой страницы
        // уже не выводятся. goto в UrqW не очищает экран, поэтому текст страницы остаётся виден
        // перед текстом следующей (как в плеере). Первая автоссылка с выполненным условием
        // уводит со страницы.
        boolean unconditionalAuto = false;
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                String constraint = linkBlocks.getLinkConstraint();
                if (linkBlocks.isNeedsAction()) {
                    // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий
                    String hasAction = "(" + ACTION_COUNT_VAR + ">=" + getNeedsActionCount() + ")";
                    constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") and " + hasAction;
                }
                if (StringHelper.isEmpty(constraint)) {
                    unconditionalAuto = true;
                } else {
                    stringBuilder.append("if ").append(constraint).append(" then ");
                }
                stringBuilder.append("goto ").append(linkBlocks.getLinkId()).append(LINE_SEPARATOR);
                if (unconditionalAuto) {
                    // дальше ничего не выполнится: остальные ссылки не нужны
                    break;
                }
            }
        }
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto() || unconditionalAuto) {
                continue;
            }
            if (!StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                stringBuilder.append("if ").append(linkBlocks.getLinkConstraint()).append(" then ");
            }
            if (linkBlocks.hasShownAltText()) {
                // альтернативный текст недоступной ссылки (#135): «if … then btn … else pln …» —
                // одной строкой, else в UrqW — до конца строки
                stringBuilder.append(linkBlocks.getLinkStart().stripTrailing());
                stringBuilder.append(" else pln ").append(linkBlocks.getLinkAltText()).append(LINE_SEPARATOR);
            } else {
                stringBuilder.append(linkBlocks.getLinkStart());
            }
        }
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder postPage = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            if (!linkBlocks.isTechnical()) {
                // переход по обычной ссылке — действие игрока (для автоссылок «needs action»);
                // он же снимает выбор предмета для применения
                postPage.append(ACTION_COUNT_VAR).append("=").append(ACTION_COUNT_VAR).append("+1").append(LINE_SEPARATOR);
                postPage.append("nlb_cnt_walk=nlb_cnt_walk+1").append(LINE_SEPARATOR);
                postPage.append("nlb_sel=0").append(LINE_SEPARATOR);
            } else if (linkBlocks.isAuto() && linkBlocks.isNeedsAction()) {
                postPage.append(ACTION_COUNT_VAR).append("=0").append(LINE_SEPARATOR);
            }
            postPage.append(linkBlocks.getLinkModifications());
            postPage.append(linkBlocks.getLinkVariable());
            postPage.append(linkBlocks.getLinkVisitStateVariable());
            postPage.append(linkBlocks.getLinkGoTo());
        }
        return postPage.toString();
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        return "btn " + linkId + ", " + linkText + LINE_SEPARATOR;
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
        return "goto " + linkTarget + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTrue() {
        return "1";
    }

    @Override
    protected String decorateFalse() {
        return "0";
    }

    @Override
    protected String decorateEq() {
        return "=";
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

    /**
     * @return
     */
    @Override
    protected String decorateNot() {
        return "not ";
    }

    /**
     * @return
     */
    @Override
    protected String decorateOr() {
        return "or";
    }

    /**
     * @return
     */
    @Override
    protected String decorateAnd() {
        return "and";
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    /**
     * @param constraintVar
     * @return
     */
    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return "(" + constraintVar + ">0)";//"(" + constraintVar + " == 1)";
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return constraintVar;
    }

    /**
     * @return
     * @param isFinish
     */
    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "end" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return ":" + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "; Link -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decorateLinkVariable(String variableName) {
        return "    " + variableName + "=1" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "    " + linkVisitStateVariable + "=1" + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decoratePageVariable(final String variableName) {
        return variableName + "=1" + LINE_SEPARATOR;
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

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return (
                    "pln " + caption.toUpperCase() + LINE_SEPARATOR
                            + "pln " + StringHelper.createRepeatedString(caption.length(), "-") + LINE_SEPARATOR
                            + "pln" + LINE_SEPARATOR
            );
        } else {
            return "";
        }
    }

    @Override
    protected String decoratePageNotes(String notes) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        // TODO: support image constraints
        ImagePathData pageImagePathData = pageImagePathDatas.get(0);
        if (pageImagePathData.getMaxFrameNumber() == 0) {
            String pageImagePath = pageImagePathData.getImagePath();
            if (!StringHelper.isEmpty(pageImagePath)) {
                return "image " + pageImagePath + LINE_SEPARATOR;
            } else {
                return Constants.EMPTY_STRING;
            }
        } else {
            // TODO: support animated images
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        return Constants.EMPTY_STRING;
    }

    /**
     * Подпись кнопки и альтернативный текст ссылки — одной строкой и без «pln »: раньше подпись
     * шла через expandVariables текста страницы, и кнопка называлась «pln Открыть дверь» (UrqW
     * берёт подписью всё после первой запятой btn)
     */
    /** Переменная в тексте UrqW: #имя$ (строковая — #%имя$) — значение подставляется при выводе */
    private String urqVariable(String name) {
        return "#" + (getVariableDataType(name) == Variable.DataType.STRING ? "%" : EMPTY_STRING) + name + "$";
    }

    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(urqString(textChunk.getText()));
                    break;
                case VARIABLE:
                    result.append(urqVariable(textChunk.getText()));
                    break;
                case ACTION_TEXT:
                    break;
                case NEWLINE:
                    result.append(" ");
                    break;
            }
        }
        return result.toString();
    }

    /** Тексты объектов (общий код вызывает эту версию) — так же, как текст страницы */
    @Override
    protected String expandVariables(List<TextChunk> textChunks) {
        return expandVariables(textChunks, null);
    }

    /** Текст страницы: каждая строка — pln (раньше pln ставился перед каждым куском текста) */
    @Override
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        StringBuilder result = new StringBuilder("pln ");
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(urqString(textChunk.getText()));
                    break;
                case VARIABLE:
                    result.append(urqVariable(textChunk.getText()));
                    break;
                case ACTION_TEXT:
                    break;
                case NEWLINE:
                    result.append(getLineSeparator()).append("pln ");
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
        return LINE_SEPARATOR;
    }

    /**
     * @param labelText
     * @param pageNumber
     * @param theme
     * @return
     */
    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        return ":" + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "; Page -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @param variableValue
     * @return
     */
    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + "=" + variableValue + LINE_SEPARATOR;
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
        Block block = new Block(true);
        m_openBlocks.push(block);
        return ":" + block.loopLabel() + LINE_SEPARATOR
                + "if not (" + constraint + ") then goto " + block.endLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        Block block = new Block(false);
        m_openBlocks.push(block);
        return "if not (" + constraint + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // место объекта — nlb_in_N (объект из переменной экспорт не отслеживает: его нет)
        Block block = new Block(false);
        m_openBlocks.push(block);
        String condition = objId != null ? "nlb_in_" + key(objId) + "=\"" + INVENTORY + "\"" : "0";
        return "if not (" + condition + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElse() {
        Block block = m_openBlocks.peek();
        if (block == null) {
            return EMPTY_STRING;
        }
        String result = "goto " + block.endLabel() + LINE_SEPARATOR + ":" + block.branchLabel() + LINE_SEPARATOR;
        block.m_branch++;
        return result;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        Block block = m_openBlocks.peek();
        if (block == null) {
            return EMPTY_STRING;
        }
        String result = "goto " + block.endLabel() + LINE_SEPARATOR + ":" + block.branchLabel() + LINE_SEPARATOR;
        block.m_branch++;
        return result + "if not (" + constraint + ") then goto " + block.branchLabel() + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        Block block = m_openBlocks.poll();
        if (block == null) {
            return EMPTY_STRING;
        }
        if (block.m_loop) {
            return "goto " + block.loopLabel() + LINE_SEPARATOR + ":" + block.endLabel() + LINE_SEPARATOR;
        }
        // метка последней ветки нужна, если ELSE не было (на неё переходит ложное условие)
        return ":" + block.branchLabel() + LINE_SEPARATOR + ":" + block.endLabel() + LINE_SEPARATOR;
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
        String destination = destinationId != null ? "\"" + destinationId + "\"" : "nlb_page";
        return "nlb_po=" + key(objectId) + LINE_SEPARATOR
                + "nlb_pd=\"\"" + LINE_SEPARATOR
                + "if nlb_in_" + key(objectId) + "=" + destination + " then proc nlb_place" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "nlb_po=" + key(objectId) + LINE_SEPARATOR
                + "nlb_pd=\"\"" + LINE_SEPARATOR
                + "if nlb_in_" + key(objectId) + "=\"" + INVENTORY + "\" then proc nlb_place" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        // без назначения — в текущую комнату (here() в INSTEAD)
        return "nlb_po=" + key(objectId) + LINE_SEPARATOR
                + "nlb_pd=" + (destinationId != null ? "\"" + destinationId + "\"" : "nlb_page") + LINE_SEPARATOR
                + "proc nlb_place" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (objectId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "nlb_po=" + key(objectId) + LINE_SEPARATOR
                + "nlb_pd=\"" + INVENTORY + "\"" + LINE_SEPARATOR
                + "proc nlb_place" + LINE_SEPARATOR;
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
        // TODO: implement adding to the arbitrary list
        return Constants.EMPTY_STRING;
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
        // rndN в UrqW — целое от 1 до N; #выражение$ UrqW вычисляет и подставляет в строку
        return variableName + "=rnd#" + maxValue + "$" + LINE_SEPARATOR;
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
        // goto уводит на страницу, оставшиеся модификации не выполняются: переход из модификаций
        // ссылки важнее цели ссылки (как в INSTEAD)
        return "goto " + locationId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        // счётчики действий игрока по видам (как PlayerContext.countGet): inv 1, act 2, use 4, walk 8
        StringBuilder sum = new StringBuilder();
        for (int i = 0; i < COUNT_TYPES.length; i++) {
            if (statType == 0 || (statType & (1 << i)) != 0) {
                if (sum.length() > 0) {
                    sum.append("+");
                }
                sum.append("(nlb_cnt_").append(COUNT_TYPES[i]).append("-nlb_cntr_").append(COUNT_TYPES[i]).append(")");
            }
        }
        return variableName + "=" + (sum.length() > 0 ? sum.toString() : "0") + LINE_SEPARATOR;
    }

    @Override
    protected String decorateCountResetOperation() {
        StringBuilder result = new StringBuilder();
        for (String type : COUNT_TYPES) {
            result.append("nlb_cntr_").append(type).append("=nlb_cnt_").append(type).append(LINE_SEPARATOR);
        }
        return result.toString();
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
        // значение выражения — в ответ (#выражение$ UrqW подставляет при разборе строки)
        return "nlb_reply=nlb_reply+\"#" + variableName + "$\"" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // описание объекта — в ответ; объект по имени, номер известен при сборке текста
        return "proc nlb_o_" + NAME_MARK + objVariableName + END_MARK + "_text" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        if (actingObjId == null) {
            return EMPTY_STRING;  // объект из переменной — не поддерживается
        }
        return "proc nlb_o_" + key(actingObjId) + "_act" + LINE_SEPARATOR;
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
        // TODO: implement
        return EMPTY_STRING;
    }
}
