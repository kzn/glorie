package name.kazennikov.glorie;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * Generator for synthetic terminals from real input terminals
 * It maps [Token: kind == number] to __T002, so the predicate evaluation
 * is done when computing the input data structure and not on reduces
 *
 * @author Anton Kazennikov
 */
public class SynthTerminalEvaluator {
	// input type
	public final String type;
	// input type id
	public int typeId = -1;

	// predicate[i] yields terminal with type types[i]
	public final List<SymbolSpanPredicate> predicates = new ArrayList<>();
	public final List<String> types = new ArrayList<>();

	// compiled predicates and types: predIds[i] yields terminal with symbol id typeIds[i]
	public final TIntArrayList typeIds = new TIntArrayList();
	public final TIntArrayList predIds = new TIntArrayList();

    public final TIntArrayList accessors = new TIntArrayList();


	public SynthTerminalEvaluator(String type) {
		this.type = type;
	}

	/**
	 * Add new type-predicate pair
	 * @param type target symbol type
	 * @param pred predicate
	 */
	public void add(String type, SymbolSpanPredicate pred) {
		types.add(type);
		predicates.add(pred);
	}

	public int size() {
		return types.size();
	}


	/**
	 * Map a terminal with predicate to synth terminal
	 * @param g working grammar
	 * @param src source symbol
	 * @return mapped terminal
	 */
	public Symbol map(Grammar g, Symbol src) {
		if(src.pred == null || src.pred == SymbolSpanPredicate.TRUE)
			return src;

		int index = predicates.indexOf(src.pred);


		if(index == -1) {
			index = predicates.size();
			predicates.add(src.pred);
			types.add(src.id + "_" + g.makeTerminalId());
		}

		String id = types.get(index);

		Symbol s = new Symbol(id, false);
		s.labels.addAll(src.labels);

		return s;
	}

	public SynthTerminalEvaluator copy() {
	    SynthTerminalEvaluator copy = new SynthTerminalEvaluator(type);
	    copy.typeId = typeId;

	    copy.types.addAll(types);
	    copy.typeIds.addAll(typeIds);
	    copy.predIds.addAll(predIds);
	    copy.accessors.addAll(accessors);

	    for(SymbolSpanPredicate p : predicates) {
	        copy.predicates.add(p.copy());
        }

        return copy;
    }
}
