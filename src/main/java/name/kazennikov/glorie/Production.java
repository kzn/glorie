package name.kazennikov.glorie;

import java.util.*;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import gate.creole.ResourceInstantiationException;
import gnu.trove.list.array.TIntArrayList;

/**
 * A production of RHS grammar
 */
public class Production {

    /**
     * Represents a binding in the RHS
     */
    public static class BindingInfo {
        String name;                                        // binding name
        boolean type;                                       // type, true if the binding value is one of RHS symbols and not in them
        TIntArrayList path = new TIntArrayList();           // index-based path to extract binding value

        public BindingInfo(String name, boolean type, TIntArrayList path) {
            this.name = name;
            this.type = type;
            this.path = path;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("type", type)
                    .toString();
        }
    }



    Symbol lhs;                                         // left-hand side
	double weight = 1.0;								// apriori weight of the target symbol of this production
	List<? extends Symbol> rhs = new ArrayList<>();     // right-hand side
	boolean synth;                                      // true, if production was created during rewriting

    List<SymbolSpanPredicate> preds = new ArrayList<>(); // predicates to check
    TIntArrayList predIds = new TIntArrayList();         // predicate id's
    RHSAction action;                                    // action to execute on reduce GLR action
    SymbolNodePostProcessor.Source postProcessor;               // symbol node post-processor

    Production parent;                                   // parent production, set during rewriting, so a production could be traced to original rule
    int rootIndex;                                       // index of the root symbol
    List<BindingInfo> bindings;                          // binding data
	boolean greedy;
    int sourceLine = -1;                                 // source line number

	
	public Production(Production parent, Symbol lhs, List<? extends Symbol> rhs, boolean synth, RHSAction action, SymbolNodePostProcessor.Source postProcessor, double weight, boolean greedy) {
		this.parent = parent;
        this.lhs = lhs;
		this.rhs = rhs;
        this.synth = synth;
        this.action = action;
        this.postProcessor = postProcessor;
		this.weight = weight;
		this.greedy = greedy;

        if(parent != null) {
            sourceLine = parent.sourceLine;
        }
	}


    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("synth", synth)
                .add("lhs", lhs)
                .add("rhs", rhs)
                .toString();
    }

    private static class ExtProd {
        Production p;
        TIntArrayList path;

        ExtProd(Production p, TIntArrayList path) {
            this.p = p;
            this.path = path;
        }
    }


    /**
     * Extract binding names of this production
     * @return
     */
    public Set<String> bindings() {
        Set<String> bindings = new HashSet<>();
        IdentityHashMap<Symbol, Symbol> visited = new IdentityHashMap<>();
        ArrayDeque<Symbol> symbols = new ArrayDeque<>();
        symbols.addAll(rhs);

        while(!symbols.isEmpty()) {
            Symbol s = symbols.pollFirst();
            if(visited.containsKey(s))
                continue;
            visited.put(s, s);
            if(s instanceof SymbolGroup) {
                symbols.addAll(((SymbolGroup) s).syms);
            }

            if(s.labels != null) {
                for(String label : s.labels) {
                    if(label != null)
                        bindings.add(label);
                }
            }
        }

        return bindings;
    }

    /**
     * Computes binding info wrt grammar
     * The grammar is needed as if a binding is contained in another production then there could be several ways to
     * represent it
     *
     * @param g grammar
     */
    public void bindings(Grammar g) {


        bindings = new ArrayList<>();
        ArrayDeque<ExtProd> q = new ArrayDeque<>();
        Set<Production> visited = new HashSet<>();

        q.add(new ExtProd(this, new TIntArrayList()));

        while(!q.isEmpty()) {
            ExtProd p = q.pollLast();
            if(visited.contains(p.p))
                continue;

            visited.add(p.p);

            for(int i = 0; i < p.p.rhs.size(); i++) {
                Symbol s = p.p.rhs.get(i);

                for(String label : s.labels) {
                    if(label != null) {
                        TIntArrayList path = new TIntArrayList(p.path);
                        path.add(i);
                        bindings.add(new BindingInfo(label, this == p.p, path));
                    }
                }

                if(s.nt) {
                    for(Production p1 : g.productions) {
                        if(visited.contains(p1))
                            continue;

                        if(p1.synth && p1.lhs.id.equals(s.id)) {
                            TIntArrayList path = new TIntArrayList(p.path);
                            path.add(i);
                            q.addLast(new ExtProd(p1, path));
                        }
                    }
                }

            }
        }
    }


    /**
     * Validate all binding of the RHS
     * @throws ResourceInstantiationException
     */
    public void validateBindings() throws ResourceInstantiationException {
        for(Symbol s : rhs) {
            validateBindings(s, new ArrayList<Symbol>());
        }
    }

    /**
     * Validate binding
     * It is not allowed to bind a symbol under the unbounded repetition
     * @param s
     * @param stack
     * @throws ResourceInstantiationException
     */
    private void validateBindings(Symbol s, ArrayList<Symbol> stack) throws ResourceInstantiationException {
        if(s instanceof SymbolGroup) {
            stack.add(s);
            for(Symbol child : ((SymbolGroup) s).syms) {
                validateBindings(child, stack);
            }
            stack.remove(stack.size() - 1);
        } else {
            String label = null;
            for(String l : s.labels) {
                if(l != null) {
                    label = l;
                    break;
                }
            }

            if(label != null) {
                for(Symbol head : stack) {
                    if(head instanceof SymbolGroup.Range) {
                        SymbolGroup.Range range = (SymbolGroup.Range) head;
                        if(range.max == Integer.MAX_VALUE) {
                            throw new ResourceInstantiationException("Invalid label '" + label + "' in rule " + rootParent());
                        }
                    }
                }
            }
        }
    }


    /**
     * Find the most distant parent (a production that in the source, pre-transformed grammar)
     * @return
     */
    public Production rootParent() {
        Production p = this;
        while(p.parent != null) {
            p = p.parent;
        }

        return p;
    }

    /**
     * Initialize root index
     */
    public void findRootIndex() {
        rootIndex = 0;
        for(int i = 0; i < rhs.size(); i++) {
            if(rhs.get(i).root) {
                rootIndex = i;
                break;
            }
        }
    }

    public BindingInfo getBindingInfo(String label) {
        for(BindingInfo info : bindings) {
            if(info.name.equals(label))
                return info;
        }



        return null;
    }

    public Set<String> bindingsNames() {
        Set<String> set = new HashSet<>();

        for(BindingInfo info : bindings) {
            set.add(info.name);
        }

        return set;
    }
}