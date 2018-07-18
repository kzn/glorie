package name.kazennikov.glorie;

import name.kazennikov.features.Function;
import name.kazennikov.features.FunctionRewriter;
import name.kazennikov.features.MemoizedValue;
import name.kazennikov.glorie.func.FeatureFunctions;
import org.codehaus.groovy.transform.ASTTransformation;

import java.util.*;

/**
 * Basic parser context. Designed as a reasonable base class for user extensions
 *
 * Basic parser context definition:
 *
 * 1. Meta features:
 *    - type
 *    - start
 *    - end
 *    - length
 *
 *  2. Feature operators:
 *     - == - equals
 *     - != - not equals
 *     - > - greater than
 *     - < - lesser than
 *     - >= - greater or equal
 *     - <= - lesser of equal
 *     - =~ - contains regexp
 *     - ==~ - matches regexp
 *     - ==* - equals ignore case (for strings)
 *     - !=* - not equals ignore case (for string)
 *     - 'in' - checks if feature equals any of given constant values. Possible values
 *       are written in a '|' separated string e.g. "1|2|3" to check if a feature is equals '1', '2' or '3'
 *     - a mechanism to handle 'notFoo' predicates if not defined. They are transformed
 *       to not(foo(...)) predicate if 'foo' predicates is defined

 *
 *  3. Context predicates:
 *     - startsWith - true, if spans A and B starts with same position
 *     - notStartsWith - true, if spans A and B NOT start with the same position
 *     - coextWith - true, if spans A and B start and ands on same positions
 *     - contains - true, if span A contains fully or is coextensive with span B
 *     - a mechanism to handle 'notFoo' predicates if not defined. They are transformed
 *       to not(foo(...)) predicate if 'foo' predicates is defined
 *
 *  4. Typed strings:
 *     - default is mapped to check Token.string feature (case insensitive)
 *     - 'cs' is is mapped to check Token.string feature (case sensitive)
 *     - any other types are mapped to check case insensitively 'string' feature of a span of specified type
 *
 *  5. Computed predicate features
 *     - 'cap' (orth == upperInitial)
 *     - 'lc' (orth == lowercase)
 *     - 'uc' (orth == allCaps)
 *     - 'mixcap' (orth == mixed)
 *     - 'fw', first word in the context
 *     - 'lw', last word in the context
 *
 *  6. Predicate functions:
 *     - 'fw', first word predicate function
 *     - 'lw', last word predicate function
 *
 */
public class BasicParserContext implements ParserContext {

    public static interface ValuePredicateFactory {
        public SymbolSpanPredicate newPredicate(FeatureAccessor fa, Object value);
    }

    public static interface MetaFeatureFactory {
        public FeatureAccessor newAccessor();
    }

    public static interface ContextPredicateFactory {
        public SymbolSpanPredicate newPredicate(FeatureAccessor fa, SymbolSpanPredicate inner);
    }


    Map<String, ValuePredicateFactory> valuePredicates = new HashMap<>();
    Map<String, MetaFeatureFactory> metaFeatures = new HashMap<>();
    Map<String, ContextPredicateFactory> contextPredicates = new HashMap<>();
    Map<String, Function> functions = new HashMap<>();
    List<FunctionRewriter> functionRewriter = new ArrayList<>();
    List<SymbolSpanPredicate.Rewriter> predRewriters = new ArrayList<>();

