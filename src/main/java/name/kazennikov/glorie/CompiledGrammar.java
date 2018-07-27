package name.kazennikov.glorie;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import groovy.lang.GroovyClassLoader;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;
import name.kazennikov.logger.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
* Compiled version of the {@link Grammar}
 *
 * Maps Symbol object to integers and finalize rules.
 * Optimized for generation of LR tables and parsing
 *
*/
public class CompiledGrammar {
    private static final Logger logger = Logger.getLogger();

    /**
     * Compiled version of a production
     */
    public static class Rule {
        Production production;

        int id;
        int lhs;
        int[] rhs;
		int reductionPathSize;

        public Rule(Production production, int id, int lhs, int[] rhs) {
            this.production = production;
            this.id = id;
            this.lhs = lhs;
            this.rhs = rhs;
			this.reductionPathSize = rhs.length * 2 + 1;
        }

        public boolean synth() {
            return production.synth;
        }
    }


    public static final String DEFAULT_IMPORTS = "import " + ReduceAction.class.getName() + ";\n"
            + "import " + SymbolSpan.class.getName() + ";\n"
            + "import " + GLRParser.class.getName() + ";\n"
            + "import " + CompiledGrammar.class.getName() + ";\n"
            + "import gate.*;\n"
            + "import name.kazennikov.glorie.*;\n"
            + "\n\n";


    Grammar grammar;
    PreBaseScript pre;
    PostBaseScript post;

    Alphabet<Symbol> symbols = new Alphabet<>(0, -1);
    TObjectIntHashMap<String> terminals = new TObjectIntHashMap<>(16, 0.5f, -1);
    int[] output;
    Rule[] rules;
    GroovyClassLoader groovyClassLoader;

    ReduceActionCompiler compiler; // compiler for reduce actions
    InterpCompiler interpCompiler; // compiler for interp actions


    SynthTerminalEvaluator[] evaluators;
    int maxSynthSize; // maximum number of synth terminal by type

    ReduceActionGenerator[] actionGenerators;
    CompiledReduceAction[] actions;

    InterpCompiler.Generator[] interpGenerators;
    InterpAction[] interp;

    List<List<Symbol>> prefixes;
    List<SymbolSpanPredicate> predicates;
    WalkFSABoolean prefixFSA;

    // predicate FSA
    Alphabet<FeatureAccessor> accessors = new Alphabet<>();
    Alphabet<Object> fsaPredValues = new Alphabet<>();
    WalkFSABoolean predFSA;

    List<GreedyPolicy> policies = new ArrayList<>();

    Object grammarCode;


    int start;
    int lrStart;
    int eof;

    public boolean isNT(int symbolId) {
        return symbols.get(symbolId).nt;
    }

    public boolean isEOF(int symbolId) {
        return symbols.get(symbolId) == Symbol.EOF;
    }

    protected CompiledGrammar() {

    }

