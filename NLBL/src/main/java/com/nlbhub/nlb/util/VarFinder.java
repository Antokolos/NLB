/**
 * @(#)VarFinder.java
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

import com.nlbhub.nlb.exception.NLBConsistencyException;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NodeVisitor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;


public class VarFinder {

    /**
     * For script "(V1ND < 0 ? Math.abs(V1ND) : 0)" output will be [V1ND, abs, Math]
     *
     * @param script
     * @return
     */
    public static Collection<String> findVariableNames(final String script) throws NLBConsistencyException {
        try {
            final Set<String> names = new HashSet<String>();
            class Visitor implements NodeVisitor {
                @Override
                public boolean visit(AstNode node) {
                    if (node instanceof Name) {
                        names.add(node.getString());
                    }
                    return true;
                }
            }
            AstNode node = new Parser().parse(script, "<cmd>", 1);
            node.visit(new Visitor());
            return names;
        } catch (Exception e) {
            throw new NLBConsistencyException("Exception while processing formula '" + script + "': " + e.getMessage());
        }
    }
}