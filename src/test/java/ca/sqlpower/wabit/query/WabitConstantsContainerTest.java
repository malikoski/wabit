/*
 * Copyright (c) 2009, SQL Power Group Inc.
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

package ca.sqlpower.wabit.query;

import ca.sqlpower.query.StringItem;
import ca.sqlpower.sqlobject.StubSQLDatabaseMapping;
import ca.sqlpower.wabit.AbstractWabitObjectTest;
import ca.sqlpower.wabit.QueryCache;
import ca.sqlpower.wabit.WabitConstantItem;
import ca.sqlpower.wabit.WabitConstantsContainer;
import ca.sqlpower.wabit.WabitObject;

public class WabitConstantsContainerTest extends AbstractWabitObjectTest {
    
    private WabitConstantsContainer container;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        
        QueryCache query = new QueryCache(new StubSQLDatabaseMapping());
        container = query.getWabitConstantsContainer();
        
        getWorkspace().addChild(query, 0);
    }

    @Override
    public WabitObject getObjectUnderTest() {
        return container;
    }

    public void testAddAndRemoveChild() throws Exception {
        StringItem item = new StringItem("item");
        WabitConstantItem wabitItem = new WabitConstantItem(item);
        
        assertFalse(container.getChildren().contains(wabitItem));
        
        container.addChild(wabitItem, 0);
        
        assertTrue(container.getChildren().contains(wabitItem));
        assertTrue(container.getDelegate().getItems().contains(item));
        
        container.removeChild(wabitItem);
        
        assertFalse(container.getChildren().contains(wabitItem));
        assertFalse(container.getDelegate().getItems().contains(item));
    }
    
}
