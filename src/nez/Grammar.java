package nez;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;

import nez.ast.SourcePosition;
import nez.ast.SymbolId;
import nez.lang.Expression;
import nez.lang.Production;
import nez.lang.expr.ExpressionCommons;
import nez.util.ConsoleUtils;
import nez.util.UList;

public class Grammar extends AbstractList<Production> {
	// private static int nsid = 0;

	Grammar parent;
	UList<Production> prodList;
	HashMap<String, Production> prodMap = null;

	public Grammar() {
		this(null);
	}

	public Grammar(Grammar parent) {
		this.parent = parent;
		this.prodList = new UList<Production>(new Production[1]);
	}

	public final String uniqueName(String name) {
		return this.hashCode() + ":" + name;
	}

	@Override
	public final boolean isEmpty() {
		return this.prodList.size() == 0;
	}

	@Override
	public final int size() {
		return this.prodList.size();
	}

	@Override
	public final Production get(int index) {
		return this.prodList.ArrayValues[index];
	}

	public final Production getStartProduction() {
		return this.prodList.ArrayValues[0];
	}

	public final Parser newParser(NezOption option) {
		return new Parser(this.getStartProduction(), option);
	}

	public final List<Production> getProductionList() {
		return this.prodList;
	}

	public final Production getProduction(String name) {
		Production p = this.getLocalProduction(name);
		if (p == null && this.parent != null) {
			return this.parent.getProduction(name);
		}
		return p;
	}

	private Production getLocalProduction(String name) {
		if (prodMap != null) {
			return this.prodMap.get(name);
		}
		for (Production p : this.prodList) {
			if (name.equals(p.getLocalName())) {
				return p;
			}
		}
		return null;
	}

	public final boolean hasProduction(String name) {
		return this.getLocalProduction(name) != null;
	}

	public final void addProduction(Production p) {
		Production p2 = this.getLocalProduction(p.getLocalName());
		if (p2 == null) {
			this.prodList.add(p);
			if (this.prodMap != null) {
				this.prodMap.put(p.getLocalName(), p);
			} else if (this.prodList.size() > 4) {
				this.prodMap = new HashMap<String, Production>();
				for (Production p3 : this.prodList) {
					this.prodMap.put(p3.getLocalName(), p3);
				}
			}
		} else {
			String name = p.getLocalName();
			for (int i = 0; i < this.prodList.size(); i++) {
				p2 = this.prodList.ArrayValues[i];
				if (name.equals(p2.getLocalName())) {
					this.prodList.ArrayValues[i] = p;
					if (this.prodMap != null) {
						this.prodMap.put(name, p);
					}
					break;
				}
			}
		}
		if (p.isPublic() && this.parent != null) {
			this.parent.addProduction(p2);
		}
	}

	public final void setSymbolExpresion(String tableName, Expression e) {
		// if (tableMap == null) {
		// tableMap = new HashMap<String, Expression>();
		// }
		// tableMap.put(tableName, e);
	}

	public final Expression getSymbolExpresion(String tableName) {
		// if (tableMap != null) {
		// Expression e = tableMap.get(tableName);
		// if (e != null && !e.isInterned()) {
		// e = e.intern();
		// tableMap.put(tableName, e);
		// }
		// return e;
		// }
		return null;
	}

	// ----------------------------------------------------------------------

	// Grammar

	public final void reportError(Expression p, String message) {
		this.reportError(p.getSourcePosition(), message);
	}

	public final void reportError(SourcePosition s, String message) {
		if (s != null) {
			ConsoleUtils.println(s.formatSourceMessage("error", message));
		}
	}

	public final void reportWarning(Expression p, String message) {
		this.reportWarning(p.getSourcePosition(), message);
	}

	public final void reportWarning(SourcePosition s, String message) {
		if (s != null) {
			ConsoleUtils.println(s.formatSourceMessage("warning", message));
		}
	}

	public final void reportNotice(Expression p, String message) {
		this.reportNotice(p.getSourcePosition(), message);
	}

	public final void reportNotice(SourcePosition s, String message) {
		// if (option.enabledNoticeReport) {
		if (s != null) {
			ConsoleUtils.println(s.formatSourceMessage("notice", message));
		}
		// }
	}

	// ----------------------------------------------------------------------

	protected SourcePosition getSourcePosition() {
		return null;
	}

	public final Expression newNonTerminal(String name) {
		return ExpressionCommons.newNonTerminal(getSourcePosition(), this, name);
	}

	public final Expression newEmpty() {
		return ExpressionCommons.newEmpty(getSourcePosition());
	}

	public final Expression newFailure() {
		return ExpressionCommons.newFailure(getSourcePosition());
	}

