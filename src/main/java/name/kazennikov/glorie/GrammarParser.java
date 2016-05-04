package name.kazennikov.glorie;

import name.kazennikov.features.*;
import name.kazennikov.glorie.func.Evaluator;
import name.kazennikov.glorie.func.FeatureFunctions;
import name.kazennikov.logger.Logger;

import java.net.URL;
import java.util.*;

/**
 * GLR Grammar parser. Converts ANTLR AST to Grammar class.
 *
 * Uses ParserContext class to resolve all entities encountered in the grammar:
 * - typed string literals
 * - functions
 * - operators
 */
public class GrammarParser extends  GLORIEBaseVisitor<Grammar> {
    private static final Logger logger = Logger.getLogger();

    Grammar grammar = new Grammar();
    URL baseURL;
    ParserContext parserContext;
    String source;
    String defaultImports = "import name.kazennikov.glorie.*;\n" +
            "import name.kazennikov.glorie.filters.*;\n" +
            "import groovy.lang.*;\n" +
            "import java.lang.*;\n" +
            "import gate.*;\n";


    public GrammarParser(URL baseURL, ParserContext parserContext, String source) {
        this.baseURL = baseURL;
        this.parserContext = parserContext;
        this.source = source;
    }


    /**
     * Parses java code source
     * @param ctx
     * @return source code as a string with some technical information in the comment in the first line
     */
    public String parseSource(GLORIEParser.JavaCodeContext ctx) {
        int start = ctx.start.getStartIndex();
        int end = ctx.stop.getStopIndex();
        int startLine = ctx.start.getLine();

        String source = GrammarParser.this.source.substring(start, end);

        return  "// Source: " + baseURL + ":" + startLine + "\n" +
                source.substring(1, source.length() - 1);
    }


    /**
     * RHS Action parser
     */
    public class ActionVisitor extends GLORIEBaseVisitor<RHSAction> {


        @Override
        public RHSAction visitJavaCode(GLORIEParser.JavaCodeContext ctx) {
            String source = parseSource(ctx);

			// changed to attr code
            return new GroovyRHSAttrAction(source);
        }

		@Override
		public RHSAction visitGroovyCode(GLORIEParser.GroovyCodeContext ctx) {
			String source = parseSource(ctx.javaCode());

			// ordinary groovy code
			return new GroovyRHSAction(source);
		}

		@Override
        public RHSAction visitMacroRef(GLORIEParser.MacroRefContext ctx) {
            RHSAction action = grammar.macros.get(ctx.ident().getText());

            if(action == null) {
                logger.error("Referenced missing macro at %s:%d, macro=%s", baseURL, ctx.start.getLine(), ctx.ident().getText());
            }

            return action;
        }

        @Override
        public RHSAction visitAttrs(GLORIEParser.AttrsContext ctx) {
            String source = parseSource(ctx.javaCode());
            return new GroovyRHSAttrAction(source);
        }
    }

    /**
     * Post proc
     */
    public class PostProcVisitor extends GLORIEBaseVisitor<SymbolNodePostProcessor.Source> {

        @Override
        public SymbolNodePostProcessor.Source visitJavaCode(GLORIEParser.JavaCodeContext ctx) {
            String source = parseSource(ctx);
            return new SymbolNodePostProcessor.Source(source);
        }

    }

    /**
     * Check is symbol type is non-terminal
     * @param type symbol span type
     * @return true if is non-terminal
     */
    public boolean nt(String type) {
        return !grammar.input.contains(type);
    }


    /**
     * Options section parser
     */
    public class OptsVisitor extends GLORIEBaseVisitor<Map<String, String>> {

        @Override
        public Map<String, String> visitOpts(GLORIEParser.OptsContext ctx) {
            Map<String, String> options = new HashMap<>();
            for(GLORIEParser.OptionContext optCtx : ctx.option()) {
                String key = optCtx.ident(0).getText();
                String value = optCtx.ident(1).getText();
                options.put(key, value);
            }

            return options;
        }
    }

