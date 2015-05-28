/**
 * @(#)PDFExportManager.java
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
package com.nlbhub.nlb.domain.export.hypertext;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.ImagePathData;
import com.nlbhub.nlb.domain.export.SoundPathData;
import com.nlbhub.nlb.domain.export.hypertext.document.*;
import com.nlbhub.nlb.exception.HTDocumentException;
import com.nlbhub.nlb.exception.NLBExportException;

import java.util.List;

/**
 * The PDFExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/5/13
 */
public class PDFExportManager extends HypertextExportManager<PDFParagraph, PDFAnchor, PDFFont> {
    public PDFExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    @Override
    protected HTDocument<PDFParagraph> createDocument(String encoding, String lineSeparator) {
        return new PDFDocument(encoding, lineSeparator);
    }

    @Override
    protected PDFFont createParaFont() throws HTDocumentException {
        return new PDFFont((float) 10.0, HTFont.Style.REGULAR, new RGBColor(0, 0, 0));
    }

    @Override
    protected PDFFont createLinkFont() throws HTDocumentException {
        return new PDFFont((float) 10.0, HTFont.Style.UNDERLINE, new RGBColor(0, 0, 255));
    }

    @Override
    protected PDFParagraph createHTParagraph(String text, PDFFont font) throws HTDocumentException {
        return new PDFParagraph(text, font);
    }

    @Override
    protected PDFAnchor createHTAnchor(boolean decapitalize, String text, PDFFont font) throws HTDocumentException {
        return new PDFAnchor(decapitalize, text, font);
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground) {
        // TODO: implement images for PDF
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX) {
        // TODO: implement and use
        return Constants.EMPTY_STRING;
    }
}
