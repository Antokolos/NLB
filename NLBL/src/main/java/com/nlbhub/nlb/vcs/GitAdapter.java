/**
 * @(#)GitAdapter.java
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
package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.api.ProgressData;
import com.nlbhub.nlb.exception.NLBVCSAuthenticationException;
import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;
import com.nlbhub.nlb.vcs.secrets.RemoteCredentials;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.eclipse.jgit.transport.sshd.agent.ConnectorFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * The GitAdapter class
 * Uses sample code from https://github.com/centic9/jgit-cookbook
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/9/14
 */
public class GitAdapter implements VCSAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitAdapter.class);
    private static final String BROKEN_REPOSITORY_MESSAGE = (
            "Your Git repository is broken.\nPlease either properly clone it (using 'git clone' command) or use 'runlight' command to run the program."
    );
    private Repository m_localRepo = null;
    private Git m_git = null;
    private Map<String, Status> m_statuses = new HashMap<>();
    /**
     * Статусы файлов, которые считаются в фоне после openRepo (git status обходит весь рабочий
     * каталог книги — секунды на большой книге); null — посчитаны. Любая операция адаптера сначала
     * дожидается их: так сохранение, начатое раньше, просто подождёт, а книга тем временем читается
     * и рисуется
     */
    private FutureTask<Void> m_statusesTask = null;
    private Author m_author;

    @Override
    public VCSAdapter newAdapter() {
        return new GitAdapter(m_author);
    }

    public GitAdapter(final Author author) {
        m_author = author;
    }

    @Override
    public void initRepo(String path) throws NLBVCSException {
        finishStatusesQuietly();
        try {
            m_localRepo = FileRepositoryBuilder.create(new File(path, ".git"));
            enableLongPaths(m_localRepo, false);
            m_localRepo.create();
            m_git = new Git(m_localRepo);
            initStatuses(false);
            // This commit solves the problem with the incorrect HEAD revision, when files
            // were added but no commits has been done yet
            commit("Initial commit");
        } catch (IOException e) {
            throw new NLBVCSException("Error while Git repository initialization", e);
        }
    }

    private static void enableLongPaths(
            final Repository repository,
            final boolean save
    ) throws IOException {
        StoredConfig config = repository.getConfig();
        config.setString("core", null, "longpaths", "true");
        if (save) {
            config.save();
        }
    }

    @Override
    public void openRepo(String path) throws NLBVCSException {
        finishStatusesQuietly();
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            // Каталог git не задаётся явно: JGit берёт <path>/.git сам, а если это файл
            // («gitdir: ...» — подмодуль git, например внешний модуль книги в modules/), читает
            // путь из него. Явный setGitDir(<path>/.git) считал файл каталогом, и книгу-подмодуль
            // нельзя было открыть
            m_localRepo = (
                    builder.setWorkTree(new File(path))
                            .readEnvironment() // scan environment GIT_* variables
                            .build()
            );
            enableLongPaths(m_localRepo, true);
            m_git = new Git(m_localRepo);
            // сломанный репозиторий (например, удалён каталог .git) — сообщение сразу, при открытии
            if (m_localRepo.exactRef(Constants.HEAD) == null) {
                throw new NLBVCSException(BROKEN_REPOSITORY_MESSAGE);
            }
            // внешний модуль-подмодуль после клонирования — в отсоединённом HEAD: на ветку с тем же
            // коммитом, иначе push из редактора невозможен
            GitSubmodules.attachDetachedHead(m_localRepo);
            startStatuses(path);
        } catch (IOException e) {
            throw new NLBVCSException("Error while Git repository opening", e);
        }
    }

    private void startStatuses(final String path) {
        m_statusesTask = new FutureTask<>(() -> {
            final long start = System.nanoTime();
            initStatuses(true);
            LOGGER.info("Git status of {} obtained in background in {} ms", path, (System.nanoTime() - start) / 1000000);
            return null;
        });
        Thread thread = new Thread(m_statusesTask, "git-status");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Репозиторий нельзя закрывать или подменять, пока его читает фоновый git status: дождаться
     * его, не глядя на результат — статусы прежнего репозитория больше не нужны
     */
    private void finishStatusesQuietly() {
        if (m_statusesTask != null) {
            try {
                m_statusesTask.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                // статусы прежнего репозитория не нужны
            }
            m_statusesTask = null;
        }
    }

    /**
     * Дождаться статусов файлов, которые считаются в фоне после openRepo; ошибка их получения —
     * здесь, при первой операции, которой они нужны
     */
    private void awaitStatuses() throws NLBVCSException {
        if (m_statusesTask == null) {
            return;
        }
        try {
            m_statusesTask.get();
            m_statusesTask = null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NLBVCSException("Interrupted while obtaining Git repository status", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NLBVCSException) {
                throw (NLBVCSException) e.getCause();
            }
            throw new NLBVCSException("Error while obtaining Git repository status", e.getCause());
        }
    }

    @Override
    public void closeAdapter() throws NLBVCSException {
        finishStatusesQuietly();
        if (m_git != null) {
            m_git.close();
            m_git = null;
        }
        if (m_localRepo != null) {
            m_localRepo.close();
            m_localRepo = null;
        }
        m_statuses.clear();
    }

    @Override
    public boolean getDirAddFlag() {
        return false;
    }

    @Override
    public boolean getAddModifiedFilesFlag() {
        return true;
    }

    private void initStatuses(boolean processExistentFiles) throws NLBVCSException {
        m_statuses.clear();
        try {
            if (processExistentFiles) {
                List<String> filePaths = listRepositoryContents();
                for (final String filePath : filePaths) {
                    // Initially mark all repository files as clean
                    // (i.e. under version control & without changes)
                    m_statuses.put(filePath, Status.Clean);
                }
            }
            org.eclipse.jgit.api.Status status = m_git.status().call();
            putItemsStatus(status.getAdded(), Status.Added);
            putItemsStatus(status.getChanged(), Status.Modified);   // ???
            putItemsStatus(status.getModified(), Status.Modified);
            putItemsStatus(status.getConflicting(), Status.Conflict);
            //System.out.println("ConflictingStageState: " + status.getConflictingStageState());
            putItemsStatus(status.getIgnoredNotInIndex(), Status.Ignored);
            putItemsStatus(status.getMissing(), Status.Missing);
            putItemsStatus(status.getRemoved(), Status.Removed);
            putItemsStatus(status.getUntracked(), Status.Unknown);
            putItemsStatus(status.getUntrackedFolders(), Status.Unknown);
        } catch (IOException | GitAPIException e) {
            throw new NLBVCSException("Error while obtaining Git repository status", e);
        }
    }

    private List<String> listRepositoryContents() throws IOException, NLBVCSException {
        List<String> result = new ArrayList<>();
        Ref head = m_localRepo.exactRef(Constants.HEAD);
        // head can be null if repo is broken (for example, .git directory was deleted)
        if (head == null) {
            throw new NLBVCSException(BROKEN_REPOSITORY_MESSAGE);
        }

        // head.getObjectId() can be null, for example, if repository was never committed.
        final ObjectId objectId = head.getObjectId();
        if (objectId != null) {
            // a RevWalk allows to walk over commits based on some filtering that is defined
            RevWalk walk = new RevWalk(m_localRepo);

            RevCommit commit = walk.parseCommit(objectId);
            RevTree tree = commit.getTree();

            // now use a TreeWalk to iterate over all files in the Tree recursively
            // you can set Filters to narrow down the results if needed
            TreeWalk treeWalk = new TreeWalk(m_localRepo);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(false);
            while (treeWalk.next()) {
                if (treeWalk.isSubtree()) {
                    //System.out.println("dir: " + treeWalk.getPathString());
                    treeWalk.enterSubtree();
                } else {
                    //System.out.println("file: " + treeWalk.getPathString());
                    result.add(treeWalk.getPathString());
                }
            }
        }

        return result;
    }

    private void putItemsStatus(final Set<String> paths, final Status status) {
        for (final String path : paths) {
            m_statuses.put(path, status);
        }
    }

    @Override
    public Status getStatus(String path) throws NLBVCSException {
        awaitStatuses();
        Status status = m_statuses.get(path);
        if (status == null) {
            return Status.VCS_Undefined;
        }
        return status;
    }

    @Override
    public void add(String path) throws NLBVCSException {
        try {
            Status prevStatus = getStatus(path);
            m_git.add().addFilepattern(path).call();
            if (prevStatus == Status.Unknown || prevStatus == Status.VCS_Undefined) {
                // If file previously was unknown to the Git, we should mark it as added
                m_statuses.put(path, Status.Added);
            }
        } catch (GitAPIException e) {
            throw new NLBVCSException("Error while adding to the Git repository", e);
        }
    }

    @Override
    public boolean remove(String path) throws NLBVCSException {
        awaitStatuses();
        try {
            // cached - true if files should only be removed from index, false if files should also
            // be deleted from the working directory
            m_git.rm().addFilepattern(path).setCached(true).call();
            m_statuses.put(path, Status.Removed);
            return false;
        } catch (GitAPIException e) {
            throw new NLBVCSException("Error while removing from the Git repository", e);
        }
    }

    @Override
    public void reset(String path) throws NLBVCSException {
        try {
            Status status = getStatus(path);
            m_git.reset().addPath(path).setRef(Constants.HEAD).call();
            switch (status) {
                case Added:
                    m_statuses.put(path, Status.Unknown);
                    break;
                case Removed:
                case Modified:
                case Missing:
                case Clean:
                    m_statuses.put(path, Status.Clean);
                    break;
                case Unknown:
                case Ignored:
                    throw new NLBVCSException(
                            "Cannot issue reset command for file with path = " + path
                                    + ", because its status was " + status
                    );
            }
        } catch (GitAPIException e) {
            throw new NLBVCSException("Error while adding to the Git repository", e);
        }
    }

    @Override
    public void commit(final String message) throws NLBVCSException {
        final Author committer = getCommitter();
        commit(message, committer != null ? committer : m_author);
    }

    @Override
    public void commit(final String message, final Author author) throws NLBVCSException {
        awaitStatuses();
        try {
            // новые коммиты внешних модулей (подмодулей) — тоже часть commit книги
            GitSubmodules.stageSubmodulePointers(m_git);
            m_git
                    .commit()
                    // редактор коммиты не подписывает; без явной пустой настройки JGit читает
                    // gpg.* из настроек git пользователя и падает на незнакомом ему формате
                    // подписи (gpg.format=ssh) — коммит из редактора был бы невозможен
                    .setSign(false)
                    .setGpgConfig(new GpgConfig(new Config()))
                    .setAll(false)
                    .setAmend(false)
                    .setAuthor(author.getName(), author.getEmail())
                    .setCommitter(author.getName(), author.getEmail())
                    .setMessage(message)
                    .call();
        } catch (GitAPIException | IOException e) {
            throw new NLBVCSException("Error while committing to the Git repository", e);
        }
    }

    @Override
    public Author getCommitter() {
        try {
            // настройки репозитория уже включают общие (~/.gitconfig) как запасные
            final Config config = (m_localRepo != null) ? m_localRepo.getConfig() : SystemReader.getInstance().getUserConfig();
            final String name = config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME);
            final String email = config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL);
            if (name == null || name.trim().isEmpty()) {
                return null;
            }
            return new Author(name.trim(), email == null ? "" : email.trim());
        } catch (IOException | ConfigInvalidException e) {
            LOGGER.warn("Cannot read Git user settings", e);
            return null;
        }
    }

    /**
     * Вопросы к автору при pull/push по ssh (пароль к ключу, незнакомый сервер)
     */
    private VCSPrompts m_prompts = null;

    @Override
    public void setPrompts(final VCSPrompts prompts) {
        m_prompts = prompts;
    }

    @Override
    public void cloneRepo(String url, String path, String userName, String password, ProgressData progressData)
            throws NLBVCSException {
        final File dir = new File(path);
        final boolean existed = dir.exists();
        final CredentialsProvider provider = RemoteCredentials.isSsh(url) || userName != null
                ? credentialsProvider(url, userName, password)
                : null;  // открытый репозиторий — без учётных данных; потребует их — спросим
        progressData.setNoteText("Cloning...");
        final ProgressMonitor monitor = new VCSProgressMonitor(progressData);
        try (Git git = Git.cloneRepository()
                .setURI(url.trim())
                .setDirectory(dir)
                .setCredentialsProvider(provider)
                .setProgressMonitor(monitor)
                .call()) {
            progressData.setNoteText("Cloning external modules...");
            GitSubmodules.cloneSubmodules(git, provider, monitor);
        } catch (GitAPIException | IOException | RuntimeException e) {
            // недоклонированную книгу — убрать, иначе повтор упрётся в непустую папку
            if (!existed) {
                try {
                    org.eclipse.jgit.util.FileUtils.delete(dir, org.eclipse.jgit.util.FileUtils.RECURSIVE
                            | org.eclipse.jgit.util.FileUtils.SKIP_MISSING | org.eclipse.jgit.util.FileUtils.RETRY);
                } catch (IOException cleanup) {
                    LOGGER.warn("Cannot remove the partially cloned book {}", dir, cleanup);
                }
            }
            throw transferError("cloning", e);
        }
    }

    /**
     * По ssh — ключи из ~/.ssh и ssh-agent, вопросы — к автору; по https — имя и пароль (токен)
     */
    private CredentialsProvider credentialsProvider(final String userName, final String password) {
        return credentialsProvider(getRemoteUrl(), userName, password);
    }

    private CredentialsProvider credentialsProvider(final String url, final String userName, final String password) {
        if (RemoteCredentials.isSsh(url)) {
            useSshHome();
            return new SshCredentialsProvider(m_prompts);
        }
        return new UsernamePasswordCredentialsProvider(userName == null ? "" : userName, password == null ? "" : password);
    }

    private static File s_sshHome = null;

    /**
     * Фабрика сессий ssh (Apache MINA SSHD): ключи и known_hosts — из ~/.ssh пользователя (на Windows
     * — %USERPROFILE%\.ssh), ssh-agent — если запущен (Pageant или агент OpenSSH в Windows,
     * SSH_AUTH_SOCK в Linux и macOS). Ставится для всего JGit: ей пользуются и подмодули
     */
    private static synchronized void useSshHome() {
        final File home = new File(System.getProperty("user.home"));
        if (home.equals(s_sshHome)) {
            return;
        }
        s_sshHome = home;
        SshSessionFactory.setInstance(
                new SshdSessionFactoryBuilder()
                        .setHomeDirectory(home)
                        .setSshDirectory(new File(home, ".ssh"))
                        .setConnectorFactory(ConnectorFactory.getDefault())
                        .build(new JGitKeyCache())
        );
    }

    @Override
    public String getRemoteUrl() {
        return m_localRepo == null ? null : m_localRepo.getConfig().getString("remote", "origin", "url");
    }

    @Override
    public void setRemoteUrl(final String url) throws NLBVCSException {
        if (m_localRepo == null) {
            throw new NLBVCSException("The book repository is not open: save the book first");
        }
        try {
            final StoredConfig config = m_localRepo.getConfig();
            final String branch = m_localRepo.getBranch();
            if (url == null) {
                config.unsetSection(ConfigConstants.CONFIG_REMOTE_SECTION, "origin");
                if (branch != null && "origin".equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                        ConfigConstants.CONFIG_KEY_REMOTE))) {
                    config.unset(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE);
                    config.unset(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE);
                }
            } else {
                // как `git remote add origin <url>` и `git push -u origin <ветка>`
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", ConfigConstants.CONFIG_KEY_URL, url.trim());
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, "origin", "fetch",
                        "+refs/heads/*:refs/remotes/origin/*");
                if (branch != null && !ObjectId.isId(branch)) {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_MERGE,
                            Constants.R_HEADS + branch);
                }
            }
            config.save();
        } catch (IOException e) {
            throw new NLBVCSException("Cannot save the address of the server in the Git settings of the book", e);
        }
    }

    @Override
    public boolean hasCommits() throws NLBVCSException {
        if (m_localRepo == null) {
            return false;
        }
        try {
            return m_localRepo.resolve(Constants.HEAD) != null;
        } catch (IOException e) {
            throw new NLBVCSException("Cannot read the Git repository of the book", e);
        }
    }

    @Override
    public boolean hasUncommittedChanges() throws NLBVCSException {
        if (m_git == null) {
            return false;
        }
        awaitStatuses();
        try {
            final org.eclipse.jgit.api.Status status = m_git.status().call();
            return !status.getAdded().isEmpty() || !status.getChanged().isEmpty() || !status.getRemoved().isEmpty();
        } catch (GitAPIException e) {
            throw new NLBVCSException("Cannot read the status of the Git repository of the book", e);
        }
    }

    /**
     * Ошибка pull/push: если сервер не принял учётные данные — {@link NLBVCSAuthenticationException}
     * (редактор забудет сохранённый токен и спросит снова)
     */
    static NLBVCSException transferError(final String what, final Exception e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            final String message = String.valueOf(t.getMessage()).toLowerCase();
            if (message.contains("not authorized") || message.contains("authentication is required")
                    || message.contains("auth fail") || message.contains("permission denied") || message.contains("cannot log in")
                    || message.contains("no more authentication methods")
                    || message.contains("not permitted") || message.contains("authentication not supported")
                    || message.contains("401") || message.contains("403")) {
                return new NLBVCSAuthenticationException(
                        "The server did not accept the user name and password or token (or the token has no write access): "
                                + e.getMessage(),
                        e
                );
            }
        }
        return new NLBVCSException("Error while " + what + " the Git repository: " + e.getMessage(), e);
    }

    @Override
    public boolean canSetCommitter() {
        return m_localRepo != null;
    }

    @Override
    public void setCommitter(final Author author) throws NLBVCSException {
        if (m_localRepo == null) {
            throw new NLBVCSException("The book repository is not open: save the book first");
        }
        final StoredConfig config = m_localRepo.getConfig();
        setOrUnset(config, ConfigConstants.CONFIG_KEY_NAME, author == null ? null : author.getName());
        setOrUnset(config, ConfigConstants.CONFIG_KEY_EMAIL, author == null ? null : author.getEmail());
        try {
            config.save();
        } catch (IOException e) {
            throw new NLBVCSException("Cannot save Git user settings", e);
        }
    }

    private static void setOrUnset(final StoredConfig config, final String key, final String value) {
        if (value == null || value.trim().isEmpty()) {
            config.unset(ConfigConstants.CONFIG_USER_SECTION, null, key);
        } else {
            config.setString(ConfigConstants.CONFIG_USER_SECTION, null, key, value.trim());
        }
    }

    @Override
    public void pull(String userName, String password, ProgressData progressData) throws NLBVCSException {
        awaitStatuses();
        CredentialsProvider provider = credentialsProvider(userName, password);
        progressData.setNoteText("Pulling...");
        ProgressMonitor monitor = new VCSProgressMonitor(progressData);
        try {
            m_git
                    .pull()
                    .setRemote("origin")
                    .setCredentialsProvider(provider)
                    .setProgressMonitor(monitor)
                    .call();
            // Analogous to `git clean -fd` command. It is needed to remove empty directories that can remain after
            // `git pull` command.
            m_git
                    .clean()
                    .setCleanDirectories(true)
                    .setDryRun(false)
                    .setIgnore(true)
                    .call();
            // внешние модули (подмодули) — на записанные в книге коммиты и на ветку
            List<String> skipped = GitSubmodules.updateSubmodules(m_git, provider, monitor);
            if (!skipped.isEmpty()) {
                throw new NLBVCSException(
                        "The book is pulled, but these modules are not updated because they have uncommitted changes: "
                                + String.join(", ", skipped) + ". Commit or revert them, then pull again."
                );
            }
        } catch (GitAPIException | IOException e) {
            throw transferError("pulling from", e);
        }
    }

    @Override
    public void push(String userName, String password, final ProgressData progressData) throws NLBVCSException {
        awaitStatuses();
        CredentialsProvider provider = credentialsProvider(userName, password);
        progressData.setNoteText("Pushing...");
        ProgressMonitor monitor = new VCSProgressMonitor(progressData);
        try {
            // сначала внешние модули (подмодули) с неотправленными коммитами: иначе книга сослалась
            // бы на коммит модуля, которого нет на сервере
            GitSubmodules.pushSubmodules(m_localRepo, provider, monitor);
            GitSubmodules.checkPushed("the book", m_git
                    .push()
                    .setRemote("origin")
                    .setCredentialsProvider(provider)
                    .setProgressMonitor(monitor)
                    .call());
        } catch (DetachedHeadException e) {
            throw new NLBVCSException(
                    "Cannot push: HEAD of the repository is detached (not on a branch). Run 'git checkout <branch>' in "
                            + m_localRepo.getWorkTree() + " and push again.",
                    e
            );
        } catch (GitAPIException | IOException e) {
            throw transferError("pushing to", e);
        }
    }
}
