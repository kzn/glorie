package name.kazennikov.glorie;

import gate.Annotation;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created on 14.12.15.
 *
 * @author Anton Kazennikov
 */
public class SymbolNodePostProcCompiler {

	public class Generator {
		SourceInfo sourceInfo;

		public Generator(SourceInfo sourceInfo) {
			this.sourceInfo = sourceInfo;
		}

		public SymbolNodePostProcessor generate() throws Exception {
			Class<?> c = compiler.getClass(sourceInfo.getClassName());
			SymbolNodePostProcessor pp = (SymbolNodePostProcessor) c.newInstance();
			return pp;
		}
	}

	Compiler compiler;

	/**
	 * Cardinality of the action class set. Used for ensuring class name
	 * uniqueness.
	 */
	private static AtomicInteger actionClassNumber = new AtomicInteger();


	/**
	 * Package name for action classes. It's called a "dir name" because
	 * we used to dump the action classes to disk and compile them there.
	 */
	static private String actionsDirName = "glractionclasses";



	public SymbolNodePostProcCompiler(Compiler compiler) {
		this.compiler = compiler;
	}

	public Generator add(Grammar g, CompiledGrammar.Rule rule, SymbolNodePostProcessor.Source src) throws Exception {


		String className = className(rule.production.lhs.id, rule.id);
		String fqClassName = actionsDirName.replace(File.separatorChar, '.').replace('/', '.').replace('\\', '.') + "." + className;
		SourceInfo sourceInfo = new SourceInfo(g.name, rule.production.lhs.id);
		sourceInfo.setClassName(fqClassName);

		StringBuilder source = new StringBuilder();
		source.append("package " + actionsDirName + ";\n");
		source.append(CompiledGrammar.DEFAULT_IMPORTS + "\n" + (g.imports == null? "" : g.imports) + "\n");
		source.append("class " + className + " extends " + FieldedSymbolNodePostProcessor.class.getName() + " {\n");


		for(String block : g.codeBlocks) {
			source.append(sourceInfo.addBlock(source.toString(), block + "\n"));
		}

		for(String binding : g.bindings()) {
			source.append("\t").append("protected ").append(SymbolSpan.class.getName()).append(" ").append(binding).append(";\n");
			source.append("\t").append("protected ").append(gate.Annotation.class.getName()).append(" ").append(binding + "Ann").append(";\n");
		}

		source.append("public void apply() {\n");
		source.append("try {\n" );

		for(Production.BindingInfo info : rule.production.bindings) {
			source.append("this.").append(info.name).append(" = ").append("(" + SymbolSpan.class.getName() + ") " + "bindings.get(").append(info.path.get(0)).append(");\n");
			source.append("this.").append(info.name + "Ann").append(" = ").append("(" + Annotation.class.getName() + ") " + "bindingAnns.get(").append(info.path.get(0)).append(");\n");
		}

		source.append(sourceInfo.addBlock(source.toString(), src.source) + "\n");
		source.append("} finally {\n");

		for(String binding : g.bindings()) {
			source.append("this.").append(binding).append(" = ").append("(" + SymbolSpan.class.getName() + ") " + "null;\n");
			source.append("this.").append(binding + "Ann").append(" = ").append("(" + Annotation.class.getName() + ") " + "null;\n");
		}

		source.append("}\n");
		source.append("}\n");
		source.append("\n}");

		compiler.add(new Compiler.Unit(GroovyCompiler.GROOVY, fqClassName, source.toString()));

		return new Generator(sourceInfo);

	}

	public void compile() throws Exception {
		compiler.compile();
	}

	public String className(String lhs, int ruleId) {
		return "GroovyPostProc_" + lhs + "_" + ruleId;
	}


}
