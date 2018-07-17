package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rewrite:
 * A -> B | C | D
 * to:
 * A -> B
 * A -> C
 * A -> D
 */
public class OrProductionRewriter implements ProductionRewriter {

	@Override
	public List<Production> rewriter(Grammar g, Production p) {
		if(p.rhs.size() != 1)
			return Arrays.asList(p);
		Symbol rhs = (Symbol) p.rhs.get(0);

		if(rhs instanceof SymbolGroup.Or) {
			List<Symbol> syms = expandOr((SymbolGroup.Or) rhs, new ArrayList<Symbol>());
			List<Production> l = new ArrayList<>();
			for(Symbol s : syms) {

				Production newP = new Production(p, p.lhs, Arrays.asList(s), p.synth, p.action, p.interp, p.weight, p.greedy);
				l.add(newP);
			}
			
			return l;
		}
		
		return Arrays.asList(p);
	}
	
	
	List<Symbol> expandOr(SymbolGroup.Or group, List<Symbol> syms) {
		for(Symbol s : group.syms) {
			if(s instanceof SymbolGroup.Or) {
				expandOr((SymbolGroup.Or)s, syms);
			} else {
				syms.add(s);
			}
		}
		
		return syms;
	}
	

}
