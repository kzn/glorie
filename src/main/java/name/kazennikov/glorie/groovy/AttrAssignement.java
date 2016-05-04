package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

/**
 * Attribute block assignment for GLR
 *
 * @author Anton Kazennikov
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
@GroovyASTTransformationClass("name.kazennikov.glr.groovy.AttrAssignementAST")
public @interface AttrAssignement {
}
