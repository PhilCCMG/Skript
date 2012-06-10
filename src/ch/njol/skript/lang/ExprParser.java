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

package ch.njol.skript.lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptLogger;
import ch.njol.skript.SkriptLogger.SubLog;
import ch.njol.skript.api.DefaultVariable;
import ch.njol.skript.api.SkriptEvent;
import ch.njol.skript.api.SkriptEvent.SkriptEventInfo;
import ch.njol.skript.api.intern.SkriptAPIException;
import ch.njol.skript.util.Utils;
import ch.njol.util.Pair;
import ch.njol.util.StringUtils;

/**
 * Used for parsing my custom patterns.<br>
 * <br>
 * Note: All parse methods print one error at most xor any amount of warnings and lower level log messages. If the given string doesn't match any pattern nothing is printed.
 * 
 * @author Peter Güttinger
 * 
 */
public class ExprParser {
	
	private final String expr;
	
	final boolean parseStatic;
	
	private String bestError = null;
	private int bestErrorQuality = 0;
	
	private static enum ErrorQuality {
		NONE, NOT_A_VARIABLE, VARIABLE_OF_WRONG_TYPE, SEMANTIC_ERROR;
		int quality() {
			return ordinal();
		}
	}
	
	private ExprParser(final String expr) {
		this.expr = expr;
		parseStatic = false;
	}
	
	private ExprParser(final String expr, final boolean parseStatic) {
		this.expr = expr;
		this.parseStatic = parseStatic;
	}
	
	public final static String wildcard = "[^\"]*?(?:\"[^\"]*?\"[^\"]*?)*?";
	public final static String stringMatcher = "\"[^\"]*?(?:\"\"[^\"]*)*?\"";
	
	public final static class ParseResult {
		public final Variable<?>[] vars;
		public final List<MatchResult> regexes = new ArrayList<MatchResult>();
		public final String expr;
		int matchedChars = 0;
		
		public ParseResult(final String expr, final String pattern, final int matchedChars) {
			this.expr = expr;
			vars = new Variable<?>[StringUtils.count(pattern, '%') / 2];
			this.matchedChars = matchedChars;
		}
	}
	
	private final static class MalformedPatternException extends RuntimeException {
		
		private static final long serialVersionUID = -2479399963189481643L;
		
		public MalformedPatternException(final String pattern, final String message) {
			super("\"" + pattern + "\": " + message);
		}
		
	}
	
	public static final <T> Literal<? extends T> parseLiteral(final String expr, final Class<T> c) {
		return makeUnparsedLiteral(expr).getConvertedVar(c);
	}
	
	/**
	 * Parses a string as one of the given expressions or as a literal
	 * 
	 * @param expr
	 * @param source
	 * @param parseLiteral
	 * @param defaultError
	 * @return
	 */
	public static final Expression parse(final String expr, final Iterator<? extends ExpressionInfo<?>> source, final boolean parseLiteral, final String defaultError) {
		final SubLog log = SkriptLogger.startSubLog();
		final Expression e = parse(expr, source, null);
		SkriptLogger.stopSubLog(log);
		if (e != null) {
			log.printLog();
			return e;
		}
		if (parseLiteral) {
			return makeUnparsedLiteral(expr);
		}
		log.printErrors(defaultError);
		return null;
	}
	
	/**
	 * Parses a string as one of the given expressions
	 * 
	 * @param expr
	 * @param source
	 * @param defaultError
	 * @return
	 */
	public static final Expression parse(final String expr, final Iterator<? extends ExpressionInfo<?>> source, final String defaultError) {
		final SubLog log = SkriptLogger.startSubLog();
		final Expression e = new ExprParser(expr).parse(source);
		SkriptLogger.stopSubLog(log);
		if (e != null) {
			log.printLog();
			return e;
		}
		log.printErrors(defaultError);
		return null;
	}
	
