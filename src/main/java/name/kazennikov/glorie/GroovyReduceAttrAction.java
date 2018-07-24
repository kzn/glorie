package name.kazennikov.glorie;

/**
 * Attribute action block. Assumes that all undeclared variables are actually features of an symbol span.
 *
 * Fo example a block of
 * {
 *     foo = "bar"
 *     bar = foo
 * }
 *
 * doesn't throw any exceptions and sets symbol span features 'foo' and 'bar' to a string "bar"
 *
 * @author Anton Kazennikov
 */
public class GroovyReduceAttrAction extends GroovyReduceAction {
	public GroovyReduceAttrAction(String block) {
		super(block);
	}
}
