package com.nlbhub.nlb.vcs;

import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * История книги в git (для {@link GitAdapter}): версии первой линии истории, их изменённые файлы,
 * тексты файлов версии. Только чтение — ни рабочие файлы, ни индекс, ни ссылки не меняются
 */
final class GitHistory {
    /**
     * Файл больше — текст не читается (поля книги — маленькие файлы; большой — не поле)
     */
    static final int MAX_TEXT_BYTES = 256 * 1024;

    private GitHistory() {
    }

    /**
     * Последнее известное состояние ветки сервера (origin/&lt;upstream текущей ветки&gt;, иначе
     * origin/&lt;текущая ветка&gt;); null — нет
     */
    static Ref remoteTrackingRef(final Repository repo) throws IOException {
        final String branch = repo.getBranch();
        if (branch == null) {
            return null;
        }
        String upstream = repo.getConfig().getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch,
                ConfigConstants.CONFIG_KEY_MERGE);
        if (upstream == null) {
            upstream = Constants.R_HEADS + branch;
        }
        return repo.exactRef(Constants.R_REMOTES + "origin/" + Repository.shortenRefName(upstream));
    }

    static List<BookVersion> history(final Repository repo, final int offset, final int limit) throws NLBVCSException {
        if (repo == null || limit <= 0) {
            return Collections.emptyList();
        }
        try {
            final ObjectId head = repo.resolve(Constants.HEAD);
            if (head == null) {
                return Collections.emptyList();
            }
            final Ref server = remoteTrackingRef(repo);
            final List<BookVersion> result = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repo)) {
                walk.setFirstParent(true);
                walk.markStart(walk.parseCommit(head));
                final RevCommit serverCommit = server == null ? null : walk.parseCommit(server.getObjectId());
                // предок версии, которая есть на сервере, — тоже на сервере: после первой такой
                // версии дальше не проверять
                boolean onServer = false;
                int index = 0;
                for (RevCommit commit : walk) {
                    if (index++ < offset) {
                        continue;
                    }
                    if (result.size() >= limit) {
                        break;
                    }
                    if (serverCommit != null && !onServer) {
                        onServer = isOnServer(repo, commit, serverCommit);
                    }
                    final BookVersion.ServerState state = serverCommit == null ? BookVersion.ServerState.NO_SERVER
                            : onServer ? BookVersion.ServerState.ON_SERVER : BookVersion.ServerState.LOCAL_ONLY;
                    result.add(version(repo, commit, state));
                }
            }
            return result;
        } catch (IOException e) {
            throw new NLBVCSException("Cannot read the history of the book", e);
        }
    }

    private static boolean isOnServer(final Repository repo, final RevCommit commit, final RevCommit serverCommit)
            throws IOException {
        // отдельный обход: RevWalk.isMergedInto сбрасывает флаги обхода, которым идёт история
        try (RevWalk walk = new RevWalk(repo)) {
            return walk.isMergedInto(walk.parseCommit(commit), walk.parseCommit(serverCommit));
        }
    }

    private static BookVersion version(final Repository repo, final RevCommit commit, final BookVersion.ServerState state)
            throws IOException {
        final boolean merge = commit.getParentCount() > 1;
        final LinkedHashSet<String> authors = new LinkedHashSet<>();
        int count = 0;
        if (merge) {
            // что принесло слияние: версии второго родителя, которых не было у первого
            try (RevWalk walk = new RevWalk(repo)) {
                walk.markStart(walk.parseCommit(commit.getParent(1)));
                walk.markUninteresting(walk.parseCommit(commit.getParent(0)));
                for (RevCommit brought : walk) {
                    if (brought.getParentCount() < 2) {
                        count++;
                        authors.add(brought.getAuthorIdent().getName());
                    }
                }
            }
        }
        final long time = (merge ? commit.getCommitterIdent() : commit.getAuthorIdent()).getWhenAsInstant().toEpochMilli();
        return new BookVersion(commit.getName(), commit.getFullMessage().trim(), commit.getAuthorIdent().getName(),
                commit.getAuthorIdent().getEmailAddress(), time, state, merge, new ArrayList<>(authors), count);
    }

    static List<BookFileChange> changes(final Repository repo, final String versionId) throws NLBVCSException {
        if (repo == null) {
            return Collections.emptyList();
        }
        try (ObjectReader reader = repo.newObjectReader();
             RevWalk walk = new RevWalk(reader);
             DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(repo);
            formatter.setDetectRenames(false);
            final AbstractTreeIterator before;
            final AbstractTreeIterator after;
            if (versionId == null) {
                // несохранённое в версии: индекс против HEAD (то, что войдёт в Commit)
                final ObjectId head = repo.resolve(Constants.HEAD);
                before = head == null ? new EmptyTreeIterator() : tree(reader, walk.parseCommit(head));
                after = new DirCacheIterator(repo.readDirCache());
            } else {
                final ObjectId id = repo.resolve(versionId);
                if (id == null) {
                    throw new NLBVCSException("Version " + versionId + " is not found in the history of the book");
                }
                final RevCommit commit = walk.parseCommit(id);
                before = commit.getParentCount() == 0 ? new EmptyTreeIterator()
                        : tree(reader, walk.parseCommit(commit.getParent(0)));
                after = tree(reader, commit);
            }
            final List<BookFileChange> result = new ArrayList<>();
            for (DiffEntry entry : formatter.scan(before, after)) {
                result.add(change(reader, entry));
            }
            return result;
        } catch (IOException e) {
            throw new NLBVCSException("Cannot read the changes of the book version", e);
        }
    }

    private static CanonicalTreeParser tree(final ObjectReader reader, final RevCommit commit) throws IOException {
        final CanonicalTreeParser parser = new CanonicalTreeParser();
        parser.reset(reader, commit.getTree().getId());
        return parser;
    }

    private static BookFileChange change(final ObjectReader reader, final DiffEntry entry) throws IOException {
        final BookFileChange.Kind kind;
        final String path;
        switch (entry.getChangeType()) {
            case ADD:
                kind = BookFileChange.Kind.ADDED;
                path = entry.getNewPath();
                break;
            case DELETE:
                kind = BookFileChange.Kind.DELETED;
                path = entry.getOldPath();
                break;
            default:
                kind = BookFileChange.Kind.MODIFIED;
                path = entry.getNewPath();
                break;
        }
        final boolean submodule = entry.getOldMode() == FileMode.GITLINK || entry.getNewMode() == FileMode.GITLINK;
        final String beforeId = kind == BookFileChange.Kind.ADDED ? null : name(entry.getOldId());
        final String afterId = kind == BookFileChange.Kind.DELETED ? null : name(entry.getNewId());
        if (submodule) {
            return new BookFileChange(path, kind, null, null, false, true, beforeId, afterId);
        }
        final byte[] before = kind == BookFileChange.Kind.ADDED ? null : bytes(reader, entry.getOldId());
        final byte[] after = kind == BookFileChange.Kind.DELETED ? null : bytes(reader, entry.getNewId());
        final boolean binary = (kind != BookFileChange.Kind.ADDED && before == null)
                || (kind != BookFileChange.Kind.DELETED && after == null)
                || (before != null && RawText.isBinary(before)) || (after != null && RawText.isBinary(after));
        return new BookFileChange(path, kind, binary ? null : text(before), binary ? null : text(after), binary, false,
                beforeId, afterId);
    }

    private static String name(final AbbreviatedObjectId id) {
        return id == null || !id.isComplete() ? null : id.name();
    }

    /**
     * Содержимое файла или null, если он слишком большой (тогда он считается двоичным)
     */
    private static byte[] bytes(final ObjectReader reader, final AbbreviatedObjectId id) throws IOException {
        if (id == null || !id.isComplete() || ObjectId.zeroId().equals(id.toObjectId())) {
            return new byte[0];
        }
        // размер — по заголовку объекта, без чтения содержимого (звуки и картинки — мегабайты)
        if (reader.getObjectSize(id.toObjectId(), Constants.OBJ_BLOB) > MAX_TEXT_BYTES) {
            return null;
        }
        return reader.open(id.toObjectId()).getCachedBytes(MAX_TEXT_BYTES);
    }

    private static String text(final byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Пути всех файлов версии (относительно папки книги); версии нет — пусто
     */
    static List<String> files(final Repository repo, final String versionId) throws NLBVCSException {
        if (repo == null) {
            return Collections.emptyList();
        }
        try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new RevWalk(reader)) {
            final ObjectId id = repo.resolve(versionId == null ? Constants.HEAD : versionId);
            if (id == null) {
                return Collections.emptyList();
            }
            final List<String> result = new ArrayList<>();
            try (TreeWalk tree = new TreeWalk(reader)) {
                tree.addTree(walk.parseCommit(id).getTree());
                tree.setRecursive(true);
                while (tree.next()) {
                    result.add(tree.getPathString());
                }
            }
            return result;
        } catch (IOException e) {
            throw new NLBVCSException("Cannot read the files of the book version", e);
        }
    }

    static Map<String, String> texts(final Repository repo, final String versionId, final String dirPath)
            throws NLBVCSException {
        if (repo == null || dirPath == null || dirPath.isEmpty()) {
            return Collections.emptyMap();
        }
        try (ObjectReader reader = repo.newObjectReader(); RevWalk walk = new RevWalk(reader)) {
            final ObjectId id = repo.resolve(versionId == null ? Constants.HEAD : versionId);
            if (id == null) {
                return Collections.emptyMap();
            }
            final Map<String, String> result = new LinkedHashMap<>();
            final String prefix = dirPath.endsWith("/") ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
            try (TreeWalk tree = new TreeWalk(reader)) {
                tree.addTree(walk.parseCommit(id).getTree());
                tree.setRecursive(true);
                tree.setFilter(PathFilter.create(prefix));
                while (tree.next()) {
                    if (tree.getFileMode(0) == FileMode.GITLINK) {
                        continue;
                    }
                    final String path = tree.getPathString();
                    if (!path.startsWith(prefix + "/")) {
                        continue;  // файл с тем же именем, что папка, — не её содержимое
                    }
                    if (reader.getObjectSize(tree.getObjectId(0), Constants.OBJ_BLOB) > MAX_TEXT_BYTES) {
                        continue;
                    }
                    final byte[] bytes = reader.open(tree.getObjectId(0)).getCachedBytes(MAX_TEXT_BYTES);
                    if (!RawText.isBinary(bytes)) {
                        result.put(path.substring(prefix.length() + 1), text(bytes));
                    }
                }
            }
            return result;
        } catch (IOException e) {
            throw new NLBVCSException("Cannot read the files of the book version", e);
        }
    }
}
