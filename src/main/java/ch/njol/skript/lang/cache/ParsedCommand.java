/**
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
 * Copyright 2011-2017 Peter Güttinger and contributors
 */
package ch.njol.skript.lang.cache;

import java.util.List;

import ch.njol.skript.util.Timespan;

public interface ParsedCommand {
	
	void name(String name);
	
	void argument(String name, Class<?> type, boolean single, boolean optional);
	
	void usage(String usage);
	
	void description(String desc);
	
	void aliases(List<String> aliases);
	
	void permission(String permission);
	
	void permissionMessage(String message);
	
	void executableBy(int executable);
	
	void cooldown(Timespan time);
	
	BitCode cooldownMessage();
	
	void cooldownBypass(String bypass);
	
	BitCode cooldownStorage();
	
	BitCode trigger();
}