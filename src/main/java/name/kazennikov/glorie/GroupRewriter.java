package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Group rewrite:
 *
 *  A -> B (C | D) E
 * to:
 * A -> B _001 E
 * _001 -> C | D
 *
 *
 * A -> B (C D)+ E
 * to:
 * A -> B _001 E
 * _001 -> (C D)+
 */
public class GroupRewriter implements ProductionRewriter {

	@Override
	public List<Production> rewriter(Grammar g, Production p) {
		List<Production> prods = new ArrayList<>();
		List<Symbol> rhs = new ArrayList<>();

        Production newP = new Production(p, p.lhs, rhs, p.synth, p.action, p.postProcessor, p.weight, p.greedy);
		prods.add(newP);
		rewrite(p, g, p.rhs, rhs, prods);
		return prods;
	}
	
	
	public List<Symbol> rewrite(Production p, Grammar g, List<? extends Symbol> syms, List<Symbol> rhs, List<Production> productions) {
		for(Symbol s : syms) {

            Symbol out = s;

			if(s instanceof SymbolGroup.Or) {
				SymbolGroup.Or or = new SymbolGroup.Or();
				or.syms = rewrite(p, g, ((SymbolGroup) s).syms, new ArrayList<Symbol>(), productions);
				Symbol nt = g.makeSynthNT();
                for(Symbol ss : or.syms) {
                    productions.add(new Production(p, nt, Arrays.asList(ss), true, null, null, 1.0, false));
                }
				out = nt;
                out.root = s.root;
                out.labels.addAll(s.labels);
			} else if(s instanceof SymbolGroup.Range) {
				SymbolGroup.Range r = new SymbolGroup.Range(((SymbolGroup.Range) s).min, ((SymbolGroup.Range) s).max);
				r.syms = rewrite(p, g, ((SymbolGroup) s).syms, new ArrayList<Symbol>(), productions);
				Symbol nt = g.makeSynthNT();
				productions.add(new Production(p, nt, Arrays.asList((Symbol)r), true, null, null, 1.0, false));
				out = nt;
                out.root = s.root;
                out.labels.addAll(s.labels);
			}

            rhs.add(out);
		}
		
		return rhs;
	}

}
