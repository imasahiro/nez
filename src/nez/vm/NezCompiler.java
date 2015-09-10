package nez.vm;

import java.util.List;

import nez.NezOption;
import nez.Parser;
import nez.lang.Expression;
import nez.lang.GrammarOptimizer;
import nez.lang.Production;
import nez.main.Verbose;
import nez.util.UList;

public abstract class NezCompiler extends NezEncoder {

	public final static NezCompiler newCompiler(NezOption option) {
		return new PackratCompiler(option);
	}

	protected NezCompiler(NezOption option) {
		super(option);
	}

	public final NezCode compile(ParserGrammar g) {
		return this.compile(g, null);
	}

	public NezCode compile(ParserGrammar g, ByteCoder coder) {
		long t = System.nanoTime();
		List<MemoPoint> memoPointList = null;
		if (option.enabledMemoization || option.enabledPackratParsing) {
			memoPointList = new UList<MemoPoint>(new MemoPoint[4]);
		}
		UList<Instruction> codeList = new UList<Instruction>(new Instruction[64]);
		for (Production p : g) {
			this.encodeProduction(codeList, p, new IRet(p));
		}
		for (Instruction inst : codeList) {
			if (inst instanceof ICall) {
				ParseFunc deref = this.funcMap.get(((ICall) inst).rule.getUniqueName());
				if (deref == null) {
					Verbose.debug("no deref: " + ((ICall) inst).rule.getUniqueName());
				}
				((ICall) inst).setResolvedJump(deref.compiled);
			}
			// Verbose.debug("\t" + inst.id + "\t" + inst);
		}
		long t2 = System.nanoTime();
		Verbose.printElapsedTime("CompilingTime", t, t2);
		if (coder != null) {
			coder.setHeader(codeList.size(), funcMap.size(), memoPointList == null ? 0 : memoPointList.size());
			coder.setInstructions(codeList.ArrayValues, codeList.size());
		}
		this.funcMap = null;
		return new NezCode(codeList.ArrayValues[0], codeList.size(), memoPointList);
	}

	public final NezCode compile(Parser grammar) {
		return this.compile(grammar, null);
	}

	public NezCode compile(Parser grammar, ByteCoder coder) {
		long t = System.nanoTime();
		List<MemoPoint> memoPointList = null;
		if (option.enabledMemoization || option.enabledPackratParsing) {
			memoPointList = new UList<MemoPoint>(new MemoPoint[4]);
		}
		initParseFuncMap(grammar, memoPointList);
		UList<Instruction> codeList = new UList<Instruction>(new Instruction[64]);
		// Production start = grammar.getStartProduction();
		// this.encodeProduction(codeList, start, new IRet(start));
		// for(Production p : grammar.getProductionList()) {
		// if(p != start) {
		// this.encodeProduction(codeList, p, new IRet(p));
		// }
		// }
		for (Production p : grammar.getProductionList()) {
			this.encodeProduction(codeList, p, new IRet(p));
		}
		for (Instruction inst : codeList) {
			if (inst instanceof ICall) {
				ParseFunc deref = this.funcMap.get(((ICall) inst).rule.getUniqueName());
				if (deref == null) {
					Verbose.debug("no deref: " + ((ICall) inst).rule.getUniqueName());
				}
				((ICall) inst).setResolvedJump(deref.compiled);
			}
			// Verbose.debug("\t" + inst.id + "\t" + inst);
		}
		long t2 = System.nanoTime();
		Verbose.printElapsedTime("CompilingTime", t, t2);
		if (coder != null) {
			coder.setHeader(codeList.size(), funcMap.size(), memoPointList == null ? 0 : memoPointList.size());
			coder.setInstructions(codeList.ArrayValues, codeList.size());
		}
		this.funcMap = null;
		return new NezCode(codeList.ArrayValues[0], codeList.size(), memoPointList);
	}

	private Production encodingProduction;

	protected final Production getEncodingProduction() {
		return this.encodingProduction;
	}

	protected void encodeProduction(UList<Instruction> codeList, Production p, Instruction next) {
		String uname = p.getUniqueName();
		ParseFunc pcode = this.funcMap.get(uname);
		if (pcode != null) {
			encodingProduction = p;
			pcode.compiled = encode(pcode.e, next, null/* failjump */);
			Instruction block = new ILabel(p, pcode.compiled);
			this.layoutCode(codeList, block);
			// if(code.memoPoint != null) {
			// code.memoStart = this.encodeMemoizingProduction(code);
			// this.layoutCode(codeList, code.memoStart);
			// }
		}
	}

	@Override
	protected Expression optimizeLocalProduction(Production p) {
		return GrammarOptimizer.resolveNonTerminal(p.getExpression());
	}

	public final void layoutCode(UList<Instruction> codeList, Instruction inst) {
		if (inst == null) {
			return;
		}
		if (inst.id == -1) {
			inst.id = codeList.size();
			codeList.add(inst);
			layoutCode(codeList, inst.next);
			if (inst.next != null && inst.id + 1 != inst.next.id) {
				Instruction.labeling(inst.next);
			}
			layoutCode(codeList, inst.branch());
			if (inst instanceof IFirst) {
				IFirst match = (IFirst) inst;
				for (int ch = 0; ch < match.jumpTable.length; ch++) {
					layoutCode(codeList, match.jumpTable[ch]);
				}
			}
			// encode(inst.branch2());
		}
	}

}
