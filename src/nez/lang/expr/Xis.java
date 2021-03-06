package nez.lang.expr;

import nez.ast.SourcePosition;
import nez.ast.Symbol;
import nez.lang.Contextual;
import nez.lang.Expression;
import nez.lang.GrammarTransducer;
import nez.lang.Visa;
import nez.parser.AbstractGenerator;
import nez.parser.Instruction;

public class Xis extends Unary implements Contextual {
	// final Grammar g;
	public final Symbol tableName;
	public final boolean is;

	Xis(SourcePosition s, NonTerminal pat, boolean is) {
		super(s, pat);
		this.tableName = Symbol.tag(pat.getLocalName());
		this.is = is;
	}

	Xis(SourcePosition s, Symbol tableName, Expression e, boolean is) {
		super(s, e);
		this.tableName = tableName;
		this.is = is;
	}

	@Override
	public final boolean equalsExpression(Expression o) {
		if (o instanceof Xis) {
			Xis e = (Xis) o;
			return this.get(0).equals(e.get(0)) && this.tableName == e.tableName && this.is == e.is;
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
		return m.reshapeXis(this);
	}

	@Override
	public void format(StringBuilder sb) {
		if (this.is) {
			sb.append("<is ");
		} else {
			sb.append("<in ");
		}
		sb.append(getTableName());
		sb.append(">");
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
		return bc.encodeXis(this, next, failjump);
	}
}