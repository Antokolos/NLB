/**
 * @(#)ObjBuildingBlocks.java
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
package com.nlbhub.nlb.domain.export;

import java.util.ArrayList;
import java.util.List;

/**
 * The ObjBuildingBlocks class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/19/13
 */
public class ObjBuildingBlocks {
    private String m_objLabel;
    private String m_objComment;
    private String m_objStart;
    private String m_objName;
    private String m_objImage;
    private String m_objDisp;
    private String m_objText;
    private String m_objActText;
    private boolean m_takable;
    private String m_objTak;
    private String m_objInv;
    private String m_objActStart;
    private String m_objVariable;
    private String m_objModifications;
    private String m_objActEnd;
    private String m_objUseStart;
    private String m_objUseEnd;
    private String m_objObjStart;
    private String m_objObjEnd;
    private String m_objEnd;
    private List<String> m_containedObjIds;
    private List<UseBuildingBlocks> m_useBuildingBlocks;

    public ObjBuildingBlocks() {
        m_useBuildingBlocks = new ArrayList<>();
        m_containedObjIds = new ArrayList<>();
    }

    public String getObjLabel() {
        return m_objLabel;
    }

    public void setObjLabel(String objLabel) {
        m_objLabel = objLabel;
    }

    public String getObjComment() {
        return m_objComment;
    }

    public void setObjComment(String objComment) {
        m_objComment = objComment;
    }

    public String getObjStart() {
        return m_objStart;
    }

    public void setObjStart(String objStart) {
        m_objStart = objStart;
    }

    public String getObjName() {
        return m_objName;
    }

    public void setObjName(String objName) {
        m_objName = objName;
    }

    public String getObjImage() {
        return m_objImage;
    }

    public void setObjImage(String objImage) {
        m_objImage = objImage;
    }

    public String getObjDisp() {
        return m_objDisp;
    }

    public void setObjDisp(String objDisp) {
        m_objDisp = objDisp;
    }

    public String getObjText() {
        return m_objText;
    }

    public void setObjText(String objText) {
        m_objText = objText;
    }

    public String getObjActText() {
        return m_objActText;
    }

    public void setObjActText(String objActText) {
        m_objActText = objActText;
    }

    public boolean isTakable() {
        return m_takable;
    }

    public void setTakable(boolean takable) {
        m_takable = takable;
    }

    public String getObjTak() {
        return m_objTak;
    }

    public void setObjTak(String objTak) {
        m_objTak = objTak;
    }

    public String getObjInv() {
        return m_objInv;
    }

    public void setObjInv(String objInv) {
        m_objInv = objInv;
    }

    public String getObjVariable() {
        return m_objVariable;
    }

    public void setObjVariable(String objVariable) {
        m_objVariable = objVariable;
    }

    public String getObjModifications() {
        return m_objModifications;
    }

    public void setObjModifications(String objModifications) {
        m_objModifications = objModifications;
    }

    public String getObjEnd() {
        return m_objEnd;
    }

    public void setObjEnd(String objEnd) {
        m_objEnd = objEnd;
    }

    public String getObjActStart() {
        return m_objActStart;
    }

    public void setObjActStart(String objActStart) {
        m_objActStart = objActStart;
    }

    public String getObjActEnd() {
        return m_objActEnd;
    }

    public void setObjActEnd(String objActEnd) {
        m_objActEnd = objActEnd;
    }

    public String getObjUseStart() {
        return m_objUseStart;
    }

    public void setObjUseStart(String objUseStart) {
        m_objUseStart = objUseStart;
    }

    public String getObjUseEnd() {
        return m_objUseEnd;
    }

    public void setObjUseEnd(String objUseEnd) {
        m_objUseEnd = objUseEnd;
    }

    public String getObjObjStart() {
        return m_objObjStart;
    }

    public void setObjObjStart(String objObjStart) {
        m_objObjStart = objObjStart;
    }

    public String getObjObjEnd() {
        return m_objObjEnd;
    }

    public void setObjObjEnd(String objObjEnd) {
        m_objObjEnd = objObjEnd;
    }

    public List<String> getContainedObjIds() {
        return m_containedObjIds;
    }

    public void setContainedObjIds(List<String> containedObjIds) {
        m_containedObjIds = containedObjIds;
    }

    public void addContainedObjId(String containedObjId) {
        m_containedObjIds.add(containedObjId);
    }

    public List<UseBuildingBlocks> getUseBuildingBlocks() {
        return m_useBuildingBlocks;
    }

    public void setUseBuildingBlocks(List<UseBuildingBlocks> useBuildingBlocks) {
        m_useBuildingBlocks = useBuildingBlocks;
    }

    public void addUseBuildingBlocks(final UseBuildingBlocks useBuildingBlocks) {
        m_useBuildingBlocks.add(useBuildingBlocks);
    }
}
