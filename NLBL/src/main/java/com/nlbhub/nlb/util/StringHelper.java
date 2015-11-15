/**
 * @(#)StringHelper.java
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
import com.nlbhub.nlb.api.TextChunk;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The StringHelper class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/20/13
 */
public class StringHelper {
    /**
     * This RegExp is used to extract multiple lines of text, separated by CR+LF or LF.
     * By default, ^ and $ match the start- and end-of-input respectively.
     * You'll need to enable MULTI-LINE mode with (?m), which causes ^ and $ to match the
     * start- and end-of-line
     */
    private static final Pattern LINE_PATTERN = Pattern.compile("(?m)^.*$");
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$([^\\s\\$]*)\\$");
    private final static String DELIMITER = ";";

    /**
     * This method is used in the NLB internal engine.
     *
     * @param pageText
     * @param visitedVars
     * @return
     */
    public static String replaceVariables(String pageText, Map<String, Object> visitedVars) {
        StringBuilder result = new StringBuilder();
        List<TextChunk> textChunks = getTextChunks(pageText);
        for (TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(textChunk.getText());
                    break;
                case VARIABLE:
                    Object mappedItem = visitedVars.get(textChunk.getText());
                    if (mappedItem != null) {
                        if (mappedItem instanceof Number) {
                            result.append(new DecimalFormat("#.###").format(mappedItem));
                        } else {
                            result.append(String.valueOf(mappedItem));
                        }
                    } else {
                        result.append("UNDEFINED");
                    }
                    break;
                case NEWLINE:
                    result.append(Constants.EOL_STRING);
                    break;
            }
        }
        return result.toString();
    }

    /**
     * TODO: this method REALLY needs JUnit
     * @return
     */
    public static List<TextChunk> getTextChunks(String text) {
        List<TextChunk> result = new ArrayList<>();
        Matcher matcher = LINE_PATTERN.matcher(text);
        boolean notFirst = false;
        while (matcher.find()) {
            if (notFirst) {
                TextChunk newlineChunk = new TextChunk();
                newlineChunk.setText(Constants.EMPTY_STRING);
                newlineChunk.setType(TextChunk.ChunkType.NEWLINE);
                result.add(newlineChunk);
            } else {
                notFirst = true;
            }

            final String line = matcher.group().trim();
            int start = 0;
            Matcher variableMatcher = VAR_PATTERN.matcher(line);
            while (variableMatcher.find()) {
                TextChunk textChunk = new TextChunk();
                final String variable = variableMatcher.group(1);
                textChunk.setText(line.substring(start, variableMatcher.start()));
                textChunk.setType(TextChunk.ChunkType.TEXT);
                result.add(textChunk);
                TextChunk variableChunk = new TextChunk();
                variableChunk.setText(variable);
                variableChunk.setType(TextChunk.ChunkType.VARIABLE);
                result.add(variableChunk);
                start = variableMatcher.end();
            }
            final int length = line.length();
            if (start < length) {
                TextChunk textChunk = new TextChunk();
                textChunk.setText(line.substring(start, length));
                textChunk.setType(TextChunk.ChunkType.TEXT);
                result.add(textChunk);
            }
        }
        return result;
    }

    public static boolean isEmpty(final String string) {
        return string == null || Constants.EMPTY_STRING.equals(string);
    }

    public static boolean notEmpty(final String string) {
        return !isEmpty(string);
    }

    public static boolean isEmpty(final MultiLangString multiLangString) {
        for (final String text : multiLangString.values()) {
            if (!isEmpty(text)) {
                return false;
            }
        }
        return true;
    }

    public static String formatSequence(final List<String> strings) {
        final StringBuilder sb = new StringBuilder();
        final int lastElemIndex = strings.size() - 1;
        if (lastElemIndex >= 0) {
            for (int i = 0; i < lastElemIndex; i++) {
                sb.append(strings.get(i)).append(DELIMITER);
            }
            sb.append(strings.get(lastElemIndex));
        }
        return sb.toString();
    }

    public static String[] getItems(final String sequenceString) {
        if (sequenceString == null) {
            return new String[0];
        } else {
            return sequenceString.split(DELIMITER);
        }
    }

    public static String createRepeatedString(int length, String fill) {
        return new String(new char[length]).replace("\0", fill);
    }
}
