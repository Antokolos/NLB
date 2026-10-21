/**
 * @(#)PlayerEngine.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
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
package com.nlbhub.nlb.player;

import com.nlbhub.nlb.api.Coords;
import com.nlbhub.nlb.api.Link;
import com.nlbhub.nlb.api.Modification;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.SpecialVariablesNameHelper;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.api.Variable;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.api.config.Text;
import com.nlbhub.nlb.util.MultiLangString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import static com.nlbhub.nlb.player.PlayerContext.isEmpty;

/**
 * Движок проигрывания книги NLB без пользовательского интерфейса. Порт PlayerEngine из
 * NLBNavigator; эталон — INSTEAD-экспорт (ExportManager.createPageBuildingBlocks /
 * createLinkBuildingBlocks, STEADExportManager: enter/autos/xact, nlb.lua).
 * {@code NonLinearBookImpl.createFilteredPage} не используется: служебные ссылки (Traverse,
 * Return, AutowiredIn/Out) строятся здесь.
 * <p>
 * Интерфейс вызывает {@link #start()}, затем команды игрока ({@link #choose}, {@link #actOnObject},
 * {@link #clickInventory}, {@link #use}, {@link #waitTick()}, {@link #look()}) и после каждой
 * забирает {@link #takeEvents()} и {@link #view()}. Состояние — у каждого движка своё.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class PlayerEngine {
    /**
     * Какой INSTEAD-экспорт эмулировать: от него зависит, какие страницы считаются VN.
     * VN (exportToVNSTEADFile): тема страницы DEFAULT трактуется как VN; STANDARD
     * (exportToSTEADFile): VN — только страницы с явной темой VN
     */
    public enum ExportMode {VN, STANDARD}

    public static final class Settings {
        public ExportMode exportMode = ExportMode.VN;
        /**
         * _needs_action_count (config.xml: export/needs-action-count)
         */
        public int needsActionCount = 3;
        /**
         * Тексты game.inv / game.nouse (config.xml: export/texts)
         */
        public String gameInvText = "";
        public String gameNouseText = "";
        public long randomSeed = System.nanoTime();
        /**
         * Детерминированная случайность для сверки с INSTEAD (см. PlayerContext.setDeterministic)
         */
        public boolean deterministic = false;
        public long deterministicStart = 0;
        /**
         * Прогресс достижений (prefs INSTEAD): переживает «Начать заново»; null — свой у движка
         */
        public Achievements achievements = null;

        /**
         * Настройки из раздела export конфигурации (cfg/config.xml) для языка книги
         */
        public static Settings fromExport(Export export, String language) {
            Settings settings = new Settings();
            if (export != null) {
                settings.needsActionCount = export.getNeedsActionCount();
                Text texts = export.getTextByLang(language == null ? "" : language);
                if (texts != null) {
                    settings.gameInvText = texts.getGameInv();
                    settings.gameNouseText = texts.getGameNouse();
                }
            }
            return settings;
        }
    }

    /**
     * Элемент вывода для интерфейса, в порядке показа
     */
    public static final class Event {
        public enum Kind {
            /** заголовок страницы */
            PageCaption,
            /** текст страницы с подставленными $var$ */
            PageText,
            /** описание объекта на странице (dsc) */
            ObjectText,
            /** альтернативный текст недоступной ссылки */
            AltText,
            /** текст из модификаций и действий (PRN, ACT, USE, ...) */
            Text,
            /** имя файла картинки */
            Image,
            /** анимация: text — шаблон кадра с %d, кадры 1..frames */
            Animation,
            /** звуковой эффект (SFX) */
            Sound,
            /**
             * SFX страницы — озвучка по порциям текста: каждое событие — файл очереди страницы
             * в порядке поля звука
             */
            Voice,
            /** фоновая музыка; пустой text — остановка */
            Music,
            /** «Получено достижение: ...» */
            Achievement,
            /** служебное: URL, оформление окна */
            Info,
            /** конец игры */
            Finish,
            /** новелла передала управление внешней игре (text — её имя) */
            ExternalGame
        }

        private final Kind m_kind;
        private final String m_text;
        private final int m_frames;
        private final String m_id;
        private final String m_subject;
        private final String m_position;

        Event(Kind kind, String text) {
            this(kind, text, 0, "", "", "");
        }

        Event(Kind kind, String text, int frames, String id, String subject, String position) {
            m_kind = kind;
            m_text = text;
            m_frames = frames;
            m_id = id;
            m_subject = subject;
            m_position = position;
        }

        public Kind getKind() {
            return m_kind;
        }

        public String getText() {
            return m_text;
        }

        public int getFrames() {
            return m_frames;
        }

        /**
         * Achievement: имя достижения в книге
         */
        public String getId() {
            return m_id;
        }

        /**
         * Для картинок объектов — отображаемое имя объекта; пусто для картинки страницы
         */
        public String getSubject() {
            return m_subject;
        }

        /**
         * Для картинок графических объектов — положение на экране ("right-top@-103,680")
         */
        public String getPosition() {
            return m_position;
        }
    }

    public static final class Choice {
        private final String m_text;
        private final boolean m_trivial;
        private final boolean m_inlineLink;

        Choice(String text, boolean trivial, boolean inlineLink) {
            m_text = text;
            m_trivial = trivial;
            m_inlineLink = inlineLink;
        }

        public String getText() {
            return m_text;
        }

        /**
         * Тривиальная ссылка: текст по умолчанию («Далее»/«Next»)
         */
        public boolean isTrivial() {
            return m_trivial;
        }

        /**
         * Ссылка в тексте, а не путь комнаты (ExportManager.isInlineLink)
         */
        public boolean isInlineLink() {
            return m_inlineLink;
        }
    }

    /**
     * Расположение графического объекта — как его задаёт INSTEAD-экспорт (decorateObjEffect):
     * корневой объект привязан к углу экрана со смещением (offset или arm); вложенный — к своему
     * родителю. Экран 1920×1080
     */
    public static final class Placement {
        private static final String[] ORIGINS = {
                "left-top", "middle-top", "right-top", "left-middle", "middle-middle",
                "right-middle", "left-bottom", "middle-bottom", "right-bottom"
        };
        boolean m_graphical = false;
        String m_origin = "";
        String m_offset = "";
        int m_armX = 0;
        int m_armY = 0;
        String m_parentId = "";

        public boolean isGraphical() {
            return m_graphical;
        }

        public String getOrigin() {
            return m_origin;
        }

        public String getOffset() {
            return m_offset;
        }

        public int getArmX() {
            return m_armX;
        }

        public int getArmY() {
            return m_armY;
        }

        public String getParentId() {
            return m_parentId;
        }

        /**
         * "left-top@1520,40" / "left-top +160,153" / "+0,0"
         */
        @Override
        public String toString() {
            if (!m_graphical) {
                return "";
            }
            if (!m_parentId.isEmpty()) {
                return "+" + m_armX + "," + m_armY;
            }
            if (!m_offset.isEmpty()) {
                return m_origin + "@" + m_offset;
            }
            return m_origin + " +" + m_armX + "," + m_armY;
        }
    }

    public static final class ObjectView {
        private final String m_instanceId;
        private final String m_disp;
        private final boolean m_takable;
        private final boolean m_active;
        private final Placement m_placement;
        private final String m_position;
        private final int m_depth;
        private final boolean m_collapsed;

        ObjectView(String instanceId, String disp, boolean takable, boolean active, Placement placement,
                   String position, int depth, boolean collapsed) {
            m_instanceId = instanceId;
            m_disp = disp;
            m_takable = takable;
            m_active = active;
            m_placement = placement;
            m_position = position;
            m_depth = depth;
            m_collapsed = collapsed;
        }

        public String getInstanceId() {
            return m_instanceId;
        }

        public String getDisp() {
            return m_disp;
        }

        public boolean isTakable() {
            return m_takable;
        }

        /**
         * false — щелчок ничего не делает (STAT, надпись): объект только для информации
         */
        public boolean isActive() {
            return m_active;
        }

        /**
         * Расположение графического объекта на странице; у предметов инвентаря не задаётся
         * (их раскладывает панель инвентаря), как и в NLBNavigator
         */
        public Placement getPlacement() {
            return m_placement;
        }

        /**
         * Положение графического объекта на экране текстом (подробно — getPlacement)
         */
        public String getPosition() {
            return m_position;
        }

        /**
         * Уровень вложенности: 0 — прямо на странице/в инвентаре, 1 — в объекте и т.д.
         */
        public int getDepth() {
            return m_depth;
        }

        /**
         * Свёрнут (collapsable): содержимое сейчас не видно
         */
        public boolean isCollapsed() {
            return m_collapsed;
        }
    }

    public static final class PageView {
        private final String m_pageId;
        private final List<Choice> m_choices;
        private final List<ObjectView> m_sceneObjects;
        private final List<ObjectView> m_inventory;
        private final boolean m_finished;

        PageView(String pageId, List<Choice> choices, List<ObjectView> sceneObjects, List<ObjectView> inventory,
                 boolean finished) {
            m_pageId = pageId;
            m_choices = Collections.unmodifiableList(choices);
            m_sceneObjects = Collections.unmodifiableList(sceneObjects);
            m_inventory = Collections.unmodifiableList(inventory);
            m_finished = finished;
        }

        public String getPageId() {
            return m_pageId;
        }

        /**
         * Доступные неавтоматические ссылки
         */
        public List<Choice> getChoices() {
            return m_choices;
        }

        public List<ObjectView> getSceneObjects() {
            return m_sceneObjects;
        }

        public List<ObjectView> getInventory() {
            return m_inventory;
        }

        /**
         * Ходов больше нет
         */
        public boolean isFinished() {
            return m_finished;
        }
    }

    /**
     * Внешняя (нетекстовая) игра: новелла передаёт ей управление командой ACT/ACTF с именем,
     * которого нет среди объектов книги (play_bp_two_ais), и ждёт {@link #finishExternalGame()}
     */
    public static final class ExternalGame {
        private final String m_name;
        private final String m_returnPageId;

        ExternalGame(String name, String returnPageId) {
            m_name = name;
            m_returnPageId = returnPageId;
        }

        public String getName() {
            return m_name;
        }

        public String getReturnPageId() {
            return m_returnPageId;
        }
    }

    private enum LinkKind {Normal, Traverse, Return, AutowiredIn, AutowiredOut}

    private enum ObjKind {Obj, Menu, Stat}

    /**
     * Ссылка в том виде, в каком её видит INSTEAD-экспорт (Link или LinkLw)
     */
    private static final class PlayerLink {
        LinkKind kind = LinkKind.Normal;
        String id = "";
        String target = "";
        String text = "";
        String altText = "";
        String constrId = "";
        String varId = "";
        boolean autoFlag = false;
        boolean needsAction = false;
        boolean once = false;
        boolean positive = true;
        boolean obeyModule = false;
        /**
         * determineTrivialStatus: текст и alt-текст по умолчанию либо auto
         */
        boolean trivial = false;
        List<Modification> modifications = Collections.emptyList();
        /**
         * Для autowired: служебная переменная "W_P" и присваиваемое значение (модификация LinkLw)
         */
        String autowiredVarId = "";
        boolean autowiredValue = false;
    }

    /**
     * Защита от бесконечной цепочки автоматических переходов за одну команду игрока
     */
    private static final int MAX_TRANSITIONS_PER_COMMAND = 1000;
    private static final int MAX_TIMER_TICKS = 100000;
    /**
     * ~10 с при тике 200 мс: пауза хода противника, короткие анимации
     */
    private static final int SHORT_WAIT_TICKS = 50;

    private final NonLinearBook m_rootBook;
    private final Settings m_settings;
    private final Achievements m_achievements;
    private PlayerContext m_context;
    private ModificationInterpreter m_interpreter;
    private Page m_page = null;
    private NonLinearBook m_book = null;
    private long m_actionCountDiffPrev = 0;
    private int m_transitions = 0;
    private List<Event> m_events = new ArrayList<>();
    private ExternalGame m_externalGame = null;
    private String m_pendingExternalGame = null;
    private Predicate<String> m_isExternalGame = null;
    private java.util.function.Consumer<String> m_warnings = message -> System.err.println(message);
    /**
     * Сохраняемые (preserved) графические объекты, которые VN-движок INSTEAD оставляет на экране
     * при смене страниц. Корни, в порядке появления
     */
    private final List<String> m_preservedRoots = new ArrayList<>();
    /**
     * Глубина объекта в дереве сцены, как его обходит sceneObjects()
     */
    private final Map<String, Integer> m_sceneDepth = new HashMap<>();
    /**
     * Картинки preserved-объектов, уже показанные игроку
     */
    private final Map<String, String> m_preservedImagesShown = new TreeMap<>();
    /**
     * Показанные картинки: страницы (ключ — id страницы) и объектов на ней
     */
    private final Map<String, String> m_shownImages = new TreeMap<>();
    private int m_renderCount = 0;
    /**
     * Фоновая музыка, которая сейчас играет (последнее событие Music)
     */
    private String m_currentMusic = "";
    /**
     * Начало событий последней показанной страницы — чтобы убрать «пустые» промежуточные страницы
     */
    private int m_lastRenderStart = 0;

    public PlayerEngine(NonLinearBook rootBook, Settings settings) {
        m_rootBook = rootBook;
        m_settings = settings;
        m_achievements = settings.achievements != null ? settings.achievements : new Achievements();
        createState();
    }

    private void createState() {
        m_context = new PlayerContext(m_rootBook, m_achievements, m_settings.randomSeed);
        m_context.setDeterministic(m_settings.deterministic, m_settings.deterministicStart);
        m_interpreter = new ModificationInterpreter(m_rootBook, m_context);
        m_interpreter.setWarnings(message -> m_warnings.accept(message));
        m_interpreter.setExternalCallHandler((name, type) -> {
            if (m_isExternalGame == null || !m_isExternalGame.test(name)) {
                return false;
            }
            // Как walk(game_room) внутри enter: остальные модификации выполняются, а управление
            // уходит игре, когда текущая команда игрока закончится
            m_pendingExternalGame = name;
            return true;
        });
        m_externalGame = null;
        m_pendingExternalGame = null;
        m_page = null;
        m_book = null;
        m_currentMusic = "";
        m_preservedRoots.clear();
        m_preservedImagesShown.clear();
    }

    /**
     * Куда писать предупреждения книги (объект модификации не найден и т.п.); по умолчанию — stderr
     */
    public void setWarnings(java.util.function.Consumer<String> warnings) {
        m_warnings = warnings;
    }

    /**
     * Какие имена считаются внешними играми; по умолчанию — никакие (будет предупреждение)
     */
    public void setExternalGameFilter(Predicate<String> isExternalGame) {
        m_isExternalGame = isExternalGame;
    }

    public ExternalGame externalGame() {
        return m_externalGame;
    }

    public PlayerContext context() {
        return m_context;
    }

    public ModificationInterpreter interpreter() {
        return m_interpreter;
    }

    public Achievements achievements() {
        return m_achievements;
    }

    public Page currentPage() {
        return m_page;
    }

    public NonLinearBook rootBook() {
        return m_rootBook;
    }

    // ------------------------------------------------------------------ доступ к состоянию

    public Value getVariable(String name) {
        return m_context.getVar(name);
    }

    public void setVariable(String name, Value value) {
        m_context.setVar(name, value);
    }

    public boolean hasVariable(String name) {
        return m_context.hasVar(name);
    }

    /**
     * Имена переменных книги и служебных (отсортированы)
     */
    public List<String> variableNames() {
        List<String> names = new ArrayList<>();
        for (String name : m_context.variables().keySet()) {
            if (!"true".equals(name) && !"false".equals(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Положить объект (по имени) в инвентарь
     */
    public boolean giveItem(String objName) {
        String id = m_interpreter.objIdByName(objName);
        if (isEmpty(id)) {
            return false;
        }
        m_context.inventoryAdd(id);
        return true;
    }

    public boolean takeItem(String objName) {
        String id = m_interpreter.objIdByName(objName);
        if (isEmpty(id) || !m_context.inInventory(id)) {
            return false;
        }
        m_context.inventoryRemove(id);
        return true;
    }

    /**
     * Выражение на языке условий книги (например, "money >= 100")
     */
    public Value evaluate(String expression) {
        return m_interpreter.evaluate(expression);
    }

    // ------------------------------------------------------------------ команды игрока

    /**
     * Вход на стартовую страницу книги
     */
    public void start() {
        start(null);
    }

    /**
     * Вход на указанную страницу (null или пусто — стартовая)
     */
    public void start(String pageId) {
        m_transitions = 0;
        m_actionCountDiffPrev = 0;
        walkTo(isEmpty(pageId) ? m_rootBook.getStartPoint() : pageId, false);
        settle(false);
    }

    /**
     * Новая игра с начала (_try_again в VN-экспорте); прогресс достижений сохраняется
     */
    public void restart() {
        createState();
        start();
    }

    /**
     * Страница показывается по правилам VN-экспорта
     */
    public boolean isVnPage(Page page) {
        if (page == null) {
            return false;
        }
        Theme theme = page.getEffectiveTheme();
        // VNSTEADExportManager.isVN: theme != STANDARD; STEADExportManager.isVN: theme == VN
        return m_settings.exportMode == ExportMode.VN ? theme != Theme.STANDARD : theme == Theme.VN;
    }

    /**
     * Переход по доступной ссылке с индексом из view().getChoices()
     */
    public boolean choose(int choiceIndex) {
        if (m_externalGame != null || m_page == null) {
            return false;
        }
        List<PlayerLink> choices = availableChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            return false;
        }
        m_transitions = 0;
        // Клик по ссылке — xact в отдельную комнату ссылки: s = комната ссылки, f.autowired = nil
        followLink(choices.get(choiceIndex), false);
        settle(false);
        return true;
    }

    /**
     * Клик по объекту на странице: tak для переносимых, иначе act
     */
    public boolean actOnObject(String instanceId) {
        if (m_externalGame != null || m_page == null || !isOnScene(instanceId)) {
            return false;
        }
        String target = instanceId;
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        if (obj == null) {
            return false;
        }
        // morphover (графические кнопки VN): под курсором объект заменяется своим «наведённым»
        // вариантом, и щёлкают уже по нему. В консоли наведения нет — щелчок сразу уходит ему
        Obj over = isEmpty(obj.getMorphOverId()) ? null : obj.getMorphOverObj();
        if (over != null && m_interpreter.isObjEnabled(over.getId())) {
            obj = over;
            target = over.getId();
        }
        m_transitions = 0;
        m_context.countIncrement(PlayerContext.COUNT_ACT);
        if (obj.isTakable()) {
            // decorateObjTak: tak = act(); затем либо взять общий объект, либо этот
            m_interpreter.objActA(target);
            Obj common = m_interpreter.commonOf(obj);
            String toTake = (common != null && common.isTakable()) ? common.getId() : target;
            // take() в STEAD: remove(obj, here()) — объект ищется в текущей комнате (в том числе
            // во вложенных объектах) и убирается оттуда, где найден. Объект может лежать и
            // в других контейнерах (ADD не убирает из прежнего места) — там он остаётся
            String owner = "";
            if (m_context.containerHas(m_page.getId(), toTake)) {
                owner = m_page.getId();
            } else {
                for (String visible : sceneObjects()) {
                    if (m_context.containerHas(visible, toTake)) {
                        owner = visible;
                        break;
                    }
                }
            }
            if (owner.isEmpty()) {
                owner = m_context.containerOf(toTake);
            }
            if (!isEmpty(owner) && m_context.containerHas(owner, toTake)) {
                m_context.containerRemove(owner, toTake);
            }
            m_context.inventoryAdd(toTake);
        } else {
            m_interpreter.objActA(target);
        }
        flushOutput();
        finishAction(true);
        return true;
    }

    /**
     * Клик по объекту в инвентаре: use(s, s), иначе game.inv
     */
    public boolean clickInventory(String instanceId) {
        if (m_externalGame != null || m_page == null || !isInInventoryTree(instanceId)) {
            return false;
        }
        m_transitions = 0;
        m_context.countIncrement(PlayerContext.COUNT_INV);
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        // decorateObjInv (MENU): menu = act(s) — действие объекта; STAT — только показ значения
        if (obj != null && objKind(obj) != ObjKind.Obj) {
            if (objKind(obj) == ObjKind.Menu) {
                m_interpreter.objActA(instanceId);
            }
            flushOutput();
            finishAction(true);
            return true;
        }
        // decorateObjInv (OBJ): inv = use(s, s); без текста INSTEAD показывает game.inv
        boolean printed = false;
        if (obj != null) {
            printed = m_interpreter.objUseA(instanceId, instanceId, instanceId);
            Obj common = m_interpreter.commonOf(obj);
            if (common != null) {
                if (printed) {
                    m_interpreter.objUseF(common.getId(), instanceId, instanceId);
                } else {
                    printed = m_interpreter.objUseA(common.getId(), instanceId, instanceId);
                }
            }
        }
        if (!printed) {
            m_context.emit(PlayerContext.OutputKind.Text, m_settings.gameInvText);
        }
        flushOutput();
        finishAction(true);
        return true;
    }

    /**
     * Применить объект инвентаря к объекту страницы или инвентаря
     */
    public boolean use(String sourceInstanceId, String targetInstanceId) {
        if (m_externalGame != null || m_page == null || !isInInventoryTree(sourceInstanceId)) {
            return false;
        }
        boolean targetVisible = isInInventoryTree(targetInstanceId) || isOnScene(targetInstanceId);
        Obj source = m_interpreter.findObj(m_context.protoOf(sourceInstanceId));
        Obj target = m_interpreter.findObj(m_context.protoOf(targetInstanceId));
        if (!targetVisible || source == null || target == null) {
            return false;
        }
        m_transitions = 0;
        m_context.countIncrement(PlayerContext.COUNT_USE);
        // decorateObjUseStart: use(s, w) = s:usea(w, w) + общий объект (usef, если текст уже был)
        boolean printed = m_interpreter.objUseA(sourceInstanceId, targetInstanceId, targetInstanceId);
        Obj common = m_interpreter.commonOf(source);
        if (common != null) {
            if (printed) {
                m_interpreter.objUseF(common.getId(), targetInstanceId, targetInstanceId);
            } else {
                printed = m_interpreter.objUseA(common.getId(), targetInstanceId, targetInstanceId);
            }
        }
        // used(s, w) цели: если у цели есть общий объект — w:usea(common, s)
        if (!printed) {
            Obj targetCommon = m_interpreter.commonOf(target);
            if (targetCommon != null) {
                printed = m_interpreter.objUseA(sourceInstanceId, targetCommon.getId(), targetInstanceId);
            }
        }
        // Ничего не произошло: nouse объекта или game.nouse
        if (!printed) {
            m_context.emit(PlayerContext.OutputKind.Text, nouseText(sourceInstanceId));
        }
        flushOutput();
        finishAction(true);
        return true;
    }

    /**
     * Подождать: один тик таймера страницы (в INSTEAD — раз в 200 мс) и проверка auto-ссылок
     */
    public boolean waitTick() {
        if (m_externalGame != null || m_page == null) {
            return false;
        }
        m_transitions = 0;
        // ровно один тик таймера (плюс автоматическое ожидание, если ходов нет)
        finishAction(true);
        return true;
    }

    /**
     * «Осмотреться» (look в INSTEAD): заново показать текущую страницу — картинки, текст,
     * описания объектов — без исполнения её модификаций
     */
    public boolean look() {
        if (m_page == null) {
            return false;
        }
        m_shownImages.clear();
        m_preservedImagesShown.clear();
        renderPage();
        return true;
    }

    /**
     * На странице есть таймер — ожидание имеет смысл
     */
    public boolean hasTimer() {
        return m_page != null && !isEmpty(m_page.getTimerVarId());
    }

    /**
     * Завершить внешнюю игру: как walk(_returnto) в INSTEAD — возврат на страницу вызова
     * с исполнением её модификаций
     */
    public boolean finishExternalGame() {
        if (m_externalGame == null) {
            return false;
        }
        String returnTo = m_externalGame.getReturnPageId();
        m_externalGame = null;
        m_transitions = 0;
        walkTo(returnTo, false);
        settle(false);
        return true;
    }

    public List<Event> takeEvents() {
        List<Event> result = m_events;
        m_events = new ArrayList<>();
        m_lastRenderStart = 1;  // события уже отданы интерфейсу
        return result;
    }

    // ------------------------------------------------------------------ ссылки

    /**
     * ExportManager.determineTrivialStatus(Link)
     */
    private static boolean isTrivial(MultiLangString texts, MultiLangString altTexts, boolean autoFlag) {
        return (Link.DEFAULT_TEXT.equals(texts) && Link.DEFAULT_ALT_TEXT.equals(altTexts)) || autoFlag;
    }

    /**
     * LinkLw.getId(): parent_target_mplLinkId_Type
     */
    private static String lwId(String parentId, String target, String mplLinkId, String type) {
        return parentId + "_" + target + "_" + mplLinkId + "_" + type;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private String moduleConstraintText(NonLinearBook book) {
        Page modulePage = book == null ? null : book.getParentPage();
        if (modulePage == null || isEmpty(modulePage.getModuleConstrId())) {
            return "";
        }
        NonLinearBook parentBook = m_interpreter.bookOfPage(modulePage.getId());
        Variable v = parentBook == null ? null : parentBook.getVariableById(modulePage.getModuleConstrId());
        return (v != null && !v.isDeleted()) ? trim(v.getValue()) : "";
    }

    private PlayerLink fromLink(Link link, LinkKind kind) {
        PlayerLink pl = new PlayerLink();
        pl.kind = kind;
        pl.id = link.getId();
        pl.target = nz(link.getTarget());
        pl.text = nz(link.getText());
        pl.altText = nz(link.getAltText());
        pl.constrId = nz(link.getConstrId());
        pl.varId = nz(link.getVarId());
        pl.autoFlag = link.isAuto();
        pl.needsAction = link.isNeedsAction();
        pl.once = link.isOnce();
        pl.positive = link.isPositiveConstraint();
        pl.modifications = link.getModifications();
        pl.trivial = isTrivial(link.getTexts(), link.getAltTexts(), pl.autoFlag);
        return pl;
    }

    private List<PlayerLink> buildLinks() {
        return buildLinks(m_page);
    }

    private List<PlayerLink> buildLinks(Page page) {
        List<PlayerLink> result = new ArrayList<>();
        if (page == null) {
            return result;
        }
        NonLinearBook book = m_interpreter.bookOfPage(page.getId());
        String pageId = page.getId();

        // 1. Собственные ссылки страницы
        for (Link link : page.getLinks()) {
            if (link.isDeleted()) {
                continue;
            }
            PlayerLink pl = fromLink(link, LinkKind.Normal);
            pl.obeyModule = link.isObeyToModuleConstraint();
            result.add(pl);
        }

        // 2. Traverse: вход в модуль страницы
        NonLinearBook module = page.getModule();
        if (module != null && !module.isEmpty()) {
            PlayerLink pl = new PlayerLink();
            pl.kind = LinkKind.Traverse;
            pl.target = module.getStartPoint();
            pl.id = lwId(pageId, pl.target, "", "Traverse");
            pl.text = nz(page.getTraverseText());
            pl.constrId = nz(page.getModuleConstrId());
            pl.autoFlag = page.isAutoTraverse();
            pl.needsAction = page.isNeedsAction();
            pl.trivial = isTrivial(page.getTraverseTexts(), Link.DEFAULT_ALT_TEXT, pl.autoFlag);
            result.add(pl);
        }

        // 3. Return: выход из модуля
        Page modulePage = book == null ? null : book.getParentPage();
        if (modulePage != null && page.shouldReturn()) {
            if (page.isUseMPL()) {
                for (Link link : modulePage.getLinks()) {
                    if (link.isDeleted()) {
                        continue;
                    }
                    PlayerLink pl = fromLink(link, LinkKind.Return);
                    pl.id = lwId(pageId, pl.target, link.getId(), "Return");
                    result.add(pl);
                }
            } else {
                // Ограничение возврата — NOT(модульное ограничение) для нелистовых страниц
                PlayerLink pl = new PlayerLink();
                pl.kind = LinkKind.Return;
                pl.target = isEmpty(page.getReturnPageId()) ? modulePage.getId() : page.getReturnPageId();
                pl.id = lwId(pageId, pl.target, "", "Return");
                pl.text = nz(page.getReturnText());
                pl.autoFlag = page.isAutoReturn();
                pl.positive = isEmpty(modulePage.getModuleConstrId());
                pl.obeyModule = !page.isLeaf();
                pl.trivial = isTrivial(page.getReturnTexts(), Link.DEFAULT_ALT_TEXT, pl.autoFlag);
                result.add(pl);
            }
        }

        // 4. AutowiredOut: из autowired-страницы обратно на обычные страницы
        if (page.isAutowire()) {
            Map<String, Page> targets = new TreeMap<>(
                    page.isGlobalAutowire() ? book.getDownwardPagesHeirarchy() : book.getPages()
            );
            for (Map.Entry<String, Page> entry : targets.entrySet()) {
                Page target = entry.getValue();
                if (target.isDeleted() || target.isAutowire()) {
                    continue;
                }
                String targetId = entry.getKey();
                PlayerLink pl = new PlayerLink();
                pl.kind = LinkKind.AutowiredOut;
                pl.target = targetId;
                pl.id = lwId(pageId, targetId, "", "AutowiredOut");
                pl.text = nz(page.getAutowireOutText());
                pl.constrId = NonLinearBook.LC_VARID_PREFIX + pageId + NonLinearBook.LC_VARID_SEPARATOR_OUT + targetId;
                pl.autoFlag = page.isAutoOut();
                // LinkLw: модификация "W_T := FALSE"
                pl.autowiredVarId = pageId + "_" + targetId;
                pl.autowiredValue = false;
                pl.trivial = isTrivial(page.getAutowireOutTexts(), Link.DEFAULT_ALT_TEXT, pl.autoFlag);
                result.add(pl);
            }
        }

        // 5. AutowiredIn: на autowired-страницы (с autowired-страниц — только при fullAutowire)
        if (book.isFullAutowire() || !page.isAutowire()) {
            List<String> autowiredIds = new ArrayList<>(book.getAutowiredPagesIds());
            autowiredIds.addAll(book.getParentGlobalAutowiredPagesIds());
            for (String autowiredId : autowiredIds) {
                if (autowiredId.equals(pageId)) {
                    continue;
                }
                Page autowired = m_interpreter.findPage(autowiredId);
                if (autowired == null) {
                    continue;
                }
                PlayerLink pl = new PlayerLink();
                pl.kind = LinkKind.AutowiredIn;
                pl.target = autowiredId;
                pl.id = lwId(pageId, autowiredId, "", "AutowiredIn");
                pl.text = nz(autowired.getAutowireInText());
                pl.constrId = nz(autowired.getAutowireInConstrId());
                pl.autoFlag = autowired.isAutoIn();
                pl.needsAction = autowired.isNeedsAction();
                // LinkLw: "W_P := TRUE", только если исходная страница сама не autowired
                if (!page.isAutowire()) {
                    pl.autowiredVarId = autowiredId + "_" + pageId;
                    pl.autowiredValue = true;
                }
                pl.trivial = isTrivial(autowired.getAutowireInTexts(), Link.DEFAULT_ALT_TEXT, pl.autoFlag);
                result.add(pl);
            }
        }
        return result;
    }

    private boolean isLinkAvailable(PlayerLink link) {
        // ExportManager.translateConstraintBody
        String constraint = "";
        if (!link.constrId.isEmpty()) {
            Variable c = m_book.getVariableById(link.constrId);
            if (c != null && !c.isDeleted()) {
                constraint = trim(c.getValue());
            }
        }
        String moduleText = link.obeyModule ? moduleConstraintText(m_book) : "";
        String additional = link.once
                ? "!" + SpecialVariablesNameHelper.decorateLinkVisitStateVar(link.id) : "";
        if (constraint.isEmpty() && moduleText.isEmpty() && additional.isEmpty()) {
            return true;  // ссылка без ограничений
        }
        String body = !moduleText.isEmpty()
                ? (!constraint.isEmpty() ? "(" + moduleText + ")&&(" + constraint + ")" : moduleText)
                : constraint;
        if (!additional.isEmpty()) {
            body = body.isEmpty() ? additional : "(" + additional + ")&&(" + body + ")";
        }
        boolean value = ModificationInterpreter.isTruthy(m_interpreter.evaluate(body));
        return link.positive == value;
    }

    /**
     * Ссылки, которые игрок может выбрать на текущей странице (с учётом VN-тривиальных страниц)
     */
    private List<PlayerLink> availableChoices() {
        List<PlayerLink> result = new ArrayList<>();
        List<PlayerLink> links = buildLinks();
        // VN: если все ссылки страницы тривиальны, экран выбора сам исполняет первую доступную
        // (vn_choices.enter) — игроку остаётся только «Далее»
        boolean trivialPage = isVnPage(m_page) && !links.isEmpty();
        for (PlayerLink link : links) {
            trivialPage = trivialPage && link.trivial;
        }
        for (PlayerLink link : links) {
            if (link.autoFlag || !isLinkAvailable(link)) {
                continue;
            }
            result.add(link);
            if (trivialPage) {
                break;
            }
        }
        return result;
    }

    private boolean hasAction() {
        // STEAD has_action(): _action_count_diff >= _needs_action_count -> count_reset
        if (m_context.countGet(0) < m_settings.needsActionCount) {
            return false;
        }
        m_context.countReset();
        return true;
    }

    private void setVarTrue(NonLinearBook book, String varId) {
        if (isEmpty(varId) || book == null) {
            return;
        }
        Variable v = book.getVariableById(varId);
        if (v != null && !v.isDeleted() && !isEmpty(v.getName())) {
            m_context.setVar(v.getName(), Value.TRUE);
        }
    }

    private static boolean isContentEvent(Event.Kind k) {
        return k == Event.Kind.PageText || k == Event.Kind.ObjectText || k == Event.Kind.Text
                || k == Event.Kind.Achievement || k == Event.Kind.Info;
    }

    /**
     * Исполнение ссылки: модификации, переменные, переход. fromPageContext — auto-ссылка (s = страница)
     */
    private void followLink(PlayerLink link, boolean fromPageContext) {
        Page page = m_page;
        // Счётчик переходов (COUNTGET, бит 8 — walk_count в INSTEAD). Щелчок по ссылке — walk
        // в комнату ссылки: он засчитывается сразу, ещё до модификаций ссылки, поэтому и
        // запрещённый переход (RETURN в ссылке) тратит действие. Технические переходы не
        // считаются: auto-ссылки, autowired-ссылки (LinkLw.isTechnical), GOTO
        if (!fromPageContext && !link.autoFlag
                && link.kind != LinkKind.AutowiredIn && link.kind != LinkKind.AutowiredOut) {
            m_context.countIncrement(PlayerContext.COUNT_WALK);
        }
        // Страница, покинутая по auto-ссылке сразу после показа, без текста — служебная: её
        // заголовок и картинки не показываем (музыку и звуки оставляем — они продолжают играть)
        if (fromPageContext && m_lastRenderStart <= m_events.size()) {
            boolean hasContent = false;
            for (int i = m_lastRenderStart; i < m_events.size(); i++) {
                if (isContentEvent(m_events.get(i).getKind())) {
                    hasContent = true;
                    break;
                }
            }
            if (!hasContent) {
                List<Event> kept = new ArrayList<>(m_events.subList(0, m_lastRenderStart));
                for (int i = m_lastRenderStart; i < m_events.size(); i++) {
                    Event.Kind k = m_events.get(i).getKind();
                    if (k == Event.Kind.Music || k == Event.Kind.Sound || k == Event.Kind.Voice) {
                        kept.add(m_events.get(i));
                    }
                }
                m_events = kept;
            }
            m_lastRenderStart = m_events.size() + 1;  // проверка — только один раз для этой страницы
        }
        NonLinearBook book = m_book;
        // Модификации ссылки; s — страница (auto-ссылка в autos) или комната ссылки (клик)
        if (!link.modifications.isEmpty()) {
            ModificationInterpreter.ExecContext ctx = new ModificationInterpreter.ExecContext(
                    book, fromPageContext ? page.getId() : link.id, null
            );
            if (!m_interpreter.execute(link.modifications, ctx)) {
                // RETURN в модификациях ссылки: return из enter комнаты ссылки (или из autos) —
                // ни переменные ссылки, ни nlbwalk. Так делают «запрещённые» переходы
                flushOutput();
                m_context.takeGoto();
                return;
            }
        }
        if (!link.autowiredVarId.isEmpty()) {
            Variable v = book.getVariableById(link.autowiredVarId);
            if (v != null && !isEmpty(v.getName())) {
                m_context.setVar(v.getName(), Value.of(link.autowiredValue));
            }
        }
        setVarTrue(book, link.varId);
        if (link.once) {
            m_context.setVar(SpecialVariablesNameHelper.decorateLinkVisitStateVar(link.id), Value.TRUE);
        }
        flushOutput();
        // GOTO из модификаций ссылки имеет приоритет над целью ссылки (вложенный nlbwalk)
        String gotoTarget = m_context.takeGoto();
        // enter(s, f): f.autowired истинно, только если переход совершает сама autowired-страница
        boolean fromAutowired = fromPageContext && page.isAutowire();
        walkTo(gotoTarget != null ? gotoTarget : link.target, fromAutowired);
    }

    // ------------------------------------------------------------------ переходы

    /**
     * nlbwalk: вход на страницу. fromAutowired — переход совершается из autowired-страницы
     */
    private void walkTo(String pageId, boolean fromAutowired) {
        Page page = m_interpreter.findPage(pageId);
        if (page == null) {
            m_events.add(new Event(Event.Kind.Info, "Страница не найдена: " + pageId));
            return;
        }
        if (++m_transitions > MAX_TRANSITIONS_PER_COMMAND) {
            throw new PlayerException(
                    "Too many automatic transitions (endless auto-link loop?) at page " + pageId
            );
        }
        enterPage(page, fromAutowired);
    }

    private void enterPage(Page page, boolean fromAutowired) {
        rememberPreservedObjects();
        Page previousPage = m_page;
        NonLinearBook previousBook = m_book;
        m_page = page;
        m_book = m_interpreter.bookOfPage(page.getId());
        m_context.recordPageVisit(page.getId());
        m_interpreter.resetLastText();

        // STEAD enter(s, f): модификации и переменная страницы, если пришли не из autowired-страницы
        if (!fromAutowired) {
            ModificationInterpreter.ExecContext ctx = new ModificationInterpreter.ExecContext(m_book, page.getId(), null);
            if (!m_interpreter.execute(page.getModifications(), ctx)) {
                // RETURN в модификациях страницы — return из enter: остаток enter не выполняется.
                // Вход отменяет только return false; return без значения (nil) переход не отменяет
                Value value = m_interpreter.lastReturnValue();
                if (value != null && value.getType() == Value.Type.BOOL && !value.asBool()) {
                    if (previousPage != null) {
                        m_context.cancelPageVisit();
                        m_page = previousPage;
                        m_book = previousBook;
                        flushOutput();
                        m_context.takeGoto();
                        return;
                    }
                }
                if (m_pendingExternalGame != null) {
                    startPendingExternalGame();
                    return;
                }
                renderPage();
                return;
            }
            setVarTrue(m_book, page.getVarId());
        }
        // initf(): таймер страницы и звук
        if (!isEmpty(page.getTimerVarId())) {
            Variable timer = m_book.getVariableById(page.getTimerVarId());
            if (timer != null && !isEmpty(timer.getName())) {
                m_context.setVar(timer.getName(), Value.of(0L));
            }
        }
        m_interpreter.playSound(page.getId());

        // Вызов внешней игры из модификаций страницы — walk(game_room) внутри enter
        if (m_pendingExternalGame != null) {
            startPendingExternalGame();
            return;
        }
        // GOTO из модификаций страницы: walk внутри enter — страница не показывается
        String gotoTarget = m_context.takeGoto();
        if (gotoTarget != null) {
            flushOutput();
            walkTo(gotoTarget, page.isAutowire());
            return;
        }
        renderPage();
    }

    /**
     * life()/autos(): счётчики, callback-объекты, auto-ссылки. true — был переход.
     * tick — прошёл один тик таймера страницы (переменная таймера +1)
     */
    private boolean runAutos(boolean tick) {
        if (m_page == null || m_externalGame != null) {
            return false;
        }
        Page page = m_page;
        // Таймер (переменная tvarid): обнуляется при входе на страницу (initf) и растёт на 1 за
        // тик. В пошаговом плеере тик — ход игрока на странице, команда w или автоматическое
        // ожидание. Переход на новую страницу тиком не считается
        if (tick && !isEmpty(page.getTimerVarId())) {
            Variable timer = m_book.getVariableById(page.getTimerVarId());
            if (timer != null && !isEmpty(timer.getName())) {
                String name = timer.getName();
                m_context.setVar(name, Value.of(m_context.getVar(name).asInt() + 1));
            }
        }
        // update_action_count(): при новых действиях игрока — callback-объекты
        long diff = m_context.countGet(0);
        if (diff > m_actionCountDiffPrev) {
            for (Map.Entry<String, Obj> entry : m_interpreter.objects().entrySet()) {
                if (entry.getValue().isCallback() && m_interpreter.isObjEnabled(entry.getKey())) {
                    m_interpreter.objActA(entry.getKey());
                }
            }
            flushOutput();
        }
        m_actionCountDiffPrev = diff;

        // auto-ссылки: первая сработавшая выполняется, остальные — нет (return false в STEAD)
        for (PlayerLink link : buildLinks()) {
            if (link.autoFlag && isLinkAvailable(link) && (!link.needsAction || hasAction())) {
                followLink(link, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Сколько тиков таймера проматывать автоматически на текущей странице (0 — не ждать)
     */
    private int autoWaitTicks() {
        if (!hasTimer() || !availableChoices().isEmpty()) {
            return 0;
        }
        boolean hasAuto = false;
        for (PlayerLink link : buildLinks()) {
            hasAuto = hasAuto || link.autoFlag;
        }
        if (!hasAuto) {
            return 0;
        }
        // Ждать больше нечего — проматываем сколько потребуется (анимация гиперперехода).
        // На странице есть объекты — игрок мог бы щёлкнуть, пока идёт время, поэтому сами
        // проматываем только короткую паузу (ход противника в бою); длинную — командой w
        return sceneObjects().isEmpty() ? MAX_TIMER_TICKS : SHORT_WAIT_TICKS;
    }

    /**
     * playerTurn — ход игрока на текущей странице: сначала проходит один тик таймера
     */
    private void settle(boolean playerTurn) {
        // В INSTEAD таймер тикает сам (обычно раз в 200 мс). Если выбирать на странице нечего,
        // а auto-ссылки ждут таймера, время "проматывается" (см. autoWaitTicks)
        Page page = m_page;
        boolean tick = playerTurn;
        int ticks = 0;
        while (true) {
            // Первый вызов — с тиком (если он положен), после перехода — без: у новой страницы таймер 0
            if (runAutos(tick)) {
                while (runAutos(false)) {
                    // цепочка автоматических переходов
                }
            }
            if (m_page != page) {
                page = m_page;
                ticks = 0;
            }
            if (m_externalGame != null || ++ticks > autoWaitTicks()) {
                break;
            }
            tick = true;  // следующая итерация — прошёл ещё один тик
        }
        startPendingExternalGame();
        if (m_externalGame == null && isFinished()) {
            m_events.add(new Event(Event.Kind.Finish, ""));
        }
    }

    private void startPendingExternalGame() {
        if (m_pendingExternalGame == null || m_page == null) {
            return;
        }
        m_externalGame = new ExternalGame(m_pendingExternalGame, m_page.getId());
        m_pendingExternalGame = null;
        m_events.add(new Event(Event.Kind.ExternalGame, m_externalGame.getName()));
    }

    /**
     * Действие игрока выполнено: auto-ссылки, затем обновление картинок, если страница не сменилась
     */
    private void finishAction(boolean playerTurn) {
        int renders = m_renderCount;
        settle(playerTurn);
        if (m_renderCount == renders) {
            refreshSceneImages();
        }
    }

    // ------------------------------------------------------------------ отображение

    private static String imageKey(PlayerContext.OutputItem image) {
        return image.getKind().ordinal() + "|" + image.getText() + "|" + image.getFrames();
    }

    private static Event.Kind imageEventKind(PlayerContext.OutputItem image) {
        return image.getKind() == PlayerContext.OutputKind.Animation ? Event.Kind.Animation : Event.Kind.Image;
    }

    private void emitObjectImage(String instanceId, PlayerContext.OutputItem image) {
        m_events.add(new Event(
                imageEventKind(image), image.getText(), image.getFrames(), "",
                sceneLabel(instanceId), objPlacement(instanceId).toString()
        ));
        m_shownImages.put(instanceId, imageKey(image));
        m_preservedImagesShown.put(instanceId, imageKey(image));
    }

    /**
     * После действия на той же странице: показать картинки, которые сменились (например,
     * флажок «Показывать эту страницу») или появились (новые карты на столе)
     */
    private void refreshSceneImages() {
        if (m_page == null || m_rootBook.isSuppressMedia()) {
            return;
        }
        // Картинка страницы (выбор по тегу мог измениться)
        PlayerContext.OutputItem pageImage = m_interpreter.selectImage(m_page.getId());
        if (pageImage != null) {
            String key = imageKey(pageImage);
            if (!key.equals(m_shownImages.get(m_page.getId()))) {
                m_events.add(new Event(imageEventKind(pageImage), pageImage.getText(), pageImage.getFrames(), "", "", ""));
                m_shownImages.put(m_page.getId(), key);
            }
        }
        for (String instanceId : sceneObjects()) {
            Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
            if (obj == null || !obj.isImageInScene() || m_context.isCollapsed(instanceId)) {
                continue;
            }
            PlayerContext.OutputItem image = m_interpreter.selectImage(instanceId);
            if (image != null) {
                String shown = m_shownImages.get(instanceId);
                if (shown == null || !shown.equals(imageKey(image))) {
                    emitObjectImage(instanceId, image);
                }
            }
        }
    }

    private void renderPage() {
        Page page = m_page;
        ++m_renderCount;
        m_shownImages.clear();
        m_lastRenderStart = m_events.size();
        // s:pic() — выбор картинки по тегу страницы; звук страницы уже выдан в enterPage
        List<PlayerContext.OutputItem> output = m_context.takeOutput();
        if (!m_rootBook.isSuppressMedia()) {
            PlayerContext.OutputItem image = m_interpreter.selectImage(page.getId());
            if (image != null) {
                output.add(0, image);
                m_shownImages.put(page.getId(), imageKey(image));
            }
        }
        // Картинки и звуки — перед текстом страницы
        appendMediaEvents(output);
        // Картинки объектов на странице (imageInScene), после картинки страницы
        List<String> visible = sceneObjects();
        // preserved-объект, исчезнувший с экрана, при следующем появлении показывается заново
        Iterator<Map.Entry<String, String>> it = m_preservedImagesShown.entrySet().iterator();
        while (it.hasNext()) {
            String id = it.next().getKey();
            if (!visible.contains(id) || m_context.isCollapsed(id)) {
                it.remove();
            }
        }
        for (String instanceId : visible) {
            Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
            if (obj == null || !obj.isImageInScene() || m_context.isCollapsed(instanceId)) {
                continue;
            }
            PlayerContext.OutputItem image = m_interpreter.selectImage(instanceId);
            if (image != null) {
                if (obj.isPreserved()) {
                    String shown = m_preservedImagesShown.get(instanceId);
                    if (shown != null && shown.equals(imageKey(image))) {
                        m_shownImages.put(instanceId, shown);  // уже на экране с прошлой страницы
                        continue;
                    }
                }
                emitObjectImage(instanceId, image);
            }
        }
        boolean vn = isVnPage(page);
        // VN: заголовок окна — название книги, подпись страницы не показывается
        if (!vn && page.isUseCaption() && !isEmpty(page.getCaption())) {
            String caption = displayText(m_interpreter.expandText(page.getCaption()));
            if (!trim(caption).isEmpty()) {
                m_events.add(new Event(Event.Kind.PageCaption, caption));
            }
        }
        String text = displayText(m_interpreter.expandText(nz(page.getText())));
        if (!text.isEmpty()) {
            m_events.add(new Event(Event.Kind.PageText, text));
        }
        // Описания объектов (dsc): в VN-экспорте комната не содержит объектов и альтернативных
        // текстов ссылок. decorateObjText: dsc печатает dscf, кроме suppress_dsc
        if (!vn) {
            for (String instanceId : visible) {
                Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
                if (obj == null || obj.isSuppressDsc()) {
                    continue;
                }
                // {метка} -> [метка]; пустая метка — щёлкают по картинке, она уже выведена отдельно
                String dsc = displayText(replaceInteractionMarks(m_interpreter.objDscF(instanceId)));
                if (!trim(dsc).isEmpty()) {
                    m_events.add(new Event(Event.Kind.ObjectText, dsc));
                }
            }
            // xdsc: альтернативные тексты недоступных ссылок
            for (PlayerLink link : buildLinks()) {
                if (!link.autoFlag && !link.altText.isEmpty() && !isLinkAvailable(link)) {
                    m_events.add(new Event(Event.Kind.AltText, displayText(m_interpreter.expandText(link.altText))));
                }
            }
        }
        // Текст из модификаций страницы, затем достижения — после текста страницы
        appendTextEvents(output);
    }

    private void flushOutput() {
        List<PlayerContext.OutputItem> output = m_context.takeOutput();
        // Звуки и картинки — первыми, достижения — последними
        appendMediaEvents(output);
        appendTextEvents(output);
    }

    private void appendTextEvents(List<PlayerContext.OutputItem> output) {
        for (PlayerContext.OutputItem item : output) {
            if (item.getKind() == PlayerContext.OutputKind.Text) {
                String text = displayText(replaceInteractionMarks(item.getText()));
                if (!trim(text).isEmpty()) {
                    m_events.add(new Event(Event.Kind.Text, text));
                }
            } else if (item.getKind() == PlayerContext.OutputKind.Info) {
                m_events.add(new Event(Event.Kind.Info, item.getText()));
            }
        }
        for (PlayerContext.OutputItem item : output) {
            if (item.getKind() == PlayerContext.OutputKind.Achievement) {
                String text = item.getText();
                int colon = text.indexOf(": ");  // «Получено достижение: ИМЯ»
                String id = colon < 0 ? text : text.substring(colon + 2);
                m_events.add(new Event(Event.Kind.Achievement, text, 0, id, "", ""));
            }
        }
    }

    private void appendMediaEvents(List<PlayerContext.OutputItem> output) {
        for (PlayerContext.OutputItem item : output) {
            switch (item.getKind()) {
                case Image:
                    m_events.add(new Event(Event.Kind.Image, item.getText()));
                    break;
                case Animation:
                    m_events.add(new Event(Event.Kind.Animation, item.getText(), item.getFrames(), "", "", ""));
                    break;
                case Sound:
                    m_events.add(new Event(Event.Kind.Sound, item.getText()));
                    break;
                case Voice:
                    m_events.add(new Event(Event.Kind.Voice, item.getText()));
                    break;
                case Music:
                    m_events.add(new Event(Event.Kind.Music, item.getText()));
                    m_currentMusic = item.getText();
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Фоновая музыка, которая сейчас играет; пусто — тишина
     */
    public String currentMusic() {
        return m_currentMusic;
    }

    // ------------------------------------------------------------------ объекты

    private String objDisp(String instanceId) {
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        if (obj == null) {
            return instanceId;
        }
        String disp = trim(displayText(m_interpreter.expandText(nz(obj.getDisp()))));
        return disp.isEmpty() ? nz(obj.getName()) : disp;
    }

    /**
     * Объекты, видимые на странице: содержимое комнаты и, рекурсивно, содержимое видимых
     * объектов (INSTEAD показывает вложенные объекты после их контейнера)
     */
    private List<String> sceneObjects() {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        m_sceneDepth.clear();
        if (m_page != null) {
            collectSceneObjects(m_page.getId(), result, seen, 0);
            // preserved-объекты с прошлых страниц остаются на экране, пока они где-то лежат
            // (REMOVE убирает их) и включены
            for (String root : m_preservedRoots) {
                if (seen.contains(root) || isEmpty(m_context.containerOf(root)) || !m_interpreter.isObjEnabled(root)) {
                    continue;
                }
                result.add(root);
                seen.add(root);
                m_sceneDepth.put(root, 0);
                if (!m_context.isCollapsed(root)) {
                    collectSceneObjects(root, result, seen, 1);
                }
            }
        }
        return result;
    }

    private void rememberPreservedObjects() {
        if (m_page == null) {
            return;
        }
        // vn.lua: при смене сцены объекты с preserved остаются. Запоминаем видимые корни
        for (String instanceId : m_context.containerContents(m_page.getId())) {
            Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
            if (obj != null && obj.isGraphical() && obj.isPreserved() && !m_preservedRoots.contains(instanceId)) {
                m_preservedRoots.add(instanceId);
            }
        }
    }

    private void collectSceneObjects(String ownerId, List<String> result, Set<String> seen, int depth) {
        if (depth > 32) {
            return;  // защита от циклической вложенности
        }
        for (String instanceId : m_context.containerContents(ownerId)) {
            if (!m_interpreter.isObjEnabled(instanceId) || seen.contains(instanceId)) {
                continue;  // выключенный объект скрывает и своё содержимое
            }
            result.add(instanceId);
            seen.add(instanceId);
            m_sceneDepth.put(instanceId, depth);
            if (!m_context.isCollapsed(instanceId)) {  // у свёрнутого свитка содержимое не видно
                collectSceneObjects(instanceId, result, seen, depth + 1);
            }
        }
    }

    /**
     * Предметы инвентаря вместе с содержимым предметов-контейнеров
     */
    private List<String> inventoryObjects() {
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String instanceId : m_context.inventory()) {
            if (!m_interpreter.isObjEnabled(instanceId) || seen.contains(instanceId)) {
                continue;
            }
            result.add(instanceId);
            seen.add(instanceId);
            collectSceneObjects(instanceId, result, seen, 1);
        }
        return result;
    }

    private boolean isInInventoryTree(String instanceId) {
        return inventoryObjects().contains(instanceId);
    }

    private boolean isOnScene(String instanceId) {
        return sceneObjects().contains(instanceId);
    }

    private static boolean hasLiveLinks(Obj obj) {
        for (Link link : obj.getLinks()) {
            if (!link.isDeleted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * У объекта есть реакция на щелчок или он участвует в применении предметов
     */
    private boolean hasBehaviour(Obj obj) {
        if (obj == null) {
            return false;
        }
        return obj.isTakable()
                || !obj.getModifications().isEmpty()
                || !isEmpty(obj.getVarId()) || !isEmpty(obj.getMorphOverId())
                || !trim(m_interpreter.expandText(nz(obj.getActText()))).isEmpty()
                || m_interpreter.commonOf(obj) != null
                || m_interpreter.hasInwardUseLinks(obj.getId())
                || hasLiveLinks(obj);
    }

    private Placement objPlacement(String instanceId) {
        Placement p = new Placement();
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        if (obj == null || !obj.isGraphical()) {
            return p;
        }
        p.m_graphical = true;
        int origin = obj.getCoordsOrigin() == null ? 0 : obj.getCoordsOrigin().ordinal();
        p.m_origin = Placement.ORIGINS[origin];
        // decorateObjEffect: у вложенного объекта (контейнер — объект) смещение 0,0 и arm 0,0.
        // Объект, положенный в другой объект во время игры, тоже раскладывается внутри родителя
        String owner = m_context.containerOf(instanceId);
        boolean runtimeParentObj = !isEmpty(owner) && m_interpreter.findObj(m_context.protoOf(owner)) != null;
        boolean hasParentObj = obj.getContainerType() == Obj.ContainerType.Obj || runtimeParentObj;
        if (hasParentObj) {
            p.m_parentId = runtimeParentObj ? owner : nz(obj.getContainerId());
            return p;
        }
        if (!isEmpty(obj.getOffset())) {
            p.m_offset = obj.getOffset();
            return p;
        }
        // ExportManager.getRelativeCoords: координаты относительно страницы, пересчитанные на
        // экран 1920×1080, и getTransformedCoords — отсчёт от выбранного угла
        final double screenW = 1920.0;
        final double screenH = 1080.0;
        Coords rel = obj.getRelativeCoords(true);
        double w = rel.getWidth();
        double h = rel.getHeight();
        double left = w != 0 ? Math.floor(rel.getLeft() * screenW / w) : 0.0;
        double top = h != 0 ? Math.floor(rel.getTop() * screenH / h) : 0.0;
        double x = left;
        double y = top;
        switch (origin) {
            case 1:  // MiddleTop
                x = left - screenW / 2;
                break;
            case 2:  // RightTop (как в экспорте)
                x = screenW - left;
                y = 0;
                break;
            case 3:  // LeftMiddle
                y = screenH / 2 - top;
                break;
            case 4:  // MiddleMiddle
                x = left - screenW / 2;
                y = screenH / 2 - top;
                break;
            case 5:  // RightMiddle
                x = screenW - left;
                y = screenH / 2 - top;
                break;
            case 6:  // LeftBottom
                y = screenH - top;
                break;
            case 7:  // MiddleBottom
                x = left - screenW / 2;
                y = screenH - top;
                break;
            case 8:  // RightBottom
                x = screenW - left;
                y = screenH - top;
                break;
            default:  // LeftTop
                break;
        }
        p.m_armX = (int) x;
        p.m_armY = (int) y;
        return p;
    }

    /**
     * ExportManager.getObjType: MENU — щелчок в инвентаре вызывает act; STAT — ничего не делает;
     * OBJ — inv = use(s, s)
     */
    private ObjKind objKind(Obj obj) {
        if (!obj.isTakable() || hasLiveLinks(obj) || m_interpreter.hasInwardUseLinks(obj.getId())) {
            return ObjKind.Obj;
        }
        if (!obj.isGraphical()
                && !obj.getDisps().isEmpty() && obj.getTexts().isEmpty() && obj.getActTexts().isEmpty()
                && obj.getNouseTexts().isEmpty()
                && obj.getModifications().isEmpty()
                && isEmpty(obj.getCommonToId())) {
            return ObjKind.Stat;
        }
        return ObjKind.Menu;
    }

    private static String firstNonEmptyLine(String text) {
        for (String line : splitLines(text)) {
            if (!trim(line).isEmpty()) {
                return trim(line);
            }
        }
        return "";
    }

    /**
     * Подпись объекта на странице: текст метки {…} из dsc, иначе disp, иначе имя
     */
    private String sceneLabel(String instanceId) {
        String dsc = m_interpreter.objDscF(instanceId);
        int open = dsc.indexOf('{');
        int close = dsc.lastIndexOf('}');
        if (open >= 0 && close > open) {
            String label = trim(displayText(dsc.substring(open + 1, close)));
            if (!label.isEmpty()) {
                return label;
            }
            // {} — щёлкают по картинке; подписью служит текст рядом с ней (первая непустая строка)
            String rest = firstNonEmptyLine(displayText(dsc.substring(0, open) + dsc.substring(close + 1)));
            if (!rest.isEmpty()) {
                return rest;
            }
        }
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        String disp = trim(displayText(m_interpreter.expandText(obj != null ? nz(obj.getDisp()) : "")));
        if (!disp.isEmpty()) {
            return disp;
        }
        // Объект-картинка без подписи (карта в Frontier): подписью служит текст действия
        String act = firstNonEmptyLine(displayText(m_interpreter.objActT(instanceId)));
        if (!act.isEmpty()) {
            return act;
        }
        return objDisp(instanceId);
    }

    private boolean isVisibleInInventory(String instanceId) {
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        if (obj == null) {
            return false;
        }
        if (!trim(m_interpreter.expandText(nz(obj.getDisp()))).isEmpty()) {
            return true;
        }
        return !m_rootBook.isSuppressMedia() && !isEmpty(obj.getImageFileName())
                && obj.isImageInInventory() && !obj.isGraphical();
    }

    private String nouseText(String instanceId) {
        Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
        String text = obj != null ? m_interpreter.expandText(nz(obj.getNouseText())) : "";
        return text.isEmpty() ? m_settings.gameNouseText : text;
    }

    public PageView view() {
        if (m_page == null) {
            return new PageView("", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true);
        }
        List<Choice> choices = new ArrayList<>();
        for (PlayerLink link : availableChoices()) {
            // ExportManager.isInlineLink: в стандартной теме такая ссылка — xact в тексте, а не путь
            char first = link.text.isEmpty() ? '\0' : link.text.charAt(0);
            boolean inlineLink = link.trivial || first == '-' || first == '"' || first == '\'';
            choices.add(new Choice(displayText(m_interpreter.expandText(link.text)), link.trivial, inlineLink));
        }
        List<ObjectView> scene = new ArrayList<>();
        for (String instanceId : sceneObjects()) {
            Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
            if (obj == null) {
                continue;
            }
            // Щёлкнуть можно только по метке {…} в описании или по графическому объекту
            String dsc = m_interpreter.objDscF(instanceId);
            int open = dsc.indexOf('{');
            int close = dsc.lastIndexOf('}');
            if (obj.isGraphical() || (open >= 0 && close > open)) {
                String label = trim(sceneLabel(instanceId));
                boolean active = hasBehaviour(obj);
                if (label.isEmpty()) {
                    // Безымянный графический объект без реакции (фон, подложка) — декорация
                    if (!active) {
                        continue;
                    }
                    label = isEmpty(obj.getImageFileName()) ? instanceId : obj.getImageFileName();
                }
                Integer depth = m_sceneDepth.get(instanceId);
                Placement placement = objPlacement(instanceId);
                scene.add(new ObjectView(
                        instanceId, label, obj.isTakable(), active, placement, placement.toString(),
                        depth == null ? 0 : depth, m_context.isCollapsed(instanceId)
                ));
            }
        }
        List<ObjectView> inventory = new ArrayList<>();
        for (String instanceId : inventoryObjects()) {
            // decorateObjDisp: пустой disp без картинки -> disp = false, предмет в инвентаре не виден
            if (m_interpreter.isObjEnabled(instanceId) && isVisibleInInventory(instanceId)) {
                Obj obj = m_interpreter.findObj(m_context.protoOf(instanceId));
                // OBJ всегда отвечает (use(s, s) или game.inv); MENU — если щелчок что-то делает
                ObjKind kind = obj != null ? objKind(obj) : ObjKind.Stat;
                boolean active = kind == ObjKind.Obj || (kind == ObjKind.Menu && hasBehaviour(obj));
                inventory.add(new ObjectView(
                        instanceId, objDisp(instanceId), false, active, new Placement(),
                        objPlacement(instanceId).toString(), 0, false
                ));
            }
        }
        return new PageView(m_page.getId(), choices, scene, inventory, isFinished());
    }

    public boolean isFinished() {
        if (m_page == null) {
            return true;
        }
        // VN-экспорт: страница без ссылок — конец игры (theEnd: экран выбора с _try_again)
        if (isVnPage(m_page)) {
            return buildLinks().isEmpty();
        }
        // Есть что выбрать или с чем действовать на странице — игра продолжается
        if (!availableChoices().isEmpty() || !sceneObjects().isEmpty()) {
            return false;
        }
        // Финальная страница книги (Page.isFinish): служебные auto-ссылки и предметы инвентаря
        // ход уже не продолжают
        if (m_page.isFinish()) {
            return true;
        }
        // Иначе ходов нет, если нет auto-ссылок, которые могут сработать позже, и инвентарь пуст
        for (PlayerLink link : buildLinks()) {
            if (link.autoFlag) {
                return false;
            }
        }
        return m_context.inventory().isEmpty();
    }

    // ------------------------------------------------------------------ текст

    private static boolean isTrimSpace(char c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n';
    }

    /**
     * Обрезка " \t\r\n" с обеих сторон (как trim в NLBNavigator, не String.trim)
     */
    static String trim(String s) {
        if (s == null) {
            return "";
        }
        int b = 0;
        int e = s.length();
        while (b < e && isTrimSpace(s.charAt(b))) {
            b++;
        }
        while (e > b && isTrimSpace(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(b, e);
    }

    /**
     * Текст для показа: '^' — перевод строки INSTEAD; хвостовые пробелы и переводы строк убираются
     */
    static String displayText(String s) {
        String r = s == null ? "" : s.replace('^', '\n');
        int e = r.length();
        while (e > 0 && isTrimSpace(r.charAt(e - 1))) {
            e--;
        }
        return r.substring(0, e);
    }

    /**
     * Метки взаимодействия в выводимом тексте: {метка} -> [метка], пустая {} убирается
     * (STEAD_OBJ_PATTERN = \{(.*)\}, жадный)
     */
    static String replaceInteractionMarks(String s) {
        int open = s.indexOf('{');
        int close = s.lastIndexOf('}');
        if (open < 0 || close <= open) {
            return s;
        }
        String label = trim(s.substring(open + 1, close));
        return s.substring(0, open) + (label.isEmpty() ? "" : "[" + label + "]") + s.substring(close + 1);
    }

    /**
     * Разбиение по '\n', как std::getline: хвостовой '\n' не даёт пустой строки
     */
    static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int eol = text.indexOf('\n', start);
            if (eol < 0) {
                lines.add(text.substring(start));
                break;
            }
            lines.add(text.substring(start, eol));
            start = eol + 1;
        }
        return lines;
    }
}
