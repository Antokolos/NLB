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
import com.nlbhub.nlb.api.TextChunk;
import com.nlbhub.nlb.domain.NonLinearBookImpl;
import com.nlbhub.nlb.exception.NLBExportException;
import com.nlbhub.nlb.util.StringHelper;

import java.util.List;
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
     * Use page numbers as destinations instead of page IDs.
     */
    private static final boolean GOTO_PAGE_NUMBERS = true;
    /**
     * Enable comments in the generated text
     */
    private static final boolean ENABLE_COMMENTS = true;

    public STEADExportManager(NonLinearBookImpl nlb, String encoding) throws NLBExportException {
        super(nlb, encoding);
    }

    @Override
    protected String generatePreambleText() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("instead_version \"2.1.1\"").append(LINE_SEPARATOR);

        stringBuilder.append("require \"xact\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"hideinv\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"para\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"dash\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"quotes\" ").append(LINE_SEPARATOR);
        stringBuilder.append("game.codepage=\"UTF-8\";").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);

        stringBuilder.append("game.act = 'Nothing happens.';").append(LINE_SEPARATOR);
        stringBuilder.append("game.inv = 'Hm.. This is strange thing..';").append(LINE_SEPARATOR);
        stringBuilder.append("game.use = 'Does not work...';").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);
        stringBuilder.append("global {").append(LINE_SEPARATOR);
        stringBuilder.append("    _lists = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    _clones = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    push = function(listname, v)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        _lists[listname] = {next = list, value = v};").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    pop = function(listname)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            _lists[listname] = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            return list.value;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    size = function(listname)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        local result = 0;").append(LINE_SEPARATOR);
        stringBuilder.append("        if list == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return 0;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("                result = result + 1;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        return result;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    shuffle = function(listname)").append(LINE_SEPARATOR);
        stringBuilder.append("        local arr = toArray(_lists[listname]);").append(LINE_SEPARATOR);
        stringBuilder.append("        _lists[listname] = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        addArr(listname, shuffled(arr));").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    addAll = function(destination, destinationList, listName)").append(LINE_SEPARATOR);
        stringBuilder.append("        local loclist = _lists[listName];").append(LINE_SEPARATOR);
        stringBuilder.append("        if loclist == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                if destination ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                    addf(destination, loclist.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                elseif destinationList ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                    push(destinationList, loclist.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                else").append(LINE_SEPARATOR);
        stringBuilder.append("                    addf(nil, loclist.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                end;").append(LINE_SEPARATOR);
        stringBuilder.append("                loclist = loclist.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until loclist == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    shuffled = function(tab)").append(LINE_SEPARATOR);
        stringBuilder.append("        local n, order, res = #tab, {}, {};").append(LINE_SEPARATOR);
        stringBuilder.append("        for i=1,n do order[i] = { rnd = math.random(), idx = i } end;").append(LINE_SEPARATOR);
        stringBuilder.append("        table.sort(order, function(a,b) return a.rnd < b.rnd end);").append(LINE_SEPARATOR);
        stringBuilder.append("        for i=1,n do res[i] = tab[order[i].idx] end;").append(LINE_SEPARATOR);
        stringBuilder.append("        return res;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    toArray = function(list)").append(LINE_SEPARATOR);
        stringBuilder.append("        local res = {}").append(LINE_SEPARATOR);
        stringBuilder.append("        local loclist = list;").append(LINE_SEPARATOR);
        stringBuilder.append("        local i = 0;").append(LINE_SEPARATOR);
        stringBuilder.append("        if loclist == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                res[i] = loclist.value;").append(LINE_SEPARATOR);
        stringBuilder.append("                loclist = loclist.next;").append(LINE_SEPARATOR);
        stringBuilder.append("                i = i + 1;").append(LINE_SEPARATOR);
        stringBuilder.append("            until loclist == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        return res;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    addArr = function(listname, arr)").append(LINE_SEPARATOR);
        stringBuilder.append("        local n = #arr").append(LINE_SEPARATOR);
        stringBuilder.append("        for i=1,n do push(listname, arr[i]) end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    addf = function(target, object)").append(LINE_SEPARATOR);
        stringBuilder.append("        if target == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if have(object) then").append(LINE_SEPARATOR);
        stringBuilder.append("                take(clone(object));").append(LINE_SEPARATOR);
        stringBuilder.append("            else").append(LINE_SEPARATOR);
        stringBuilder.append("                take(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            if exist(object, target) then").append(LINE_SEPARATOR);
        stringBuilder.append("                objs(target):add(clone(object));").append(LINE_SEPARATOR);
        stringBuilder.append("            else").append(LINE_SEPARATOR);
        stringBuilder.append("                objs(target):add(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    deepcopy = function(t)").append(LINE_SEPARATOR);
        stringBuilder.append("        local k; local v;").append(LINE_SEPARATOR);
        stringBuilder.append("        if type(t) ~= \"table\" then return t end;").append(LINE_SEPARATOR);
        stringBuilder.append("        local mt = getmetatable(t);").append(LINE_SEPARATOR);
        stringBuilder.append("        local res = {};").append(LINE_SEPARATOR);
        stringBuilder.append("        for k,v in pairs(t) do").append(LINE_SEPARATOR);
        stringBuilder.append("            if type(v) == \"table\" then").append(LINE_SEPARATOR);
        stringBuilder.append("                v = deepcopy(v)").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            res[k] = v;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        setmetatable(res,mt);").append(LINE_SEPARATOR);
        stringBuilder.append("        return res;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clone = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local ret = deepcopy(s);").append(LINE_SEPARATOR);
        stringBuilder.append("        local r = _clones[s.nam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if r == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            r = 1;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            r = r + 1;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        _clones[s.nam] = r;").append(LINE_SEPARATOR);
        stringBuilder.append("        ret.nam = s.nam..r;").append(LINE_SEPARATOR);
        stringBuilder.append("        return ret;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("}").append(LINE_SEPARATOR);
        stringBuilder.append("listobj = obj {").append(LINE_SEPARATOR);
        stringBuilder.append("    nam = \"listobj\",").append(LINE_SEPARATOR);
        stringBuilder.append("    listnam = \"\",").append(LINE_SEPARATOR);
        stringBuilder.append("    act = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.actf(s)").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    actf = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                list.value.act(list.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    use = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.usef(s, w);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    usef = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                list.value.usef(list.value, w);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    used = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.usedf(s, w);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    usedf = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                w.usef(w, list.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("}").append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    @Override
    protected String generateObjText(ObjBuildingBlocks objBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(objBlocks.getObjComment());
        }
        stringBuilder.append(objBlocks.getObjLabel()).append(objBlocks.getObjStart());
        stringBuilder.append(objBlocks.getObjName());
        stringBuilder.append(objBlocks.getObjDisp());
        stringBuilder.append(objBlocks.getObjText());
        if (objBlocks.isTakable()) {
            stringBuilder.append(objBlocks.getObjTak());
            stringBuilder.append(objBlocks.getObjInv());
        }
        stringBuilder.append(objBlocks.getObjActStart());
        boolean varsOrModsPresent = (
                !StringHelper.isEmpty(objBlocks.getObjVariable())
                        || !StringHelper.isEmpty(objBlocks.getObjModifications())
        );
        if (varsOrModsPresent) {
            stringBuilder.append(objBlocks.getObjVariable());
            stringBuilder.append(objBlocks.getObjModifications());
        }
        stringBuilder.append(objBlocks.getObjActEnd());
        List<UseBuildingBlocks> usesBuildingBlocks = objBlocks.getUseBuildingBlocks();
        final boolean hasUses = usesBuildingBlocks.size() != 0;
        if (!objBlocks.isTakable() && hasUses) {
            // Not takable but usable => scene_use should be specified
            stringBuilder.append("    scene_use = true,").append(LINE_SEPARATOR);
        }
        stringBuilder.append(objBlocks.getObjImage());
        if (objBlocks.isTakable() || hasUses) {
            // If object is takable, then empty use function should be specified
            stringBuilder.append(objBlocks.getObjUseStart());
        }
        if (hasUses) {
            StringBuilder usepBuilder = new StringBuilder();
            usepBuilder.append("    usep = function(s, w)").append(LINE_SEPARATOR);
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
                    usesStartBuilder.append(padding).append("elseif ");
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
                stringBuilder.append(useBuildingBlocks.getUseVariable()).append(LINE_SEPARATOR);
                //stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append(useBuildingBlocks.getUseModifications()).append(LINE_SEPARATOR);
                /*
                The following code does not needed. INSTEAD handles changes in variables
                automatically. But I will keep the code to show my intention :)
                stringBuilder.append(padding).append(extraPadding);
                if (ENABLE_COMMENTS) {
                    stringBuilder.append(
                        "    -- walk to the current room again in order to "
                        + "reflect changes in the variables"
                    ).append(LINE_SEPARATOR);
                }
                stringBuilder.append(padding).append(extraPadding);
                stringBuilder.append("    walk(here());").append(LINE_SEPARATOR);
                */
                if (constrained) {
                    usesEndBuilder.append(padding).append("    end;").append(LINE_SEPARATOR);
                    stringBuilder.append(usesEndBuilder);
                }

                usepBuilder.append(usesStartBuilder);
                usepBuilder.append("p \"").append(useBuildingBlocks.getUseSuccessText()).append("\";").append(LINE_SEPARATOR);
                usepBuilder.append(usesEndBuilder);
            }
            usepBuilder.append("        end;").append(LINE_SEPARATOR);
            usepBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append("        else").append(LINE_SEPARATOR);
            stringBuilder.append("            if w.used ~= nil then").append(LINE_SEPARATOR);
            stringBuilder.append("                w.used(w, s);").append(LINE_SEPARATOR);
            stringBuilder.append("            end;").append(LINE_SEPARATOR);
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append(usepBuilder);
        } else if (objBlocks.isTakable()) {
            // If object is takable, then empty use function should be specified anyway
            stringBuilder.append(objBlocks.getObjUseEnd());
        }
        List<String> containedObjIds = objBlocks.getContainedObjIds();
        if (containedObjIds.size() != 0) {
            stringBuilder.append(objBlocks.getObjObjStart());
            for (final String objString : containedObjIds) {
                stringBuilder.append(objString);
            }
            stringBuilder.append(objBlocks.getObjObjEnd());
        }
        stringBuilder.append(objBlocks.getObjEnd());
        return stringBuilder.toString();
    }

    @Override
    protected String generatePageText(PageBuildingBlocks pageBlocks) {
        StringBuilder stringBuilder = new StringBuilder();
        StringBuilder linksBuilder = new StringBuilder();
        StringBuilder autosBuilder = new StringBuilder();
        if (ENABLE_COMMENTS) {
            stringBuilder.append(pageBlocks.getPageComment());
        }
        stringBuilder.append(pageBlocks.getPageLabel());
        stringBuilder.append("    forcedsc = true,").append(LINE_SEPARATOR);
        // Do not check pageBlocks.isUseCaption() here, because in INSTEAD all rooms must have name
        stringBuilder.append(pageBlocks.getPageCaption());
        stringBuilder.append(pageBlocks.getPageImage());
        stringBuilder.append(pageBlocks.getPageTextStart());
        autosBuilder.append("    autos = function()").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            final boolean constrained = !StringHelper.isEmpty(linkBlocks.getLinkConstraint());
            if (linkBlocks.isAuto()) {
                autosBuilder.append("        ");
                if (constrained) {
                    autosBuilder.append("if (").append(linkBlocks.getLinkConstraint()).append(") then ");
                }
                autosBuilder.append(linkBlocks.getLinkVariable());
                autosBuilder.append(linkBlocks.getLinkModifications());
                autosBuilder.append(linkBlocks.getLinkGoTo());
                if (constrained) {
                    autosBuilder.append(" end;");
                }
                autosBuilder.append(LINE_SEPARATOR);
            } else {
                stringBuilder.append("        p ");
                if (constrained) {
                    stringBuilder.append("((").append(linkBlocks.getLinkConstraint()).append(") and ");
                }
                stringBuilder.append("\"").append(linkBlocks.getLinkLabel()).append("\"");
                if (constrained) {
                    stringBuilder.append(" or ");
                    stringBuilder.append("\"\")");
                }
                stringBuilder.append(";").append(LINE_SEPARATOR);
            }
        }
        autosBuilder.append("    end,").append(LINE_SEPARATOR);

        boolean varsOrModsPresent = (
                !StringHelper.isEmpty(pageBlocks.getPageVariable())
                        || !StringHelper.isEmpty(pageBlocks.getPageModifications())
        );
        stringBuilder.append(pageBlocks.getPageTextEnd());

        stringBuilder.append(autosBuilder.toString());
        // TODO: check that here() will not be used in modifications (for example, when automatically taking objects to the inventory)
        stringBuilder.append("    enter = function(s)").append(LINE_SEPARATOR);
        if (varsOrModsPresent) {
            stringBuilder.append(pageBlocks.getPageVariable());
            stringBuilder.append(pageBlocks.getPageModifications());
        }
        stringBuilder.append("        s.snd();").append(LINE_SEPARATOR);
        stringBuilder.append("        s.autos();").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageSound());

        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (!linkBlocks.isAuto()) {
                linksBuilder.append(linkBlocks.getLinkStart());
                if (ENABLE_COMMENTS) {
                    linksBuilder.append(linkBlocks.getLinkComment());
                }
                linksBuilder.append(linkBlocks.getLinkVariable());
                linksBuilder.append(linkBlocks.getLinkModifications());
                linksBuilder.append(linkBlocks.getLinkGoTo());
                linksBuilder.append(linkBlocks.getLinkEnd());
            }
        }
        String linksText = linksBuilder.toString();
        final boolean containedObjIdsIsEmpty = pageBlocks.getContainedObjIds().isEmpty();
        final boolean linksTextIsEmpty = StringHelper.isEmpty(linksText);
        if (!linksTextIsEmpty || !containedObjIdsIsEmpty) {
            stringBuilder.append("    obj = { ").append(LINE_SEPARATOR);
            if (!containedObjIdsIsEmpty) {
                for (String containedObjId : pageBlocks.getContainedObjIds()) {
                    stringBuilder.append(containedObjId);
                }
            }
            stringBuilder.append("        xdsc(),").append(LINE_SEPARATOR);
            if (!linksTextIsEmpty) {
                stringBuilder.append(linksText).append(LINE_SEPARATOR);
            }
            stringBuilder.append("    },").append(LINE_SEPARATOR);
        }
        stringBuilder.append(decoratePageEnd());
        return stringBuilder.toString();
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
    protected String decorateObjStart() {
        return " = obj {" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjName(String name) {
        return "    nam = \"" + name + "\"," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjImage(String objImagePath) {
        if (StringHelper.isEmpty(objImagePath)) {
            return Constants.EMPTY_STRING;
        } else {
            return "    imgv = function() return img('" + objImagePath + "'); " + "end," + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateObjDisp(List<TextChunk> dispChunks, boolean imageEnabled) {
        if (imageEnabled) {
            return (
                    "    disp = function(s) return [[" +
                            expandVariables(dispChunks) +
                            "]]..s.imgv() end," + LINE_SEPARATOR
            );
        } else {
            return "    disp = [[" + expandVariables(dispChunks) + "]]," + LINE_SEPARATOR;
        }
    }

    private String expandInteractionMarks(String text) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = STEAD_OBJ_PATTERN.matcher(text);
        int start = 0;
        while (matcher.find()) {
            result.append(text.substring(start, matcher.start())).append("{");
            result.append(matcher.group(1)).append("]]..s.imgv()..[[").append("}");
            start = matcher.end();
        }
        result.append(text.substring(start, text.length()));
        return result.toString();
    }

    @Override
    protected String decorateObjText(List<TextChunk> textChunks, boolean imageEnabled) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("    dsc = function(s) p([[");
        if (imageEnabled) {
            stringBuilder.append(expandInteractionMarks(expandVariables(textChunks)));
        } else {
            stringBuilder.append(expandVariables(textChunks));
        }
        stringBuilder.append("]]); end,").append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    @Override
    protected String decorateObjTak(List<TextChunk> takChunks) {
        return (
                "    tak = function(s)" + LINE_SEPARATOR +
                        "        p([[" + expandVariables(takChunks) + "]]);" + LINE_SEPARATOR +
                        "        s.act(s);" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjInv() {
        return (
                "    inv = function(s)" + LINE_SEPARATOR
                        + "        s.use(s, s);" + LINE_SEPARATOR
                        + "    end," + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjActStart(List<TextChunk> actTextChunks) {
        return (
                "    act = function(s)" + LINE_SEPARATOR +
                        "        p([[" + expandVariables(actTextChunks) + "]]);" + LINE_SEPARATOR +
                        "        s.actf(s);" + LINE_SEPARATOR +
                        "        here().autos();" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        "    actf = function(s)" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjActEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjUseStart() {
        return (
                "    use = function(s, w)" + LINE_SEPARATOR +
                        "        s.usep(s, w);" + LINE_SEPARATOR +
                        "        s.usef(s, w);" + LINE_SEPARATOR +
                        "        here().autos();" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        "    usef = function(s, w)" + LINE_SEPARATOR
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
        return "w == " + decorateId(targetId);
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
    protected String decorateDelObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName) {
        if (destinationId == null) {
            return (
                    "            if have(\"" + objectName + "\") then remove(\""
                            + objectName + "\", " + "inv()); end;" + LINE_SEPARATOR
            );
        } else {
            return (
                    "            objs(" + decorateId(destinationId) + "):del(" +
                            ((objectId != null) ? decorateId(objectId) : objectVar) +
                            ");" + LINE_SEPARATOR
            );
        }
    }

    @Override
    protected String decorateAddObj(String destinationId, String objectId, String objectVar, String objectName, String objectDisplayName) {
        return (
                "            addf(" + ((destinationId != null) ? decorateId(destinationId) : "nil") +
                        ", " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationList, String listName) {
        return (
                "        addAll(" + ((destinationId != null) ? decorateId(destinationId) : "nil") +
                        ", " + ((destinationList != null) ? "'" + destinationList + "'" : "nil") +
                        ", '" + listName + "');" + LINE_SEPARATOR +
                        createListObj(listName)
        );
    }

    @Override
    protected String decoratePushOperation(String listName, String objectId, String objectVar) {
        return (
                "        push('" +
                        listName + "', " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR +
                        createListObj(listName)
        );
    }

    private String createListObj(String listName) {
        return (
                "        if " + GLOBAL_VAR_PREFIX + listName + " == nil then" +
                        "            " + GLOBAL_VAR_PREFIX + listName + " = clone(listobj);" + LINE_SEPARATOR +
                        "            " + GLOBAL_VAR_PREFIX  + listName + ".listnam = \"" + listName + "\";" + LINE_SEPARATOR +
                        "        end;" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePopOperation(String variableName, String listName) {
        return variableName + " = pop('" + listName + "');" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listName) {
        return variableName + " = size('" + listName + "');" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateShuffleOperation(String listName) {
        return "        shuffle('" + listName + "');" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        String source = (actingObjId != null) ? decorateId(actingObjId) : actingObjVariable;
        return source + ".actf(" + source + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        String source = (sourceId != null) ? decorateId(sourceId) : sourceVariable;
        String target = (targetId != null) ? decorateId(targetId) : targetVariable;
        return source + ".usef(" + source + ", " + target + ");" + LINE_SEPARATOR;
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
        return "not";
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
    protected String decorateLinkLabel(String linkId, String linkText) {
        return "{" + decorateId(linkId) + "|" + linkText + "}^";
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "                --" + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, int pageNumber) {
        return (
                "        xact(" + LINE_SEPARATOR
                        + "            '" + decorateId(linkId) + "'," + LINE_SEPARATOR
                        + "            function(s) " + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateLinkGoTo(
            String linkId,
            String linkText,
            String linkTarget,
            int targetPageNumber
    ) {
        return (
                "                walk("
                        + (
                        GOTO_PAGE_NUMBERS
                                ? decorateId(String.valueOf(targetPageNumber))
                                : decorateId(linkTarget)
                )
                        + "); "
        );
    }

    @Override
    protected String decorateLinkEnd() {
        return (
                LINE_SEPARATOR
                        + "            end" + LINE_SEPARATOR
                        + "        )," + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePageEnd() {
        return "};" + LINE_SEPARATOR + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageVariable(String variableName) {
        String globalVar = GLOBAL_VAR_PREFIX + variableName;
        return globalVar + " = true;" + LINE_SEPARATOR;
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
    protected String decoratePageCaption(String caption) {
        if (!StringHelper.isEmpty(caption)) {
            return "    nam = \"" + caption + "\"," + LINE_SEPARATOR;
        } else {
            return "    nam = \"...\"," + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decoratePageImage(String pageImagePath) {
        if (StringHelper.isEmpty(pageImagePath)) {
            return Constants.EMPTY_STRING;
        } else {
            return "    pic = '" + pageImagePath + "';" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decoratePageSound(String pageSoundPath) {
        if (StringHelper.isEmpty(pageSoundPath)) {
            return "    snd = function() end," + LINE_SEPARATOR;
        } else {
            return (
                    "    snd = function() " + LINE_SEPARATOR +
                            (
                                    Constants.VOID.equals(pageSoundPath)
                                            ? "        stop_music();"
                                            : "        set_music('" + pageSoundPath + "', 1);"
                            ) + LINE_SEPARATOR +
                            "    end," + LINE_SEPARATOR
            );
        }
    }

    /**
     * Expands varibles from text chunks.
     *
     * @param textChunks
     * @return
     */
    private String expandVariables(List<TextChunk> textChunks) {
        StringBuilder result = new StringBuilder();
        for (final TextChunk textChunk : textChunks) {
            switch (textChunk.getType()) {
                case TEXT:
                    result.append(textChunk.getText());
                    break;
                case VARIABLE:
                    result.append("]]..");
                    result.append(GLOBAL_VAR_PREFIX).append(textChunk.getText());
                    result.append("..[[").append(LINE_SEPARATOR);
                    break;
                case NEWLINE:
                    result.append("^").append(getLineSeparator());
                    break;
            }
        }
        return result.toString();
    }

    protected String decoratePageTextStart(List<TextChunk> pageTextChunks) {
        StringBuilder pageText = new StringBuilder();
        pageText.append("    dsc = function(s)").append(LINE_SEPARATOR).append("p([[");
        pageText.append(LINE_SEPARATOR);
        pageText.append(expandVariables(pageTextChunks));
        pageText.append("]]);").append(LINE_SEPARATOR);
        pageText.append("    end,").append(LINE_SEPARATOR);
        pageText.append("    xdsc = function(s)").append(LINE_SEPARATOR);
        pageText.append("        p \"^^\";").append(LINE_SEPARATOR);
        return pageText.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageLabel(String labelText, int pageNumber) {
        StringBuilder roomBeginning = new StringBuilder();
        String roomName = GOTO_PAGE_NUMBERS ? decorateId(String.valueOf(pageNumber)) : decorateId(labelText);
        roomBeginning.append(roomName);
        if (pageNumber == 1) {
            roomBeginning.append(", main = room { nam = \"main\", enter = function(s) walk(main); end }, ");
        } else {
            roomBeginning.append(" = ");
        }
        return roomBeginning.toString() + "room {" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageNumber(int pageNumber) {
        return "-- PageNo. " + String.valueOf(pageNumber);
    }

    @Override
    protected String decoratePageComment(String comment) {
        return "-- " + comment + LINE_SEPARATOR;
    }

    private String decorateId(String id) {
        return "v_" + id.replaceAll("-", "_");
    }
}
