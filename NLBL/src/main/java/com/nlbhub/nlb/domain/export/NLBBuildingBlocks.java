/**
 * @(#)NLBBuildingBlocks.java This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2014 Anton P. Kolosov
 * Authors: Anton P. Kolosov, et al.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation with the addition of the
 * following permission added to Section 15 as permitted in Section 7(a):
 * FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
 * ANTON P. KOLOSOV. ANTON P. KOLOSOV DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
 * OF THIRD PARTY RIGHTS
 * <p>
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, see http://www.gnu.org/licenses or write to
 * the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 * <p>
 * The interactive user interfaces in modified source and object code versions
 * of this program must display Appropriate Legal Notices, as required under
 * Section 5 of the GNU Affero General Public License.
 * <p>
 * You can be released from the requirements of the license by purchasing
 * a commercial license. Buying such a license is mandatory as soon as you
 * develop commercial activities involving the Non-Linear Book software without
 * disclosing the source code of your own applications.
 * <p>
 * For more information, please contact Anton P. Kolosov at this
 * address: antokolos@gmail.com
 * <p>
 * Copyright (c) 2013 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The NLBBuildingBlocks class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/5/13
 */
public class NLBBuildingBlocks {
    private String m_title;
    private String m_author;
    private String m_version;
    private String m_lang;
    private Set<String> m_achievements = new TreeSet<>();
    private String m_achievementNamePerfectGame;
    private String m_gameActText;
    private String m_gameInvText;
    private String m_gameNouseText;
    private boolean m_gameForcedsc;
    private List<PageBuildingBlocks> m_pagesBuildingBlocks;
    private List<ObjBuildingBlocks> m_objsBuildingBlocks;

    public NLBBuildingBlocks(
            String title,
            String author,
            String version,
            String lang,
            Set<String> achievements,
            String achievementNamePerfectGame,
            String gameActText,
            String gameInvText,
            String gameNouseText,
            boolean gameForceDsc
    ) {
        m_title = title;
        m_author = author;
        m_version = version;
        m_lang = lang;
        m_pagesBuildingBlocks = new ArrayList<>();
        m_objsBuildingBlocks = new ArrayList<>();
        m_achievements.addAll(achievements);
        m_achievementNamePerfectGame = achievementNamePerfectGame;
        m_gameActText = gameActText;
        m_gameInvText = gameInvText;
        m_gameNouseText = gameNouseText;
        m_gameForcedsc = gameForceDsc;
    }

    public String getTitle() {
        return m_title;
    }

    public void setTitle(String title) {
        m_title = title;
    }

    public String getAuthor() {
        return m_author;
    }

    public void setAuthor(String author) {
        m_author = author;
    }

    public String getVersion() {
        return m_version;
    }

    public void setVersion(String version) {
        m_version = version;
    }

    public String getLang() {
        return m_lang;
    }

    public void setLang(String lang) {
        m_lang = lang;
    }

    public List<PageBuildingBlocks> getPagesBuildingBlocks() {
        return m_pagesBuildingBlocks;
    }

    public void addPageBuildingBlocks(final PageBuildingBlocks pageBuildingBlocks) {
        m_pagesBuildingBlocks.add(pageBuildingBlocks);
    }

    public List<ObjBuildingBlocks> getObjsBuildingBlocks() {
        return m_objsBuildingBlocks;
    }

    public void addObjBuildingBlocks(final ObjBuildingBlocks objBuildingBlocks) {
        m_objsBuildingBlocks.add(objBuildingBlocks);
    }

    public void addNLBBuildingBlocks(final NLBBuildingBlocks nlbBuildingBlocks) {
        m_pagesBuildingBlocks.addAll(nlbBuildingBlocks.m_pagesBuildingBlocks);
        m_objsBuildingBlocks.addAll(nlbBuildingBlocks.m_objsBuildingBlocks);
    }

    public String getAchievementNamePerfectGame() {
        return m_achievementNamePerfectGame;
    }

    public String getGameActText() {
        return m_gameActText;
    }

    public String getGameInvText() {
        return m_gameInvText;
    }

    public String getGameNouseText() {
        return m_gameNouseText;
    }

    public boolean isGameForcedsc() {
        return m_gameForcedsc;
    }

    public Set<String> getAchievements() {
        return m_achievements;
    }

    public void addAchievements(Set<String> achievements) {
        m_achievements.addAll(achievements);
    }
}
