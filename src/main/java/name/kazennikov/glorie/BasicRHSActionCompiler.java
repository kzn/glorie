package name.kazennikov.glorie;

import name.kazennikov.glorie.groovy.AttrAssignment;
import name.kazennikov.logger.Logger;

import java.io.File;

/**
 * Implements groovy-compilation of RHS blocks
 */
public class BasicRHSActionCompiler implements RHSActionCompiler {
	private static final Logger logger = Logger.getLogger();


	Compiler compiler;

	/**
	 * Package name for action classes. It's called a "dir name" because
	 * we used to dump the action classes to disk and compile them there.
	 */
	static private String actionsDirName = "glractionclasses";


	public BasicRHSActionCompiler(Compiler compiler) {
		this.compiler = compiler;
	}

	@Override
	public RHSActionGenerator add(Grammar g, CompiledGrammar.Rule rule, RHSAction action) {
		try {
			if(action instanceof GroovyRHSAttrAction) {
				SourceInfo sourceInfo = addAttrAction(((GroovyRHSAction) action).block, g, rule);
				return new NamedClassRHSGenerator(compiler, sourceInfo);
			} else if(action instanceof GroovyRHSAction) {
				SourceInfo sourceInfo = addAction(((GroovyRHSAction) action).block, g, rule);
				return new NamedClassRHSGenerator(compiler, sourceInfo);
			}
		} catch(Exception e) {
			logger.error(e);
		}

		return null;
	}

	@Override
	public void compile() throws Exception {
		try {
			compiler.compile();
		} catch(Exception e) {
			logger.error("Couldn't compile rhs", e);
			throw e;
		}
	}

	public SourceInfo addAction(String src, Grammar g, CompiledGrammar.Rule rule) throws Exception {


		String className = className(rule.production.lhs.id, rule.id);
		String fqClassName = actionsDirName.replace(File.separatorChar, '.').replace('/', '.').replace('\\', '.') + "." + className;
		SourceInfo sourceInfo = new SourceInfo(g.name, rule.production.lhs.id);
		sourceInfo.setClassName(fqClassName);

		StringBuilder source = new StringBuilder();
		source.append("package " + actionsDirName + ";\n");
		source.append(CompiledGrammar.DEFAULT_IMPORTS + "\n" + (g.imports == null? "" : g.imports) + "\n");
		source.append("class " + className + " extends " + FieldedRHSAction.class.getName() + " {\n");

		for(String block : g.codeBlocks) {
			source.append(sourceInfo.addBlock(source.toString(), block + "\n"));
		}

		for(String binding : g.bindings()) {
			source.append("\t").append("protected ").append(SymbolSpan.class.getName()).append(" ").append(binding).append(";\n");
		}

		source.append("public Object exec() {\n");
		source.append("try {\n" );

		for(Production.BindingInfo info : rule.production.bindings) {
			source.append("this.").append(info.name).append(" = ").append("("+ SymbolSpan.class.getName()+ ")"+"rhs.get(").append(info.path.get(0)).append(");\n");
		}

		source.append(sourceInfo.addBlock(source.toString(), src) + "\n");
		source.append("} finally {\n");

		for(String name : g.bindings()) {
			source.append("this.").append(name).append(" = ").append("(" + SymbolSpan.class.getName()+ ")"+"null;\n");
		}

		source.append("}\n");
		source.append("}\n");
		source.append("\n}");

		compiler.add(new Compiler.Unit(GroovyCompiler.GROOVY, fqClassName, source.toString()));

		return sourceInfo;
	}

	public SourceInfo addAttrAction(String src, Grammar g, CompiledGrammar.Rule rule) throws Exception {


		String className = className(rule.production.lhs.id, rule.id);
		String fqClassName = actionsDirName.replace(File.separatorChar, '.').replace('/', '.').replace('\\', '.') + "." + className;
		SourceInfo sourceInfo = new SourceInfo(g.name, rule.production.lhs.id);
		sourceInfo.setClassName(fqClassName);

		StringBuilder source = new StringBuilder();
		source.append("package " + actionsDirName + ";\n");
		source.append(CompiledGrammar.DEFAULT_IMPORTS + "\n" + (g.imports == null? "" : g.imports) + "\n");
		source.append("class " + className + " extends " + FieldedRHSAction.class.getName() + " {\n");

		for(String block : g.codeBlocks) {
			source.append(sourceInfo.addBlock(source.toString(), block + "\n"));
		}

		for(String binding : g.bindings()) {
			source.append("\t").append("protected ").append(SymbolSpan.class.getName()).append(" ").append(binding).append(";\n");
		}
		source.append("@" + AttrAssignment.class.getName() + "\n");
		source.append("public Object _exec() {\n");
		source.append(sourceInfo.addBlock(source.toString(), src) + "\n");
		source.append("}\n");

		source.append("public Object exec() {\n");
		source.append("try {\n" );

		for(Production.BindingInfo info : rule.production.bindings) {
			source.append("this.").append(info.name).append(" = ").append("("+ SymbolSpan.class.getName()+ ")" + "rhs.get(").append(info.path.get(0)).append(");\n");
		}

		source.append("return _exec()\n");



		source.append("} finally {\n");

		for(String name : g.bindings()) {
			source.append("this.").append(name).append(" = ").append("(" + SymbolSpan.class.getName()+ ")" + "null;\n");
		}

		source.append("}\n");
		source.append("}\n");
		source.append("\n}");

		compiler.add(new Compiler.Unit(GroovyCompiler.GROOVY, fqClassName, source.toString()));

		return sourceInfo;
	}


	public String className(String lhs, int ruleId) {
		return "GroovyRHS_" + lhs + "_" + ruleId;
	}

}
