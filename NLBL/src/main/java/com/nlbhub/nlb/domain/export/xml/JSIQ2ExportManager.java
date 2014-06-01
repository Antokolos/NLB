/**
 * @(#)JSIQ2ExportManager.java
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
package com.nlbhub.nlb.domain.export.xml;

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.NLBBuildingBlocks;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.JaxbMarshaller;

/**
 * The JSIQ2ExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 6/01/14
 */
public class JSIQ2ExportManager extends XMLExportManager {
    protected JSIQ2ExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected JaxbMarshaller createMarshaller() {
        return null;
    }

    @Override
    protected Object createRootObject(NLBBuildingBlocks nlbBlocks) {
        return null;
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return null;
    }

    @Override
    protected String decorateDelObj(String objectId, String objectName) {
        return null;
    }

    @Override
    protected String decorateAddObj(String objectId, String objectName) {
        return null;
    }

    @Override
    protected String decorateNot() {
        return null;
    }

    @Override
    protected String decorateOr() {
        return null;
    }

    @Override
    protected String decorateAnd() {
        return null;
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return null;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText) {
        return null;
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return null;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return null;
    }

    @Override
    protected String decorateLinkGoTo(String linkId, String linkText, String linkTarget, int targetPageNumber) {
        return null;
    }

    @Override
    protected String decoratePageEnd() {
        return null;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        return null;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        return null;
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return null;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return null;
    }

    @Override
    protected String decoratePageCaption(String caption) {
        return null;
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        return null;
    }

    @Override
    protected String decoratePageTextEnd() {
        return null;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        return null;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return null;
    }

    @Override
    protected String decoratePageComment(String comment) {
        return null;
    }
}
