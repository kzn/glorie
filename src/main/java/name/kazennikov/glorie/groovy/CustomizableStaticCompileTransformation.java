package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.classgen.asm.WriterControllerFactory;
import org.codehaus.groovy.classgen.asm.sc.StaticTypesWriterControllerFactoryImpl;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.StaticTypesTransformation;
import org.codehaus.groovy.transform.sc.StaticCompilationMetadataKeys;
import org.codehaus.groovy.transform.sc.StaticCompilationVisitor;
import org.codehaus.groovy.transform.sc.transformers.StaticCompilationTransformer;
import org.codehaus.groovy.transform.stc.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.codehaus.groovy.ast.ClassHelper.makeWithoutCaching;

/**
 * Groovy AST transformation that implements categories and method overrides as in DefaultGroovyMethods class does.
 *
 * The class extends CompileStatic AST transformation and override findMethod() behavior. If the method resolver
 * is provided then it is used before any default method resolution techniques. This allows to override already
 * defined methods.
 *
 * The resolved can be used to specify new object instance methods or override existing ones.
 *
 * Also, the resolver allows to specify own operator substitution functions. This more or less respects groovy
 * conventions:
 * a + b => a.plus(b)
 * a - b => a.minus(b)
 * a * b => a.multiply(b)
 * a / b =>  a.div(b)
 * a[b] =>  a.getAt(b)
 * a[b] = foo => a.putAt(b, foo)
 * a in b => b.isCase(a)
 * a << b => a.leftShift(b)
 * a >> b => a.rightShift(b)
 * a >>> b => a.rightShiftUnsigned(b)
 * a % b => a.mod(b)
 * a ** b => a.power(b)
 * a | b => a.or(b)
 * a & b => a.and(b)
 * a ^ b => a.xor(b)
 * a++ => a.next()
 * a-- => a.previous()
 * +a => a.positive()
 * -a => a.negative()
 * ~a => a.bitwiseNegate()
 * a as b => a.asType(b)
 * a(args) => a.call(args)

 New:
 * a[b]++ => a.indexedNext(b)
 * a[b]-- => a.indexedPrevious(b)
 */
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class CustomizableStaticCompileTransformation extends StaticTypesTransformation {

    protected static final ClassNode Deprecated_TYPE = makeWithoutCaching(Deprecated.class);

    public static final String INDEXED_NEXT = "indexedNext";
    public static final String INDEXED_PREVIOUS = "indexedPrevious";

    public static final String MISSING_PROPERTY = "missingProperty";
    public static final String MISSING_ATTRIBUTE = "missingAttribute";

    public static final String POSITIVE = "positive";
    public static final String NEGATIVE = "negative";
    public static final String BITWISE_NEGATE = "bitwiseNegate";

    public static final String PUT_AT = "putAt";
    public static final String GET_AT = "getAt";

    MethodResolver methodResolver;


    List<TypeCheckingExtension> extensions = new ArrayList<>();


    private final StaticTypesWriterControllerFactoryImpl factory = new StaticTypesWriterControllerFactoryImpl();


    /**
     * Custom handling of missing properties
     */
	public class PropertyChecker extends TypeCheckingExtension {



		public PropertyChecker(StaticTypeCheckingVisitor typeCheckingVisitor) {
			super(typeCheckingVisitor);
		}


		@Override
		public boolean handleUnresolvedProperty(PropertyExpression exp) {
			ClassNode target = getType(exp.getObjectExpression());
			Expression prop = exp.getProperty();


			ClassNode[] argTypes = new ClassNode[] {getType(prop)};

			List<MethodNode> methods = methodResolver.resolve(target, MISSING_PROPERTY, argTypes);
			methods = StaticTypeCheckingSupport.chooseBestMethod(target, methods, argTypes);


			return !methods.isEmpty();
		}
	}

    public CustomizableStaticCompileTransformation(MethodResolver methodResolver) {
        this.methodResolver = methodResolver;
    }

    public void visitClass(ClassNode classNode, final SourceUnit source) {
        ExtendedStaticCompilationVisitor visitor = newVisitor(source, classNode);
		visitor.addTypeCheckingExtension(new PropertyChecker(visitor));
        visitor.setCompilationUnit(compilationUnit);

        classNode.putNodeMetaData(WriterControllerFactory.class, factory);
        classNode.putNodeMetaData(StaticCompilationMetadataKeys.STATIC_COMPILE_NODE, !visitor.isSkipMode(classNode));
        visitor.initialize();
        visitor.visitClass(classNode);

        OperatorExpander operatorExpander = new OperatorExpander(visitor);
        operatorExpander.visitClass(classNode);


        visitor.performSecondPass();


        StaticCompilationTransformer transformer = new StaticCompilationTransformer(source, visitor);
        transformer.visitClass(classNode);

    }

    public void visitMethod(MethodNode methodNode, final SourceUnit source) {
        ClassNode declaringClass = methodNode.getDeclaringClass();
        ExtendedStaticCompilationVisitor visitor = newVisitor(source, declaringClass);
        visitor.setCompilationUnit(compilationUnit);

        methodNode.putNodeMetaData(StaticCompilationMetadataKeys.STATIC_COMPILE_NODE, !visitor.isSkipMode(methodNode));
        if (declaringClass.getNodeMetaData(WriterControllerFactory.class) == null) {
            declaringClass.putNodeMetaData(WriterControllerFactory.class, factory);
        }
        visitor.setMethodsToBeVisited(Collections.singleton(methodNode));
        visitor.initialize();
        visitor.visitMethod(methodNode);

        OperatorExpander operatorExpander = new OperatorExpander(visitor);
        operatorExpander.visitMethod(methodNode);


        visitor.performSecondPass();



        StaticCompilationTransformer transformer = new StaticCompilationTransformer(source, visitor);
        transformer.visitMethod(methodNode);

    }

    @Override
    public void visit(final ASTNode[] nodes, final SourceUnit source) {
        for(ClassNode classNode : source.getAST().getClasses()) {
            visitClass(classNode, source);
        }

        for(MethodNode methodNode : source.getAST().getMethods()) {
            visitMethod(methodNode, source);
        }
    }

    @Override
    protected ExtendedStaticCompilationVisitor newVisitor(final SourceUnit unit, final ClassNode node) {
        ExtendedStaticCompilationVisitor visitor = new ExtendedStaticCompilationVisitor(unit, node);

        for(TypeCheckingExtension extension : extensions) {
            visitor.addTypeCheckingExtension(extension);
        }

        return visitor;
    }

    /**
     * MethodResolver-based Groovy static compilation visitor
     */
    public class ExtendedStaticCompilationVisitor extends StaticCompilationVisitor {

        public ExtendedStaticCompilationVisitor(SourceUnit unit, ClassNode node) {
            super(unit, node);
        }

        @Override
        protected List<MethodNode> findMethod(ClassNode receiver, String name, ClassNode... args) {

            if(methodResolver != null) {
                List<MethodNode> methods = methodResolver.resolve(receiver, name, args);

                if(!methods.isEmpty()) {
                    methods = StaticTypeCheckingSupport.chooseBestMethod(receiver, methods, args);
                }

                if(!methods.isEmpty())
                    return methods;
            }

            return super.findMethod(receiver, name, args);
        }
    }

    /**
     * Expand operator to method calls before static analysis is done.
     *
     * Groovy transforms operator to functions call by default. But in some
     * cases it signals an error even then the method call could be resolved with provided
     * method resolver.
     *
     * So some operators need to be expanded to method calls in explicit fashion.
     *
     */
    class OperatorExpander extends ClassCodeExpressionTransformer {

        ExtendedStaticCompilationVisitor visitor;
        List<Expression> stack = new ArrayList<>();

        OperatorExpander(ExtendedStaticCompilationVisitor visitor) {
            this.visitor = visitor;
        }

        @Override
        protected SourceUnit getSourceUnit() {
            return null;
        }

        @Override
        public Expression transform(Expression exp) {
            if(exp == null)
                return null;

            stack.add(exp);
            Expression origExpr = exp;
            origExpr.getType();
            exp = super.transform(exp);
            stack.remove(stack.size() - 1);
            stack.add(exp);

            if(exp instanceof BinaryExpression) {
                exp = rewrite((BinaryExpression)exp, origExpr instanceof BinaryExpression? (BinaryExpression) origExpr : null);
            } else if(exp instanceof UnaryPlusExpression) {
                exp = rewrite((UnaryPlusExpression) exp);
            } else if(exp instanceof UnaryMinusExpression) {
                exp = rewrite((UnaryMinusExpression) exp);
            } else if(exp instanceof BitwiseNegationExpression) {
                exp = rewrite((BitwiseNegationExpression) exp);
            } else if(exp instanceof PostfixExpression) {
                exp = rewrite((PostfixExpression) exp);
            } else if(exp instanceof PrefixExpression) {
                exp = rewrite((PrefixExpression) exp);
            } else if(exp instanceof AttributeExpression) {
				exp = rewrite((AttributeExpression) exp, origExpr instanceof AttributeExpression? (AttributeExpression) origExpr : null);
			} else if(exp instanceof PropertyExpression) {
				exp = rewrite((PropertyExpression) exp, origExpr instanceof PropertyExpression? (PropertyExpression) origExpr : null);
			}

            stack.remove(stack.size() - 1);
            return exp;
        }

        boolean inLeftAssign(Expression target) {
            if(stack.size() > 1) {
                Expression expr = stack.get(stack.size() - 2);
                if(!(expr instanceof BinaryExpression))
                    return false;
                BinaryExpression bexpr = (BinaryExpression) expr;
                return bexpr.getOperation().getType() == Types.ASSIGN && bexpr.getLeftExpression() == target;
            }

            return false;
        }

        boolean inAffix() {
            if(stack.size() > 1) {
                Expression expr = stack.get(stack.size() - 2);

                return expr instanceof PrefixExpression || expr instanceof PostfixExpression;
            }
            return false;
        }

        private Expression rewrite(PostfixExpression exp) {
            if(exp.getExpression() instanceof BinaryExpression) {
                BinaryExpression bexpr = (BinaryExpression) exp.getExpression();
                if(bexpr.getOperation().getType() == Types.LEFT_SQUARE_BRACKET) {

                    String type = null;
                    switch(exp.getOperation().getType()) {
                        case Types.PLUS_PLUS:
                            type = INDEXED_NEXT;
                            break;
                        case Types.MINUS_MINUS:
                            type = INDEXED_PREVIOUS;
                            break;
                    }

                    if(type == null) {
                        return exp.transformExpression(this);
                    }

                    ClassNode targetClass = getType(bexpr.getLeftExpression());
                    ClassNode indexTypeClass = getType(bexpr.getRightExpression());
                    ClassNode[] argTypes = new ClassNode[] {indexTypeClass, ClassHelper.boolean_TYPE};

                    List<MethodNode> methods = methodResolver.resolve(targetClass, type, argTypes);
                    methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

                    if(!methods.isEmpty()) {
                        MethodCallExpression mce = new MethodCallExpression(bexpr.getLeftExpression(), type,
                                new ArgumentListExpression(Arrays.asList(bexpr.getRightExpression(), new ConstantExpression(Boolean.TRUE, true))));
                        visitor.visitMethodCallExpression(mce);
                        return mce;
                    }

                }
            }


            return exp.transformExpression(this);
        }


        private Expression rewrite(PrefixExpression exp) {
            if(exp.getExpression() instanceof BinaryExpression) {
                BinaryExpression bexpr = (BinaryExpression) exp.getExpression();


                if(bexpr.getOperation().getType() == Types.LEFT_SQUARE_BRACKET) {

                    String type = null;
                    switch(exp.getOperation().getType()) {
                        case Types.PLUS_PLUS:
                            type = INDEXED_NEXT;
                            break;
                        case Types.MINUS_MINUS:
                            type = INDEXED_PREVIOUS;
                            break;
                    }

                    if(type == null) {
                        return exp.transformExpression(this);
                    }

                    ClassNode targetClass = getType(bexpr.getLeftExpression());
                    ClassNode indexTypeClass = getType(bexpr.getRightExpression());
                    ClassNode[] argTypes = new ClassNode[] {indexTypeClass, ClassHelper.boolean_TYPE};

                    List<MethodNode> methods = methodResolver.resolve(targetClass, type, argTypes);
                    methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

                    if(!methods.isEmpty()) {
                        MethodCallExpression mce = new MethodCallExpression(bexpr.getLeftExpression(), type,
                                new ArgumentListExpression(Arrays.asList(bexpr.getRightExpression(), new ConstantExpression(Boolean.FALSE, true))));
                        visitor.visitMethodCallExpression(mce);
                        return mce;
                    }

                }
            }


            return exp.transformExpression(this);
        }


        private Expression rewrite(UnaryPlusExpression exp) {
            ClassNode targetClass = getType(exp.getExpression());
            ClassNode[] argTypes = new ClassNode[0];
            List<MethodNode> methods = methodResolver.resolve(targetClass, POSITIVE, argTypes);
            methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

            if(!methods.isEmpty()) {
                MethodCallExpression mce = new MethodCallExpression(exp.getExpression(), POSITIVE, new ArgumentListExpression());
                visitor.visitMethodCallExpression(mce);
                return mce;
            }

            return exp.transformExpression(this);
        }


		private Expression rewrite(PropertyExpression exp, PropertyExpression oldExpr) {

			// skip assignment
			if(inLeftAssign(oldExpr) || inAffix())
				return exp.transformExpression(this);


			ClassNode target = getType(exp.getObjectExpression());
			Expression prop = exp.getProperty();


			ClassNode[] argTypes = new ClassNode[] {getType(prop)};

			List<MethodNode> methods = methodResolver.resolve(target, MISSING_PROPERTY, argTypes);
			methods = StaticTypeCheckingSupport.chooseBestMethod(target, methods, argTypes);

			if(!methods.isEmpty()) {
				MethodCallExpression mce = new MethodCallExpression(exp.getObjectExpression(), MISSING_PROPERTY, new ArgumentListExpression(prop));
				visitor.visitMethodCallExpression(mce);
				return mce;
			}

			return exp.transformExpression(this);
		}

		private Expression rewrite(AttributeExpression exp, AttributeExpression oldExpr) {

			// skip assignment
			if(inLeftAssign(oldExpr) || inAffix())
				return exp.transformExpression(this);


			ClassNode target = getType(exp.getObjectExpression());
			Expression prop = exp.getProperty();


			ClassNode[] argTypes = new ClassNode[] {getType(prop)};

			List<MethodNode> methods = methodResolver.resolve(target, MISSING_ATTRIBUTE, argTypes);
			methods = StaticTypeCheckingSupport.chooseBestMethod(target, methods, argTypes);

			if(!methods.isEmpty()) {
				MethodCallExpression mce = new MethodCallExpression(exp.getObjectExpression(), MISSING_ATTRIBUTE, new ArgumentListExpression(prop));
				visitor.visitMethodCallExpression(mce);
				return mce;
			}

			return exp.transformExpression(this);
		}


        private Expression rewrite(UnaryMinusExpression exp) {
            ClassNode targetClass = getType(exp.getExpression());
            ClassNode[] argTypes = new ClassNode[0];
            List<MethodNode> methods = methodResolver.resolve(targetClass, NEGATIVE, argTypes);
            methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

            if(!methods.isEmpty()) {
                MethodCallExpression mce = new MethodCallExpression(exp.getExpression(), NEGATIVE, new ArgumentListExpression());
                visitor.visitMethodCallExpression(mce);
                return mce;
            }

            return exp.transformExpression(this);
        }

        private Expression rewrite(BitwiseNegationExpression exp) {
            ClassNode targetClass = getType(exp.getExpression());
            ClassNode[] argTypes = new ClassNode[0];
            List<MethodNode> methods = methodResolver.resolve(targetClass, BITWISE_NEGATE, argTypes);
            methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

            if(!methods.isEmpty()) {
                MethodCallExpression mce = new MethodCallExpression(exp.getExpression(), BITWISE_NEGATE, new ArgumentListExpression());
                visitor.visitMethodCallExpression(mce);
                return mce;
            }

            return exp.transformExpression(this);
        }

        public Expression rewrite(BinaryExpression expr, BinaryExpression oldExpr) {
            int type = expr.getOperation().getType();

            if(type == Types.ASSIGN) {


                if(expr.getLeftExpression() instanceof BinaryExpression) {
                    BinaryExpression left = (BinaryExpression) expr.getLeftExpression();
                    if(left.getOperation().getType() == Types.LEFT_SQUARE_BRACKET) {
                        // foo[x] = y

                        Expression target = left.getLeftExpression();
                        Expression index = left.getRightExpression();
                        Expression value = expr.getRightExpression();

                        ClassNode targetClass = getType(target);
                        ClassNode[] argTypes = new ClassNode[] {getType(index), getType(value)};
                        List<MethodNode> methods = methodResolver.resolve(getType(target), PUT_AT, argTypes);
                        methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

                        if(!methods.isEmpty()) {
                            MethodCallExpression mce = new MethodCallExpression(target, PUT_AT, new ArgumentListExpression(index, value));
                            visitor.visitMethodCallExpression(mce);
                            return mce;
                        }
                    }
                } else if(expr.getLeftExpression() instanceof AttributeExpression) { // attribute is subclass of property, so check in early
					AttributeExpression left = (AttributeExpression) expr.getLeftExpression();
					ClassNode target = getType(left.getObjectExpression());
					Expression prop = left.getProperty();
					Expression value = expr.getRightExpression();

					ClassNode[] argTypes = new ClassNode[] {getType(prop), getType(value)};

					List<MethodNode> methods = methodResolver.resolve(target, MISSING_ATTRIBUTE, argTypes);
					methods = StaticTypeCheckingSupport.chooseBestMethod(target, methods, argTypes);

					if(!methods.isEmpty()) {
						MethodCallExpression mce = new MethodCallExpression(left.getObjectExpression(), MISSING_ATTRIBUTE, new ArgumentListExpression(prop, value));
						visitor.visitMethodCallExpression(mce);
						return mce;
					}


				} else if(expr.getLeftExpression() instanceof PropertyExpression) {
					PropertyExpression left = (PropertyExpression) expr.getLeftExpression();
					ClassNode target = getType(left.getObjectExpression());
					Expression prop = left.getProperty();
					Expression value = expr.getRightExpression();

					ClassNode[] argTypes = new ClassNode[] {getType(prop), getType(value)};

					List<MethodNode> methods = methodResolver.resolve(target, MISSING_PROPERTY, argTypes);
					methods = StaticTypeCheckingSupport.chooseBestMethod(target, methods, argTypes);

					if(!methods.isEmpty()) {
						MethodCallExpression mce = new MethodCallExpression(left.getObjectExpression(), MISSING_PROPERTY, new ArgumentListExpression(prop, value));
						visitor.visitMethodCallExpression(mce);
						return mce;
					}


				}


            } else if(type == Types.LEFT_SQUARE_BRACKET && !inLeftAssign(oldExpr) && !inAffix()) {
                // simple foo[x]
                Expression target = expr.getLeftExpression();
                Expression index = expr.getRightExpression();

                ClassNode targetClass = getType(target);
                ClassNode[] argTypes = new ClassNode[] {getType(index)};
                List<MethodNode> methods = methodResolver.resolve(getType(target), GET_AT, argTypes);
                methods = StaticTypeCheckingSupport.chooseBestMethod(targetClass, methods, argTypes);

                if(!methods.isEmpty()) {
                    MethodCallExpression mce = new MethodCallExpression(target, GET_AT, new ArgumentListExpression(index));
                    visitor.visitMethodCallExpression(mce);
                    return mce;
                }
            }

            return expr.transformExpression(this);
        }



        public ClassNode getType(Expression e) {
            ClassNode type = e.getType();
            if(e.getNodeMetaData(StaticTypesMarker.INFERRED_TYPE) != null) {
                type = e.getNodeMetaData(StaticTypesMarker.INFERRED_TYPE);
            }

            return type;
        }
    }


    /**
     * Create extension method for static DefaultGroovyMethods-like transformations.
     * As foo.bar() -> bar(foo)
     * @param metaMethod extension method, used for replacement of .bar()
     * @param isStatic true, if it will be a static method in the target class Foo.bar() -> bar(null) vs. foo.bar() -> bar(foo)
     * @return
     */
    public static ExtensionMethodNode extend(MethodNode metaMethod, boolean isStatic) {
        Parameter[] types = metaMethod.getParameters();
        if(metaMethod.isStatic() && metaMethod.isPublic() && types.length > 0
                && metaMethod.getAnnotations(Deprecated_TYPE).isEmpty()) {
            Parameter[] parameters = new Parameter[types.length - 1];
            System.arraycopy(types, 1, parameters, 0, parameters.length);
            ExtensionMethodNode node = new ExtensionMethodNode(
                    metaMethod,
                    metaMethod.getName(),
                    metaMethod.getModifiers(),
                    metaMethod.getReturnType(),
                    parameters,
                    ClassNode.EMPTY_ARRAY, null,
                    isStatic);

            ClassNode declaringClass = types[0].getType();

            node.setDeclaringClass(declaringClass);

            return node;
        }

        return null;
    }



}
