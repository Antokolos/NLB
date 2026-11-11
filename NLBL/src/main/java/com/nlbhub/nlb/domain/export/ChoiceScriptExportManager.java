/**
 * @(#)ChoiceScriptExportManager.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2015 Anton P. Kolosov
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
 * Copyright (c) 2015 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ChoiceScriptExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class ChoiceScriptExportManager extends TextExportManager {
    // Windows line separator should be used for URQ format
    private static final String LINE_SEPARATOR = "\r\n";

    /**
     * Начало строки-заготовки блока модификаций (if, elseif, else, while, end). Блоки ChoiceScript
     * задаются отступами, а модификации собираются плоским списком, поэтому сначала пишутся
     * заготовки, а {@link #indentBlocks(String)} превращает их в *if/*elseif/*else с отступами.
     */
    private static final String BLOCK_MARK = "*nlb_block ";
    private static final String INDENT = "    ";
    private static final int LOOP_LIMIT = 100000;
    /** Строка варианта *choice: отступ, #, текст */
    private static final Pattern OPTION_LINE = Pattern.compile("(?m)^(\\s*#)([^\\r\\n]*)");
    /** Язык книги — для подписи варианта у ссылки без текста */
    private String m_language = "en";
    /** Счётчик меток циклов (WHILE): *label nlb_while_N */
    private int m_loopCount = 0;

    public ChoiceScriptExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    /**
     * Заготовки блоков — в ChoiceScript: IF/ELSEIF/ELSE — *if/*elseif/*else с отступом тела,
     * WHILE — *label и *if, в конце тела которого *goto на эту метку. Выход из ветки в *else
     * разрешает *create implicit_control_flow true (см. generateVariableInitializationText).
     * Пустое тело ChoiceScript не принимает — в него пишется *comment.
     */
    private String indentBlocks(String modificationsText) {
        if (!modificationsText.contains(BLOCK_MARK)) {
            return modificationsText;
        }
        StringBuilder result = new StringBuilder();
        java.util.Deque<String> loops = new java.util.ArrayDeque<>();
        int depth = 0;
        boolean emptyBody = false;
        for (String line : modificationsText.split(LINE_SEPARATOR, -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!trimmed.startsWith(BLOCK_MARK)) {
                result.append(INDENT.repeat(depth)).append(trimmed).append(LINE_SEPARATOR);
                emptyBody = false;
                continue;
            }
            String block = trimmed.substring(BLOCK_MARK.length());
            String command = block.split(" ", 2)[0];
            String condition = block.substring(command.length()).trim();
            if (emptyBody && !"if".equals(command) && !"while".equals(command)) {
                result.append(INDENT.repeat(depth)).append("*comment").append(LINE_SEPARATOR);
            }
            switch (command) {
                case "if":
                    result.append(INDENT.repeat(depth)).append("*if ").append(condition).append(LINE_SEPARATOR);
                    loops.push(EMPTY_STRING);
                    depth++;
                    emptyBody = true;
                    break;
                case "while":
                    String label = "nlb_while_" + (++m_loopCount);
                    result.append(INDENT.repeat(depth)).append("*label ").append(label).append(LINE_SEPARATOR);
                    result.append(INDENT.repeat(depth)).append("*if ").append(condition).append(LINE_SEPARATOR);
                    loops.push(label);
                    depth++;
                    emptyBody = true;
                    break;
                case "elseif":
                case "else":
                    if (depth > 0) {
                        result.append(INDENT.repeat(depth - 1)).append("*").append(command);
                        if (!condition.isEmpty()) {
                            result.append(" ").append(condition);
                        }
                        result.append(LINE_SEPARATOR);
                        emptyBody = true;
                    }
                    break;
                case "end":
                    if (depth > 0) {
                        String loop = loops.pop();
                        if (!loop.isEmpty()) {
                            result.append(INDENT.repeat(depth)).append("*goto ").append(loop).append(LINE_SEPARATOR);
                        }
                        depth--;
                        emptyBody = false;
                    }
                    break;
                default:
                    break;
            }
        }
        return result.toString();
    }

    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        m_language = StringHelper.isEmpty(nlbBuildingBlocks.getLang()) ? "en" : nlbBuildingBlocks.getLang();
        m_achNames.addAll(nlbBuildingBlocks.getAchievements());
        m_perfectGame = StringHelper.notEmpty(nlbBuildingBlocks.getAchievementNamePerfectGame())
                ? nlbBuildingBlocks.getAchievementNamePerfectGame() : "";
        if (!m_perfectGame.isEmpty()) {
            m_achNames.add(m_perfectGame);
        }
        return "*comment Generated with Non-Linear Book Builder, http://nlbhub.com" + LINE_SEPARATOR;
    }

    @Override
    protected String generateVariableInitializationText(Map<String, String> initValuesMap) {
        StringBuilder result = new StringBuilder();
        // ветка *if может заканчиваться без *goto перед *elseif/*else (блоки модификаций)
        result.append("*create implicit_control_flow true").append(LINE_SEPARATOR);
        result.append("*create ").append(ACTION_COUNT_VAR).append(" 0").append(LINE_SEPARATOR);
        for (Map.Entry<String, String> entry : initValuesMap.entrySet()) {
            result.append("*create ").append(entry.getKey()).append(" ").append(entry.getValue()).append(LINE_SEPARATOR);
        }
        // Вся книга — одна сцена, а ChoiceScript по умолчанию останавливает игру, когда строка
        // сцены выполнена 1000 раз («visited this line too many times»). В книге с возвратами в
        // хаб (или на страницу «Поражение» и обратно) это обычная игра, поэтому предел выше;
        // настоящий вечный цикл автопереходов он по-прежнему остановит
        result.append(achievementDeclarations());
        result.append("*looplimit ").append(LOOP_LIMIT).append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        return EMPTY_STRING;
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder trivialLinksBuilder = new StringBuilder();
        stringBuilder.append(pageBlocks.getPageComment());
        stringBuilder.append(pageBlocks.getPageLabel());
        // Таймер страницы (tvarid) обнуляется при входе (initf) — здесь до текста: модификации
        // ChoiceScript выполняет после вывода текста, а текст должен видеть уже 0. Действий на
        // месте (объектов) в ChoiceScript нет, поэтому сам он не тикает — только проматывается
        if (pageBlocks.isHasPageTimer()) {
            stringBuilder.append(pageBlocks.getPageTimerVariableInit());
        }
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        stringBuilder.append(pageBlocks.getPageTextEnd());
        stringBuilder.append(pageBlocks.getPageModifications());
        stringBuilder.append(pageBlocks.getPageVariable());
        String wait = timerWait(pageBlocks);
        if (wait != null) {
            stringBuilder.append("*label ").append(pageBlocks.getPageId()).append("_wait").append(LINE_SEPARATOR);
        }
        List<LinkBuildingBlocks> allLinksBlocks = pageBlocks.getLinksBuildingBlocks();
        // альтернативные тексты недоступных ссылок (#135) — после текста страницы, до вариантов
        for (final LinkBuildingBlocks linkBlocks : allLinksBlocks) {
            if (linkBlocks.hasShownAltText()) {
                stringBuilder.append("*if not(").append(cs(linkBlocks.getLinkConstraint())).append(")").append(LINE_SEPARATOR);
                stringBuilder.append("    ").append(linkBlocks.getLinkAltText()).append(LINE_SEPARATOR);
            }
        }
        List<LinkBuildingBlocks> linksBlocks = new ArrayList<>();
        for (final LinkBuildingBlocks linkBlocks : allLinksBlocks) {
            if (linkBlocks.isAuto()) {
                stringBuilder.append(getLinkRedirectText(linkBlocks, true));
            } else if (linkBlocks.isTrivial() && pageBlocks.isHasTrivialLinks()) {
                trivialLinksBuilder.append(getLinkRedirectText(linkBlocks, true));
            } else {
                linksBlocks.add(linkBlocks);
            }
        }
        if (wait != null) {
            stringBuilder.append(wait);
        }
        if (pageBlocks.isHasTrivialLinks()) {
            stringBuilder.append("*page_break").append(LINE_SEPARATOR);
            stringBuilder.append(trivialLinksBuilder.toString());
        } else {
            stringBuilder.append(pageBlocks.getPageEnd());
        }

        Map<String, Integer> optionNames = new HashMap<>();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            stringBuilder.append(uniqueOption(getLinkRedirectText(linkBlocks, false), optionNames));
        }
        return stringBuilder.toString();
    }

    /**
     * Выбирать нечего (ни одна обычная ссылка не доступна), а автоссылки ждут таймера — время
     * проматывается, как в плеере (PlayerEngine.autoWaitTicks): тик и снова проверка автоссылок
     * (метка id_wait перед ними). Щёлкнуть, пока идёт время, в ChoiceScript не по чему, поэтому
     * ждать — сколько потребуется, но меньше *looplimit (он считает и эти строки). null — не ждать
     */
    private String timerWait(PageBuildingBlocks pageBlocks) {
        if (!pageBlocks.isHasPageTimer() || pageBlocks.getTimerWaitTicks() <= 0) {
            return null;
        }
        boolean hasAuto = false;
        String any = null;
        for (LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            if (linkBlocks.isAuto()) {
                hasAuto = true;
            } else if (StringHelper.isEmpty(linkBlocks.getLinkConstraint())) {
                return null;
            } else {
                // ChoiceScript: у and/or ровно два операнда в скобках
                String c = "(" + linkBlocks.getLinkConstraint() + ")";
                any = (any == null) ? c : "(" + any + " or " + c + ")";
            }
        }
        if (!hasAuto) {
            return null;
        }
        String timer = pageBlocks.getPageTimerVariable();
        String indent = (any == null) ? "" : INDENT;
        StringBuilder result = new StringBuilder();
        if (any != null) {
            result.append("*if not(").append(cs(any)).append(")").append(LINE_SEPARATOR);
        }
        // счётчик ожидания — сам таймер: при входе он 0, а тиков на месте здесь не бывает
        String var = timer.substring("*set ".length(), timer.indexOf(' ', "*set ".length()));
        result.append(indent).append("*if ").append(var).append(" < ").append(LOOP_LIMIT / 10).append(LINE_SEPARATOR);
        result.append(indent).append(INDENT).append(timer);
        result.append(indent).append(INDENT).append("*goto ").append(pageBlocks.getPageId()).append("_wait").append(LINE_SEPARATOR);
        return result.toString();
    }

    /** Выражение в виде, который понимает ChoiceScript (см. {@link ChoiceScriptExpression}) */
    private static String cs(String expression) {
        return ChoiceScriptExpression.normalize(expression);
    }

    /**
     * ChoiceScript не допускает в одном *choice двух видимых вариантов с одинаковым текстом
     * («Invalid option; conflicts with option»), а в книге NLB это бывает (несколько «Закрыть» —
     * выходы из модуля, bookU). Повтор получает невидимый суффикс из символов нулевой ширины:
     * ChoiceScript их не обрезает, игрок их не видит.
     */
    private static String uniqueOption(String optionText, Map<String, Integer> optionNames) {
        Matcher matcher = OPTION_LINE.matcher(optionText);
        if (!matcher.find()) {
            return optionText;
        }
        String name = matcher.group(2).trim();
        int count = optionNames.merge(name, 1, Integer::sum);
        if (count == 1) {
            return optionText;
        }
        return optionText.substring(0, matcher.end(2)) + "\u200B".repeat(count - 1) + optionText.substring(matcher.end(2));
    }

    /**
     * Вариант *choice. Тривиальная ссылка (с текстом по умолчанию, «Далее») попадает в список
     * вариантов, если на странице есть и другие варианты; decorateLinkStart выдаёт для неё только
     * *goto, а внутри *choice нужен вариант #текст — иначе её *if оставался без тела и игрок
     * ссылку не видел (wild-island-quest, «Шапочка»).
     */
    private String choiceOption(final LinkBuildingBlocks linkBlocks) {
        if (!linkBlocks.isTrivial() || linkBlocks.isAuto()) {
            return linkBlocks.getLinkStart();
        }
        String text = linkBlocks.getLinkText();
        if (StringHelper.isEmpty(text) || text.trim().isEmpty()) {
            text = Link.DEFAULT_TEXT.get(m_language);
            if (StringHelper.isEmpty(text)) {
                text = Link.DEFAULT_TEXT.get("en");
            }
        }
        return decorateLinkStart(linkBlocks.getLinkId(), text, false, false, false, 0, linkBlocks.getTheme());
    }

    protected String getLinkRedirectText(final LinkBuildingBlocks linkBlocks, final boolean isAutoOrTrivial) {
        final String padding = "    ";
        StringBuilder stringBuilder = new StringBuilder();
        String constraint = linkBlocks.getLinkConstraint();
        if (linkBlocks.isAuto() && linkBlocks.isNeedsAction()) {
            // has_action(): автоссылка ждёт, пока игрок сделает нужное число действий
            String hasAction = "(" + ACTION_COUNT_VAR + " >= " + getNeedsActionCount() + ")";
            constraint = StringHelper.isEmpty(constraint) ? hasAction : "(" + constraint + ") and " + hasAction;
        }
        if (!StringHelper.isEmpty(constraint)) {
            stringBuilder.append(isAutoOrTrivial ? Constants.EMPTY_STRING : padding).append("*if ").append(cs(constraint)).append(LINE_SEPARATOR);
            stringBuilder.append(padding);
        }
        stringBuilder.append(isAutoOrTrivial ? linkBlocks.getLinkStart() : choiceOption(linkBlocks));
        return stringBuilder.toString();
    }

    @Override
    protected String getIndentString() {
        return EMPTY_STRING;
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        StringBuilder postPage = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBlocks.getLinksBuildingBlocks()) {
            postPage.append(linkBlocks.getLinkComment());
            postPage.append(linkBlocks.getLinkLabel());
            if (!linkBlocks.isTechnical()) {
                postPage.append("*set ").append(ACTION_COUNT_VAR).append(" +1").append(LINE_SEPARATOR);
            } else if (linkBlocks.isAuto() && linkBlocks.isNeedsAction()) {
                postPage.append("*set ").append(ACTION_COUNT_VAR).append(" 0").append(LINE_SEPARATOR);
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
        return achievementPerfectSubroutine();
    }

    // ------------------------------------------------------------------ достижения

    /** Все достижения книги (и те, что встретились в модификациях), perfect game, места ACHIEVE */
    private final java.util.Set<String> m_achNames = new java.util.TreeSet<>();
    private String m_perfectGame = "";
    private final Map<String, Integer> m_achieveSites = new HashMap<>();

    /** Имя переменной ChoiceScript для достижения: строчные буквы, цифры и «_» */
    private static String achVar(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    /** Имя *achievement: ChoiceScript принимает только ^[a-z][a-z0-9_]+$ */
    private static boolean validAchievementName(String name) {
        return name.toLowerCase().matches("[a-z][a-z0-9_]+");
    }

    /**
     * Счётчики достижений (*create) и *achievement для каждого достижения книги: очки — поровну,
     * от 1 до 100 и не больше 1000 в сумме (ограничения ChoiceScript), название и описание — имя
     * достижения. Сами «полученные» достижения ChoiceScript хранит между играми (*achieve), а
     * счётчики мест (nlb_ach_…, ACHIEVED) — только на эту игру
     */
    private String achievementDeclarations() {
        StringBuilder result = new StringBuilder();
        for (String name : m_achNames) {
            String v = achVar(name);
            result.append("*create nlb_ach_").append(v).append(" 0").append(LINE_SEPARATOR);
            result.append("*create nlb_achmax_").append(v).append(" 0").append(LINE_SEPARATOR);
            result.append("*create nlb_achgot_").append(v).append(" false").append(LINE_SEPARATOR);
        }
        for (Integer site : new java.util.TreeSet<>(m_achieveSites.values())) {
            result.append("*create nlb_achid_").append(site).append(" false").append(LINE_SEPARATOR);
        }
        if (!m_achNames.isEmpty()) {
            result.append("*create nlb_perf false").append(LINE_SEPARATOR);
        }
        long valid = m_achNames.stream().filter(ChoiceScriptExportManager::validAchievementName).count();
        int points = valid == 0 ? 1 : (int) Math.max(1, Math.min(100, 1000 / valid));
        for (String name : m_achNames) {
            if (validAchievementName(name)) {
                String title = name.length() > 50 ? name.substring(0, 50) : name;
                result.append("*achievement ").append(name.toLowerCase()).append(" visible ").append(points).append(" ")
                        .append(title).append(LINE_SEPARATOR);
                result.append(INDENT).append(title).append(LINE_SEPARATOR);
            }
        }
        return result.toString();
    }

    /**
     * Perfect game (PlayerContext.achieve): после каждого ACHIEVE — *gosub nlb_ach_perfect: все
     * прочие достижения книги набрали свой порог (без ACHMAX — 1). Мест у perfect game ChoiceScript
     * не различает (имени переменной из строки нет) — его счётчик 1, когда оно получено
     */
    private String achievementPerfectSubroutine() {
        StringBuilder result = new StringBuilder();
        result.append("*finish").append(LINE_SEPARATOR);
        result.append("*label nlb_ach_perfect").append(LINE_SEPARATOR);
        if (!m_perfectGame.isEmpty()) {
            String p = achVar(m_perfectGame);
            result.append("*set nlb_perf true").append(LINE_SEPARATOR);
            for (String name : m_achNames) {
                if (!name.equals(m_perfectGame)) {
                    String v = achVar(name);
                    result.append("*if (nlb_ach_").append(v).append(" < 1) or (nlb_ach_").append(v).append(" < nlb_achmax_").append(v)
                            .append(")").append(LINE_SEPARATOR);
                    result.append(INDENT).append("*set nlb_perf false").append(LINE_SEPARATOR);
                }
            }
            result.append("*if nlb_perf and not(nlb_achgot_").append(p).append(")").append(LINE_SEPARATOR);
            result.append(INDENT).append("*set nlb_achgot_").append(p).append(" true").append(LINE_SEPARATOR);
            result.append(INDENT).append("*set nlb_ach_").append(p).append(" 1").append(LINE_SEPARATOR);
            if (validAchievementName(m_perfectGame)) {
                result.append(INDENT).append("*achieve ").append(m_perfectGame.toLowerCase()).append(LINE_SEPARATOR);
            }
            result.append(INDENT).append(achievementAnnouncement(m_language)).append(m_perfectGame).append(LINE_SEPARATOR);
        }
        result.append("*return").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, boolean isTechnical, int pageNumber, Theme theme) {
        if (isAuto || isTrivial) {
            return "*goto " + linkId + LINE_SEPARATOR;
        } else {
            return "    #" + linkText + LINE_SEPARATOR + "            *goto " + linkId + LINE_SEPARATOR;
        }
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
        return "*goto " + linkTarget + LINE_SEPARATOR + LINE_SEPARATOR;
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
        return "=";
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

    /**
     * @return
     */
    @Override
    protected String decorateNot() {
        return "not";
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
    protected String additionalDecorationForVariableInExpression(String variable) {
        return "(" + variable + ")";
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
        return constraintVar;
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
        if (isFinish) {
            return "*finish" + LINE_SEPARATOR;
        } else {
            return "*choice" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        return "*label " + linkId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "*comment Link -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decorateLinkVariable(String variableName) {
        return "*set " + variableName + " true" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        return "*set " + linkVisitStateVariable + " true" + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @return
     */
    @Override
    protected String decoratePageVariable(final String variableName) {
        return "*set " + variableName + " true" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING : "*set " + variableName + " 0" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        return StringHelper.isEmpty(variableName) ? Constants.EMPTY_STRING : "*set " + variableName + " +1" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return indentBlocks(modificationsText);
    }

    @Override
    protected String decoratePageThemeModifications(String modificationsText) {
        // TODO: implement
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return indentBlocks(modificationsText);
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return (
                    caption.toUpperCase() + LINE_SEPARATOR +
                            StringHelper.createRepeatedString(caption.length(), "-") + LINE_SEPARATOR +
                            LINE_SEPARATOR
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
            if (StringHelper.isEmpty(pageImagePath)) {
                return Constants.EMPTY_STRING;
            } else {
                return "*image scenes/" + pageImagePath + LINE_SEPARATOR;
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

    private String getAppropriateText(TextChunk textChunk) {
        switch (textChunk.getType()) {
            case TEXT:
                return textChunk.getText();
            case VARIABLE:
                return "${" + textChunk.getText() + "}";
            default:
                return Constants.EMPTY_STRING;
        }
    }

    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        StringBuilder pageText = new StringBuilder();
        String lineSeparator = getLineSeparator();
        int newlines = 0;
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                case VARIABLE:
                    if (newlines == 1) {
                        pageText.append(lineSeparator).append("*line_break").append(lineSeparator);
                    } else {
                        for (int i = 0; i < newlines; i++) {
                            pageText.append(lineSeparator);
                        }
                    }
                    newlines = 0;
                    pageText.append(getAppropriateText(textChunk));
                    break;
                case ACTION_TEXT:
                    // TODO: support
                    break;
                case NEWLINE:
                    newlines++;
                    break;
            }
        }
        return pageText.toString();
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
        return "*label " + labelText + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "*comment Page -- " + comment + LINE_SEPARATOR;
    }

    /**
     * @param variableName
     * @param variableValue
     * @return
     */
    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return "*set " + variableName + " " + cs(variableValue) + LINE_SEPARATOR;
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
        return BLOCK_MARK + "while " + cs(constraint) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        return BLOCK_MARK + "if " + cs(constraint) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        // Инвентаря в экспорте ChoiceScript нет (ADDINV не поддерживается): предмета у игрока не бывает
        return BLOCK_MARK + "if false" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElse() {
        return BLOCK_MARK + "else" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return BLOCK_MARK + "elseif " + cs(constraint) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        return BLOCK_MARK + "end" + LINE_SEPARATOR;
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        // TODO: implement inventory
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        // TODO: implement inventory
        return EMPTY_STRING;
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
        // *rand переменная минимум максимум — целое от 1 до n; выражение — в скобках
        String normalized = cs(maxValue).trim();
        String max = normalized.matches("[\\w.]+") || normalized.startsWith("(") ? normalized : "(" + normalized + ")";
        return "*rand " + variableName + " 1 " + max + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        m_achNames.add(achievementName);
        return "*set nlb_achmax_" + achVar(achievementName) + " " + max + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        // место засчитывается один раз; набран порог (ACHMAX, без него — первое место) — *achieve и
        // объявление строкой; затем perfect game (*gosub). Блоки — заготовками (indentBlocks)
        m_achNames.add(achievementName);
        int site = m_achieveSites.computeIfAbsent(modificationId, k -> m_achieveSites.size() + 1);
        String v = achVar(achievementName);
        StringBuilder result = new StringBuilder();
        result.append(BLOCK_MARK).append("if not(nlb_achid_").append(site).append(")").append(LINE_SEPARATOR);
        result.append("*set nlb_achid_").append(site).append(" true").append(LINE_SEPARATOR);
        result.append("*set nlb_ach_").append(v).append(" +1").append(LINE_SEPARATOR);
        result.append(BLOCK_MARK).append("end").append(LINE_SEPARATOR);
        result.append(BLOCK_MARK).append("if (not(nlb_achgot_").append(v).append(")) and ((nlb_achmax_").append(v)
                .append(" = 0) or (nlb_ach_").append(v).append(" >= nlb_achmax_").append(v).append("))").append(LINE_SEPARATOR);
        result.append("*set nlb_achgot_").append(v).append(" true").append(LINE_SEPARATOR);
        if (validAchievementName(achievementName)) {
            result.append("*achieve ").append(achievementName.toLowerCase()).append(LINE_SEPARATOR);
        }
        result.append(achievementAnnouncement(bookLanguage())).append(achievementName).append(LINE_SEPARATOR);
        result.append(BLOCK_MARK).append("end").append(LINE_SEPARATOR);
        result.append("*gosub nlb_ach_perfect").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        m_achNames.add(achievementName);
        return "*set " + variableName + " nlb_ach_" + achVar(achievementName) + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        // *goto уводит на страницу, оставшиеся модификации не выполняются: переход из модификаций
        // ссылки важнее цели ссылки (как в INSTEAD)
        return "*goto " + locationId + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountGetOperation(String variableName, int statType) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateCountResetOperation() {
        // TODO: implement
        return EMPTY_STRING;
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
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        // TODO: implement
        return EMPTY_STRING;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        // TODO: implement
        return EMPTY_STRING;
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
