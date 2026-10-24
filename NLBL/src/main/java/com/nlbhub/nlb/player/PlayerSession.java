/**
 * @(#)PlayerSession.java
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Игровая сессия для интерфейса, который рисует снимок экрана сам (веб-клиент): движок в режиме
 * реального времени, снимок экрана, правила щелчков INSTEAD (выбор предмета, применение, осмотр),
 * события и очередь озвучки страницы. Порт Session NLBNavigator без звука и рисования — их делает
 * интерфейс. Интерфейс:
 * <ol>
 *     <li>время от времени вызывает {@link #advance(int)} и, если {@link #revision()} изменился,
 *     перерисовывает {@link #screen()};</li>
 *     <li>на щелчки вызывает {@link #choose}, {@link #clickObject}, {@link #clickItem};</li>
 *     <li>разбирает {@link #takeEvents()}: звуки и музыка, достижения, внешние игры, конец игры.</li>
 * </ol>
 * Сессия — на одного игрока; книгу (модель) могут делить многие сессии, она не меняется.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class PlayerSession {
    private final PlayerEngine m_engine;
    private ScreenModel m_screen;
    private final List<PlayerEngine.Event> m_pending = new ArrayList<>();
    /**
     * Выбранный предмет инвентаря (следующий щелчок применит его); пусто — не выбран
     */
    private String m_selected = "";
    /**
     * Выбор предмета меняет вид, но не снимок движка
     */
    private long m_localRevision = 0;
    /**
     * Озвучка текущей страницы: SFX-файлы поля звука страницы в порядке поля (не стеком, как в
     * nlb.lua); интерфейс запускает очередной файл на каждой новой порции текста VN
     */
    private final List<String> m_voices = new ArrayList<>();
    private int m_nextVoice = 0;

    /**
     * settings.realTime включается всегда: время идёт через {@link #advance(int)}
     */
    public PlayerSession(NonLinearBook book, PlayerEngine.Settings settings) {
        this(book, settings, null);
    }

    /**
     * Сессия, которая начинается не со стартовой страницы, а с сохранения (state — JSON из
     * {@link PlayerEngine#saveGame()} или {@link PlayerEngine#exportState()}): так игра переходит
     * на экземпляр книги на другом языке. null — с начала игры
     */
    public PlayerSession(NonLinearBook book, PlayerEngine.Settings settings, String state) {
        settings.realTime = true;
        m_engine = new PlayerEngine(book, settings);
        if (state == null) {
            m_engine.start();
        } else {
            m_engine.loadGame(state);
        }
        afterCommand();
    }

    /**
     * Движок — для мини-игр и сцен, которым передано управление (getVariable/setVariable, ...);
     * после команд, отданных ему напрямую, — {@link #refresh()}
     */
    public PlayerEngine engine() {
        return m_engine;
    }

    public ScreenModel screen() {
        return m_screen;
    }

    /**
     * Растёт при каждом изменении того, что видит игрок
     */
    public long revision() {
        return m_screen.getRevision() + m_localRevision;
    }

    /**
     * Забрать события движка и обновить снимок — после команд, отданных напрямую engine()
     */
    public void refresh() {
        afterCommand();
    }

    private void afterCommand() {
        List<PlayerEngine.Event> events = m_engine.takeEvents();
        m_pending.addAll(events);
        String pageBefore = m_screen == null ? "" : m_screen.getPageId();
        m_screen = m_engine.screen();
        // Новые файлы озвучки — новая очередь; уход со страницы — очередь прочь
        List<String> voices = new ArrayList<>();
        for (PlayerEngine.Event event : events) {
            if (event.getKind() == PlayerEngine.Event.Kind.Voice) {
                voices.add(event.getText());
            }
        }
        if (!voices.isEmpty() || !pageBefore.equals(m_screen.getPageId())) {
            m_voices.clear();
            m_voices.addAll(voices);
            m_nextVoice = 0;
        }
    }

    /**
     * Прошло реальное время (мс): таймер страницы и кадры анимации
     */
    public void advance(int milliseconds) {
        if (m_engine.advanceTime(milliseconds) > 0) {
            afterCommand();
        }
    }

    /**
     * Переход по варианту с индексом {@link ScreenModel.Choice#getIndex()}
     */
    public boolean choose(int choiceIndex) {
        m_selected = "";
        boolean ok = choiceIndex >= 0 && m_engine.choose(choiceIndex);
        afterCommand();
        return ok;
    }

    /**
     * Щелчок по объекту сцены: если выбран предмет — применить его к объекту, иначе действие
     */
    public boolean clickObject(String objectId) {
        // при выбранном предмете щелчок по menu INSTEAD игнорирует (game.c: ссылка menu — act,
        // в режиме use она не срабатывает), выбор остаётся
        if (!m_selected.isEmpty() && m_engine.isMenuItem(objectId)) {
            return false;
        }
        boolean ok;
        if (!m_selected.isEmpty()) {
            ok = m_engine.use(m_selected, objectId);
            m_selected = "";
        } else {
            ok = m_engine.actOnObject(objectId);
        }
        afterCommand();
        return ok;
    }

    /**
     * Щелчок по предмету: выбрать; повторно — осмотр; при выбранном другом — применить к нему
     */
    public boolean clickItem(String itemId) {
        boolean ok;
        // menu (кнопки боя «Фронтира» и т. п.) срабатывает с первого щелчка, как в INSTEAD
        // (iface.xref: у menu — команда act); при выбранном предмете щелчок по нему INSTEAD
        // игнорирует (game.c: в режиме use ссылка act не срабатывает), выбор остаётся
        boolean menu = m_engine.isMenuItem(itemId);
        if (menu && !m_selected.isEmpty()) {
            return false;
        }
        if (menu || m_selected.equals(itemId)) {
            // menu — действие; второй щелчок по тому же — осмотр (inv)
            m_selected = "";
            ok = m_engine.clickInventory(itemId);
        } else if (!m_selected.isEmpty()) {
            // предмет к предмету
            ok = m_engine.use(m_selected, itemId);
            m_selected = "";
        } else {
            m_selected = itemId;
            ++m_localRevision;
            return true;
        }
        afterCommand();
        return ok;
    }

    public void clearSelection() {
        if (!m_selected.isEmpty()) {
            m_selected = "";
            ++m_localRevision;
        }
    }

    /**
     * Выбранный предмет инвентаря; пусто — не выбран
     */
    public String selectedItem() {
        return m_selected;
    }

    /**
     * «Осмотреться»: снова описание сцены
     */
    public void look() {
        m_engine.look();
        afterCommand();
    }

    /**
     * «Начать заново» из меню: снимок SNAPSHOT забывается
     */
    public void restart() {
        m_selected = "";
        m_engine.restart();
        afterCommand();
    }

    /**
     * «Попробовать ещё раз» в конце игры: к снимку SNAPSHOT, если он есть, иначе сначала
     */
    public void tryAgain() {
        m_selected = "";
        m_engine.tryAgain();
        afterCommand();
    }

    public boolean finishExternalGame() {
        boolean ok = m_engine.finishExternalGame();
        afterCommand();
        return ok;
    }

    /**
     * Сохранение игры (JSON), см. {@link PlayerEngine#saveGame()}
     */
    public String save() {
        return m_engine.saveGame();
    }

    /**
     * Загрузить игру; при ошибке (PlayerException) игра не меняется
     */
    public void load(String json) {
        m_engine.loadGame(json);
        m_selected = "";
        afterCommand();
    }

    /**
     * События с прошлого вызова: звуки, музыка, озвучка, достижения, внешние игры, конец игры
     */
    public List<PlayerEngine.Event> takeEvents() {
        List<PlayerEngine.Event> result = new ArrayList<>(m_pending);
        m_pending.clear();
        return result;
    }

    /**
     * Озвучка текущей страницы целиком, в порядке поля звука
     */
    public List<String> voices() {
        return Collections.unmodifiableList(m_voices);
    }

    /**
     * Следующий файл озвучки (интерфейс зовёт на каждой новой порции текста VN, в стандартной
     * теме — при входе на страницу); null — очередь пуста
     */
    public String nextVoice() {
        return m_nextVoice < m_voices.size() ? m_voices.get(m_nextVoice++) : null;
    }

    /**
     * Фоновая музыка, которая сейчас играет; пусто — тишина
     */
    public String currentMusic() {
        return m_engine.currentMusic();
    }
}
