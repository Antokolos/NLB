/**
 * @(#)TaggedTextExportManager.java
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
package com.nlbhub.nlb.domain.export.hypertext;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.LinkBuildingBlocks;
import com.nlbhub.nlb.domain.export.hypertext.document.*;
import com.nlbhub.nlb.exception.HTDocumentException;
import com.nlbhub.nlb.exception.NLBExportException;

import java.util.List;

/**
 * The TaggedTextExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class TaggedTextExportManager extends HypertextExportManager<TXTParagraph, TXTAnchor, TXTFont> {
    public TaggedTextExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    @Override
    protected String getLinkReference(LinkBuildingBlocks linkBlocks) {
        return linkBlocks.getLinkGoTo();
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return linkText;
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return String.valueOf(targetPageNumber);
    }

    @Override
    protected HTDocument<TXTParagraph> createDocument(String encoding, String lineSeparator) {
        return new TXTDocument(encoding, lineSeparator);
    }

    @Override
    protected TXTFont createParaFont() throws HTDocumentException {
        return new TXTFont();
    }

    @Override
    protected TXTFont createLinkFont() throws HTDocumentException {
        return new TXTFont();
    }

    @Override
    protected TXTParagraph createHTParagraph(String text, TXTFont font) throws HTDocumentException {
        return new TXTParagraph(text, font);
    }

    @Override
    protected TXTAnchor createHTAnchor(boolean decapitalize, String text, TXTFont font) throws HTDocumentException {
        return new TXTAnchor(decapitalize, text, font);
    }

    @Override
    protected String decoratePageTextStart(List<TextChunk> pageTextChunks) {
        StringBuilder result = new StringBuilder();
        result.append(super.decoratePageTextStart(pageTextChunks));
        result.append(getLineSeparator());
        return result.toString();
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageSoundPath) {
        return Constants.EMPTY_STRING;
    }
}
