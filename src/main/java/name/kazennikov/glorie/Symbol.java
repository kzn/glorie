package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a symbol occurrence in the GLR Grammar rules.
 *
 * A symbol can be either a terminal or non-terminal. A terminal symbol denotes an annotation in the
 * input annotation list. A non-terminal symbol is constructed during GLR parsing.
 * If the symbol is non-terminal, then the 'nt' field is true.
 *
 * A symbol consists of the id and that attached predicates, so
 * [Token: string == foo] is parsed into a symbol those id is "Token" and predicate is "string == foo"
 *
 * A symbol also contains a list of label associated with it.
 * A symbol could be marked as root indicating that it is a head symbol in a production. A head usually
 * means that the reduced non-terminal inherits all root properties.
 *
 */
public class Symbol {
    /**
     * EPSILON symbol. Means that the input could be empty
     */
    public static final Symbol EPSILON = new Symbol("EPSILON", false);

    /**
     * EOF symbol. represents end of input
     */
    public static final Symbol EOF = new Symbol("EOF", false);



    public final String id;                                  // symbol id
    public final SymbolSpanPredicate pred;                   // additional constraints of the span
    public List<String> labels = new ArrayList<>();          // labels of the symbol
    public final boolean nt;                                 // true, if the symbol is non-terminal
    public boolean head;                                     // true, if symbol is marked as head of production in RHS


    public Symbol(String id, boolean nt) {
        this(id, nt, SymbolSpanPredicate.TRUE);
    }

    public Symbol(String id, boolean nt, SymbolSpanPredicate pred) {
        this.id = id;
        this.nt = nt;
        this.pred = pred;
    }

	
	
	@Override
	public String toString() {
        return id;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(id, nt);
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof Symbol))
			return false;
		Symbol o = (Symbol) obj;
		
		return Objects.equals(nt, o.nt) && Objects.equals(id, o.id);
	}
}
