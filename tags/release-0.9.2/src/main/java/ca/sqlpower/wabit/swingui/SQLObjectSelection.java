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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Collection;

import ca.sqlpower.architect.SQLObject;

/**
 * A transferable implementation that passes a reference to an array of SQLObjects.
 */
public class SQLObjectSelection implements Transferable {

    /**
     * Data flavour that indicates a JVM-local reference to an ArrayList containing
     * 0 or more SQLObjects.
     */
    public static final DataFlavor LOCAL_SQLOBJECT_ARRAY_FLAVOUR =
        new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType +
                "; class=\"[Lca.sqlpower.architect.SQLObject;\"", "Local Array of SQLObject");
    
    /**
     * These are the actual SQLObjects we're transfering.
     */
    private final SQLObject[] sqlObjects;

    /**
     * Creates a new transferable for the given collection of SQLObjects.
     * 
     * @param sqlObjects
     *            converted to an array in the order the elements are returned
     *            by the iterator.
     */
    public SQLObjectSelection(Collection<SQLObject> sqlObjects) {
        this(sqlObjects.toArray(new SQLObject[sqlObjects.size()]));
    }

    /**
     * Creates a new transferable for the given array of SQLObjects.
     * 
     * @param sqlObjects
     *            the exact array that will be returned by
     *            {@link #getTransferData(DataFlavor)}. Must not be null, but
     *            can have a length of 0.
     */
    public SQLObjectSelection(SQLObject[] sqlObjects) {
        if (sqlObjects == null) {
            throw new NullPointerException("Can't transfer a null array. Try an empty one instead!");
        }
        this.sqlObjects = sqlObjects;
    }
    
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (flavor == LOCAL_SQLOBJECT_ARRAY_FLAVOUR) {
            return sqlObjects;
        } else {
            throw new UnsupportedFlavorException(flavor);
        }
    }

    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { LOCAL_SQLOBJECT_ARRAY_FLAVOUR };
    }

    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor == LOCAL_SQLOBJECT_ARRAY_FLAVOUR;
    }

}
