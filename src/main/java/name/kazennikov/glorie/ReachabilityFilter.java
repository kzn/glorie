package name.kazennikov.glorie;

import org.apache.log4j.Logger;

import java.util.*;

/**
 * Grammar reachability filter.
 *
 * Removes all productions unreachable from the grammar start symbol.
 * Assumes that productions are already flattened out i.e. in BNF (without SymbolGroups)
 *
 */
public class ReachabilityFilter {
    private static final Logger logger = Logger.getLogger(ReachabilityFilter.class);
    Grammar g;

    public ReachabilityFilter(Grammar g) {
        this.g = g;
    }

    public Set<Symbol> reachable() {
        Map<Symbol, List<Production>> groups = new HashMap<>();

        for(Production p : g.productions) {
            List<Production> l = groups.get(p.lhs);
            if(l == null) {
                l = new ArrayList<>();
                groups.put(p.lhs, l);
            }
            l.add(p);
        }

        Set<Symbol> reachable = new HashSet<>();


        Deque<Symbol> q = new ArrayDeque<>();
        q.add(g.start);

        while(!q.isEmpty()) {
            Symbol s = q.pop();

            if(!reachable.add(s))
                continue;

            List<Production> l = groups.get(s);
            if(l == null)
                continue;

            for(Production p : l) {
                q.addAll(p.rhs);
            }
        }

        return reachable;
    }

    public void filter() {
        Set<Symbol> reachable = reachable();
        List<Production> l = new ArrayList<>();
        for(Production p : g.productions) {
            if(!reachable.contains(p.lhs))
                continue;

            l.add(p);
        }

        int filtered = g.productions.size() - l.size();
        if(filtered > 0) {
            logger.info(String.format("Filtered %d unreachable productions", filtered));
            g.productions = l;
        }
    }
}
