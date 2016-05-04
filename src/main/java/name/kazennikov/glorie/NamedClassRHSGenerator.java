package name.kazennikov.glorie;


/**
 * Generate an RHS action using the Java class action name.
 *
 * The construction caller is responsible to check if the passed class name is a valid
 * class name and the class is an implementation of CompiledRHSAction interface.
 */
public class NamedClassRHSGenerator implements RHSActionGenerator {
	Compiler compiler;
	SourceInfo sourceInfo;

	public NamedClassRHSGenerator(Compiler compiler, SourceInfo sourceInfo) {
		this.compiler = compiler;
		this.sourceInfo = sourceInfo;
	}

	@Override
	public CompiledRHSAction generate() throws Exception {
		Class c = compiler.getClass(sourceInfo.getClassName());
		CompiledRHSAction action = (CompiledRHSAction) c.newInstance();
		return new CompiledRHSAction.Friendly(action, sourceInfo);
	}
}
