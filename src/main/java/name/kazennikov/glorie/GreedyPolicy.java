package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kzn on 3/17/16.
 */
public interface GreedyPolicy {

    enum Result {
        REMOVE,
        CONTINUE,
        ACCEPT
    }

    public GLRParser.PerformedReduction max(List<GLRParser.PerformedReduction> l);
    public Result check(GLRParser.PerformedReduction r, GreedyVisitor v, SymbolNode symbolNode);


    public abstract class Base implements GreedyPolicy {

        @Override
        public Result check(GLRParser.PerformedReduction r, GreedyVisitor v, SymbolNode n) {
            // terminals pass the filter unconditionally
            if(n.parseChildren.isEmpty())
                return Result.ACCEPT;

            return Result.CONTINUE;
        }
    }

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
