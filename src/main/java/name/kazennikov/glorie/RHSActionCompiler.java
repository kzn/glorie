package name.kazennikov.glorie;

/**
 * Compiler interface for RHS actions.
 *
 * Proceeds in two steps.
 * First, you need to add RHS action to the compiler. It returns an RHS generator that can generate
 * actual RHS code after the compilation.
 *
 * After all rhs are added, you can compile all RHS blocks
 */
public interface RHSActionCompiler {
	/**
	 * Add an RHS to the compiler
	 * @param g grammar
	 * @param rule RHS rule
	 * @param action RHS action itself
	 * @return
	 */
	public RHSActionGenerator add(Grammar g, CompiledGrammar.Rule rule, RHSAction action);

	/**
	 * Compile all contained RHS actions
	 * @throws Exception
	 */
	public void compile() throws Exception;
}
