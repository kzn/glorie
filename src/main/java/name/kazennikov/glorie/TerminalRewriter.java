package name.kazennikov.glorie;

import java.util.*;

/**
 * Created on 11.12.15.
 *
 * @author Anton Kazennikov
 */
public class TerminalRewriter implements ProductionRewriter {

	public static class SymbolRec {
		Symbol sym;
		SymbolSpanPredicate pred;

		public SymbolRec(Symbol sym, SymbolSpanPredicate pred) {
			this.sym = sym;
			this.pred = pred;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o)
				return true;

			if(o == null || getClass() != o.getClass())
				return false;

			SymbolRec symbolRec = (SymbolRec) o;

			return Objects.equals(sym, symbolRec.sym) &&
					Objects.equals(pred, symbolRec.pred);
		}

		@Override
		public int hashCode() {
			return Objects.hash(sym, pred);
		}

		@Override
		public String toString() {
			return "SymbolRec{" +
					"sym=" + sym +
					", pred=" + pred +
					'}';
		}
	}


	Map<SymbolRec, Symbol> ntmap = new HashMap<>();



	@Override
	public List<Production> rewriter(Grammar g, Production p) {
		List<Symbol> rhs = new ArrayList<>();

		List<Production> out = new ArrayList<>();

		for(Symbol s : p.rhs) {
			if(!s.nt && (s.pred != null && s.pred != SymbolSpanPredicate.TRUE)) {
				SymbolRec rec = new SymbolRec(s, s.pred);
				Symbol newS = ntmap.get(rec);

				if(newS == null) {
					newS = g.makeSynthNT(p.lhs.id, p.sourceLine);
					List<Symbol> rhs1 = new ArrayList<>();
					rhs1.add(s);
					out.add(new Production(p, newS, rhs1, true, null, null, 1.0, false));
					ntmap.put(rec, newS);
				}

				Symbol ss = new Symbol(newS.id, newS.nt, newS.pred);
				ss.labels.addAll(s.labels);
				rhs.add(ss);

			} else {
				rhs.add(s);
			}
		}

		Production prod = new Production(p, p.lhs, rhs, p.synth, p.action, p.interp, p.weight, p.greedy);
		out.add(prod);

		return out;
	}


}
