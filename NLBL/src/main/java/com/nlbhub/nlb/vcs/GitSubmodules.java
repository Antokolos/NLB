package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Внешние модули книги, подключённые подмодулями git (modules/&lt;имя&gt;; см.
 * docs/external-modules.md), в операциях VCS редактора:
 * <ul>
 * <li>подмодуль после клонирования или обновления — в отсоединённом HEAD (detached): push из
 * него невозможен. {@link #attachDetachedHead} переводит HEAD на ветку, которая указывает на тот
 * же коммит (индекс и рабочий каталог не меняются);</li>
 * <li>commit книги записывает и новые коммиты модулей ({@link #stageSubmodulePointers});</li>
 * <li>push книги сначала отправляет модули с неотправленными коммитами ({@link #pushSubmodules}),
 * иначе книга сослалась бы на коммит модуля, которого нет на сервере;</li>
 * <li>pull книги обновляет и модули ({@link #updateSubmodules}).</li>
 * </ul>
 */
final class GitSubmodules {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitSubmodules.class);
    private static final String ORIGIN = "origin";

    private GitSubmodules() {
    }

    /**
     * Отсоединённый HEAD — на ветку с тем же коммитом: локальную, а если её нет — с сервера
     * (локальная ветка создаётся или перематывается вперёд). Предпочтение — ветке по умолчанию
     * сервера, затем master и main; при нескольких других кандидатах HEAD не меняется.
     *
     * @return имя ветки, на которую переведён HEAD, или null
     */
    static String attachDetachedHead(final Repository repo) throws IOException {
        final String full = repo.getFullBranch();
        if (full == null || full.startsWith(Constants.R_HEADS)) {
            return null;
        }
        final ObjectId head = repo.resolve(Constants.HEAD);
        if (head == null) {
            return null;
        }
        final RefDatabase refs = repo.getRefDatabase();
        final String preferred = defaultRemoteBranch(repo);
        List<String> local = new ArrayList<>();
        for (Ref ref : refs.getRefsByPrefix(Constants.R_HEADS)) {
            if (head.equals(ref.getObjectId())) {
                local.add(ref.getName().substring(Constants.R_HEADS.length()));
            }
        }
        String branch = choose(local, preferred);
        if (branch == null) {
            final String remotePrefix = Constants.R_REMOTES + ORIGIN + "/";
            List<String> remote = new ArrayList<>();
            for (Ref ref : refs.getRefsByPrefix(remotePrefix)) {
                String name = ref.getName().substring(remotePrefix.length());
                if (!name.equals(Constants.HEAD) && !ref.isSymbolic() && head.equals(ref.getObjectId())) {
                    remote.add(name);
                }
            }
            branch = choose(remote, preferred);
            if (branch == null || !moveLocalBranch(repo, branch, head)) {
                return null;
            }
        }
        RefUpdate update = repo.updateRef(Constants.HEAD);
        RefUpdate.Result result = update.link(Constants.R_HEADS + branch);
        if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NEW && result != RefUpdate.Result.NO_CHANGE) {
            LOGGER.warn("Cannot switch detached HEAD of {} to branch {}: {}", repo.getWorkTree(), branch, result);
            return null;
        }
        LOGGER.info("Detached HEAD of {} switched to branch {} (same commit)", repo.getWorkTree(), branch);
        return branch;
    }

    private static String defaultRemoteBranch(final Repository repo) throws IOException {
        Ref ref = repo.exactRef(Constants.R_REMOTES + ORIGIN + "/" + Constants.HEAD);
        if (ref != null && ref.isSymbolic()) {
            String target = ref.getTarget().getName();
            String prefix = Constants.R_REMOTES + ORIGIN + "/";
            return target.startsWith(prefix) ? target.substring(prefix.length()) : null;
        }
        return null;
    }

    private static String choose(final List<String> candidates, final String preferred) {
        for (String name : new String[]{preferred, "master", "main"}) {
            if (name != null && candidates.contains(name)) {
                return name;
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * Локальная ветка name — на коммит head с сервера: создать (с отслеживанием origin/name)
     * или перемотать вперёд; ветку со своими коммитами не трогать
     */
    private static boolean moveLocalBranch(final Repository repo, final String name, final ObjectId head) throws IOException {
        final String refName = Constants.R_HEADS + name;
        final Ref existing = repo.exactRef(refName);
        if (existing != null) {
            try (RevWalk walk = new RevWalk(repo)) {
                if (!walk.isMergedInto(walk.parseCommit(existing.getObjectId()), walk.parseCommit(head))) {
                    return false;  // в локальной ветке есть коммиты, которых нет на сервере
                }
            }
        }
        RefUpdate update = repo.updateRef(refName);
        update.setNewObjectId(head);
        update.setExpectedOldObjectId(existing == null ? ObjectId.zeroId() : existing.getObjectId());
        update.setRefLogMessage("NLB: branch " + name + " moved to the module commit", false);
        RefUpdate.Result result = update.update();
        if (result != RefUpdate.Result.NEW && result != RefUpdate.Result.FAST_FORWARD && result != RefUpdate.Result.NO_CHANGE) {
            return false;
        }
        if (existing == null) {
            StoredConfig config = repo.getConfig();
            config.setString("branch", name, "remote", ORIGIN);
            config.setString("branch", name, "merge", refName);
            config.save();
        }
        return true;
    }

    /**
     * Перед commit книги — в индекс новые коммиты модулей (у которых HEAD не тот, что записан
     * в книге). Незакоммиченные правки модуля в commit книги не попадают — о них предупреждение
     *
     * @return пути модулей, чьи коммиты добавлены
     */
    static List<String> stageSubmodulePointers(final Git git) throws IOException, GitAPIException {
        List<String> staged = new ArrayList<>();
        try (SubmoduleWalk walk = SubmoduleWalk.forIndex(git.getRepository())) {
            while (walk.next()) {
                try (Repository sub = walk.getRepository()) {
                    if (sub == null) {
                        continue;  // модуль не скачан
                    }
                    ObjectId head = sub.resolve(Constants.HEAD);
                    if (head != null && !head.equals(walk.getObjectId())) {
                        git.add().addFilepattern(walk.getPath()).call();
                        staged.add(walk.getPath());
                    }
                    if (Git.wrap(sub).status().call().hasUncommittedChanges()) {
                        LOGGER.warn("Module {} has uncommitted changes: they are not part of the book commit", walk.getPath());
                    }
                }
            }
        }
        return staged;
    }

    /**
     * Перед push книги — push модулей, в ветке которых есть неотправленные коммиты. Модуль в
     * отсоединённом HEAD на коммите, которого нет на сервере, — ошибка: книга сослалась бы на него
     */
    static void pushSubmodules(
            final Repository repo,
            final CredentialsProvider provider,
            final ProgressMonitor monitor
    ) throws IOException, GitAPIException, NLBVCSException {
        try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
            while (walk.next()) {
                try (Repository sub = walk.getRepository()) {
                    if (sub == null) {
                        continue;
                    }
                    final String full = sub.getFullBranch();
                    final ObjectId head = sub.resolve(Constants.HEAD);
                    if (head == null) {
                        continue;
                    }
                    if (full == null || !full.startsWith(Constants.R_HEADS)) {
                        if (!isOnServer(sub, head)) {
                            throw new NLBVCSException(
                                    "Module " + walk.getPath() + " is at a commit that is not on the server and not on a branch "
                                            + "(detached HEAD). Open the module in the editor (it will be switched to its branch) "
                                            + "or run 'git checkout <branch>' in it, then push again."
                            );
                        }
                        continue;
                    }
                    final String branch = full.substring(Constants.R_HEADS.length());
                    final Ref upstream = sub.exactRef(Constants.R_REMOTES + ORIGIN + "/" + branch);
                    if (upstream != null && upstream.getObjectId() != null) {
                        try (RevWalk revWalk = new RevWalk(sub)) {
                            if (revWalk.isMergedInto(revWalk.parseCommit(head), revWalk.parseCommit(upstream.getObjectId()))) {
                                continue;  // всё уже на сервере
                            }
                        }
                    }
                    LOGGER.info("Pushing module {} (branch {}) before the book", walk.getPath(), branch);
                    checkPushed(
                            "module " + walk.getPath(),
                            Git.wrap(sub).push().setRemote(ORIGIN).setCredentialsProvider(provider).setProgressMonitor(monitor).call()
                    );
                }
            }
        }
    }

    private static boolean isOnServer(final Repository sub, final ObjectId commit) throws IOException {
        try (RevWalk walk = new RevWalk(sub)) {
            for (Ref ref : sub.getRefDatabase().getRefsByPrefix(Constants.R_REMOTES)) {
                if (ref.getObjectId() != null
                        && walk.isMergedInto(walk.parseCommit(commit), walk.parseCommit(ref.getObjectId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Push, отклонённый сервером (например, на сервере есть чужие коммиты), — ошибка, а не
     * молчаливый успех
     */
    static void checkPushed(final String what, final Iterable<PushResult> results) throws NLBVCSException {
        for (PushResult result : results) {
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    throw new NLBVCSException(
                            "Push of " + what + " was rejected (" + update.getRemoteName() + ": " + status
                                    + (update.getMessage() != null ? ", " + update.getMessage() : "")
                                    + "). Pull first, then push again."
                    );
                }
            }
        }
    }

    /**
     * После pull книги — модули на записанные в книге коммиты (недостающие скачиваются) и на
     * ветку; модули с незакоммиченными правками не трогаются
     *
     * @return пути модулей, которые не обновлены из-за незакоммиченных правок
     */
    static List<String> updateSubmodules(
            final Git git,
            final CredentialsProvider provider,
            final ProgressMonitor monitor
    ) throws IOException, GitAPIException {
        final Repository repo = git.getRepository();
        git.submoduleInit().call();
        List<String> update = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
            while (walk.next()) {
                try (Repository sub = walk.getRepository()) {
                    if (sub == null) {
                        update.add(walk.getPath());  // новый модуль — клонируется
                        continue;
                    }
                    ObjectId head = sub.resolve(Constants.HEAD);
                    if (walk.getObjectId().equals(head)) {
                        attachDetachedHead(sub);
                        continue;
                    }
                    Git subGit = Git.wrap(sub);
                    if (subGit.status().call().hasUncommittedChanges()) {
                        LOGGER.warn("Module {} has uncommitted changes and is not updated", walk.getPath());
                        skipped.add(walk.getPath());
                        continue;
                    }
                    // JGit не скачивает недостающие коммиты уже клонированного подмодуля сам
                    subGit.fetch().setRemote(ORIGIN).setCredentialsProvider(provider).setProgressMonitor(monitor).call();
                    update.add(walk.getPath());
                }
            }
        }
        if (!update.isEmpty()) {
            org.eclipse.jgit.api.SubmoduleUpdateCommand command = git.submoduleUpdate()
                    .setCredentialsProvider(provider)
                    .setProgressMonitor(monitor);
            for (String path : update) {
                command.addPath(path);
            }
            command.call();
            try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
                while (walk.next()) {
                    if (update.contains(walk.getPath())) {
                        try (Repository sub = walk.getRepository()) {
                            if (sub != null) {
                                attachDetachedHead(sub);
                            }
                        }
                    }
                }
            }
        }
        return skipped;
    }
}
