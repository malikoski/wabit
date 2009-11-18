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

package ca.sqlpower.wabit;

import ca.sqlpower.object.SPObject;

public interface WabitObject extends SPObject {

    /**
     * Returns the parent of this WabitObject. This will be null when the object
     * is first created until it is added as a child to another object. If this
     * object is never added as a child to another object this will remain null
     * and the object may be treated as the root node of a {@link WabitObject}
     * tree.
     * 
     * @return The parent of this object.
     */
    WabitObject getParent();

}
