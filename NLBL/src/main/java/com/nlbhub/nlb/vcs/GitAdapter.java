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

import com.nlbhub.nlb.exception.NLBVCSException;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * The GitAdapter class
 * Uses sample code from https://github.com/centic9/jgit-cookbook
 *
 * @author Anton P. Kolosov
 * @version 1.0 1/9/14
 */
public class GitAdapter implements VCSAdapter {
    private Repository m_localRepo = null;
    private Git m_git = null;
    private Map<String, Status> m_statuses = new HashMap<>();
    private Author m_author;

    public GitAdapter(final Author author) {
        m_author = author;
    }

    @Override
    public void initRepo(String path) throws NLBVCSException {
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
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            m_localRepo = (
                    builder.setWorkTree(new File(path))
                            .readEnvironment() // scan environment GIT_* variables
                            .setGitDir(new File(path, ".git")) // in fact, this can be omitted
                            .build()
            );
            enableLongPaths(m_localRepo, true);
            m_git = new Git(m_localRepo);
            initStatuses(true);
        } catch (IOException e) {
            throw new NLBVCSException("Error while Git repository opening", e);
        }
    }

    @Override
    public void closeAdapter() throws NLBVCSException {
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

    private List<String> listRepositoryContents() throws IOException {
        List<String> result = new ArrayList<>();
        Ref head = m_localRepo.getRef(Constants.HEAD);
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
        try {
            m_git
                    .commit()
                    .setAll(false)
                    .setAmend(false)
                    .setAuthor(m_author.getName(), m_author.getEmail())
                    .setMessage(message)
                    .call();
        } catch (GitAPIException e) {
            throw new NLBVCSException("Error while committing to the Git repository", e);
        }
    }

    @Override
    public void push(String userName, String password) throws NLBVCSException {
        CredentialsProvider provider = new UsernamePasswordCredentialsProvider(userName, password);
        try {
            m_git
                    .push()
                    .setRemote("origin")
                    .setCredentialsProvider(provider)
                    .call();
        } catch (GitAPIException e) {
            throw new NLBVCSException("Error while pushing to the Git repository", e);
        }
    }
}
