/**
 * @(#)HTMLExportManager.java
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

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.domain.export.hypertext.document.*;
import com.nlbhub.nlb.exception.HTDocumentException;
import com.nlbhub.nlb.exception.NLBExportException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The HTMLExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/6/13
 */
public class HTMLExportManager extends HypertextExportManager<HTMLParagraph, HTMLAnchor, HTMLFont> {
    public HTMLExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String getLineSeparator() {
        return "<br/>" + System.getProperty("line.separator");
    }

    @Override
    protected HTDocument<HTMLParagraph> createDocument(String encoding, String lineSeparator) {
        return new HTMLDocument(encoding, lineSeparator);
    }

    @Override
    protected HTMLFont createParaFont() throws HTDocumentException {
        return new HTMLFont();
    }

    @Override
    protected HTMLFont createLinkFont() throws HTDocumentException {
        return new HTMLFont();
    }

    @Override
    protected HTMLParagraph createHTParagraph(String text, HTMLFont font) throws HTDocumentException {
        return new HTMLParagraph(text, font);
    }

    @Override
    protected HTMLAnchor createHTAnchor(boolean decapitalize, String text, HTMLFont font) throws HTDocumentException {
        return new HTMLAnchor(decapitalize, text, font);
    }

    @Override
    protected String decoratePageTextStart(String pageText) {
        StringBuilder result = new StringBuilder();
        /*
         * This RegExp is used to extract multiple lines of text, separated by CR+LF or LF.
         * By default, ^ and $ match the start- and end-of-input respectively.
         * You'll need to enable MULTI-LINE mode with (?m), which causes ^ and $ to match the
         * start- and end-of-line
         */
        Pattern pattern = Pattern.compile("(?m)^.*$");
        Matcher matcher = pattern.matcher(pageText);
        while (matcher.find()) {
            final String line = matcher.group().trim();
            result.append(line).append(getLineSeparator());
        }
        result.append(getLineSeparator());
        return result.toString();
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        // TODO: implement and use
        throw new UnsupportedOperationException("This operation is unsupported");
    }
}
