package nez.ext;

import java.io.IOException;

import nez.Grammar;
import nez.Parser;
import nez.ast.CommonTree;
import nez.debugger.DebugManager;
import nez.io.SourceContext;
import nez.lang.Formatter;
import nez.lang.GrammarFile;
import nez.main.Command;
import nez.main.CommandContext;
import nez.main.ReadLine;
import nez.util.ConsoleUtils;

public class Cshell extends Command {
	String text = null;
	int linenum = 0;

	@Override
	public void exec(CommandContext config) throws IOException {
		Command.displayVersion();
		Grammar g = config.newGrammar();
		if (g.isEmpty()) {
			ConsoleUtils.println("Grammar file name is not found: " + config.getGrammarPath());
			return;
		}
		Parser p = config.newParser();
		while (readLine(">>> ")) {
			SourceContext sc = SourceContext.newStringContext("<stdio>", linenum, text);
			CommonTree node = p.parseCommonTree(sc);
			if (node == null) {
				ConsoleUtils.println(sc.getSyntaxErrorMessage());
				activateNezDebugger(config);
				continue;
			}
			if (sc.hasUnconsumed()) {
				activateNezDebugger(config);
				ConsoleUtils.println(sc.getUnconsumedMessage());
			}
			sc = null;
			ConsoleUtils.println(node.toString());
			if (g instanceof GrammarFile) {
				GrammarFile gfile2 = (GrammarFile) g;
				if (Formatter.isSupported(gfile2, node)) {
					ConsoleUtils.println("Formatted: " + Formatter.format(gfile2, node));
				}
			}
		}
	}

	private boolean readLine(String prompt) {
		Object console = ReadLine.getConsoleReader();
		StringBuilder sb = new StringBuilder();
		String line = ReadLine.readSingleLine(console, prompt);
		if (line == null) {
			return false;
		}
		sb.append(line);
		ReadLine.addHistory(console, line);
		while (true) {
			line = ReadLine.readSingleLine(console, "...");
			if (line == null) {
				return false;
			}
			if (line.equals("")) {
				text = sb.toString();
				return true;
			}
			sb.append(line);
			ReadLine.addHistory(console, line);
			sb.append("\n");
		}
	}

	private boolean readActivateDebugger() {
		Object console = ReadLine.getConsoleReader();
		while (true) {
			String line = ReadLine.readSingleLine(console, "Do you want to start the Nez debugger? (yes/no)");
			if (line == null) {
				ConsoleUtils.println("Please push the key of yes or no. You input the key: " + line);
				continue;
			}
			if (line.equals("yes") || line.equals("y")) {
				return true;
			}
			if (line.equals("no") || line.equals("n")) {
				return false;
			}
			ConsoleUtils.println("Please push the key of yes or no. You input the key: " + line);
		}
	}

	private void activateNezDebugger(CommandContext config) {
		if (readActivateDebugger()) {
			Parser parser = config.newParser();
			DebugManager manager = new DebugManager(text);
			manager.exec(parser, config.getStrategy());
		}
	}
}
