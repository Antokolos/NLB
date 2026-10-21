/**
 * @(#)PlayerServer.java
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
package com.nlbhub.nlb.web.player;

import com.nlbhub.nlb.api.PropertyManager;
import com.nlbhub.nlb.api.config.Export;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import java.io.File;

/**
 * Веб-плеер отдельно от редактора: сервер для библиотеки книг (каталог, в котором каталоги книг).
 * <pre>
 *   java -cp "lib/*" com.nlbhub.nlb.web.player.PlayerServer &lt;каталог книг&gt; [--port 8111] [--host localhost]
 *        [--config cfg/config.xml]
 * </pre>
 * Плеер — на http://&lt;host&gt;:&lt;port&gt;/play/
 *
 * @author Anton P. Kolosov
 * @version 1.0
 */
public final class PlayerServer {
    /**
     * Путь сервлета плеера в контексте
     */
    public static final String PATH = "/play/*";

    private PlayerServer() {
    }

    /**
     * Добавить веб-плеер в контекст сервлетов (так его подключает и Launcher редактора)
     */
    public static void addTo(ServletContextHandler context, BookLibrary library, Export export) {
        context.addServlet(new ServletHolder(new PlayerServlet(library, export)), PATH);
    }

    /**
     * Сервер плеера (ещё не запущенный); port 0 — любой свободный
     */
    public static Server create(BookLibrary library, Export export, String host, int port) {
        Server server = new Server();
        ServerConnector http = new ServerConnector(server);
        http.setHost(host);
        http.setPort(port);
        server.addConnector(http);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        addTo(context, library, export);
        server.setHandler(context);
        return server;
    }

    public static void main(String[] args) throws Exception {
        String root = null;
        String host = "localhost";
        int port = 8111;
        String config = "cfg/config.xml";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--port") && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            } else if (args[i].equals("--host") && i + 1 < args.length) {
                host = args[++i];
            } else if (args[i].equals("--config") && i + 1 < args.length) {
                config = args[++i];
            } else {
                root = args[i];
            }
        }
        if (root == null || !new File(root).isDirectory()) {
            System.out.println("Usage: PlayerServer <books directory> [--port 8111] [--host localhost] [--config cfg/config.xml]");
            System.exit(1);
        }
        Export export = null;
        if (new File(config).isFile()) {
            export = PropertyManager.readSettings(new File(config)).getDefaultConfig().getExport();
        }
        Server server = create(new BookLibrary(new File(root)), export, host, port);
        server.start();
        System.out.println("NLB web player: http://" + host + ":" + port + "/play/");
        server.join();
    }
}
