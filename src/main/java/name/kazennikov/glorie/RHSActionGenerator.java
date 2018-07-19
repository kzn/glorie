package name.kazennikov.glorie;

/**
 * Container for the compiled RHS action code
 */
public interface RHSActionGenerator {
	public static final Simple SIMPLE = new Simple();


	/**
	 * Returns compiled RHS action bounded with this generator.
     *
     * If the generator is issued from the {@link RHSActionCompiler}
     * then with method should be invoked *only* after the {@link RHSActionCompiler#compile()}
     * is called
	 */
	public CompiledRHSAction generate() throws Exception;

    /**
     * Simple generator that wraps a default RHS action
     */
	class Simple implements RHSActionGenerator {

		@Override
		public CompiledRHSAction generate() {
			return CompiledRHSAction.SIMPLE;
		}
	}
}