    /**
     * Object value parser.
     * Tries to parse given string as:
     * - integer
     * - double
     *
     * return string otherwise, or if number parsing failed
     */
    public class ValueVisitor extends GLORIEBaseVisitor<Object> {
        @Override
        public Object visitValue(GLORIEParser.ValueContext ctx) {
            if(ctx.number() != null) {
                String s = ctx.number().getText();
                try {
                    return Integer.parseInt(s);
                } catch(NumberFormatException e) {
                    try {
                        return Double.parseDouble(s);
                    } catch(NumberFormatException e1) {
                        return s;
                    }
                }
            }

            if(ctx.STRING() != null) {
                String s = ctx.STRING().getText();
                return s.substring(1, s.length() - 1);
            }

            return ctx.getText();
        }
    }

    public class ValueParser extends GLORIEBaseVisitor<Value> {

        @Override
        public Value visitValue(GLORIEParser.ValueContext ctx) {
            Object o = new ValueVisitor().visitValue(ctx);
            return new Values.Const(o);
        }

        @Override
        public Value visitFeatureValue(GLORIEParser.FeatureValueContext ctx) {
            String featureName = ctx.ident().getText();
            int rootCount = ctx.root() == null? 0 : ctx.root().size();

            List<Value> args = new ArrayList<>();
            List<Value> rootArgs = new ArrayList<>();


            if(rootCount > 0) {
                rootArgs.add(new Values.Const(rootCount));
            } else {
                rootArgs.add(new MemoizedValue(FeatureFunctions.IDENTITY_SPAN_SYMBOL, new Values.Var(), new ArrayList<Value>()));
            }

            args.add(new MemoizedValue(FeatureFunctions.ROOT, new Values.Var(), rootArgs));

            MemoizedValue v = new MemoizedValue(new FeatureFunctions.FeatureAccessorFunction(featureName), new Values.Var(), args);

            return v;
        }

        @Override
        public Value visitMetaFeatureValue(GLORIEParser.MetaFeatureValueContext ctx) {
            String featureName = ctx.ident().getText();
            int rootCount = ctx.root() == null? 0 : ctx.root().size();

            List<Value> args = new ArrayList<>();
            List<Value> rootArgs = new ArrayList<>();

            if(rootCount > 0) {
                rootArgs.add(new Values.Const(rootCount));
            } else {
                rootArgs.add(new MemoizedValue(FeatureFunctions.IDENTITY_SPAN_SYMBOL, new Values.Var(), new ArrayList<Value>()));
            }

            args.add(new MemoizedValue(FeatureFunctions.ROOT, new Values.Var(), rootArgs));

            MemoizedValue v = new MemoizedValue(new FeatureFunctions.MetaFeatureFunction(parserContext, featureName), new Values.Var(), args);

            return v;
        }

        @Override
        public Value visitFunc(GLORIEParser.FuncContext ctx) {
            String name = ctx.ident().getText();

            List<Value> args = new ArrayList<>();

            for(GLORIEParser.ExprContext valContext : ctx.expr()) {
                Value val = valContext.accept(this);
                args.add(val);
            }

            Function fun = parserContext.getFunction(name);


            return new MemoizedValue(fun, new Values.Var(), args);
        }

		@Override
		public Value visitExpr(GLORIEParser.ExprContext ctx) {
			if(ctx.func() != null) {
				return ctx.func().accept(this);
			} else if(ctx.simpleVal() != null) {
				return ctx.simpleVal().accept(this);
			} else if(ctx.ident() != null) { // val '.' ident
				Function fun = parserContext.getFunction("attr");
				Value base = ctx.expr(0).accept(this);
				String attr = ctx.ident().getText();

				List<Value> args = new ArrayList<>();
				args.add(base);
				args.add(new Values.Const(attr));
				return new MemoizedValue(fun,new Values.Var(), args);
			} else { // val '[' val ']'
				Function fun = parserContext.getFunction("at");
				Value base = ctx.expr(0).accept(this);
				Value index = ctx.expr(1).accept(this);
				List<Value> args = new ArrayList<>();
				args.add(base);
				args.add(index);
				return new MemoizedValue(fun,new Values.Var(), args);
			}
		}


    }

