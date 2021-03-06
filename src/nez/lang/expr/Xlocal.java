package nez.lang.expr;

import nez.ast.SourcePosition;
import nez.ast.Symbol;
import nez.lang.Expression;
import nez.lang.GrammarTransducer;
import nez.lang.Visa;
import nez.parser.AbstractGenerator;
import nez.parser.Instruction;

public class Xlocal extends Unary {
	public final Symbol tableName;

	Xlocal(SourcePosition s, Symbol table, Expression inner) {
		super(s, inner);
		this.tableName = table;
	}

	@Override
	public final boolean equalsExpression(Expression o) {
		if (o instanceof Xlocal) {
			Xlocal s = (Xlocal) o;
			if (this.tableName == s.tableName) {
				return this.get(0).equalsExpression(s.get(0));
			}
		}
		return false;
	}

	public final Symbol getTable() {
		return tableName;
	}

	public final String getTableName() {
		return tableName.getSymbol();
	}

	@Override
	public Expression reshape(GrammarTransducer m) {
		return m.reshapeXlocal(this);
	}

	@Override
	public boolean isConsumed() {
		return this.inner.isConsumed();
	}

	@Override
	public int inferTypestate(Visa v) {
		return this.inner.inferTypestate(v);
	}

	@Override
	public short acceptByte(int ch) {
		return this.inner.acceptByte(ch);
	}

	@Override
	public Instruction encode(AbstractGenerator bc, Instruction next, Instruction failjump) {
		return bc.encodeXlocal(this, next, failjump);
	}

}
