/**
 * @(#)ExportPreviewServer.java
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
package com.nlbhub.nlb.web.preview;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.File;

/**
 * Сервер проверки экспортов в браузере без редактора (ChoiceScript, jsIQ2, QSP, URQ, AXMA, см.
 * {@link ExportPreview}).
 * <pre>
 *   export-preview.sh [каталог] [--port 8112] [--host localhost]
 *   java -cp "lib/*" com.nlbhub.nlb.web.preview.ExportPreviewServer [каталог] [--port 8112]
 * </pre>
 * Страница — http://&lt;host&gt;:&lt;port&gt;/preview/; каталог по умолчанию — export-preview.
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class ExportPreviewServer {
    private ExportPreviewServer() {
    }

    /** Добавить /preview/ в контекст сервлетов (так его подключают сервер плеера и редактор) */
    public static void addTo(ServletContextHandler context, ExportPreview preview) {
        context.addServlet(new ServletHolder(new ExportPreviewServlet(preview)), ExportPreviewServlet.PATH);
    }

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 8112;
        ExportPreview preview = ExportPreview.defaultPreview();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[++i];
            } else {
                preview = new ExportPreview(new File(args[i]).getAbsoluteFile());
            }
        }
        preview.createDirs();
        System.out.println("Каталог: " + preview.getRoot());
        System.out.println("  ChoiceScript: " + preview.getChoiceScriptDir() + "  (startup.txt и images)");
        System.out.println("  jsIQ2:        " + preview.getJsiqDir() + "  (.xml)");
        System.out.println("  QSP:          " + preview.getQspDir() + "  (book.txt, images, sound)");
        System.out.println("  URQ:          " + preview.getUrqDir() + "  (book.qst, images, sound)");
        System.out.println("  AXMA:         " + preview.getAxmaDir() + "  (book.sm, images, sound)");
        try {
            preview.ensureRuntime();
        } catch (Exception e) {
            System.out.println("Движки пока не скачаны (" + e.getMessage() + "); сервер попробует ещё раз при открытии страницы");
        }
        Server server = new Server();
        ServerConnector http = new ServerConnector(server);
        http.setHost(host);
        http.setPort(port);
        server.addConnector(http);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        addTo(context, preview);
        server.setHandler(context);
        server.start();
        System.out.println("Проверка экспорта: http://" + host + ":" + port + "/preview/");
        server.join();
    }
}