    /**
     * SymbolSpan predicate parser
     */
    public class PredicateParser extends GLORIEBaseVisitor<SymbolSpanPredicate> {

        @Override
        public SymbolSpanPredicate visitSimpleFeatureSpec(GLORIEParser.SimpleFeatureSpecContext ctx) {
            ValueParser p = new ValueParser();
            Value v = ctx.accessor().accept(p);
            String op = ctx.op().getText();
            Object o = new ValueVisitor().visitValue(ctx.value());
            MemoizedValue vv = null;

            // shortcut
            if(v instanceof Values.Const) {
                FeatureAccessor fa = new FeatureAccessor.Simple(v.get().toString());
                return parserContext.getFeaturePredicate(op, fa, o);
            }

            if(v instanceof MemoizedValue) {
                vv = (MemoizedValue) v;
                // simple metafeature
                if(vv.getFeature() instanceof FeatureFunctions.MetaFeatureFunction &&
                        ((MemoizedValue) vv.arg(0)).getFeature() instanceof FeatureFunctions.RootFunction &&
                        ((MemoizedValue) ((MemoizedValue) vv.arg(0)).arg(0)).getFeature() == FeatureFunctions.IDENTITY_SPAN_SYMBOL) {
                    FeatureAccessor fa = parserContext.getMetaFeatureAccessor(vv.getFeature().name().substring(1));
                    return parserContext.getFeaturePredicate(op, fa, o);
                }


            } else {
                vv = new MemoizedValue(FeatureFunctions.SIMPLE_FEATURE, new Values.Var(), new ArrayList<>(Arrays.asList(v)));
            }

            Evaluator eval = new Evaluator(parserContext.optimize(vv));

            return parserContext.getFeaturePredicate(op, new FeatureAccessor.EvaluatorAccessor(eval), o);
        }

        @Override
        public SymbolSpanPredicate visitBooleanFeatureSpec(GLORIEParser.BooleanFeatureSpecContext ctx) {
            ValueParser p = new ValueParser();
            Value v = ctx.accessor().accept(p);


			// feature (possibly with root)
			if(ctx.accessor().simpleAccessor() != null && ctx.accessor().simpleAccessor().featureValue() != null) {
				GLORIEParser.FeatureValueContext featureValueContext = ctx.accessor().simpleAccessor().featureValue();
				int root = featureValueContext.root() != null? 0 : featureValueContext.root().size();

				SymbolSpanPredicate pred = parserContext.parseFeaturePredicate(featureValueContext.ident().getText());

				if(root == 0) {
					return pred;
				}

				List<Value> args = new ArrayList<>();
				List<Value> rootArgs = new ArrayList<>();


				rootArgs.add(new Values.Const(root));

				args.add(new MemoizedValue(FeatureFunctions.ROOT, new Values.Var(), rootArgs));

				MemoizedValue mv = new MemoizedValue(new FeatureFunctions.SymbolSpanPredicateFunction(pred), new Values.Var(), args);

				return new SymbolSpanPredicates.MemoizedValuePredicate(new Evaluator(parserContext.optimize(mv)));

			}

            MemoizedValue vv = null;

            if(v instanceof MemoizedValue) {
                vv = (MemoizedValue) v;
            } else {
                vv = new MemoizedValue(Functions.IDENTITY, new Values.Var(), new ArrayList<>(Arrays.asList(v)));
            }

            Evaluator eval = new Evaluator(parserContext.optimize(vv));

            return new SymbolSpanPredicates.MemoizedValuePredicate(eval);
        }

        @Override
        public SymbolSpanPredicate visitRecursiveSpec(GLORIEParser.RecursiveSpecContext ctx) {
            String op = ctx.op().getText();
            Symbol s = new RhsVisitor().visitSimpleMatcher(ctx.simpleMatcher());
            int root = ctx.root() == null? 0 : ctx.root().size();

            FeatureAccessor fa = root == 0? new FeatureAccessor.AnnotationAccessor() : new FeatureAccessor.HeadAnnotationAccessor(root);

            return parserContext.getContextPredicate(op, fa, s.pred);
        }

    }

    /**
     * Parser for whole RHS of a GLR rule
     */
    public class RhsVisitor extends GLORIEBaseVisitor<Symbol> {

