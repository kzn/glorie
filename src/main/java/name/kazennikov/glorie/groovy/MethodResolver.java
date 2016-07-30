package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.List;

/**
 * Interface for resolving method calls during processing of Groovy AST.
 */
public interface MethodResolver {
	/**
	 * Resolve a method call in form of foo.bar(baz, quux) using
	 * the class of foo (the receiver) and classes of arguments (baz and quux)
	 *
	 * Foo in the function is called a receiver class
	 *
	 * @param receiver class node of receiver
	 * @param name method name
	 * @param args argument types
	 * @return list of compatible methods
	 */
    public List<MethodNode> resolve(ClassNode receiver, String name, ClassNode... args);
}