    /**
     * Creates compiled version of the grammar
     * @param g GLR grammar source
     * @param groovyClassLoader groovy class loader
     * @param reduceActionCompiler compiler of reduce actions
     * @param interpCompiler compiler of post-processing actions
     */
    public CompiledGrammar(Grammar g, GroovyClassLoader groovyClassLoader, ReduceActionCompiler reduceActionCompiler, InterpCompiler interpCompiler) throws Exception {
        this.grammar = g;
        this.groovyClassLoader = groovyClassLoader;
        this.compiler = reduceActionCompiler;
        this.interpCompiler = interpCompiler;

        rules = new Rule[g.productions.size()];

        // compile all productions
        for(int prodId = 0; prodId < g.productions.size(); prodId++) {
            Production p = g.productions.get(prodId);
            int lhs = symbols.get(p.lhs);
            int[] rhs = new int[p.rhs.size()];

            for(int i = 0; i < p.rhs.size(); i++) {
                rhs[i] = symbols.get(p.rhs.get(i));
            }

            rules[prodId] = new Rule(p, prodId, lhs, rhs);
        }

        start = symbols.get(g.start);
        lrStart = symbols.get(g.lrStart);

        // add plain annotation symbols from evaluator
        for(Map.Entry<String, SynthTerminalEvaluator> e : g.evaluators.entrySet()) {
            symbols.get(new Symbol(e.getKey(), false));
        }

        // set output symbols
        output = new int[symbols.size()];
        for(int i = 0; i < symbols.size(); i++) {
            Symbol s = symbols.get(i);
            if(grammar.output.contains(s.id) || grammar.output.isEmpty()) {
                output[i] = 1;
            }

        }

        // set terminals
        for(int i = 0; i < symbols.size(); i++) {
            Symbol s = symbols.get(i);
            if(s.nt)
                continue;

            terminals.put(s.id, i);
        }

        // compile evaluators
        evaluators = new SynthTerminalEvaluator[symbols.size() + 1];
        for(Map.Entry<String, SynthTerminalEvaluator> e : g.evaluators.entrySet()) {
            int id = terminals.get(e.getKey());

            SynthTerminalEvaluator eval = e.getValue();

            // skip empty evaluators
            if(eval.size() == 0)
                continue;

            eval.typeId = id;

            for(String s : eval.types) {
                int typeId = terminals.get(s);
                eval.typeIds.add(typeId);
            }

            for(SymbolSpanPredicate pred : eval.predicates) {
                int predId = g.predicates.get(pred);
                eval.predIds.add(predId);
            }

            evaluators[id] = eval;
            maxSynthSize = Math.max(maxSynthSize, eval.typeIds.size());
        }

        maxSynthSize++; // truth predicate should be also considered

        logger.info("Grammar has %d productions with %d symbols (%d terminals)",
                rules.length, symbols.size(), terminals.size());
        logger.info("Using %d distinct type terminal evaluators", g.nextSynthTerminalId);
        logger.info("Max # evaluators for single type: %d", maxSynthSize);


        compilePreScript();
        compilePostScript();
        compileReduceActions();
        compileInterp();
        buildPrefixTrie();
        optimizeSynth();

        if(grammar.useGreedy) {
            computePolicies();
        }

        compileCode();

        eof = terminals.get("EOF");
        predicates = g.predicates.entries();
        compileSourcePredicates();


    }

    /**
     * Build grammar prefix trie
     */
    public void buildPrefixTrie() {

        int prefixLength = 3;
        BooleanFSABuilder fsaBuilder = new BooleanFSABuilder();
        prefixes = new ArrayList<>();
		long st = System.currentTimeMillis();

        // iterate over prefixes
        for(TIntArrayList l : buildFirstK(prefixLength).get(start)) {
            List<Symbol> prefix = new ArrayList<>();
            for(int i = 0; i < l.size(); i++) {
                prefix.add(symbols.get(l.get(i)));
            }
            fsaBuilder.add(l);
            prefixes.add(prefix);
        }

        prefixFSA = fsaBuilder.build();
        logger.info("Prefix FSA: %d states for %d prefixes of length %d, built in %d ms",
				fsaBuilder.size(), prefixes.size(), prefixLength, System.currentTimeMillis() - st);
    }


    // building m_FirstSets.
// m_FirstSets[i] is a set of all terminals which can start a terminal sequence,
// which can be produced from  non-terminal symbol  i
//==============
//  1. We first initialize the FIRST sets to the empty set
// 2. Then we process each grammar rule in the following way: if the right-hand side
// starts with a terminal  symbol. we add this symbol to the FIRST set  of the
//  left-hand side, since it  can be the first symbol of a sentential form
// derived from the left side.  If the right-hand side starts with a non-terminal symbol
// we add all symbols of the present FIRST set of this non-terminal to the FIRST set
// of the left-hand side. These are all symbols that can be the first  terminal of
//  a sentential for derived from the left-hand side.
//  3. The previous  step is repeated until no more new symbols are added to any of the
// FIRST sets.



    public TIntSet[] buildFIRST() {


        TIntSet[] firstSets = new TIntSet[symbols.size()];
        for(int i = 0; i < symbols.size(); i++) {
            firstSets[i] = new TIntHashSet();
        }

        while(true) {
            boolean changed = false;

            for(Rule p : rules) {
                int first = p.rhs[0];
                if(isNT(first)) {
                    TIntSet firstSet = firstSets[first];
                    TIntIterator it = firstSet.iterator();

                    while(it.hasNext()) {
                        if(firstSets[p.lhs].add(it.next()))
                            changed = true;
                    }
                } else {
                    if(firstSets[p.lhs].add(first))
                        changed = true;
                }
            }

            if(!changed)
                break;
        }

        return firstSets;
    }

