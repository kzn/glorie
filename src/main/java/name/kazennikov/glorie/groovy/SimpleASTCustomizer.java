package name.kazennikov.glorie.groovy;

import groovy.transform.CompilationUnitAware;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * AST Customizer that applies transformation to every passed source unit
 */
public class SimpleASTCustomizer extends CompilationCustomizer implements CompilationUnitAware {

    private final ASTTransformation transformation;
    protected CompilationUnit compilationUnit;

    /**
     * Creates an AST transformation customizer using the specified transformation.
     */
    public SimpleASTCustomizer(final ASTTransformation transformation) {
        super(findPhase(transformation));
        this.transformation = transformation;
    }

    @Override
    public void setCompilationUnit(CompilationUnit unit) {
        compilationUnit = unit;
    }

    private static CompilePhase findPhase(ASTTransformation transformation) {
        if(transformation == null)
            throw new IllegalArgumentException("Provided transformation must not be null");

        final Class<?> clazz = transformation.getClass();
        final GroovyASTTransformation annotation = clazz.getAnnotation(GroovyASTTransformation.class);

        if(annotation == null)
            throw new IllegalArgumentException("Provided ast transformation is not annotated with " + GroovyASTTransformation.class.getName());

        return annotation.phase();
    }



    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if(transformation instanceof CompilationUnitAware) {
            ((CompilationUnitAware) transformation).setCompilationUnit(compilationUnit);
        }

        transformation.visit(null, source);

    }
}

