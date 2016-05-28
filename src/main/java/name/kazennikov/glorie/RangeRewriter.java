package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Range operator rewriter.
 *
 * Rewrites:
 * A*, A+, and A[2,3]
 * Rewrite:
 * A -> (D)*
 * to:
 *  _001 -> D
 * A -> [eps]
 * A -> _001 A
 *
 * A -> (D)+
 * to:
 * _001 -> D
 * A -> _001 A
 *
 * Rewrite
 * A -> D[2,3]
 * to:
 * A -> D D
 * A -> D D D
 */
public class RangeRewriter implements ProductionRewriter {

	@Override
	public List<Production> rewriter(Grammar g, Production p) {

		if(p.rhs.size() == 1 && (p.rhs.get(0) instanceof SymbolGroup.Range)) {
			List<Production> l = new ArrayList<>();
			SymbolGroup.Range r = (SymbolGroup.Range) p.rhs.get(0);
			Symbol baseLHS = g.makeSynthNT(p.sourceLine);
			Production base = new Production(p, baseLHS, new ArrayList<>(r.syms), true, null, null, 1.0, false);
			l.add(base);

			if(r.min == 0) {
				l.add(new Production(p, p.lhs, Arrays.asList(Symbol.EPSILON), p.synth, null, null, 1.0, false));
			}

			for(int i = 1; i <= r.min; i++) {
				List<Symbol> l1 = new ArrayList<>();

				for(int j = 0; j < i; j++) {
					l1.add(baseLHS);
				}


				l.add(new Production(p, p.lhs, l1, p.synth, p.action, p.postProcessor, p.weight, p.greedy));
			}

			if(r.max == Integer.MAX_VALUE) {
				l.add(new Production(p, p.lhs, Arrays.asList(p.lhs, baseLHS), p.synth, p.action, p.postProcessor, p.weight, p.greedy));
			} else {
				for(int i = r.min + 1; i <= r.max; i++) {
					List<Symbol> l1 = new ArrayList<>();

                    for(int j = 0; j < i; j++) {
						l1.add(baseLHS);
					}

					l.add(new Production(p, p.lhs, l1, p.synth, p.action, p.postProcessor, p.weight, p.greedy));
				}
			}
			
			return l;
		}
		
		return Arrays.asList(p);
	}
	

}
