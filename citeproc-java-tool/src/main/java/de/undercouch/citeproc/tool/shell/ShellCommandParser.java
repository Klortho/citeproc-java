// Copyright 2014 Michel Kraemer
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package de.undercouch.citeproc.tool.shell;

import java.beans.IntrospectionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;

import de.undercouch.citeproc.CSLTool;
import de.undercouch.citeproc.helper.tool.Command;
import de.undercouch.citeproc.helper.tool.InvalidOptionException;
import de.undercouch.citeproc.helper.tool.Option;
import de.undercouch.citeproc.helper.tool.OptionGroup;
import de.undercouch.citeproc.helper.tool.OptionIntrospector;
import de.undercouch.citeproc.helper.tool.OptionIntrospector.ID;

/**
 * Parses command lines in the interactive shell
 * @author Michel Kraemer
 */
public final class ShellCommandParser {
	private ShellCommandParser() {
		//hidden constructor
	}
	
	/**
	 * Parser result
	 */
	public static class Result {
		private final String[] remainingArgs;
		private final List<Class<? extends Command>> commands;
		
		private Result(String[] remainingArgs, List<Class<? extends Command>> commands) {
			this.remainingArgs = remainingArgs;
			this.commands = commands;
		}
		
		/**
		 * @return the remaining (unparsed) arguments
		 */
		public String[] getRemainingArgs() {
			return remainingArgs;
		}
		
		/**
		 * @return a list of all parsed commands
		 */
		public List<Class<? extends Command>> getCommands() {
			return commands;
		}
		
		/**
		 * @return the class of the first command parsed
		 */
		public Class<? extends Command> getFirstCommand() {
			if (commands.isEmpty()) {
				return null;
			}
			return commands.get(0);
		} 
		
		/**
		 * @return the class of the last command parsed
		 */
		public Class<? extends Command> getLastCommand() {
			if (commands.isEmpty()) {
				return null;
			}
			return commands.get(commands.size() - 1);
		}
	}
	
	/**
	 * Splits the given command line
	 * @param line the command line
	 * @return an array of strings calculated by splitting the command line
	 */
	public static String[] split(String line) {
		return line.trim().split("\\s+");
	}
	
	/**
	 * Parses a shell command line
	 * @param line the command line
	 * @return the parser result
	 * @throws IntrospectionException if a {@link de.undercouch.citeproc.tool.CSLToolCommand}
	 * could not be introspected
	 * @throws InvalidOptionException if the command line contains an
	 * option (only commands are allowed in the interactive shell)
	 */
	public static Result parse(String line) throws IntrospectionException,
			InvalidOptionException {
		return parse(line, Collections.<Class<? extends Command>>emptyList());
	}
	
	/**
	 * Parses a shell command line
	 * @param line the command line
	 * @param excluded a collection of commands that should not be parsed
	 * @return the parser result
	 * @throws IntrospectionException if a {@link de.undercouch.citeproc.tool.CSLToolCommand}
	 * could not be introspected
	 * @throws InvalidOptionException if the command line contains an
	 * option (only commands are allowed in the interactive shell)
	 */
	public static Result parse(String line,
			Collection<Class<? extends Command>> excluded)
			throws IntrospectionException, InvalidOptionException {
		String[] args = split(line);
		return parse(args, excluded);
	}
	
	/**
	 * Parses arguments of a shell command line
	 * @param args the arguments to parse
	 * @param excluded a collection of commands that should not be parsed
	 * @return the parser result
	 * @throws IntrospectionException if a {@link de.undercouch.citeproc.tool.CSLToolCommand}
	 * could not be introspected
	 * @throws InvalidOptionException if the command line contains an
	 * option (only commands are allowed in the interactive shell)
	 */
	public static Result parse(String[] args,
			Collection<Class<? extends Command>> excluded)
			throws IntrospectionException, InvalidOptionException {
		List<Class<? extends Command>> classes = new ArrayList<Class<? extends Command>>();
		return getCommandClass(args, 0, classes,
				new HashSet<Class<? extends Command>>(excluded));
	}
	
	private static Result getCommandClass(String[] args, int i,
			List<Class<? extends Command>> classes,
			Set<Class<? extends Command>> excluded)
					throws IntrospectionException, InvalidOptionException {
		if (i >= args.length) {
			return new Result(new String[0], classes);
		}
		
		if (args[i].startsWith("-")) {
			//block options
			throw new InvalidOptionException(args[i]);
		}
		
		OptionGroup<ID> options;
		if (classes.isEmpty()) {
			options = OptionIntrospector.introspect(CSLTool.class,
					AdditionalShellCommands.class);
		} else {
			options = OptionIntrospector.introspect(classes.get(classes.size() - 1));
		}
		
		List<Option<ID>> commands = options.getCommands();
		if (commands != null) {
			for (Option<ID> cmd : commands) {
				if (cmd.getLongName().equals(args[i])) {
					Class<? extends Command> cmdClass =
							OptionIntrospector.getCommand(cmd.getId());
					if (!excluded.contains(cmdClass)) {
						classes.add(cmdClass);
						return getCommandClass(args, i + 1, classes, excluded);
					}
				}
			}
		}
		
		return new Result(ArrayUtils.subarray(args, i, args.length), classes);
	}
}
