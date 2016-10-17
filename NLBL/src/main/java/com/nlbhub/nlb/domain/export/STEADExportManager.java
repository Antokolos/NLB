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

import java.util.Date;
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
        String title = StringHelper.notEmpty(nlbBuildingBlocks.getTitle()) ? nlbBuildingBlocks.getTitle() : "NLBB_" + new Date().toString();
        String version = StringHelper.notEmpty(nlbBuildingBlocks.getVersion()) ? nlbBuildingBlocks.getVersion() : "0.1";
        String author = StringHelper.notEmpty(nlbBuildingBlocks.getAuthor()) ? nlbBuildingBlocks.getAuthor() : "Unknown";
        stringBuilder.append("--$Name:").append(title).append("$").append(LINE_SEPARATOR);
        stringBuilder.append("--$Version:").append(version).append("$").append(LINE_SEPARATOR);
        stringBuilder.append("--$Author:").append(author).append("$").append(LINE_SEPARATOR);
        stringBuilder.append("instead_version \"2.3.0\"").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        stringBuilder.append("--package.cpath = \"./?.so\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"luapassing\"").append(LINE_SEPARATOR).append(LINE_SEPARATOR);

        stringBuilder.append("require \"xact\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"hideinv\"").append(LINE_SEPARATOR);
        stringBuilder.append("--require \"para\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"dash\"").append(LINE_SEPARATOR);
        stringBuilder.append("require \"quotes\" ").append(LINE_SEPARATOR);
        stringBuilder.append("require \"theme\" ").append(LINE_SEPARATOR);
        stringBuilder.append("require \"timer\" ").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/fonts'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/paginator'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/vn'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/big_pig'").append(LINE_SEPARATOR);
        stringBuilder.append("require 'modules/gobj'").append(LINE_SEPARATOR);
        stringBuilder.append("game.codepage=\"UTF-8\";").append(LINE_SEPARATOR);
        stringBuilder.append("stead.scene_delim = '^';").append(LINE_SEPARATOR);
        stringBuilder.append(LINE_SEPARATOR);

        stringBuilder.append("--game.act = function() curloc().lasttext = 'Nothing happens.'; p(curloc().lasttext); curloc().wastext = true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("--game.inv = function() curloc().lasttext = 'Hm... This is strange thing...'; p(curloc().lasttext); curloc().wastext = true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("--game.use = function() curloc().lasttext = 'Does not work...'; p(curloc().lasttext); curloc().wastext = true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("game.act = function() return true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("game.inv = function() return true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("game.use = function() return true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("game.forcedsc = true;").append(LINE_SEPARATOR);

        stringBuilder.append("f1 = font('fonts/STEINEMU.ttf', 32);").append(LINE_SEPARATOR);
        stringBuilder.append("fend = font('fonts/STEINEMU.ttf', 128);").append(LINE_SEPARATOR);
        stringBuilder.append("function pname(n, c)").append(LINE_SEPARATOR);
        stringBuilder.append("    return function()").append(LINE_SEPARATOR);
        stringBuilder.append("        pn(img 'blank:8x1',f1:txt(n, c, 1))").append(LINE_SEPARATOR);
        stringBuilder.append("    end").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);

        stringBuilder.append("paginator.delim = '\\n[ \\t]*\\n'").append(LINE_SEPARATOR);

        stringBuilder.append("function exec(s)").append(LINE_SEPARATOR);
        stringBuilder.append("    p('$'..s:gsub(\"\\n\", \"^\")..'$^^')").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);

        stringBuilder.append("function init()").append(LINE_SEPARATOR);
        stringBuilder.append("    statsAPI.init();").append(LINE_SEPARATOR);
        stringBuilder.append("    vn:scene(nil);").append(LINE_SEPARATOR);
        stringBuilder.append("    vn.fading = 8").append(LINE_SEPARATOR);
        stringBuilder.append("    nlbticks = stead.ticks();").append(LINE_SEPARATOR);
        stringBuilder.append("end").append(LINE_SEPARATOR);

        stringBuilder.append(generateLibraryMethods());
        return stringBuilder.toString();
    }

    protected String generateLibraryMethods() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(LINE_SEPARATOR);
        stringBuilder.append("global {").append(LINE_SEPARATOR);
        stringBuilder.append("    _fps = 25;").append(LINE_SEPARATOR);
        stringBuilder.append("    _lists = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    _clones = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    _filter = {};").append(LINE_SEPARATOR);
        stringBuilder.append("    _curloc = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("    push = function(listname, v)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        _lists[listname] = {next = list, value = v};").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    pushObjs = function(listname, v)").append(LINE_SEPARATOR);
        stringBuilder.append("        for k,vv in stead.opairs(objs(v)) do").append(LINE_SEPARATOR);
        stringBuilder.append("            local o = stead.ref(vv);").append(LINE_SEPARATOR);
        stringBuilder.append("            if (isObject(o)) then").append(LINE_SEPARATOR);
        stringBuilder.append("                push(listname, o);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
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
        stringBuilder.append("    inject = function(listname, v)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            _lists[listname] = {next = nil, value = v};").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            while list.next ~= nil do").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            list.next = {next = nil, value = v};").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    eject = function(listname)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        local prevlist = list;").append(LINE_SEPARATOR);
        stringBuilder.append("        local islast = true;").append(LINE_SEPARATOR);
        stringBuilder.append("        if list == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            while list.next ~= nil do").append(LINE_SEPARATOR);
        stringBuilder.append("                prevlist = list").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next").append(LINE_SEPARATOR);
        stringBuilder.append("                islast = false").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            prevlist.next = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("            if islast then").append(LINE_SEPARATOR);
        stringBuilder.append("                _lists[listname] = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            return list.value;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    rmv = function(listname, v)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[listname];").append(LINE_SEPARATOR);
        stringBuilder.append("        local val;").append(LINE_SEPARATOR);
        stringBuilder.append("        local prevlist = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        while list ~= nil do").append(LINE_SEPARATOR);
        stringBuilder.append("            val = list.value;").append(LINE_SEPARATOR);
        stringBuilder.append("            if (val.nlbid == v.nlbid) then").append(LINE_SEPARATOR);
        stringBuilder.append("                if (prevlist == nil) then").append(LINE_SEPARATOR);
        stringBuilder.append("                    _lists[listname] = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("                    return;").append(LINE_SEPARATOR);
        stringBuilder.append("                else").append(LINE_SEPARATOR);
        stringBuilder.append("                    prevlist.next = list.next").append(LINE_SEPARATOR);
        stringBuilder.append("                    return;").append(LINE_SEPARATOR);
        stringBuilder.append("                end;").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            prevlist = list;").append(LINE_SEPARATOR);
        stringBuilder.append("            list = list.next;").append(LINE_SEPARATOR);
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
        stringBuilder.append("    addAll = function(s, destination, destinationList, listName, unique)").append(LINE_SEPARATOR);
        stringBuilder.append("        local loclist = _lists[listName];").append(LINE_SEPARATOR);
        stringBuilder.append("        if loclist == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                if destination ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                    addf(destination, loclist.value, unique);").append(LINE_SEPARATOR);
        stringBuilder.append("                elseif destinationList ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                    push(destinationList, loclist.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                else").append(LINE_SEPARATOR);
        stringBuilder.append("                    addf(s, loclist.value, unique);").append(LINE_SEPARATOR);
        stringBuilder.append("                end;").append(LINE_SEPARATOR);
        stringBuilder.append("                loclist = loclist.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until loclist == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    shuffled = function(tab)").append(LINE_SEPARATOR);
        stringBuilder.append("        local n, order, res = #tab, {}, {};").append(LINE_SEPARATOR);
        stringBuilder.append("        for i=1,n do order[i] = { rand = rnd(n), idx = i } end;").append(LINE_SEPARATOR);
        stringBuilder.append("        table.sort(order, function(a,b) return a.rand < b.rand end);").append(LINE_SEPARATOR);
        stringBuilder.append("        for i=1,n do res[i] = tab[order[i].idx] end;").append(LINE_SEPARATOR);
        stringBuilder.append("        return res;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    toArray = function(list)").append(LINE_SEPARATOR);
        stringBuilder.append("        local res = {}").append(LINE_SEPARATOR);
        stringBuilder.append("        local loclist = list;").append(LINE_SEPARATOR);
        stringBuilder.append("        local i = 1;").append(LINE_SEPARATOR);
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
        stringBuilder.append("    usea = function(actionObject, targetObject)").append(LINE_SEPARATOR);
        stringBuilder.append("        if actionObject ~= nil and targetObject ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if actionObject.usea ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                actionObject.usea(actionObject, targetObject);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    acta = function(object)").append(LINE_SEPARATOR);
        stringBuilder.append("        if object ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if object.acta ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                object.acta(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    actf = function(object)").append(LINE_SEPARATOR);
        stringBuilder.append("        if object ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if object.actf ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                object.actf(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clear = function(object)").append(LINE_SEPARATOR);
        stringBuilder.append("        if object ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if object.clear ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                object.clear(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    addf = function(target, object, unique)").append(LINE_SEPARATOR);
        stringBuilder.append("        if target == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            if not have(object) then").append(LINE_SEPARATOR);
        stringBuilder.append("                take(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            elseif not unique then").append(LINE_SEPARATOR);
        stringBuilder.append("                take(clone(object));").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            local ores = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("            if not exist(object, target) then").append(LINE_SEPARATOR);
        stringBuilder.append("                ores = object;").append(LINE_SEPARATOR);
        stringBuilder.append("            elseif not unique then").append(LINE_SEPARATOR);
        stringBuilder.append("                ores = clone(object);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            if ores ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                ores.container = function() return target; end;").append(LINE_SEPARATOR);
        stringBuilder.append("                objs(target):add(ores);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clrcntnr = function(coll)").append(LINE_SEPARATOR);
        stringBuilder.append("        -- TODO: add implementation (set container to nil func for every obj from coll)").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    revive = function()").append(LINE_SEPARATOR);
        stringBuilder.append("        for k,v in pairs(_filter) do").append(LINE_SEPARATOR);
        stringBuilder.append("            if v then").append(LINE_SEPARATOR);
        stringBuilder.append("                local o = stead.ref(k);").append(LINE_SEPARATOR);
        stringBuilder.append("                o.revive(o);").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    deepcopy = function(t)").append(LINE_SEPARATOR);
        stringBuilder.append("        local k; local v;").append(LINE_SEPARATOR);
        stringBuilder.append("        if type(t) ~= \"table\" then return t end;").append(LINE_SEPARATOR);
        stringBuilder.append("        local mt = getmetatable(t);").append(LINE_SEPARATOR);
        stringBuilder.append("        local res = {};").append(LINE_SEPARATOR);
        stringBuilder.append("        for k,v in pairs(t) do").append(LINE_SEPARATOR);
        stringBuilder.append("            if type(v) == \"table\" and k ~= \"container\" then").append(LINE_SEPARATOR);
        stringBuilder.append("                v = deepcopy(v)").append(LINE_SEPARATOR);
        stringBuilder.append("            end;").append(LINE_SEPARATOR);
        stringBuilder.append("            res[k] = v;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        setmetatable(res,mt);").append(LINE_SEPARATOR);
        stringBuilder.append("        return res;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clone = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        if s.nlbobj == \"listobj\" then").append(LINE_SEPARATOR);
        stringBuilder.append("            return new('clonefdl(listobj, \\''..s.listnam..'\\')');").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            return new('clonef(\\''..s:deref()..'\\')');").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clonelst = function(s, listnam)").append(LINE_SEPARATOR);
        stringBuilder.append("        if s.nlbobj == \"listobj\" then").append(LINE_SEPARATOR);
        stringBuilder.append("            return new('clonefdl(listobj, \\''..listnam..'\\')');").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            return nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clonef = function(sref)").append(LINE_SEPARATOR);
        stringBuilder.append("        local s = stead.ref(sref);").append(LINE_SEPARATOR);
        stringBuilder.append("        return clonefd(s);").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clonefd = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local ret = deepcopy(s);").append(LINE_SEPARATOR);
        stringBuilder.append("        local r = _clones[s.nlbid];").append(LINE_SEPARATOR);
        stringBuilder.append("        if r == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            r = 1;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            r = r + 1;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        _clones[s.nlbid] = r;").append(LINE_SEPARATOR);
        stringBuilder.append("        ret.nam = s.nam..r;").append(LINE_SEPARATOR);
        stringBuilder.append("        return ret;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    clonefdl = function(s, listnam)").append(LINE_SEPARATOR);
        stringBuilder.append("        local ret = clonefd(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        ret.listnam = listnam;").append(LINE_SEPARATOR);
        stringBuilder.append("        return ret;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    curloc = function()").append(LINE_SEPARATOR);
        stringBuilder.append("        if _curloc == nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            return here();").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            return _curloc;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("    nlbwalk = function(s, loc)").append(LINE_SEPARATOR);
        stringBuilder.append("        if (s ~= loc) then").append(LINE_SEPARATOR);
        stringBuilder.append("            _curloc = loc;").append(LINE_SEPARATOR);
        stringBuilder.append("            walk(loc);").append(LINE_SEPARATOR);
        stringBuilder.append("            if _curloc.wastext then loc.wastext = true; end;").append(LINE_SEPARATOR);
        stringBuilder.append("        else").append(LINE_SEPARATOR);
        stringBuilder.append("            local lasttext = s.lasttext;").append(LINE_SEPARATOR);
        stringBuilder.append("            local wastext = s.wastext;").append(LINE_SEPARATOR);
        stringBuilder.append("            if s.exit ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                s.exit(s, s);").append(LINE_SEPARATOR);
        stringBuilder.append("            end").append(LINE_SEPARATOR);
        stringBuilder.append("            if s.enter ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("                s.enter(s, s);").append(LINE_SEPARATOR);
        stringBuilder.append("            end").append(LINE_SEPARATOR);
        stringBuilder.append("            s.wastext = wastext;").append(LINE_SEPARATOR);
        stringBuilder.append("            s.lasttext = lasttext;").append(LINE_SEPARATOR);
        stringBuilder.append("        end").append(LINE_SEPARATOR);
        stringBuilder.append("        vn:request_full_clear();").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("}").append(LINE_SEPARATOR).append(LINE_SEPARATOR);
        stringBuilder.append("listobj = {").append(LINE_SEPARATOR);
        stringBuilder.append("    nam = \"listobj\",").append(LINE_SEPARATOR);
        stringBuilder.append("    nlbobj = \"listobj\",").append(LINE_SEPARATOR);
        stringBuilder.append("    nlbid = \"listobj\",").append(LINE_SEPARATOR);
        stringBuilder.append("    deref = function(s) return stead.deref(listobj); end,").append(LINE_SEPARATOR);
        stringBuilder.append("    listnam = \"\",").append(LINE_SEPARATOR);
        stringBuilder.append("    clear = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local r = eject(s.listnam);").append(LINE_SEPARATOR);
        stringBuilder.append("        while r ~= nil do").append(LINE_SEPARATOR);
        stringBuilder.append("            r = eject(s.listnam);").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    act = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.acta(s)").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    acta = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.actf(s)").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    actf = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                list.value.acta(list.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    use = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.usea(s, w, w);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    usea = function(s, w, ww)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.usef(s, w, ww);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    usef = function(s, w, ww)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                list.value.usea(list.value, w, ww);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    used = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.useda(s, w);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    useda = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.usedf(s, w);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    usedf = function(s, w)").append(LINE_SEPARATOR);
        stringBuilder.append("        local list = _lists[s.listnam];").append(LINE_SEPARATOR);
        stringBuilder.append("        if list ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            repeat").append(LINE_SEPARATOR);
        stringBuilder.append("                w.usea(w, list.value, list.value);").append(LINE_SEPARATOR);
        stringBuilder.append("                list = list.next;").append(LINE_SEPARATOR);
        stringBuilder.append("            until list == nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end;").append(LINE_SEPARATOR);
        stringBuilder.append("}").append(LINE_SEPARATOR);
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
        if (hasUses) {
            stringBuilder.append(objBlocks.getObjUseStart());
        }
        if (hasUses) {
            StringBuilder usepBuilder = new StringBuilder();
            usepBuilder.append("    usep = function(s, w, ww)").append(LINE_SEPARATOR);
            usepBuilder.append("        local prevt = curloc().lasttext;").append(LINE_SEPARATOR);
            usepBuilder.append("        local wasnouses = true;").append(LINE_SEPARATOR);
            usepBuilder.append("        curloc().lasttext = \"\";").append(LINE_SEPARATOR);
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
                    usepBuilder.append("local t = \"").append(useSuccessText).append(" \"; curloc().lasttext = curloc().lasttext..t; p(t); curloc().wastext = true; wasnouses = false;").append(LINE_SEPARATOR);
                    if (StringHelper.notEmpty(useFailureText)) {
                        usepBuilder.append(padding).append("    else").append(LINE_SEPARATOR);
                        usepBuilder.append("local t = \"").append(useFailureText).append(" \"; curloc().lasttext = curloc().lasttext..t; p(t); curloc().wastext = true; wasnouses = false;").append(LINE_SEPARATOR);
                    }
                }
                usepBuilder.append(usesEndBuilder);
            }
            usepBuilder.append("        if wasnouses then curloc().lasttext = prevt; end;").append(LINE_SEPARATOR);
            usepBuilder.append("        end;").append(LINE_SEPARATOR);
            usepBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append("        if w.useda ~= nil then").append(LINE_SEPARATOR);
            stringBuilder.append("            w.useda(w, s, s);").append(LINE_SEPARATOR);
            stringBuilder.append("        end;").append(LINE_SEPARATOR);
            stringBuilder.append(objBlocks.getObjUseEnd());
            stringBuilder.append(usepBuilder);
        }
        stringBuilder.append(objBlocks.getObjCommonTo());
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
        stringBuilder.append(pageBlocks.getPageImage());
        boolean hasAnim = pageBlocks.isHasObjectsWithAnimatedImages();
        boolean hasPageAnim = pageBlocks.isHasAnimatedPageImage();
        boolean hasFastAnim = hasAnim || hasPageAnim;
        boolean timerSet =  hasFastAnim || pageBlocks.isHasPageTimer();
        stringBuilder.append("    var { time = 0; wastext = false; lasttext = nil; tag = '").append(pageBlocks.getPageDefaultTag()).append("'; ");
        stringBuilder.append("autowired = ").append(pageBlocks.isAutowired() ? "true" : "false").append("; ");
        stringBuilder.append("},").append(LINE_SEPARATOR);
        if (timerSet) {
            stringBuilder.append("    timer = function(s)").append(LINE_SEPARATOR);
            if (hasFastAnim) {
                stringBuilder.append("        if (_fps * (get_ticks() - nlbticks) <= 1000) then").append(LINE_SEPARATOR);
                stringBuilder.append("            return;").append(LINE_SEPARATOR);
                stringBuilder.append("        end").append(LINE_SEPARATOR);
                stringBuilder.append("        nlbticks = get_ticks();").append(LINE_SEPARATOR);
            }
            stringBuilder.append("        if not s.wastext then").append(LINE_SEPARATOR);
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariable()).append(LINE_SEPARATOR);
            stringBuilder.append("        end; ").append(LINE_SEPARATOR);
            if (hasPageAnim) {
                stringBuilder.append("        s.add_gobj(s); ").append(LINE_SEPARATOR);
            }
            stringBuilder.append("        local afl = s.autos(s); ").append(LINE_SEPARATOR);
            stringBuilder.append("        if (s.lasttext ~= nil) and not s.wastext then return s.lasttext; elseif afl and not s.wastext then return true; end; ").append(LINE_SEPARATOR);
            stringBuilder.append("        s.wastext = false; ").append(LINE_SEPARATOR);
            stringBuilder.append("    end,").append(LINE_SEPARATOR);
        }
        stringBuilder.append(pageBlocks.getPageTextStart());
        autosBuilder.append("    autos = function(s)").append(LINE_SEPARATOR);
        autosBuilder.append("        revive();").append(LINE_SEPARATOR);
        List<LinkBuildingBlocks> linksBlocks = pageBlocks.getLinksBuildingBlocks();
        for (final LinkBuildingBlocks linkBlocks : linksBlocks) {
            if (linkBlocks.isAuto()) {
                autosBuilder.append(generateAutoLinkCode(linkBlocks));
            }
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
        stringBuilder.append("        local sndfile = pop('").append(pageBlocks.getPageName()).append("_snds');").append(LINE_SEPARATOR);
        stringBuilder.append("        if sndfile ~= nil then").append(LINE_SEPARATOR);
        stringBuilder.append("            s.sndout(s);").append(LINE_SEPARATOR);
        stringBuilder.append("            add_sound(sndfile);").append(LINE_SEPARATOR);
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    enter = function(s, f)").append(LINE_SEPARATOR);
        stringBuilder.append("        lifeon(s);").append(LINE_SEPARATOR);
        if (pageBlocks.getTheme() == Theme.STANDARD) {
            stringBuilder.append("    dofile('theme_standard.lua');").append(LINE_SEPARATOR);
        } else if (pageBlocks.getTheme() == Theme.VN) {
            stringBuilder.append("    dofile('theme_vn.lua');").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append(getDefaultThemeSwitchExpression());
        }
        if (hasFastAnim) {
            stringBuilder.append("        nlbticks = stead.ticks();").append(LINE_SEPARATOR);
        }
        stringBuilder.append("        s.lasttext = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        s.wastext = false;").append(LINE_SEPARATOR);
        if (timerSet) {
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariableInit()).append(LINE_SEPARATOR);
        }
        stringBuilder.append("        if not (f.autowired) then").append(LINE_SEPARATOR);
        if (varsOrModsPresent) {
            stringBuilder.append(pageBlocks.getPageModifications());
            stringBuilder.append(pageBlocks.getPageVariable());
        }
        stringBuilder.append("        end;").append(LINE_SEPARATOR);
        stringBuilder.append("        s.snd(s);").append(LINE_SEPARATOR);
        stringBuilder.append("        s.add_gobj(s);").append(LINE_SEPARATOR);
        if (timerSet) {
            int timerRate = (hasFastAnim ? 1 : 200);
            stringBuilder.append("        ").append(pageBlocks.getPageTimerVariable()).append(LINE_SEPARATOR);
            // stringBuilder.append("        s.autos(s);").append(LINE_SEPARATOR); -- will be called when timer triggers
            // Timer will be triggered first time immediately after timer:set()
            stringBuilder.append("        timer:set(").append(timerRate).append(");").append(LINE_SEPARATOR);
        } else {
            stringBuilder.append("        s.autos(s);").append(LINE_SEPARATOR);
        }
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append(getGraphicalObjectAppendingExpression(pageBlocks));
        stringBuilder.append("    exit = function(s, t)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.sndout(s);").append(LINE_SEPARATOR);
        if (timerSet) {
            stringBuilder.append("        timer:stop();").append(LINE_SEPARATOR);
        }
        stringBuilder.append("        s.wastext = false;").append(LINE_SEPARATOR);
        stringBuilder.append("        s.lasttext = nil;").append(LINE_SEPARATOR);
        stringBuilder.append("        lifeoff(s);").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append("    life = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        s.autos(s);").append(LINE_SEPARATOR);
        //stringBuilder.append("        return true;").append(LINE_SEPARATOR);
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        stringBuilder.append(pageBlocks.getPageSound());

        stringBuilder.append(generateObjsCollection(pageBlocks, linksBlocks));
        stringBuilder.append(pageBlocks.getPageEnd());
        return stringBuilder.toString();
    }

    protected String getGraphicalObjectAppendingExpression(PageBuildingBlocks pageBuildingBlocks) {
        if (isVN(pageBuildingBlocks.getTheme())) {
            return m_vnsteadExportManager.getGraphicalObjectAppendingExpression(pageBuildingBlocks);
        }
        StringBuilder stringBuilder = new StringBuilder("    add_gobj = function(s)").append(LINE_SEPARATOR);
        stringBuilder.append("        local bg_img = s.bgimg(s);").append(getLineSeparator());
        final boolean imageBackground = pageBuildingBlocks.isImageBackground();
        if (pageBuildingBlocks.isHasGraphicalObjects()) {
            stringBuilder.append("        vn:turnon();").append(LINE_SEPARATOR);
            stringBuilder.append("        vn:scene(bg_img);").append(getLineSeparator());
            for (String graphicalObjId : pageBuildingBlocks.getContainedGraphicalObjIds()) {
                stringBuilder.append("        vn:gshow(" + graphicalObjId + ");").append(LINE_SEPARATOR);
            }
            stringBuilder.append("        vn:start();").append(LINE_SEPARATOR);
        } else if (imageBackground) {
            stringBuilder.append("        theme.gfx.bg(bg_img);").append(getLineSeparator());
        }
        stringBuilder.append("    end,").append(LINE_SEPARATOR);
        return stringBuilder.toString();
    }

    protected String getDefaultThemeSwitchExpression() {
        return "    dofile('theme_standard.lua');" + LINE_SEPARATOR;
    }

    protected String generateOrdinaryLinkTextInsideRoom(PageBuildingBlocks pageBuildingBlocks) {
        if (isVN(pageBuildingBlocks.getTheme())) {
            return m_vnsteadExportManager.generateOrdinaryLinkTextInsideRoom(pageBuildingBlocks);
        }
        StringBuilder linksBuilder = new StringBuilder();
        for (final LinkBuildingBlocks linkBlocks : pageBuildingBlocks.getLinksBuildingBlocks()) {
            if (!linkBlocks.isAuto()) {
                linksBuilder.append(generateOrdinaryLinkCode(linkBlocks));
            }
        }
        return linksBuilder.toString();
    }

    @Override
    protected String generatePostPageText(PageBuildingBlocks pageBlocks) {
        if (isVN(pageBlocks.getTheme())) {
            return m_vnsteadExportManager.generatePostPageText(pageBlocks);
        }
        return Constants.EMPTY_STRING;
    }

    protected String generateObjsCollection(PageBuildingBlocks pageBlocks, List<LinkBuildingBlocks> linksBlocks) {
        if (isVN(pageBlocks.getTheme())) {
            return m_vnsteadExportManager.generateObjsCollection(pageBlocks, linksBlocks);
        }
        StringBuilder result = new StringBuilder();
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
            result.append("((").append(linkBlocks.getLinkConstraint()).append(") and ");
        }
        result.append("\"").append(linkBlocks.getLinkLabel()).append("\"");
        if (constrained) {
            result.append(" or ");
            result.append("\"").append(linkBlocks.getLinkAltText()).append("\")");
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
    protected String decorateObjStart(final String id, String containerRef, ObjType objType, boolean preserved, boolean clearUnderTooltip, String objDefaultTag) {
        StringBuilder result = new StringBuilder();
        switch (objType) {
            case STAT:
                result.append(" = stat {").append(LINE_SEPARATOR);
                break;
            case MENU:
                result.append(" = menu {").append(LINE_SEPARATOR);
                break;
            case GOBJ:
                result.append(" = gobj {").append(LINE_SEPARATOR);
                if (clearUnderTooltip) {
                    result.append("clear_under_tooltip = true,").append(LINE_SEPARATOR);
                }
                if (preserved) {
                    result.append("preserved = true,").append(LINE_SEPARATOR);
                }
                break;
            case GMENU:
                result.append(" = gmenu {").append(LINE_SEPARATOR);
                if (clearUnderTooltip) {
                    result.append("clear_under_tooltip = true,").append(LINE_SEPARATOR);
                }
                if (preserved) {
                    result.append("preserved = true,").append(LINE_SEPARATOR);
                }
                break;
            default:
                result.append(" = obj {").append(LINE_SEPARATOR);
        }
        result.append("    var { tag = '").append(objDefaultTag).append("'; ").append(getContainerExpression(containerRef));
        result.append(" },").append(LINE_SEPARATOR);
        result.append("    nlbid = '").append(id).append("',").append(LINE_SEPARATOR);
        result.append("    deref = function(s) return stead.deref(").append(decorateObjLabel(id)).append("); end,").append(LINE_SEPARATOR);
        return result.toString();
    }

    @Override
    protected String decorateObjName(String name) {
        return "    nam = \"" + name + "\"," + LINE_SEPARATOR;
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
            if (objImagePathData.getMaxFrameNumber() == 0) {
                if (StringHelper.notEmpty(objImagePath)) {
                    ifTermination = "        end" + LINE_SEPARATOR;
                    resultBuilder.append(tempBuilder).append("            ");
                    resultBuilder.append("return '").append(objImagePath).append("';").append(LINE_SEPARATOR);
                }
            } else {
                ifTermination = "        end" + LINE_SEPARATOR;
                resultBuilder.append(tempBuilder).append("            ");
                resultBuilder.append("return string.format('").append(objImagePath).append("', curloc().time % ");
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

    protected String decorateObjEffect(String offsetString, String coordString, boolean graphicalObj, Obj.MovementDirection movementDirection, Obj.Effect effect) {
        boolean hasDefinedOffset = StringHelper.notEmpty(offsetString);
        String offset = hasDefinedOffset ? offsetString : "0,0";
        String pos = "left-top@" + offset;
        String steps = (effect == Obj.Effect.None) ? "" : "    maxStep = 15," + LINE_SEPARATOR + "    startFrame = 0," + LINE_SEPARATOR;
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
            return "    eff = \"" + pos + "\"," + LINE_SEPARATOR + steps +
                    (hasDefinedOffset ? "" : "    arm = { [0] = { " + coordString + " } }," + LINE_SEPARATOR);
        } else {
            return EMPTY_STRING;
        }
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
                return "    disp = function(s) end," + LINE_SEPARATOR;
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
                result.append(StringHelper.isEmpty(objName) ? objId : objName).append("|");
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
    protected String decorateObjTak(final String objName) {
        return (
                "    tak = function(s)" + LINE_SEPARATOR +
                        "        return s.act(s);" + LINE_SEPARATOR +
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
    protected String decorateObjActStart(String actTextExpanded, boolean collapsable, int curStep) {
        final boolean actTextNotEmpty = StringHelper.notEmpty(actTextExpanded);
        final String returnStatement = (actTextNotEmpty) ? "" : "        return true;" + LINE_SEPARATOR;
        String actText = getActText(actTextNotEmpty);
        String suffix = "    actf = function(s)" + LINE_SEPARATOR;
        if (collapsable) {
            suffix = (
                    ((curStep > 0) ? "    curStep = " + curStep + "," + LINE_SEPARATOR : "") +
                    "    actf = function(s)" + LINE_SEPARATOR +
                    "        local v = vn:glookup(stead.deref(s));" + LINE_SEPARATOR +
                    "        if s.is_paused then" + LINE_SEPARATOR +
                    "            vn:vpause(v, false);" + LINE_SEPARATOR +
                    "        else" + LINE_SEPARATOR +
                    "            vn:set_step(v, nil, not v.forward);" + LINE_SEPARATOR +
                    "        end" + LINE_SEPARATOR +
                    "        vn:start();" + LINE_SEPARATOR
            );
        }
        return (
                "    act = function(s)" + LINE_SEPARATOR +
                        "        s.acta(s);" + LINE_SEPARATOR +
                        returnStatement +
                        "    end," + LINE_SEPARATOR +
                        "    actt = function(s)" + LINE_SEPARATOR +
                        "        return \"" + actTextExpanded + "\";" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        "    acta = function(s)" + LINE_SEPARATOR +
                        actText +
                        "        s.actf(s);" + LINE_SEPARATOR +
                        "        s.actcmn(s);" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        suffix
        );
    }

    protected String getActText(boolean actTextNotEmpty) {
        return (
                (actTextNotEmpty)
                        ? "        curloc().lasttext = s.actt(s); p(curloc().lasttext); curloc().wastext = true;" + LINE_SEPARATOR
                        : Constants.EMPTY_STRING
        );
    }

    @Override
    protected String decorateObjActEnd() {
        return "    end," + LINE_SEPARATOR;
    }

    @Override
    protected String decorateObjUseStart() {
        // Before use, execute possible act commands (without printing act text) -> s.actf(s)
        return (
                "    use = function(s, w)" + LINE_SEPARATOR +
                        "        s.actf(s);" + LINE_SEPARATOR +
                        "        s.usea(s, w, w);" + LINE_SEPARATOR +
                        "    end," + LINE_SEPARATOR +
                        "    usea = function(s, w, ww)" + LINE_SEPARATOR +
                        "        s.usep(s, w, ww);" + LINE_SEPARATOR +
                        "        s.usef(s, w, ww);" + LINE_SEPARATOR +
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
            result.append("    life = function(s)").append(LINE_SEPARATOR);
            result.append("        if not (").append(constraintValue).append(") then").append(LINE_SEPARATOR);
            result.append("            _filter[").append("stead.deref(s)").append("] = true;").append(LINE_SEPARATOR);
            result.append("            s:disable();").append(LINE_SEPARATOR);
            result.append("        end;").append(LINE_SEPARATOR);
            result.append("    end,").append(LINE_SEPARATOR);
            result.append("    revive = function(s)").append(LINE_SEPARATOR);
            result.append("        if ").append(constraintValue).append(" then").append(LINE_SEPARATOR);
            result.append("            _filter[").append("stead.deref(s)").append("] = false;").append(LINE_SEPARATOR);
            result.append("            s:enable();").append(LINE_SEPARATOR);
            result.append("        end;").append(LINE_SEPARATOR);
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
            result.append("        ").append("w.usea(w, ").append(id).append(", s);").append(LINE_SEPARATOR);
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
    protected String decorateReturn() {
        return "return;" + LINE_SEPARATOR;
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
            return variableName + " = clone(" + decorateId(objId) + ");" + LINE_SEPARATOR;
        } else if (objVar != null) {
            return variableName + " = clone(" + objVar + ");" + LINE_SEPARATOR;
        } else {
            return variableName + " = clone(s);" + LINE_SEPARATOR;
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
                return "            rmv(\"" + destinationName + "\", " + objToDel + "); " + getClearContainerStatement(objToDel) + LINE_SEPARATOR;
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
                "            addf(" + ((destinationId != null) ? decorateId(destinationId) : "s") +
                        ", " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        (unique ? ", true" : ", false") +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddInvObj(String objectId, String objectVar, String objectName, String objectDisplayName) {
        return (
                "            addf(nil, " + ((objectId != null) ? decorateId(objectId) : objectVar) + ", false);" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateAddAllOperation(String destinationId, String destinationListVariableName, String sourceListVariableName, boolean unique) {
        return (
                createListObj(destinationListVariableName) +
                createListObj(sourceListVariableName) +
                        "        addAll(s, " + ((destinationId != null) ? decorateId(destinationId) : "nil") +
                        ", " + ((destinationListVariableName != null) ? "(" + destinationListVariableName + " ~= nil) and " + destinationListVariableName + ".listnam or \"\"" : "nil") +
                        ", " + "(" + sourceListVariableName + " ~= nil) and " + sourceListVariableName + ".listnam or \"\"" +
                        (unique ? ", true" : ", false") +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateObjsOperation(String listVariableName, String srcObjId, String objectVar) {
        return (
                createListObj(listVariableName) +
                        "        pushObjs(" +
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
        return createListObj(listVariableName) + "        push(" + listVariableName + ".listnam, s);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateWPushOperation(String listVariableName) {
        return createListObj(listVariableName) + "        push(" + listVariableName + ".listnam, ww);  -- will push nil if undef" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePushOperation(String listVariableName, String objectId, String objectVar) {
        return (
                createListObj(listVariableName) +
                "        push(" +
                        listVariableName + ".listnam, " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    private String createListObj(String listVariableName) {
        if (listVariableName != null) {
            return (
                    "        if " + listVariableName + " == nil then" + LINE_SEPARATOR +
                            "            stead.add_var { " + listVariableName + " = clonelst(listobj, \"" + listVariableName + "\"); }" + LINE_SEPARATOR +
                            "        end;" + LINE_SEPARATOR
            );
        } else {
            return Constants.EMPTY_STRING;
        }
    }

    @Override
    protected String decoratePopOperation(String variableName, String listVariableName) {
        // TODO: handle pops from nonexistent lists
        return variableName + " = pop(" + listVariableName + ".listnam);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSInjectOperation(String listVariableName) {
        return createListObj(listVariableName) + "        inject(" + listVariableName + ".listnam, s);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateInjectOperation(String listVariableName, String objectId, String objectVar) {
        return (
                createListObj(listVariableName) +
                        "        inject(" +
                        listVariableName + ".listnam, " + ((objectId != null) ? decorateId(objectId) : objectVar) +
                        ");" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decorateEjectOperation(String variableName, String listVariableName) {
        // TODO: handle ejects from nonexistent lists
        return variableName + " = eject(" + listVariableName + ".listnam);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateClearOperation(String destinationId, String destinationVar) {
        if (destinationId != null) {
            return "clrcntnr(objs(" + decorateId(destinationId) + ")); " + "objs(" + decorateId(destinationId) + "):zap();" + LINE_SEPARATOR;
        } else if (destinationVar != null) {
            return "clear(" + destinationVar + ");" + LINE_SEPARATOR;
        } else {
            return "clrcntnr(objs()); objs():zap();" + LINE_SEPARATOR;
        }
    }

    @Override
    protected String decorateClearInvOperation() {
        return "inv():zap();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateSizeOperation(String variableName, String listVariableName) {
        return "if " + listVariableName + " ~= nil then " + variableName + " = size(" + listVariableName + ".listnam) else " + variableName + " = 0 end;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateRndOperation(String variableName, String maxValue) {
        return variableName + " = rnd(" + maxValue + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateAchieveOperation(String achievementName) {
        // TODO: implement
        return "statsAPI.setAchievement('" + achievementName + "', true);" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateShuffleOperation(String listVariableName) {
        return (
                "        if (" + listVariableName + " ~= nil) then" + LINE_SEPARATOR +
                        "            shuffle(" + listVariableName + ".listnam);" + LINE_SEPARATOR +
                        "        end;" + LINE_SEPARATOR
        );
    }

    @Override
    protected String decoratePRNOperation(String variableName) {
        return "curloc().lasttext = curloc().lasttext.." + variableName + "; p(" + variableName + "); curloc().wastext = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateDSCOperation(String resultVariableName, String dscObjVariable, String dscObjId) {
        return resultVariableName + " = " + ((dscObjId != null) ? decorateId(dscObjId) : dscObjVariable) + ":dscf();" + LINE_SEPARATOR;
    }

    @Override
    protected String decoratePDscOperation(String objVariableName) {
        return "curloc().lasttext = curloc().lasttext..\" \".." + objVariableName + ":dscf(); p(" + objVariableName + ":dscf()); curloc().wastext = true;" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActOperation(String actingObjVariable, String actingObjId) {
        String source = (actingObjId != null) ? decorateId(actingObjId) : actingObjVariable;
        return "acta(" + source + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActtOperation(String resultVariableName, String actObjVariable, String actObjId) {
        return resultVariableName + " = " + ((actObjId != null) ? decorateId(actObjId) : actObjVariable) + ":actt();" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateActfOperation(String actingObjVariable, String actingObjId) {
        String source = (actingObjId != null) ? decorateId(actingObjId) : actingObjVariable;
        return "actf(" + source + ");" + LINE_SEPARATOR;
    }

    @Override
    protected String decorateUseOperation(String sourceVariable, String sourceId, String targetVariable, String targetId) {
        String source = (sourceId != null) ? decorateId(sourceId) : sourceVariable;
        String target = (targetId != null) ? decorateId(targetId) : targetVariable;
        return "usea(" + source + ", " + target + ");" + LINE_SEPARATOR;
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
    protected String decorateLinkAltText(String text) {
        if (StringHelper.isEmpty(text)) {
            return Constants.EMPTY_STRING;
        } else {
            return text + "^";
        }
    }

    @Override
    protected String decorateLinkLabel(String linkId, String linkText, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkLabel(linkId, linkText, theme);
        }
        return "{" + decorateId(linkId) + "|" + linkText + "}^";
    }

    @Override
    protected String decorateLinkComment(String comment) {
        return "                --" + comment + LINE_SEPARATOR;
    }

    @Override
    protected String decorateLinkStart(String linkId, String linkText, boolean isAuto, boolean isTrivial, int pageNumber, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkStart(linkId, linkText, isAuto, isTrivial, pageNumber, theme);
        }
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
            int targetPageNumber,
            Theme theme
    ) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkGoTo(linkId, linkText, linkTarget, targetPageNumber, theme);
        }
        return "                nlbwalk(s, " + decoratePageName(linkTarget, targetPageNumber) + "); ";
    }

    @Override
    protected String decorateLinkEnd(Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decorateLinkEnd(theme);
        }
        return (
                LINE_SEPARATOR
                        + "            end" + LINE_SEPARATOR
                        + "        )," + LINE_SEPARATOR
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
    protected String decoratePageCaption(String caption, boolean useCaption) {
        if (StringHelper.notEmpty(caption) && useCaption) {
            return "    nam = \"" + caption + "\"," + LINE_SEPARATOR;
        } else {
            return "    nam = \"...\"," + LINE_SEPARATOR;
        }
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
        bgimgBuilder.append(bgimgIfTermination).append("    end,").append(LINE_SEPARATOR);
        picBuilder.append(picIfTermination).append("    end,").append(LINE_SEPARATOR);
        return bgimgBuilder.toString() + picBuilder.toString();
    }

    private String decorateImagePath(String imagePath, int maxFrameNumber) {
        if (maxFrameNumber > 0) {
            return "string.format('" + imagePath + "', curloc().time % " + maxFrameNumber + " + 1)";
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
                        result.append("            push('").append(pageName).append("_snds").append("', '").append(pageSoundPath).append("');").append(LINE_SEPARATOR);
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

    protected String getGlobalVarPrefix() {
        return GLOBAL_VAR_PREFIX;
    }

    protected String decoratePageTextStart(String labelText, int pageNumber, List<TextChunk> pageTextChunks, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageTextStart(labelText, pageNumber, pageTextChunks, theme);
        }
        StringBuilder pageText = new StringBuilder();
        pageText.append("    dsc = function(s)").append(LINE_SEPARATOR);
        pageText.append("p(\"");
        if (pageTextChunks.size() > 0) {
            pageText.append(expandVariables(pageTextChunks, theme));
        }
        pageText.append("\");").append(LINE_SEPARATOR);
        pageText.append("    end,").append(LINE_SEPARATOR);
        pageText.append("    xdsc = function(s)").append(LINE_SEPARATOR);
        pageText.append("        p \"^\";").append(LINE_SEPARATOR);
        return pageText.toString();
    }

    @Override
    protected String getLineSeparator() {
        return LINE_SEPARATOR;
    }

    @Override
    protected String decoratePageTextEnd(String labelText, int pageNumber, Theme theme) {
        if (isVN(theme)) {
            return m_vnsteadExportManager.decoratePageTextEnd(labelText, pageNumber, theme);
        }
        return "    end," + LINE_SEPARATOR;
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
            roomBeginning.append(" = room { nam = \"main\", enter = function(s) nlbwalk(s, ").append(roomName).append("); end }, ");
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
