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

package ca.sqlpower.wabit.dao;

import com.lowagie.text.pdf.codec.Base64.InputStream;

import ca.sqlpower.wabit.WabitObject;
import ca.sqlpower.wabit.WabitWorkspace;

/**
 * An interface for objects that persist {@link WabitObject}s. The medium into
 * which they are persisted is entirely up to the implementation.
 */
public interface WabitPersister {

	public enum DataType {
		STRING("String", String.class),
		INTEGER("Integer", Integer.class),
		DOUBLE("Double", Double.class),
		BOOLEAN("Boolean", Boolean.class),
		REFERENCE("Reference", String.class),
		PNG_IMG("PNG_IMG", InputStream.class),
		NULL("Null", null);
		
		private final String name;
		private final Class representation;
		
		private DataType(String name, Class representation){
			this.name = name;
			this.representation = representation;
		}

		public String getTypeName() {
			return name;
		}

		public Class getRepresentation() {
			return representation;
		}
	}

	/**
	 * Modifies the named property of the specified WabitObject in this
	 * persister's workspace. It may throw an {@link Exception} if the actual
	 * previous value in persistent storage does not match the expected previous
	 * value as an indication to the object using this WabitPersister that their
	 * cached copy of the {@link WabitObject} may be out of sync with the
	 * persistent storage.
	 * 
	 * @param uuid
	 *            The UUID of the {@link WabitObject} in which to set the
	 *            property
	 * @param propertyName
	 *            The JavaBeans property name of the property that changed, as
	 *            it would be discovered by the java.beans.Introspector class
	 * @param propertyType
	 *            The type, and Java representation, of this property
	 * @param oldValue
	 *            The expected previous value of the property
	 * @param newValue
	 *            The new value to set for the property
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the property. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID. Some potential exceptional situations
	 *             include:
	 *             <ul>
	 *             <li>The WabitObject UUID is unknown to this persister</li>
	 *             <li>The given property name does not exist</li>
	 *             <li>The given property is not writable</li>
	 *             <li>The property type of the given old/new values do not
	 *             match each other or the actual property in the object</li>
	 *             <li>The existing persistent value doesn't match expected
	 *             oldValue</li>
	 *             </ul>
	 */
	public void persistProperty(String uuid, String propertyName,
			DataType propertyType, Object oldValue, Object newValue)
			throws WabitPersistenceException;

	/**
	 * Modifies the named property of the specified WabitObject in this
	 * persister's workspace. This version is an unconditional call, and does
	 * not check the previous state of the property. To ensure the
	 * {@link WabitObject}s stay in synch, this method should only be called by
	 * the Persister representing the master copy of the WabitObject.
	 * 
	 * @param uuid
	 * @param propertyName
	 * @param newValue
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the property. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID. Some potential exceptional situations
	 *             include:
	 *             <ul>
	 *             <li>The WabitObject UUID is unknown to this persister</li>
	 *             <li>The given property name does not exist</li>
	 *             <li>The given property is not writable</li>
	 *             <li>The property type of the given value does not match the
	 *             actual property in the object</li>
	 */
	public void persistProperty(String uuid, String propertyName,
			DataType propertyType, Object newValue)
			throws WabitPersistenceException;

	/**
	 * Adds a {@link WabitObject} into the persistent storage. If the
	 * WabitObject already exists in persistent storage, then it will throw an
	 * exception Note that this will not persist its properties or any child
	 * objects.
	 * 
	 * @param parentUUID
	 *            The UUID of the parent {@link WabitObject} of the object to
	 *            persist. If the WabitObject has no parent (as is the case for
	 *            {@link WabitWorkspace}), then it can be set to null.
	 * @param type
	 *            A String of the class name of the WabitObject to be persisted
	 *            (ex. WabitWorkspace)
	 * @param uuid
	 *            The UUID of the {@link WabitObject} to actually persist
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the WabitObject. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID. Some potential exceptional situations
	 *             include:
	 *             <ul>
	 *             <li>A WabitObject with the given UUID already exists in the
	 *             persistent storage</li>
	 *             <li>A WabitObject with the given parent UUID does not exist</li>
	 *             </ul>
	 */
	public void persistObject(String parentUUID, String type, String uuid) throws WabitPersistenceException;

	/**
	 * Removes a WabitObject from persistent storage
	 * 
	 * @param parentUUID
	 *            The UUID of the parent {@link WabitObject} of the object to
	 *            remove
	 * @param uuid
	 *            The UUID of the {@link WabitObject} to remove
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the WabitObject. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID.
	 */
	public void removeObject(String parentUUID, String uuid)
			throws WabitPersistenceException;

	/**
	 * Indicates the start of an atomic transaction of persisting multiple
	 * {@link WabitObject}s. To be used with a paired call to {@link #commit()}
	 * 
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the WabitObject. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID.
	 */
	public void begin() throws WabitPersistenceException;

	/**
	 * Causes a current {@link WabitObject} persistence transaction to commit
	 * its results.
	 * 
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while committing the transaction. This could be caused by one
	 *             being thrown by {@link #persistObject(String, String)},
	 *             {@link #persistProperty(String, String, Object, Object)}, or
	 *             {@link #removeObject(String, String)}. Some other exceptional
	 *             situations include:
	 *             <ul>
	 *             <li>An update conflict with another transaction</li>
	 *             <li>Network issues</li>
	 *             <li>Insufficient permissions in the backing store</li>
	 *             </ul>
	 */
	public void commit() throws WabitPersistenceException;

	/**
	 * Restores the persisted WabitObject back to the state it was in before the
	 * transaction began (i.e. when the call to {@link #begin()} was made).
	 * Typically, this would be called if an exception occurs during an atomic
	 * transaction. In the event that a rollback() is called within a nested
	 * transaction (that is, a begin() call after another begin() call before
	 * commit()), then the state of the WabitObjects must be rolled back to the
	 * state they were in before the highest level transaction began.
	 * 
	 * @throws WabitPersistenceException
	 *             A general Exception that is thrown if any Exception occurs
	 *             while persisting the WabitObject. It can be used to wrap the
	 *             specific cause Exception and provide other details like the
	 *             WabitObject UUID.
	 */
	public void rollback() throws WabitPersistenceException;
}