    public TIntSet[] buildFOLLOW(TIntSet[] firstSets) {
        TIntSet[] followSets = new TIntSet[symbols.size()];

        for(int i = 0; i < symbols.size(); i++) {
            followSets[i] = new TIntHashSet();
        }

        while(true) {
            boolean changed = false;

            for(Rule p : rules) {
                for(int i = 0; i < p.rhs.length - 1; i++) {
                    int follow = p.rhs[i + 1];
                    if(isNT(follow)) {
                        TIntSet set = firstSets[follow];
                        TIntIterator it = set.iterator();
                        while(it.hasNext()) {
                            if(followSets[p.rhs[i]].add(it.next()))
                                changed = true;
                        }
                    } else {
                        if(followSets[p.rhs[i]].add(follow))
                            changed = true;
                    }
                }

                int follow = p.rhs[p.rhs.length - 1];
                if(isNT(follow)) {
                    TIntIterator it = followSets[p.lhs].iterator();
                    while(it.hasNext()) {
                        if(followSets[follow].add(it.next()))
                            changed = true;
                    }
                }
            }

            if(!changed)
                break;
        }

        return followSets;
    }



	/**
	 this procedure builds map First_k.  First_k[i] contains all possible sequence of terminals
	 which can be a prefix of length PrefixLength or less of a string which is  derived from meta symbol i.
	 The process of computing First_k() is described in many sources about Parsing and is similar
	 to function CWorkGrammar::Build_FIRST_Set.

	 @param prefixLength prefix length
	 */
	public TIntObjectHashMap<Set<TIntArrayList>> buildFirstK(int prefixLength) {
		TIntObjectHashMap<Set<TIntArrayList>> firstKSet = new TIntObjectHashMap<>();

		for(int i = 0; i < symbols.size(); i++) {
			firstKSet.put(i, new HashSet<TIntArrayList>());
		}

		while(true) {
			boolean changed = false;

			for(Rule p : rules) {
				Set<TIntArrayList> thisRuleResult = new HashSet<>();
				thisRuleResult.add(new TIntArrayList()); // add empty prefix

				int min = Math.min(p.rhs.length, prefixLength);

				for(int i = 0; i < min; i++ ) {
					Set<TIntArrayList> additions = new HashSet<>();
					int s = p.rhs[i];

					if(isNT(s)) {
						additions.addAll(firstKSet.get(s));
					} else {
						additions.add(new TIntArrayList(new int[]{s}));
					}



					if(additions.isEmpty()) {
						thisRuleResult.clear();
						break;
					}



					Set<TIntArrayList> newRuleResult = new HashSet<>();

					for(TIntArrayList prefix : thisRuleResult) {
						if(prefix.size() == prefixLength) {
							newRuleResult.add(prefix);
							continue;
						}

						for(TIntArrayList addition : additions) {
							TIntArrayList updated = new TIntArrayList(prefixLength);
							updated.addAll(prefix);
							int addLen = Math.min(addition.size(), prefixLength - prefix.size());
							updated.addAll(addition.subList(0, addLen));
							newRuleResult.add(updated);
						}
					}

					thisRuleResult = new HashSet<>(newRuleResult);

				}

				Set<TIntArrayList> s = firstKSet.get(p.lhs);
				int initSize = s.size();
				s.addAll(thisRuleResult);
				changed = changed || initSize != s.size();
			}

			if(!changed)
				break;
		}

		return firstKSet;
	}


	public String context() {
		return grammar.context;
    }

    public Set<String> input() {
        return grammar.input;
    }

    public boolean hasInput(String type) {
        return grammar.input.contains(type);
    }

