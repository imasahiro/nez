package nez.lang.expr;

import nez.ast.SourceLocation;
import nez.ast.Symbol;
import nez.lang.Expression;
import nez.lang.Nez;

public class Tlink extends Nez.Link {

	Tlink(SourceLocation s, Symbol label, Expression e) {
		super(label, e);
	}

	@Override
	public boolean isConsumed() {
		return this.inner.isConsumed();
	}

}