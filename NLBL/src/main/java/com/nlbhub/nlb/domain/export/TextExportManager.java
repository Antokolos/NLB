/**
 * @(#)TextExportManager.java
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

import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBConsistencyException;
import com.nlbhub.nlb.exception.NLBExportException;

import java.io.*;
import java.util.List;

/**
 * The TextExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/5/13
 */
public abstract class TextExportManager extends ExportManager {

    protected TextExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    public void exportToFile(
        final File targetFile
    ) throws NLBExportException {
        OutputStream outputStream = null;
        try {
            try {
                NLBBuildingBlocks nlbBlocks = createNLBBuildingBlocks();
                final byte[] bytes = generateNLBText(nlbBlocks).getBytes(getEncoding());
                outputStream = new FileOutputStream(targetFile);
                outputStream.write(bytes);
            }  finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        } catch (FileNotFoundException | UnsupportedEncodingException | NLBConsistencyException e) {
            throw new NLBExportException("Error while exporting NLB", e);
        } catch (IOException e) {
            throw new NLBExportException("Error while exporting NLB", e);
        }
    }

    protected String generateNLBText(final NLBBuildingBlocks nlbBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        final List<ObjBuildingBlocks> objsBlocks = nlbBlocks.getObjsBuildingBlocks();
        final List<PageBuildingBlocks> pagesBlocks = nlbBlocks.getPagesBuildingBlocks();
        stringBuilder.append(generatePreambleText());
        for (int i = 0; i < objsBlocks.size(); i++) {
            stringBuilder.append(generateObjText(objsBlocks.get(i)));
        }
        for (int i = 0; i < pagesBlocks.size(); i++) {
            stringBuilder.append(generatePageText(pagesBlocks.get(i)));
        }
        stringBuilder.append(generateTrailingText());
        return stringBuilder.toString();
    }

    protected abstract String generatePreambleText();

    protected abstract String generateObjText(final ObjBuildingBlocks objBlocks);

    protected abstract String generatePageText(final PageBuildingBlocks pageBlocks);

    protected abstract String generateTrailingText();
}
