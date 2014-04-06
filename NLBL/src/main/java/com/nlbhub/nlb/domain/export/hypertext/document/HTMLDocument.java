/**
 * @(#)HTMLDocument.java
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
package com.nlbhub.nlb.domain.export.hypertext.document;

import com.nlbhub.nlb.exception.HTDocumentException;

import java.io.*;

/**
 * The HTMLDocument class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/9/13
 */
public class HTMLDocument implements HTDocument<HTMLParagraph> {
    private String m_encoding;
    private StringBuilder m_textBuilder = new StringBuilder();
    private FileOutputStream m_fileOutputStream;
    private String m_lineSeparator;
    private static final String SYSTEM_SEPARATOR = System.getProperty("line.separator");

    public HTMLDocument(String encoding, String lineSeparator) {
        m_encoding = encoding;
        m_lineSeparator = lineSeparator;
    }

    @Override
    public void initWriter(File targetFile) throws FileNotFoundException, HTDocumentException {
        m_fileOutputStream = new FileOutputStream(targetFile);
    }

    @Override
    public void open() {
        m_textBuilder.append("<html>").append(SYSTEM_SEPARATOR);
        m_textBuilder.append(
            "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"></head>"
        );
        m_textBuilder.append(SYSTEM_SEPARATOR).append("<body>").append(SYSTEM_SEPARATOR);
    }

    @Override
    public void close() throws IOException {
        m_textBuilder.append(m_lineSeparator).append("</body>").append(SYSTEM_SEPARATOR);
        m_textBuilder.append("</html>");
        final byte[] bytes = m_textBuilder.toString().getBytes(m_encoding);
        m_fileOutputStream.write(bytes);
    }

    @Override
    public void add(HTMLParagraph paragraph) throws HTDocumentException {
        m_textBuilder.append(paragraph.toString()).append(SYSTEM_SEPARATOR);
    }
}
