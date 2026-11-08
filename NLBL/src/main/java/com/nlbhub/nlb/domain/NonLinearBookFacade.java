/**
 * @(#)NonLinearBookFacade.java
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
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.exception.*;
import com.nlbhub.nlb.util.BookFiles;
import com.nlbhub.nlb.util.FileManipulator;
import com.nlbhub.nlb.util.MultiLangString;
import com.nlbhub.nlb.util.ResourceManager;
import com.nlbhub.nlb.util.StringHelper;
import com.nlbhub.nlb.vcs.Author;
import com.nlbhub.nlb.vcs.VCSAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The NonLinearBookFacade class
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/15/14
 */
public class NonLinearBookFacade implements NLBObservable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NonLinearBookFacade.class);
    /**
     * Как сообщить автору, что правка не принята (вкладка внешнего модуля только для чтения);
     * редактор показывает сообщение, без интерфейса — только лог
     */
    private static volatile java.util.function.Consumer<String> s_refusedChangeNotifier = (
            reason -> LOGGER.warn("Change refused: {}", reason)
    );
    /**
     * Почему правки в этой книге не принимаются, или null. Вкладка внешнего модуля (подмодуль
     * в modules/) показывает копию модуля, сделанную при загрузке книги-родителя: правки в ней
     * не сохранялись бы и молча терялись (модуль правится, если открыть его как отдельную книгу)
     */
    private String m_readOnlyReason = null;
    private NonLinearBookImpl m_nlb;
    private Map<String, PageImpl> m_newPagesPool = new HashMap<>();
    private Map<String, ObjImpl> m_newObjsPool = new HashMap<>();
    private Map<String, LinkImpl> m_newLinksPool = new HashMap<>();
    /**
     * Main undo manager.
     */
    private UndoManager m_undoManager = new UndoManager();
    /**
     * Items' own undo managers.
     */
    private Map<String, UndoManager> m_undoManagersMap = new HashMap<>();
    private ObserverHandler m_observerHandler = new ObserverHandler();
    private VCSAdapter m_vcsAdapter;
    private Author m_author;
    private boolean m_rootFacade;
    private NonLinearBookFacade m_parentFacade;
    private List<NonLinearBookFacade> m_moduleFacades = new ArrayList<>();
    /**
     * Вкладка внешнего модуля, который правится прямо в книге: имя модуля (modules/<имя>), сама
     * книга модуля (её сохраняет save() книги, перенося в неё правки из копии m_nlb — см.
     * {@link NonLinearBookImpl#takeContentFrom}), адаптер VCS её репозитория (подмодуль git) и
     * пометка «сохранён после последнего commit» (тогда commit книги сначала коммитит модуль)
     */
    private String m_externalModuleName = null;
    private NonLinearBookImpl m_externalModuleBook = null;
    private VCSAdapter m_externalModuleVcs = null;
    private boolean m_externalModuleUncommitted = false;

    public NonLinearBookFacade(Author author, VCSAdapter vcsAdapter) {
        m_rootFacade = true;
        m_parentFacade = null;
        m_author = author;
        m_vcsAdapter = vcsAdapter;
        // m_nlb should be initialized later
    }

    private NonLinearBookFacade(
            NonLinearBookFacade parentFacade,
            Author author,
            VCSAdapter vcsAdapter,
            NonLinearBookImpl nlb
    ) {
        m_rootFacade = false;
        m_parentFacade = parentFacade;
        m_author = author;
        m_vcsAdapter = vcsAdapter;
        m_nlb = nlb;
    }

    public void createNewBook() {
        DummyNLB parentNLB = DummyNLB.singleton();
        m_nlb = new NonLinearBookImpl(parentNLB, new RootModulePage(parentNLB, Constants.MAIN_MODULE_NAME));
        notifyObservers();
    }

    public NonLinearBookFacade createModuleFacade(final String modulePageId) {
        PageImpl page = m_nlb.getPageImplById(modulePageId);
        NonLinearBookFacade facade = (
                new NonLinearBookFacade(this, m_author, m_vcsAdapter, page.getModuleImpl())
        );
        if (page.isModuleExternal()) {
            final String reason = externalModuleReadOnlyReason(page);
            if (reason == null) {
                // правится прямо в книге: копия на вкладке, сохраняется книга модуля
                facade.m_externalModuleName = page.getModuleName();
                facade.m_externalModuleBook = (NonLinearBookImpl) m_nlb.findExternalModule(page.getModuleName());
            } else {
                facade.m_readOnlyReason = reason;
            }
        } else {
            // внутренний модуль: правится вместе с книгой или с внешним модулем, в котором лежит
            facade.m_readOnlyReason = m_readOnlyReason;
        }
        m_moduleFacades.add(facade);
        notifyObservers();
        return facade;
    }

    /**
     * Почему внешний модуль страницы нельзя править на его вкладке (null — можно). Правится модуль
     * из modules/ самой книги (подключённый её страницей или страницей её внутреннего модуля).
     * Только для чтения: внешний модуль внутри внешнего модуля (он в modules/ того модуля — откройте
     * тот модуль отдельной книгой) и второе подключение того же модуля (две копии разошлись бы —
     * правится первая открытая)
     */
    private String externalModuleReadOnlyReason(final PageImpl page) {
        final String name = page.getModuleName();
        final String asBook = "To edit it, open modules/" + name + " of that module as a separate book.";
        if (m_readOnlyReason != null || insideExternalModule()) {
            return "Module '" + name + "' is an external module of another external module. This tab shows its copy, "
                    + "and changes here would not be saved. " + asBook;
        }
        final NonLinearBook module = m_nlb.findExternalModule(name);
        if (!(module instanceof NonLinearBookImpl) || module.getRootDir() == null) {
            return "Module '" + name + "' is an external module (modules/" + name + "), but its book was not found. "
                    + "This tab shows its copy, and changes here would not be saved.";
        }
        for (NonLinearBookFacade facade : getMainFacade().allModuleFacades()) {
            if (name.equals(facade.m_externalModuleName)) {
                return "Module '" + name + "' is used on several pages of the book, and it is edited in its first tab. "
                        + "This tab shows its copy, and changes here would not be saved.";
            }
        }
        return null;
    }

    /**
     * Эта вкладка — внешний модуль, правящийся в книге, или лежит внутри такого
     */
    private boolean insideExternalModule() {
        for (NonLinearBookFacade facade = this; facade != null; facade = facade.m_parentFacade) {
            if (facade.m_externalModuleName != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Все вкладки модулей под этой (рекурсивно)
     */
    private List<NonLinearBookFacade> allModuleFacades() {
        List<NonLinearBookFacade> result = new ArrayList<>();
        for (NonLinearBookFacade facade : m_moduleFacades) {
            result.add(facade);
            result.addAll(facade.allModuleFacades());
        }
        return result;
    }

    /**
     * Имя внешнего модуля, если вкладка правит его прямо в книге; иначе null
     */
    public String getEditedExternalModuleName() {
        return m_externalModuleName;
    }

    /**
     * Сохранить внешние модули, которые правились на своих вкладках: правки из копии — в книгу
     * модуля, она — в modules/<имя> через свой репозиторий (подмодуль git: файлы добавляются в его
     * индекс). Сама книга внешние модули при сохранении не трогает
     */
    private void saveEditedExternalModules() throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        for (NonLinearBookFacade facade : allModuleFacades()) {
            if (facade.m_externalModuleName == null || !facade.hasChanges()) {
                continue;
            }
            // правки — и в экземпляр модуля в памяти (из него делаются копии), но на диск пишется
            // книга модуля, прочитанная заново так же, как при открытии её отдельной книгой: у
            // экземпляра нет книги-родителя (свойства записались бы все, в том числе language),
            // и язык у него — язык игры книги-родителя (setPlaybackLanguage)
            facade.m_externalModuleBook.takeContentFrom(facade.m_nlb);
            try {
                final File moduleDir = facade.m_externalModuleBook.getRootDir();
                final DummyNLB root = DummyNLB.singleton();
                final NonLinearBookImpl module = new NonLinearBookImpl(root, new RootModulePage(root, Constants.MAIN_MODULE_NAME));
                module.load(moduleDir.getCanonicalPath(), new DummyProgressData());
                module.takeContentFrom(facade.m_nlb);
                if (facade.m_externalModuleVcs == null) {
                    facade.m_externalModuleVcs = m_vcsAdapter.newAdapter();
                    facade.m_externalModuleVcs.openRepo(moduleDir.getCanonicalPath());
                }
                final FileManipulator fileManipulator = new FileManipulator(facade.m_externalModuleVcs, moduleDir);
                final DummyProgressData progress = new DummyProgressData();
                module.save(fileManipulator, progress, new PartialProgressData(progress, 0, 100, 1));
                facade.m_externalModuleUncommitted = true;
                LOGGER.info("External module {} saved to {}", facade.m_externalModuleName, moduleDir);
            } catch (IOException e) {
                throw new NLBIOException("Error while obtaining canonical path of external module " + facade.m_externalModuleName, e);
            }
        }
    }

    public static void setRefusedChangeNotifier(java.util.function.Consumer<String> notifier) {
        s_refusedChangeNotifier = notifier;
    }

    /**
     * null — книгу можно править; иначе — почему нельзя (копия внешнего модуля)
     */
    public String getReadOnlyReason() {
        return m_readOnlyReason;
    }

    /**
     * Правка книги только для чтения не принимается: представление возвращается к модели
     * (перетащенный узел — на место), автору — сообщение (если tell; при перетаскивании — нет,
     * чтобы не показывать окно на каждое движение мыши)
     */
    private boolean refuseChange(final boolean tell) {
        if (m_readOnlyReason == null) {
            return false;
        }
        if (tell) {
            s_refusedChangeNotifier.accept(m_readOnlyReason);
        }
        // сначала все узлы (перетащенный — на место), потом все связи: связь рисуется по узлам
        for (Page page : m_nlb.getPages().values()) {
            page.notifyObservers();
        }
        for (Obj obj : m_nlb.getObjs().values()) {
            obj.notifyObservers();
        }
        updateAllViews();
        return true;
    }

    public NonLinearBookFacade getMainFacade() {
        NonLinearBookFacade result = this;
        while (result.getParentFacade() != null) {
            result = result.getParentFacade();
        }
        return result;
    }

    public NonLinearBookFacade getParentFacade() {
        return m_parentFacade;
    }

    public NonLinearBook getNlb() {
        return m_nlb;
    }

    /**
     * Версия редактора, которым книгу уже сохраняли, если она новее этого (#166): тогда автору
     * стоит обновить NLBB — старый редактор может потерять то, чего не знает. Иначе null
     */
    public String getNewerNlbVersion() {
        final String bookVersion = m_nlb.getNlbVersion();
        final String current = NLBVersion.current();
        if (NLBVersion.isKnown(bookVersion) && NLBVersion.isKnown(current)
                && NLBVersion.compare(bookVersion, current) > 0) {
            return bookVersion;
        }
        return null;
    }

    public void clear() throws NLBVCSException {
        m_nlb.clear();
        if (m_rootFacade) {
            m_vcsAdapter.closeAdapter();
        }
        if (m_externalModuleVcs != null) {
            m_externalModuleVcs.closeAdapter();
            m_externalModuleVcs = null;
        }
        clearUndosAndPools();
    }

    public void clearUndosAndPools() throws NLBVCSException {
        clearUndos();
        clearPools();
        for (NonLinearBookFacade facade : m_moduleFacades) {
            facade.clearUndosAndPools();
        }
        notifyObservers();
    }

    private void clearUndos() {
        m_undoManager.clear();
        for (Map.Entry<String, UndoManager> entry : m_undoManagersMap.entrySet()) {
            entry.getValue().clear();
        }
    }

    /**
     * Кто коммитит книгу: user.name и user.email из настроек её репозитория, иначе из общих
     * настроек git пользователя; null — не задано (редактор спросит перед commit)
     */
    public Author getCommitter() {
        return getMainFacade().m_vcsAdapter.getCommitter();
    }

    /**
     * Имя и e-mail того, кто коммитит, можно записать (репозиторий книги открыт)
     */
    public boolean canSetCommitter() {
        return getMainFacade().m_vcsAdapter.canSetCommitter();
    }

    /**
     * Записать имя и e-mail того, кто коммитит, в настройки репозитория книги (.git/config: в
     * клоне книги, не в её файлах — у каждого соавтора свои)
     */
    public void setCommitter(final Author author) throws NLBVCSException {
        getMainFacade().m_vcsAdapter.setCommitter(author);
    }

    public void commit(final String commitMessageText) throws NLBVCSException {
        // от имени того, кто задан в настройках репозитория книги (иначе — в общих настройках
        // git), — и книга, и её внешние модули (у подмодуля свои настройки, но коммит тот же)
        final Author committer = m_vcsAdapter.getCommitter() != null ? m_vcsAdapter.getCommitter() : m_author;
        // сначала внешние модули, сохранённые из книги, — в их репозитории (подмодули); новые
        // коммиты модулей commit книги затем добавит сам (GitSubmodules.stageSubmodulePointers)
        for (NonLinearBookFacade facade : allModuleFacades()) {
            if (facade.m_externalModuleUncommitted && facade.m_externalModuleVcs != null) {
                facade.m_externalModuleVcs.commit(commitMessageText, committer);
                facade.m_externalModuleUncommitted = false;
            }
        }
        m_vcsAdapter.commit(commitMessageText, committer);
        notifyObservers();
    }

    /**
     * Адрес сервера книги (remote origin); null — нет
     */
    public String getRemoteUrl() {
        return getMainFacade().m_vcsAdapter.getRemoteUrl();
    }

    /**
     * Скачать книгу с сервера в новую папку (с её внешними модулями) — без установки git; открыть
     * её потом — {@link #load}
     */
    public void cloneBook(final String url, final String path, final String userName, final String password,
                          final ProgressData progressData) throws NLBVCSException {
        getMainFacade().m_vcsAdapter.cloneRepo(url, path, userName, password, progressData);
    }

    /**
     * Вопросы к автору при pull/push по ssh (пароль к ключу, незнакомый сервер)
     */
    /**
     * Публикация: записать адрес сервера книги (null — убрать)
     */
    public void setRemoteUrl(final String url) throws NLBVCSException {
        getMainFacade().m_vcsAdapter.setRemoteUrl(url);
    }

    /**
     * В репозитории книги есть коммиты (иначе публиковать нечего)
     */
    public boolean hasCommits() throws NLBVCSException {
        return getMainFacade().m_vcsAdapter.hasCommits();
    }

    /**
     * Книга или её правленые внешние модули сохранены, но не закоммичены
     */
    public boolean hasUncommittedChanges() throws NLBVCSException {
        for (final NonLinearBookFacade facade : getMainFacade().allModuleFacades()) {
            if (facade.m_externalModuleUncommitted) {
                return true;
            }
        }
        return getMainFacade().m_vcsAdapter.hasUncommittedChanges();
    }

    public void setVcsPrompts(final com.nlbhub.nlb.vcs.VCSPrompts prompts) {
        getMainFacade().m_vcsAdapter.setPrompts(prompts);
    }

    public void pull(final String userName, final String password, final ProgressData progressData) throws NLBVCSException {
        m_vcsAdapter.pull(userName, password, progressData);
        notifyObservers();
    }

    /**
     * Новое на сервере (фоновая проверка); null — проверить нельзя
     */
    public com.nlbhub.nlb.vcs.ServerChanges checkServer(final String userName, final String password,
                                                        final com.nlbhub.nlb.vcs.VCSPrompts prompts) throws NLBVCSException {
        return getMainFacade().m_vcsAdapter.checkServer(userName, password, prompts);
    }

    /**
     * Что последний pull восстановил, чтобы книга осталась целой
     */
    public List<com.nlbhub.nlb.vcs.RestoredElement> getRestoredByLastPull() {
        return getMainFacade().m_vcsAdapter.getRestoredByLastPull();
    }

    /**
     * Pull с решением для каждого пересёкшегося поля
     */
    public void pull(final String userName, final String password, final ProgressData progressData,
                     final Map<String, com.nlbhub.nlb.vcs.ConflictResolution> resolutions) throws NLBVCSException {
        m_vcsAdapter.pull(userName, password, progressData, resolutions);
    }

    /**
     * Pull с выбором для пересёкшихся правок (см. {@link com.nlbhub.nlb.vcs.VCSAdapter.ConflictChoice})
     */
    public void pull(final String userName, final String password, final ProgressData progressData,
                     final com.nlbhub.nlb.vcs.VCSAdapter.ConflictChoice choice) throws NLBVCSException {
        m_vcsAdapter.pull(userName, password, progressData, choice);
    }

    public void push(final String userName, final String password, final ProgressData progressData) throws NLBVCSException {
        m_vcsAdapter.push(userName, password, progressData);
        notifyObservers();
    }

    public void exportToChoiceScript(File exportDir) throws NLBExportException {
        m_nlb.exportToChoiceScript(new File(exportDir, "startup.txt"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToQSPTextFile(File exportDir) throws NLBExportException {
        m_nlb.exportToQSPTextFile(new File(exportDir, "book.txt"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToURQTextFile(File exportDir) throws NLBExportException {
        m_nlb.exportToURQTextFile(new File(exportDir, "book.qst"));
    }

    public void exportToPDFFile(File exportFile) throws NLBExportException {
        m_nlb.exportToPDFFile(exportFile);
    }

    public void exportToTXTFile(File exportDir) throws NLBExportException {
        m_nlb.exportToTXTFile(new File(exportDir, "book.txt"));
    }

    public void exportToHTMLFile(File exportDir) throws NLBExportException {
        m_nlb.exportToHTMLFile(new File(exportDir, "index.html"));
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
    }

    public void exportToJSIQFile(File exportDir) throws NLBExportException {
        m_nlb.exportToJSIQFile(new File(exportDir, "example.xml"));
    }

    public void exportToSTEADFile(File exportDir) throws NLBExportException, NLBIOException {
        m_nlb.exportToSTEADFile(new File(exportDir, "main.lua"));
        exportMedia(exportDir);
        exportAdditionalMedia(exportDir);
    }

    public void exportToVNSTEADFile(File exportDir) throws NLBExportException, NLBIOException {
        m_nlb.exportToVNSTEADFile(new File(exportDir, "main.lua"));
        exportMedia(exportDir);
        exportAdditionalMedia(exportDir);
    }

    private void exportAdditionalMedia(File exportDir) throws NLBIOException {
        ResourceManager.exportBundledFiles(exportDir);
    }

    private void exportMedia(File exportDir) throws NLBExportException {
        File imagesExportDir = new File(exportDir, NonLinearBook.IMAGES_DIR_NAME);
        m_nlb.exportImages(true, imagesExportDir);
        File soundExportDir = new File(exportDir, NonLinearBook.SOUND_DIR_NAME);
        m_nlb.exportSound(true, soundExportDir);
    }

    public void exportToASMFile(File exportDir) throws NLBExportException {
        m_nlb.exportToASMFile(new File(exportDir, "book.sm"));
    }

    public void updateModifications(
            final ModifyingItem modifyingItem,
            final ModificationsTableModel modificationsTableModel
    ) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.UpdateModificationsCommand command = (
                m_nlb.createUpdateModificationsCommand(
                        modifyingItem,
                        modificationsTableModel
                )
        );
        getUndoManagerByItemId(
                modifyingItem.getId() + Constants.MODIFICATIONS_UNDO_ID_POSTFIX
        ).executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method does not have undo and immediately sets constraint id of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param constrId
     */
    public void setMediaFileConstrId(final MediaFile.Type mediaType, final String fileName, final String constrId) {
        if (refuseChange(true)) {
            return;
        }
        m_nlb.setMediaFileConstrId(mediaType, fileName, constrId);
    }

    /**
     * This method does not have undo and immediately sets redirect of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param redirect
     */
    public void setMediaFileRedirect(final MediaFile.Type mediaType, final String fileName, final String redirect) {
        if (refuseChange(true)) {
            return;
        }
        m_nlb.setMediaFileRedirect(mediaType, fileName, redirect);
    }

    /**
     * This method does not have undo and immediately sets flagged state of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param flag
     */
    public void setMediaFileFlag(final MediaFile.Type mediaType, final String fileName, final boolean flag) {
        if (refuseChange(true)) {
            return;
        }
        m_nlb.setMediaFileFlag(mediaType, fileName, flag);
    }

    /**
     * This method does not have undo and immediately sets export parameters preset of the media file with the specified
     * type and name.
     *
     * @param mediaType
     * @param fileName
     * @param preset
     */
    public void setMediaFileExportParametersPreset(final MediaFile.Type mediaType, final String fileName, final MediaExportParameters.Preset preset) {
        if (refuseChange(true)) {
            return;
        }
        m_nlb.setMediaFileExportParametersPreset(mediaType, fileName, preset);
    }

    public void updateBookProperties(
            final String license,
            final Theme theme,
            final String language,
            final String title,
            final String author,
            final String version,
            final String perfectGameAchievementName,
            final boolean fullAutowire,
            final boolean suppressMedia,
            final boolean suppressSound,
            final boolean forceDsc,
            final boolean choiceLinks,
            final boolean propagateToSubmodules
    ) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.UpdateBookPropertiesCommand command = (
                m_nlb.createUpdateBookPropertiesCommand(license, theme, language, title, author, version, perfectGameAchievementName, fullAutowire, suppressMedia, suppressSound, forceDsc, choiceLinks, propagateToSubmodules)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updatePage(
            final Page page,
            final String imageFileName,
            final boolean imageBackground,
            final boolean imageAnimated,
            final String soundFileName,
            final boolean soundSFX,
            final String pageVariableName,
            final String pageTimerVariableName,
            final String pageDefTagVariableValue,
            final MultiLangString pageText,
            final MultiLangString pageCaptionText,
            final Theme theme,
            final boolean useCaption,
            final boolean useMPL,
            final String moduleName,
            final boolean moduleExternal,
            final MultiLangString traverseText,
            final boolean autoTraverse,
            final boolean autoReturn,
            final MultiLangString returnText,
            final String returnPageId,
            final String moduleConsraintVariableName,
            final boolean autowire,
            final MultiLangString autowireInText,
            final MultiLangString autowireOutText,
            final boolean autoIn,
            final boolean needsAction,
            final boolean autoOut,
            final String autowireInConstraint,
            final String autowireOutConstraint,
            final boolean globalAutowire,
            final boolean noSave,
            final boolean autosFirst,
            final String authorNotes,
            final LinksTableModel linksTableModel
    ) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.UpdatePageCommand command = (
                m_nlb.createUpdatePageCommand(
                        page,
                        imageFileName,
                        imageBackground,
                        imageAnimated,
                        soundFileName,
                        soundSFX,
                        pageVariableName,
                        pageTimerVariableName,
                        pageDefTagVariableValue,
                        pageText,
                        pageCaptionText,
                        theme,
                        useCaption,
                        useMPL,
                        moduleName,
                        moduleExternal,
                        traverseText,
                        autoTraverse,
                        autoReturn,
                        returnText,
                        returnPageId,
                        moduleConsraintVariableName,
                        autowire,
                        autowireInText,
                        autowireOutText,
                        autoIn,
                        needsAction,
                        autoOut,
                        autowireInConstraint,
                        autowireOutConstraint,
                        globalAutowire,
                        noSave,
                        autosFirst,
                        authorNotes,
                        linksTableModel
                )
        );
        getUndoManagerByItemId(page.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateLink(
            final Link link,
            final String linkVariableName,
            final String linkConstraintValue,
            final MultiLangString linkText,
            final MultiLangString linkAltText,
            final boolean auto,
            final boolean once,
            final Link.Kind kind
    ) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.UpdateLinkCommand command = (
                m_nlb.createUpdateLinkCommand(
                        link,
                        linkVariableName,
                        linkConstraintValue,
                        linkText,
                        linkAltText,
                        auto,
                        once,
                        kind
                )
        );
        getUndoManagerByItemId(link.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateNode(final NodeItem nodeToUpdate) {
        nodeToUpdate.notifyObservers();
        // TODO: maybe not update links?
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeToUpdate);
        for (Link link : adjacentLinks) {
            link.notifyObservers();
        }
    }

    public void updateLink(final Link linkToUpdate) {
        linkToUpdate.notifyObservers();
    }

    public void updateObj(
            final Obj obj,
            final String objVariableName,
            final String objDefTagVariableValue,
            final String objConstraintValue,
            final String objCommonToName,
            final String objName,
            final String imageFileName,
            final String soundFileName,
            final boolean soundSFX,
            final boolean animatedImage,
            final boolean suppressDsc,
            final MultiLangString objDisp,
            final MultiLangString objText,
            final MultiLangString objActText,
            final MultiLangString objNouseText,
            final boolean objIsGraphical,
            final boolean objIsShowOnCursor,
            final boolean objIsPreserved,
            final boolean objIsLoadOnce,
            final boolean objIsCollapsable,
            final String offset,
            final Obj.MovementDirection movementDirection,
            final Obj.Effect effect,
            final int startFrame,
            final int maxFrame,
            final int preloadFrames,
            final int pauseFrames,
            final Obj.CoordsOrigin coordsOrigin,
            final boolean objIsClearUnderTooltip,
            final boolean objIsActOnKey,
            final boolean objIsCacheText,
            final boolean objIsLooped,
            final boolean objIsNoRedrawOnAct,
            final String objMorphOver,
            final String objMorphOut,
            final boolean objIsTakable,
            final boolean objIsCallback,
            final boolean imageInScene,
            final boolean imageInInventory
    ) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.UpdateObjCommand command = (
                m_nlb.createUpdateObjCommand(
                        obj,
                        objVariableName,
                        objDefTagVariableValue,
                        objConstraintValue,
                        objCommonToName,
                        objName,
                        imageFileName,
                        soundFileName,
                        soundSFX,
                        animatedImage,
                        suppressDsc,
                        objDisp,
                        objText,
                        objActText,
                        objNouseText,
                        objIsGraphical,
                        objIsShowOnCursor,
                        objIsPreserved,
                        objIsLoadOnce,
                        objIsCollapsable,
                        offset,
                        movementDirection,
                        effect,
                        startFrame,
                        maxFrame,
                        preloadFrames,
                        pauseFrames,
                        coordsOrigin,
                        objIsClearUnderTooltip,
                        objIsActOnKey,
                        objIsCacheText,
                        objIsLooped,
                        objIsNoRedrawOnAct,
                        objMorphOver,
                        objMorphOut,
                        objIsTakable,
                        objIsCallback,
                        imageInScene,
                        imageInInventory
                )
        );
        getUndoManagerByItemId(obj.getId()).executeAndStore(command);
        notifyObservers();
    }

    public void updateLinkCoords(
            final Link link,
            final float left,
            final float top
    ) {
        if (refuseChange(false)) {
            return;
        }
        final NLBCommand command = new UpdateLinkCoordsCommand(m_nlb, link, left, top);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updateLinkCoords(
            final Link link,
            final float height
    ) {
        if (refuseChange(false)) {
            return;
        }
        final NLBCommand command = new UpdateLinkCoordsCommand(m_nlb, link, height);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * If imageFileName is null, then imageFile.getName() is used.
     *
     * @param imageFile
     * @param imageFileName
     * @throws NLBFileManipulationException
     * @throws NLBIOException
     * @throws NLBConsistencyException
     * @throws NLBVCSException
     */
    public void addImageFile(
            final @NotNull File imageFile,
            final @Nullable String imageFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException, NLBVCSException {
        if (refuseChange(true)) {
            return;
        }
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.copyAndAddImageFile(fileManipulator, imageFile, imageFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    /**
     * If soundFileName is null, then soundFile.getName() is used.
     *
     * @param soundFile
     * @param soundFileName
     * @throws NLBFileManipulationException
     * @throws NLBIOException
     * @throws NLBConsistencyException
     * @throws NLBVCSException
     */
    public void addSoundFile(
            final @NotNull File soundFile,
            final @Nullable String soundFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException, NLBVCSException {
        if (refuseChange(true)) {
            return;
        }
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.copyAndAddSoundFile(fileManipulator, soundFile, soundFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void removeImageFile(
            final String imageFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        if (refuseChange(true)) {
            return;
        }
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.removeImageFile(fileManipulator, imageFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void removeSoundFile(
            final String soundFileName
    ) throws NLBFileManipulationException, NLBIOException, NLBConsistencyException {
        if (refuseChange(true)) {
            return;
        }
        File rootDir = m_nlb.getRootDir();
        if (rootDir != null) {
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            m_nlb.removeSoundFile(fileManipulator, soundFileName);
        } else {
            throw new NLBConsistencyException("NLB root dir is undefined");
        }
    }

    public void resizeNode(
            NodeItem nodeItem,
            NodeItem.Orientation orientation,
            double deltaX,
            double deltaY
    ) {
        if (refuseChange(false)) {
            return;
        }
        AbstractNodeItem node = m_nlb.getPageImplById(nodeItem.getId());
        if (node == null) {
            node = m_nlb.getObjImplById(nodeItem.getId());
        }
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeItem);
        AbstractNodeItem.ResizeNodeCommand command = (
                node.createResizeNodeCommand(orientation, deltaX, deltaY, adjacentLinks)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void updateNodeCoords(
            final NodeItem nodeItem,
            final Set<NodeItem> additionallyMovedItems,
            final float deltaX,
            final float deltaY
    ) {
        if (refuseChange(false)) {
            return;
        }
        final CommandChainCommand commandChain = new CommandChainCommand();
        // Каждый узел сдвигается один раз: выделенный контейнер перетаскиваемого объекта не
        // сдвигает его повторно (как и GraphEditor на экране), кольцо вложенности не зацикливает
        final Set<String> movedIds = new HashSet<>();
        updateNodeCoords(commandChain, nodeItem, deltaX, deltaY, movedIds);
        for (NodeItem additionalNodeItem : additionallyMovedItems) {
            updateNodeCoords(commandChain, additionalNodeItem, deltaX, deltaY, movedIds);
        }
        m_undoManager.executeAndStore(commandChain);
        notifyObservers();
    }

    private void updateNodeCoords(
            final CommandChainCommand commandChain,
            final NodeItem nodeItem,
            final float deltaX,
            final float deltaY,
            final Set<String> movedIds
    ) {
        if (!movedIds.add(nodeItem.getId())) {
            return;
        }
        final NLBCommand command = new UpdateNodeCoordsCommand(m_nlb, nodeItem, deltaX, deltaY);
        commandChain.addCommand(command);
        // Содержимое — вместе с контейнером, на всю глубину (вложенные объекты сдвигает
        // рекурсивный вызов; раньше их сдвигало ещё и отдельным обходом — дважды)
        for (String nodeId : nodeItem.getContainedObjIds()) {
            NodeItem node = m_nlb.getObjById(nodeId);
            if (node != null) {
                updateNodeCoords(commandChain, node, deltaX, deltaY, movedIds);
            }
        }
    }

    public void changeContainer(
            final String previousContainerId,
            final String newContainerId,
            final String objId
    ) {
        if (refuseChange(true)) {
            return;
        }
        AbstractNodeItem prevContainer = null;
        if (!StringHelper.isEmpty(previousContainerId)) {
            prevContainer = m_nlb.getPageImplById(previousContainerId);
            if (prevContainer == null) {
                prevContainer = m_nlb.getObjImplById(previousContainerId);
            }
        }
        AbstractNodeItem newContainer = null;
        if (!StringHelper.isEmpty(newContainerId)) {
            newContainer = m_nlb.getPageImplById(newContainerId);
            if (newContainer == null) {
                newContainer = m_nlb.getObjImplById(newContainerId);
            }
        }
        if (newContainer != null && m_nlb.isObjContainedIn(newContainerId, objId)) {
            // Объект нельзя положить в себя или в то, что лежит в нём: вложенность замкнулась
            // бы в кольцо, и всё, что обходит содержимое, уходило бы в бесконечность (#174)
            return;
        }
        if (
                (prevContainer == null && newContainer != null)           // from nowhere to something
                        || (prevContainer != null && newContainer == null)        // from something to nowhere
                        || (
                        prevContainer != null                                 // from something...
                                && !prevContainer.getId().equals(newContainer.getId()) // ...to something new
                )
                ) {
            ObjImpl obj = m_nlb.getObjImplById(objId);
            ChangeContainerCommand command = (
                    new ChangeContainerCommand(
                            prevContainer,
                            newContainer,
                            obj
                    )
            );
            m_undoManager.executeAndStore(command);
            notifyObservers();
        }
    }

    public void changeStartPoint(final String startPoint) {
        if (refuseChange(true)) {
            return;
        }
        NonLinearBookImpl.ChangeStartPointCommand command = (
                m_nlb.createChangeStartPointCommand(startPoint)
        );
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void cut(final Collection<String> pageIds, final Collection<String> objIds) {
        if (refuseChange(true)) {
            return;
        }
        CommandChainCommand command = new CommandChainCommand();
        command.addCommand(m_nlb.createCopyCommand(pageIds, objIds));
        command.addCommand(m_nlb.createDeleteCommand(pageIds, objIds));
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void copy(final Collection<String> pageIds, final Collection<String> objIds) {
        NonLinearBookImpl.CopyCommand command = m_nlb.createCopyCommand(pageIds, objIds);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void paste() {
        if (refuseChange(true)) {
            return;
        }
        final NonLinearBookImpl nlbToPaste = Clipboard.singleton().getNonLinearBook();
        if (nlbToPaste != null) {
            NonLinearBookImpl.PasteCommand command = m_nlb.createPasteCommand(nlbToPaste);
            m_undoManager.executeAndStore(command);
            notifyObservers();
        }
    }

    /**
     * This method is not participating in undo. It pre-creates page.
     */
    public Page createPage(final float left, final float top) {
        PageImpl page = new PageImpl(m_nlb, left, top);
        m_newPagesPool.put(page.getId(), page);
        return page;
    }

    public void addPage(final Page page) {
        if (refuseChange(true)) {
            return;
        }
        final PageImpl pageImpl = m_newPagesPool.get(page.getId());
        // m_newPagesPool.remove(page.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        NonLinearBookImpl.AddPageCommand command = m_nlb.createAddPageCommand(pageImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method is not participating in undo. It pre-creates obj.
     */
    public Obj createObj(final float left, final float top) {
        ObjImpl obj = new ObjImpl(m_nlb, left, top);
        m_newObjsPool.put(obj.getId(), obj);
        return obj;
    }

    public void addObj(final Obj obj) {
        if (refuseChange(true)) {
            return;
        }
        final ObjImpl objImpl = m_newObjsPool.get(obj.getId());
        // m_newObjsPool.remove(obj.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        NonLinearBookImpl.AddObjCommand command = m_nlb.createAddObjCommand(objImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    /**
     * This method is not participating in undo. It pre-creates link.
     */
    public Link createLink(final NodeItem item, final NodeItem target) {
        NodeItem origin = m_nlb.getPageById(item.getId());
        if (origin == null) {
            origin = m_nlb.getObjById(item.getId());
        }
        LinkImpl link = new LinkImpl(origin, target.getId());
        m_newLinksPool.put(link.getId(), link);
        return link;
    }

    public void addLink(final Link link) {
        if (refuseChange(true)) {
            return;
        }
        AbstractNodeItem origin = m_nlb.getPageImplById(link.getParent().getId());
        if (origin == null) {
            origin = m_nlb.getObjImplById(link.getParent().getId());
        }
        LinkImpl linkImpl = m_newLinksPool.get(link.getId());
        // m_newLinksPool.remove(link.getId()); -- do not remove here, instead of it this map
        // will be entirely cleared on save
        AbstractNodeItem.AddLinkCommand command = origin.createAddLinkCommand(linkImpl);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public boolean hasChanges() {
        if (canUndo() || canRedo()) {
            return true;
        }
        for (UndoManager undoManager : m_undoManagersMap.values()) {
            if (undoManager.canUndo() || undoManager.canRedo()) {
                return true;
            }
        }
        for (NonLinearBookFacade facade : m_moduleFacades) {
            if (facade.hasChanges()) {
                return true;
            }
        }
        return false;
    }

    public void save(
            final boolean create,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        saveNLB(create, progressData);
        saveEditedExternalModules();
        clearUndosAndPools();
    }

    private void saveNLB(
            final boolean create,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        try {
            final File rootDir = m_nlb.getRootDir();
            progressData.setProgressValue(5);
            progressData.setNoteText("Opening VCS repository...");
            // Here we actually duplicating code from NonLinearBookImpl.save(), because
            // we need to be sure that main root dir exists before trying to init or open VCS repo
            if (!rootDir.exists()) {
                if (!rootDir.mkdirs()) {
                    throw new NLBIOException("Cannot create NLB root directory");
                }
                m_vcsAdapter.initRepo(rootDir.getCanonicalPath());
            } else {
                if (create) {
                    m_vcsAdapter.initRepo(rootDir.getCanonicalPath());
                } else {
                    m_vcsAdapter.openRepo(rootDir.getCanonicalPath());
                }
            }
            progressData.setProgressValue(15);
            progressData.setNoteText("Saving Non-Linear Book...");
            FileManipulator fileManipulator = new FileManipulator(m_vcsAdapter, rootDir);
            int effectivePagesCount = m_nlb.getEffectivePagesCountForSave();
            int startProgress = 25; // it will become 25 when pages saving will be started, see the code in m_nlb.save()
            int maxProgress = 85; // it will become 85 when pages saving will be finished, see the code in m_nlb.save()
            Double itemsCountPerIncrement = (
                    Math.ceil(((double) effectivePagesCount) / ((double) (maxProgress - startProgress)))
            );
            // if pages count is less than (max - start), then max will never be reached, but we don't care,
            // because it will become max rightly after pages' saving.
            // All this mess is about to support progress handling in case when pages count is much more than 60
            PartialProgressData partialProgressData = (
                    new PartialProgressData(progressData, startProgress, maxProgress, itemsCountPerIncrement.intValue())
            );
            m_nlb.save(fileManipulator, progressData, partialProgressData);
        } catch (IOException e) {
            throw new NLBIOException("Error while obtaining canonical path during save");
        }
    }

    public boolean canUndo() {
        return m_undoManager.canUndo();
    }

    public void undo() {
        m_undoManager.undo();
        notifyObservers();
    }

    public boolean canUndo(final String id) {
        return getUndoManagerByItemId(id).canUndo();
    }

    public void undo(final String id) {
        getUndoManagerByItemId(id).undo();
        notifyObservers();
    }

    public boolean canRedo() {
        return m_undoManager.canRedo();
    }

    public void redo() {
        m_undoManager.redo();
        notifyObservers();
    }

    public boolean canRedo(final String id) {
        return getUndoManagerByItemId(id).canRedo();
    }

    public void redo(final String id) {
        getUndoManagerByItemId(id).redo();
        notifyObservers();
    }

    public void redoAll(final String id) {
        getUndoManagerByItemId(id).redoAll();
        notifyObservers();
    }

    private void clearPools() {
        m_newPagesPool.clear();
        m_newObjsPool.clear();
        m_newLinksPool.clear();
    }

    public void saveAs(
            final File nlbFolder,
            final ProgressData progressData
    ) throws NLBVCSException, NLBConsistencyException, NLBFileManipulationException, NLBIOException {
        final File oldRoot = m_nlb.getRootDir();
        checkSaveAsFolder(oldRoot, nlbFolder);
        m_nlb.setRootDir(nlbFolder);
        try {
            // то, чего нет в модели книги, — до сохранения (метаданные медиа пишутся, только если
            // папка images/sound уже есть), в VCS — после (сохранение создаёт репозиторий)
            final List<String> copied = copyFilesOutsideModel(oldRoot, nlbFolder);
            saveNLB(true, progressData);
            for (String path : copied) {
                m_vcsAdapter.add(path);
            }
        } catch (NLBVCSException | NLBConsistencyException | NLBFileManipulationException | NLBIOException e) {
            restoreRootAfterFailedSaveAs(oldRoot);
            throw e;
        } catch (RuntimeException e) {
            // раньше такое исключение (#163: IllegalStateException от JGit) пропадало в окне
            // редактора без следа, а книга оставалась привязанной к недосохранённой папке
            restoreRootAfterFailedSaveAs(oldRoot);
            throw new NLBIOException("Cannot save the book to " + nlbFolder + ": " + e, e);
        }
        clearUndosAndPools();
    }

    /**
     * Папка для «Сохранить как»: новая, пустая или репозиторий без книги (клон пустого репозитория
     * с сервера: .git, README, LICENSE, .gitignore, .gitattributes). Иначе файлы другой книги
     * смешались бы с этой (#163)
     */
    private static void checkSaveAsFolder(final File oldRoot, final File nlbFolder) throws NLBIOException {
        try {
            if (oldRoot != null && oldRoot.getCanonicalFile().equals(nlbFolder.getCanonicalFile())) {
                throw new NLBIOException(
                        "The book is already in " + nlbFolder + ". Use Save, or choose another folder for Save As"
                );
            }
        } catch (IOException e) {
            throw new NLBIOException("Cannot check the folder " + nlbFolder, e);
        }
        final String[] children = nlbFolder.list();
        if (children == null) {
            return;
        }
        final List<String> foreign = new ArrayList<>();
        for (String child : children) {
            if (!isRepositoryStarterFile(child)) {
                foreign.add(child);
            }
        }
        if (!foreign.isEmpty()) {
            Collections.sort(foreign);
            throw new NLBIOException(
                    "The folder " + nlbFolder + " is not empty (" + String.join(", ", foreign.subList(0, Math.min(5, foreign.size())))
                            + (foreign.size() > 5 ? ", ..." : "")
                            + "). Choose an empty folder or a clone of an empty repository"
            );
        }
    }

    private static boolean isRepositoryStarterFile(final String name) {
        final String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals(".git") || lower.equals(".gitignore") || lower.equals(".gitattributes")
                || lower.startsWith("readme") || lower.startsWith("license") || lower.startsWith("licence");
    }

    private void restoreRootAfterFailedSaveAs(final File oldRoot) {
        m_nlb.setRootDir(oldRoot);
        if (oldRoot != null && oldRoot.isDirectory()) {
            try {
                m_vcsAdapter.openRepo(oldRoot.getCanonicalPath());
            } catch (NLBVCSException | IOException | RuntimeException e) {
                LOGGER.warn("Cannot reopen the repository of " + oldRoot + " after failed Save As", e);
            }
        }
    }

    /**
     * Save As: файлы книги, которых нет в её модели и которые сохранение само не пишет, — из
     * прежней папки в новую (#175: копия книги оставалась без картинок и звуков):
     * <ul>
     *     <li>картинки и звуки книги (images/, sound/ — только файлы, которые есть в книге; их
     *     .preset/.flag/.constrid/.redirect запишет сохранение);</li>
     *     <li>внешние модули (modules/&lt;имя&gt;) — без служебных файлов git: в копии это обычные
     *     папки книги, не подмодули (подмодули копии — через Clone);</li>
     *     <li>тема игры __theme__ и .gitignore.</li>
     * </ul>
     *
     * @return пути скопированного относительно новой папки (через «/»), чтобы добавить в VCS
     */
    private List<String> copyFilesOutsideModel(final File oldRoot, final File newRoot) throws NLBIOException {
        final List<String> copied = new ArrayList<>();
        if (oldRoot == null || newRoot == null) {
            return copied;
        }
        try {
            if (oldRoot.getCanonicalFile().equals(newRoot.getCanonicalFile()) || !oldRoot.isDirectory()) {
                return copied;
            }
            for (final com.nlbhub.nlb.api.MediaFile image : m_nlb.getImageFiles()) {
                copyTree(new File(oldRoot, "images/" + image.getFileName()), newRoot, "images/" + image.getFileName(), copied);
            }
            for (final com.nlbhub.nlb.api.MediaFile sound : m_nlb.getSoundFiles()) {
                copyTree(new File(oldRoot, "sound/" + sound.getFileName()), newRoot, "sound/" + sound.getFileName(), copied);
            }
            for (final String extra : new String[]{"modules", "__theme__", ".gitignore"}) {
                copyTree(new File(oldRoot, extra), newRoot, extra, copied);
            }
        } catch (IOException e) {
            throw new NLBIOException("Cannot copy the book files to " + newRoot, e);
        }
        return copied;
    }

    private static void copyTree(final File source, final File newRoot, final String path, final List<String> copied)
            throws IOException {
        if (source.getName().equals(".git") || source.getName().equals(".gitmodules")) {
            return;  // служебное git (у подмодуля .git — файл со ссылкой на прежний репозиторий)
        }
        if (source.isDirectory()) {
            final File[] children = source.listFiles();
            if (children != null) {
                for (final File child : children) {
                    copyTree(child, newRoot, path + "/" + child.getName(), copied);
                }
            }
        } else if (source.isFile()) {
            final File target = new File(newRoot, path);
            target.getParentFile().mkdirs();
            java.nio.file.Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            copied.add(path);
        }
    }

    public void load(
            final String path,
            final ProgressData progressData
    ) throws NLBIOException, NLBConsistencyException, NLBVCSException {
        try {
            final File rootDir = new File(path);
            if (!rootDir.exists()) {
                // We need to be sure that main root dir exists before trying to open VCS repo
                throw new NLBIOException("Specified NLB root directory " + path + " does not exist");
            }
            progressData.setNoteText("Opening VCS repository...");
            progressData.setProgressValue(5);
            final long start = System.nanoTime();
            m_vcsAdapter.openRepo(rootDir.getCanonicalPath());
            final long vcsDone = System.nanoTime();
            progressData.setNoteText("Loading book contents...");
            progressData.setProgressValue(15);
            m_nlb.load(path, progressData);
            // замер открытия книги — в лог запуска (по нему решается, нужна ли ускоренная загрузка)
            final int[] counts = new int[2];
            countPagesAndObjs(m_nlb, counts);
            final String cache = BookFiles.lastLoadSummary();
            LOGGER.info(
                    "Book {} read in {} ms (VCS repository opened in {} ms): {} pages, {} objects, including internal modules; {}",
                    path, (System.nanoTime() - vcsDone) / 1000000, (vcsDone - start) / 1000000, counts[0], counts[1],
                    cache == null ? "book cache is off" : cache
            );
            progressData.setProgressValue(70);
            progressData.setNoteText("Prepare to drawing...");
            notifyObservers();
        } catch (IOException e) {
            throw new NLBIOException("Error while obtaining canonical path for path = " + path);
        }
    }

    private static void countPagesAndObjs(final NonLinearBook book, final int[] counts) {
        counts[0] += book.getPages().size();
        counts[1] += book.getObjs().size();
        for (Page page : book.getPages().values()) {
            if (!page.getModule().isEmpty()) {
                countPagesAndObjs(page.getModule(), counts);
            }
        }
    }

    public void deleteNode(final NodeItem nodeToDelete) {
        if (refuseChange(true)) {
            return;
        }
        final List<Link> adjacentLinks = m_nlb.getAssociatedLinks(nodeToDelete);
        PageImpl page = m_nlb.getPageImplById(nodeToDelete.getId());
        NonLinearBookImpl.DeleteNodeCommand command = null;
        if (page != null) {
            command = m_nlb.createDeletePageCommand(page, adjacentLinks);
        } else {
            command = m_nlb.createDeleteObjCommand(
                    m_nlb.getObjImplById(nodeToDelete.getId()),
                    adjacentLinks
            );
        }
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void deleteLink(final Link link) {
        if (refuseChange(true)) {
            return;
        }
        final IdentifiableItem parent = link.getParent();
        AbstractNodeItem nodeItem = m_nlb.getPageImplById(parent.getId());
        if (nodeItem == null) {
            nodeItem = m_nlb.getObjImplById(parent.getId());
        }
        AbstractNodeItem.DeleteLinkCommand command = nodeItem.createDeleteLinkCommand(link);
        m_undoManager.executeAndStore(command);
        notifyObservers();
    }

    public void invalidateAssociatedLinks(NodeItem nodeItem) {
        List<Link> associatedLinks = m_nlb.getAssociatedLinks(nodeItem);
        for (Link link : associatedLinks) {
            link.notifyObservers();
        }
    }

    public void updateAllViews() {
        for (Map.Entry<String, Page> pageEntry : m_nlb.getPages().entrySet()) {
            pageEntry.getValue().notifyObservers();
            List<Link> links = pageEntry.getValue().getLinks();
            for (Link link : links) {
                link.notifyObservers();
            }
        }
        for (Map.Entry<String, Obj> objEntry : m_nlb.getObjs().entrySet()) {
            objEntry.getValue().notifyObservers();
            List<Link> links = objEntry.getValue().getLinks();
            for (Link link : links) {
                link.notifyObservers();
            }
        }
    }

    private UndoManager getUndoManagerByItemId(final String id) {
        UndoManager undoManager = m_undoManagersMap.get(id);
        if (undoManager == null) {
            undoManager = new UndoManager();
            m_undoManagersMap.put(id, undoManager);
        }
        return undoManager;
    }

    @Override
    public String addObserver(NLBObserver observer) {
        return m_observerHandler.addObserver(observer);
    }

    @Override
    public void removeObserver(String observerId) {
        m_observerHandler.removeObserver(observerId);
    }

    @Override
    public void notifyObservers() {
        m_observerHandler.notifyObservers();
    }
}
