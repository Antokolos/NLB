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
import com.nlbhub.nlb.exception.NLBVCSConflictException;
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
        if (new File(path, ".git").exists()) {
            // Репозиторий уже есть — например, пустой клон с сервера, куда книгу сохраняют
            // «Сохранить как», чтобы опубликовать: открыть его (история и remote остаются).
            // JGit на create() бросал IllegalStateException «Repository already exists» (#163)
            openRepo(path);
            return;
        }
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
    public synchronized void commit(final String message, final Author author) throws NLBVCSException {
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
        return credentialsProvider(url, userName, password, m_prompts);
    }

    private CredentialsProvider credentialsProvider(final String url, final String userName, final String password,
                                                    final VCSPrompts prompts) {
        if (RemoteCredentials.isSsh(url)) {
            useSshHome();
            return new SshCredentialsProvider(prompts);
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
     * Фоновая проверка сервера: fetch (только refs/remotes/origin/* и объекты — книга, индекс и
     * рабочие файлы не меняются) и новые коммиты ветки сервера, которых нет в HEAD. Синхронно с
     * pull/push/commit этого адаптера (один репозиторий)
     */
    @Override
    public synchronized ServerChanges checkServer(final String userName, final String password, final VCSPrompts prompts)
            throws NLBVCSException {
        final String url = getRemoteUrl();
        if (m_localRepo == null || url == null) {
            return null;
        }
        final CredentialsProvider provider = RemoteCredentials.isSsh(url) || userName != null
                ? credentialsProvider(url, userName, password, prompts) : null;
        try {
            m_git.fetch().setRemote("origin").setCredentialsProvider(provider).call();
            final String branch = m_localRepo.getBranch();
            String upstream = m_localRepo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                    ConfigConstants.CONFIG_KEY_MERGE);
            if (upstream == null) {
                upstream = Constants.R_HEADS + branch;
            }
            final Ref theirs = m_localRepo.exactRef(Constants.R_REMOTES + "origin/" + Repository.shortenRefName(upstream));
            final ObjectId head = m_localRepo.resolve(Constants.HEAD);
            if (theirs == null) {
                return new ServerChanges(0, Collections.emptyList(), null, 0);
            }
            int count = 0;
            final java.util.LinkedHashSet<String> authors = new java.util.LinkedHashSet<>();
            String lastMessage = null;
            long lastTime = 0;
            try (RevWalk walk = new RevWalk(m_localRepo)) {
                walk.markStart(walk.parseCommit(theirs.getObjectId()));
                if (head != null) {
                    walk.markUninteresting(walk.parseCommit(head));
                }
                for (RevCommit commit : walk) {
                    // коммиты слияния — не правки, их не считать и не показывать
                    if (commit.getParentCount() < 2) {
                        if (count == 0) {
                            lastMessage = commit.getShortMessage();
                            lastTime = commit.getAuthorIdent().getWhenAsInstant().toEpochMilli();
                        }
                        count++;
                        authors.add(commit.getAuthorIdent().getName());
                    }
                }
            }
            return new ServerChanges(count, new ArrayList<>(authors), lastMessage, lastTime);
        } catch (GitAPIException | IOException e) {
            throw transferError("checking", e);
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
        pull(userName, password, progressData, ConflictChoice.NONE);
    }

    /**
     * Что восстановил последний pull
     */
    private List<RestoredElement> m_restored = Collections.emptyList();

    @Override
    public List<RestoredElement> getRestoredByLastPull() {
        return m_restored;
    }

    @Override
    public void pull(String userName, String password, ProgressData progressData, ConflictChoice choice)
            throws NLBVCSException {
        pull(userName, password, progressData, choice, null);
    }

    @Override
    public void pull(String userName, String password, ProgressData progressData,
                     java.util.Map<String, ConflictResolution> resolutions) throws NLBVCSException {
        pull(userName, password, progressData, ConflictChoice.NONE, resolutions);
    }

    private synchronized void pull(String userName, String password, ProgressData progressData, ConflictChoice choice,
                                   java.util.Map<String, ConflictResolution> resolutions) throws NLBVCSException {
        awaitStatuses();
        m_restored = Collections.emptyList();
        CredentialsProvider provider = credentialsProvider(userName, password);
        progressData.setNoteText("Pulling...");
        ProgressMonitor monitor = new VCSProgressMonitor(progressData);
        try {
            m_git
                    .fetch()
                    .setRemote("origin")
                    .setCredentialsProvider(provider)
                    .setProgressMonitor(monitor)
                    .call();
            mergeFetched(monitor, choice, resolutions);
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

    /**
     * Вторая половина pull — слияние скачанной ветки сервера с нашей. Не через PullCommand: тот
     * при пересечении правок оставлял в файлах книги маркеры конфликта (<<<<<<<) и незавершённое
     * слияние, а коммит слияния делал сам — с подписью по настройкам git пользователя (на
     * gpg.format=ssh JGit падает). Здесь: слияние без коммита; удалось — коммит от того, кто
     * коммитит книгу, без подписи; правки пересеклись — книга возвращается к состоянию до pull
     * и {@link NLBVCSConflictException} называет пересёкшиеся файлы
     */
    private void mergeFetched(final ProgressMonitor monitor, final ConflictChoice choice,
                              final java.util.Map<String, ConflictResolution> resolutions)
            throws GitAPIException, IOException, NLBVCSException {
        final String branch = m_localRepo.getBranch();
        String upstream = m_localRepo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_MERGE);
        if (upstream == null) {
            upstream = Constants.R_HEADS + branch;
        }
        final Ref theirs = m_localRepo.exactRef(Constants.R_REMOTES + "origin/" + Repository.shortenRefName(upstream));
        if (theirs == null) {
            return;  // на сервере этой ветки ещё нет — сливать нечего
        }
        final ObjectId before = m_localRepo.resolve(Constants.HEAD);
        final org.eclipse.jgit.api.MergeResult result;
        try {
            result = m_git
                    .merge()
                    .include(theirs)
                    .setCommit(false)
                    .setFastForward(org.eclipse.jgit.api.MergeCommand.FastForwardMode.FF)
                    .setProgressMonitor(monitor)
                    .call();
        } catch (org.eclipse.jgit.api.errors.CheckoutConflictException e) {
            // перемотка вперёд упёрлась в сохранённые, но не закоммиченные правки тех же файлов
            throw uncommittedConflict(e.getConflictingPaths(),
                    "The changes on the server touch the same parts of the book as your saved but not committed "
                            + "changes. Nothing is changed. Commit your changes first, then pull again.");
        }
        org.eclipse.jgit.api.MergeResult.MergeStatus status = result.getMergeStatus();
        // что решил автор: путь -> «kept the local version» и т. п. (для сообщения коммита)
        final java.util.Map<String, String> decided = new java.util.TreeMap<>();
        if (status == org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING) {
            // механическое (порядок, положение на схеме) — само; остальное — как выбрал автор
            final java.util.Set<String> remaining = autoResolve(result.getConflicts().keySet(), before, theirs.getObjectId());
            if (remaining.isEmpty()) {
                status = org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED_NOT_COMMITTED;
            } else if (choice != null && choice != ConflictChoice.NONE
                    || resolutions != null && resolutions.keySet().containsAll(remaining)) {
                applyResolutions(remaining, choice, resolutions, before, theirs.getObjectId(), decided);
                status = org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED_NOT_COMMITTED;
            } else {
                final List<String> paths = new ArrayList<>(remaining);
                final List<ConflictField> fields = conflictFields(remaining, before, theirs.getObjectId());
                abortMerge(before, theirs.getObjectId());
                throw new NLBVCSConflictException(
                        "Your changes and the changes on the server touch the same parts of the book, so they "
                                + "cannot be merged automatically. Nothing is changed: the book is as it was before pull.",
                        paths, false, fields
                );
            }
        }
        switch (status) {
            case MERGED_NOT_COMMITTED:
                // удалённое на одной стороне, но нужное правкам другой — вернуть целиком
                m_restored = restoreNeeded(before, theirs.getObjectId());
                if (!decided.isEmpty()) {
                    // оставленное по явному решению автора — не «восстановлено», о нём он знает
                    final List<RestoredElement> reported = new ArrayList<>();
                    for (RestoredElement element : m_restored) {
                        boolean chosen = false;
                        for (String path : decided.keySet()) {
                            chosen |= path.startsWith(element.getPath() + "/");
                        }
                        if (!chosen) {
                            reported.add(element);
                        }
                    }
                    m_restored = reported;
                }
                // списки ссылок и модификаций — ровно по папкам (иначе книга не откроется)
                reconcileOrders();
                final Author committer = getCommitter() != null ? getCommitter() : m_author;
                String message = m_localRepo.readMergeCommitMsg();
                // MERGE_MSG JGit дописывает строками-комментариями «# Conflicts:» — git их убирает, мы тоже
                message = message == null ? "" : message.replaceAll("(?m)^#.*(\\R|$)", "").trim();
                if (message.isEmpty()) {
                    message = "Merge changes from the server";
                }
                if (!m_restored.isEmpty()) {
                    final StringBuilder restored = new StringBuilder("\n\nRestored (deleted on one side, but needed "
                            + "by the changes of the other):");
                    for (RestoredElement element : m_restored) {
                        restored.append("\n").append(element);
                    }
                    message += restored;
                }
                if (!decided.isEmpty()) {
                    final StringBuilder overlapping = new StringBuilder("\n\nOverlapping changes:");
                    for (java.util.Map.Entry<String, String> entry : decided.entrySet()) {
                        overlapping.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
                    }
                    message += overlapping;
                }
                m_git
                        .commit()
                        .setSign(false)
                        .setGpgConfig(new GpgConfig(new Config()))
                        .setAuthor(committer.getName(), committer.getEmail())
                        .setCommitter(committer.getName(), committer.getEmail())
                        .setMessage(message)
                        .call();
                return;
            case FAILED:
                // настоящее (не перемотка) слияние JGit начинает только без незакоммиченных правок
                // в индексе — любых, не только пересекающихся; файлы он не трогал
                throw uncommittedConflict(result.getFailingPaths() == null
                                ? Collections.emptyList() : new ArrayList<>(result.getFailingPaths().keySet()),
                        "Your saved changes are not committed yet, and the changes on the server have to be merged "
                                + "with yours. Nothing is changed. Commit your changes first, then pull again.");
            default:
                if (!result.getMergeStatus().isSuccessful()) {
                    throw new NLBVCSException("Cannot merge the changes from the server: " + result.getMergeStatus());
                }
        }
    }

    /**
     * Решения автора: «своё» и «с сервера» — целиком (удаление — элементом), свой текст —
     * записать (поле, удалённое на одной стороне, сначала возвращается элементом с другой)
     */
    private void applyResolutions(final java.util.Set<String> remaining, final ConflictChoice choice,
                                  final java.util.Map<String, ConflictResolution> resolutions, final ObjectId ours,
                                  final ObjectId theirs, final java.util.Map<String, String> decided)
            throws IOException, GitAPIException {
        final java.util.Set<String> mine = new java.util.TreeSet<>();
        final java.util.Set<String> server = new java.util.TreeSet<>();
        final java.util.Map<String, String> texts = new java.util.TreeMap<>();
        for (String path : remaining) {
            final ConflictResolution resolution = choice == ConflictChoice.MINE ? ConflictResolution.mine()
                    : choice == ConflictChoice.THEIRS ? ConflictResolution.theirs() : resolutions.get(path);
            switch (resolution.getKind()) {
                case MINE:
                    mine.add(path);
                    decided.put(path, "kept the local version");
                    break;
                case THEIRS:
                    server.add(path);
                    decided.put(path, "took the version from the server");
                    break;
                default:
                    texts.put(path, resolution.getText() == null ? "" : resolution.getText());
                    decided.put(path, "merged by hand");
            }
        }
        if (!texts.isEmpty()) {
            // удалено на одной стороне — сначала вернуть элемент с той, где он есть
            final java.util.Map<String, String[]> stages = conflictStages(texts.keySet());
            for (String path : texts.keySet()) {
                (stages.get(path)[1] != null ? mine : server).add(path);
            }
        }
        if (!mine.isEmpty()) {
            takeWholeFiles(mine, 2, ours, theirs);
        }
        if (!server.isEmpty()) {
            takeWholeFiles(server, 3, ours, theirs);
        }
        for (java.util.Map.Entry<String, String> text : texts.entrySet()) {
            writeResolved(text.getKey(), text.getValue());
        }
    }

    /**
     * Версии пересёкшихся полей для окна сравнения: предок, своя, с сервера, и кто изменил поле
     * на сервере (последний коммит сервера с этим файлом после общего предка)
     */
    private List<ConflictField> conflictFields(final java.util.Set<String> paths, final ObjectId ours,
                                               final ObjectId theirs) throws IOException {
        final List<ConflictField> result = new ArrayList<>();
        final java.util.Map<String, byte[][]> stages = new java.util.HashMap<>();
        for (String path : paths) {
            stages.put(path, new byte[3][]);
        }
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.readDirCache();
        for (int i = 0; i < cache.getEntryCount(); i++) {
            final org.eclipse.jgit.dircache.DirCacheEntry entry = cache.getEntry(i);
            final byte[][] versions = stages.get(entry.getPathString());
            if (versions != null && entry.getStage() >= 1 && entry.getStage() <= 3
                    && !FileMode.GITLINK.equals(entry.getFileMode())) {
                versions[entry.getStage() - 1] = m_localRepo.open(entry.getObjectId()).getBytes();
            }
        }
        RevCommit base = null;
        try (RevWalk walk = new RevWalk(m_localRepo)) {
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(walk.parseCommit(ours));
            walk.markStart(walk.parseCommit(theirs));
            base = walk.next();
        }
        for (String path : new java.util.TreeSet<>(paths)) {
            final byte[][] versions = stages.get(path);
            boolean binary = false;
            for (byte[] version : versions) {
                binary |= version != null && isBinary(version);
            }
            String author = null;
            long time = 0;
            String message = null;
            try (RevWalk walk = new RevWalk(m_localRepo)) {
                walk.setTreeFilter(org.eclipse.jgit.treewalk.filter.AndTreeFilter.create(
                        org.eclipse.jgit.treewalk.filter.PathFilter.create(path),
                        org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF));
                walk.markStart(walk.parseCommit(theirs));
                if (base != null) {
                    walk.markUninteresting(walk.parseCommit(base));
                }
                final RevCommit commit = walk.next();
                if (commit != null) {
                    author = commit.getAuthorIdent().getName();
                    time = commit.getAuthorIdent().getWhenAsInstant().toEpochMilli();
                    message = commit.getShortMessage();
                }
            }
            result.add(new ConflictField(path, text(versions[0], binary), text(versions[1], binary),
                    text(versions[2], binary), versions[1] != null, versions[2] != null, binary,
                    author, time, message));
        }
        return result;
    }

    private static boolean isBinary(final byte[] content) {
        if (content.length > 256 * 1024) {
            return true;
        }
        for (byte b : content) {
            if (b == 0) {
                return true;
            }
        }
        return false;
    }

    private static String text(final byte[] content, final boolean binary) {
        return content == null || binary ? null : new String(content, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Решить механические пересечения ({@link BookMergeRules}): списки id — объединением,
     * положение на схеме — наше; удалено на одной стороне (элемент удалён), а на другой только
     * подвинуто — удаление, если других пересечений у этого элемента нет (иначе — вместе с ними,
     * как выберет автор)
     *
     * @return пересечения, которые решает автор
     */
    private java.util.Set<String> autoResolve(final java.util.Set<String> conflicts, final ObjectId ours,
                                              final ObjectId theirs) throws IOException, GitAPIException {
        final java.util.Map<String, String[]> stages = conflictStages(conflicts);
        final java.util.Set<String> remaining = new java.util.TreeSet<>();
        for (String path : conflicts) {
            if (BookMergeRules.isCoords(path)) {
                final String node = path.substring(0, path.lastIndexOf("/coords/"));
                boolean otherConflicts = false;
                for (String other : conflicts) {
                    if (other.startsWith(node + "/") && !BookMergeRules.isCoords(other)) {
                        otherConflicts = true;
                        break;
                    }
                }
                final String[] versions = stages.get(path);
                if (versions[1] != null && versions[2] != null) {
                    writeResolved(path, versions[1]);
                } else if (!otherConflicts) {
                    // элемент удалён на одной стороне, на другой только подвинут — удалить целиком
                    takeWholeFiles(Collections.singleton(path), versions[1] == null ? 2 : 3, ours, theirs);
                } else {
                    remaining.add(path);
                }
            } else if (BookMergeRules.isList(path)) {
                final String[] versions = stages.get(path);
                final String merged = BookMergeRules.mergeList(versions[0], versions[1], versions[2]);
                writeResolved(path, merged.isEmpty() ? null : merged);
            } else {
                remaining.add(path);
            }
        }
        return remaining;
    }

    /**
     * Содержимое пересёкшихся файлов по стадиям индекса: [0] — общий предок, [1] — наше, [2] — с
     * сервера; null — нет на этой стороне
     */
    private java.util.Map<String, String[]> conflictStages(final java.util.Set<String> paths) throws IOException {
        final java.util.Map<String, String[]> result = new java.util.HashMap<>();
        for (String path : paths) {
            result.put(path, new String[3]);
        }
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.readDirCache();
        for (int i = 0; i < cache.getEntryCount(); i++) {
            final org.eclipse.jgit.dircache.DirCacheEntry entry = cache.getEntry(i);
            final String[] versions = result.get(entry.getPathString());
            if (versions != null && entry.getStage() >= 1 && entry.getStage() <= 3) {
                versions[entry.getStage() - 1] = new String(
                        m_localRepo.open(entry.getObjectId()).getBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return result;
    }

    /**
     * Записать решённый файл и отметить решённым в индексе; null — удалить
     */
    private void writeResolved(final String path, final String content) throws IOException, GitAPIException {
        final File file = new File(m_localRepo.getWorkTree(), path);
        if (content == null) {
            if (file.isFile() && !file.delete()) {
                throw new IOException("Cannot remove " + file);
            }
            m_git.rm().setCached(true).addFilepattern(path).call();
        } else {
            file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            m_git.add().addFilepattern(path).call();
        }
    }

    private static final java.util.regex.Pattern UUID = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Целостность после слияния: элемент книги (страница, объект, ссылка, модификация,
     * переменная, страница внутреннего модуля), который на одной стороне удалили, а другая
     * сторона в него что-то добавила (остался обрывок) или на него сослалась (цель ссылки, объект
     * в содержимом, переменная модификации, стартовая страница — любой файл с его id, которого нет
     * в версии удалившей стороны), — вернуть целиком из версии другой стороны. Удалить снова —
     * одно действие автора, а потерянную работу соавтора не вернуть. Повторяется, пока
     * восстановленное само не перестанет ссылаться на удалённое
     */
    private List<RestoredElement> restoreNeeded(final ObjectId ours, final ObjectId theirs) throws IOException, GitAPIException {
        final java.util.Map<String, ObjectId> base;
        final java.util.Map<String, ObjectId> ourFiles;
        final java.util.Map<String, ObjectId> theirFiles;
        final RevTree ourTree;
        final RevTree theirTree;
        try (RevWalk walk = new RevWalk(m_localRepo)) {
            final RevCommit ourCommit = walk.parseCommit(ours);
            final RevCommit theirCommit = walk.parseCommit(theirs);
            ourTree = ourCommit.getTree();
            theirTree = theirCommit.getTree();
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(ourCommit);
            walk.markStart(theirCommit);
            final RevCommit baseCommit = walk.next();
            if (baseCommit == null) {
                return Collections.emptyList();
            }
            base = files(baseCommit.getTree());
            ourFiles = files(ourTree);
            theirFiles = files(theirTree);
        }
        final java.util.Set<String> baseDirs = elementDirsOf(base.keySet());
        final java.util.Set<String> ourDirs = elementDirsOf(ourFiles.keySet());
        final java.util.Set<String> theirDirs = elementDirsOf(theirFiles.keySet());
        final java.util.Map<String, RestoredElement> restored = new java.util.TreeMap<>();
        // первый проход — то, что нужно правкам другой стороны (о нём сообщить автору); дальше —
        // то, что нужно самому восстановленному (переменные его модификаций и т. п.)
        final java.util.Set<String> reported = new java.util.TreeSet<>();
        boolean first = true;
        boolean changed = true;
        while (changed) {
            changed = false;
            final java.util.Map<String, ObjectId> merged = indexFiles();
            final java.util.Set<String> mergedDirs = elementDirsOf(merged.keySet());
            // удалённое одной стороной (было у предка, есть у другой стороны) — по id
            final java.util.Map<String, String> deletedById = new java.util.HashMap<>();
            for (String dir : baseDirs) {
                final boolean here = ourDirs.contains(dir);
                final boolean there = theirDirs.contains(dir);
                if (here != there) {
                    deletedById.put(dir.substring(dir.lastIndexOf('/') + 1), dir);
                }
            }
            final java.util.Set<String> needed = new java.util.TreeSet<>();
            // обрывки: удалено одной стороной, а в слиянии что-то от элемента осталось
            for (String dir : deletedById.values()) {
                if (mergedDirs.contains(dir)) {
                    needed.add(dir);
                }
            }
            // ссылки на удалённое, которых нет в версии удалившей стороны
            for (java.util.Map.Entry<String, ObjectId> file : merged.entrySet()) {
                final String path = file.getKey();
                // поля книги (не медиа и не внешние модули), принесённые одной из сторон
                if (!(path.startsWith("pages/") || path.startsWith("objs/") || path.startsWith("vars/")
                        || path.indexOf('/') < 0)
                        || file.getValue().equals(ourFiles.get(path)) && file.getValue().equals(theirFiles.get(path))
                        || !isSmallText(file.getValue())) {
                    continue;
                }
                final String content = new String(m_localRepo.open(file.getValue()).getBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
                final java.util.regex.Matcher matcher = UUID.matcher(content);
                while (matcher.find()) {
                    final String dir = deletedById.get(matcher.group());
                    if (dir == null || mergedDirs.contains(dir) || under(path, Collections.singleton(dir))) {
                        continue;
                    }
                    final java.util.Map<String, ObjectId> deleting = ourDirs.contains(dir) ? theirFiles : ourFiles;
                    final ObjectId deletingVersion = deleting.get(path);
                    if (deletingVersion == null || !deletingVersion.equals(file.getValue())) {
                        needed.add(dir);
                    }
                }
            }
            for (String dir : needed) {
                if (restored.containsKey(dir)) {
                    continue;
                }
                final boolean deletedLocally = !ourDirs.contains(dir);
                restoreElement(dir, deletedLocally ? theirTree : ourTree);
                restored.put(dir, new RestoredElement(dir, deletedLocally));
                if (first) {
                    reported.add(dir);
                }
                LOGGER.info("Merge: restored {}{}", restored.get(dir), first ? "" : " (needed by restored elements)");
                changed = true;
            }
            first = false;
        }
        if (!restored.isEmpty()) {
            addRestoredToContainers(restored.keySet());
        }
        // вложенное во восстановленное целиком (ссылка восстановленной страницы) — не перечислять
        final List<RestoredElement> result = new ArrayList<>();
        for (String dir : reported) {
            final java.util.Set<String> others = new java.util.HashSet<>(reported);
            others.remove(dir);
            if (!under(dir, others)) {
                result.add(restored.get(dir));
            }
        }
        return result;
    }

    /**
     * Восстановленный объект — в содержимое (content) его контейнера, если его там нет
     */
    private void addRestoredToContainers(final java.util.Set<String> dirs) throws IOException, GitAPIException {
        final java.util.Map<String, ObjectId> merged = indexFiles();
        for (String dir : dirs) {
            final String[] parts = dir.split("/");
            if (parts.length < 2 || !"objs".equals(parts[parts.length - 2])) {
                continue;
            }
            final ObjectId containerFile = merged.get(dir + "/containerid");
            if (containerFile == null) {
                continue;
            }
            final String container = new String(m_localRepo.open(containerFile).getBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            final String prefix = dir.substring(0, dir.length() - ("objs/" + parts[parts.length - 1]).length());
            for (String kind : new String[]{"pages/", "objs/"}) {
                final String containerDir = prefix + kind + container;
                if (elementDirsOf(merged.keySet()).contains(containerDir)) {
                    final String contentPath = containerDir + "/content";
                    final ObjectId content = merged.get(contentPath);
                    final List<String> ids = BookMergeRules.split(content == null ? null : new String(
                            m_localRepo.open(content).getBytes(), java.nio.charset.StandardCharsets.UTF_8));
                    final String id = parts[parts.length - 1];
                    if (!ids.contains(id)) {
                        ids.add(id);
                        writeResolved(contentPath, BookMergeRules.join(ids));
                    }
                }
            }
        }
    }

    /**
     * Элемент целиком из версии tree — в индекс и рабочую копию: недостающие файлы; имеющиеся
     * (решение автора, добавленное другой стороной) не трогаются
     */
    private void restoreElement(final String dir, final RevTree tree) throws IOException, GitAPIException {
        final List<String> files = new ArrayList<>();
        final java.util.Set<String> present = indexFiles().keySet();
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.lockDirCache();
        try {
            final org.eclipse.jgit.dircache.DirCacheBuilder builder = cache.builder();
            for (int i = 0; i < cache.getEntryCount(); i++) {
                builder.add(cache.getEntry(i));
            }
            try (TreeWalk walk = new TreeWalk(m_localRepo)) {
                walk.addTree(tree);
                walk.setRecursive(true);
                walk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(dir));
                while (walk.next()) {
                    if (present.contains(walk.getPathString())) {
                        continue;
                    }
                    final org.eclipse.jgit.dircache.DirCacheEntry entry =
                            new org.eclipse.jgit.dircache.DirCacheEntry(walk.getPathString());
                    entry.setFileMode(walk.getFileMode(0));
                    entry.setObjectId(walk.getObjectId(0));
                    builder.add(entry);
                    files.add(walk.getPathString());
                }
            }
            builder.commit();
        } finally {
            cache.unlock();
        }
        if (!files.isEmpty()) {
            m_git.checkout().addPaths(files).call();
        }
    }

    private java.util.Map<String, ObjectId> files(final RevTree tree) throws IOException {
        final java.util.Map<String, ObjectId> result = new java.util.HashMap<>();
        try (TreeWalk walk = new TreeWalk(m_localRepo)) {
            walk.addTree(tree);
            walk.setRecursive(true);
            while (walk.next()) {
                result.put(walk.getPathString(), walk.getObjectId(0));
            }
        }
        return result;
    }

    private java.util.Map<String, ObjectId> indexFiles() throws IOException {
        final java.util.Map<String, ObjectId> result = new java.util.HashMap<>();
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.readDirCache();
        for (int i = 0; i < cache.getEntryCount(); i++) {
            final org.eclipse.jgit.dircache.DirCacheEntry entry = cache.getEntry(i);
            if (entry.getStage() == 0) {
                result.put(entry.getPathString(), entry.getObjectId());
            }
        }
        return result;
    }

    private static java.util.Set<String> elementDirsOf(final java.util.Collection<String> paths) {
        final java.util.Set<String> result = new java.util.HashSet<>();
        for (String path : paths) {
            if (!path.startsWith("modules/")) {
                result.addAll(elementDirs(path));
            }
        }
        return result;
    }

    /**
     * Небольшой файл поля (не картинка и не звук): в нём могут быть id других элементов
     */
    private boolean isSmallText(final ObjectId blob) throws IOException {
        return m_localRepo.getObjectDatabase().open(blob).getSize() <= 64 * 1024;
    }

    /**
     * Сверить lnkorder и modorder каждого элемента с папками его ссылок и модификаций (см.
     * {@link BookMergeRules#reconcile}); расхождения исправить в рабочей копии и индексе
     */
    private void reconcileOrders() throws IOException, GitAPIException {
        // элемент -> вид (links / modifications) -> id по папкам
        final java.util.Map<String, java.util.Map<String, java.util.Set<String>>> items = new java.util.TreeMap<>();
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.readDirCache();
        for (int i = 0; i < cache.getEntryCount(); i++) {
            final String path = cache.getEntry(i).getPathString();
            final String[] parts = path.split("/");
            for (int k = 1; k + 2 < parts.length; k++) {
                if ("links".equals(parts[k]) || "modifications".equals(parts[k])) {
                    final String item = String.join("/", Arrays.copyOfRange(parts, 0, k));
                    items.computeIfAbsent(item, x -> new java.util.HashMap<>())
                            .computeIfAbsent(parts[k], x -> new java.util.HashSet<>()).add(parts[k + 1]);
                }
            }
            if (path.endsWith("/lnkorder") || path.endsWith("/modorder")) {
                final String item = path.substring(0, path.lastIndexOf('/'));
                items.computeIfAbsent(item, x -> new java.util.HashMap<>())
                        .computeIfAbsent(path.endsWith("/lnkorder") ? "links" : "modifications", x -> new java.util.HashSet<>());
            }
        }
        for (java.util.Map.Entry<String, java.util.Map<String, java.util.Set<String>>> item : items.entrySet()) {
            for (java.util.Map.Entry<String, java.util.Set<String>> kind : item.getValue().entrySet()) {
                final String orderPath = item.getKey() + "/" + ("links".equals(kind.getKey()) ? "lnkorder" : "modorder");
                final File orderFile = new File(m_localRepo.getWorkTree(), orderPath);
                final String current = orderFile.isFile()
                        ? new String(java.nio.file.Files.readAllBytes(orderFile.toPath()), java.nio.charset.StandardCharsets.UTF_8)
                        : "";
                final List<String> order = BookMergeRules.split(current);
                final List<String> fixed = BookMergeRules.reconcile(order, kind.getValue());
                if (!fixed.equals(order)) {
                    LOGGER.info("Merge: {} corrected to match the existing {}", orderPath, kind.getKey());
                    writeResolved(orderPath, fixed.isEmpty() ? null : BookMergeRules.join(fixed));
                }
            }
        }
    }

    /**
     * Пересёкшиеся файлы — целиком с одной стороны слияния (stage 2 — наша версия, 3 — с
     * сервера). Удалено на одной стороне, изменено на другой — решается для элемента книги
     * целиком (страница, объект, ссылка, модификация, переменная): git сообщает о пересечении
     * только по изменённому файлу, а остальные файлы удалённого элемента убирает молча, так что
     * «оставить» — вернуть весь элемент с выбранной стороны, «удалить» — убрать его весь (и то,
     * что в нём успели добавить). Иначе осталась бы, например, ссылка из одного текста — без
     * цели и координат, и книга не открылась бы
     *
     * @param ours   наш коммит (HEAD до слияния)
     * @param theirs коммит сервера
     */
    private void takeWholeFiles(final java.util.Set<String> paths, final int stage, final ObjectId ours,
                                final ObjectId theirs) throws IOException, GitAPIException {
        final java.util.Map<String, String[]> stages = conflictStages(paths);
        final java.util.Set<String> simple = new java.util.TreeSet<>();
        final java.util.Set<String> removed = new java.util.TreeSet<>();
        final java.util.Map<String, RevTree> restored = new java.util.TreeMap<>();
        try (RevWalk walk = new RevWalk(m_localRepo)) {
            final RevTree ourTree = walk.parseCommit(ours).getTree();
            final RevTree theirTree = walk.parseCommit(theirs).getTree();
            for (String path : paths) {
                final String[] versions = stages.get(path);
                final boolean chosenHas = versions[stage - 1] != null;
                final boolean otherHas = versions[stage == 2 ? 2 : 1] != null;
                if (chosenHas && otherHas) {
                    simple.add(path);
                    continue;
                }
                final RevTree deleting = chosenHas ? (stage == 2 ? theirTree : ourTree) : (stage == 2 ? ourTree : theirTree);
                final String element = deletedElement(path, deleting);
                if (chosenHas) {
                    restored.put(element, stage == 2 ? ourTree : theirTree);
                } else {
                    removed.add(element);
                }
            }
        }
        final List<String> files = new ArrayList<>();
        final org.eclipse.jgit.dircache.DirCache cache = m_localRepo.lockDirCache();
        try {
            final org.eclipse.jgit.dircache.DirCacheBuilder builder = cache.builder();
            for (int i = 0; i < cache.getEntryCount(); i++) {
                final org.eclipse.jgit.dircache.DirCacheEntry entry = cache.getEntry(i);
                final String path = entry.getPathString();
                if (under(path, removed) || under(path, restored.keySet())) {
                    continue;
                }
                if (!simple.contains(path) && !paths.contains(path)) {
                    builder.add(entry);
                } else if (simple.contains(path) && entry.getStage() == stage) {
                    final org.eclipse.jgit.dircache.DirCacheEntry chosen = new org.eclipse.jgit.dircache.DirCacheEntry(path);
                    chosen.setFileMode(entry.getFileMode());
                    chosen.setObjectId(entry.getObjectId());
                    builder.add(chosen);
                    if (!FileMode.GITLINK.equals(entry.getFileMode())) {
                        files.add(path);
                    }
                }
            }
            for (java.util.Map.Entry<String, RevTree> element : restored.entrySet()) {
                try (TreeWalk tree = new TreeWalk(m_localRepo)) {
                    tree.addTree(element.getValue());
                    tree.setRecursive(true);
                    tree.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(element.getKey()));
                    while (tree.next()) {
                        final org.eclipse.jgit.dircache.DirCacheEntry entry =
                                new org.eclipse.jgit.dircache.DirCacheEntry(tree.getPathString());
                        entry.setFileMode(tree.getFileMode(0));
                        entry.setObjectId(tree.getObjectId(0));
                        builder.add(entry);
                        if (!FileMode.GITLINK.equals(tree.getFileMode(0))) {
                            files.add(tree.getPathString());
                        }
                    }
                }
            }
            builder.commit();
        } finally {
            cache.unlock();
        }
        // рабочая копия: удалённое — убрать (и пустые папки), остальное — из индекса
        final java.util.Set<String> gone = new java.util.TreeSet<>(removed);
        for (String path : paths) {
            if (!files.contains(path) && !under(path, restored.keySet())) {
                gone.add(path);
            }
        }
        for (String path : gone) {
            final File file = new File(m_localRepo.getWorkTree(), path);
            if (file.exists()) {
                org.eclipse.jgit.util.FileUtils.delete(file, org.eclipse.jgit.util.FileUtils.RECURSIVE
                        | org.eclipse.jgit.util.FileUtils.SKIP_MISSING);
            }
        }
        if (!files.isEmpty()) {
            m_git.checkout().addPaths(files).call();
        }
    }

    /**
     * Элементы книги, в которых лежит файл, — от внешнего к внутреннему: pages/&lt;id&gt;,
     * pages/&lt;id&gt;/links/&lt;id&gt;, …/modifications/&lt;id&gt;, objs/…, vars/…, страницы внутренних модулей
     */
    private static List<String> elementDirs(final String path) {
        final String[] parts = path.split("/");
        final List<String> result = new ArrayList<>();
        for (int k = 0; k + 2 < parts.length; k++) {
            if ("pages".equals(parts[k]) || "objs".equals(parts[k]) || "links".equals(parts[k])
                    || "modifications".equals(parts[k]) || "vars".equals(parts[k])) {
                result.add(String.join("/", Arrays.copyOfRange(parts, 0, k + 2)));
            }
        }
        return result;
    }

    /**
     * Что удалила сторона deleting: самый внешний элемент книги с этим файлом, которого в её
     * версии нет; все есть (удалено только это поле) — сам файл
     */
    private String deletedElement(final String path, final RevTree deleting) throws IOException {
        for (String dir : elementDirs(path)) {
            try (TreeWalk entry = TreeWalk.forPath(m_localRepo, dir, deleting)) {
                if (entry == null) {
                    return dir;
                }
            }
        }
        return path;
    }

    private static boolean under(final String path, final java.util.Collection<String> dirs) {
        for (String dir : dirs) {
            if (path.equals(dir) || path.startsWith(dir + "/")) {
                return true;
            }
        }
        return false;
    }

    private static NLBVCSConflictException uncommittedConflict(final List<String> files, final String message) {
        final List<String> paths = new ArrayList<>(files);
        Collections.sort(paths);
        return new NLBVCSConflictException(message, paths, true);
    }

    /**
     * Как `git merge --abort`: вернуть к HEAD до слияния только файлы, которые трогало слияние
     * (изменённые на сервере с общего предка), и убрать состояние слияния. Остальные файлы не
     * трогаются (незакоммиченных правок в индексе здесь нет: с ними слияние не начинается — FAILED)
     */
    private void abortMerge(final ObjectId before, final ObjectId theirs) throws IOException, GitAPIException {
        final List<String> touched = new ArrayList<>();
        try (RevWalk walk = new RevWalk(m_localRepo)) {
            final RevCommit ours = walk.parseCommit(before);
            final RevCommit their = walk.parseCommit(theirs);
            walk.setRevFilter(org.eclipse.jgit.revwalk.filter.RevFilter.MERGE_BASE);
            walk.markStart(ours);
            walk.markStart(their);
            final RevCommit base = walk.next();
            try (TreeWalk tree = new TreeWalk(m_localRepo)) {
                tree.setRecursive(true);
                if (base != null) {
                    tree.addTree(base.getTree());
                } else {
                    tree.addTree(new org.eclipse.jgit.treewalk.EmptyTreeIterator());
                }
                tree.addTree(their.getTree());
                tree.setFilter(org.eclipse.jgit.treewalk.filter.TreeFilter.ANY_DIFF);
                while (tree.next()) {
                    touched.add(tree.getPathString());
                }
            }
        }
        if (!touched.isEmpty()) {
            // индекс (с ним — стадии конфликта) этих путей — как в HEAD
            final org.eclipse.jgit.api.ResetCommand reset = m_git.reset().setRef(before.name());
            touched.forEach(reset::addPath);
            reset.call();
            // рабочие файлы: есть в HEAD — оттуда, нет — удалить (их добавил сервер)
            final List<String> inHead = new ArrayList<>();
            try (RevWalk walk = new RevWalk(m_localRepo)) {
                final RevTree head = walk.parseCommit(before).getTree();
                for (String path : touched) {
                    try (TreeWalk entry = TreeWalk.forPath(m_localRepo, path, head)) {
                        if (entry != null) {
                            if (!FileMode.GITLINK.equals(entry.getFileMode(0))) {
                                inHead.add(path);
                            }
                        } else {
                            final File file = new File(m_localRepo.getWorkTree(), path);
                            if (file.isFile() && !file.delete()) {
                                LOGGER.warn("Cannot remove {} after the failed merge", file);
                            }
                        }
                    }
                }
            }
            if (!inHead.isEmpty()) {
                m_git.checkout().setStartPoint(before.name()).addPaths(inHead).call();
            }
        }
        m_localRepo.writeMergeHeads(null);
        m_localRepo.writeMergeCommitMsg(null);
    }

    @Override
    public synchronized void push(String userName, String password, final ProgressData progressData) throws NLBVCSException {
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
