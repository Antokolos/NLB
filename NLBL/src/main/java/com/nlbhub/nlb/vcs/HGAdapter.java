/**
 * @(#)HGAdapter.java
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

import com.nlbhub.nlb.exception.NLBVCSException;
import org.tmatesoft.hg.core.*;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.Outcome;
import org.tmatesoft.hg.util.Path;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The HGAdapter class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/30/13
 */
public class HGAdapter implements VCSAdapter {
    private HgRepoFacade m_hgRepo = new HgRepoFacade();
    private Map<String, HgStatus.Kind> m_statuses = new HashMap<>();
    private Author m_author;

    public HGAdapter(final Author author) {
        m_author = author;
    }

    @Override
    public void initRepo(String path) throws NLBVCSException {
        File repoLoc = new File(path);
        HgInitCommand cmd = new HgInitCommand().location(repoLoc).revlogV1();
        try {
            m_hgRepo.init(cmd.execute());
            initStatuses();
        } catch (HgException | CancelledException e) {
            throw new NLBVCSException("Error while HG repository initialization", e);
        }
    }

    @Override
    public void openRepo(String path) throws NLBVCSException {
        try {
            if (!m_hgRepo.initFrom(new File(path))) {
                throw new NLBVCSException(
                    "Can't find repository in: " + m_hgRepo.getRepository().getLocation()
                );
            }
            initStatuses();
        } catch (HgRepositoryNotFoundException e) {
            throw new NLBVCSException("Error while HG repository opening", e);
        }
    }

    @Override
    public void closeAdapter() throws NLBVCSException {
        // TODO: Add more functionality (if needed)
        m_statuses.clear();
    }

    @Override
    public boolean getDirAddFlag() {
        return false;
    }

    @Override
    public boolean getAddModifiedFilesFlag() {
        return false;
    }

    @Override
    public Status getStatus(String path) throws NLBVCSException {
        HgStatus.Kind kind = m_statuses.get(path);
        if (kind == HgStatus.Kind.Modified) {
            return Status.Modified;
        } else if (kind == HgStatus.Kind.Added) {
            return Status.Added;
        } else if (kind == HgStatus.Kind.Removed) {
            return Status.Removed;
        } else if (kind == HgStatus.Kind.Missing) {
            return Status.Missing;
        } else if (kind == HgStatus.Kind.Unknown) {
            return Status.Unknown;
        } else if (kind == HgStatus.Kind.Clean) {
            return Status.Clean;
        } else if (kind == HgStatus.Kind.Ignored) {
            return Status.Ignored;
        }
        return Status.VCS_Undefined;
    }

    private void initStatuses() throws NLBVCSException {
        m_statuses.clear();
        final StringBuilder stringBuilder = new StringBuilder();
        HgStatusCommand statusCommand = m_hgRepo.createStatusCommand();

        // .all() - indicates we're interested in any status,
        // not only default Modified, Added, Removed, Missing, Unknown
        try {
            statusCommand.all().execute(new HgStatusHandler() {
                @Override
                public void status(HgStatus hgStatus) throws HgCallbackTargetException {
                    m_statuses.put(hgStatus.getPath().toString(), hgStatus.getKind());
                }

                @Override
                public void error(Path path, Outcome outcome) throws HgCallbackTargetException {
                    stringBuilder.append(path).append("; ");
                }
            });
            if (stringBuilder.length() > 0) {
                throw new NLBVCSException(
                    "Error during retrieving status for path(s): " + stringBuilder.toString()
                );
            }
        } catch (HgCallbackTargetException | HgException | IOException | CancelledException e) {
            throw new NLBVCSException("Error while obtaining HG repository status", e);
        }
    }

    @Override
    public void add(String path) throws NLBVCSException {
        HgAddRemoveCommand cmd = new HgAddRemoveCommand(m_hgRepo.getRepository());
        try {
            Status prevStatus = getStatus(path);
            cmd.add(Path.create(path));
            cmd.execute();
            if (prevStatus == Status.Unknown || prevStatus == Status.VCS_Undefined) {
                // If file previously was unknown to the HG, we should mark it as added
                // This is not necessarily needed for HG (because addModifiedFilesFlag is false),
                // but it was added by analogy with Git
                m_statuses.put(path, HgStatus.Kind.Added);
            }
        } catch (HgException | CancelledException e) {
            throw new NLBVCSException("Error while adding to the HG repository", e);
        }
    }

    @Override
    public boolean remove(String path) throws NLBVCSException {
        HgAddRemoveCommand cmd = new HgAddRemoveCommand(m_hgRepo.getRepository());
        try {
            cmd.remove(Path.create(path));
            cmd.execute();
            m_statuses.put(path, HgStatus.Kind.Removed);
            return true;
        } catch (HgException | CancelledException e) {
            throw new NLBVCSException("Error while removing from HG repository", e);
        }
    }

    @Override
    public void reset(String path) throws NLBVCSException {
        HgRevertCommand cmd = new HgRevertCommand(m_hgRepo.getRepository()).file(Path.create(path));
        try {
            Status prevStatus = getStatus(path);
            cmd.execute();
            switch (prevStatus) {
                case Added:
                    m_statuses.put(path, HgStatus.Kind.Unknown);
                    break;
                case Removed:
                case Modified:
                case Missing:
                case Clean:
                    m_statuses.put(path, HgStatus.Kind.Clean);
                    break;
                case Unknown:
                case VCS_Undefined:
                case Ignored:
                    throw new NLBVCSException(
                        "Cannot issue reset command for file with path = " + path
                        + ", because its status was " + prevStatus
                    );
            }
        } catch (HgException | CancelledException e) {
            throw new NLBVCSException("Error while reverting file in HG repository", e);
        }
    }

    @Override
    public void commit(String message) throws NLBVCSException {
        // TODO: add implementation
    }
}
