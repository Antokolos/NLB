/**
 * @(#)Achievements.java
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Прогресс достижений. Как prefs в INSTEAD, он живёт отдельно от состояния игры: переживает
 * «Начать заново», SNAPSHOT и загрузку сохранения. Хранение между запусками плеера — забота
 * того, кто им владеет (снимок — {@link #getIds()}, {@link #getMax()}, {@link #getGranted()}).
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class Achievements {
    /**
     * Имя достижения -> id модификаций ACHIEVE, которые его засчитали
     */
    private final Map<String, Set<String>> m_ids = new TreeMap<>();
    private final Map<String, Integer> m_max = new TreeMap<>();
    private final Set<String> m_granted = new TreeSet<>();

    public Map<String, Set<String>> getIds() {
        return Collections.unmodifiableMap(m_ids);
    }

    public Map<String, Integer> getMax() {
        return Collections.unmodifiableMap(m_max);
    }

    public Set<String> getGranted() {
        return Collections.unmodifiableSet(m_granted);
    }

    /**
     * Объявляет достижение (prefs.achievements_ids[name] = {} в STEAD-экспорте)
     */
    void register(String name) {
        m_ids.computeIfAbsent(name, k -> new TreeSet<>());
    }

    void setMax(String name, int max) {
        m_max.put(name, max);
    }

    Integer max(String name) {
        return m_max.get(name);
    }

    void addId(String name, String modificationId) {
        m_ids.computeIfAbsent(name, k -> new TreeSet<>()).add(modificationId);
    }

    int count(String name) {
        Set<String> ids = m_ids.get(name);
        return ids == null ? 0 : ids.size();
    }

    /**
     * @return true, если достижение получено впервые
     */
    boolean grant(String name) {
        return m_granted.add(name);
    }

    boolean isGranted(String name) {
        return m_granted.contains(name);
    }

    /**
     * Прогресс в JSON: {"ids": {имя: [id модификаций]}, "max": {имя: n}, "granted": [имена]}
     */
    public ObjectNode toJson() {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        ObjectNode ids = result.putObject("ids");
        for (Map.Entry<String, Set<String>> entry : m_ids.entrySet()) {
            ArrayNode array = ids.putArray(entry.getKey());
            entry.getValue().forEach(array::add);
        }
        ObjectNode max = result.putObject("max");
        m_max.forEach(max::put);
        ArrayNode granted = result.putArray("granted");
        m_granted.forEach(granted::add);
        return result;
    }

    /**
     * Добавляет прогресс из JSON ({@link #toJson()}) к текущему
     */
    public void restore(JsonNode json) {
        Map<String, Set<String>> ids = new TreeMap<>();
        json.path("ids").fields().forEachRemaining(
                e -> ids.put(e.getKey(), new TreeSet<>(PlayerContext.strings(e.getValue())))
        );
        Map<String, Integer> max = new TreeMap<>();
        json.path("max").fields().forEachRemaining(e -> max.put(e.getKey(), e.getValue().asInt()));
        restore(ids, max, new TreeSet<>(PlayerContext.strings(json.path("granted"))));
    }

    /**
     * Восстанавливает сохранённый прогресс (без повторных объявлений)
     */
    public void restore(Map<String, ? extends Set<String>> ids, Map<String, Integer> max, Set<String> granted) {
        for (Map.Entry<String, ? extends Set<String>> entry : ids.entrySet()) {
            m_ids.computeIfAbsent(entry.getKey(), k -> new TreeSet<>()).addAll(entry.getValue());
        }
        m_max.putAll(max);
        m_granted.addAll(granted);
    }
}
