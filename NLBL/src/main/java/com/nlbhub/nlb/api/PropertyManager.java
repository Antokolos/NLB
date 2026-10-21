/**
 * @(#)PropertiesBean.java
 *
 * This file is part of the Non-Linear Book project.
 * Copyright (c) 2012-2016 Anton P. Kolosov
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
 * Copyright (c) 2016 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.api;

import com.nlbhub.nlb.api.config.*;
import com.nlbhub.nlb.exception.NLBJAXBException;
import com.nlbhub.nlb.util.JaxbMarshaller;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author Anton P. Kolosov
 * @version 1.0
 */
public class PropertyManager {
    private static final JaxbMarshaller CONFIGS_MARSHALLER = (
            new JaxbMarshaller(
                    Settings.class,
                    Config.class,
                    General.class,
                    Export.class,
                    Text.class
            )
    );
    private static Settings m_settings;

    public static void init() throws IOException, NLBJAXBException {
        m_settings = readSettings();
    }

    public static Settings getSettings() {
        return m_settings;
    }

    private static Settings readSettings() throws IOException, NLBJAXBException {
        File configDir = new File("cfg");
        if (!configDir.exists()) {
            throw new IOException("Config dir 'cfg' does not exist!");
        }
        File configFile = new File(configDir, "config.xml");
        if (!configFile.exists()) {
            throw new IOException("Config file 'cfg/config.xml' does not exist!");
        }
        return readSettings(configFile);
    }

    /**
     * Читает настройки из указанного файла (не трогая текущие настройки приложения): так ими
     * пользуются плеер и тесты, которым не подходит каталог cfg в текущем каталоге
     */
    public static Settings readSettings(File configFile) throws IOException, NLBJAXBException {
        try (InputStream input = new FileInputStream(configFile)) {
            return (Settings) CONFIGS_MARSHALLER.unmarshal(input);
        }
    }
}
