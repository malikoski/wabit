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

package ca.sqlpower.wabit.swingui.tree;

import java.awt.Component;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import org.apache.log4j.Logger;

import ca.sqlpower.architect.swingui.dbtree.DBTreeCellRenderer;
import ca.sqlpower.swingui.ComposedIcon;
import ca.sqlpower.wabit.QueryCache;
import ca.sqlpower.wabit.WabitDataSource;
import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.report.ContentBox;
import ca.sqlpower.wabit.report.Guide;
import ca.sqlpower.wabit.report.Layout;
import ca.sqlpower.wabit.report.Page;

public class ProjectTreeCellRenderer extends DefaultTreeCellRenderer {
	
	private static final Logger logger = Logger.getLogger(ProjectTreeCellRenderer.class);

    public static final Icon PAGE_ICON = new ImageIcon(ProjectTreeCellRenderer.class.getResource("/icons/page_white.png"));
    public static final Icon LAYOUT_ICON = new ImageIcon(ProjectTreeCellRenderer.class.getResource("/icons/layout.png"));
    public static final Icon BOX_ICON = new ImageIcon(ProjectTreeCellRenderer.class.getResource("/icons/shape_square.png"));
    public static final Icon QUERY_ICON = new ImageIcon(ProjectTreeCellRenderer.class.getClassLoader().getResource("icons/wabit_query.png"));
    public static final Icon STREAMING_QUERY_BADGE = new ImageIcon(ProjectTreeCellRenderer.class.getClassLoader().getResource("icons/stream-badge.png"));
    
    /**
     * This map contains {@link WabitObject}s that have an image or badge
     * that is currently moving. The Integer value notes at what point in
     * the sequence the image should be.
     */
    private final Map<WabitObject, Integer> objectToTimedImageMap = new HashMap<WabitObject, Integer>();
    
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        ProjectTreeCellRenderer r = (ProjectTreeCellRenderer) super.getTreeCellRendererComponent(
                tree, value, sel, expanded, leaf, row, hasFocus);
        
        if (value instanceof WabitObject) {
            WabitObject wo = (WabitObject) value;
            
            r.setText(wo.getName());

            if (wo instanceof WabitDataSource) {
                r.setIcon(DBTreeCellRenderer.DB_ICON);
            } else if (wo instanceof Page) {
                Page page = (Page) wo;
                r.setIcon(PAGE_ICON);
                r.setText(page.getName() + " (" + page.getWidth() + "x" + page.getHeight() + ")");
            } else if (wo instanceof Layout) {
                r.setIcon(LAYOUT_ICON);
            } else if (wo instanceof ContentBox) {
                ContentBox cb = (ContentBox) wo;
                r.setIcon(BOX_ICON);
                r.setText(cb.getName() + " ("+cb.getX()+","+cb.getY()+" "+cb.getWidth()+"x"+cb.getHeight()+")");
            } else if (wo instanceof Guide) {
            	Guide g = (Guide) wo;
            	r.setText(g.getName() + " @" + g.getOffset());
            } else if (wo instanceof QueryCache) {
            	if (((QueryCache) wo).isRunning()) {
            		if (((QueryCache) wo).isStreaming()) {
            			r.setIcon(new ComposedIcon(Arrays.asList(new Icon[]{QUERY_ICON, STREAMING_QUERY_BADGE})));
            		} else {
            			if (objectToTimedImageMap.containsKey(wo)) {
            				logger.debug("The image for " + wo + " should be at position " + objectToTimedImageMap.get(wo));
            				int imageNumber = (objectToTimedImageMap.get(wo) % 12) + 1;
            				final String imageURL = "icons/throbber-badge_" + imageNumber + ".png";
            				logger.debug("Loading image: " + imageURL);
							r.setIcon(new ComposedIcon(Arrays.asList(new Icon[]{QUERY_ICON, new ImageIcon(ProjectTreeCellRenderer.class.getClassLoader().getResource(imageURL))})));
            			} else { 
            				r.setIcon(QUERY_ICON);
            			}
            		}
            	} else {
            		r.setIcon(QUERY_ICON);
            	}
            }

        }
        return r;
    }


	public void updateTimer(WabitObject object, Integer newValue) {
		logger.debug("Received update event of " + newValue);
		objectToTimedImageMap.put(object, newValue);
	}
	
	public void removeTimer(WabitObject object) {
		objectToTimedImageMap.remove(object);
	}
}
