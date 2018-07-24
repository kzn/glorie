package name.kazennikov.glorie;

/**
 * Compiler interface for reduce actions.
 *
 * The compiler is applied in two steps.
 * First, reduce actions are added to the compiler. The compiler returns a generator that produces
 * actual code after the compilation.
 *
 * After all reduce actions are added to the compiler, the invocation of {@link #compile()} compile
 * all actions and {@link ReduceActionGenerator#generate()} could be called
 */
public interface ReduceActionCompiler {
	/**
	 * Add an reduce action to the compiler
     *
	 * @param g current grammar
	 * @param rule source rule of the action
	 * @param action action
     *
	 * @return wrapper of the compiled action (valid only after the {@link #compile()} method is invoked)
	 */
	public ReduceActionGenerator add(Grammar g, CompiledGrammar.Rule rule, ReduceAction action);

	/**
	 * Compile all contained actions
     *
	 * @throws Exception
	 */
	public void compile() throws Exception;
}
