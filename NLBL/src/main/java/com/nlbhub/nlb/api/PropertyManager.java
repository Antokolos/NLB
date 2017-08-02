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
    private static PropertiesBean m_properties;

    public static void init() throws IOException {
        m_properties = readProperties();
    }

    public static PropertiesBean getProperties() {
        return m_properties;
    }

    private static PropertiesBean readProperties() throws IOException {
        PropertiesBean result = new PropertiesBean();
        Properties prop = new Properties();
        InputStream input = null;

        try {
            File configDir = new File("cfg");
            if (!configDir.exists()) {
                throw new IOException("Config dir 'cfg' does not exist!");
            }
            File configFile = new File(configDir, "config.properties");
            if (!configFile.exists()) {
                throw new IOException("Config file 'cfg/config.properties' does not exist!");
            }

            input = new FileInputStream(configFile);

            // load a properties file
            prop.load(input);

            String gameActText = getTrimmedProperty(prop, "nlb.export.parameters.game.act.text");
            String gameInvText = getTrimmedProperty(prop, "nlb.export.parameters.game.inv.text");
            String gameNouseText = getTrimmedProperty(prop, "nlb.export.parameters.game.nouse.text");
            String gameForcedsc = getTrimmedProperty(prop, "nlb.export.parameters.game.forcedsc");
            String setLookAndFeel = getTrimmedProperty(prop, "nlb.general.parameters.default.set-look-and-feel");
            String lookAndFeel = getTrimmedProperty(prop, "nlb.general.parameters.default.look-and-feel");
            String findUnusualQuotes = getTrimmedProperty(prop, "nlb.general.parameters.default.find-unusual-quotes");
            String convertpng2jpg = getTrimmedProperty(prop, "media.export.parameters.default.convertpng2jpg");
            String quality = getTrimmedProperty(prop, "media.export.parameters.default.quality");
            result.setSetLookAndFeel("true".equalsIgnoreCase(setLookAndFeel));
            result.setLookAndFeel(lookAndFeel);
            result.setFindUnusualQuotes("true".equalsIgnoreCase(findUnusualQuotes));
            result.setConvertPNG2JPG("true".equalsIgnoreCase(convertpng2jpg));
            result.setQuality(Integer.parseInt(quality));
            result.setGameActText(gameActText);
            result.setGameInvText(gameInvText);
            result.setGameNouseText(gameNouseText);
            result.setGameForcedsc("true".equalsIgnoreCase(gameForcedsc));
            return result;
        } finally {
            if (input != null) {
                input.close();
            }
        }
    }

    private static String getTrimmedProperty(Properties prop, String propertyName) {
        String property = prop.getProperty(propertyName);
        if (property == null) {
            return Constants.EMPTY_STRING;
        }
        return property.trim();
    }
}