    /**
     * Compile grammar reduce actions
     * @throws Exception
     */
    public void compileReduceActions() throws Exception {
        // gather all reduce actions from all rules
        actionGenerators = new ReduceActionGenerator[grammar.productions.size()];

        for(int i = 0; i < grammar.productions.size(); i++) {
            actionGenerators[i] = compiler.add(grammar, rules[i], grammar.productions.get(i).action);
        }

        // compile them
        try {
            compiler.compile();
        } catch(Exception e) {
            logger.error("Error while compiling code of grammar %s", grammar.name, e);
            throw e;
        }

        // set generated rules back
        actions = new CompiledReduceAction[grammar.productions.size()];

        for(int i = 0; i < actionGenerators.length; i++) {
            actions[i] = actionGenerators[i] != null? actionGenerators[i].generate() : CompiledReduceAction.SIMPLE;
        }


    }

    private void compilePostScript() throws Exception {
        // compile post block
        if(grammar.postSource != null) {
            compilePost();
        } else if(grammar.postClassName != null) {
			try {
				Class c = groovyClassLoader.loadClass(grammar.postClassName);
				post = (PostBaseScript) c.newInstance();
			} catch(Exception e) {
				logger.error(e);
			}
		}

        if(post == null) {
post = new PostBaseScript.Simple();
}
    }

    private void compilePreScript() throws Exception {
        // compile pre block
        if(grammar.preSource != null) {
            compilePre();
        } else if(grammar.preClassName != null) {
			try {
				Class c = groovyClassLoader.loadClass(grammar.preClassName);
				pre = (PreBaseScript) c.newInstance();
			} catch(Exception e) {
				logger.error(e);
			}
		}

        if(pre == null) {
pre = new PreBaseScript.Simple();
}
    }

    /**
     * Compile the PRE block
     */
    public void compilePre() throws Exception {
        SourceInfo sourceInfo = new SourceInfo(grammar.name, "PRE");
        StringBuilder src = new StringBuilder();
        src.append(DEFAULT_IMPORTS).append("\n");
        src.append(grammar.imports).append("\n");
        src.append("@groovy.transform.BaseScript ").append(PreBaseScript.class.getName()).append(" __script;\n");
        src.append(sourceInfo.addBlock(src.toString(), grammar.preSource));
        try {
            Class c = groovyClassLoader.parseClass(src.toString());
            pre = (PreBaseScript) c.newInstance();
            pre.sourceInfo = sourceInfo;
            pre.sourceInfo.setClassName(pre.getClass().getName());
        } catch(Exception e) {
            logger.error("Error while compiling PRE block of grammar %s", grammar.name, e);
            throw e;
        }
    }


    /**
     * Compile the POST block
     */
    public void compilePost() throws Exception {
        SourceInfo sourceInfo = new SourceInfo(grammar.name, "PRE");
        StringBuilder src = new StringBuilder();
        src.append(DEFAULT_IMPORTS).append("\n");
        src.append(grammar.imports).append("\n");
        src.append("@groovy.transform.BaseScript ").append(PostBaseScript.class.getName()).append(" __script;\n");
        src.append(sourceInfo.addBlock(src.toString(), grammar.postSource));
        try {
            Class c = groovyClassLoader.parseClass(src.toString());
            post = (PostBaseScript) c.newInstance();
            post.sourceInfo = sourceInfo;
            post.sourceInfo.setClassName(post.getClass().getName());
        } catch(Exception e) {
            logger.error("Error while compiling POST block of grammar %s", e, grammar.name);
            throw e;
        }
    }

    /**
     * Compile the code blocks
     */
    public void compileCode() throws Exception {
        SourceInfo sourceInfo = new SourceInfo(grammar.name, "CODE");
        StringBuilder src = new StringBuilder();
        src.append(DEFAULT_IMPORTS).append("\n");
        src.append(grammar.imports).append("\n");

        for(String code : grammar.codeBlocks) {
            src.append(sourceInfo.addBlock(src.toString(), code));
        }

        try {
            grammarCode = groovyClassLoader.parseClass(src.toString(), grammar.name + "_code.groovy").newInstance();
        } catch(Exception e) {
            logger.error("Error while compiling code blocks of grammar %s", e, grammar.name);
            throw e;
        }
    }

