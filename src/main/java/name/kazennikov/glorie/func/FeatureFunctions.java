package name.kazennikov.glorie.func;

import name.kazennikov.features.Value;
import name.kazennikov.glorie.*;

import java.util.List;
import java.util.Objects;

/**
 * Default function definitions for GLORIE
 *
 * @author Anton Kazennikov
 */
public class FeatureFunctions {

	public static final SimpleFeatureFunction SIMPLE_FEATURE = new SimpleFeatureFunction();
	public static final IdentitySpanSymbolFunction IDENTITY_SPAN_SYMBOL = new IdentitySpanSymbolFunction();
	public static final HeadFunction HEAD = new HeadFunction();

	/**
	 * Function that returns a value by feature name from 'features' field of a SymbolSpan.
	 * The feature name is passed as function parameter:
	 * f('name').
	 *
	 * Function expects that the symbol span will be injected before evaluation
	 */
	public static class SimpleFeatureFunction implements SymbolSpanInjectable {
		@Override
		public String name() {
			return "feat";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpan ss = args.get(0).get(SymbolSpan.class);
			String name = args.get(1).get(String.class);
			res.set(ss.features.get(name));
		}

	}

    /**
     * Function on {@link SymbolSpanPredicate}
     */
	public static class SymbolSpanPredicateFunction implements PredicateEvaluatorInjectable, SymbolSpanInjectable {
		SymbolSpanPredicate pred;

		public SymbolSpanPredicateFunction(SymbolSpanPredicate pred) {
			this.pred = pred;
		}

		@Override
		public String name() {
			return pred.toString();
		}



		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpanPredicateEvaluator eval = args.get(0).get(SymbolSpanPredicateEvaluator.class);
			SymbolSpan ss = args.get(1).get(SymbolSpan.class);
			res.set(pred.match(eval, ss));
		}
	}


	/**
	 * Function that retrieves some predefined value from 'features' field of a SymbolSpan
	 * The feature name is a part of the function definition:
	 * f()
	 *
	 * Function expects that the symbol span will be injected before evaluation
	 */
	public static class FeatureAccessorFunction implements SymbolSpanInjectable {
		final Object feature;

		public FeatureAccessorFunction(Object feature) {
			this.feature = feature;
		}

		@Override
		public String name() {
			return feature.toString();
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpan ss = args.get(0).get(SymbolSpan.class);
			res.set(ss.features.get(feature));
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			FeatureAccessorFunction that = (FeatureAccessorFunction) o;
			return Objects.equals(feature, that.feature);
		}

		@Override
		public int hashCode() {
			return Objects.hash(feature);
		}
	}

	/**
	 * Function that returns the Symbol span itself
	 */
	public static class IdentitySpanSymbolFunction implements SymbolSpanInjectable {

		@Override
		public String name() {
			return "sspIdentity";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			res.set(args.get(0));
		}
	}

	/**
	 * Get head from SymbolSpan.
	 * The order of the root is passed as a function argument
	 */
	public static class HeadFunction implements SymbolSpanInjectable {

		@Override
		public String name() {
			return "head";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpan ss = args.get(0).get(SymbolSpan.class);
			Integer num = args.get(1).get(Integer.class);
			res.set(ss.head(num));
		}
	}

	/**
	 * Retrieve meta feature value from SymbolSpan
	 * The meta feature name is passed as function parameter.
	 */
	public static class MetaFeatureFunction implements PredicateEvaluatorInjectable, SymbolSpanInjectable {
		final FeatureAccessor fa;
		final String name;

		public MetaFeatureFunction(ParserContext context, String name) {
			fa = context.getMetaFeatureAccessor(name);
			this.name = name;
		}


		@Override
		public String name() {
			return "@" + name;
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpanPredicateEvaluator eval = args.get(0).get(SymbolSpanPredicateEvaluator.class);
			SymbolSpan ss = args.get(1).get(SymbolSpan.class);
			res.set(fa.get(eval, ss));
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			MetaFeatureFunction that = (MetaFeatureFunction) o;
			return Objects.equals(fa, that.fa) &&
					Objects.equals(name, that.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(fa, name);
		}
	}

	/**
	 * Function for retrieve meta feature value from SymbolSpan
	 * The meta feature name is a part of the function definition.
	 */
	public static class MetaFeatureAccessorFunction implements PredicateEvaluatorInjectable, SymbolSpanInjectable {
		final ParserContext context;

		public MetaFeatureAccessorFunction(ParserContext context) {
			this.context = context;
		}


		@Override
		public String name() {
			return "@";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpanPredicateEvaluator eval = args.get(0).get(SymbolSpanPredicateEvaluator.class);
			SymbolSpan ss = args.get(1).get(SymbolSpan.class);
			String name = args.get(2).get(String.class);
			FeatureAccessor fa = context.getMetaFeatureAccessor(name);
			res.set(fa.get(eval, ss));
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;
			MetaFeatureAccessorFunction that = (MetaFeatureAccessorFunction) o;
			return Objects.equals(context, that.context);
		}

		@Override
		public int hashCode() {
			return Objects.hash(context);
		}
	}

	public static class FirstWordPredicate implements PredicateEvaluatorInjectable, SymbolSpanInjectable {
		@Override
		public String name() {
			return "fw";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpanPredicateEvaluator eval = args.get(0).get(SymbolSpanPredicateEvaluator.class);
			SymbolSpan ss = args.get(1).get(SymbolSpan.class);

			res.set(ss.start == eval.getInput().get(0).start);
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			return true;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}

	public static class LastWordPredicate implements PredicateEvaluatorInjectable, SymbolSpanInjectable {
		@Override
		public String name() {
			return "lw";
		}

		@Override
		public void eval(Value.Settable res, List<Value> args) {
			SymbolSpanPredicateEvaluator eval = args.get(0).get(SymbolSpanPredicateEvaluator.class);
			InputData inputData = eval.getInput();
			SymbolSpan ss = args.get(1).get(SymbolSpan.class);
			// last is EOF
			res.set(ss.end == inputData.get(inputData.size() - 2).end);
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			return true;
		}

		@Override
		public int hashCode() {
			return 0;
		}
	}
}
