/**
 * @(#)HGAdapterWithPathDecoration.java
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

public class HGAdapterWithPathDecoration implements VCSAdapter {
    HGAdapter m_hgAdapter;

    public HGAdapterWithPathDecoration(final Author author) {
        m_hgAdapter = new HGAdapter(author);
    }

    @Override
    public void initRepo(String path) throws NLBVCSException {
        m_hgAdapter.initRepo(decoratePath(path));
    }

    @Override
    public void openRepo(String path) throws NLBVCSException {
        m_hgAdapter.openRepo(decoratePath(path));
    }

    @Override
    public void closeAdapter() throws NLBVCSException {
        m_hgAdapter.closeAdapter();
    }

    @Override
    public boolean getDirAddFlag() {
        return m_hgAdapter.getDirAddFlag();
    }

    @Override
    public boolean getAddModifiedFilesFlag() {
        return m_hgAdapter.getAddModifiedFilesFlag();
    }

    @Override
    public Status getStatus(String path) throws NLBVCSException {
        return m_hgAdapter.getStatus(decoratePath(path));
    }

    @Override
    public void add(String path) throws NLBVCSException {
        m_hgAdapter.add(decoratePath(path));
    }

    @Override
    public boolean remove(String path) throws NLBVCSException {
        return m_hgAdapter.remove(decoratePath(path));
    }

    @Override
    public void reset(String path) throws NLBVCSException {
        m_hgAdapter.reset(decoratePath(path));
    }

    @Override
    public void commit(String message) throws NLBVCSException {
        m_hgAdapter.commit(message);
    }

    @Override
    public void pull(String userName, String password, ProgressData progressData) throws NLBVCSException {
        m_hgAdapter.pull(userName, password, progressData);
    }

    @Override
    public void push(String userName, String password, ProgressData progressData) throws NLBVCSException {
        m_hgAdapter.push(userName, password, progressData);
    }

    private String decoratePath(final String path) {
        return path.replaceAll("\\\\", "/");
    }
}
