package name.kazennikov.glorie;

/**
 * A container of generated RHS action code
 */
public interface RHSActionGenerator {
	public static final Simple SIMPLE = new Simple();


	/**
	 * Generated an executable RHS action on contained data
	 * @return
	 * @throws Exception
	 */
	public CompiledRHSAction generate() throws Exception;

	class Simple implements RHSActionGenerator {

		@Override
		public CompiledRHSAction generate() {
			return CompiledRHSAction.SIMPLE;
		}
	}
}
