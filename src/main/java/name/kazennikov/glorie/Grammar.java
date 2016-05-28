package name.kazennikov.glorie;

import com.google.common.base.Objects;
import gate.jape.constraint.EqualPredicate;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.fsa.BooleanFSABuilder;
import name.kazennikov.fsa.walk.WalkFSABoolean;

import java.util.*;

/**
 * Class to represent a GLR grammar as list of production rules.
 *
 * A grammar has:
 * a name;
 * a list of input annotation types;
 * a list of output symbol span types;
 * a map with options of this grammar;
 * a context annotation type;
 * a start symbol;
 * a list of productions;
 *
 *
 * a pre block that can modify the input symbol span sequence;
 * a post block that controls how resulting symbol spans are converted to annotations
 */
public class Grammar {


    String name; // grammar name
	Set<String> input = new HashSet<>();                // list of input types
    Set<String> output = new HashSet<>();               // list of output symbol names
    Map<String, String> options = new HashMap<>();      // grammar options
    String context;                                     // context annotation type

    Symbol lrStart;                                     // LR root symbol
	Symbol start;                                       // grammar root symbol

	List<Production> productions = new ArrayList<>();   // list of productions

    String imports;                                     // import block for groovy code
    String preSource;                                   // source code of 'pre' block
    String postSource;                                  // source code of 'post' block
	String preClassName;
	String postClassName;
    List<String> codeBlocks = new ArrayList<>();        // list of code blocks
    Map<String, RHSAction> macros = new HashMap<>();    // RHS macros
    PredInfo[] predInfos;


	int nextSynthProductionId = 0;
    int nextSynthTerminalId = 0;

    // alphabet of predicates, used to deduplicate the symbol span predicates
    Alphabet<SymbolSpanPredicate> predicates = new Alphabet<>();
    Map<String, SynthTerminalEvaluator> evaluators = new HashMap<>();

