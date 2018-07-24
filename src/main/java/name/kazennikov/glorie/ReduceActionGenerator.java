package name.kazennikov.glorie;

/**
 * Container for the compiled reduce action code
 */
public interface ReduceActionGenerator {
	public static final Simple SIMPLE = new Simple();


	/**
	 * Returns compiled action bounded with this generator.
     *
     * If the generator is issued from the {@link ReduceActionCompiler}
     * then with method should be invoked *only* after the {@link ReduceActionCompiler#compile()}
     * is called
	 */
	public CompiledReduceAction generate() throws Exception;

    /**
     * Simple generator that wraps a default reduce action
     */
	class Simple implements ReduceActionGenerator {

		@Override
		public CompiledReduceAction generate() {
			return CompiledReduceAction.SIMPLE;
		}
	}
}
