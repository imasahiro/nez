package nez.lang.expr;

import nez.ast.SourcePosition;
import nez.lang.Expression;
import nez.lang.ExpressionVisitor;
import nez.lang.Typestate;
import nez.lang.Visa;

public class Tdetree extends Unary {
	Tdetree(SourcePosition s, Expression inner) {
		super(s, inner);
	}

	@Override
	public final boolean equalsExpression(Expression o) {
		if (o instanceof Tdetree) {
			return this.get(0).equalsExpression(o.get(0));
		}
		return false;
	}

	@Override
	public final void format(StringBuilder sb) {
		this.formatUnary(sb, "~", inner, null);
	}

	@Override
	public Object visit(ExpressionVisitor v, Object a) {
		return v.visitTdetree(this, a);
	}

	@Override
	public boolean isConsumed() {
		return this.inner.isConsumed();
	}

	@Override
	public int inferTypestate(Visa v) {
		return Typestate.BooleanType;
	}

	@Override
	public short acceptByte(int ch) {
		return this.inner.acceptByte(ch);
	}

}