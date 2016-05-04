package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.List;

/**
 * Resolve method in Groovy AST
 */
public interface MethodResolver {
	/**
	 * Resolve a method call in form of foo.bar(baz, quux) using
	 * the class of foo and classes of arguments (baz and quux)
	 *
	 * Foo in the function is called a receiver class
	 *
	 * @param receiver class node of receiver
	 * @param name method name
	 * @param args argument types
	 * @return
	 */
    public List<MethodNode> resolve(ClassNode receiver, String name, ClassNode... args);
}
