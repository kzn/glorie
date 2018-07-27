package name.kazennikov.glorie;

import gate.FeatureMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
* Symbol span. A core concept of the GLR parser.
 *
 * A Symbol span is a continuous span in the source documents extended with type and features.
 * It maps a span of text to a symbol (either terminal or non-terminal) in terms of GLR parser.
 *
 * A symbol span is formed by:
 * * symbol - an identifier of the type used by the GLR parser
 * * start - start offset
 * * end - end offset
 * * type - named type of the span (at present the type is a 1:1 match with the symbol id)
 * * id - unique span identifier
 * * features - span features
 * * head - span head. A head is the "master" underlying span on which the current span is built. Only non-terminals have non-null heads
 * * data - arbitrary data that could be associated with the span
 *
*/
public class SymbolSpan {

    /**
     * Compare symbols spans: ascending starts, then descending ends
     */
    public static final Comparator<SymbolSpan> COMPARATOR = new Comparator<SymbolSpan>() {
        @Override
        public int compare(SymbolSpan o1, SymbolSpan o2) {
            int res = o1.start - o2.start;

            if(res != 0)
                return res;

            res = o2.end - o1.end;

            if(res != 0)
                return res;

            return o1.symbol - o2.symbol;
        }
    };


    public final int symbol;        // grammar symbol id
    public final int start;         // start offset of the span
    public final int end;           // end offset of the span

    public final String type;       // span type
    public int id;                  // span id
    public FeatureMap features;     // span features
    public SymbolSpan head;         // span head
    public Object data;             // interp data (assigned at postprocessing)
	public double weight;			// weight of the symbol span


    public SymbolSpan(int symbol, String type, int id, int start, int end, FeatureMap features, Object data, double weight) {
        this.symbol = symbol;
        this.type = type;
        this.id = id;
        this.start = start;
        this.end = end;
        this.features = features;
        this.data = data;
        this.head = this;
		this.weight = weight;
    }

    @Override
    public String toString() {
        return String.format("%d(%s,id=%d)[%d,%d]%s", symbol, type, id, start, end, features);
    }

    /**
     * Get all parents of the span. Returns the parent list from the direct parent.
     * Excludes the span itself
     */
    public List<SymbolSpan> parents() {
        List<SymbolSpan> parents = new ArrayList<>();
        SymbolSpan s = this;

        while(s.head != s) {
            parents.add(s.head);
            s = s.head;
        }

        return parents;
    }

    /**
     * Return n-th head of the symbol
     *
     * @param n order of the head
     * @return n-th order head or null, if there is no such head
     */
    public SymbolSpan head(int n) {
        SymbolSpan s = this;

        for(int i = 0; i < n; i++) {
            if(s.head == s)
                return null;

            s = s.head;
        }

        return s;
    }

    /**
     * Check this span and given span are coextensive
     * @param span span to check against
     */
    public boolean coextensive(SymbolSpan span) {
        return start == span.start && end == span.end;
    }

    /**
     * Check if this span contains given span
     * @param span span to check against
     */
    public boolean contains(SymbolSpan span) {
        return start <= span.start && end >= span.end;
    }

    /**
     * Check if this span is within given span
     * @param span span to check against
     */
    public boolean within(SymbolSpan span) {
        return span.start <= start && span.end >= end;
    }

    /**
     * Check if this span is left of given span
     * @param span span to check against
     */
    public boolean leftOf(SymbolSpan span) {
        return start < span.start;
    }

    /**
     * Check if this span is right of given span
     * @param span span to check against
     */
    public boolean rightOf(SymbolSpan span) {
        return span.start < start;
    }

    /**
     * Check if this span overlaps with given span
     * @param span span to check against
     */
    public boolean overlaps(SymbolSpan span) {
        return (start <= span.start && end >= span.start)
                ||
                (start <= span.end && end >= span.end);
    }

	/**
	 * Length of span in characters
	 */
	public int length() {
		return end - start;
	}

	@Override
	public int hashCode() {
		return Objects.hash(start, end, symbol, weight);
	}

	@Override
	public boolean equals(Object o) {
		if(o == this)
			return true;

		if(o == null)
			return false;

		if(!(o instanceof SymbolSpan)) {
			return false;
		}

		SymbolSpan other = (SymbolSpan) o;

		if(other.start != start)
			return false;

		if(other.end != end)
			return false;

		if(other.symbol != symbol)
			return false;

		if(other.head != head)
			return false;

		if(other.weight != weight)
			return false;

		return other.features == features || Objects.equals(other.features, features);
	}
}
