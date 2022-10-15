/**
 * @(#)Launcher.java
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
package com.nlbhub.nlb.web;

import com.nlbhub.nlb.api.NonLinearBook;
import com.nlbhub.nlb.web.exception.LauncherException;
import com.nlbhub.nlb.web.service.rest.GetNLBDataService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 * The Launcher class
 *
 * @author Anton P. Kolosov
 * @version 1.0 11/13/13
 */
public class Launcher implements Runnable {
    private Server m_server = new Server();

    public Launcher() {
        // set the TransformFactory to use the Saxon TransformerFactoryImpl method
        System.setProperty(
                "javax.xml.transform.TransformerFactory",
                "net.sf.saxon.TransformerFactoryImpl"
        );

        ServerConnector http = new ServerConnector(m_server);
        http.setHost("localhost");
        http.setPort(8111);
        http.setIdleTimeout(30000);
        m_server.addConnector(http);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        m_server.setHandler(context);
        /*
        TODO: FIX
        ServletHolder holder = (
                context.addServlet(org.apache.wink.server.internal.servlet.RestServlet.class, "/nlb/*")
        );
        holder.setInitParameter(
                "javax.ws.rs.Application",
                "com.nlbhub.nlb.web.service.rest.NLBServiceWebApplication"
        );
        */
    }

    private void start() throws LauncherException {
        try {
            if (m_server.isStopped()) {
                m_server.start();
                m_server.join();
            } else {
                throw new LauncherException("Cannot start server because it is not stopped");
            }
        } catch (Exception e) {
            throw new LauncherException("Exception during server start", e);
        }
    }

    public void stop() throws LauncherException {
        try {
            if (m_server.isRunning()) {
                m_server.stop();
            } else {
                throw new LauncherException("Cannot stop server because it is not running");
            }
        } catch (Exception e) {
            throw new LauncherException("Exception during server stop", e);
        }
    }

    public boolean isRunning() {
        return m_server.isRunning();
    }

    public void setNLBLibraryRoot(final String path) throws LauncherException {
        if (m_server.isRunning()) {
            throw new LauncherException("Cannot change NLB library root while server is running!");
        }
        GetNLBDataService.s_nlbLibraryRoot = path + "/";
    }

    public void putNLBInMemoryToCache(
            final String bookId,
            final NonLinearBook nlb
    ) {
        GetNLBDataService.putNLBInMemoryToCache(bookId, nlb);
    }

    public void clearNLBCache() {
        GetNLBDataService.clearNLBCache();
    }

    @Override
    public void run() {
        try {
            start();
        } catch (LauncherException e) {
            // TODO: Log exception information
        }
    }
}