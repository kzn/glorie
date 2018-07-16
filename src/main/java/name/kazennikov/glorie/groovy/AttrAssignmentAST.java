package name.kazennikov.glorie.groovy;

import name.kazennikov.glorie.SymbolSpan;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.sc.ListOfExpressionsExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms all assignments in method execute() from
 *
 * a = b
 *
 * to
 *
 * target['a'] = b
 *
 * if 'a' wasn't declared. For example:
 * String a = "foo"
 * a = "bar"
 * will be rewritten to:
 * String a = "foo"
 * target['a'] = "bar"
 * This is a special mode for attribute assignment.
 *
 * There is also a special type
 * $foo = bar
 * that transforms to:
 * target.foo = bar
 *
 * This is needed to distinguish feature assignment from SymbolNode one
 *
 * !NB
 *
 * Rewriting is done for LHS of the assignment only:
 * String a = "foo"
 * b = a
 * will be rewritten as:
 * String a = "foo"
 * target['b'] = a // eventually this will result in target['b'] = "foo"
 *
 *
 * @author Anton Kazennikov
 */
@GroovyASTTransformation
public class AttrAssignmentAST implements ASTTransformation {

	public static class AttrVisitor extends ClassCodeExpressionTransformer {
		SourceUnit sourceUnit;
		List<Expression> stack = new ArrayList<>();

        protected int varId = 0;
        protected boolean allowNullValues = false;

		public AttrVisitor(SourceUnit sourceUnit, boolean allowNullValues) {
			this.sourceUnit = sourceUnit;
            this.allowNullValues = allowNullValues;
		}

		@Override
		protected SourceUnit getSourceUnit() {
			return sourceUnit;
		}

		@Override
		public Expression transform(Expression exp) {
			if(exp == null)
				return null;

			stack.add(exp);
			Expression expOld = exp;
			expOld.getType();
			exp = super.transform(exp);
			stack.remove(stack.size() - 1); // as transform may change the exp
			stack.add(exp);

			if(exp instanceof BinaryExpression) {
				exp = rewrite((BinaryExpression)exp);
			}

			stack.remove(stack.size() - 1);
			return exp;
		}

		public Expression rewrite(BinaryExpression expr) {
			if(expr.getOperation().getType() == Types.ASSIGN) {

                // rewrite left part

                if(expr.getLeftExpression() instanceof VariableExpression) {
					Variable v = ((VariableExpression) expr.getLeftExpression()).getAccessedVariable();
					if(!(v instanceof VariableExpression)) {
                        ListOfExpressionsExpression exprList = new ListOfExpressionsExpression();

                        Expression valueExpr = expr.getRightExpression();

                        String varName = "$$var" + varId++;

                        // assignment
                        exprList.addExpression(new DeclarationExpression(
                                new VariableExpression(varName),
                                new Token(Types.ASSIGN, "=", expr.getLineNumber(), expr.getColumnNumber()),
                                valueExpr
                        ));

						String attrName = v.getName();

						Expression lhs;

                        // $var = foo -> target.var = foo
						if(attrName.startsWith("$")) {
							lhs = new AttributeExpression(new VariableExpression("target", new ClassNode(SymbolSpan.class)), new ConstantExpression(attrName.substring(1)));
						} else { // else, var = foo -> target.features[var] = foo
							lhs = new BinaryExpression(
									new AttributeExpression(new VariableExpression("target", new ClassNode(SymbolSpan.class)), new ConstantExpression("features")), new Token(Types.LEFT_SQUARE_BRACKET,
									"[", expr.getLineNumber(), 0),
									new ConstantExpression(attrName));
						}

                        exprList.addExpression(new TernaryExpression(
                                new BooleanExpression(
                                        new BinaryExpression(
                                                new VariableExpression(varName),
                                                new Token(Types.COMPARE_EQUAL, "==", expr.getLineNumber(), expr.getColumnNumber()),
                                                new ConstantExpression(null)
                                                )

                                        ),
                                        new BinaryExpression(new VariableExpression(varName), expr.getOperation(), new VariableExpression(varName)),
                                        new BinaryExpression(lhs, expr.getOperation(), new VariableExpression(varName))


                        ));


                        return allowNullValues? new BinaryExpression(lhs, expr.getOperation(), expr.getRightExpression()) : exprList;
					}
				}
			}


			return expr;
		}


	}

	@Override
	public void visit(ASTNode[] nodes, SourceUnit source) {
		AnnotationNode annotationInformation = (AnnotationNode) nodes[0];
		AnnotatedNode node = (AnnotatedNode) nodes[1];
		if (node instanceof MethodNode) {
			MethodNode methodNode = (MethodNode) node;
			AttrVisitor visitor = new AttrVisitor(source, false);
			visitor.visitMethod(methodNode);
		} else {
			source.addError(new SyntaxException("Attribute block AST transformation unimplemented for: ",
					node.getLineNumber(), node.getColumnNumber(), node.getLastLineNumber(), node.getLastColumnNumber()));
		}

	}


}
