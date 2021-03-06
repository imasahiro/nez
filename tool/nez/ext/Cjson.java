package nez.ext;

import nez.ast.Tree;
import nez.ast.TreeWriter;
import nez.io.SourceContext;
import nez.main.CommandContext;

public class Cjson extends Cparse {
	@Override
	protected void makeOutputFile(CommandContext config, SourceContext source, Tree<?> node) {
		TreeWriter w = new TreeWriter(config.getStrategy(), config.getOutputFileName(source, "json"));
		w.writeTree(node);
		w.writeNewLine();
		w.close();
	}
}
