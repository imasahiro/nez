package nez.lang.expr;

import nez.ast.SourceLocation;
import nez.ast.Symbol;
import nez.lang.Expression;
import nez.lang.Nez;

public class Xlocal extends Nez.LocalScope {

	Xlocal(SourceLocation s, Symbol table, Expression inner) {
		super(table, inner);
		this.set(s);
	}

	@Override
	public boolean isConsumed() {
		return this.inner.isConsumed();
	}

	public final Symbol getTable() {
		return tableName;
	}

	public final java.lang.String getTableName() {
		return tableName.getSymbol();
	}

}
