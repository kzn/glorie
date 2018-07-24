package name.kazennikov.glorie;


/**
 * Generate an reduce action using the Java class action name.
 *
 * The construction caller is responsible to check if the passed class name is a valid
 * class name and the class is an implementation of {@link CompiledReduceAction} interface.
 */
public class NamedClassReduceGenerator implements ReduceActionGenerator {
	Compiler compiler;
	SourceInfo sourceInfo;

	public NamedClassReduceGenerator(Compiler compiler, SourceInfo sourceInfo) {
		this.compiler = compiler;
		this.sourceInfo = sourceInfo;
	}

	@Override
	public CompiledReduceAction generate() throws Exception {
		Class c = compiler.getClass(sourceInfo.getClassName());
		CompiledReduceAction action = (CompiledReduceAction) c.newInstance();
		return new CompiledReduceAction.Friendly(action, sourceInfo);
	}
}
