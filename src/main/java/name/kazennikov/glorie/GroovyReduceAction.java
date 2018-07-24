package name.kazennikov.glorie;

/**
 * Class for Groovy code reduce action
 */
public class GroovyReduceAction implements ReduceAction {
	String block;

	/**
	 * Create action from source code
	 * @param block action source code
	 */
	public GroovyReduceAction(String block) {
		this.block = block;
	}
}