    /**
     * Compile grammar interp actions
     */
    public void compileInterp() throws Exception {
        interp = new InterpAction[grammar.productions.size()];
        interpGenerators = new InterpCompiler.Generator[grammar.productions.size()];

        for(int i = 0; i < grammar.productions.size(); i++) {
            InterpAction.Source src = grammar.productions.get(i).interp;
            if(src != null) {
                interpGenerators[i] = interpCompiler.add(grammar, rules[i], src);
            }
        }

        // compile them
        try {
            interpCompiler.compile();
        } catch(Exception e) {
            logger.error("Error while compiling interp actions for grammar %s", grammar.name, e);
            throw e;
        }

        // set generated rules back


        for(int i = 0; i < interpGenerators.length; i++) {
            if(interpGenerators[i] != null) {
                interp[i] = interpGenerators[i].generate();
            }
        }
    }

    public CompiledGrammar copy() throws Exception {
        CompiledGrammar copy = new CompiledGrammar();
        copy.grammar = grammar;
        copy.pre = pre.getClass().newInstance();
        copy.pre.sourceInfo = pre.sourceInfo;
        copy.post = post.getClass().newInstance();
        copy.post.sourceInfo = post.sourceInfo;

        copy.symbols = symbols;
        copy.terminals = terminals;
        copy.output = output;
        copy.rules = rules;
        copy.groovyClassLoader = groovyClassLoader;

        copy.compiler = compiler;
        copy.interpCompiler = interpCompiler;


        copy.maxSynthSize = maxSynthSize; // maximum number of synth terminal by type

        copy.actionGenerators = actionGenerators;
        copy.interpGenerators = interpGenerators;
        copy.prefixes = prefixes;
        copy.prefixFSA = prefixFSA;
        copy.start = start;
        copy.lrStart = lrStart;
        copy.eof = eof;
        copy.accessors = accessors;


        copy.actions = new CompiledReduceAction[actions.length];
        for(int i = 0; i < actionGenerators.length; i++) {
            ReduceActionGenerator g = actionGenerators[i];
            copy.actions[i] = g != null? g.generate() : CompiledReduceAction.SIMPLE;
        }

        copy.interp = new InterpAction[interp.length];
        for(int i = 0; i < interpGenerators.length; i++) {
            InterpCompiler.Generator g = interpGenerators[i];
            if(g != null) {
                copy.interp[i] = g.generate();
            }
        }

        copy.predicates = new ArrayList<>();
        for(SymbolSpanPredicate p : predicates) {
            copy.predicates.add(p.copy());
        }

        copy.evaluators = new SynthTerminalEvaluator[evaluators.length];

        for(int i = 0; i < evaluators.length; i++) {
            if(evaluators[i] != null) {
                copy.evaluators[i] = evaluators[i].copy();
            }
        }


        return copy;
    }


    /**
     * Compute feature accessors used under equals/equalsIgnoreCase predicate
     * To be called after computePredInfo()
     */
    public void optimizeSynth() {
        BooleanFSABuilder fsaBuilder = new BooleanFSABuilder();

        for(int i = 0; i < evaluators.length; i++) {
            SynthTerminalEvaluator eval = evaluators[i];
            if(eval == null)
                continue;
            SynthTerminalEvaluator newEval = new SynthTerminalEvaluator(eval.type);
            evaluators[i] = newEval;

            for(int synthId = 0; synthId < eval.typeIds.size(); synthId++) {
                SymbolSpanPredicate pred = eval.predicates.get(synthId);

                // TODO: add not-equal handling
                if(pred instanceof SymbolSpanPredicates.Equal) {
                    SymbolSpanPredicates.Equal eq = (SymbolSpanPredicates.Equal) pred;
                    TIntArrayList l = new TIntArrayList();

                    FeatureAccessor fa = eq.fa;
                    int faId = accessors.get(fa);
                    int objId = fsaPredValues.get(eq.value);
                    eq.faId = faId;
                    eq.objId = objId;
                    l.add(faId);
                    l.add(objId);
                    l.add(eval.typeIds.get(synthId));
                    fsaBuilder.add(l);
                    if(!newEval.accessors.contains(faId)) {
                        newEval.accessors.add(faId);
                    }


                } else {
                    // copy
                    newEval.predIds.add(eval.predIds.get(synthId));
                    newEval.typeIds.add(eval.typeIds.get(synthId));
                    newEval.types.add(eval.types.get(synthId));
                    newEval.predicates.add(eval.predicates.get(synthId));
                }
            }


        }
        predFSA = fsaBuilder.build();
    }