    public BasicParserContext() {
        registerMetaFeature("type", new MetaFeatureFactory() {
            @Override
            public FeatureAccessor newAccessor() {
                return new FeatureAccessor.Type();
            }
        });

        registerMetaFeature("start", new MetaFeatureFactory() {
            @Override
            public FeatureAccessor newAccessor() {
                return new FeatureAccessor.Start();
            }
        });

        registerMetaFeature("end", new MetaFeatureFactory() {
            @Override
            public FeatureAccessor newAccessor() {
                return new FeatureAccessor.End();
            }
        });

        registerMetaFeature("length", new MetaFeatureFactory() {
            @Override
            public FeatureAccessor newAccessor() {
                return new FeatureAccessor.Length();
            }
        });

        registerValuePredicate("==", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.Equal(fa, value);
            }
        });

        registerValuePredicate("!=", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.NotPredicate(new SymbolSpanPredicates.Equal(fa, value));
            }
        });

        registerContextPredicate("startsWith", new ContextPredicateFactory() {
            @Override
            public SymbolSpanPredicates.ContextPredicate newPredicate(FeatureAccessor fa, SymbolSpanPredicate inner) {
                return new SymbolSpanPredicates.StartsWith(fa, inner);
            }
        });

        registerContextPredicate("notStartsWith", new ContextPredicateFactory() {
            @Override
            public SymbolSpanPredicate newPredicate(FeatureAccessor fa, SymbolSpanPredicate inner) {
                return new SymbolSpanPredicates.NotPredicate(new SymbolSpanPredicates.StartsWith(fa, inner));
            }
        });


		registerContextPredicate("coextWith", new ContextPredicateFactory() {
			@Override
			public SymbolSpanPredicate newPredicate(FeatureAccessor fa, SymbolSpanPredicate inner) {
				return new SymbolSpanPredicates.CoextensiveWith(fa, inner);
			}
		});

		registerContextPredicate("contains", new ContextPredicateFactory() {
			@Override
			public SymbolSpanPredicate newPredicate(FeatureAccessor fa, SymbolSpanPredicate inner) {
				return new SymbolSpanPredicates.ContainsPredicate(fa, inner);
			}
		});

        registerValuePredicate(">", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.GreaterPredicate(fa, value);
            }
        });

        registerValuePredicate(">=", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.GreaterEqualsPredicate(fa, value);
            }
        });

        registerValuePredicate("<", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.LesserPredicate(fa, value);
            }
        });

        registerValuePredicate("<=", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.LesserEqualsPredicate(fa, value);
            }
        });

        registerValuePredicate("=~", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.RegexFindPredicate(fa, value);
            }
        });

        registerValuePredicate("==~", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.RegexMatchPredicate(fa, value);
            }
        });

        registerValuePredicate("==*", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicates.ValuePredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.EqualIgnoreCase(fa, value);
            }
        });

        registerValuePredicate("!=*", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.NotPredicate(new SymbolSpanPredicates.EqualIgnoreCase(fa, value));
            }
        });

        registerValuePredicate("in", new ValuePredicateFactory() {
            @Override
            public SymbolSpanPredicate newPredicate(FeatureAccessor fa, Object value) {
                return new SymbolSpanPredicates.InPredicate(fa, (String) value);
            }
        });

        registerFunction(new FeatureFunctions.FirstWordPredicate());
        registerFunction(new FeatureFunctions.LastWordPredicate());
    }

    @Override
    public SymbolSpanPredicate getFeaturePredicate(String op, FeatureAccessor accessor, Object value) {
        ValuePredicateFactory factory = valuePredicates.get(op);

        if(factory != null)
            return factory.newPredicate(accessor, value);

        // special case for notFoo predicates
        // compile them to not(foo(..)
        if(op.startsWith("not") && op.length() > 4 && Character.isUpperCase(op.charAt(3))) {
            String newOp = "" + Character.toLowerCase(op.charAt(3)) + op.substring(4);
            factory = valuePredicates.get(newOp);
            if(factory != null) {
                SymbolSpanPredicate p = factory.newPredicate(accessor, value);
                return new SymbolSpanPredicates.NotPredicate(p);
            }
        }

        return null;
    }

    @Override
    public FeatureAccessor getMetaFeatureAccessor(String type) {
        MetaFeatureFactory factory = metaFeatures.get(type);
        return factory != null? factory.newAccessor() : null;
    }

    @Override
    public SymbolSpanPredicate getContextPredicate(String op, FeatureAccessor fa, SymbolSpanPredicate innerPredicate) {
        ContextPredicateFactory factory = contextPredicates.get(op);

        if(factory != null)
            return factory.newPredicate(fa, innerPredicate);

        // special case for notFoo predicates
        // compile them to not(foo(..)
        if(op.startsWith("not") && op.length() > 4 && Character.isUpperCase(op.charAt(3))) {
            String newOp = "" + Character.toLowerCase(op.charAt(3)) + op.substring(4);
            factory = contextPredicates.get(newOp);
            if(factory != null) {
                SymbolSpanPredicate p = factory.newPredicate(fa, innerPredicate);
                return new SymbolSpanPredicates.NotPredicate(p);
            }
        }

        return null;
    }

    @Override
    public Symbol typedStringSymbol(String type, String text, char quote) {

        // Foo, without quotes
		if(quote == 0) {
			return new Symbol(text, true);
		}

		if(Objects.equals(type, "cs")) {
			FeatureAccessor acc = new FeatureAccessor.Simple("string");
			SymbolSpanPredicate pred = new SymbolSpanPredicates.Equal(acc, text);
			return new Symbol(type, false, pred);
		}

        if(type == null) {
            type = "Token";
        }

        FeatureAccessor acc = new FeatureAccessor.Simple("string");
        SymbolSpanPredicate pred = new SymbolSpanPredicates.EqualIgnoreCase(acc, text);
        return new Symbol(type, false, pred);
    }

    @Override
    public Function getFunction(String name) {
        return functions.get(name);
    }

    public void registerValuePredicate(String op, ValuePredicateFactory factory) {
        valuePredicates.put(op, factory);
    }

    public void registerMetaFeature(String name, MetaFeatureFactory factory) {
        metaFeatures.put(name, factory);
    }

    public void registerContextPredicate(String op, ContextPredicateFactory factory) {
        contextPredicates.put(op, factory);
    }

    public void registerFunction(Function f) {
        functions.put(f.name(), f);
    }

    public void addFunctionRewriter(FunctionRewriter rewriter) {
        functionRewriter.add(rewriter);
    }

    public void addPredicateRewriter(SymbolSpanPredicate.Rewriter rewriter) {
        predRewriters.add(rewriter);
    }

    @Override
    public MemoizedValue optimize(MemoizedValue value) {
        for(FunctionRewriter rw : functionRewriter) {
            value = rw.rewrite(value);
        }

        return value;
    }

    @Override
    public SymbolSpanPredicate optimize(SymbolSpanPredicate predicate) {
        for(SymbolSpanPredicate.Rewriter r : predRewriters) {
            predicate = r.rewrite(predicate);
        }

        return predicate;
    }

	@Override
	public SymbolSpanPredicate parseFeaturePredicate(String feature) {
        switch(feature) {
            case "cap": // capitalized case
                return new SymbolSpanPredicates.Equal(new FeatureAccessor.Simple("orth"), "upperInitial");
            case "lc": // lowercase
                return new SymbolSpanPredicates.Equal(new FeatureAccessor.Simple("orth"), "lowercase");
            case "uc": // upper case
                return new SymbolSpanPredicates.Equal(new FeatureAccessor.Simple("orth"), "allCaps");
            case "mixcap": // mixed
                return new SymbolSpanPredicates.Equal(new FeatureAccessor.Simple("orth"), "mixed");

            case "fw": // first word
                return new SymbolSpanPredicates.FirstWordPredicate();
            case "lw": // last word
                return new SymbolSpanPredicates.LastWordPredicate();


            default:
                return new SymbolSpanPredicates.SourceCodeSymbolSpanPredicate(feature);
        }
	}

    @Override
    public ASTTransformation astTransformation() {
        return null;
    }
}
