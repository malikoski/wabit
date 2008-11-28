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

import java.io.IOException;

import ca.sqlpower.architect.ArchitectException;
import ca.sqlpower.wabit.WabitSession;
import ca.sqlpower.wabit.WabitSessionContextImpl;

/**
 * This is the swing version of the WabitSessionContext. Swing specific operations for
 * the context will be done in this implementation 
 */
public class WabitSwingSessionContextImpl extends WabitSessionContextImpl {

	public WabitSwingSessionContextImpl(boolean terminateWhenLastSessionCloses)
			throws IOException, ArchitectException {
		super(terminateWhenLastSessionCloses);
		Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandler());
	}
	
	@Override
	public WabitSession createSession() {
		WabitSwingSession session = new WabitSwingSessionImpl(this);
		return session;
	}

}