    WalkFSABoolean predFSA;
    Alphabet<Object> objectAlphabet = new Alphabet<>();
    Alphabet<FeatureAccessor> accessorAlphabet = new Alphabet<>();



    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("name", name)
                .add("start", start)
                .add("input", input)
                .add("productions", productions)
                .toString();
    }

    public void rewrite(ProductionRewriter r) {
    	List<Production> newProds = new ArrayList<>();

    	for(Production p : productions) {
    		for(Production newP : r.rewriter(this, p)) {
    			newProds.add(newP);
    		}
    	}

    	productions = newProds;
    }

    public Symbol makeSynthNT(int lineNum) {
    	String name = String.format("__%d_%05d", lineNum, ++nextSynthProductionId);
    	Symbol nt = new Symbol(name, true);
    	return nt;
    }

    public Symbol makeSynthTerminal() {
        String name = makeTerminalId();
        Symbol sym = new Symbol(name, false);
        return sym;
    }

    public String makeTerminalId() {
        return String.format("__T%05d", ++nextSynthTerminalId);
    }

    /**
     * Convert grammar to epsilon-free type
     */
    public void removeEpsilon() {
        Set<Symbol> eps = new HashSet<>();
        List<Production> toRewrite = new ArrayList<>();

        for(Production p : productions) {
            if(p.rhs.size() == 1 && p.rhs.get(0) == Symbol.EPSILON) {
                eps.add(p.lhs);
            } else {
                toRewrite.add(p);
            }
        }

        List<Production> prods = new ArrayList<>();

        for(Production p : toRewrite) {
            generateEpsilonFree(p, eps, new ArrayList<>(), 0, prods);
        }

        this.productions = prods;
    }

    public List<Production> generateEpsilonFree(Production p, Set<Symbol> eps, List<Symbol> buf, int pos, List<Production> out) {
        if(pos == p.rhs.size()) {
            if(!buf.isEmpty()) {
                Production p1 = new Production(p, p.lhs, new ArrayList<>(buf), p.synth, p.action, p.postProcessor, p.weight, p.greedy);
                out.add(p1);
            }
            return out;

        }

        Symbol s = p.rhs.get(pos);

        buf.add(s);
        generateEpsilonFree(p, eps, buf, pos + 1, out);
        buf.remove(buf.size() - 1);

        if(eps.contains(s)) {
            generateEpsilonFree(p, eps, buf, pos + 1, out);
        }

        return out;
    }


    /**
     * Add synth root for LR-table building
     */
    public void augmentGrammar() {
        Symbol newRoot = makeSynthNT(0);
        Production p = new Production(null, newRoot, Arrays.asList(start, Symbol.EOF), true, null, null, 1.0, false);
        productions.add(p);
        lrStart = newRoot;
    }

    /**
     * Compute list of predicates to check on a reduce parser action
     */
    public void computeReduceInfo() {
        int trueId = predicates.get(SymbolSpanPredicate.TRUE);

        for(Production p : productions) {
            for(Symbol s : p.rhs) {
                if(s.pred != null) {
                    p.preds.add(s.pred);
                    p.predIds.add(s.pred.compile(predicates));
                } else {
                    p.preds.add(SymbolSpanPredicate.TRUE);
                    p.predIds.add(trueId);
                }
            }
        }
    }

    /**
     * Rewrites top-level or of each production:
     *
     *
     * A -> B | C | D
     * =>
     * A -> B
     * A -> C
     * A -> D
     */
    public void rewriteTopLevelOr() {
        List<Production> prods = new ArrayList<>();

        for(Production p : productions) {
            if(p.rhs.size() == 1 && (p.rhs.get(0) instanceof SymbolGroup.Or)) {
                Symbol s = p.rhs.get(0);
                for(Symbol alt : ((SymbolGroup.Or) s).syms) {
                    Production newP = new Production(p, p.lhs, Arrays.asList(alt), p.synth, p.action, p.postProcessor, p.weight, p.greedy);
                    prods.add(newP);

                }
            } else {
                prods.add(p);
            }
        }

        productions = prods;
    }

    /**
     * Flattens top-level nested sequences
     * A -> B (C D)
     * A -> B C D
     */
    public void rewriteTopLevelSeq() {
        List<Production> prods = new ArrayList<>();

        for(Production p : productions) {
            List<Symbol> rhs = new ArrayList<>();

            for(Symbol elem : p.rhs) {
                if(elem instanceof SymbolGroup.Simple) {
                    rhs.addAll(((SymbolGroup.Simple) elem).syms);
                } else {
                    rhs.add(elem);
                }
            }
            Production newP = new Production(p, p.lhs, rhs, p.synth, p.action, p.postProcessor, p.weight, p.greedy);
            prods.add(newP);
        }

        productions = prods;

    }

    /**
     * Get used bindings by this grammar
     * @return set of binding names
     */
    public Set<String> bindings() {
        Set<String> bindings = new HashSet<>();

        for(Production p : productions) {
            bindings.addAll(p.bindings());
        }

        return bindings;
    }

    /**
     * Compute synth terminal evaluators.
     *
     * The procedure must be called after the symbol span predicates are compiled.
     * I.e. after the computeReduceInfo() call.
     *
     */
    public void computeEvaluators() {
        List<Production> prods = new ArrayList<>();

        for(Production p : productions) {
            // replace all terminals
            List<Symbol> syms = new ArrayList<>();
            TIntArrayList predIds = new TIntArrayList();
            List<SymbolSpanPredicate> preds = new ArrayList<>();

            int index = 0;
            for(Symbol s : p.rhs) {
                if(!s.nt && s.pred != null && s.pred != SymbolSpanPredicate.TRUE) {
                    SynthTerminalEvaluator eval = evaluators.get(s.id);
                    if(eval == null) {
                        eval = new SynthTerminalEvaluator(s.id);
                        evaluators.put(s.id, eval);
                    }

                    syms.add(eval.map(this, s));
                    predIds.add(1);
                    preds.add(SymbolSpanPredicate.TRUE);
                } else {
                    syms.add(s);
                    predIds.add(p.predIds.get(index));
                    preds.add(p.preds.get(index));

                }


                index++;
            }

            Production prod = new Production(p, p.lhs, syms, p.synth, p.action, p.postProcessor, p.weight, p.greedy);
            prod.rootIndex = p.rootIndex;
            prod.action = p.action;
            prod.predIds = predIds;
            prod.preds = preds;
            prod.bindings = p.bindings;
            prod.rootIndex = p.rootIndex;
            prods.add(prod);
        }

        productions = prods;
    }

    public void computePredInfo() {
        predInfos = new PredInfo[predicates.size() + 1];
        // initial fill
        for(int i = 0; i < predInfos.length; i++) {
            predInfos[i] = new PredInfo();
        }


        for(int i = 0; i < predicates.size(); i++) {
            SymbolSpanPredicate p1 = predicates.get(i + 1);

            for(int j = 0; j < predicates.size(); j++) {

                if(i == j)
                    continue;

                SymbolSpanPredicate p2 = predicates.get(j + 1);

                // foo == a, foo == b
                if(p1 instanceof SymbolSpanPredicates.Equal && p2 instanceof SymbolSpanPredicates.Equal) {
                    SymbolSpanPredicates.Equal ep1 = (SymbolSpanPredicates.Equal) p1;
                   SymbolSpanPredicates.Equal ep2 = (SymbolSpanPredicates.Equal) p2;

                    if(Objects.equal(ep1.fa, ep2.fa) && !Objects.equal(ep1.value, ep2.value)) {
                        predInfos[i].alsoFalse.add(j);
                        predInfos[j].alsoFalse.add(i);
                    }
                }

                // foo != a, foo == b
                if(p1 instanceof SymbolSpanPredicates.NotPredicate && ((SymbolSpanPredicates.NotPredicate) p1).pred instanceof EqualPredicate
                        && p2 instanceof SymbolSpanPredicates.Equal) {
                    SymbolSpanPredicates.Equal ep1 = (SymbolSpanPredicates.Equal) ((SymbolSpanPredicates.NotPredicate) p1).pred;
                    SymbolSpanPredicates.Equal ep2 = (SymbolSpanPredicates.Equal) p2;

                    if(Objects.equal(ep1.fa, ep2.fa) && !Objects.equal(ep1.value, ep2.value)) {
                        predInfos[i].converseFalse.add(j); // foo = a after all
                        predInfos[j].alsoTrue.add(i); // foo = b => foo != a
                    }
                }

                if(p1 instanceof SymbolSpanPredicates.EqualIgnoreCase && p2 instanceof SymbolSpanPredicates.EqualIgnoreCase) {
                    SymbolSpanPredicates.EqualIgnoreCase ep1 = (SymbolSpanPredicates.EqualIgnoreCase) p1;
                    SymbolSpanPredicates.EqualIgnoreCase ep2 = (SymbolSpanPredicates.EqualIgnoreCase) p2;

                    if(Objects.equal(ep1.fa, ep2.fa) && !Objects.equal(ep1.value, ep2.value)) {
                        predInfos[i].alsoFalse.add(j);
                        predInfos[j].alsoFalse.add(i);
                    }
                }

                // foo != a, foo == b
                if(p1 instanceof SymbolSpanPredicates.NotPredicate && ((SymbolSpanPredicates.NotPredicate) p1).pred instanceof SymbolSpanPredicates.EqualIgnoreCase
                        && p2 instanceof SymbolSpanPredicates.EqualIgnoreCase) {
                    SymbolSpanPredicates.EqualIgnoreCase ep1 = (SymbolSpanPredicates.EqualIgnoreCase) ((SymbolSpanPredicates.NotPredicate) p1).pred;
                    SymbolSpanPredicates.EqualIgnoreCase ep2 = (SymbolSpanPredicates.EqualIgnoreCase) p2;

                    if(Objects.equal(ep1.fa, ep2.fa) && !Objects.equal(ep1.value, ep2.value)) {
                        predInfos[i].converseFalse.add(j); // foo = a after all
                        predInfos[j].alsoTrue.add(i); // foo = b => foo != a
                    }
                }


                // foo, !foo
                if(p1 instanceof SymbolSpanPredicates.NotPredicate && Objects.equal(p2, ((SymbolSpanPredicates.NotPredicate) p1).pred)) {
                    predInfos[i].alsoFalse.add(j);
                    predInfos[j].alsoFalse.add(i);
                }

            }
        }

        for(int i = 0; i < predInfos.length; i++) {
            predInfos[i].alsoFalse = new TIntArrayList(new TIntHashSet(predInfos[i].alsoFalse));
            predInfos[i].alsoFalse.sort();

            predInfos[i].alsoTrue = new TIntArrayList(new TIntHashSet(predInfos[i].alsoTrue));
            predInfos[i].alsoTrue.sort();

            predInfos[i].converseTrue = new TIntArrayList(new TIntHashSet(predInfos[i].converseTrue));
            predInfos[i].converseTrue.sort();

            predInfos[i].converseFalse = new TIntArrayList(new TIntHashSet(predInfos[i].converseFalse));
            predInfos[i].converseFalse.sort();
        }
    }


    /**
     * Predicate optimization data.
     *
     * Sometimes, the evaluation result of one predicate can be used for other
     * predicates. Currently, this is used for equality/inequality predicates.
     * For example, if we checked that foo = "bar" and is holds true,
     * then we could also set the result of predicate foo = "baz" to false, as we
     * assume, that the equality predicate a categorical predicate i.e. "1 of n" and can
     * take only one value simultaneously.
     */
    public static class PredInfo {
        TIntArrayList alsoTrue = new TIntArrayList(); // if x true, then these also true
        TIntArrayList alsoFalse = new TIntArrayList(); // if x true, the these are false
        TIntArrayList converseTrue = new TIntArrayList(); // if x false, then these also true
        TIntArrayList converseFalse = new TIntArrayList();  // if x false, then these are false
        boolean fsa; // is in fsa predicate
        int fa; // feature accessor index in fa alphabet

    }

    /**
     * Build FSA for categorical predicates. The optimization is that
     * if some predicate is categorical (1 of n), then we can build a FSA on strings
     * {featureAccessorId, valueId, predicateId}, so instead of checking at most n, we
     * check 1 value through the FSA and find the predicate that holds true on this value
     */
    public void computePredFSA() {
        BooleanFSABuilder builder = new BooleanFSABuilder();
        for(int i = 0; i < predicates.size(); i++) {
            SymbolSpanPredicate pred = predicates.get(i + 1);
            if(pred instanceof SymbolSpanPredicates.Equal) {


                SymbolSpanPredicates.Equal equal = (SymbolSpanPredicates.Equal) pred;
                int faId = accessorAlphabet.get(equal.fa);
                int objId = objectAlphabet.get(equal.value);

                TIntArrayList l = new TIntArrayList();
                l.add(faId);
                l.add(objId);
                l.add(i + 1); // predicate id
                builder.add(l);
                predInfos[i].fsa = true;
                predInfos[i].fa = faId;
            }
        }

        predFSA = builder.build();
    }


	/**
	 * Transform non-terminals with predicates to standalone productions:
	 * X -> Y[...] Z
	 * =>
	 * X -> Y__01 Z
	 * Y__01 -> Y [....]
	 */
	public void transformPredicateNT() {
		List<Production> prods = new ArrayList<>();

		List<Symbol> srcSym = new ArrayList<>();
		List<Symbol> dstSym = new ArrayList<>();

		// add predefined single non-terminal production
		for(Production p : productions) {
			if(p.rhs.size() == 1) {
				prods.add(p);

				// skip those with non-trivial RHS Action
				if(p.action == null) {
					srcSym.add(p.rhs.get(0));
					dstSym.add(p.lhs);
				}
			}
		}

		for(Production p : productions) {

			if(p.rhs.size() == 1 && p.action == null) {
				continue;
			}

			List<Symbol> rhs = new ArrayList<>();
			Production newP = new Production(p.parent, p.lhs, rhs, p.synth, p.action, p.postProcessor, p.weight, p.greedy);

			for(Symbol s : p.rhs) {
				if(!s.nt) {
					rhs.add(s);
					continue;
				}

				if(s.pred == SymbolSpanPredicate.TRUE) {
					rhs.add(s);
					continue;
				}


				int index = 0;
				while(index < srcSym.size()) {
					Symbol src = srcSym.get(index);
					if(src.equals(s) && Objects.equal(src.pred, s.pred)) {
						break;
					}
					index++;
				}

				if(index == srcSym.size()) {
					srcSym.add(s);
					Symbol dest = makeSynthNT(p.sourceLine);
					dstSym.add(dest);
					prods.add(new Production(p.parent, dest, Arrays.asList(s), true, null, null, 1.0, false));
				}
				Symbol dest = dstSym.get(index);
				Symbol sym = new Symbol(dest.id, dest.nt, SymbolSpanPredicate.TRUE);
				sym.labels.addAll(s.labels);
				rhs.add(sym);

			}

			prods.add(newP);
		}

		productions = prods;
	}


}