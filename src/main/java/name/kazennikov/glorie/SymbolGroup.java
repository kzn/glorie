package name.kazennikov.glorie;

import com.google.common.base.MoreObjects;

import java.util.ArrayList;
import java.util.List;

/**
 * A base class for a group of symbols in an RHS of a production
 */
public abstract class SymbolGroup extends Symbol {
	protected List<Symbol> syms = new ArrayList<>();

    public SymbolGroup() {
        super(null, true, null);
    }

    public void add(Symbol sym) {
		syms.add(sym);
	}
	
	public static class Or extends SymbolGroup {
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.add("syms", syms)
					.toString();
		}

	}

	/**
	 * A repeated group of symbols.
	 *
	 * if max = Integer.MAX_VALUE, then the group can be repeated infinitely
	 * else, it is repeated specified number of times
	 *
	 */
	public static class Range extends SymbolGroup {
		int min;
		int max;
		
		public Range() {
			
		}
		
		public Range(int min, int max) {
			this.min = min;
			this.max = max;
		}
		
		public int getMin() {
			return min;
		}

		public void setMin(int min) {
			this.min = min;
		}

		public int getMax() {
			return max;
		}

		public void setMax(int max) {
			this.max = max;
		}

		public List<Symbol> getSyms() {
			return syms;
		}
		
		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.add("min", min)
					.add("max", max)
					.add("syms", syms)
					.toString();
		}
	}

	/**
	 * Simple group that represents a sequence of symbols
	 */
    public static class Simple extends SymbolGroup {

    }
	

}
