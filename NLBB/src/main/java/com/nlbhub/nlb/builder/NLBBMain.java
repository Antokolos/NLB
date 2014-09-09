/**
 * @(#)NLBBMain.java
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
 * Copyright (c) 2014 Anton P. Kolosov All rights reserved.
 */
package com.nlbhub.nlb.builder;

import com.nlbhub.nlb.builder.form.MainFrame;
import com.nlbhub.nlb.domain.NonLinearBookFacade;
import com.nlbhub.nlb.web.Launcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * The NLBBMain class represents main class used to start the application.
 *
 * @author Anton P. Kolosov
 * @version 1.0 7/6/12
 */
public class NLBBMain implements Runnable {
    private final NonLinearBookFacade m_nlbFacade;
    private final Launcher m_launcher = new Launcher();

    public NLBBMain() {
        m_nlbFacade = new NonLinearBookFacade();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new NLBBMain());
    }

    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    private void createAndShowGUI(
    ) throws
            ClassNotFoundException,
            UnsupportedLookAndFeelException,
            IllegalAccessException,
            InstantiationException {

        //Create and set up the window.
        final JFrame frame = new JFrame("Non-Linear Book Builder");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                if (m_nlbFacade.hasChanges()) {
                    String ObjButtons[] = {"No", "Yes"};
                    int PromptResult = JOptionPane.showOptionDialog(
                            frame,
                            "There are unsaved changes. Are you sure you want to exit?",
                            "Non-Linear Book Builder",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            null,
                            ObjButtons,
                            ObjButtons[0]);
                    if (PromptResult == 1) {
                        System.exit(0);
                    }
                } else {
                    System.exit(0);
                }
            }
        });

        m_nlbFacade.createNewBook();
        MainFrame mf = new MainFrame(m_nlbFacade, m_launcher);

        final Container container = frame.getContentPane();
        JComponent component = mf.$$$getRootComponent$$$();
        container.add(component);
        JMenuBar menuBar = createMenuBar();
        // TODO: uncomment the following line to use the menu
        //frame.setJMenuBar(menuBar);
        mf.serrext();
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // If Nimbus is not available, you can set the GUI to another look and feel.
        }
        SwingUtilities.updateComponentTreeUI(component);
        SwingUtilities.updateComponentTreeUI(menuBar);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

    private static JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem("New    CTRL+N"));
        menuBar.add(file);
        return menuBar;
    }

    @Override
    public void run() {
        try {
            createAndShowGUI();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (UnsupportedLookAndFeelException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IllegalAccessException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (InstantiationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
