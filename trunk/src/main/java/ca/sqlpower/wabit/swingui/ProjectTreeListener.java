/*
 * Copyright (c) 2008, SQL Power Group Inc.
 *
 * This file is part of Wabit.
 *
 * Wabit is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Wabit is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 */

package ca.sqlpower.wabit.swingui;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import org.apache.log4j.Logger;

import ca.sqlpower.sql.SPDataSource;
import ca.sqlpower.swingui.SPSUtils;
import ca.sqlpower.swingui.db.DatabaseConnectionManager;
import ca.sqlpower.wabit.swingui.action.AddDataSourceAction;
import ca.sqlpower.wabit.swingui.action.NewLayoutAction;
import ca.sqlpower.wabit.swingui.action.NewQueryAction;

/**
 * This listener is the main listener on the project tree in Wabit.
 * It will listen and handle all tree events from creating new elements
 * to changing the view based on selected nodes.
 */
public class ProjectTreeListener extends MouseAdapter {
	
	private static final Logger logger = Logger.getLogger(ProjectTreeListener.class);
	
	DatabaseConnectionManager dbConnectionManager;
	private final WabitSwingSession session;

	public ProjectTreeListener(WabitSwingSession session) {
		this.session = session;
		dbConnectionManager = new DatabaseConnectionManager(session.getContext().getDataSources());
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		maybeShowPopup(e);
	}

	@Override
	public void mousePressed(MouseEvent e) {
		maybeShowPopup(e);
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		JTree t = (JTree) e.getSource();
		int row = t.getRowForLocation(e.getX(), e.getY());
		TreePath tp = t.getPathForRow(row);
		Object lastPathComponent = null;
		if (tp != null) {
			lastPathComponent = tp.getLastPathComponent();
			logger.debug("Clicked on " + lastPathComponent.getClass());
		}
		if (e.isPopupTrigger()) {
			maybeShowPopup(e);
		} else if (lastPathComponent != null) {
			session.setEditorPanel(lastPathComponent);
		}
	}

	/**
	 * This will Display a List of options once you right click on the ProjectTree.
	 */
	private void maybeShowPopup(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}

		JPopupMenu menu = new JPopupMenu();
		menu.add(new AbstractAction("Database Connection Manager...") {

			public void actionPerformed(ActionEvent e) {
				dbConnectionManager.showDialog(session.getFrame());
			}
		});

		menu.add(createDataSourcesMenu());

		menu.addSeparator();

		menu.add(new NewQueryAction(session.getProject()));

		menu.add(new NewLayoutAction(session.getProject()));

		menu.show(e.getComponent(), e.getX(), e.getY());
	}
	
    /**
     * Creates a JMenu with an item for each data source defined in the context's
     * data source collection. When one of these items is selected, it invokes an
     * action that adds that data source to the project. 
     */
	public JMenu createDataSourcesMenu() {
        JMenu dbcsMenu = new JMenu("Add data source"); //$NON-NLS-1$
//        dbcsMenu.add(new JMenuItem(new NewDataSourceAction(this)));
//        dbcsMenu.addSeparator();

        for (SPDataSource dbcs : session.getContext().getDataSources().getConnections()) {
            dbcsMenu.add(new JMenuItem(new AddDataSourceAction(session.getProject(), dbcs)));
        }
        SPSUtils.breakLongMenu(session.getFrame(), dbcsMenu);
        
        return dbcsMenu;
	}

}
