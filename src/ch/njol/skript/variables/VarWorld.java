/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011, 2012 Peter Güttinger
 * 
 */

package ch.njol.skript.variables;

import org.bukkit.World;
import org.bukkit.event.Event;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.variables.base.VarVariable;

/**
 * @author Peter Güttinger
 * 
 */
public class VarWorld extends VarVariable<World> {
	
	static {
		Skript.registerVariable(VarWorld.class, World.class, "[the] world [of %world%]");
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean init(final ch.njol.skript.lang.Variable<?>[] vars, final int matchedPattern, final ch.njol.skript.lang.ExprParser.ParseResult parser) {
		var = (Variable<World>) vars[0];
		return true;
	}
	
	@Override
	public String toString() {
		return "the world" + (var.isDefault() ? "" : " of " + var);
	}
	
	@Override
	public String getDebugMessage(final Event e) {
		return "the world" + (var.isDefault() ? "" : " of " + var.getDebugMessage(e));
	}
	
}