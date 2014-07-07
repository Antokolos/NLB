/**
 * @(#)FileManipulator.java
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
package com.nlbhub.nlb.util;

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.exception.NLBFileManipulationException;
import com.nlbhub.nlb.exception.NLBIOException;
import com.nlbhub.nlb.exception.NLBVCSException;
import com.nlbhub.nlb.vcs.VCSAdapter;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The FileManipulator class contains useful file operations.
 *
 * @author AKolosov
 * @version 1.0
 */
public class FileManipulator {
    /**
     * Maximum block size
     */
    private static final int BLOCK_SIZE = 1024;
    private VCSAdapter m_vcsAdapter;
    private File m_mainRoot;

    public FileManipulator(VCSAdapter vcsAdapter, File mainRoot) {
        m_vcsAdapter = vcsAdapter;
        m_mainRoot = mainRoot;
    }

    /* Constructors begin ==> */
    /* <== Constructors end. */
    /* Methods begin ==> */

    /**
     * Deletes recursively file and directories.
     *
     * @param file File or directory for delete
     * @return <code>true</code> if all files and subdirectories
     * successfully deleted.
     */
    public boolean deleteFileOrDir(File file) throws NLBFileManipulationException, NLBIOException {
        try {
            boolean ret = true;
            if (file.isDirectory()) {
                String[] children = file.list();
                for (int i = 0; i < children.length; i++) {
                    if (!deleteFileOrDir(new File(file, children[i]))) {
                        ret = false;
                    }
                }
            }

            // The directory is now empty so delete it
            final String path = getPathRelativeToMainRoot(file);
            VCSAdapter.Status status = m_vcsAdapter.getStatus(path);
            boolean removed = false;
            switch (status) {
                case Modified:
                case Clean:
                    removed = m_vcsAdapter.remove(path);
                    break;
                case Added:
                    m_vcsAdapter.reset(path);
                    break;
                case Removed:
                case Unknown:
                case VCS_Undefined:
                    // do nothing
                    break;
                case Missing:
                case Ignored:
                    throw new NLBFileManipulationException(
                            "Incorrect file status while deleting file with path = "
                                    + path
                                    + " from VCS: "
                                    + status
                    );
            }
            if (!removed) {
                removed = file.delete();
            }
            return removed && ret;
        } catch (NLBVCSException e) {
            throw new NLBFileManipulationException("Error while deleting directory", e);
        } catch (IOException e) {
            throw new NLBIOException("Error while deleting directory", e);
        }
    }

    /**
     * Returns a specified file as a string
     *
     * @param fName file name from classpath.
     * @return String representation for the specified file or
     * <tt>null</tt> if file is not found.
     */
    private static String getFileAsString(String fName) {

        InputStream strm = (
                FileManipulator.class.getClassLoader().getResourceAsStream(fName)
        );

        return getFileAsString(strm);
    }

    public static String getRequiredFileAsString(
            final File rootDir,
            final String fileName,
            final String errorMessage
    ) throws NLBIOException {
        try {
            InputStream fis = null;
            try {
                final File file = new File(rootDir, fileName);
                if (!file.exists()) {
                    throw new NLBIOException(errorMessage);
                }
                fis = new FileInputStream(file);
                return FileManipulator.getFileAsString(fis);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }
    }

    /**
     * This method is similar to getRequiredFileAsString(), but it does not throw an exception if
     * file does not exist. In such case default value is returned.
     *
     * @param rootDir
     * @param fileName
     * @return
     * @throws NLBIOException
     */
    public static String getOptionalFileAsString(
            final File rootDir,
            final String fileName,
            final String defaultValue
    ) throws NLBIOException {
        try {
            InputStream fis = null;
            try {
                final File file = new File(rootDir, fileName);
                if (!file.exists()) {
                    return defaultValue;
                }
                fis = new FileInputStream(file);
                return FileManipulator.getFileAsString(fis);
            } finally {
                if (fis != null) {
                    fis.close();
                }
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }
    }

    /**
     * Returns a specified file as a string
     *
     * @param strm input file stream.
     * @return String representation for the specified file or
     * <tt>null</tt> if file is not found.
     */
    private static String getFileAsString(InputStream strm) {

        if (strm != null) {
            StringBuilder sb = new StringBuilder();

            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(strm, "UTF-8"));
                String str;
                if ((str = in.readLine()) != null) {
                    sb.append(str);
                }
                while ((str = in.readLine()) != null) {
                    sb.append(Constants.EOL_STRING).append(str);
                }
            } catch (IOException e) {
                return null;
            }

            return sb.toString();
        } else {
            return null;
        }
    }