	private final Expression parse(final Iterator<? extends ExpressionInfo<?>> source) {
		while (source.hasNext()) {
			final ExpressionInfo<?> info = source.next();
			for (int i = 0; i < info.patterns.length; i++) {
				try {
					final ParseResult res = parse_i(info.patterns[i], 0, 0);
					if (res != null) {
						int x = -1;
						for (int j = 0; (x = next(info.patterns[i], '%', x + 1)) != -1; j++) {
							final int x2 = next(info.patterns[i], '%', x + 1);
							if (res.vars[j] == null) {
								final String name = info.patterns[i].substring(x + 1, x2);
								if (!name.startsWith("-")) {
									final VarInfo vi = getVarInfo(name);
									final DefaultVariable<?> var = Skript.getDefaultVariable(vi.name);
									if (var == null)
										throw new SkriptAPIException("The class '" + vi.name + "' does not provide a default variable. Either allow null (with %-" + vi.name + "%) or make it mandatory");
									if (!vi.isPlural && !var.isSingle())
										throw new SkriptAPIException("The default variable of '" + vi.name + "' is not a single-element variable. Change your pattern to allow multiple elements or make the variable mandatory");
									if (vi.time != 0 && !var.setTime(vi.time))
										throw new SkriptAPIException("The default variable of '" + vi.name + "' does not have distinct time states. Either allow null (with %-" + vi.name + "%) or make it mandatory");
									var.init();
									res.vars[j] = var;
								}
							}
							x = x2;
						}
						final Expression e = info.c.newInstance();
						SubLog log = SkriptLogger.startSubLog();
						if (!e.init(res.vars, i, res)) {
							SkriptLogger.stopSubLog(log);
							if (!log.hasErrors())
								continue;
							if (bestErrorQuality < ErrorQuality.SEMANTIC_ERROR.quality()) {
								bestError = log.getLastError();
								bestErrorQuality = ErrorQuality.SEMANTIC_ERROR.quality();
							}
							Skript.error(bestError);
							return null;
						}
						SkriptLogger.stopSubLog(log);
						log.printLog();
						return e;
					}
					if (bestErrorQuality == ErrorQuality.SEMANTIC_ERROR.quality()) {
						Skript.error(bestError);
						return null;
					}
				} catch (final InstantiationException e) {
					SkriptAPIException.instantiationException("the " + Skript.getExpressionName(info.c), info.c, e);
				} catch (final IllegalAccessException e) {
					SkriptAPIException.inaccessibleConstructor(info.c, e);
				}
			}
		}
		if (bestError != null) {
			Skript.error(bestError);
		}
		return null;
	}
	
	/**
	 * Does not print errors
	 * 
	 * @param returnType
	 * @param expr
	 * @param literalOnly
	 * @return
	 */
	private final Variable<?> parseVar(final Class<?> returnType, final String expr, final boolean literalOnly) {
		if (!literalOnly) {
			final SubLog log = SkriptLogger.startSubLog();
			final ExprParser parser = new ExprParser(expr);
			final Variable<?> v = (Variable<?>) parser.parse(Skript.getVariables().iterator());
			SkriptLogger.stopSubLog(log);
			if (v != null) {
				final Variable<?> w = v.getConvertedVariable(returnType);
				if (w == null && bestErrorQuality < ErrorQuality.VARIABLE_OF_WRONG_TYPE.quality()) {
					bestError = v.toString() + " " + (v.isSingle() ? "is" : "are") + " not " + Utils.a(Skript.getExactClassName(returnType));
					bestErrorQuality = ErrorQuality.VARIABLE_OF_WRONG_TYPE.quality();
				}
				return w;
			} else {
				if (parser.bestErrorQuality > bestErrorQuality) {
					bestError = parser.bestError;
					bestErrorQuality = parser.bestErrorQuality;
				}
			}
		}
		final UnparsedLiteral l = makeUnparsedLiteral(expr);
		if (returnType == Object.class)
			return l;
		final SubLog log = SkriptLogger.startSubLog();
		final Literal<?> p = l.getConvertedVar(returnType);
		SkriptLogger.stopSubLog(log);
		if (p == null && bestErrorQuality < ErrorQuality.NOT_A_VARIABLE.quality()) {
			bestError = log.getLastError() == null ? "'" + expr + "' is not " + Utils.a(Skript.getExactClassName(returnType)) : log.getLastError();
			bestErrorQuality = ErrorQuality.NOT_A_VARIABLE.quality();
		}
		return p;
	}
	
	public static Pair<SkriptEventInfo<?>, SkriptEvent> parseEvent(final String event, final String defaultError) {
		final SubLog log = SkriptLogger.startSubLog();
		final Pair<SkriptEventInfo<?>, SkriptEvent> e = new ExprParser(event, true).parseEvent();
		SkriptLogger.stopSubLog(log);
		if (e != null) {
			log.printLog();
			return e;
		}
		log.printErrors(defaultError);
		return null;
	}
	
