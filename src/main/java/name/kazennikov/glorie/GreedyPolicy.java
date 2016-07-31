package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Greedy policy.
 *
 * The policy is divided into two parts:
 * 1. The maximal production selection function. It selects a maximal performed reduction (symbol) from a list of
 *    reductions.
 * 2. A check procedure for a symbol node. The procedure may return any of three values:
 *    - REMOVE - this node is unconditionally removed
 *    - CONTINUE - this node has passed the check, but the check procedure should continue on other related nodes
 *    - ACCEPT - unconditionally accept this node. No further tree checks are performed on this node.
 */
public interface GreedyPolicy {

    public enum Result {
        REMOVE,
        CONTINUE,
        ACCEPT
    }


    /**
     * Choose maximal (in terms of the policy) reduction from the list
     * @param l list of reductions
     * @return
     */
    public GLRParser.PerformedReduction max(List<GLRParser.PerformedReduction> l);


    /**
     * Check if passed symbol node passes the checks WRT provided reduction
     * @param r reduction
     * @param v visitor to inner symbols
     * @param symbolNode inspected symbol node
     *
     * @return pass status
     */
    public Result check(GLRParser.PerformedReduction r, GreedyVisitor v, SymbolNode symbolNode);


    /**
     * Base policy.
     *
     * This policy accepts all nodes unconditionally by default
     */
    public abstract class Base implements GreedyPolicy {

        @Override
        public Result check(GLRParser.PerformedReduction r, GreedyVisitor v, SymbolNode n) {
            // terminals pass the filter unconditionally
            if(n.parseChildren.isEmpty())
                return Result.ACCEPT;

            return Result.CONTINUE;
        }
    }

    /**
     * 'Type' Greedy policy.
     *
     * The max reduction is defined as follows:
     * 1. The reduction must be of exactly match type with the policy
     * 2. The reduction must be marked as greedy
     * 3. The reduction should start as early as possible (the end of the reduction is fixed by current position)
     *
     * The check is defined as follows:
     * 1. ACCEPT all symbols that end before the maximal reduction
     * 2. REMOVE all symbols of the policy type that start after the current position
     * 3. allow co-staring greedy symbols from current position
     * 4. REMOVE the symbol node if all possible parses are filtered out.
     *
     *
     */
    public static class Type extends Base {

        int symbol;

        public Type(int symbol) {
            this.symbol = symbol;
        }

        @Override
        public GLRParser.PerformedReduction max(List<GLRParser.PerformedReduction> l) {

            // find longest greedy reduction
            GLRParser.PerformedReduction maxReduction = null;
            for(GLRParser.PerformedReduction r : l) {
                // skip other symbol types
                if(r.sym.symbol.symbol != symbol)
                    continue;

                // skip non-greedy reductions
                if(!r.rule.production.greedy)
                    continue;


                if(maxReduction == null) {
                    maxReduction = r;
                    continue;
                }


                if(r.sym.symbol.start < maxReduction.sym.symbol.start) {
                    maxReduction = r;
                }
            }

            return maxReduction;
        }

        @Override
        public Result check(GLRParser.PerformedReduction r, GreedyVisitor v, SymbolNode n) {
            if(super.check(r, v, n) == Result.ACCEPT)
                return Result.ACCEPT;

            // symbols that end before greedy reduction also pass the filter
            if(n.symbol.end <= r.sym.symbol.start)
                return Result.ACCEPT;


            boolean sameSymbol = n.symbol.symbol == r.sym.symbol.symbol;

            // for now, greediness is applied only for symbols of same type
            // for now, fail unconditionally any symbol that start after the greedy reduction start
            if(sameSymbol && (n.symbol.start > r.sym.symbol.start || n.symbol.end < r.sym.symbol.end))
                return Result.REMOVE;

            // for now we have only non-terminals that start before or strictly from greedy reduction start

            List<GLRParser.ParsingChildrenSet> parses = new ArrayList<>(n.parseChildren.size());


            boolean start = n.symbol.start == r.sym.symbol.start;

            for(GLRParser.ParsingChildrenSet parse : n.parseChildren) {
                CompiledGrammar.Rule rule = parse.rule;

                // if co-start && parse is greedy, pass
                if(start && sameSymbol) {
                    if(rule.production.greedy) {
                        parses.add(parse);
                    }

                    // skip non-greedy co-start
                    continue;
                }

                boolean pass = true;
                for(SymbolNode child : parse.items) {
                    if(v.visit(child) == null) {
                        pass = false;
                        break;
                    }
                }

                if(pass) {
                    parses.add(parse);
                }
            }

            n.parseChildren.clear();

            if(parses.isEmpty())
                return Result.REMOVE;

            n.parseChildren.addAll(parses);

            return Result.CONTINUE;
        }
    }
}
