/**
 * @(#)PlayerContext.java
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

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.Page;
import com.nlbhub.nlb.api.Variable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Состояние игры: переменные, списки, инвентарь, контейнеры, клоны и теги объектов, счётчики
 * действий, история страниц, снимок SNAPSHOT, очередь вывода. Порт PlayerContext из
 * NLBNavigator; поведение — как в nlb.lua (STEAD-экспорт).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class PlayerContext {
    /**
     * Вид элемента вывода. Плеер решает, где его показать: картинки и звуки — перед текстом
     * страницы, достижения — после
     */
    public enum OutputKind {Text, Image, Animation, Sound, Music, Achievement, Info, Voice}

    public static final class OutputItem {
        private final OutputKind m_kind;
        private final String m_text;
        /**
         * Для Animation — число кадров (шаблон имени кадра с %d — в text)
         */
        private final int m_frames;

        public OutputItem(OutputKind kind, String text, int frames) {
            m_kind = kind;
            m_text = text;
            m_frames = frames;
        }

        public OutputKind getKind() {
            return m_kind;
        }

        public String getText() {
            return m_text;
        }

        public int getFrames() {
            return m_frames;
        }
    }

    /**
     * Типы статистики для COUNTGET — битовая маска, как в nlb.lua count_get (0 — все)
     */
    public static final int COUNT_INV = 1;
    public static final int COUNT_ACT = 2;
    public static final int COUNT_USE = 4;
    public static final int COUNT_WALK = 8;
    private static final int[] COUNT_TYPES = {COUNT_INV, COUNT_ACT, COUNT_USE, COUNT_WALK};

    private static final String CLONE_SEPARATOR = "#";
    private static final String ACHIEVEMENT_PREFIX = "Получено достижение: ";

    /**
     * Игровое состояние, которое сохраняет SNAPSHOT
     */
    private static final class GameState {
        private Map<String, Deque<String>> m_lists = new TreeMap<>();
        private List<String> m_inventory = new ArrayList<>();
        private Map<String, List<String>> m_containers = new TreeMap<>();
        private Map<String, String> m_containerOf = new TreeMap<>();
        private Map<String, String> m_cloneProto = new TreeMap<>();
        private Map<String, Integer> m_cloneCounters = new TreeMap<>();
        private Map<String, String> m_tags = new TreeMap<>();
        private Map<String, Long> m_counts = new TreeMap<>();
        private Map<String, Long> m_countsAtReset = new TreeMap<>();
        private List<String> m_pageHistory = new ArrayList<>();
        private Set<String> m_followedLinks = new TreeSet<>();
        private Set<NonLinearBook> m_initializedBooks = Collections.newSetFromMap(new IdentityHashMap<>());
        private Set<String> m_collapsed = new TreeSet<>();

        private GameState copy() {
            GameState result = new GameState();
            for (Map.Entry<String, Deque<String>> entry : m_lists.entrySet()) {
                result.m_lists.put(entry.getKey(), new ArrayDeque<>(entry.getValue()));
            }
            result.m_inventory = new ArrayList<>(m_inventory);
            for (Map.Entry<String, List<String>> entry : m_containers.entrySet()) {
                result.m_containers.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            result.m_containerOf = new TreeMap<>(m_containerOf);
            result.m_cloneProto = new TreeMap<>(m_cloneProto);
            result.m_cloneCounters = new TreeMap<>(m_cloneCounters);
            result.m_tags = new TreeMap<>(m_tags);
            result.m_counts = new TreeMap<>(m_counts);
            result.m_countsAtReset = new TreeMap<>(m_countsAtReset);
            result.m_pageHistory = new ArrayList<>(m_pageHistory);
            result.m_followedLinks = new TreeSet<>(m_followedLinks);
            result.m_initializedBooks.addAll(m_initializedBooks);
            result.m_collapsed = new TreeSet<>(m_collapsed);
            return result;
        }
    }

    private final NonLinearBook m_rootBook;
    /**
     * Переменные книги и служебные. Имена глобальны, как в STEAD
     */
    private Map<String, Value> m_vars = new TreeMap<>();
    private GameState m_state = new GameState();

    private boolean m_hasSnapshot = false;
    private Map<String, Value> m_snapshotVars = new TreeMap<>();
    private GameState m_snapshotState = new GameState();

    private final Achievements m_achievements;
    private final String m_perfectGameName;

    private final List<OutputItem> m_output = new ArrayList<>();
    private String m_goto = null;
    private final Random m_random;
    private boolean m_deterministic = false;
    private long m_deterministicCounter = 0;

    public PlayerContext(NonLinearBook rootBook, Achievements achievements, long randomSeed) {
        m_rootBook = rootBook;
        m_achievements = achievements;
        m_random = new Random(randomSeed);
        // Литералы true/false в выражениях книги
        m_vars.put("true", Value.TRUE);
        m_vars.put("false", Value.FALSE);
        // generateVarsInitBlock в STEAD-экспорте: _export_lang = '<язык экспорта>'. Игры выбирают
        // по нему языковые варианты (например, теги LangRu/LangEn для картинок)
        if (rootBook != null) {
            m_vars.put("export_lang", Value.of(rootBook.getLanguage()));
            m_perfectGameName = rootBook.getPerfectGameAchievementName() == null
                    ? "" : rootBook.getPerfectGameAchievementName();
            registerAchievements(rootBook);
            initContainers(rootBook);
            ensureBookVars(rootBook);
        } else {
            m_perfectGameName = "";
        }
    }

    /**
     * Режим сверки с INSTEAD и NLBNavigator: k-й вызов rnd(n) даёт (k mod n) + 1, SHUFFLE
     * разворачивает список (как nlb.shuffle при тождественной перестановке)
     */
    public void setDeterministic(boolean deterministic, long start) {
        m_deterministic = deterministic;
        m_deterministicCounter = start;
    }

    // ------------------------------------------------------------------ переменные

    /**
     * Заводит отсутствующие переменные книги и всех её модулей со значениями по умолчанию по типу
     * данных, как initializeVariables() STEAD-экспорта: NUMBER -> 0, STRING -> "",
     * BOOLEAN/AUTO -> false. Уже существующие не трогает
     */
    public void ensureBookVars(NonLinearBook book) {
        if (book == null || m_state.m_initializedBooks.contains(book)) {
            return;
        }
        m_state.m_initializedBooks.add(book);
        for (Variable var : book.getVariables()) {
            if (var == null || var.isDeleted() || isEmpty(var.getName()) || hasVar(var.getName())) {
                continue;
            }
            // Как getVariableDataTypes(): переменные состояния — только эти типы
            switch (var.getType()) {
                case PAGE:
                case TIMER:
                case OBJ:
                case LINK:
                case VAR:
                    break;
                default:
                    continue;
            }
            switch (var.getDataType()) {
                case NUMBER:
                    m_vars.put(var.getName(), Value.of(0L));
                    break;
                case STRING:
                    m_vars.put(var.getName(), Value.of(""));
                    break;
                case BOOLEAN:
                case AUTO:
                default:
                    m_vars.put(var.getName(), Value.FALSE);
                    break;
            }
        }
        // initializeVariables() в STEAD охватывает и модули (getVariableDataTypes рекурсивен)
        for (Page page : sortedPages(book)) {
            NonLinearBook module = page.getModule();
            if (module != null && !module.isEmpty()) {
                ensureBookVars(module);
            }
        }
    }

    public boolean hasVar(String name) {
        return m_vars.containsKey(name);
    }

    public void setVar(String name, Value value) {
        m_vars.put(name, value);
    }

    /**
     * Отсутствующая переменная — false
     */
    public Value getVar(String name) {
        Value value = m_vars.get(name);
        return value == null ? Value.FALSE : value;
    }

    /**
     * @return переменная или null, если её нет
     */
    public Value findVar(String name) {
        return m_vars.get(name);
    }

    /**
     * Все переменные (отсортированы по имени)
     */
    public Map<String, Value> variables() {
        return Collections.unmodifiableMap(m_vars);
    }

    // ------------------------------------------------------------------ списки
    // push/pop работают с ГОЛОВОЙ списка, inject/eject — с ХВОСТОМ (nlb.lua)

    public void listPush(String list, String value) {
        m_state.m_lists.computeIfAbsent(list, k -> new ArrayDeque<>()).addFirst(value);
    }

    public String listPop(String list) {
        Deque<String> items = m_state.m_lists.get(list);
        if (items == null || items.isEmpty()) {
            return null;
        }
        String value = items.pollFirst();
        if (items.isEmpty()) {
            m_state.m_lists.remove(list);  // nlb.lua: после снятия последнего элемента список = nil
        }
        return value;
    }

    public void listInject(String list, String value) {
        m_state.m_lists.computeIfAbsent(list, k -> new ArrayDeque<>()).addLast(value);
    }

    public String listEject(String list) {
        Deque<String> items = m_state.m_lists.get(list);
        if (items == null || items.isEmpty()) {
            return null;
        }
        String value = items.pollLast();
        if (items.isEmpty()) {
            m_state.m_lists.remove(list);
        }
        return value;
    }

    /**
     * Удаляет первое вхождение (nlb.lua rmv)
     */
    public void listRemove(String list, String value) {
        Deque<String> items = m_state.m_lists.get(list);
        if (items == null) {
            return;
        }
        if (items.removeFirstOccurrence(value) && items.isEmpty()) {
            m_state.m_lists.remove(list);
        }
    }

    public void listShuffle(String list) {
        Deque<String> items = m_state.m_lists.get(list);
        if (items == null) {
            return;
        }
        List<String> values = new ArrayList<>(items);
        if (m_deterministic) {
            Collections.reverse(values);
        } else {
            Collections.shuffle(values, m_random);
        }
        items.clear();
        items.addAll(values);
    }

    public void listClear(String list) {
        m_state.m_lists.remove(list);
    }

    public int listSize(String list) {
        Deque<String> items = m_state.m_lists.get(list);
        return items == null ? 0 : items.size();
    }

    public boolean listExists(String list) {
        return m_state.m_lists.containsKey(list);
    }

    /**
     * Пустой список, если списка нет
     */
    public List<String> listItems(String list) {
        Deque<String> items = m_state.m_lists.get(list);
        return items == null ? new ArrayList<>() : new ArrayList<>(items);
    }

    // ------------------------------------------------------------------ инвентарь

    public boolean inInventory(String instanceId) {
        return m_state.m_inventory.contains(instanceId);
    }

    /**
     * Добавляет экземпляр, если его там ещё нет
     */
    public void inventoryAdd(String instanceId) {
        if (!inInventory(instanceId)) {
            m_state.m_inventory.add(instanceId);
        }
    }

    public void inventoryRemove(String instanceId) {
        m_state.m_inventory.removeIf(id -> id.equals(instanceId));
    }

    public void inventoryClear() {
        m_state.m_inventory.clear();
    }

    public List<String> inventory() {
        return Collections.unmodifiableList(m_state.m_inventory);
    }

    // ------------------------------------------------------------------ контейнеры
    // Владелец — id страницы или экземпляра объекта

    public List<String> containerContents(String ownerId) {
        List<String> items = m_state.m_containers.get(ownerId);
        return items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    public boolean containerHas(String ownerId, String instanceId) {
        return containerContents(ownerId).contains(instanceId);
    }

    /**
     * Кладёт экземпляр в контейнер (без дублей) и запоминает его владельца. Из прежнего места НЕ
     * удаляет — как objs(target):add в STEAD
     */
    public void containerAdd(String ownerId, String instanceId) {
        if (!containerHas(ownerId, instanceId)) {
            m_state.m_containers.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(instanceId);
        }
        m_state.m_containerOf.put(instanceId, ownerId);
    }

    /**
     * Удаляет из контейнера и сбрасывает владельца (как decorateDelObj: container = nil)
     */
    public void containerRemove(String ownerId, String instanceId) {
        List<String> items = m_state.m_containers.get(ownerId);
        if (items != null) {
            items.removeIf(id -> id.equals(instanceId));
        }
        m_state.m_containerOf.remove(instanceId);
    }

    public void containerClear(String ownerId) {
        List<String> items = m_state.m_containers.get(ownerId);
        if (items == null) {
            return;
        }
        for (String instanceId : items) {
            if (ownerId.equals(m_state.m_containerOf.get(instanceId))) {
                m_state.m_containerOf.remove(instanceId);
            }
        }
        items.clear();
    }

    /**
     * Последний владелец экземпляра; пустая строка — нигде
     */
    public String containerOf(String instanceId) {
        String owner = m_state.m_containerOf.get(instanceId);
        return owner == null ? "" : owner;
    }

    // ------------------------------------------------------------------ клоны, теги, свёртывание

    /**
     * Создаёт клон экземпляра (тег копируется — deepcopy в nlb.lua) и возвращает его id
     */
    public String cloneInstance(String instanceId) {
        String proto = protoOf(instanceId);
        int n = m_state.m_cloneCounters.merge(proto, 1, Integer::sum);  // nlb.lua clonefd
        String cloneId = proto + CLONE_SEPARATOR + n;
        m_state.m_cloneProto.put(cloneId, proto);
        String tag = m_state.m_tags.get(instanceId);
        if (tag != null) {
            m_state.m_tags.put(cloneId, tag);
        }
        return cloneId;
    }

    /**
     * id исходного объекта книги; для не-клона — сам instanceId
     */
    public String protoOf(String instanceId) {
        String proto = m_state.m_cloneProto.get(instanceId);
        return proto == null ? instanceId : proto;
    }

    public boolean isClone(String instanceId) {
        return m_state.m_cloneProto.containsKey(instanceId);
    }

    /**
     * @return тег или null, если тег не задан
     */
    public String tag(String instanceId) {
        return m_state.m_tags.get(instanceId);
    }

    public void setTag(String instanceId, String tag) {
        m_state.m_tags.put(instanceId, tag);
    }

    /**
     * Сворачиваемые (collapsable) графические объекты: свиток навыков/инвентаря
     */
    public boolean isCollapsed(String instanceId) {
        return m_state.m_collapsed.contains(instanceId);
    }

    public void toggleCollapsed(String instanceId) {
        if (!m_state.m_collapsed.remove(instanceId)) {
            m_state.m_collapsed.add(instanceId);
        }
    }

    // ------------------------------------------------------------------ достижения

    private void registerAchievements(NonLinearBook rootBook) {
        // STEAD-экспорт заводит prefs.achievements_ids[name] = {} для всех достижений книги и модулей
        for (String name : rootBook.getAllAchievementNames(true)) {
            m_achievements.register(name);
        }
        if (!m_perfectGameName.isEmpty()) {
            m_achievements.register(m_perfectGameName);
        }
    }

    public void setAchievementMax(String name, int max) {
        m_achievements.setMax(name, max);
    }

    /**
     * nlb.lua setAchievement: прогресс = число РАЗНЫХ modificationId. При первом получении
     * печатает «Получено достижение: NAME»; проверяет perfect game
     */
    public void achieve(String name, String modificationId) {
        m_achievements.addId(name, modificationId);
        // nlb.lua storeAchievement
        Integer max = m_achievements.max(name);
        if (max == null || achievementCount(name) >= max) {
            announceAchievement(name);
        }
        // perfect game — когда все прочие достижения набрали свой максимум
        boolean perfect = !m_perfectGameName.isEmpty();
        for (Map.Entry<String, Set<String>> entry : m_achievements.getIds().entrySet()) {
            if (!perfect) {
                break;
            }
            if (entry.getKey().equals(m_perfectGameName)) {
                continue;
            }
            Integer otherMax = m_achievements.max(entry.getKey());
            int required = otherMax == null ? 1 : otherMax;
            perfect = entry.getValue().size() >= required;
        }
        if (perfect) {
            m_achievements.addId(m_perfectGameName, modificationId);
            announceAchievement(m_perfectGameName);
        }
    }

    public int achievementCount(String name) {
        return m_achievements.count(name);
    }

    public boolean isAchievementGranted(String name) {
        return m_achievements.isGranted(name);
    }

    public String getPerfectGameAchievementName() {
        return m_perfectGameName;
    }

    private void announceAchievement(String name) {
        if (m_achievements.grant(name)) {
            emit(OutputKind.Achievement, ACHIEVEMENT_PREFIX + name);
        }
    }

    // ------------------------------------------------------------------ счётчики (COUNTGET)

    private static String countKey(int type) {
        switch (type) {
            case COUNT_INV:
                return "inv";
            case COUNT_ACT:
                return "act";
            case COUNT_USE:
                return "use";
            case COUNT_WALK:
                return "walk";
            default:
                return "";
        }
    }

    public void countIncrement(int type) {
        m_state.m_counts.merge(countKey(type), 1L, Long::sum);
    }

    public long countGet(long statType) {
        long diff = 0;
        for (int type : COUNT_TYPES) {
            if (statType == 0 || (statType & type) != 0) {
                String key = countKey(type);
                diff += m_state.m_counts.getOrDefault(key, 0L) - m_state.m_countsAtReset.getOrDefault(key, 0L);
            }
        }
        return diff;
    }

    public void countReset() {
        m_state.m_countsAtReset = new TreeMap<>(m_state.m_counts);
    }

    // ------------------------------------------------------------------ SNAPSHOT

    public void makeSnapshot() {
        m_snapshotVars = new TreeMap<>(m_vars);
        m_snapshotState = m_state.copy();
        m_hasSnapshot = true;
    }

    public boolean hasSnapshot() {
        return m_hasSnapshot;
    }

    /**
     * Восстанавливает игровое состояние; достижения и очередь вывода не трогает
     */
    public boolean restoreSnapshot() {
        if (!m_hasSnapshot) {
            return false;
        }
        m_vars = new TreeMap<>(m_snapshotVars);
        m_state = m_snapshotState.copy();
        m_goto = null;
        return true;
    }

    // ------------------------------------------------------------------ вывод и навигация

    public void emit(OutputKind kind, String text) {
        emit(kind, text, 0);
    }

    public void emit(OutputKind kind, String text, int frames) {
        m_output.add(new OutputItem(kind, text, frames));
    }

    /**
     * Забирает накопленный вывод (очередь очищается)
     */
    public List<OutputItem> takeOutput() {
        List<OutputItem> result = new ArrayList<>(m_output);
        m_output.clear();
        return result;
    }

    public void requestGoto(String pageId) {
        m_goto = pageId;
    }

    /**
     * @return запрошенный GOTO или null
     */
    public String takeGoto() {
        String result = m_goto;
        m_goto = null;
        return result;
    }

    public void recordPageVisit(String pageId) {
        m_state.m_pageHistory.add(pageId);
    }

    public void cancelPageVisit() {
        if (!m_state.m_pageHistory.isEmpty()) {
            m_state.m_pageHistory.remove(m_state.m_pageHistory.size() - 1);
        }
    }

    public String currentPageId() {
        List<String> history = m_state.m_pageHistory;
        return history.isEmpty() ? "" : history.get(history.size() - 1);
    }

    public String previousPageId() {
        List<String> history = m_state.m_pageHistory;
        return history.size() < 2 ? "" : history.get(history.size() - 2);
    }

    public void recordLinkFollowed(String linkId) {
        m_state.m_followedLinks.add(linkId);
    }

    public boolean wasLinkFollowed(String linkId) {
        return m_state.m_followedLinks.contains(linkId);
    }

    /**
     * Равномерно на [1, max] (INSTEAD rnd); max < 1 -> 1
     */
    public long random(long max) {
        final long n = Math.max(1, max);
        if (m_deterministic) {
            return Math.floorMod(m_deterministicCounter++, n) + 1;
        }
        return 1 + (long) (m_random.nextDouble() * n);
    }

    // ------------------------------------------------------------------ инициализация

    private void initContainers(NonLinearBook book) {
        if (book == null) {
            return;
        }
        for (Page page : sortedPages(book)) {
            for (String objId : page.getContainedObjIds()) {
                containerAdd(page.getId(), objId);
            }
            NonLinearBook module = page.getModule();
            if (module != null && !module.isEmpty()) {
                initContainers(module);
            }
        }
        for (Obj obj : sortedObjs(book)) {
            for (String containedId : obj.getContainedObjIds()) {
                containerAdd(obj.getId(), containedId);
            }
            // Страховка: объект знает свой контейнер, даже если список владельца его не содержит
            String containerId = obj.getContainerId();
            if (!isEmpty(containerId) && !containerHas(containerId, obj.getId())) {
                containerAdd(containerId, obj.getId());
            }
        }
    }

    // ------------------------------------------------------------------ вспомогательное

    static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /**
     * Страницы книги в порядке id — как std::map в NLBNavigator
     */
    static List<Page> sortedPages(NonLinearBook book) {
        return new ArrayList<>(new TreeMap<>(book.getPages()).values());
    }

    /**
     * Объекты книги в порядке id — как std::map в NLBNavigator
     */
    static List<Obj> sortedObjs(NonLinearBook book) {
        return new ArrayList<>(new TreeMap<>(book.getObjs()).values());
    }
}
