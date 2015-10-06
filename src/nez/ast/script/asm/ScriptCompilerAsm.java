package nez.ast.script.asm;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import nez.ast.TreeVisitor;
import nez.ast.script.CommonSymbols;
import nez.ast.script.Hint;
import nez.ast.script.ScriptContext;
import nez.ast.script.TypeSystem;
import nez.ast.script.TypedTree;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class ScriptCompilerAsm extends TreeVisitor implements CommonSymbols {
	private TypeSystem typeSystem;
	private ScriptClassLoader cLoader;
	private ClassBuilder cBuilder;
	private MethodBuilder mBuilder;
	private String classPath = "konoha/runtime";

	public ScriptCompilerAsm(TypeSystem typeSystem, ScriptClassLoader cLoader) {
		super(TypedTree.class);
		this.typeSystem = typeSystem;
		this.cLoader = cLoader;
	}

	private void visit(TypedTree node) {
		switch (node.hint) {
		case Constant:
			visitConstantHint(node);
			return;
		case StaticDynamicInvocation:
		case StaticInvocation:
			visitStaticInvocationHint(node);
			return;
		case UpCast:
			visitUpCastHint(node);
			return;
		case DownCast:
			visitDownCastHint(node);
			return;
		case Apply:
			visitApplyHint(node);
			return;
		case RecursiveApply:
			visitRecursiveApplyHint(node);
			return;
		case GetField:
			this.visitGetFieldHint(node);
			return;
		case SetField:
			this.visitSetFieldHint(node);
			return;
		case MethodApply:
			this.visitMehodApplyHint(node);
			break;
		case Unique:
			break;
		default:
			break;
		}
		TRACE("compiling (no hint): " + node.getTag());
		this.visit("visit", node);
	}

	private Class<?> typeof(TypedTree node) {
		// node.getTypedClass();
		return typeSystem.typeof(node);
	}

	private void TRACE(String fmt, Object... args) {
		if (ScriptContext.verbose) {
			System.err.println("TRACE: " + String.format(fmt, args));
		}
	}

	private void TODO(String fmt, Object... args) {
		if (ScriptContext.verbose) {
			System.err.println("TODO: " + String.format(fmt, args));
		}
	}

	/* typechecker hints */

	private void visitConstantHint(TypedTree node) {
		assert (node.hint() == Hint.Constant);
		Object v = node.getValue();
		if (v instanceof String) {
			this.mBuilder.push((String) v);
		} else if (v instanceof Integer || v instanceof Character || v instanceof Byte) {
			this.mBuilder.push(((Number) v).intValue());
		} else if (v instanceof Double) {
			this.mBuilder.push(((Double) v).doubleValue());
		} else if (v instanceof Boolean) {
			this.mBuilder.push(((Boolean) v).booleanValue());
		} else if (v instanceof Long) {
			this.mBuilder.push(((Long) v).longValue());
		} else if (v instanceof Class<?>) {
			this.mBuilder.push(Type.getType((Class<?>) v));
		} else if (v == null) {
			this.mBuilder.pushNull();
		} else {
			TODO("FIXME: Constant %s", v.getClass().getName());
		}
	}

	private void visitStaticInvocationHint(TypedTree node) {
		for (TypedTree sub : node) {
			visit(sub);
		}
		Type owner = Type.getType(node.getMethod().getDeclaringClass());
		Method methodDesc = Method.getMethod(node.getMethod());
		this.mBuilder.invokeStatic(owner, methodDesc);
	}

	private void visitUpCastHint(TypedTree node) {
		visit(node.get(_expr));
	}

	private void visitDownCastHint(TypedTree node) {
		visit(node.get(_expr));
		this.mBuilder.checkCast(Type.getType(node.getClassType()));
	}

	private void visitApplyHint(TypedTree node) {
		for (TypedTree sub : node.get(_param)) {
			visit(sub);
		}
		Type owner = Type.getType(node.getMethod().getDeclaringClass());
		Method methodDesc = Method.getMethod(node.getMethod());
		this.mBuilder.invokeStatic(owner, methodDesc);
	}

	private void visitRecursiveApplyHint(TypedTree node) {
		for (TypedTree sub : node.get(_param)) {
			visit(sub);
		}
		this.mBuilder.invokeStatic(this.cBuilder.getTypeDesc(), this.mBuilder.getMethod());
	}

	private void visitGetFieldHint(TypedTree node) {
		Field f = node.getField();
		if (Modifier.isStatic(f.getModifiers())) {
			Type owner = Type.getType(f.getDeclaringClass());
			String name = f.getName();
			Type fieldType = Type.getType(f.getType());
			this.mBuilder.getStatic(owner, name, fieldType);
		} else {
			visit(node.get(_recv));
			Type owner = Type.getType(f.getDeclaringClass());
			String name = f.getName();
			Type fieldType = Type.getType(f.getType());
			this.mBuilder.getField(owner, name, fieldType);
		}
	}

	private void visitMehodApplyHint(TypedTree node) {
		for (TypedTree sub : node.get(_param)) {
			visit(sub);
		}
		Type owner = Type.getType(node.getMethod().getDeclaringClass());
		Method methodDesc = Method.getMethod(node.getMethod());
		this.mBuilder.invokeVirtual(owner, methodDesc);
	}

	private void visitSetFieldHint(TypedTree node) {
		Field f = node.getField();
		if (Modifier.isStatic(f.getModifiers())) {
			visit(node.get(_expr));
			Type owner = Type.getType(f.getDeclaringClass());
			String name = f.getName();
			Type fieldType = Type.getType(f.getType());
			this.mBuilder.putStatic(owner, name, fieldType);
		} else {
			visit(node.get(_recv));
			visit(node.get(_expr));
			Type owner = Type.getType(f.getDeclaringClass());
			String name = f.getName();
			Type fieldType = Type.getType(f.getType());
			this.mBuilder.putField(owner, name, fieldType);
		}
	}

	void visitArrayUtils(Class<?> elementType, TypedTree node) {
		this.mBuilder.push(node.size());
		this.mBuilder.newArray(Type.getType(elementType));
		int index = 0;
		for (TypedTree sub : node) {
			this.mBuilder.dup();
			this.mBuilder.push(index);
			this.visit(sub);
			this.mBuilder.arrayStore(Type.getType(elementType));
			index++;
		}
	}

	// methodAdapter.newInstance(NPE_TYPE);
	// methodAdapter.dup();
	// methodAdapter.push("The dispatcher must never be null!");
	// methodAdapter.invokeConstructor(NPE_TYPE,NPE_CONSTRUCTOR);

	/* class */

	public void openClass(String name) {
		this.cBuilder = new ClassBuilder(this.classPath + name, null, null, null);
	}

	public void openClass(String name, Class<?> superClass, Class<?>... interfaces) {
		this.cBuilder = new ClassBuilder(this.classPath + name, null, superClass, interfaces);
	}

	public void openClass(int acc, String name, Class<?> superClass, Class<?>... interfaces) {
		this.cBuilder = new ClassBuilder(acc, this.classPath + name, null, superClass, interfaces);
	}

	public Class<?> closeClass() {
		// cLoader.setDump(true);
		Class<?> c = cLoader.definedAndLoadClass(this.cBuilder.getQualifiedClassName(), cBuilder.toByteArray());
		this.cBuilder = null; //
		return c;
	}

	/* global variable */

	public Class<?> compileGlobalVariableClass(Class<?> t, String name) {
		this.openClass("G_" + name);
		this.cBuilder.addField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "v", t, null);
		return this.closeClass();
	}

	/* generate function class */
	public Class<?> compileFuncType(String cname, Class<?> returnType, Class<?>... paramTypes) {
		this.openClass(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, cname, konoha.Function.class);
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, returnType, "invoke", paramTypes);
		this.mBuilder.endMethod();
		Method desc = Method.getMethod("void <init> ()");
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC, desc);
		this.mBuilder.loadThis(); // InitMethod.visitVarInsn(ALOAD, 0);
		this.mBuilder.invokeConstructor(Type.getType(konoha.Function.class), desc);
		this.mBuilder.returnValue();
		this.mBuilder.endMethod();

		return this.closeClass();
	}

	/* generate function class */

	public Class<?> compileFunctionWrapperClass(Class<?> superClass, java.lang.reflect.Method staticMethod) {
		this.openClass("C" + staticMethod.getName() + "Wrapper" + unique, superClass);
		unique++;
		Class<?> returnType = staticMethod.getReturnType();
		Class<?>[] paramTypes = staticMethod.getParameterTypes();
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC, returnType, "invoke", paramTypes);
		int index = 1;
		for (int i = 0; i < paramTypes.length; i++) {
			Type aType = Type.getType(paramTypes[i]);
			this.mBuilder.visitVarInsn(aType.getOpcode(Opcodes.ILOAD), index);
			// this.mBuilder.loadLocal(index, aType); FIXME
			index += aType.getSize();
		}
		Type owner = Type.getType(staticMethod.getDeclaringClass());
		Method methodDesc = Method.getMethod(staticMethod);
		this.mBuilder.invokeStatic(owner, methodDesc);
		this.mBuilder.returnValue();
		this.mBuilder.endMethod();

		Method desc = Method.getMethod("void <init> ()");
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC, desc);
		this.mBuilder.loadThis(); // InitMethod.visitVarInsn(ALOAD, 0);
		this.mBuilder.invokeConstructor(Type.getType(superClass), desc);
		this.mBuilder.returnValue();
		this.mBuilder.endMethod();

		return this.closeClass();
	}

	/* static function */
	int unique = 0;

	public Class<?> compileStaticFuncDecl(String className, TypedTree node) {
		this.openClass("F_" + className + "_" + unique);
		unique++;
		this.visitFuncDecl(node);
		return this.closeClass();
	}

	private void createClosure() {
		// ClassBuilder closure = new ClassBuilder(fullyQualifiedClassName,
		// sourceName, superClass, interfaces);
	}

	public void visitFuncDecl(TypedTree node) {
		// this.mBuilderStack.push(this.mBuilder);
		TypedTree nameNode = node.get(_name);
		TypedTree args = node.get(_param);
		String name = nameNode.toText();
		Class<?> funcType = typeof(nameNode);
		Class<?>[] paramTypes = new Class<?>[args.size()];
		for (int i = 0; i < paramTypes.length; i++) {
			paramTypes[i] = typeof(args.get(i));
		}
		this.mBuilder = this.cBuilder.newMethodBuilder(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, funcType, name, paramTypes);
		this.mBuilder.enterScope();
		for (TypedTree arg : args) {
			this.mBuilder.defineArgument(arg.getText(_name, null), typeof(arg));
		}
		visit(node.get(_body));
		this.mBuilder.exitScope();
		this.mBuilder.returnValue();
		this.mBuilder.endMethod();
	}

	public void visitClassDecl(TypedTree node) throws ClassNotFoundException {
		String name = node.getText(_name, null);
		TypedTree implNode = node.get(_impl);
		TypedTree bodyNode = node.get(_body);
		Class<?> superClass = Class.forName(this.classPath + node.getText(_super, null));
		Class<?>[] implClasses = new Class<?>[implNode.size()];
		for (int i = 0; i < implNode.size(); i++) {
			implClasses[i] = Class.forName(this.classPath + implNode.getText(i, null));
		}
		this.openClass(name, superClass, implClasses);
		for (TypedTree n : bodyNode) {
			this.visit(n);
		}
		this.closeClass();
	}

	public void visitBlock(TypedTree node) {
		this.mBuilder.enterScope();
		for (TypedTree stmt : node) {
			this.mBuilder.setLineNum(node.getLineNum());
			visit(stmt);
		}
		this.mBuilder.exitScope();
	}

	public void visitConstructor(TypedTree node) {
		// TODO
	}

	public void visitFieldDecl(TypedTree node) {
		// TODO
	}

	public void visitMethodDecl(TypedTree node) {
		// TODO
	}

	public void visitVarDecl(TypedTree node) {
		if (node.size() > 1) {
			TypedTree varNode = node.get(_name);
			TypedTree valueNode = node.get(_expr);
			visit(valueNode);
			varNode.setType(typeof(valueNode));
			this.mBuilder.createNewVarAndStore(varNode.toText(), typeof(valueNode));
		}
	}

	public void visitIf(TypedTree node) {
		visit(node.get(_cond));
		this.mBuilder.push(true);

		Label elseLabel = this.mBuilder.newLabel();
		Label mergeLabel = this.mBuilder.newLabel();

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.NE, elseLabel);

		// then
		visit(node.get(_then));
		this.mBuilder.goTo(mergeLabel);

		// else
		this.mBuilder.mark(elseLabel);
		if (node.size() > 2) {
			visit(node.get(_else));
		}

		// merge
		this.mBuilder.mark(mergeLabel);
	}

	public void visitWhile(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();
		Label condLabel = this.mBuilder.newLabel();

		this.mBuilder.goTo(condLabel);

		// Block
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));

		// Condition
		this.mBuilder.mark(condLabel);
		visit(node.get(_cond));
		this.mBuilder.push(true);

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, beginLabel);
	}

	public void visitDoWhile(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();

		// Do
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));

		// Condition
		visit(node.get(_cond));
		this.mBuilder.push(true);

		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, GeneratorAdapter.EQ, beginLabel);
	}

	public void visitFor(TypedTree node) {
		Label beginLabel = this.mBuilder.newLabel();
		Label condLabel = this.mBuilder.newLabel();

		// Initialize
		visit(node.get(_init));

		this.mBuilder.goTo(condLabel);

		// Block
		this.mBuilder.mark(beginLabel);
		visit(node.get(_body));
		visit(node.get(_iter));

		// Condition
		this.mBuilder.mark(condLabel);
		visit(node.get(_cond));
		this.mBuilder.push(true);
		this.mBuilder.ifCmp(Type.BOOLEAN_TYPE, MethodBuilder.EQ, beginLabel);
	}

	public void visitAssign(TypedTree node) {
		String name = node.getText(_left, null);
		TypedTree valueNode = node.get(_right);
		VarEntry var = this.mBuilder.getVar(name);
		visit(valueNode);
		if (var != null) {
			this.mBuilder.storeToVar(var);
		} else {
			this.mBuilder.createNewVarAndStore(name, typeof(valueNode));
		}
	}

	public void visitReturn(TypedTree node) {
		if (node.get(_expr, null) != null) {
			this.visit(node.get(_expr));
		}
		this.mBuilder.returnValue();
	}

	public void visitExpression(TypedTree node) {
		this.visit(node.get(0));
		if (node.getType() != void.class) {
			this.mBuilder.pop(node.getClassType());
		}
	}

	public void visitName(TypedTree node) {
		VarEntry var = this.mBuilder.getVar(node.toText());
		// node.setType(var.getVarClass());
		this.mBuilder.loadFromVar(var);
	}

	// public void visitBinaryNode(TypedTree node) {
	// TypedTree left = node.get(_left);
	// TypedTree right = node.get(_right);
	// this.visit(left);
	// this.visit(right);
	// this.mBuilder.callStaticMethod(JCodeOperator.class, typeof(node),
	// node.getTag().getSymbol(), typeof(left), typeof(right));
	// }
	//
	// public void visitCompNode(TypedTree node) {
	// TypedTree left = node.get(_left);
	// TypedTree right = node.get(_right);
	// this.visit(left);
	// this.visit(right);
	// this.mBuilder.callStaticMethod(JCodeOperator.class, typeof(node),
	// node.getTag().getSymbol(), typeof(left), typeof(right));
	// }
	//
	// public void visitAdd(TypedTree node) {
	// this.visitBinaryNode(node);
	// }
	//
	// public void visitSub(TypedTree node) {
	// this.visitBinaryNode(node);
	// }
	//
	// public void visitMul(TypedTree node) {
	// this.visitBinaryNode(node);
	// }
	//
	// public void visitDiv(TypedTree node) {
	// this.visitBinaryNode(node);
	// }
	//
	// public void visitNotEquals(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitLessThan(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitLessThanEquals(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitGreaterThan(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitGreaterThanEquals(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitLogicalAnd(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	// public void visitLogicalOr(TypedTree node) {
	// this.visitCompNode(node);
	// }
	//
	//
	// public void visitContinue(TypedTree node) {
	// }
	//
	// public void visitBreak(TypedTree node) {
	// }
	//
	// public void visitReturn(TypedTree node) {
	// this.mBuilder.returnValue();
	// }
	//
	// public void visitThrow(TypedTree node) {
	// }
	//
	// public void visitWith(TypedTree node) {
	// }
	//
	// public void visitExpression(TypedTree node) {
	// this.visit(node.get(0));
	// }
	//

	//
	// public void visitApply(TypedTree node) {
	// // TypedTree fieldNode = node.get(_recv"));
	// TypedTree argsNode = node.get(_param);
	// TypedTree name = node.get(_name);
	// // VarEntry var = null;
	//
	// Class<?>[] argTypes = new Class<?>[argsNode.size()];
	// for (int i = 0; i < argsNode.size(); i++) {
	// TypedTree arg = argsNode.get(i);
	// this.visit(arg);
	// argTypes[i] = arg.getTypedClass();
	// }
	// org.objectweb.asm.commons.Method method =
	// Methods.method(node.getTypedClass(), name.toText(), argTypes);
	// this.mBuilder.invokeStatic(this.cBuilder.getTypeDesc(), method);
	// // var = this.scope.getLocalVar(top.toText());
	// // if (var != null) {
	// // this.mBuilder.loadFromVar(var);
	// //
	// // } else {
	// // this.generateRunTimeLibrary(top, argsNode);
	// // this.popUnusedValue(node);
	// // return;
	// // }
	// }
	//
	// public void generateRunTimeLibrary(TypedTree fieldNode, TypedTree
	// argsNode) {
	// String classPath = "";
	// String methodName = null;
	// for (int i = 0; i < fieldNode.size(); i++) {
	// if (i < fieldNode.size() - 2) {
	// classPath += fieldNode.get(i).toText();
	// classPath += ".";
	// } else if (i == fieldNode.size() - 2) {
	// classPath += fieldNode.get(i).toText();
	// } else {
	// methodName = fieldNode.get(i).toText();
	// }
	// }
	// Type[] argTypes = new Type[argsNode.size()];
	// for (int i = 0; i < argsNode.size(); i++) {
	// TypedTree arg = argsNode.get(i);
	// this.visit(arg);
	// argTypes[i] = Type.getType(arg.getTypedClass());
	// }
	// this.mBuilder.callDynamicMethod("nez/ast/jcode/StandardLibrary",
	// "bootstrap", methodName, classPath, argTypes);
	// }
	//
	// public void visitField(TypedTree node) {
	// TypedTree top = node.get(0);
	// VarEntry var = null;
	// if (_Name.equals(top.getTag())) {
	// var = this.scope.getLocalVar(top.toText());
	// if (var != null) {
	// this.mBuilder.loadFromVar(var);
	// } else {
	//
	// return;
	// }
	// } else {
	// visit(top);
	// }
	// for (int i = 1; i < node.size(); i++) {
	// TypedTree member = node.get(i);
	// if (_Name.equals(member.getTag())) {
	// this.mBuilder.getField(Type.getType(var.getVarClass()), member.toText(),
	// Type.getType(Object.class));
	// visit(member);
	// }
	// }
	// }
	//
	// //
	// public void visitUnaryNode(TypedTree node) {
	// TypedTree child = node.get(0);
	// this.visit(child);
	// node.setType(this.typeInfferUnary(node.get(0)));
	// this.mBuilder.callStaticMethod(JCodeOperator.class, node.getTypedClass(),
	// node.getTag().getSymbol(), child.getTypedClass());
	// this.popUnusedValue(node);
	// }
	//
	// public void visitPlus(TypedTree node) {
	// this.visitUnaryNode(node);
	// }
	//
	// public void visitMinus(TypedTree node) {
	// this.visitUnaryNode(node);
	// }
	//
	private void evalPrefixInc(TypedTree node, int amount) {
		String name = node.getText(_name, null);
		VarEntry var = this.mBuilder.getVar(name);
		if (var != null) {
			this.mBuilder.callIinc(var, amount);
			this.mBuilder.loadFromVar(var);
		} else {
			throw new RuntimeException("undefined variable " + name);
		}
	}

	private void evalSuffixInc(TypedTree node, int amount) {
		String name = node.getText(_name, null);
		VarEntry var = this.mBuilder.getVar(name);
		if (var != null) {
			node.setType(int.class);
			this.mBuilder.loadFromVar(var);
			this.mBuilder.callIinc(var, amount);
		} else {
			throw new RuntimeException("undefined variable " + name);
		}
	}

	public void visitSuffixInc(TypedTree node) {
		this.evalSuffixInc(node, 1);
	}

	public void visitSuffixDec(TypedTree node) {
		this.evalSuffixInc(node, -1);
	}

	public void visitPrefixInc(TypedTree node) {
		this.evalPrefixInc(node, 1);
	}

	public void visitPrefixDec(TypedTree node) {
		this.evalPrefixInc(node, -1);
	}

	//
	// private Class<?> typeInfferUnary(TypedTree node) {
	// Class<?> nodeType = node.getTypedClass();
	// if (nodeType == int.class) {
	// return int.class;
	// } else if (nodeType == double.class) {
	// return double.class;
	// }
	// throw new RuntimeException("type error: " + node);
	// }

	// public void visitNull(TypedTree p) {
	// this.mBuilder.pushNull();
	// }

	// void visitArray(TypedTree p){
	// this.mBuilder.newArray(Object.class);
	// }

	public void visitList(TypedTree node) {
		for (TypedTree element : node) {
			visit(element);
		}
	}

	// public void visitTrue(TypedTree p) {
	// // p.setType(boolean.class);
	// this.mBuilder.push(true);
	// }
	//
	// public void visitFalse(TypedTree p) {
	// // p.setType(boolean.class);
	// this.mBuilder.push(false);
	// }

	// public void visitInt(TypedTree p) {
	// // p.setType(int.class);
	// this.mBuilder.push(Integer.parseInt(p.toText()));
	// }
	//
	// public void visitInteger(TypedTree p) {
	// this.visitInt(p);
	// }
	//
	// public void visitOctalInteger(TypedTree p) {
	// // p.setType(int.class);
	// this.mBuilder.push(Integer.parseInt(p.toText(), 8));
	// }
	//
	// public void visitHexInteger(TypedTree p) {
	// // p.setType(int.class);
	// this.mBuilder.push(Integer.parseInt(p.toText(), 16));
	// }
	//
	// public void visitDouble(TypedTree p) {
	// // p.setType(double.class);
	// this.mBuilder.push(Double.parseDouble(p.toText()));
	// }
	//
	// public void visitString(TypedTree p) {
	// // p.setType(String.class);
	// this.mBuilder.push(p.toText());
	// }
	//
	// public void visitCharacter(TypedTree p) {
	// // p.setType(String.class);
	// this.mBuilder.push(p.toText());
	// // p.setType(char.class);
	// // this.mBuilder.push(p.toText().charAt(0));
	// }

	// public void visitUndefined(TypedTree p) {
	// System.out.println("undefined: " + p.getTag().getSymbol());
	// }

	/* code copied from libzen */

	// private JavaStaticFieldNode GenerateFunctionAsSymbolField(String
	// FuncName, ZFunctionNode Node) {
	// @Var ZFuncType FuncType = Node.GetFuncType();
	// String ClassName = this.NameFunctionClass(FuncName, FuncType);
	// Class<?> FuncClass = this.LoadFuncClass(FuncType);
	// @Var AsmClassBuilder ClassBuilder =
	// this.AsmLoader.NewClass(ACC_PUBLIC|ACC_FINAL, Node, ClassName,
	// FuncClass);
	//
	// AsmMethodBuilder InvokeMethod = ClassBuilder.NewMethod(ACC_PUBLIC |
	// ACC_FINAL, "Invoke", FuncType);
	// int index = 1;
	// for(int i = 0; i < FuncType.GetFuncParamSize(); i++) {
	// Type AsmType = this.AsmType(FuncType.GetFuncParamType(i));
	// InvokeMethod.visitVarInsn(AsmType.getOpcode(ILOAD), index);
	// index += AsmType.getSize();
	// }
	// InvokeMethod.visitMethodInsn(INVOKESTATIC, ClassName, "f", FuncType);
	// InvokeMethod.visitReturn(FuncType.GetReturnType());
	// InvokeMethod.Finish();
	//
	// ClassBuilder.AddField(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "function",
	// FuncClass, null);
	//
	//
	// FuncClass = this.AsmLoader.LoadGeneratedClass(ClassName);
	// this.SetGeneratedClass(ClassName, FuncClass);
	// return new JavaStaticFieldNode(null, FuncClass, FuncType, "function");
	// }

	// @Override public void VisitArrayLiteralNode(ZArrayLiteralNode Node) {
	// if(Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "ambigious array");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// else {
	// Class<?> ArrayClass = LibAsm.AsArrayClass(Node.Type);
	// String Owner = Type.getInternalName(ArrayClass);
	// this.mBuilder.visitTypeInsn(NEW, Owner);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(Node.Type.TypeId);
	// this.mBuilder.PushNodeListAsArray(LibAsm.AsElementClass(Node.Type), 0,
	// Node);
	// this.mBuilder.SetLineNumber(Node);
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, Owner, "<init>",
	// LibAsm.NewArrayDescriptor(Node.Type));
	// }
	// }

	// @Override
	// public void VisitMapLiteralNode(ZMapLiteralNode Node) {
	// if (Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "ambigious map");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// } else {
	// String Owner = Type.getInternalName(ZObjectMap.class);
	// this.mBuilder.visitTypeInsn(NEW, Owner);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(Node.Type.TypeId);
	// this.mBuilder.PushInt(Node.GetListSize() * 2);
	// this.mBuilder.visitTypeInsn(ANEWARRAY,
	// Type.getInternalName(Object.class));
	// for (int i = 0; i < Node.GetListSize(); i++) {
	// ZMapEntryNode EntryNode = Node.GetMapEntryNode(i);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(i * 2);
	// this.mBuilder.PushNode(String.class, EntryNode.KeyNode());
	// this.mBuilder.visitInsn(Opcodes.AASTORE);
	// this.mBuilder.visitInsn(DUP);
	// this.mBuilder.PushInt(i * 2 + 1);
	// this.mBuilder.PushNode(Object.class, EntryNode.ValueNode());
	// this.mBuilder.visitInsn(Opcodes.AASTORE);
	// }
	// this.mBuilder.SetLineNumber(Node);
	// String Desc = Type.getMethodDescriptor(Type.getType(void.class), new
	// Type[] { Type.getType(int.class), Type.getType(Object[].class) });
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, Owner, "<init>", Desc);
	// }
	// }
	//
	// @Override
	// public void VisitNewObjectNode(ZNewObjectNode Node) {
	// if (Node.IsUntyped()) {
	// ZLogger._LogError(Node.SourceToken, "no class for new operator");
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// } else {
	// String ClassName = Type.getInternalName(this.GetJavaClass(Node.Type));
	// this.mBuilder.visitTypeInsn(NEW, ClassName);
	// this.mBuilder.visitInsn(DUP);
	// Constructor<?> jMethod = this.GetConstructor(Node.Type, Node);
	// if (jMethod != null) {
	// Class<?>[] P = jMethod.getParameterTypes();
	// for (int i = 0; i < P.length; i++) {
	// this.mBuilder.PushNode(P[i], Node.GetListAt(i));
	// }
	// this.mBuilder.SetLineNumber(Node);
	// this.mBuilder.visitMethodInsn(INVOKESPECIAL, ClassName, "<init>",
	// Type.getConstructorDescriptor(jMethod));
	// } else {
	// ZLogger._LogError(Node.SourceToken, "no constructor: " + Node.Type);
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// }
	// }
	//
	// public void VisitStaticFieldNode(JavaStaticFieldNode Node) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// Type.getInternalName(Node.StaticClass), Node.FieldName,
	// this.GetJavaClass(Node.Type));
	// }
	//
	// @Override
	// public void VisitGlobalNameNode(ZGlobalNameNode Node) {
	// if (Node.IsFuncNameNode()) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// this.NameFunctionClass(Node.GlobalName, Node.FuncType), "f",
	// this.GetJavaClass(Node.Type));
	// } else if (!Node.IsUntyped()) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC,
	// this.NameGlobalNameClass(Node.GlobalName), "_",
	// this.GetJavaClass(Node.Type));
	// } else {
	// ZLogger._LogError(Node.SourceToken, "undefined symbol: " +
	// Node.GlobalName);
	// this.mBuilder.visitInsn(Opcodes.ACONST_NULL);
	// }
	// }
	//
	// @Override
	// public void VisitGetterNode(ZGetterNode Node) {
	// if (Node.IsUntyped()) {
	// Method sMethod = JavaMethodTable.GetStaticMethod("GetField");
	// ZNode NameNode = new ZStringNode(Node, null, Node.GetName());
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), NameNode });
	// } else {
	// Class<?> RecvClass = this.GetJavaClass(Node.RecvNode().Type);
	// Field jField = this.GetField(RecvClass, Node.GetName());
	// String Owner = Type.getType(RecvClass).getInternalName();
	// String Desc = Type.getType(jField.getType()).getDescriptor();
	// if (Modifier.isStatic(jField.getModifiers())) {
	// this.mBuilder.visitFieldInsn(Opcodes.GETSTATIC, Owner, Node.GetName(),
	// Desc);
	// } else {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// this.mBuilder.visitFieldInsn(GETFIELD, Owner, Node.GetName(), Desc);
	// }
	// this.mBuilder.CheckReturnCast(Node, jField.getType());
	// }
	// }
	//
	// @Override
	// public void VisitSetterNode(ZSetterNode Node) {
	// if (Node.IsUntyped()) {
	// Method sMethod = JavaMethodTable.GetStaticMethod("SetField");
	// ZNode NameNode = new ZStringNode(Node, null, Node.GetName());
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), NameNode, Node.ExprNode() });
	// } else {
	// Class<?> RecvClass = this.GetJavaClass(Node.RecvNode().Type);
	// Field jField = this.GetField(RecvClass, Node.GetName());
	// String Owner = Type.getType(RecvClass).getInternalName();
	// String Desc = Type.getType(jField.getType()).getDescriptor();
	// if (Modifier.isStatic(jField.getModifiers())) {
	// this.mBuilder.PushNode(jField.getType(), Node.ExprNode());
	// this.mBuilder.visitFieldInsn(PUTSTATIC, Owner, Node.GetName(), Desc);
	// } else {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// this.mBuilder.PushNode(jField.getType(), Node.ExprNode());
	// this.mBuilder.visitFieldInsn(PUTFIELD, Owner, Node.GetName(), Desc);
	// }
	// }
	// }
	//
	// @Override
	// public void VisitGetIndexNode(ZGetIndexNode Node) {
	// Method sMethod =
	// JavaMethodTable.GetBinaryStaticMethod(Node.RecvNode().Type, "[]",
	// Node.IndexNode().Type);
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), Node.IndexNode() });
	// }
	//
	// @Override
	// public void VisitSetIndexNode(ZSetIndexNode Node) {
	// Method sMethod =
	// JavaMethodTable.GetBinaryStaticMethod(Node.RecvNode().Type, "[]=",
	// Node.IndexNode().Type);
	// this.mBuilder.ApplyStaticMethod(Node, sMethod, new ZNode[] {
	// Node.RecvNode(), Node.IndexNode(), Node.ExprNode() });
	// }
	//
	// @Override
	// public void VisitMethodCallNode(ZMethodCallNode Node) {
	// this.mBuilder.SetLineNumber(Node);
	// Method jMethod = this.GetMethod(Node.RecvNode().Type, Node.MethodName(),
	// Node);
	// if (jMethod != null) {
	// if (!Modifier.isStatic(jMethod.getModifiers())) {
	// this.mBuilder.PushNode(null, Node.RecvNode());
	// }
	// Class<?>[] P = jMethod.getParameterTypes();
	// for (int i = 0; i < P.length; i++) {
	// this.mBuilder.PushNode(P[i], Node.GetListAt(i));
	// }
	// int inst = this.GetInvokeType(jMethod);
	// String owner = Type.getInternalName(jMethod.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(inst, owner, jMethod.getName(),
	// Type.getMethodDescriptor(jMethod));
	// this.mBuilder.CheckReturnCast(Node, jMethod.getReturnType());
	// } else {
	// jMethod = JavaMethodTable.GetStaticMethod("InvokeUnresolvedMethod");
	// this.mBuilder.PushNode(Object.class, Node.RecvNode());
	// this.mBuilder.PushConst(Node.MethodName());
	// this.mBuilder.PushNodeListAsArray(Object.class, 0, Node);
	// this.mBuilder.ApplyStaticMethod(Node, jMethod);
	// }
	// }
	//
	// @Override
	// public void VisitInstanceOfNode(ZInstanceOfNode Node) {
	// if (!(Node.LeftNode().Type instanceof ZGenericType)) {
	// this.VisitNativeInstanceOfNode(Node);
	// return;
	// }
	// Node.LeftNode().Accept(this);
	// this.mBuilder.Pop(Node.LeftNode().Type);
	// this.mBuilder.PushLong(Node.LeftNode().Type.TypeId);
	// this.mBuilder.PushLong(Node.TargetType().TypeId);
	// Method method = JavaMethodTable.GetBinaryStaticMethod(ZType.IntType,
	// "==", ZType.IntType);
	// String owner = Type.getInternalName(method.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(INVOKESTATIC, owner, method.getName(),
	// Type.getMethodDescriptor(method));
	// }
	//
	// private void VisitNativeInstanceOfNode(ZInstanceOfNode Node) {
	// Class<?> JavaClass = this.GetJavaClass(Node.TargetType());
	// if (Node.TargetType().IsIntType()) {
	// JavaClass = Long.class;
	// } else if (Node.TargetType().IsFloatType()) {
	// JavaClass = Double.class;
	// } else if (Node.TargetType().IsBooleanType()) {
	// JavaClass = Boolean.class;
	// }
	// this.invokeBoxingMethod(Node.LeftNode());
	// this.mBuilder.visitTypeInsn(INSTANCEOF, JavaClass);
	// }
	//
	// private void invokeBoxingMethod(ZNode TargetNode) {
	// Class<?> TargetClass = Object.class;
	// if (TargetNode.Type.IsIntType()) {
	// TargetClass = Long.class;
	// } else if (TargetNode.Type.IsFloatType()) {
	// TargetClass = Double.class;
	// } else if (TargetNode.Type.IsBooleanType()) {
	// TargetClass = Boolean.class;
	// }
	// Class<?> SourceClass = this.GetJavaClass(TargetNode.Type);
	// Method Method = JavaMethodTable.GetCastMethod(TargetClass, SourceClass);
	// TargetNode.Accept(this);
	// if (!TargetClass.equals(Object.class)) {
	// String owner = Type.getInternalName(Method.getDeclaringClass());
	// this.mBuilder.visitMethodInsn(INVOKESTATIC, owner, Method.getName(),
	// Type.getMethodDescriptor(Method));
	// }
	// }
	//
	// @Override
	// public void VisitAndNode(ZAndNode Node) {
	// Label elseLabel = new Label();
	// Label mergeLabel = new Label();
	// this.mBuilder.PushNode(boolean.class, Node.LeftNode());
	// this.mBuilder.visitJumpInsn(IFEQ, elseLabel);
	//
	// this.mBuilder.PushNode(boolean.class, Node.RightNode());
	// this.mBuilder.visitJumpInsn(IFEQ, elseLabel);
	//
	// this.mBuilder.visitLdcInsn(true);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(elseLabel);
	// this.mBuilder.visitLdcInsn(false);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(mergeLabel);
	// }
	//
	// @Override
	// public void VisitOrNode(ZOrNode Node) {
	// Label thenLabel = new Label();
	// Label mergeLabel = new Label();
	// this.mBuilder.PushNode(boolean.class, Node.LeftNode());
	// this.mBuilder.visitJumpInsn(IFNE, thenLabel);
	//
	// this.mBuilder.PushNode(boolean.class, Node.RightNode());
	// this.mBuilder.visitJumpInsn(IFNE, thenLabel);
	//
	// this.mBuilder.visitLdcInsn(false);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(thenLabel);
	// this.mBuilder.visitLdcInsn(true);
	// this.mBuilder.visitJumpInsn(GOTO, mergeLabel);
	//
	// this.mBuilder.visitLabel(mergeLabel);
	// }

}