    /**
     * Copies specified file to the target location.
     *
     * @param sourcePath source file path.
     * @param targetPath destination file path.
     * @throws IOException if an I/O error occurs.
     */
    private void transfer(
            File sourcePath, File targetPath
    ) throws IOException {

        FileInputStream sourceStream = new FileInputStream(sourcePath);

        try {
            writeFile(targetPath, sourceStream);
        } finally {
            sourceStream.close();
        }
    }

    /**
     * Transfers content from input stream to the output stream.
     *
     * @param input  input stream whose content is to be transferred.
     * @param output output stream output which content is to be transferred.
     * @throws IOException if an I/O error occurs.
     */
    private static void transfer(
            InputStream input, OutputStream output
    ) throws IOException {
        byte[] buffer = new byte[BLOCK_SIZE];
        int bytesRead;

        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Writes content from the specified input stream to the specified file.
     *
     * @param file  the file to be opened for writing.
     * @param input input stream whose content is to be written to the file.
     * @throws IOException if an I/O error occurs.
     */
    private void writeFile(
            File file, InputStream input
    ) throws IOException {
        FileOutputStream output = new FileOutputStream(file);

        try {
            transfer(input, output);
        } finally {
            output.close();
        }
    }

    /**
     * This method differs from writeOptionalString in that it does not have default content and always writes files, even
     * empty ones.
     *
     * @param rootDir
     * @param fileName
     * @param content
     * @throws NLBIOException
     * @throws NLBFileManipulationException
     * @throws NLBVCSException
     */
    public void writeRequiredString(
            final File rootDir,
            final String fileName,
            final String content
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        ByteArrayInputStream inputStream = null;
        try {
            try {
                final File file = new File(rootDir, fileName);
                final boolean newFile = !file.exists();
                if (content != null) {
                    inputStream = (
                            new ByteArrayInputStream(content.getBytes(Constants.UNICODE_ENCODING))
                    );
                    writeFile(file, inputStream);
                    addToVCS(file, newFile);
                } else {
                    createFile(file, "Cannot create file with name " + fileName);
                }
            } finally {
                if (inputStream != null) inputStream.close();
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }
    }

    public void writeOptionalString(
            final File rootDir,
            final String fileName,
            final String content,
            final String defaultContent
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        ByteArrayInputStream inputStream = null;
        try {
            try {
                final File file = new File(rootDir, fileName);
                final boolean newFile = !file.exists();
                if (content != null) {
                    if (content.equals(defaultContent)) {
                        if (!newFile) {
                            // remove existing file if its content contains default data
                            deleteFileOrDir(file);
                        }
                    } else {
                        inputStream = (
                                new ByteArrayInputStream(content.getBytes(Constants.UNICODE_ENCODING))
                        );
                        writeFile(file, inputStream);
                        addToVCS(file, newFile);
                    }
                } else {
                    // if default content is empty, do nothing
                    if (!StringHelper.isEmpty(defaultContent)) {
                        createFile(file, "Cannot create file with name " + fileName);
                    }
                }
            } finally {
                if (inputStream != null) inputStream.close();
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }
    }

    public void writeOptionalMultiLangString(
            final File mlsRootDir,
            final MultiLangString content,
            final MultiLangString defaultContent
    ) throws NLBIOException, NLBFileManipulationException, NLBVCSException {
        ByteArrayInputStream inputStream = null;
        try {
            if (mlsRootDir.exists() && !mlsRootDir.isDirectory()) {
                // TODO: Warning! Deleting old file when writing! This is done for backward compatibility and can be removed when all books will be converted.
                deleteFileOrDir(mlsRootDir);
            }
            if (!mlsRootDir.exists()) {
                if (!mlsRootDir.mkdir()) {
                    throw new NLBFileManipulationException(
                            "Cannot create MultiLangString root: " + mlsRootDir.getCanonicalPath()
                    );
                }
            }
            for (String key : content.keySet()) {
                try {
                    final File file = new File(mlsRootDir, key);
                    final boolean newFile = !file.exists();
                    if (content.equalsAs(key, defaultContent)) {
                        if (!newFile) {
                            // remove existing file if its content contains default data
                            deleteFileOrDir(file);
                        }
                    } else {
                        inputStream = (
                                new ByteArrayInputStream(content.get(key).getBytes(Constants.UNICODE_ENCODING))
                        );
                        writeFile(file, inputStream);
                        addToVCS(file, newFile);
                    }
                } finally {
                    if (inputStream != null) inputStream.close();
                }
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }
    }

    public static MultiLangString readOptionalMultiLangString(
            final File mlsRootDir,
            final MultiLangString defaultValue
    ) throws NLBIOException {
        if (!mlsRootDir.exists()) {
            return MultiLangString.createCopy(defaultValue);
        }
        // Create copy of default value and then overwrite it with actual data, if it exists
        MultiLangString result = MultiLangString.createCopy(defaultValue);
        try {
            if (mlsRootDir.isDirectory()) {
                String[] langKeys = mlsRootDir.list();
                if (langKeys != null) {
                    // if (langKeys.length == 0) then copy of default value will be returned
                    for (String langKey : langKeys) {
                        InputStream fis = null;
                        try {
                            final File file = new File(mlsRootDir, langKey);
                            fis = new FileInputStream(file);
                            result.put(langKey, FileManipulator.getFileAsString(fis));
                        } finally {
                            if (fis != null) {
                                fis.close();
                            }
                        }
                    }
                } else {
                    throw new NLBIOException("Error while listing directory");
                }
            } else {
                // TODO: This code is provided for backward compatibility and should be removed, when all books will be converted
                InputStream fis = null;
                try {
                    fis = new FileInputStream(mlsRootDir);
                    result.put(NonLinearBook.DEFAULT_LANGUAGE, FileManipulator.getFileAsString(fis));
                } finally {
                    if (fis != null) {
                        fis.close();
                    }
                }
            }
        } catch (IOException e) {
            throw new NLBIOException("IOException occured", e);
        }

        return result;
    }

    public void createDir(
            final File dir,
            final String errorMessage
    ) throws NLBIOException, NLBFileManipulationException {
        try {
            if (!dir.exists()) {
                if (!dir.mkdir()) {
                    throw new NLBIOException(
                            errorMessage
                    );
                }
                addToVCS(dir, true);
            }
        } catch (NLBVCSException e) {
            throw new NLBFileManipulationException(
                    "Error while creating directory", e
            );
        } catch (IOException e) {
            throw new NLBIOException(
                    "Error while creating directory", e
            );
        }
    }

    private void createFile(
            final File file,
            final String errorMessage
    ) throws NLBIOException, NLBFileManipulationException {
        try {
            if (!file.exists()) {
                file.createNewFile();
                addToVCS(file, true);
            }
        } catch (IOException e) {
            throw new NLBIOException(errorMessage, e);
        } catch (NLBVCSException e) {
            throw new NLBFileManipulationException(
                    "Error while creating file", e
            );
        }
    }

    private void addToVCS(
            final File file,
            final boolean isNewFile
    ) throws IOException, NLBVCSException, NLBFileManipulationException {
        if (file.isDirectory() && !m_vcsAdapter.getDirAddFlag()) {
            // Directories does not count as files in some VCSs, such as Mercurial
            return;
        }
        final String path = getPathRelativeToMainRoot(file);
        if (isNewFile) {
            VCSAdapter.Status status = m_vcsAdapter.getStatus(path);
            switch (status) {
                case Unknown:
                case VCS_Undefined:
                    m_vcsAdapter.add(path);
                    break;
                case Added:
                    // do nothing
                    break;
                case Removed:
                case Modified:
                case Clean:
                case Missing:
                case Ignored:
                    throw new NLBFileManipulationException(
                            "Incorrect file status while adding file with path = "
                                    + path
                                    + " to VCS: "
                                    + status
                    );
            }
        } else {
            if (m_vcsAdapter.getAddModifiedFilesFlag()) {
                m_vcsAdapter.add(path);
            }
        }
    }

    private String getPathRelativeToMainRoot(File file) throws IOException {
        Path pathAbsolute = Paths.get(file.getCanonicalPath());
        Path pathBase = Paths.get(m_mainRoot.getCanonicalPath());
        Path pathRelative = pathBase.relativize(pathAbsolute);
        return pathRelative.toString();
    }
}
