/**
 * @(#)ResourceManager.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2015 Anton P. Kolosov
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
 * Copyright (c) 2015 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.exception.NLBIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ResourceManager class
 *
 * @author Anton P. Kolosov
 */
public class ResourceManager {

    private static final String VNSTEAD = "vnstead";

    public static void exportBundledFiles(File targetDir) throws NLBIOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Map<String, List<File>> resourceFolderFiles = getResourceFolderFiles(loader);
        for (Map.Entry<String, List<File>> resourceFileEntry : resourceFolderFiles.entrySet()) {
            exportBundledFiles(resourceFileEntry, targetDir);
        }
    }

    private static Map<String, List<File>> getResourceFolderFiles(ClassLoader loader) throws NLBIOException {
        File resDir = new File("res");
        if (!resDir.exists()) {
            throw new NLBIOException("Resources dir 'res' does not exist!");
        }
        return getAllChildren(new File(resDir, VNSTEAD), "");
    }

    private static Map<String, List<File>> getAllChildren(File parent, String parentPath) {
        Map<String, List<File>> result = new HashMap<>();
        File[] files = parent.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                String key = StringHelper.notEmpty(parentPath) ? parentPath + "/" + file.getName() : file.getName();
                result.putAll(getAllChildren(file, key));
            } else {
                List<File> filesList = result.get(parentPath);
                if (filesList == null) {
                    filesList = new ArrayList<>();
                    result.put(parentPath, filesList);
                }
                filesList.add(file);
            }
        }
        return result;
    }

    private static void exportBundledFiles(
            Map.Entry<String, List<File>> resourceFileEntry,
            File targetDir
    ) throws NLBIOException {
        String key = resourceFileEntry.getKey();
        boolean hasParentFolder = StringHelper.notEmpty(key);
        File resourceFileParent = hasParentFolder ? new File(targetDir, key) : targetDir;
        if (hasParentFolder) {
            resourceFileParent.mkdirs();
        }
        for (File resourceFile : resourceFileEntry.getValue()) {
            exportBundledFile(resourceFileParent, resourceFile);
        }
    }

    private static void exportBundledFile(
            File resourceFileParent,
            File resourceFile
    ) throws NLBIOException {
        File file = new File(resourceFileParent, resourceFile.getName());
        try {
            try (InputStream is = new FileInputStream(resourceFile)) {
                FileManipulator.writeFile(file, is);
            }
        } catch (IOException e) {
            throw new NLBIOException("Error exporting bundled file", e);
        }
    }
}