        @Override
        public Symbol visitRhs(GLORIEParser.RhsContext ctx) {
            SymbolGroup g = new SymbolGroup.Or();

            for(int i = 0; i < ctx.rhsOrElem().size(); i++) {
                GLORIEParser.RhsOrElemContext elem = ctx.rhsOrElem(i);
                g.add(visitRhsOrElem(elem));
            }

            return g;
        }

        @Override
        public Symbol visitRhsOrElem(GLORIEParser.RhsOrElemContext ctx) {
            SymbolGroup g = new SymbolGroup.Simple();

            for(int i = 0; i < ctx.rhsElem().size(); i++) {
                GLORIEParser.RhsElemContext elem = ctx.rhsElem(i);
                g.add(visitRhsElem(elem));



            }

            return g;
        }

        @Override
        public Symbol visitIdentRHS(GLORIEParser.IdentRHSContext ctx) {
            String ident = ctx.getText();
			Symbol s = parserContext.typedStringSymbol(null, ident, (char) 0);
            return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitSimpleMatcher(GLORIEParser.SimpleMatcherContext ctx) {
            Symbol mainMatcher = visitAnnotSpec(ctx.annotSpec(0));
            List<SymbolSpanPredicate> preds = new ArrayList<>();

            if(mainMatcher.pred != SymbolSpanPredicate.TRUE)
                preds.add(mainMatcher.pred);

            for(int i = 1; i < ctx.annotSpec().size(); i++) {
                Symbol aux = visitAnnotSpec(ctx.annotSpec(i));
                List<SymbolSpanPredicate> auxPreds = new ArrayList<>();
                if(aux.pred != SymbolSpanPredicate.TRUE) {
                    auxPreds.add(aux.pred);
                }

                SymbolSpanPredicate pred = new SymbolSpanPredicates.StartsWith(new FeatureAccessor.AnnotationAccessor(),
                        new SymbolSpanPredicates.TypePredicate(aux.id, auxPreds));


                if(ctx.annotSpec(i).neg() != null) {
                    pred = new SymbolSpanPredicates.NotPredicate(pred);
                }

                preds.add(pred);
            }

			SymbolSpanPredicate pred = mainMatcher.pred;

            if(preds.size() > 1 || ctx.annotSpec().size() > 1) {
                pred = new SymbolSpanPredicates.AndPredicate(preds);
            }

            return new Symbol(mainMatcher.id, mainMatcher.nt, pred);
        }

        @Override
        public Symbol visitStringRHS(GLORIEParser.StringRHSContext ctx) {
            String value = ctx.getText();
            value = value.substring(1, value.length() - 1); // strip ""

			Symbol s = parserContext.typedStringSymbol(null, value, '"');

            return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitNumRHS(GLORIEParser.NumRHSContext ctx) {
            String value = ctx.getText();

            Symbol s = parserContext.typedStringSymbol(null, value, (char) 0);
			return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitSimpleRHS(GLORIEParser.SimpleRHSContext ctx) {
            return visitSimpleMatcher(ctx.simpleMatcher());
        }

        @Override
        public Symbol visitQstringRHS(GLORIEParser.QstringRHSContext ctx) {
            String value = ctx.getText();
            value = value.substring(1, value.length() - 1); // strip ''

            Symbol s = parserContext.typedStringSymbol(null, value, '\'');

            return new Symbol(s.id, nt(s.id), s.pred);

        }

        @Override
        public Symbol visitTypedREStringRHS(GLORIEParser.TypedREStringRHSContext ctx) {
            String value = ctx.getText();
            value = value.substring(1, value.length() - 1); // strip ''

            Symbol s = parserContext.typedStringSymbol(null, value, '/');

            return new Symbol(s.id, nt(s.id), s.pred);

        }

        @Override
        public Symbol visitRestringRHS(GLORIEParser.RestringRHSContext ctx) {
            String value = ctx.getText();
            int start = value.indexOf('"');
            String type = value.substring(0, start);
            String text = value.substring(start + 1, value.length() - 1);

            Symbol s = parserContext.typedStringSymbol(type, text, '/');
            return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitTypedQStringRHS(GLORIEParser.TypedQStringRHSContext ctx) {
            String value = ctx.getText();

            Symbol s = parserContext.typedStringSymbol(null, value, '\'');
            return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitTypedStringRHS(GLORIEParser.TypedStringRHSContext ctx) {
            String value = ctx.getText();
            int start = value.indexOf('"');
            String type = value.substring(0, start);
            String text = value.substring(start + 1, value.length() - 1);

            Symbol s = parserContext.typedStringSymbol(type, text, '"');
			return new Symbol(s.id, nt(s.id), s.pred);
        }

        @Override
        public Symbol visitAnnotSpec(GLORIEParser.AnnotSpecContext ctx) {
            String type = ctx.ident().getText();
            List<SymbolSpanPredicate> preds = new ArrayList<>();


            for(GLORIEParser.FeatureContext featCtx : ctx.feature()) {
                String s = featCtx.getChild(0).getText();
                SymbolSpanPredicate p = featCtx.featureSpec().accept(new PredicateParser());
                p = parserContext.optimize(p);
                if(featCtx.neg() != null) {
                    p = new SymbolSpanPredicates.NotPredicate(p);
                }

                if(p == SymbolSpanPredicate.TRUE)
                    continue;

                preds.add(p);
            }



            SymbolSpanPredicate p;
            switch(preds.size()) {
                case 0:
                    p = SymbolSpanPredicate.TRUE;
                    break;
                case 1:
                    p = preds.get(0);
                    break;
                default:
                    p = new SymbolSpanPredicates.AndPredicate(preds);
            }


            return new Symbol(type,  nt(type), p);
        }

        @Override
        public Symbol visitGroupRHS(GLORIEParser.GroupRHSContext ctx) {
            return visitRhs(ctx.rhs());
        }

        @Override
        public Symbol visitRhsElem(GLORIEParser.RhsElemContext ctx) {
            Symbol s = ctx.rhsAtom().accept(this);
            String label = null;

            if(ctx.label() != null) {
                label = ctx.label().ident().getText();
            }

            if(ctx.modif() != null) {

                int min = 0;
                int max = Integer.MAX_VALUE;

                if(ctx.modif() instanceof GLORIEParser.OptionalContext) {
                    min = 0;
                    max = 1;

                } else if(ctx.modif() instanceof GLORIEParser.PlusContext) {
                    min = 1;
                } else if(ctx.modif() instanceof GLORIEParser.StarContext) {
                    min = 0;
                } else if(ctx.modif() instanceof GLORIEParser.RangeContext) {
                    GLORIEParser.RangeContext rc = (GLORIEParser.RangeContext) ctx.modif();
                    min = Integer.parseInt(rc.number(0).getText());
                    if(rc.number().size() > 1) {
                        max = Integer.parseInt(rc.number(1).getText());
                    }
                }

                SymbolGroup.Range g = new SymbolGroup.Range(min, max);
                g.add(s);
                s = g;
            }

            s.labels.add(label);

            if(ctx.root() != null) {
                s.root = true;
            }
            return s;
        }
    }

    @Override
    public Grammar visitGlr(GLORIEParser.GlrContext ctx) {
        super.visitGlr(ctx);
        return grammar;
    }

    @Override
    public Grammar visitName(GLORIEParser.NameContext ctx) {
        grammar.name = ctx.ident().getText();
        return super.visitName(ctx);
    }

    @Override
    public Grammar visitHeader(GLORIEParser.HeaderContext ctx) {
        grammar.name = ctx.name().ident().getText();

        if(ctx.input() != null && !ctx.input().isEmpty()) {
            if(ctx.input().size() > 1)
                throw new IllegalStateException("Input specified more than once in the grammar");

            for(GLORIEParser.IdentContext identContext : ctx.input().get(0).ident()) {
                grammar.input.add(identContext.getText());
            }
        }

        if(ctx.output() != null && !ctx.output().isEmpty()) {
            if(ctx.output().size() > 1)
                throw new IllegalStateException("Output specified more than once in the grammar");

            for(GLORIEParser.IdentContext identContext : ctx.output().get(0).ident()) {
                grammar.output.add(identContext.getText());
            }
        }

        if(ctx.start() != null) {
            if(ctx.start().size() > 1)
                throw new IllegalStateException("Start specified more than once in the grammar");

            grammar.start = new Symbol(ctx.start().get(0).ident().getText(), true);
        }

        if(ctx.opts() != null) {
            if(ctx.opts().size() > 1)
                throw new IllegalStateException("Options specified more than once in the grammar");

            //grammar.options = new OptsVisitor().visitOpts(ctx.opts(0));
        }

        if(ctx.context() != null) {
            if(ctx.context().size() == 1) {
                grammar.context = ctx.context(0).ident().getText();
            }
        }

        if(ctx.importBlock() != null) {
            if(ctx.importBlock().size() == 1) {
                GLORIEParser.JavaCodeContext block = ctx.importBlock(0).javaCode();
                int start = block.start.getStartIndex();
                int end = block.stop.getStopIndex();
                String source = GrammarParser.this.source.substring(start, end);
                grammar.imports = defaultImports + "\n" + source.substring(1, source.length() - 1);
            }
        }

        return super.visitHeader(ctx);
    }

    @Override
    public Grammar visitCode(GLORIEParser.CodeContext ctx) {
        int start = ctx.javaCode().start.getStartIndex();
        int end = ctx.javaCode().stop.getStopIndex();
        String source = GrammarParser.this.source.substring(start, end);

        grammar.codeBlocks.add(source.substring(1, source.length() - 1));
        return grammar;
    }

    @Override
    public Grammar visitPre(GLORIEParser.PreContext ctx) {
		if(ctx.javaCode() != null) {
			grammar.preSource = parseSource(ctx.javaCode());
		}

		if(ctx.className() != null) {
			grammar.preClassName = GrammarParser.this.source.substring(ctx.className().start.getStartIndex(), ctx.className().stop.getStopIndex());
		}

        return grammar;
    }

    @Override
    public Grammar visitPost(GLORIEParser.PostContext ctx) {
		if(ctx.javaCode() != null) {
			grammar.postSource = parseSource(ctx.javaCode());
		}

		if(ctx.className() != null) {
			grammar.postClassName = GrammarParser.this.source.substring(ctx.className().start.getStartIndex(), ctx.className().stop.getStopIndex() + 1);
		}

        return grammar;
    }

    @Override
    public Grammar visitProduction(GLORIEParser.ProductionContext ctx) {
        Symbol lhs = new Symbol(ctx.lhs().ident().getText(), true);

        RhsVisitor rhsVisitor = new RhsVisitor();
        SymbolGroup rhs = (SymbolGroup) rhsVisitor.visitRhs(ctx.rhs());

        ActionVisitor actionVisitor = new ActionVisitor();

        RHSAction action = null;

        if(ctx.action() != null) {
            action = actionVisitor.visitAction(ctx.action());
        }

		double weight = 1.0;

		if(ctx.lhsWeight() != null) {
			weight = Double.parseDouble(ctx.lhsWeight().Number().getText());
		}

        SymbolNodePostProcessor.Source ppSource = null;
        PostProcVisitor ppVistor = new PostProcVisitor();

        if(ctx.postprocAction() != null) {
            ppSource = ppVistor.visitPostprocAction(ctx.postprocAction());
        }

		boolean greedy = ctx.getChild(0).getText().equals("!");

        Production p = new Production(null, lhs, Arrays.asList(rhs), false, action, ppSource, weight, greedy);
        p.sourceLine = ctx.getStart().getLine();


        grammar.productions.add(p);

        return grammar;
    }

    @Override
    public Grammar visitMacro(GLORIEParser.MacroContext ctx) {
        String name = ctx.ident().getText();
        ActionVisitor visitor = new ActionVisitor();
        RHSAction action = visitor.visitAction(ctx.action());

        if(grammar.macros.containsKey(name)) {
            logger.info("Redefining macro %s at %s:%d", name, baseURL, ctx.start.getLine());
        }

        grammar.macros.put(name, action);

        return grammar;
    }
}
