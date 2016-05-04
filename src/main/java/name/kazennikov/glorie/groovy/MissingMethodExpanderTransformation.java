package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * Created by kzn on 6/21/15.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class MissingMethodExpanderTransformation implements ASTTransformation {
    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {

    }
}
