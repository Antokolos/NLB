/**
 * @(#)WebSession.java
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
package com.nlbhub.nlb.web.player;

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.api.config.Export;
import com.nlbhub.nlb.player.Achievements;
import com.nlbhub.nlb.player.PlayerEngine;
import com.nlbhub.nlb.player.PlayerSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Игра одного игрока в веб-плеере: сессия плеера на экземпляре книги нужного языка, режим
 * экспорта, прогресс достижений (его хранит клиент и передаёт при начале игры). Методы сессии
 * вызываются под её монитором: запросы одного игрока могут прийти одновременно
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
final class WebSession {
    private final String m_id;
    private final String m_bookId;
    private final PlayerEngine.ExportMode m_mode;
    private final Export m_export;
    private final Achievements m_achievements;
    private String m_language;
    private PlayerSession m_session;
    private volatile long m_lastAccess = System.currentTimeMillis();
    /**
     * События старой сессии, не доставленные до смены языка
     */
    private final List<PlayerEngine.Event> m_pending = new ArrayList<>();

    WebSession(String id, String bookId, NonLinearBook book, String language, PlayerEngine.ExportMode mode,
               Export export, Achievements achievements) {
        m_id = id;
        m_bookId = bookId;
        m_mode = mode;
        m_export = export;
        m_achievements = achievements;
        m_language = language;
        m_session = new PlayerSession(book, settings(book), null);
    }

    private PlayerEngine.Settings settings(NonLinearBook book) {
        PlayerEngine.Settings settings = PlayerEngine.Settings.fromExport(m_export, book.getLanguage());
        settings.exportMode = m_mode;
        settings.achievements = m_achievements;
        return settings;
    }

    String id() {
        return m_id;
    }

    String bookId() {
        return m_bookId;
    }

    String language() {
        return m_language;
    }

    PlayerEngine.ExportMode mode() {
        return m_mode;
    }

    Achievements achievements() {
        return m_achievements;
    }

    PlayerSession session() {
        return m_session;
    }

    void touch() {
        m_lastAccess = System.currentTimeMillis();
    }

    long lastAccess() {
        return m_lastAccess;
    }

    /**
     * Перейти на экземпляр книги на другом языке: игра переносится сохранением и загрузкой
     * (id страниц, объектов и переменных во всех экземплярах одни и те же), страница
     * показывается заново уже на новом языке. Недоставленные события старой сессии не теряются
     */
    void switchLanguage(NonLinearBook book, String language) {
        String state = m_session.engine().exportState();
        m_pending.addAll(m_session.takeEvents());
        m_session = new PlayerSession(book, settings(book), state);
        m_language = language;
    }

    /**
     * События сессии вместе с недоставленными до смены языка
     */
    List<PlayerEngine.Event> takeEvents() {
        List<PlayerEngine.Event> events = new ArrayList<>(m_pending);
        m_pending.clear();
        events.addAll(m_session.takeEvents());
        return events;
    }
}
