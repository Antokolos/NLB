/**
 * @(#)STEADExportManager.java
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

import com.nlbhub.nlb.api.Constants;
import com.nlbhub.nlb.api.Obj;
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.api.Theme;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The STEADExportManager class
 *
 * @author Anton P. Kolosov
 * @version 1.0 12/10/13
 */
public class STEADExportManager extends TextExportManager {
    private static final String GLOBAL_VAR_PREFIX = "_";
    private static final String LINE_SEPARATOR = System.getProperty("line.separator");
    private static final Pattern STEAD_OBJ_PATTERN = Pattern.compile("\\{(.*)\\}");

    /**
     * Enable comments in the generated text
     */
    private static final boolean ENABLE_COMMENTS = true;

    private boolean m_technicalInstance = false;
    private STEADExportManager m_vnsteadExportManager;

    public STEADExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
        m_vnsteadExportManager = new VNSTEADExportManager(nlb, encoding, true);
    }

    public STEADExportManager(NonLinearBookImpl nlb, String encoding, boolean technicalInstance) throws NLBExportException {
        super(nlb, encoding);
        m_technicalInstance = technicalInstance;
        m_vnsteadExportManager = null;
    }

    protected boolean isVN(Theme theme) {
        return !m_technicalInstance && theme == Theme.VN;
    }

    @Override
    protected String generatePreambleText(NLBBuildingBlocks nlbBuildingBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(getGameFileHeader(nlbBuildingBlocks));

        stringBuilder.append("--package.cpath = './?.so'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'luapassing'").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        stringBuilder.append("require 'prefs'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'xact'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'nouse'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'hideinv'").append(LINE_SEPARATOR);
        stringBuilder.append("--require 'para'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'dash'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'snapshots'").append(LINE_SEPARATOR);
        String lang = nlbBuildingBlocks.getLang();
        if (Constants.RU.equalsIgnoreCase(lang)) {
            // quotes module should be used only for russian language
            stringBuilder.append("require 'quotes'").append(LINE_SEPARATOR);
        }
        stringBuilder.append("require 'theme'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'timer'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/nlb'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/fonts'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/paginator'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/vn'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/gobj'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'dice/modules/big_pig'").append(LINE_SEPARATOR);
        stringBuilder.append("game.codepage='UTF-8';").append(LINE_SEPARATOR);
        stringBuilder.append("stead.scene_delim = '^';").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);

        if (StringHelper.isEmpty(nlbBuildingBlocks.getGameActText())) {
            stringBuilder.append("game.act = function() return true; end;").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append("game.act = function() nlb:curloc().lasttext = '").append(nlbBuildingBlocks.getGameActText()).append("'; p(nlb:curloc().lasttext); nlb:curloc().wastext = true; end;").append(LINE_SEPARATOR);
        }
        if (StringHelper.isEmpty(nlbBuildingBlocks.getGameInvText())) {
            stringBuilder.append("game.inv = function() return true; end;").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append("game.inv = function() nlb:curloc().lasttext = '").append(nlbBuildingBlocks.getGameInvText()).append("'; p(nlb:curloc().lasttext); nlb:curloc().wastext = true; end;").append(LINE_SEPARATOR);
        }
        if (StringHelper.isEmpty(nlbBuildingBlocks.getGameNouseText())) {
            stringBuilder.append("game.nouse = function() return true; end;").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append("game.nouse = function() nlb:curloc().lasttext = '").append(nlbBuildingBlocks.getGameNouseText()).append("'; p(nlb:curloc().lasttext); nlb:curloc().wastext = true; end;").append(LINE_SEPARATOR);
        }
        stringBuilder.append("game.forcedsc = ").append(String.valueOf(nlbBuildingBlocks.isGameForcedsc())).append(";").append(LINE_SEPARATOR);

        /*
        stringBuilder.append("f1 = font(nlb:theme_root() .. 'fonts/STEINEMU.ttf', 32);").append(LINE_SEPARATOR);
        stringBuilder.append("").append(LINE_SEPARATOR);
        stringBuilder.append("function pname(n, c)").append(LINE_SEPARATOR);
        stringBuilder.append("    return function()").append(LINE_SEPARATOR);
        stringBuilder.append("        pn(img 'blank:8x1',f1:txt(n, c, 1))").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);
        */

        stringBuilder.append("paginator.delim = '\\n[ \\t]*\\n'").append(LINE_SEPARATOR);

        stringBuilder.append("function exec(s)").append(LINE_SEPARATOR);
        stringBuilder.append("    p('$'..s:gsub('\\n', '^')..'$^^')").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);

        stringBuilder.append("function init()").append(LINE_SEPARATOR);
        stringBuilder.append("    statsAPI.init();").append(LINE_SEPARATOR);
        stringBuilder.append("    if dice then").append(LINE_SEPARATOR);
        stringBuilder.append("        paginator:set_onproceed(function()").append(LINE_SEPARATOR);
        stringBuilder.append("            dice:hide(true);").append(LINE_SEPARATOR);
        stringBuilder.append("            -- Use code below if you want smooth rollout animation and text change on next step").append(LINE_SEPARATOR);
        stringBuilder.append("            --if dice:hide() then").append(LINE_SEPARATOR);
        stringBuilder.append("            --    return").append(LINE_SEPARATOR);
        stringBuilder.append("            --end").append(LINE_SEPARATOR);
        stringBuilder.append("        end);").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        //stringBuilder.append(getThemeInit());  // TODO: moved to vn.lua, remove later if everything is OK
        stringBuilder.append("    vn:scene(nil);").append(LINE_SEPARATOR);
        //stringBuilder.append("    vn.fading = 8").append(LINE_SEPARATOR); // TODO: moved to vn.lua, remove later if everything is OK
        stringBuilder.append("    nlbticks = stead.ticks();").append(LINE_SEPARATOR);
        stringBuilder.append(generateVarsInitBlock(nlbBuildingBlocks));
        stringBuilder.append("end").append(LINE_SEPARATOR);
        stringBuilder.append(generateSysObjectsBlock(nlbBuildingBlocks));
        return stringBuilder.toString();
    }

    // TODO: moved to vn.lua, remove later if everything is OK
    protected String getThemeInit() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("    if vn:in_vnr() then").append(getLineSeparator());
        stringBuilder.append("        vn:turnoff();").append(getLineSeparator());  // Needed for dofile("theme_vn.lua"), because otherwise it will not switch the theme
        stringBuilder.append("        nlb:theme_switch(\"theme_vn.lua\");").append(getLineSeparator());  // Reset theme to VN (default is non-VN)
        stringBuilder.append("    else").append(getLineSeparator());
        stringBuilder.append("        vn:turnon();").append(getLineSeparator());  // Needed for dofile("theme_standard.lua"), because otherwise it will not switch the theme
        stringBuilder.append("        nlb:theme_switch(\"theme_standard.lua\");").append(getLineSeparator());  // Reset theme to non-VN
        stringBuilder.append("    end").append(getLineSeparator());
        return stringBuilder.toString();
    }

    protected String generateSysObjectsBlock(NLBBuildingBlocks nlbBuildingBlocks) {
        String version = StringHelper.notEmpty(nlbBuildingBlocks.getVersion()) ? nlbBuildingBlocks.getVersion() : "0.1";
        StringBuilder result = new StringBuilder();
        result.append(LINE_SEPARATOR).append("_version_obj = gobj {").append(LINE_SEPARATOR)
                .append("    nam = 'version_obj',").append(LINE_SEPARATOR)
                .append("    system_type = true,").append(LINE_SEPARATOR)
                .append("    pic = 'gfx/version.png',").append(LINE_SEPARATOR)
                .append("    txtfn = function(s) return { [1] = {['text'] = '").append(version).append("', ['color'] = 'white' } }; end,").append(LINE_SEPARATOR)
                .append("    eff = 'left-top@0,0',").append(LINE_SEPARATOR)
                .append("    iarm = {[0] = {0, 16}}").append(LINE_SEPARATOR)
                .append(LINE_SEPARATOR).append("}")
                .append(LINE_SEPARATOR);
        return result.toString();
    }

    protected String getGameFileHeader(NLBBuildingBlocks nlbBuildingBlocks) {
        StringBuilder result = new StringBuilder();
        String title = StringHelper.notEmpty(nlbBuildingBlocks.getTitle()) ? nlbBuildingBlocks.getTitle() : "NLBB_" + new Date().toString();
        String version = StringHelper.notEmpty(nlbBuildingBlocks.getVersion()) ? nlbBuildingBlocks.getVersion() : "0.1";
        String author = StringHelper.notEmpty(nlbBuildingBlocks.getAuthor()) ? nlbBuildingBlocks.getAuthor() : "Unknown";
        result.append("--$Name:").append(title).append("$").append(LINE_SEPARATOR);
        result.append("--$Version:").append(version).append("$").append(LINE_SEPARATOR);
        result.append("--$Author:").append(author).append("$").append(LINE_SEPARATOR);
        result.append("instead_version '2.3.0'").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        return result.toString();
    }

    protected String generateVarsInitBlock(NLBBuildingBlocks nlbBuildingBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        String lang = nlbBuildingBlocks.getLang();
        stringBuilder.append("    _export_lang = '").append(lang).append("';").append(LINE_SEPARATOR);
        stringBuilder.append("    _syscall_showmenubtn:act();").append(LINE_SEPARATOR);
        if (Constants.RU.equalsIgnoreCase(lang)) {
            stringBuilder.append("    format.quotes = true;").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append("    format.quotes = false;").append(LINE_SEPARATOR);
        }
        stringBuilder.append("    if not prefs.achievements then").append(LINE_SEPARATOR);
        stringBuilder.append("        prefs.achievements = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        stringBuilder.append("    if not prefs.achievements_max then").append(LINE_SEPARATOR);
        stringBuilder.append("        prefs.achievements_max = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        stringBuilder.append("    if not prefs.achievements_ids then").append(LINE_SEPARATOR);
        stringBuilder.append("        prefs.achievements_ids = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        stringBuilder.append(initBlockAchievements(nlbBuildingBlocks));
        String perfectGame = nlbBuildingBlocks.getAchievementNamePerfectGame();
        if (StringHelper.notEmpty(perfectGame)) {
            String achievementItemPerfectGame = "prefs.achievements['" + perfectGame + "']";
            stringBuilder.append("    if not ").append(achievementItemPerfectGame).append(" then ").append(achievementItemPerfectGame).append(" = 0; end;").append(LINE_SEPARATOR);
            stringBuilder.append("    if not prefs.achievementNamePerfectGame then prefs.achievementNamePerfectGame = '").append(perfectGame).append("'; end;").append(LINE_SEPARATOR);
        }
        stringBuilder.append("    prefs:store();").append(LINE_SEPARATOR);
        stringBuilder.append("    nlb:resendAchievements(statsAPI);").append(LINE_SEPARATOR);
        stringBuilder.append("    initializeVariables();").append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    private String initBlockAchievements(NLBBuildingBlocks nlbBuildingBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String achievement : nlbBuildingBlocks.getAchievements()) {
            String achievementItem = "prefs.achievements['" + achievement + "']";
            stringBuilder.append("    if not ").append(achievementItem).append(" then ").append(achievementItem).append(" = 0; end;").append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    private String getContainerExpression(String containerRef) {
        if (NO_CONTAINER.equals(containerRef)) {
            return "container = " + NO_CONTAINER + ";";
        } else {
            return "container = " + containerRef + ";";
        }
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(objBlocks.getObjComment());
        }
        stringBuilder.append(objBlocks.getObjLabel()).append(objBlocks.getObjStart());
        stringBuilder.append(objBlocks.getObjName());
        stringBuilder.append(objBlocks.getObjEffect());
        stringBuilder.append(objBlocks.getMorphOver());
        stringBuilder.append(objBlocks.getMorphOut());
        stringBuilder.append(objBlocks.getObjArm());
        stringBuilder.append(objBlocks.getObjSound());
        stringBuilder.append(objBlocks.getObjDisp());
        stringBuilder.append(objBlocks.getObjText());
        if (objBlocks.isTakable()) {
            stringBuilder.append(objBlocks.getObjTak());
            stringBuilder.append(objBlocks.getObjInv());
        }
        stringBuilder.append(objBlocks.getObjConstraint());
        stringBuilder.append(objBlocks.getObjActStart());
        boolean varsOrModsPresent = (
                StringHelper.notEmpty(objBlocks.getObjVariable())
                        || StringHelper.notEmpty(objBlocks.getObjModifications())
        );
        if (varsOrModsPresent) {
            stringBuilder.append(objBlocks.getObjModifications());
            stringBuilder.append(objBlocks.getObjVariable());
        }
        stringBuilder.append(objBlocks.getObjActEnd());
        List<UseBuildingBlocks> usesBuildingBlocks = objBlocks.getUseBuildingBlocks();
        final boolean hasUses = usesBuildingBlocks.size() != 0;
        if (!objBlocks.isTakable() && hasUses) {
            // Not takable but usable => scene_use should be specified
            stringBuilder.append("    scene_use = true,").append(LINE_SEPARATOR);
        }
        stringBuilder.append(objBlocks.getObjImage());
        stringBuilder.append(objBlocks.getObjPreload());
        if (hasUses) {
            stringBuilder.append(objBlocks.getObjUseStart());
        }
        if (hasUses) {
            StringBuilder usepBuilder = new StringBuilder();
            usepBuilder.append("    usep = function(s, w, ww)").append(LINE_SEPARATOR);
            usepBuilder.append("        local prevt = nlb:curloc().lasttext;").append(LINE_SEPARATOR);
            usepBuilder.append("        local wasnouses = true;").append(LINE_SEPARATOR);
            usepBuilder.append("        nlb:curloc().lasttext = \"\";").append(LINE_SEPARATOR);
            for (int i = 0; i < usesBuildingBlocks.size(); i++) {
                StringBuilder usesStartBuilder = new StringBuilder();
                StringBuilder usesEndBuilder = new StringBuilder();
                UseBuildingBlocks useBuildingBlocks = usesBuildingBlocks.get(i);
                String padding = "        ";
                if (i == 0) {
                    usesStartBuilder.append(padding).append("if ");
                    usesStartBuilder.append(useBuildingBlocks.getUseTarget());
                    usesStartBuilder.append(" then").append(LINE_SEPARATOR);
                } else {
                    usesStartBuilder.append(padding).append("end;").append(LINE_SEPARATOR);
                    usesStartBuilder.append(padding).append("if ").append(LINE_SEPARATOR);
                    usesStartBuilder.append(useBuildingBlocks.getUseTarget());
                    usesStartBuilder.append(" then").append(LINE_SEPARATOR);
                }
                final boolean constrained = !StringHelper.isEmpty(useBuildingBlocks.getUseConstraint());
                String extraPadding = constrained ? "    " : "";
                if (constrained) {
                    usesStartBuilder.append(padding).append("    if ").append(useBuildingBlocks.getUseConstraint());
                    usesStartBuilder.append(" then").append(LINE_SEPARATOR);
                }
                stringBuilder.append(usesStartBuilder).append(padding).append(extraPadding);
                stringBuilder.append(useBuildingBlocks.getUseModifications()).append(LINE_SEPARATOR);
                //stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append(useBuildingBlocks.getUseVariable()).append(LINE_SEPARATOR);
                /*
                INSTEAD handles changes in variables automatically. Therefore it is not needed to walk to the current
                room again in order to reflect changes in the variables.
                */
                if (constrained) {
                    usesEndBuilder.append(padding).append("    end;").append(LINE_SEPARATOR);
                    stringBuilder.append(usesEndBuilder);
                }

                usepBuilder.append(usesStartBuilder);
                String useSuccessText = useBuildingBlocks.getUseSuccessText();
                String useFailureText = useBuildingBlocks.getUseFailureText();
                if (StringHelper.notEmpty(useSuccessText)) {
                    usepBuilder.append("local t = \"").append(useSuccessText).append(" \"; nlb:curloc().lasttext = nlb:lasttext()..t; p(t); nlb:curloc().wastext = true; wasnouses = false;").append(LINE_SEPARATOR);
                    if (StringHelper.notEmpty(useFailureText)) {
                        usepBuilder.append(padding).append("    else").append(LINE_SEPARATOR);
                        usepBuilder.append("local t = \"").append(useFailureText).append(" \"; nlb:curloc().lasttext = nlb:lasttext()..t; p(t); nlb:curloc().wastext = true; wasnouses = false;").append(LINE_SEPARATOR);
                    }
                }
                usepBuilder.append(usesEndBuilder);
            }
            usepBuilder.append("        if wasnouses then nlb:curloc().lasttext = prevt; end;").append(LINE_SEPARATOR);
            usepBuilder.append("        end;").append(LINE_SEPARATOR);
            usepBuilder.append("        return not wasnouses;").append(LINE_SEPARATOR);
            usepBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append("        if w.useda then").append(LINE_SEPARATOR);
            stringBuilder.append("            w.useda(w, s, s);").append(LINE_SEPARATOR);
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append(usepBuilder);
        }
        stringBuilder.append(objBlocks.getObjNouse());
        List<String> containedObjIds = objBlocks.getContainedObjIds();
        if (containedObjIds.size() != 0) {
            stringBuilder.append(objBlocks.getObjObjStart());
            for (final String objString : containedObjIds) {
                stringBuilder.append(objString);
            }
            stringBuilder.append(objBlocks.getObjObjEnd());
        }
        stringBuilder.append(objBlocks.getObjEnd());
        if (StringHelper.notEmpty(objBlocks.getObjConstraint())) {
            stringBuilder.append("lifeon('").append(objBlocks.getObjLabel()).append("');").append(LINE_SEPARATOR);
        }
        if (StringHelper.notEmpty(objBlocks.getObjAlias())) {
            stringBuilder.append(objBlocks.getObjAlias()).append(" = ").append(objBlocks.getObjLabel()).append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder autosBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(pageBlocks.getPageComment());
        }
        stringBuilder.append(pageBlocks.getPageLabel());
        // Do not check pageBlocks.isUseCaption() here, because in INSTEAD all rooms must have name
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getNotes());
        stringBuilder.append(pageBlocks.getPageImage());
        boolean hasAnim = pageBlocks.isHasObjectsWithAnimatedImages();
        boolean hasPageAnim = pageBlocks.isHasAnimatedPageImage();
        boolean hasFastAnim = hasAnim || hasPageAnim;
        boolean timerSet = (hasFastAnim || pageBlocks.isHasPageTimer()) && !pageBlocks.isHasGraphicalObjects();
        stringBuilder.append("    var { time = 0; wastext = false; lasttext = nil; tag = '").append(pageBlocks.getPageDefaultTag()).append("'; ");
        stringBuilder.append("autowired = ").append(pageBlocks.isAutowired() ? "true" : "false").append("; ");
        stringBuilder.append("},").append(LINE_SEPARATOR);
        if (timerSet) {
            stringBuilder.append("    timer = function(s)").append(LINE_SEPARATOR);
            if (hasFastAnim) {
                stringBuilder.append("        if (nlb._fps * (get_ticks() - nlbticks) <= 1000) then").append(LINE_SEPARATOR);
                stringBuilder.append("            return;").append(LINE_SEPARATOR);
                stringBuilder.append("        end").append(LINE_SEPARATOR);
                stringBuilder.append("        nlbticks = get_ticks();").append(LINE_SEPARATOR);
            }
            stringBuilder.append("        if not s.wastext then").append(LINE_SEPARATOR);
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariable()).append(LINE_SEPARATOR);
            stringBuilder.append("        end; ").append(LINE_SEPARATOR);
            if (hasPageAnim) {
                stringBuilder.append("        vn:scene(s:bgimg()); ").append(LINE_SEPARATOR);
            }
            stringBuilder.append("        local afl = s.autos(s); ").append(LINE_SEPARATOR);
            stringBuilder.append("        if (s.lasttext ~= nil) and not s.wastext then return s.lasttext; elseif afl and not s.wastext then return true; end; ").append(LINE_SEPARATOR);
            stringBuilder.append("        s.wastext = false; ").append(LINE_SEPARATOR);
            stringBuilder.append("    end,").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageTextStart());
        autosBuilder.append("    autos = function(s)").append(LINE_SEPARATOR);
        autosBuilder.append("        nlb:revive();").append(LINE_SEPARATOR);
        autosBuilder.append("        nlb:ways_chk(s);").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                autosBuilder.append(generateAutoLinkCode(linkBlocks));
            }
        }
        if (!isVN(pageBlocks.getTheme())) {
            autosBuilder.append("        vn:standard_renew();").append(LINE_SEPARATOR);
        }
        autosBuilder.append("        return true;").append(LINE_SEPARATOR);
        autosBuilder.append("    end,").append(LINE_SEPARATOR);

        boolean varsOrModsPresent = (
                !StringHelper.isEmpty(pageBlocks.getPageVariable())
                        || !StringHelper.isEmpty(pageBlocks.getPageModifications())
        );
        stringBuilder.append(generateOrdinaryLinkTextInsideRoom(pageBlocks));
        stringBuilder.append(pageBlocks.getPageTextEnd());

        stringBuilder.append(autosBuilder.toString());
        // TODO: check that here() will not be used in modifications (for example, when automatically taking objects to the inventory)
        stringBuilder.append("    nextsnd = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local sndfile = nlb:pop('").append(pageBlocks.getPageName()).append("_snds');").append(LINE_SEPARATOR);
        stringBuilder.append("        if sndfile ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            s.sndout(s);").append(LINE_SEPARATOR);
        stringBuilder.append("            add_sound(sndfile);").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    theme_file = function(s)").append(LINE_SEPARATOR);
        if (pageBlocks.getTheme() == Theme.STANDARD) {
            stringBuilder.append("        return 'theme_standard.lua';").append(LINE_SEPARATOR);
        } else if (pageBlocks.getTheme() == Theme.VN) {
            stringBuilder.append("        return 'theme_vn.lua';").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append(getDefaultThemeSwitchExpression());
        }
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    enter = function(s, f)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.lasttext = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        s.wastext = false;").append(LINE_SEPARATOR);
        stringBuilder.append("        if not (f.autowired) then").append(LINE_SEPARATOR);
        if (varsOrModsPresent) {
            stringBuilder.append(pageBlocks.getPageModifications());
            stringBuilder.append(pageBlocks.getPageVariable());
        }
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        s:initf(false);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    initf = function(s, from_vn)").append(LINE_SEPARATOR);
        stringBuilder.append("        nlb:theme_switch(s:theme_file(), from_vn);").append(LINE_SEPARATOR);
        if (hasFastAnim) {
            stringBuilder.append("        nlbticks = stead.ticks();").append(LINE_SEPARATOR);
        }
        if (timerSet) {
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariableInit()).append(LINE_SEPARATOR);
            int timerRate = (hasFastAnim ? 1 : 200);
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariable()).append(LINE_SEPARATOR);
            // stringBuilder.append("        s.autos(s);").append(LINE_SEPARATOR); -- will be called when timer triggers
            // Timer will be triggered first time immediately after timer:set()
            stringBuilder.append("        timer:set(").append(timerRate).append(");").append(LINE_SEPARATOR);
        }
        stringBuilder.append("        s.snd(s);").append(LINE_SEPARATOR);
        stringBuilder.append(generateDirectModeStartText(pageBlocks));
        stringBuilder.append("        return s.add_gobj(s);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append(getGraphicalObjectAppendingExpression(pageBlocks, timerSet));
        stringBuilder.append("    exit = function(s, t)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.sndout(s);").append(LINE_SEPARATOR);
        if (timerSet) {
            stringBuilder.append("        timer:stop();").append(LINE_SEPARATOR);
        }
        stringBuilder.append("        s.wastext = false;").append(LINE_SEPARATOR);
        stringBuilder.append("        s.lasttext = nil;").append(LINE_SEPARATOR);
        stringBuilder.append(generateDirectModeStopText(pageBlocks));
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    life = function(s)").append(LINE_SEPARATOR);
        if (!timerSet) {
            stringBuilder.append("        if vn.stopped then s.autos(s); end;").append(LINE_SEPARATOR);
        }
        //stringBuilder.append("        return true;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageSound());

        stringBuilder.append(generateObjsCollection(pageBlocks, linksBlocks));
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
    }

    protected boolean isDirectMode(PageBuildingBlocks pageBlocks) {
        return pageBlocks.isDirectMode() && pageBlocks.getTheme() == Theme.VN;
    }

    protected String generateDirectModeStartText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        if (isDirectMode(pageBlocks)) {
            stringBuilder.append("        vn:request_full_clear();").append(LINE_SEPARATOR);
            stringBuilder.append("        vn:lock_direct();").append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    protected String generateDirectModeStopText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        if (isDirectMode(pageBlocks)) {
            stringBuilder.append("        vn:request_full_clear();").append(LINE_SEPARATOR);
            stringBuilder.append("        vn:unlock_direct();").append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    protected String getGraphicalObjectAppendingExpression(PageBuildingBlocks pageBuildingBlocks, boolean timerSet) {
        StringBuilder stringBuilder = new StringBuilder("    add_gobj = function(s)").append(getLineSeparator());
        stringBuilder.append("        local bg_img = s.bgimg(s);").append(getLineSeparator());
        //final boolean imageBackground = pageBuildingBlocks.isImageBackground();
        // vn:scene should be called in all cases
        stringBuilder.append("        nlb:revive();").append(getLineSeparator());
        stringBuilder.append("        nlb:ways_chk(s);").append(LINE_SEPARATOR);
        stringBuilder.append("        vn:scene(bg_img);").append(getLineSeparator());
        if (timerSet) {
            stringBuilder.append("        return vn:get_win_coords();").append(getLineSeparator());
        } else {
            stringBuilder.append("        local geomFuncNeedToCall = true;").append(getLineSeparator());
            if (pageBuildingBlocks.isHasGraphicalObjects()) {
                for (String graphicalObjId : pageBuildingBlocks.getContainedGraphicalObjIds()) {
                    stringBuilder.append("        if " + graphicalObjId + ".preload then").append(getLineSeparator());
                    stringBuilder.append("            geomFuncNeedToCall = false;").append(getLineSeparator());
                    stringBuilder.append("            " + graphicalObjId + ":preload(s);").append(getLineSeparator());
                    stringBuilder.append("        else").append(getLineSeparator());
                    stringBuilder.append("            vn:gshow(" + graphicalObjId + ");").append(getLineSeparator());
                    stringBuilder.append("        end").append(getLineSeparator());
                }
            }
            stringBuilder.append("        if geomFuncNeedToCall then").append(getLineSeparator());
            stringBuilder.append("            if s:autos() then vn:auto_geom('dissolve', function() s.autos(s); end); end;").append(getLineSeparator());
            // TODO: check possible errors because of s:autos() check
            stringBuilder.append("        end;").append(getLineSeparator());
            if (isDirectMode(pageBuildingBlocks)) {
                stringBuilder.append("        return 0, 0, vn.scr_w, vn.scr_h;").append(getLineSeparator());
            } else {
                stringBuilder.append("        return vn:get_win_coords();").append(getLineSeparator());
            }
        }
        stringBuilder.append("    end,").append(getLineSeparator());
        return stringBuilder.toString();
    }

    protected String getDefaultThemeSwitchExpression() {
        return "        return 'theme_standard.lua';" + LINE_SEPARATOR;
    }

    protected String generateOrdinaryLinkTextInsideRoom(PageBuildingBlocks pageBuildingBlocks) {
        if (isVN(pageBuildingBlocks.getTheme())) {
            return m_vnsteadExportManager.generateOrdinaryLinkTextInsideRoom(pageBuildingBlocks);
        }
        /*
        // Legacy version
        StringBuilder linksBuilder = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBuildingBlocks.getLinksBuildingBlocks()) {
            if (!linkBlocks.isAuto()) {
                linksBuilder.append(generateOrdinaryLinkCode(linkBlocks));
            }
        }
        return linksBuilder.toString();
        */
        StringBuilder wayBuilder = new StringBuilder("    way = {" + LINE_SEPARATOR);
        StringBuilder xdscBuilder = new StringBuilder("    xdsc = function(s)" + LINE_SEPARATOR);
        if (!pageBuildingBlocks.getContainedObjIds().isEmpty()) {
            xdscBuilder.append("        p \"^\";").append(LINE_SEPARATOR);
        }
        xdscBuilder.append("        p(nlb:alts_txt(s));").append(LINE_SEPARATOR);
        StringBuilder wcnsBuilder = new StringBuilder("    wcns = {" + LINE_SEPARATOR);
        StringBuilder altsBuilder = new StringBuilder("    alts = {" + LINE_SEPARATOR);
        for (final LinkBuildingBlocks linkBlocks : pageBuildingBlocks.getLinksBuildingBlocks()) {
            if (!linkBlocks.isAuto()) {
                if (linkBlocks.isInline()) {
                    xdscBuilder.append(generateOrdinaryLinkCode(linkBlocks));
                } else {
                    wayBuilder.append("        '").append(linkBlocks.getLinkLabel()).append("',").append(LINE_SEPARATOR);
                }
                final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
                if (constrained) {
                    wcnsBuilder.append("        ").append("['").append(linkBlocks.getLinkLabel()).append("'] = function() return ").append(linkBlocks.getLinkConstraint()).append("; end,").append(LINE_SEPARATOR);
                    String altText = linkBlocks.getLinkAltText();
                    if (StringHelper.notEmpty(altText)) {
                        altsBuilder.append("        ").append("['").append(linkBlocks.getLinkLabel()).append("'] = function() return \"").append(altText).append("\"; end,").append(LINE_SEPARATOR);
                    }
                }
            }
        }
        wayBuilder.append("    },").append(LINE_SEPARATOR);
        xdscBuilder.append("    end,").append(LINE_SEPARATOR);
        wcnsBuilder.append("    },").append(LINE_SEPARATOR);
        altsBuilder.append("    },").append(LINE_SEPARATOR);
        return wayBuilder.toString() + xdscBuilder.toString() + wcnsBuilder.toString() + altsBuilder.toString();
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        if (isVN(pageBlocks.getTheme())) {
            return m_vnsteadExportManager.generatePostPageText(pageBlocks);
        }
        /*
        // Legacy version
        return Constants.EMPTY_STRING;
        */
        List<LinkBuildingBlocks> linksBuildingBlocks = pageBlocks.getLinksBuildingBlocks();
        if (!hasChoicesOrLeaf(pageBlocks)) {
            return Constants.EMPTY_STRING;
        }
        StringBuilder linksBuilder = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : linksBuildingBlocks) {
            if (!linkBlocks.isAuto()) {
                if (ENABLE_COMMENTS) {
                    linksBuilder.append(linkBlocks.getLinkComment());
                }
                linksBuilder.append(linkBlocks.getLinkStart());
                linksBuilder.append(linkBlocks.getLinkModifications());
                linksBuilder.append(linkBlocks.getLinkVariable());
                linksBuilder.append(linkBlocks.getLinkVisitStateVariable());
                linksBuilder.append(linkBlocks.getLinkGoTo());
                linksBuilder.append(linkBlocks.getLinkEnd()).append(LINE_SEPARATOR);
            }
        }
        return linksBuilder.toString();
    }

    protected String generateObjsCollection(PageBuildingBlocks pageBlocks, List<LinkBuildingBlocks> linksBlocks) {
        if (isVN(pageBlocks.getTheme())) {
            return m_vnsteadExportManager.generateObjsCollection(pageBlocks, linksBlocks);
        }
        StringBuilder result = new StringBuilder();
        /*
        // Legacy version
        StringBuilder linksBuilder = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!linkBlocks.isAuto()) {
                linksBuilder.append(linkBlocks.getLinkStart());
                if (ENABLE_COMMENTS) {
                    linksBuilder.append(linkBlocks.getLinkComment());
                }
                linksBuilder.append(linkBlocks.getLinkModifications());
                linksBuilder.append(linkBlocks.getLinkVariable());
                linksBuilder.append(linkBlocks.getLinkVisitStateVariable());
                linksBuilder.append(linkBlocks.getLinkGoTo());
                linksBuilder.append(linkBlocks.getLinkEnd());
            }
        }
        String linksText = linksBuilder.toString();
        final boolean containedObjIdsIsEmpty = pageBlocks.getContainedObjIds().isEmpty();
        final boolean linksTextIsEmpty = StringHelper.isEmpty(linksText);
        if (!linksTextIsEmpty || !containedObjIdsIsEmpty) {
            result.append("    obj = { ").append(LINE_SEPARATOR);
            if (!containedObjIdsIsEmpty) {
                for (String containedObjId : pageBlocks.getContainedObjIds()) {
                    result.append(containedObjId);
                }
            }
            result.append("        xdsc(),").append(LINE_SEPARATOR);
            if (!linksTextIsEmpty) {
                result.append(linksText).append(LINE_SEPARATOR);
            }
            result.append("    },").append(LINE_SEPARATOR);
        }
        */
        final boolean containedObjIdsIsEmpty = pageBlocks.getContainedObjIds().isEmpty();
        result.append("    obj = { ").append(LINE_SEPARATOR);
        if (!containedObjIdsIsEmpty) {
            for (String containedObjId : pageBlocks.getContainedObjIds()) {
                result.append(containedObjId);
            }
        }
        result.append("        xdsc()").append(LINE_SEPARATOR);
        result.append("    },").append(LINE_SEPARATOR);
        return result.toString();
    }

    protected String generateOrdinaryLinkCode(LinkBuildingBlocks linkBlocks) {
        if (isVN(linkBlocks.getTheme())) {
            return m_vnsteadExportManager.generateOrdinaryLinkCode(linkBlocks);
        }
        final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
        StringBuilder result = new StringBuilder();
        result.append("        p(");
        if (constrained) {
            //result.append("((").append(linkBlocks.getLinkConstraint()).append(") and ");
            result
                    .append("(not s.wcns['")
                    .append(linkBlocks.getLinkLabel())
                    .append("'] or s.wcns['")
                    .append(linkBlocks.getLinkLabel())
                    .append("']()) and ");
        }
        result
                .append("\"{")
                .append(linkBlocks.getLinkLabel())
                .append("_XLnk|")
                .append(linkBlocks.getLinkText())
                .append("}^\"");
        if (constrained) {
            result.append(" or \"\"");
            //result.append("\"").append(linkBlocks.getLinkAltText()).append("\")");
        }
        result.append(");").append(LINE_SEPARATOR);
        return result.toString();
    }

    protected String generateAutoLinkCode(LinkBuildingBlocks linkBlocks) {
        final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
        StringBuilder result = new StringBuilder();
        result.append("        ");
        result.append("if (").append((constrained) ? linkBlocks.getLinkConstraint() : "true").append(") then ");
        result.append(linkBlocks.getLinkModifications());
        result.append(linkBlocks.getLinkVariable());
        result.append(linkBlocks.getLinkVisitStateVariable());
        result.append(linkBlocks.getLinkGoTo());
        // Should return immediately to prevent unwanted following of other auto links
        result.append(LINE_SEPARATOR).append("        return false;").append(LINE_SEPARATOR);
        result.append(" end;"); // matching end for if (...)
        result.append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String generateVariableInitializationText(Map<String, String> initValuesMap) {
        StringBuilder result = new StringBuilder(LINE_SEPARATOR);
        result.append("-- In INSTEAD, when using the snapshots module, you will run into unpredictable behaviour if the variable is not initialized yet.").append(LINE_SEPARATOR);
        result.append("-- More specifically, if the value is nil, it won't be included in the snapshot & therefore will not be overwritten when the snapshot is loaded.").append(LINE_SEPARATOR);
        result.append("-- So, for example, if the flag was initially nil, then you make snapshot, then you walked into location which sets this flag to true,").append(LINE_SEPARATOR);
        result.append("-- than when loading the snapshot the value of the flag will NOT change back to nil.").append(LINE_SEPARATOR);
        result.append("-- That's why we should initialize all nil variables on start (so that they equal false or 0 or '', but not nil).").append(LINE_SEPARATOR);
        result.append("function initializeVariables()").append(LINE_SEPARATOR);
        for (Map.Entry<String, String> entry : initValuesMap.entrySet()) {
            result.append("    if ").append(entry.getKey()).append(" == nil then").append(LINE_SEPARATOR);
            result.append("        ").append(entry.getKey()).append(" = ").append(entry.getValue()).append(";").append(LINE_SEPARATOR);
            result.append("    end").append(LINE_SEPARATOR);
        }
        result.append("end").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String escapeText(String text) {
        return (
                text
                        .replaceAll("\\\\", Matcher.quoteReplacement("\\\\"))
                        .replaceAll("\'", Matcher.quoteReplacement("\\\'"))
                        .replaceAll("\"", Matcher.quoteReplacement("\\\""))
                        .replaceAll("\\[", Matcher.quoteReplacement("\\91"))
                        .replaceAll("\\]", Matcher.quoteReplacement("\\93"))
        );
    }

    @Override
    protected String decorateObjLabel(String id) {
        return decorateId(id);
    }

    @Override
    protected String decorateObjComment(String name) {
        return "-- " + name + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjStart(final String id, String containerRef, ObjType objType, boolean showOnCursor, boolean preserved, boolean loadOnce, boolean clearUnderTooltip, boolean actOnKey, boolean cacheText, boolean looped, boolean noRedrawOnAct, boolean collapsable, String objDefaultTag, int pauseFrames) {
        StringBuilder result = new StringBuilder();
        switch (objType) {
            case STAT:
                result.append(" = nlbstat {").append(LINE_SEPARATOR);
                break;
            case MENU:
                result.append(" = nlbmenu {").append(LINE_SEPARATOR);
                break;
            case GOBJ:
                result.append(" = gobj {").append(LINE_SEPARATOR);
                if (clearUnderTooltip) {
                    result.append("clear_under_tooltip = true,").append(LINE_SEPARATOR);
                }
                if (showOnCursor) {
                    result.append("showoncur = true,").append(LINE_SEPARATOR);
                }
                if (preserved) {
                    result.append("preserved = true,").append(LINE_SEPARATOR);
                }
                if (loadOnce) {
                    result.append("load_once = true,").append(LINE_SEPARATOR);
                }
                if (cacheText) {
                    result.append("cache_text = true,").append(LINE_SEPARATOR);
                }
                if (looped) {
                    result.append("looped = true,").append(LINE_SEPARATOR);
                }
                if (noRedrawOnAct) {
                    result.append("noactredraw = true,").append(LINE_SEPARATOR);
                }
                if (collapsable) {
                    result.append("collapsable = true,").append(LINE_SEPARATOR);
                }
                break;
            case GMENU:
                result.append(" = gmenu {").append(LINE_SEPARATOR);
                if (clearUnderTooltip) {
                    result.append("clear_under_tooltip = true,").append(LINE_SEPARATOR);
                }
                if (showOnCursor) {
                    result.append("showoncur = true,").append(LINE_SEPARATOR);
                }
                if (preserved) {
                    result.append("preserved = true,").append(LINE_SEPARATOR);
                }
                if (loadOnce) {
                    result.append("load_once = true,").append(LINE_SEPARATOR);
                }
                if (cacheText) {
                    result.append("cache_text = true,").append(LINE_SEPARATOR);
                }
                if (looped) {
                    result.append("looped = true,").append(LINE_SEPARATOR);
                }
                if (noRedrawOnAct) {
                    result.append("noactredraw = true,").append(LINE_SEPARATOR);
                }
                if (collapsable) {
                    result.append("collapsable = true,").append(LINE_SEPARATOR);
                }
                break;
            default:
                result.append(" = nlbobj {").append(LINE_SEPARATOR);
        }
        result.append("    var { tag = '").append(objDefaultTag).append("'; ").append(getContainerExpression(containerRef));
        result.append(" },").append(LINE_SEPARATOR);
        if (pauseFrames > 0) {
            result.append("    pause = ").append(pauseFrames).append(";").append(LINE_SEPARATOR);
        }
        result.append("    nlbid = '").append(id).append("',").append(LINE_SEPARATOR);
        if (actOnKey) {
            result.append("    actonkey = function(s, down, key)").append(LINE_SEPARATOR);
            result.append("        if down then").append(LINE_SEPARATOR);
            result.append("            s:actf();").append(LINE_SEPARATOR);
            result.append("        end").append(LINE_SEPARATOR);
            result.append("        return down;").append(LINE_SEPARATOR);
            result.append("    end,").append(LINE_SEPARATOR);
        }
        result.append("    deref = function(s) return stead.deref(").append(decorateObjLabel(id)).append("); end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateObjName(String name, String id) {
        return "    nam = '" + (StringHelper.notEmpty(name) ? name : decorateId(id)) + "'," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjImage(List<ImagePathData> objImagePathDatas, boolean graphicalObj) {
        StringBuilder resultBuilder = new StringBuilder();
        boolean notFirst = false;
        String ifTermination = Constants.EMPTY_STRING;
        for (ImagePathData objImagePathData : objImagePathDatas) {
            String objImagePath = objImagePathData.getImagePath();
            StringBuilder tempBuilder = new StringBuilder();
            tempBuilder.append("        ").append(notFirst ? "else" : Constants.EMPTY_STRING).append("if (");
            String constraint = objImagePathData.getConstraint();
            tempBuilder.append(StringHelper.notEmpty(constraint) ? "s.tag == '" + constraint + "'" : "true").append(") then");
            tempBuilder.append(LINE_SEPARATOR);
            if (objImagePathData.getMaxFrameNumber() == 0 || objImagePathData.isRemoveFrameNumber()) {
                if (StringHelper.notEmpty(objImagePath)) {
                    ifTermination = "        end" + LINE_SEPARATOR;
                    resultBuilder.append(tempBuilder).append("            ");
                    resultBuilder.append("return '").append(objImagePath).append("';").append(LINE_SEPARATOR);
                }
            } else {
                ifTermination = "        end" + LINE_SEPARATOR;
                resultBuilder.append(tempBuilder).append("            ");
                resultBuilder.append("return string.format('").append(objImagePath).append("', nlb:curloc().time % ");
                resultBuilder.append(objImagePathData.getMaxFrameNumber()).append(" + 1); ").append(LINE_SEPARATOR);
            }
            notFirst = true;
        }
        String funbody = resultBuilder.toString();
        if (StringHelper.isEmpty(funbody)) {
            return Constants.EMPTY_STRING;
        } else {
            String result = "    pic = function(s)" + LINE_SEPARATOR + funbody + LINE_SEPARATOR + ifTermination + "end," + LINE_SEPARATOR;
            return result + "    imgv = function(s) return img(s.pic(s)); end," + LINE_SEPARATOR;
        }
    }

    protected String decorateObjEffect(String offsetString, String coordString, boolean graphicalObj, boolean hasParentObj, Obj.MovementDirection movementDirection, Obj.Effect effect, Obj.CoordsOrigin coordsOrigin, int startFrame, int curStep, int maxStep) {
        boolean hasDefinedOffset = StringHelper.notEmpty(offsetString);
        String offset = (hasDefinedOffset && !hasParentObj) ? offsetString : "0,0";
        String pos = getPos(coordsOrigin, offset);
        String steps = (effect == Obj.Effect.None) ? "" : "    maxStep = " + maxStep + "," + LINE_SEPARATOR + "    startFrame = " + startFrame + "," + LINE_SEPARATOR + "    curStep = " + curStep + "," + LINE_SEPARATOR;
        if (effect != Obj.Effect.None) {
            String eff = effect.name().toLowerCase();
            switch (movementDirection) {
                case Top:
                    pos = eff + "top-" + pos;
                    break;
                case Left:
                    pos = eff + "left-" + pos;
                    break;
                case Right:
                    pos = eff + "right-" + pos;
                    break;
                case Bottom:
                    pos = eff + "bottom-" + pos;
                    break;
                default:
                    pos = eff + "-" + pos;
            }
        }
        if (graphicalObj) {
            return "    eff = '" + pos + "'," + LINE_SEPARATOR + steps +
                    (hasDefinedOffset ? "" : "    arm = { [0] = { " + coordString + " } }," + LINE_SEPARATOR);
        } else {
            return EMPTY_STRING;
        }
    }

    @Override
    protected String decorateObjPreload(int startFrame, int maxFrames, int preloadFrames) {
        if (preloadFrames > 0) {
            return "    preload = function(s, room)" + LINE_SEPARATOR +
                    "        vn.hz = vn.hz_preloaded;" + LINE_SEPARATOR +
                    "        vn:preload_effect(s:pic(), " + startFrame + ", " + maxFrames + ", " + startFrame + ", " + preloadFrames + ", function()" + LINE_SEPARATOR +
                    "            vn:gshow(s);" + LINE_SEPARATOR +
                    "            if room then vn:geom(8, 864, 1904, 184, 'dissolve', 240, function() room.autos(room); vn.hz = vn.hz_onthefly; end); end;" + LINE_SEPARATOR +
                    "        end, false, s.load_once);" + LINE_SEPARATOR +
                    "    end," + LINE_SEPARATOR;
        } else {
            return EMPTY_STRING;
        }
    }

    @NotNull
    private String getPos(Obj.CoordsOrigin coordsOrigin, String offset) {
        String pos = "left-top";
        switch (coordsOrigin) {
            case LeftTop:
                pos = "left-top";
                break;
            case MiddleTop:
                pos = "middle-top";
                break;
            case RightTop:
                pos = "right-top";
                break;
            case LeftMiddle:
                pos = "left-middle";
                break;
            case MiddleMiddle:
                pos = "middle-middle";
                break;
            case RightMiddle:
                pos = "right-middle";
                break;
            case LeftBottom:
                pos = "left-bottom";
                break;
            case MiddleBottom:
                pos = "middle-bottom";
                break;
            case RightBottom:
                pos = "right-bottom";
        }
        return pos + "@" + offset;
    }

    @Override
    protected String decorateMorphOver(String morphOverId, boolean graphicalObj) {
        if (graphicalObj && StringHelper.notEmpty(morphOverId)) {
            return "    morphover = function(s) return " + decorateId(morphOverId) + ":deref(); end," + LINE_SEPARATOR;
        } else {
            return EMPTY_STRING;
        }
    }

    @Override
    protected String decorateMorphOut(String morphOutId, boolean graphicalObj) {
        if (graphicalObj && StringHelper.notEmpty(morphOutId)) {
            return "    morphout = function(s) return " + decorateId(morphOutId) + ":deref(); end," + LINE_SEPARATOR;
        } else {
            return EMPTY_STRING;
        }
    }

    @Override
    protected String decorateObjDisp(String dispText, boolean imageEnabled, boolean isGraphicalObj) {
        if (imageEnabled && !isGraphicalObj) {
            return (
                    "    disp = function(s) return s.imgv(s)..\"" +
                            dispText +
                            "\" end," + LINE_SEPARATOR
            );
        } else {
            if (StringHelper.notEmpty(dispText)) {
                return "    disp = function(s) return \"" + dispText + "\" end," + LINE_SEPARATOR;
            } else {
                return "    disp = function(s) return false; end," + LINE_SEPARATOR;
            }

        }
    }

    private String expandInteractionMarks(String objId, String objName, boolean useReference, String text, boolean withImage, boolean isGraphicalObj) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = STEAD_OBJ_PATTERN.matcher(text);
        int start = 0;
        while (matcher.find()) {
            result.append(text.substring(start, matcher.start())).append("{");
            if (useReference) {
                result.append(StringHelper.isEmpty(objName) ? decorateId(objId) : objName).append("|");
            }
            if (withImage && !isGraphicalObj) {
                result.append("\"..s.imgv(s)..\"");
            }
            result.append(matcher.group(1)).append("}");
            start = matcher.end();
        }
        result.append(text.substring(start, text.length()));
        return result.toString();
    }

    @Override
    protected String decorateObjText(String objId, String objName, boolean suppressDsc, String objText, boolean imageEnabled, boolean isGraphicalObj) {
        StringBuilder stringBuilder = new StringBuilder();
        if (suppressDsc) {
            stringBuilder.append("    suppress_dsc = true,").append(LINE_SEPARATOR);
        }
        stringBuilder.append("    dscf = function(s) ");
        if (StringHelper.notEmpty(objText)) {
            stringBuilder.append("return \"");
            stringBuilder.append(expandInteractionMarks(objId, objName, suppressDsc, objText, imageEnabled, isGraphicalObj));
            stringBuilder.append("\"; ");
        }
        stringBuilder.append("end,").append(LINE_SEPARATOR);
        stringBuilder.append("    dsc = function(s) ");
        if (isGraphicalObj) {
            stringBuilder.append("return s.dscf(s); ");
        } else {
            if (!suppressDsc) {
                stringBuilder.append("p(s.dscf(s)); ");
            }
        }
        stringBuilder.append("end,").append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    @Override
    protected String decorateObjTak(final String objName, final String commonObjId) {
        boolean hasCmn = StringHelper.notEmpty(commonObjId);
        StringBuilder returnExpression = new StringBuilder();
        if (hasCmn) {
            returnExpression.append("        local hasCmnTak = (").append(decorateId(commonObjId)).append(".tak ~= nil").append(");").append(LINE_SEPARATOR);
            returnExpression.append("        if hasCmnTak then").append(LINE_SEPARATOR);
            returnExpression.append("            nlb:addf(nil, ").append(decorateId(commonObjId)).append(", false);").append(LINE_SEPARATOR);  // Adding commonobj to inventory, analogous to ADDINV, and doing nothing for this object
            returnExpression.append("        end").append(LINE_SEPARATOR);
            returnExpression.append("        return not hasCmnTak;").append(LINE_SEPARATOR);
        } else {
            returnExpression.append("        return true;").append(LINE_SEPARATOR);
        }
        return (
                "    tak = function(s)" + LINE_SEPARATOR +
                        "        s.act(s);" + LINE_SEPARATOR +
                        returnExpression.toString() +
                        "    end," + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjInv(ObjType objType) {
        switch (objType) {
            case MENU:
                return (
                        "    menu = function(s)" + LINE_SEPARATOR
                                + "        return s.act(s);" + LINE_SEPARATOR
                                + "    end," + LINE_SEPARATOR
                );
            case OBJ:
                return (
                        "    inv = function(s)" + LINE_SEPARATOR
                                + "        if s.use then s.use(s, s); end;" + LINE_SEPARATOR
                                + "    end," + LINE_SEPARATOR
                );
            default:
                return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decorateObjActStart(String actTextExpanded, String commonObjId) {
        final boolean actTextNotEmpty = StringHelper.notEmpty(actTextExpanded);
        final String returnStatement = (actTextNotEmpty) ? "" : "        return true;" + LINE_SEPARATOR;
        String actText = getActText(actTextNotEmpty);

        StringBuilder result = new StringBuilder();
        boolean hasCmn = StringHelper.notEmpty(commonObjId);
        result.append("    used = function(s, w)").append(LINE_SEPARATOR);
        String id = hasCmn ? decorateId(commonObjId) : Constants.EMPTY_STRING;
        if (hasCmn) {
            result.append("        ").append("w:usea(").append(id).append(", s);").append(LINE_SEPARATOR);
        }
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    act = function(s)").append(LINE_SEPARATOR);
        result.append("        s:acta();").append(LINE_SEPARATOR);
        result.append(returnStatement);
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    actt = function(s)").append(LINE_SEPARATOR);
        result.append("        return \"").append(actTextExpanded).append("\";").append(LINE_SEPARATOR);
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    actp = function(s)").append(LINE_SEPARATOR);
        result.append(actText);
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    acta = function(s)").append(LINE_SEPARATOR);
        result.append("        s:actp();").append(LINE_SEPARATOR);
        result.append("        s:actf();").append(LINE_SEPARATOR);
        if (hasCmn) {
            // Here we are calling actf of common object, replacing its argument by the current object
            // TODO: Try to fix this, replacing s parameter with another object is not very good idea
            if (actTextNotEmpty) {
                result.append("        ").append(id).append(".actf(s);").append(LINE_SEPARATOR);
            } else {
                result.append("        ").append(id).append(":actp();").append(LINE_SEPARATOR);
                result.append("        ").append(id).append(".actf(s);").append(LINE_SEPARATOR);
            }
        }
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    actf = function(s)").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateObjNouse(String nouseTextExpanded) {
        final boolean nouseTextEmpty = StringHelper.isEmpty(nouseTextExpanded);
        if (nouseTextEmpty) {
            return Constants.EMPTY_STRING;
        }
        StringBuilder result = new StringBuilder();
        result.append("    nouse = function(s)").append(LINE_SEPARATOR);
        result.append("        nlb:curloc().lasttext = \"").append(nouseTextExpanded).append("\"; nlb:curloc().wastext = true;").append(LINE_SEPARATOR);
        result.append("        return nlb:curloc().lasttext;").append(LINE_SEPARATOR);
        result.append("    end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    protected String getActText(boolean actTextNotEmpty) {
        return (
                (actTextNotEmpty)
                        ? "        nlb:curloc().lasttext = s.actt(s); p(nlb:curloc().lasttext); nlb:curloc().wastext = true;" + LINE_SEPARATOR
                        : Constants.EMPTY_STRING
        );
    }

    @Override
    protected String decorateObjActEnd(boolean collapseOnAct) {
        final String prefix = (collapseOnAct)
                ? (
                "        local v = vn:glookup(stead.deref(s));" + LINE_SEPARATOR +
                        "        if s.is_paused then" + LINE_SEPARATOR +
                        "            vn:vpause(v, false);" + LINE_SEPARATOR +
                        "        else" + LINE_SEPARATOR +
                        "            vn:set_step(v, nil, not v.forward);" + LINE_SEPARATOR +
                        "        end" + LINE_SEPARATOR +
                        "        vn:start();" + LINE_SEPARATOR
        ) : "";
        return prefix + "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjUseStart(String commonObjId) {
        // Before use, execute possible act commands (without printing act text) -> s.actf(s)
        boolean hasCmn = StringHelper.notEmpty(commonObjId);
        String cmnUse = (hasCmn) ? "        if was_nonempty_usetext then " + decorateId(commonObjId) + ":usef(w, w); else " + decorateId(commonObjId) + ":usea(w, w); end;" + LINE_SEPARATOR : "";
        return (
                "    use = function(s, w)" + LINE_SEPARATOR +
                        // "        s:actf();" + LINE_SEPARATOR + // TODO: Possible used somewhere
                        "        local was_nonempty_usetext = s:usea(w, w);" + LINE_SEPARATOR +
                        cmnUse +
                        "    end," + LINE_SEPARATOR +
                        "    usea = function(s, w, ww)" + LINE_SEPARATOR +
                        "        local was_nonempty_usetext = s:usep(w, ww);" + LINE_SEPARATOR +
                        "        s:usef(w, ww);" + LINE_SEPARATOR +
                        "        return was_nonempty_usetext;" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        "    usef = function(s, w, ww)" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjUseEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjObjStart() {
        return "    obj = {" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjObjEnd() {
        return "    }," + LINE_SEPARATOR;
    }

    protected String decorateUseTarget(String targetId) {
        return "w.nlbid == " + decorateId(targetId) + ".nlbid";
    }

    protected String decorateUseVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    protected String decorateUseModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjConstraint(String constraintValue) {
        StringBuilder result = new StringBuilder();
        if (StringHelper.notEmpty(constraintValue)) {
            result.append("    alive = function(s)").append(LINE_SEPARATOR);
            result.append("        return ").append(constraintValue).append(" and s:cont_alive();").append(LINE_SEPARATOR);
            result.append("    end,").append(LINE_SEPARATOR);
        }
        return result.toString();
    }

    @Override
    protected String decorateObjCommonTo(String commonObjId) {
        StringBuilder result = new StringBuilder();
        boolean hasCmn = StringHelper.notEmpty(commonObjId);
        result.append("    used = function(s, w)").append(LINE_SEPARATOR);
        String id = hasCmn ? decorateId(commonObjId) : Constants.EMPTY_STRING;
        if (hasCmn) {
            result.append("        ").append("w:usea(").append(id).append(", s);").append(LINE_SEPARATOR);
        }
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    actcmn = function(s)").append(LINE_SEPARATOR);
        if (hasCmn) {
            // Here we are calling actf of common object, replacing its argument by the current object
            result.append("        ").append(id).append(".actf(s);").append(LINE_SEPARATOR);
        }
        result.append("    end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateObjModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateObjEnd() {
        return "};" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateContainedObjId(String containedObjId) {
        return "        '" + decorateId(containedObjId) + "'," + LINE_SEPARATOR;
    }

    @Override
    protected String generateTrailingText() {
        return "";
    }

    @Override
    protected String decorateAssignment(String variableName, String variableValue) {
        return variableName + " = " + variableValue + ";" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTag(final String variable, final String objId, final String tag) {
        StringBuilder result = new StringBuilder();
        if (StringHelper.isEmpty(variable) && objId == null) {
            result.append("s.tag = ");
        } else {
            if (objId != null) {
                result.append(decorateId(objId)).append(".tag = ");
            } else {
                result.append(variable).append(".tag = ");
            }
        }
        result.append("'").append(tag).append("';").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateGetTagOperation(String resultingVariable, String objId, String objVariableName) {
        StringBuilder result = new StringBuilder();
        if (StringHelper.notEmpty(resultingVariable)) {
            result.append(resultingVariable).append(" = ");
            if (StringHelper.notEmpty(objVariableName)) {
                result.append(objVariableName);
            } else if (objId != null) {
                result.append(decorateId(objId));
            } else {
                result.append("s");
            }
            result.append(".tag;").append(LINE_SEPARATOR);
        }
        return result.toString();
    }

    @Override
    protected String decorateWhile(final String constraint) {
        return "while (" + constraint + ") do" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIf(final String constraint) {
        return "if (" + constraint + ") then" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateIfHave(String objId, String objVar) {
        if (objId != null) {
            return "if have(" + decorateId(objId) + ") then" + LINE_SEPARATOR;
        } else {
            return "if have(stead.deref(" + objVar + ")) then" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateElse() {
        return "else" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateElseIf(final String constraint) {
        return "elseif (" + constraint + ") then" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateEnd() {
        return "end;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateReturn(String returnValue) {
        // "if true" because of possible error if return is used without enclosing if ('end' expected (to close 'function' at line XXX))
        // TODO: possibly we can use left hand side of return to hold the constraint instead of just 'true'
        String valueToReturn = StringHelper.isEmpty(returnValue) ? Constants.EMPTY_STRING : returnValue;
        return "if true then return " + valueToReturn + "; end;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateHaveOperation(String variableName, String objId, String objVar) {
        if (objId != null) {
            return variableName + " = have(" + decorateId(objId) + ");" + LINE_SEPARATOR;
        } else {
            return variableName + " = have(stead.deref(" + objVar + "));" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateCloneOperation(final String variableName, final String objId, final String objVar) {
        if (objId != null) {
            return variableName + " = nlb:clone(" + decorateId(objId) + ");" + LINE_SEPARATOR;
        } else if (objVar != null) {
            return variableName + " = nlb:clone(" + objVar + ");" + LINE_SEPARATOR;
        } else {
            return variableName + " = nlb:clone(s);" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateContainerOperation(String variableName, String objId, String objVar) {
        if (objId != null) {
            return variableName + " = " + decorateId(objId) + ".container();" + LINE_SEPARATOR;
        } else if (objVar != null) {
            return variableName + " = " + objVar + ".container();" + LINE_SEPARATOR;
        } else {
            return variableName + " = s.container();" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateGetIdOperation(final String variableName, final String objId, final String objVar) {
        if (objId != null) {
            return variableName + " = '" + objId + "';" + LINE_SEPARATOR;
        } else if (objVar != null) {
            return variableName + " = " + objVar + ".nlbid;" + LINE_SEPARATOR;
        } else {
            return variableName + " = s.nlbid;" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateDelObj(String destinationId, final String destinationName, String objectId, String objectVar, String objectName, String objectDisplayName) {
        String objToDel = (objectId != null) ? decorateId(objectId) : objectVar;
        if (destinationId == null) {
            if (destinationName != null) {
                return "            nlb:rmv(\"" + destinationName + "\", " + objToDel + "); " + getClearContainerStatement(objToDel) + LINE_SEPARATOR;
            } else {
                return "            objs():del(" + objToDel + "); " + getClearContainerStatement(objToDel) + LINE_SEPARATOR;
            }
        } else {
            return "            objs(" + decorateId(destinationId) + "):del(" + objToDel + "); " + getClearContainerStatement(objToDel) + LINE_SEPARATOR;
        }
    }

    private String getClearContainerStatement(String objVar) {
        return objVar + ".container = " + NO_CONTAINER + "; ";
    }

    @Override
    protected String decorateDelInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return (
                "            if have(stead.deref(" + objectVar + ")) then remove(stead.deref("
                        + objectVar + "), " + "inv()); end;" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName, boolean unique) {
        return (
                "            nlb:addf(" + ((destinationId != null) ? decorateId(destinationId) : "s") +
                        ", " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        (unique ? ", true" : ", false") +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return (
                "            nlb:addf(nil, " + ((objectId != null) ? decorateId(objectId) : objectVar) + ", false);" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        return (
                createListObj(destinationListVariableName) +
                        createListObj(sourceListVariableName) +
                        "        nlb:addAll(s, " + ((destinationId != null) ? decorateId(destinationId) : "nil") +
                        ", " + ((destinationListVariableName != null) ? destinationListVariableName + " and " + destinationListVariableName + ".listnam or \"\"" : "nil") +
                        ", " + sourceListVariableName + " and " + sourceListVariableName + ".listnam or \"\"" +
                        (unique ? ", true" : ", false") +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return (
                createListObj(listVariableName) +
                        "        nlb:pushObjs(" +
                        listVariableName + ".listnam, " + ((srcObjId != null) ? decorateId(srcObjId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateSSndOperation() {
        return "        s:snd();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWSndOperation() {
        return "        ww:snd();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSndOperation(String objectId, String objectVar) {
        if (objectId != null) {
            return "        " + decorateId(objectId) + ":snd();" + LINE_SEPARATOR;
        } else if (objectVar != null) {
            return "        " + objectVar + ":snd();" + LINE_SEPARATOR;
        } else {
            return decorateSSndOperation();
        }
    }

    @Override
    protected String decorateSPushOperation(String listVariableName) {
        return createListObj(listVariableName) + "        nlb:push(" + listVariableName + ".listnam, s);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        return createListObj(listVariableName) + "        nlb:push(" + listVariableName + ".listnam, ww);  -- will push nil if undef" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return (
                createListObj(listVariableName) +
                        "        nlb:push(" +
                        listVariableName + ".listnam, " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    private String createListObj(String listVariableName) {
        if (listVariableName != null) {
            return (
                    "        if not " + listVariableName + " then" + LINE_SEPARATOR +
                            "            stead.add_var { " + listVariableName + " = nlb:clonelst(listobj, \"" + listVariableName + "\"); }" + LINE_SEPARATOR +
                            "        end;" + LINE_SEPARATOR
            );
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        // TODO: handle pops from nonexistent lists
        return variableName + " = nlb:pop(" + listVariableName + ".listnam);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return createListObj(listVariableName) + "        nlb:inject(" + listVariableName + ".listnam, s);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return (
                createListObj(listVariableName) +
                        "        nlb:inject(" +
                        listVariableName + ".listnam, " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        // TODO: handle ejects from nonexistent lists
        return variableName + " = nlb:eject(" + listVariableName + ".listnam);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "nlb:clrcntnr(objs(" + decorateId(destinationId) + ")); " + "objs(" + decorateId(destinationId) + "):zap();" + LINE_SEPARATOR;
        } else if (destinationVar != null) {
            return "nlb:clear(" + destinationVar + ");" + LINE_SEPARATOR;
        } else {
            return "nlb:clrcntnr(objs()); objs():zap();" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateClearInvOperation() {
        return "inv():zap();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return "if " + listVariableName + " then " + variableName + " = nlb:size(" + listVariableName + ".listnam) else " + variableName + " = 0 end;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        return variableName + " = rnd(" + maxValue + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchMaxOperation(String achievementName, int max) {
        return "nlb:setAchievementMax(statsAPI, '" + achievementName + "', " + max + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName, String modificationId) {
        return "nlb:setAchievement(statsAPI, '" + achievementName + "', '" + modificationId + "');" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchievedOperation(String variableName, String achievementName) {
        return variableName + " = nlb:getAchievement(statsAPI, '" + achievementName + "');" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateGoToOperation(String locationId) {
        return "nlb:nlbwalk(nil, " + decorateId(locationId) + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSnapshotOperation(String snapshotId) {
        // TODO: use snapshotId? Right now there is only one snapshot
        return "nlb:snapshot();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateOpenURLOperation(String url) {
        return "statsAPI.openURL(\"" + url + "\");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWinGeomOperation(String arg) {
        return "theme.win.geom(" + arg + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateInvGeomOperation(String arg) {
        return "theme.inv.geom(" + arg + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWinColorOperation(String arg) {
        return "theme.win.color(" + arg + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateInvColorOperation(String arg) {
        return "theme.inv.color(" + arg + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return (
                "        if " + listVariableName + " then" + LINE_SEPARATOR +
                        "            nlb:shuffle(" + listVariableName + ".listnam);" + LINE_SEPARATOR +
                        "        end;" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        return "nlb:curloc().lasttext = nlb:lasttext().." + variableName + "; p(" + variableName + "); nlb:curloc().wastext = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        return resultVariableName + " = " + ((dscObjId != null) ? decorateId(dscObjId) : dscObjVariable) + ":dscf();" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        return "nlb:pdscf(" + objVariableName + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscsOperation(String objId, String objVar) {
        if (objId != null) {
            return "nlb:pdscs(" + decorateId(objId) + ");" + LINE_SEPARATOR;
        } else if (objVar != null) {
            return "nlb:pdscs(" + objVar + ");" + LINE_SEPARATOR;
        } else {
            return "nlb:pdscs(s);" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        String source = (actingObjId != null) ? decorateId(actingObjId) : actingObjVariable;
        return "nlb:acta(" + source + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        return resultVariableName + " = " + ((actObjId != null) ? decorateId(actObjId) : actObjVariable) + ":actt();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        String source = (actingObjId != null) ? decorateId(actingObjId) : actingObjVariable;
        return "nlb:actf(" + source + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        String source = (sourceId != null) ? decorateId(sourceId) : sourceVariable;
        String target = (targetId != null) ? decorateId(targetId) : targetVariable;
        return "nlb:usea(" + source + ", " + target + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateTrue() {
        return "true";
    }

    @Override
    protected String decorateFalse() {
        return "false";
    }

    @Override
    protected String decorateEq() {
        return "==";
    }

    @Override
    protected String decorateNEq() {
        return "~=";
    }

    @Override
    protected String decorateGt() {
        return ">";
    }

    @Override
    protected String decorateGte() {
        return ">=";
    }

    @Override
    protected String decorateLt() {
        return "<";
    }

    @Override
    protected String decorateLte() {
        return "<=";
    }

    @Override
    protected String decorateNot() {
        return "not ";
    }

    @Override
    protected String decorateOr() {
        return "or";
    }

    @Override
    protected String decorateAnd() {
        return "and";
    }

    @Override
    protected String decorateExistence(final String decoratedVariable) {
        return "(" + decoratedVariable + " ~= nil)";
    }

    @Override
    protected String decorateBooleanVar(String constraintVar) {
        return GLOBAL_VAR_PREFIX + constraintVar;
    }

    @Override
    protected String decorateStringVar(String constraintVar) {
        return GLOBAL_VAR_PREFIX + constraintVar;
    }

    @Override
    protected String decorateNumberVar(String constraintVar) {
        return GLOBAL_VAR_PREFIX + constraintVar;
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkLabel(linkId, linkText, theme);
        }
        /*
        // Legacy version
        return "{" + decorateId(linkId) + "|" + linkText + "}^";
        */
        return decorateId(linkId);
    }

    @Override
    protected String decorateLinkComment(String comment) {
        /*
        // Legacy version
        return "                --" + comment + LINE_SEPARATOR;
        */
        return "--" + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, int pageNumber, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkStart(linkId, linkText, isAuto, isTrivial, pageNumber, theme);
        }
        /*
        // Legacy version
        return (
                "        xact(" + LINE_SEPARATOR
                        + "            '" + decorateId(linkId) + "'," + LINE_SEPARATOR
                        + "            function(s) " + LINE_SEPARATOR
        );
        */
        String id = decorateId(linkId);
        return (
                id + "_XLnk = xact(" + LINE_SEPARATOR
                        + "'" + id + "_XLnk'," + LINE_SEPARATOR
                        + "function() walk('" + id + "'); end" + LINE_SEPARATOR
                        + ");" + LINE_SEPARATOR + LINE_SEPARATOR
                        + id + " = room {" + LINE_SEPARATOR
                        + "    nam = '" + id + "'," + LINE_SEPARATOR
                        + "    disp = function(s)" + LINE_SEPARATOR
                        + "        return \"" + linkText + "\";" + LINE_SEPARATOR
                        + "    end," + LINE_SEPARATOR
                        + "    enter = function(s, f)" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkSource,
            int sourcePageNumber,
            String linkTarget,
            int targetPageNumber,
            Theme theme
    ) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkGoTo(linkId, linkText, linkSource, sourcePageNumber, linkTarget, targetPageNumber, theme);
        }
        return "        nlb:nlbwalk(" + decoratePageName(linkSource, sourcePageNumber) + ", " + decoratePageName(linkTarget, targetPageNumber) + "); ";
    }

    @Override
    protected String decorateLinkEnd(Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkEnd(theme);
        }
        /*
        // Legacy version
        return (
                LINE_SEPARATOR
                        + "            end" + LINE_SEPARATOR
                        + "        )," + LINE_SEPARATOR
        );
        */
        return (
                LINE_SEPARATOR
                        + "    end" + LINE_SEPARATOR
                        + "}" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageEnd(boolean isFinish) {
        return "};" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVisitStateVariable(String linkVisitStateVariable) {
        String globalVar = GLOBAL_VAR_PREFIX + linkVisitStateVariable;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTimerVariableInit(final String variableName) {
        if (StringHelper.isEmpty(variableName)) {
            return "s.time = 0; ";
        } else {
            String timerVar = decorateNumberVar(variableName);
            return timerVar + " = 0; s.time = " + timerVar + "; ";
        }
    }

    @Override
    protected String decoratePageTimerVariable(final String variableName) {
        if (StringHelper.isEmpty(variableName)) {
            return "s.time = s.time + 1; ";
        } else {
            String timerVar = decorateNumberVar(variableName);
            return timerVar + " = " + timerVar + " + 1; s.time = " + timerVar + "; ";
        }
    }

    @Override
    protected String decoratePageModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decorateLinkModifications(String modificationsText) {
        return modificationsText;
    }

    @Override
    protected String decoratePageCaption(String caption, boolean useCaption, String moduleTitle, boolean noSave) {
        StringBuilder result = new StringBuilder();
        String title = getNonEmptyTitle(moduleTitle);
        if (StringHelper.notEmpty(caption) && useCaption) {
            result.append("    nam = \"").append(caption).append("\",").append(LINE_SEPARATOR);
        } else {
            result.append("    nam = '").append(title).append("',").append(LINE_SEPARATOR);
        }
        if (noSave) {
            result.append("    nosave = true,").append(LINE_SEPARATOR);
        }
        return result.toString();
    }

    @Override
    protected String decoratePageNotes(String notes) {
        return "    notes = '" + notes + "'," + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageImage(List<ImagePathData> pageImagePathDatas, final boolean imageBackground, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageImage(pageImagePathDatas, imageBackground, theme);
        }
        StringBuilder bgimgBuilder = new StringBuilder("    bgimg = function(s)" + LINE_SEPARATOR);
        StringBuilder picBuilder = new StringBuilder("    pic = function(s)" + LINE_SEPARATOR);
        boolean notFirst = false;
        String bgimgIfTermination = Constants.EMPTY_STRING;
        String picIfTermination = Constants.EMPTY_STRING;
        for (ImagePathData pageImagePathData : pageImagePathDatas) {
            String pageImagePath = pageImagePathData.getImagePath();
            if (StringHelper.notEmpty(pageImagePath)) {
                StringBuilder tempBuilder = new StringBuilder();
                tempBuilder.append("        ").append(notFirst ? "else" : Constants.EMPTY_STRING).append("if (");
                String constraint = pageImagePathData.getConstraint();
                tempBuilder.append(StringHelper.notEmpty(constraint) ? "s.tag == '" + constraint + "'" : "true").append(") then");
                tempBuilder.append(LINE_SEPARATOR);
                final String img = decorateImagePath(pageImagePath, pageImagePathData.getMaxFrameNumber());
                if (imageBackground) {
                    bgimgIfTermination = "        end" + LINE_SEPARATOR;
                    bgimgBuilder.append(tempBuilder).append("            ");
                    bgimgBuilder.append("return ").append(img).append(";").append(LINE_SEPARATOR);
                } else {
                    picIfTermination = "        end" + LINE_SEPARATOR;
                    picBuilder.append(tempBuilder).append("            ");
                    picBuilder.append("return ").append(img).append(";").append(LINE_SEPARATOR);
                }
            }
            notFirst = true;
        }
        // Always returning nlb:std_bg() as bgimg by default
        bgimgBuilder.append(bgimgIfTermination).append("return nlb:std_bg();").append(LINE_SEPARATOR).append("    end,").append(LINE_SEPARATOR);
        picBuilder.append(picIfTermination).append("    end,").append(LINE_SEPARATOR);
        return bgimgBuilder.toString() + picBuilder.toString();
    }

    private String decorateImagePath(String imagePath, int maxFrameNumber) {
        if (maxFrameNumber > 0) {
            return "string.format('" + imagePath + "', nlb:curloc().time % " + maxFrameNumber + " + 1)";
        } else {
            return "'" + imagePath + "'";
        }
    }

    @Override
    protected String decorateObjSound(List<SoundPathData> objSoundPathDatas, boolean soundSFX) {
        // TODO: Code duplication with decoratePageSound()
        StringBuilder result = new StringBuilder("    snd = function(s) " + LINE_SEPARATOR);
        boolean notFirst = false;
        String ifTermination = Constants.EMPTY_STRING;
        for (SoundPathData objSoundPathData : objSoundPathDatas) {
            String objSoundPath = objSoundPathData.getSoundPath();
            if (StringHelper.notEmpty(objSoundPath)) {
                String constraint = objSoundPathData.getConstraint();
                final boolean hasConstraint = StringHelper.notEmpty(constraint);
                if (hasConstraint) {
                    ifTermination = "        end" + LINE_SEPARATOR;
                    result.append("        ").append(notFirst ? "else" : Constants.EMPTY_STRING).append("if (");
                    result.append("s.tag == '").append(constraint).append("'").append(") then");
                    result.append(LINE_SEPARATOR);
                } else {
                    result.append(ifTermination);
                }
                if (Constants.VOID.equals(objSoundPath)) {
                    result.append("            stop_music();").append(LINE_SEPARATOR);
                } else {
                    if (soundSFX || objSoundPathData.isSfx()) {
                        result.append("            add_sound('").append(objSoundPath).append("');").append(LINE_SEPARATOR);
                    } else {
                        result.append("            set_music('").append(objSoundPath).append("', 0);").append(LINE_SEPARATOR);
                    }
                }
            }
            notFirst = true;
        }
        result.append(ifTermination);
        result.append("    end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateObjArm(float left, float top) {
        return "    iarm = { [0] = { " + left + ", " + top + " } };" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageSound(String pageName, List<SoundPathData> pageSoundPathDatas, boolean soundSFX, Theme theme) {
        StringBuilder result = new StringBuilder("    snd = function(s) " + LINE_SEPARATOR);
        boolean notFirst = false;
        boolean hasSFX = false;
        String ifTermination = Constants.EMPTY_STRING;
        for (SoundPathData pageSoundPathData : pageSoundPathDatas) {
            String pageSoundPath = pageSoundPathData.getSoundPath();
            if (StringHelper.notEmpty(pageSoundPath)) {
                String constraint = pageSoundPathData.getConstraint();
                final boolean hasConstraint = StringHelper.notEmpty(constraint);
                if (hasConstraint) {
                    ifTermination = "        end" + LINE_SEPARATOR;
                    result.append("        ").append(notFirst ? "else" : Constants.EMPTY_STRING).append("if (");
                    result.append("s.tag == '").append(constraint).append("'").append(") then");
                    result.append(LINE_SEPARATOR);
                } else {
                    result.append(ifTermination);
                }
                if (Constants.VOID.equals(pageSoundPath)) {
                    result.append("            stop_music();").append(LINE_SEPARATOR);
                } else {
                    if (soundSFX || pageSoundPathData.isSfx()) {
                        hasSFX = true;
                        result.append("            nlb:push('").append(pageName).append("_snds").append("', '").append(pageSoundPath).append("');").append(LINE_SEPARATOR);
                    } else {
                        result.append("            set_music('").append(pageSoundPath).append("', 0);").append(LINE_SEPARATOR);
                    }
                }
            }
            notFirst = true;
        }
        result.append(ifTermination);
        if (!isVN(theme)) {
            result.append("        s.nextsnd(s);").append(LINE_SEPARATOR);
        }
        result.append("    end,").append(LINE_SEPARATOR);
        result.append("    sndout = function(s) ");
        if (hasSFX) {
            result.append("stop_sound(); ");
        }
        result.append("end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    /**
     * Expands variables from text chunks.
     *
     * @param textChunks
     * @return
     */
    protected String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(textChunk.getText());
                    break;
                case ACTION_TEXT:
                    result.append("\"..nlb:lasttext()..\"");
                    break;
                case VARIABLE:
                    result.append("\"..");
                    result.append("tostring(").append(GLOBAL_VAR_PREFIX).append(textChunk.getText()).append(")");
                    result.append("..\"");
                    break;
                case NEWLINE:
                    result.append("^\"..").append(getLineSeparator()).append("\"");
                    break;
            }
        }
        return result.toString();
    }

    /**
     * Expands variables from text chunks.
     *
     * @param textChunks
     * @param theme
     * @return
     */
    protected String expandVariables(List<TextChunk> textChunks, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.expandVariables(textChunks, theme);
        }
        return expandVariables(textChunks);
    }

    @Override
    protected String expandVariablesForLinks(List<TextChunk> textChunks, Theme theme) {
        // Expand variables for links should not use VN-related logic at all, even for VN case we should use plain INSTEAD approach
        return expandVariables(textChunks);
    }

    protected String getGlobalVarPrefix() {
        return GLOBAL_VAR_PREFIX;
    }

    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageTextStart(labelText, pageNumber, pageTextChunks, theme);
        }
        StringBuilder pageText = new StringBuilder();
        if (pageTextChunks.size() > 0) {
            pageText.append("    dsc = function(s)").append(LINE_SEPARATOR);
            pageText.append("        p(\"");
            pageText.append(expandVariables(pageTextChunks, theme));
            pageText.append("\");").append(LINE_SEPARATOR);
            pageText.append("    end,").append(LINE_SEPARATOR);
        } else {
            pageText.append("    dsc = true,").append(LINE_SEPARATOR);
        }
        /*
        // Legacy version
        pageText.append("    xdsc = function(s)").append(LINE_SEPARATOR);
        pageText.append("        p \"^\";").append(LINE_SEPARATOR);
        */
        return pageText.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme, boolean hasChoicesOrLeaf) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageTextEnd(labelText, pageNumber, theme, hasChoicesOrLeaf);
        }
        /*
        // Legacy version
        return "    end," + LINE_SEPARATOR;
        */
        return Constants.EMPTY_STRING;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageLabel(labelText, pageNumber, theme);
        }
        return generatePageBeginningCode(labelText, pageNumber) + "room {" + LINE_SEPARATOR;
    }

    protected String generatePageBeginningCode(String labelText, int pageNumber) {
        StringBuilder roomBeginning = new StringBuilder();
        String roomName = decoratePageName(labelText, pageNumber);
        if (pageNumber == 1) {
            roomBeginning.append("main, ").append(roomName);
            roomBeginning.append(" = room { nam = 'main', enter = function(s) nlb:nlbwalk(s, ").append(roomName).append("); end }, ");
        } else {
            roomBeginning.append(roomName).append(" = ");
        }
        return roomBeginning.toString();
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return "-- PageNo. " + String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "-- " + comment + LINE_SEPARATOR;
    }
}
