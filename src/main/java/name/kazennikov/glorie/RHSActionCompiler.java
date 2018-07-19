package name.kazennikov.glorie;

/**
 * Compiler interface for RHS actions.
 *
 * The compiler is applied in two steps.
 * First, RHS actions are added to the compiler. The compiler returns an RHS generator that generates
 * actual RHS code after the compilation.
 *
 * After all RHS actions are added, you an invocation of compile() compile all RHS blocks
 * and {@link RHSActionGenerator#generate()} could be called
 */
public interface RHSActionCompiler {
	/**
	 * Add an RHS action to the compiler
     *
	 * @param g current grammar
	 * @param rule source rule of the RHS action
	 * @param action RHS action
     *
	 * @return wrapper of the compiled RHS action (valid only after the compile() method is invoked)
	 */
	public RHSActionGenerator add(Grammar g, CompiledGrammar.Rule rule, RHSAction action);

	/**
	 * Compile all contained RHS actions
     *
	 * @throws Exception
	 */
	public void compile() throws Exception;
}