    /**
     * Compute greedy policies.
     * Currently only 'typed' policies are supported. These are created
     * if any production has a greedy flag
     */
    public void computePolicies() {
        TIntHashSet set = new TIntHashSet();
        for(Rule r : rules) {
            if(r.production.greedy) {
                set.add(r.lhs);
            }
        }

        TIntIterator it = set.iterator();

        while(it.hasNext()) {
            int sym = it.next();
            policies.add(new GreedyPolicy.Type(sym));
        }
    }

    /**
     * Compile predicates specified directly in grammar code (in code blocks)
     */
    public void compileSourcePredicates() {

        Class[] returnTypes = new Class[] {Object.class, boolean.class, Boolean.class};

        outer:
        for(int i = 0; i < predicates.size(); i++) {
            SymbolSpanPredicate p = predicates.get(i);

            if(p instanceof SymbolSpanPredicates.SourceCodeSymbolSpanPredicate) {
                String id = ((SymbolSpanPredicates.SourceCodeSymbolSpanPredicate) p).feature;




                MethodHandles.Lookup lookup = MethodHandles.lookup();

                // foo(SymbolSpan)
                for(Class rt : returnTypes) {
                    try {
                        MethodType mt = MethodType.methodType(rt, SymbolSpan.class);
                        MethodHandle mh = lookup.findVirtual(grammarCode.getClass(), id, mt);
                        mh = mh.bindTo(grammarCode);
                        predicates.set(i, new SymbolSpanPredicates.MethodHandleSimple(mh));
                        continue outer;
                    } catch(NoSuchMethodException e) {
                        // empty
                    }

                    catch(Exception e) {
                        logger.error(e);
                    }
                }

                // foo(SymbolSpan, "foo")
                for(Class rt : returnTypes) {
                    try {
                        MethodType mt = MethodType.methodType(rt, SymbolSpan.class, String.class);
                        MethodHandle mh = lookup.findVirtual(grammarCode.getClass(), id, mt);
                        mh = mh.bindTo(grammarCode);
                        predicates.set(i, new SymbolSpanPredicates.MethodHandleSimpleName(mh, id));
                        continue outer;
                    } catch(NoSuchMethodException e) {
                        // empty
                    }

                    catch(Exception e) {
                        logger.error(e);
                    }
                }

                // foo(SymbolSpanPredicateEvaluator, SymbolSpan)
                for(Class rt : returnTypes) {
                    try {
                        MethodType mt = MethodType.methodType(rt, SymbolSpanPredicateEvaluator.class, SymbolSpan.class);
                        MethodHandle mh = lookup.findVirtual(grammarCode.getClass(), id, mt);
                        mh = mh.bindTo(grammarCode);
                        predicates.set(i, new SymbolSpanPredicates.MethodHandleFull(mh));
                        continue outer;
                    } catch(NoSuchMethodException e) {
                        // empty
                    } catch(Exception e) {
                        logger.error(e);
                    }
                }

                // foo(SymbolSpanPredicateEvaluator, SymbolSpan, "foo")
                // as previous, but with explicit method name
                for(Class rt : returnTypes) {
                    try {
                        MethodType mt = MethodType.methodType(rt, SymbolSpanPredicateEvaluator.class, SymbolSpan.class, String.class);
                        MethodHandle mh = lookup.findVirtual(grammarCode.getClass(), id, mt);
                        mh = mh.bindTo(grammarCode);
                        predicates.set(i, new SymbolSpanPredicates.MethodHandleFullName(mh, id));
                        continue outer;
                    } catch(NoSuchMethodException e) {
                        // empty
                    }catch(Exception e) {
                        logger.error(e);
                    }
                }

                // default case
                predicates.set(i, new SymbolSpanPredicates.NotNullFeaturePredicate(id));
            }
        }
    }




}
