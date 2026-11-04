/**
 * @(#)VCSAdapter.java
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
package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.ProgressData;
import com.nlbhub.nlb.exception.NLBVCSException;

/**
 * The VCSAdapter class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/30/13
 */
public interface VCSAdapter {
    public enum Status {Modified, Added, Removed, Missing, Unknown, Clean, Ignored, Conflict, VCS_Undefined}

    public void initRepo(String path) throws NLBVCSException;

    public void openRepo(String path) throws NLBVCSException;

    public void closeAdapter() throws NLBVCSException;

    public boolean getDirAddFlag();

    /**
     * If add to repository should be used on changed files already existent in repo to ensure
     * that this files will be staged for commit
     *
     * @return
     */
    public boolean getAddModifiedFilesFlag();

    public Status getStatus(String path) throws NLBVCSException;

    public void add(String path) throws NLBVCSException;

    /**
     * @return true if file has been physically removed from the working directory, false otherwise
     */
    public boolean remove(String path) throws NLBVCSException;

    public void reset(String path) throws NLBVCSException;

    public void commit(String message) throws NLBVCSException;

    public void pull(String userName, String password, ProgressData progressData) throws NLBVCSException;

    public void push(String userName, String password, ProgressData progressData) throws NLBVCSException;

    /**
     * Новый адаптер того же вида — для отдельного репозитория (внешний модуль книги в modules/ —
     * подмодуль git со своим репозиторием). По умолчанию — без системы контроля версий
     */
    public default VCSAdapter newAdapter() {
        return new DummyVCSAdapter();
    }

    /**
     * Commit от имени author (а не того, кто записан в настройках репозитория): внешние модули
     * коммитятся от имени того же человека, что и книга
     */
    public default void commit(String message, Author author) throws NLBVCSException {
        commit(message);
    }

    /**
     * Кто коммитит: имя и e-mail (user.name, user.email) из настроек репозитория книги, а если их
     * там нет — из общих настроек пользователя; null — не задано или VCS нет
     */
    public default Author getCommitter() {
        return null;
    }

    /**
     * Имя и e-mail того, кто коммитит, можно записать: VCS есть и репозиторий книги открыт
     */
    public default boolean canSetCommitter() {
        return false;
    }

    /**
     * Записать имя и e-mail того, кто коммитит, в настройки репозитория книги (.git/config — в
     * клоне книги, не в её файлах: у каждого соавтора своё); пустое значение — убрать
     */
    public default void setCommitter(Author author) throws NLBVCSException {
    }

    /**
     * Адрес сервера книги (remote origin), например https://github.com/user/book.git; null — нет
     */
    public default String getRemoteUrl() {
        return null;
    }

    /**
     * Как решать пересёкшиеся правки при pull: NONE — не сливать (ошибка
     * {@link com.nlbhub.nlb.exception.NLBVCSConflictException}, книга как была), MINE / THEIRS —
     * пересёкшиеся части книги (файлы-поля) целиком из нашей версии или с сервера, остальное
     * сливается как обычно
     */
    public enum ConflictChoice {NONE, MINE, THEIRS}

    /**
     * Pull с выбором для пересёкшихся правок
     */
    public default void pull(String userName, String password, ProgressData progressData, ConflictChoice choice)
            throws NLBVCSException {
        pull(userName, password, progressData);
    }

    /**
     * Что последний pull восстановил, чтобы книга осталась целой (см. {@link RestoredElement});
     * пусто — ничего
     */
    public default java.util.List<RestoredElement> getRestoredByLastPull() {
        return java.util.Collections.emptyList();
    }

    /**
     * Записать адрес сервера книги (remote origin) и связать с ним текущую ветку (для pull);
     * null — убрать адрес
     */
    public default void setRemoteUrl(String url) throws NLBVCSException {
        throw new NLBVCSException("The editor is started without version control (novcs): publishing is not available");
    }

    /**
     * В репозитории книги есть хотя бы один коммит
     */
    public default boolean hasCommits() throws NLBVCSException {
        return false;
    }

    /**
     * Есть сохранённые, но не закоммиченные изменения (в индексе)
     */
    public default boolean hasUncommittedChanges() throws NLBVCSException {
        return false;
    }

    /**
     * Вопросы к автору при pull/push по ssh (пароль к ключу, незнакомый сервер)
     */
    public default void setPrompts(VCSPrompts prompts) {
    }

    /**
     * Скачать книгу с сервера в новую папку path вместе с её внешними модулями (подмодулями, на
     * любую глубину), без установки git. userName == null — без учётных данных (открытый
     * репозиторий по https); по ssh — ключи и вопросы, как при pull/push. Не удалось — папка,
     * если её создало клонирование, удаляется
     */
    public default void cloneRepo(String url, String path, String userName, String password, ProgressData progressData)
            throws NLBVCSException {
        throw new NLBVCSException("The editor is started without version control (novcs): cloning is not available");
    }
}
