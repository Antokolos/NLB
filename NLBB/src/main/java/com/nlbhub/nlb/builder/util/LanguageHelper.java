/**
 * @(#)LanguageHelper.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2026 Anton P. Kolosov
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
 * Copyright (c) 2026 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.builder.util;

import com.nlbhub.nlb.api.*;
import com.nlbhub.nlb.util.MultiLangString;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The LanguageHelper class fills and handles the language comboboxes of the NLB Builder dialogs.
 * The list of languages is not fixed: it consists of the predefined languages, the language of the book
 * and all languages which are already used in the book texts. New language can be added simply by typing
 * its code into the (editable) language combobox.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class LanguageHelper {
    private static final String[] PREDEFINED_LANGUAGES = {Constants.RU, Constants.EN, Constants.ZH};
    /**
     * Language code is used as a file name inside the book, so only safe codes are allowed,
     * e.g. "ru", "en", "zh", "zh-tw", "pt_br"
     */
    private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("[a-z]{2,3}([-_][a-z0-9]{1,8})*");
    private static Set<String> s_knownLanguages;

    private LanguageHelper() {
    }

    /**
     * Creates editable language combobox model containing all languages known for the given book
     * and selects the book language in it.
     */
    public static void initLanguageComboBox(final JComboBox<String> comboBox, final NonLinearBook nlb) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (String language : getAvailableLanguages(nlb)) {
            model.addElement(language);
        }
        comboBox.setEditable(true);
        comboBox.setModel(model);
        comboBox.setSelectedItem(nlb.getLanguage());
    }

    /**
     * Should be called from the language combobox action listener.
     *
     * @param comboBox        language combobox
     * @param currentLanguage language which was selected before
     * @param parent          parent component for the error message
     * @return normalized newly selected language; if it is invalid, currentLanguage is restored and returned
     */
    public static String processLanguageSelection(
            final JComboBox<String> comboBox,
            final String currentLanguage,
            final Component parent
    ) {
        final Object selectedItem = comboBox.getSelectedItem();
        final String language = validateLanguage(comboBox, selectedItem, parent);
        if (language == null) {
            comboBox.setSelectedItem(currentLanguage);
            return currentLanguage;
        }
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) comboBox.getModel();
        if (model.getIndexOf(language) < 0) {
            model.addElement(language);
        }
        if (!language.equals(selectedItem)) {
            comboBox.setSelectedItem(language);
        }
        return language;
    }

    /**
     * Checks the language entered into the language combobox. Languages already present in the combobox
     * (i.e. already used in the book) are always accepted, new languages should have a known ISO 639 code.
     *
     * @return trimmed lowercase language code, or null if the code is invalid (error message is shown in that case)
     */
    public static String validateLanguage(
            final JComboBox<String> comboBox,
            final Object value,
            final Component parent
    ) {
        final String language = normalize(value != null ? value.toString() : null);
        if (language != null) {
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) comboBox.getModel();
            if (model.getIndexOf(language) >= 0 || isKnownLanguage(language)) {
                return language;
            }
        }
        JOptionPane.showMessageDialog(
                parent,
                "Incorrect language code: '" + value + "'.\n"
                        + "Please use ISO 639 code, for example: ru, en, zh, de, fr, zh-tw",
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
        return null;
    }

    /**
     * @return trimmed lowercase language code, or null if the code has incorrect format
     */
    public static String normalize(final String language) {
        if (language == null) {
            return null;
        }
        final String result = language.trim().toLowerCase(Locale.ROOT);
        return isValid(result) ? result : null;
    }

    /**
     * Checks only the format of the language code, so that it can be safely used as a file name
     */
    public static boolean isValid(final String language) {
        return language != null && LANGUAGE_CODE_PATTERN.matcher(language).matches();
    }

    /**
     * @return true if the primary subtag of the language code (e.g. "zh" for "zh-tw")
     * is a known ISO 639-1 or ISO 639-2 language code
     */
    public static boolean isKnownLanguage(final String language) {
        final String primaryLanguage = language.split("[-_]", 2)[0];
        return getKnownLanguages().contains(primaryLanguage);
    }

    private static synchronized Set<String> getKnownLanguages() {
        if (s_knownLanguages == null) {
            Set<String> knownLanguages = new HashSet<>();
            for (String language : Locale.getISOLanguages()) {
                knownLanguages.add(language);
                knownLanguages.add(Locale.forLanguageTag(language).getISO3Language());
            }
            s_knownLanguages = knownLanguages;
        }
        return s_knownLanguages;
    }

    /**
     * @return predefined languages first, then all other languages used in the whole book (including modules),
     * sorted alphabetically
     */
    public static List<String> getAvailableLanguages(final NonLinearBook nlb) {
        NonLinearBook rootNLB = nlb;
        // The parent of the main book is DummyNLB, which has no pages, so we should stop before it
        while (rootNLB.getParentNLB() != null && !rootNLB.getParentNLB().isDummy()) {
            rootNLB = rootNLB.getParentNLB();
        }
        Set<String> usedLanguages = new TreeSet<>();
        addLanguage(usedLanguages, nlb.getLanguage());
        collectLanguages(rootNLB, usedLanguages, Collections.newSetFromMap(new IdentityHashMap<>()));
        List<String> result = new ArrayList<>(Arrays.asList(PREDEFINED_LANGUAGES));
        for (String language : usedLanguages) {
            if (!result.contains(language)) {
                result.add(language);
            }
        }
        return result;
    }

    private static void collectLanguages(
            final NonLinearBook nlb,
            final Set<String> languages,
            final Set<NonLinearBook> visited
    ) {
        if (nlb == null || !visited.add(nlb)) {
            return;
        }
        addLanguage(languages, nlb.getLanguage());
        for (Page page : nlb.getPages().values()) {
            addLanguages(languages, page.getCaptions());
            addLanguages(languages, page.getTexts());
            addLanguages(languages, page.getTraverseTexts());
            addLanguages(languages, page.getReturnTexts());
            addLanguages(languages, page.getAutowireInTexts());
            addLanguages(languages, page.getAutowireOutTexts());
            collectLinkLanguages(page, languages);
            collectLanguages(page.getModule(), languages, visited);
        }
        for (Obj obj : nlb.getObjs().values()) {
            addLanguages(languages, obj.getDisps());
            addLanguages(languages, obj.getTexts());
            addLanguages(languages, obj.getActTexts());
            addLanguages(languages, obj.getNouseTexts());
            collectLinkLanguages(obj, languages);
        }
    }

    private static void collectLinkLanguages(final NodeItem nodeItem, final Set<String> languages) {
        for (Link link : nodeItem.getLinks()) {
            addLanguages(languages, link.getTexts());
            addLanguages(languages, link.getAltTexts());
        }
    }

    private static void addLanguages(final Set<String> languages, final MultiLangString multiLangString) {
        if (multiLangString != null) {
            for (String language : multiLangString.keySet()) {
                addLanguage(languages, language);
            }
        }
    }

    private static void addLanguage(final Set<String> languages, final String language) {
        // Invalid keys (e.g. some garbage files in the book directory) are skipped
        if (isValid(language)) {
            languages.add(language);
        }
    }
}