	private Pair<SkriptEventInfo<?>, SkriptEvent> parseEvent() {
		for (final SkriptEventInfo<?> info : Skript.getEvents()) {
			for (int i = 0; i < info.patterns.length; i++) {
				try {
					final ParseResult res = parse_i(info.patterns[i], 0, 0);
					if (res != null) {
						final SkriptEvent e = info.c.newInstance();
						e.init(Arrays.copyOf(res.vars, res.vars.length, Literal[].class), i, res);
						return new Pair<SkriptEventInfo<?>, SkriptEvent>(info, e);
					}
					if (bestErrorQuality == ErrorQuality.SEMANTIC_ERROR.quality()) {
						Skript.error(bestError);
						return null;
					}
				} catch (final InstantiationException e) {
					SkriptAPIException.instantiationException("the event", info.c, e);
				} catch (final IllegalAccessException e) {
					SkriptAPIException.inaccessibleConstructor(info.c, e);
				}
			}
		}
		if (bestError != null) {
			Skript.error(bestError);
		}
		return null;
	}
	
	private static int next(final String s, final char c, final char x, final int start) {
		int n = 0;
		for (int i = start; i < s.length(); i++) {
			if (s.charAt(i) == '\\') {
				i++;
				continue;
			} else if (s.charAt(i) == c) {
				if (n == 0)
					return i;
				n--;
			} else if (s.charAt(i) == x) {
				n++;
			}
		}
		throw new MalformedPatternException(s, "missing closing bracket '" + c + "'");
	}
	
	private static int next(final String s, final char c, final int from) {
		for (int i = from; i < s.length(); i++) {
			if (s.charAt(i) == '\\') {
				i++;
			} else if (s.charAt(i) == c) {
				return i;
			}
		}
		return -1;
	}
	
	private static int nextQuote(final String s, final int from) {
		for (int i = from; i < s.length(); i++) {
			if (s.charAt(i) == '"') {
				if (i == s.length() - 1 || s.charAt(i + 1) != '"')
					return i;
				i++;
			}
		}
		return -1;
	}
	
	private static boolean hasOnly(final String s, final String chars, final int start, final int end) {
		for (int i = start; i < end; i++) {
			if (chars.indexOf(s.charAt(i)) == -1)
				return false;
		}
		return true;
	}
	
