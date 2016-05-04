package name.kazennikov.glorie.func;

import name.kazennikov.features.*;
import name.kazennikov.glorie.SymbolSpan;
import name.kazennikov.glorie.SymbolSpanPredicateEvaluator;

import java.util.*;

/**
 * Dynamic function evaluator to retrieve computed value from given symbol span
 *
 * The Evaluator memoizes intermediate value for reducing computation time
 *
 * @author Anton Kazennikov
 */
public class Evaluator {
	Values.Var symbolSpan = new Values.Var();
	Values.Var predicateEvaluator = new Values.Var();

	// values to clear before next evaluation
	List<MemoizedValue> toClear = new ArrayList<>();

	// set of functions, used for deduplication
	Map<MemoizedValue, MemoizedValue> funSet = new HashMap<>();

	// list of function rewriters
	List<FunctionRewriter> rewriters = new ArrayList<>();

	// placeholder for target value
	public final MemoizedValue f;

	public Evaluator(List<FunctionRewriter> pre, List<FunctionRewriter> post, MemoizedValue f) {
		if(pre != null) {
			rewriters.addAll(pre);
		}

		rewriters.add(new ValueInjector(symbolSpan, SymbolSpanInjectable.class));
		rewriters.add(new ValueInjector(predicateEvaluator, PredicateEvaluatorInjectable.class));

		if(post != null) {
			rewriters.addAll(post);
		}

		this.f = minimize(rewrite(f));
	}

	public Evaluator(MemoizedValue f) {
		this(null, null, f);
	}



	/**
	 * Evaluate stored function graph wrt input parameters
	 *
	 * @param eval input data
	 * @param symbolSpan symbol span object
	 * @return computed value
	 */
	public Object eval(SymbolSpanPredicateEvaluator eval, SymbolSpan symbolSpan) {
		clear();
		this.symbolSpan.set(symbolSpan);
		this.predicateEvaluator.set(eval);
		return f.get();
	}

	/**
	 * Clear cached values for re-evaluation of the function graph
	 */
	public void clear() {
		for(MemoizedValue v : toClear)
			v.clear();
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(o == null || getClass() != o.getClass()) return false;
		Evaluator evaluator = (Evaluator) o;
		return Objects.equals(f, evaluator.f);
	}

	@Override
	public int hashCode() {
		return Objects.hash(f);
	}

	public MemoizedValue get(MemoizedValue v) {
		if(v instanceof MemoizedValue) {
			if(funSet.containsKey(v))
				return funSet.get(v);

			if(!toClear.contains(v))
				toClear.add(v);

			funSet.put(v, v);
		}

		return v;
	}

	/**
	 * Rewriter that injects a value as a first parameter for all occurrences of
	 * given marker class
	 */
	public static class ValueInjector implements FunctionRewriter {
		Value injectedValue;
		Class<?> markerClass;

		/**
		 *
		 * @param injectedValue value to inject
		 * @param markerClass injectable function class
		 */
		public ValueInjector(Value injectedValue, Class<?> markerClass) {
			this.injectedValue = injectedValue;
			this.markerClass = markerClass;
		}

		@Override
		public MemoizedValue rewrite(MemoizedValue f) {
			List<Value> args = new ArrayList<Value>();

			if(markerClass.isAssignableFrom(f.getFeature().getClass())) {
				args.add(injectedValue);
			}

			for(Value arg : f.args()) {
				if(arg instanceof MemoizedValue) {
					args.add(rewrite((MemoizedValue)arg));
				} else {
					args.add(arg);
				}
			}


			return new MemoizedValue(f.getFeature(), new Values.Var(), args);
		}
	}

	MemoizedValue rewrite(MemoizedValue f) {
		for(FunctionRewriter rw : rewriters) {
			f = rw.rewrite(f);
		}

		return f;
	}


	/**
	 * Minimize the function graph
	 * @param f function graph to minimize
	 * @return
	 */
	public MemoizedValue minimize(MemoizedValue f) {
		List<Value> args = new ArrayList<Value>();

		for(Value arg : f.args()) {
			Value v = null;
			if(arg instanceof MemoizedValue) {
				v = minimize((MemoizedValue)arg);
			} else {
				v = arg;
			}

			args.add(v);
		}

		return get(new MemoizedValue(f.getFeature(), new Values.Var(), args));
	}


}