	public final Expression newByteChar(int ch) {
		return ExpressionCommons.newCbyte(getSourcePosition(), false, ch);
	}

	public final Expression newAnyChar() {
		return ExpressionCommons.newCany(getSourcePosition(), false);
	}

	public final Expression newString(String text) {
		return ExpressionCommons.newString(getSourcePosition(), text);
	}

	public final Expression newCharSet(String text) {
		return ExpressionCommons.newCharSet(getSourcePosition(), text);
	}

	public final Expression newByteMap(boolean[] byteMap) {
		return ExpressionCommons.newCset(getSourcePosition(), false, byteMap);
	}

	public final Expression newSequence(Expression... seq) {
		UList<Expression> l = new UList<Expression>(new Expression[8]);
		for (Expression p : seq) {
			ExpressionCommons.addSequence(l, p);
		}
		return ExpressionCommons.newPsequence(getSourcePosition(), l);
	}

	public final Expression newChoice(Expression... seq) {
		UList<Expression> l = new UList<Expression>(new Expression[8]);
		for (Expression p : seq) {
			ExpressionCommons.addChoice(l, p);
		}
		return ExpressionCommons.newPchoice(getSourcePosition(), l);
	}

	public final Expression newOption(Expression... seq) {
		return ExpressionCommons.newUoption(getSourcePosition(), newSequence(seq));
	}

	public final Expression newRepetition(Expression... seq) {
		return ExpressionCommons.newUzero(getSourcePosition(), newSequence(seq));
	}

	public final Expression newRepetition1(Expression... seq) {
		return ExpressionCommons.newUone(getSourcePosition(), newSequence(seq));
	}

	public final Expression newAnd(Expression... seq) {
		return ExpressionCommons.newUand(getSourcePosition(), newSequence(seq));
	}

	public final Expression newNot(Expression... seq) {
		return ExpressionCommons.newUnot(getSourcePosition(), newSequence(seq));
	}

	// public final Expression newByteRange(int c, int c2) {
	// if(c == c2) {
	// return newByteChar(s, c);
	// }
	// return internImpl(s, new ByteMap(s, c, c2));
	// }

	// PEG4d
	public final Expression newMatch(Expression... seq) {
		return ExpressionCommons.newUmatch(getSourcePosition(), newSequence(seq));
	}

	public final Expression newLink(Expression... seq) {
		return ExpressionCommons.newTlink(getSourcePosition(), null, newSequence(seq));
	}

	public final Expression newLink(SymbolId label, Expression... seq) {
		return ExpressionCommons.newTlink(getSourcePosition(), label, newSequence(seq));
	}

	public final Expression newNew(Expression... seq) {
		return ExpressionCommons.newNewCapture(getSourcePosition(), false, null, newSequence(seq));
	}

	// public final Expression newLeftNew(Expression ... seq) {
	// return GrammarFactory.newNew(getSourcePosition(), true,
	// newSequence(seq));
	// }

	public final Expression newTagging(String tag) {
		return ExpressionCommons.newTtag(getSourcePosition(), SymbolId.tag(tag));
	}

	public final Expression newReplace(String msg) {
		return ExpressionCommons.newTreplace(getSourcePosition(), msg);
	}

	// Conditional Parsing
	// <if FLAG>
	// <on FLAG e>
	// <on !FLAG e>

	public final Expression newIfFlag(String flagName) {
		return ExpressionCommons.newXif(getSourcePosition(), flagName);
	}

	public final Expression newXon(String flagName, Expression... seq) {
		return ExpressionCommons.newXon(getSourcePosition(), true, flagName, newSequence(seq));
	}

	public final Expression newBlock(Expression... seq) {
		return ExpressionCommons.newXblock(getSourcePosition(), newSequence(seq));
	}

	public final Expression newDefSymbol(String table, Expression... seq) {
		return ExpressionCommons.newXdef(getSourcePosition(), this, SymbolId.tag(table), newSequence(seq));
	}

	public final Expression newIsSymbol(String table) {
		return ExpressionCommons.newXis(getSourcePosition(), this, SymbolId.tag(table));
	}

	public final Expression newIsaSymbol(String table) {
		return ExpressionCommons.newXisa(getSourcePosition(), this, SymbolId.tag(table));
	}

	public final Expression newExists(String table, String symbol) {
		return ExpressionCommons.newXexists(getSourcePosition(), SymbolId.tag(table), symbol);
	}

	public final Expression newLocal(String table, Expression... seq) {
		return ExpressionCommons.newXlocal(getSourcePosition(), SymbolId.tag(table), newSequence(seq));
	}

	public final Expression newScan(int number, Expression scan, Expression repeat) {
		return null;
	}

	public final Expression newRepeat(Expression e) {
		return null;

	}

}