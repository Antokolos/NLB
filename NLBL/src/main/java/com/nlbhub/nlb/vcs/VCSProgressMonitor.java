/**
 * @(#)VCSProgressMonitor.java
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
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * The VCSProgressMonitor class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class VCSProgressMonitor implements ProgressMonitor {
    final ProgressData m_progressData;

    public VCSProgressMonitor(ProgressData progressData) {
        m_progressData = progressData;
    }

    /**
     * Advise the monitor of the total number of subtasks.
     * This should be invoked at most once per progress monitor interface.
     *
     * @param i - the total number of tasks the caller will need to complete their processing.
     */
    @Override
    public void start(int i) {
    }

    /**
     * Begin processing a single task.
     *
     * @param s title to describe the task. Callers should publish these as stable string constants that
     *          implementations could match against for translation support.
     * @param i total number of work units the application will perform; UNKNOWN if it cannot be predicted
     *          in advance.
     */
    @Override
    public void beginTask(String s, int i) {
    }

    /**
     * Denote that some work units have been completed.
     * This is an incremental update; if invoked once per work unit the correct value for our argument is 1, to
     * indicate a single unit of work has been finished by the caller.
     *
     * @param i the number of work units completed since the last call.
     */
    @Override
    public void update(int i) {
        // TODO: more informative progress
        m_progressData.setProgressValue(50);
    }

    /**
     * Finish the current task, so the next can begin.
     */
    @Override
    public void endTask() {
    }

    /**
     * Check for user task cancellation.
     *
     * @return true if the user asked the process to stop working.
     */
    @Override
    public boolean isCancelled() {
        return false;
    }
}