	/**
	 * Does not print errors
	 * 
	 * @param pattern
	 * @param i
	 * @param j
	 * @return
	 */
	private final ParseResult parse_i(final String pattern, int i, int j) {
		if (expr.isEmpty())
			throw new SkriptAPIException("Empty expression (pattern: " + pattern + ")");
		
		ParseResult res;
		int matchedChars = 0;
		int end, i2;
		
		while (j < pattern.length()) {
			switch (pattern.charAt(j)) {
				case '[':
					res = parse_i(pattern, i, j + 1);
					if (res != null) {
						return res;
					}
					end = next(pattern, ']', '[', j + 1);
					if ((hasOnly(pattern, "[(", 0, j) || pattern.charAt(j - 1) == ' ')
							&& end < pattern.length() - 1 && pattern.charAt(end + 1) == ' ') {
						end++;
					}
					j = end + 1;
				break;
				case '(':
					end = next(pattern, ')', '(', j + 1);
					final String[] gs = pattern.substring(j + 1, end).split("\\|");
					int j2 = j + 1;
					for (int k = 0; k < gs.length; k++) {
						res = parse_i(pattern, i, j2);
						if (res != null) {
							return res;
						}
						j2 += gs[k].length() + 1;
					}
					return null;
				case '%':
					if (i == expr.length())
						return null;
					end = next(pattern, '%', j + 1);
					if (end == -1)
						throw new MalformedPatternException(pattern, "odd number of '%'");
					String name = pattern.substring(j + 1, end);
					if (name.startsWith("-"))
						name = name.substring(1);
					final VarInfo vi = getVarInfo(name);
					name = vi.name;
					final Class<?> returnType = Skript.getClass(name);
					if (end == pattern.length() - 1) {
						i2 = expr.length();
					} else if (expr.charAt(i) == '"') {
						i2 = nextQuote(expr, i + 1) + 1;
						if (i2 == 0)
							return null;
					} else {
						i2 = i + 1;
					}
					for (; i2 <= expr.length(); i2++) {
						if (i2 < expr.length() && expr.charAt(i2) == '"') {
							i2 = nextQuote(expr, i2 + 1) + 1;
							if (i2 == 0)
								return null;
						}
						res = parse_i(pattern, i2, end + 1);
						if (res != null) {
							final Variable<?> var = parseVar(returnType, expr.substring(i, i2), parseStatic);
							if (var != null) {
								if (!vi.isPlural && !var.isSingle()) {
									if (bestErrorQuality < ErrorQuality.SEMANTIC_ERROR.quality()) {
										bestError = "this expression can only accept a single " + Skript.getExactClassName(returnType) + ", but multiple are given.";
										bestErrorQuality = ErrorQuality.SEMANTIC_ERROR.quality();
									}
									return null;
								}
								if (vi.time != 0 && !var.setTime(vi.time)) {
									if (bestErrorQuality < ErrorQuality.SEMANTIC_ERROR.quality()) {
										bestError = var + " does not have a " + (vi.time == -1 ? "past" : "future") + " state";
										bestErrorQuality = ErrorQuality.SEMANTIC_ERROR.quality();
									}
									return null;
								}
								res.vars[StringUtils.count(pattern, '%', 0, j - 1) / 2] = var;
								return res;
							} else if (bestErrorQuality < ErrorQuality.NOT_A_VARIABLE.quality() && res.matchedChars + matchedChars >= 5) {
								bestError = "'" + expr.substring(i, i2) + "' is not " + Utils.a(Skript.getExactClassName(returnType));
								bestErrorQuality = ErrorQuality.NOT_A_VARIABLE.quality();
							}
						}
					}
					return null;
				case '<':
					end = pattern.indexOf('>', j + 1);// not next()
					if (end == -1)
						throw new MalformedPatternException(pattern, "missing closing regex bracket '>'");
					for (i2 = i + 1; i2 <= expr.length(); i2++) {
						res = parse_i(pattern, i2, end + 1);
						if (res != null) {
							final Matcher m = Pattern.compile(pattern.substring(j + 1, end)).matcher(expr.substring(i, i2));
							if (m.matches()) {
								res.regexes.add(0, m.toMatchResult());
								return res;
							}
						}
					}
					return null;
				case ')':
				case ']':
					j++;
				break;
				case '|':
					j = next(pattern, ')', '(', j + 1) + 1;
				break;
				case ' ':
					if (i == expr.length() || (i > 0 && expr.charAt(i - 1) == ' ')) {
						j++;
						break;
					} else if (expr.charAt(i) != ' ') {
						return null;
					}
					matchedChars++;
					i++;
					j++;
				break;
				case '\\':
					j++;
					if (j == pattern.length())
						throw new MalformedPatternException(pattern, "must not end with a backslash");
					//$FALL-THROUGH$
				default:
					if (i == expr.length() || Character.toLowerCase(pattern.charAt(j)) != Character.toLowerCase(expr.charAt(i)))
						return null;
					matchedChars++;
					i++;
					j++;
			}
		}
		if (i == expr.length() && j == pattern.length())
			return new ParseResult(expr, pattern, matchedChars);
		return null;
	}
	
	private final static UnparsedLiteral makeUnparsedLiteral(final String s) {
		final ArrayList<String> parts = new ArrayList<String>();
		final Pattern p = Pattern.compile("^(" + wildcard + ")(,\\s*|,?\\s+and\\s+|,?\\s+n?or\\s+)");
		final Matcher m = p.matcher(s);
		int prevEnd = 0;
		boolean and = true;
		boolean isAndSet = false;
		while (m.find()) {
			if (!m.group(2).matches(",\\s*")) {
				if (isAndSet) {
					Skript.warning("list has multiple 'and' or 'or', will default to 'and': " + s);
					and = true;
				} else {
					and = m.group(2).contains("and");
					isAndSet = true;
				}
			}
			parts.add(m.group(1).trim());
			prevEnd = m.end();
			m.region(m.end(), s.length());
		}
		if (!isAndSet && !parts.isEmpty()) {
			Skript.warning("list is missing 'and' or 'or', will default to 'and': " + s);
		}
		parts.add(s.substring(prevEnd).trim());
		return new UnparsedLiteral(parts.toArray(new String[0]), and);
	}
	
	private final static class VarInfo {
		String name;
		boolean isPlural;
		int time = 0;
	}
	
	private static VarInfo getVarInfo(final String s) {
		final String[] a = s.split("@", 2);
		final VarInfo r = new VarInfo();
		if (a.length == 1) {
			r.name = s;
		} else {
			r.time = Integer.parseInt(a[1]);
			r.name = a[0];
		}
		final Pair<String, Boolean> p = Utils.getPlural(r.name);
		r.name = p.first;
		r.isPlural = p.second;
		return r;
	}
	
}