package name.kazennikov.glorie;

/**
 * Class for Groovy code RHS action
 */
public class GroovyRHSAction implements RHSAction {
	String block;

	/**
	 * Create action from source code
	 * @param block action source code
	 */
	public GroovyRHSAction(String block) {
		this.block = block;
	}
}
