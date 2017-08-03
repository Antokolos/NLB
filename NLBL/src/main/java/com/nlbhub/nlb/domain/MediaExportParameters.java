/**
 * @(#)MediaExportParameters.java
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
 * Copyright (c) 2012 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.domain;

import com.nlbhub.nlb.api.PropertyManager;

/**
 * The MediaExportParameters class represents parameters used when saving media files during export of the scheme
 * to some end format (such as INSTEAD game).
 *
 * @author Anton P. Kolosov
 * @version 1.0 8/9/12
 */
public class MediaExportParameters {
    public enum Preset {CUSTOM, DEFAULT, NOCHANGE, COMPRESSED};
    private static final MediaExportParameters NOCHANGE = new MediaExportParameters(Preset.NOCHANGE, false, 0);
    private static final MediaExportParameters COMPRESSED = new MediaExportParameters(Preset.COMPRESSED, true, 80);
    private static final MediaExportParameters DEFAULT = (
            new MediaExportParameters(
                    Preset.DEFAULT,
                    PropertyManager.getSettings().getDefaultConfig().getExport().isConvertpng2jpg(),
                    PropertyManager.getSettings().getDefaultConfig().getExport().getQuality()
            )
    );
    private Preset m_preset = Preset.CUSTOM;
    private boolean m_convertPNG2JPG;
    private int m_quality;

    public static MediaExportParameters fromPreset(Preset preset) {
        switch (preset) {
            case NOCHANGE:
                return MediaExportParameters.NOCHANGE;
            case COMPRESSED:
                return MediaExportParameters.COMPRESSED;
            default:
                return MediaExportParameters.DEFAULT;
        }
    }

    public static MediaExportParameters getDefault() {
        return DEFAULT;
    }

    /*
    public MediaExportParameters(boolean convertPNG2JPG, int quality) {
        m_preset = Preset.CUSTOM;
        m_convertPNG2JPG = convertPNG2JPG;
        m_quality = quality;
    }
    */

    private MediaExportParameters(Preset preset, boolean convertPNG2JPG, int quality) {
        m_preset = preset;
        m_convertPNG2JPG = convertPNG2JPG;
        m_quality = quality;
    }

    public Preset getPreset() {
        return m_preset;
    }

    public boolean isConvertPNG2JPG() {
        return m_convertPNG2JPG;
    }

    public int getQuality() {
        return m_quality;
    }
}
