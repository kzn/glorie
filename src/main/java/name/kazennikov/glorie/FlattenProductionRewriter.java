package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flattens SymbolGroups
 * A -> B (C (D E)) F => A -> B (C D E F)
 * A -> B (C | D | (E | F)) => A -> B (C | D | E | F)
 */
public class FlattenProductionRewriter implements ProductionRewriter {

    @Override
    public List<Production> rewriter(Grammar g, Production p) {
        List<Symbol> rhs = new ArrayList<>(p.rhs);
        List<Symbol> out = new ArrayList<>();

        for(Symbol s : rhs) {
            out.add(rewrite(s));
        }


        Production newP = new Production(p, p.lhs, out, p.synth, p.action, p.interp, p.weight, p.greedy);
        return Arrays.asList(newP);
    }

    Symbol rewrite(Symbol s) {
        if(s instanceof SymbolGroup.Simple) {
            List<Symbol> out = new ArrayList<>();
            for(Symbol ss : ((SymbolGroup.Simple) s).syms) {

                Symbol newSS = rewrite(ss);
                if(newSS instanceof SymbolGroup.Simple)
                    out.addAll(((SymbolGroup.Simple) newSS).syms);
                else
                    out.add(newSS);

            }
            SymbolGroup.Simple sym = new SymbolGroup.Simple();
            sym.syms = out;
            sym.root = s.root;
            sym.labels.addAll(s.labels);

            if(out.size() == 1) {
                Symbol ss = out.get(0);
                ss.labels.addAll(s.labels);
                ss.root = sym.root;
                return ss;
            }

            return sym;


        } else if(s instanceof SymbolGroup.Or) {
            List<Symbol> out = new ArrayList<>();
            for(Symbol ss : ((SymbolGroup.Or) s).syms) {

                Symbol newSS = rewrite(ss);
                if(newSS instanceof SymbolGroup.Or)
                    out.addAll(((SymbolGroup.Or) newSS).syms);
                else
                    out.add(newSS);

            }
            SymbolGroup.Or sym = new SymbolGroup.Or();
            sym.syms = out;
            sym.root = s.root;
            sym.labels.addAll(s.labels);

            if(out.size() == 1) {
                Symbol ss = out.get(0);
                ss.labels.addAll(sym.labels);
                ss.root = sym.root;
                return ss;
            }

            return out.size() == 1? out.get(0) : sym;


        } else if(s instanceof SymbolGroup) {
            List<Symbol> out1 = new ArrayList<>();
            for(Symbol ss : ((SymbolGroup) s).syms) {
                out1.add(rewrite(ss));
            }

            ((SymbolGroup) s).syms = out1;
            return s;
        } else {
            return s;
        }
    }



}
