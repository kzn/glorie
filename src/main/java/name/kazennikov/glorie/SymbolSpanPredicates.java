package name.kazennikov.glorie;

import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.glorie.func.Evaluator;

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Built-in symbol span predicates
 */
public class SymbolSpanPredicates {


    /**
     * Simple true predicates. Always returns 'true' value
     */
    public static class TruePredicate implements SymbolSpanPredicate {

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            return true;
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TruePredicate;
        }

        @Override
        public int hashCode() {
            return 31;
        }


    }


    /**
     * Predicate to match the type of the symbol span.
     *
     * Returns true only if the type of the symbol span equals the type encoded in the predicate
     */
    public static class TypePredicate extends AndPredicate {
        final String type;

        public TypePredicate(String type) {
            super(new ArrayList<SymbolSpanPredicate>());
            this.type = type;
        }

        public TypePredicate(String type, List<SymbolSpanPredicate> preds) {
            super(preds);
            this.type = type;

        }

        public void add(SymbolSpanPredicate pred) {
            preds.add(pred);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            // FIXME: type check excessive?
            return span.type.equals(type) && super.match(eval, span);
        }

        @Override
        public String toString() {
            return String.format("[%s:%s]", type, preds);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            TypePredicate that = (TypePredicate) o;

            if(!type.equals(that.type)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + type.hashCode();
            return result;
        }
    }


    /**
     * Base class for all predicates that are evaluated against value retrieved
     * by a FeatureAccessor from a SymbolSpan
     */
    public static abstract class ValuePredicate implements SymbolSpanPredicate {
        final FeatureAccessor fa;

        public ValuePredicate(FeatureAccessor fa) {
            this.fa = fa;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            ValuePredicate that = (ValuePredicate) o;

            if(!fa.equals(that.fa)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return fa.hashCode();
        }

        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }
    }


    /**
     * Equality predicate for given FeatureAccessor and value.
     *
     * Retrieves a value from SymbolSpan using FeatureAccessor and tests equality against encoded value.
     * The equality is defined by Java equality rules.
     *
     */
    public static class Equal extends ValuePredicate {
        final Object value;
        int faId; // assigned at Grammar.computeAccessors()
        int objId; // assigned at Grammar.computeAccessors()

        public Equal(FeatureAccessor fa, Object value) {
            super(fa);
            this.value = value;
        }


        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);
            return Objects.equals(v, value);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            Equal equal = (Equal) o;

            if(value != null ? !value.equals(equal.value) : equal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s == %s", fa, value);
        }
    }

    /**
     * Equality predicate for given FeatureAccessor and value.
     *
     * Retrieves a value from SymbolSpan using FeatureAccessor and tests equality against encoded value.
     * The equality is defined by Java equality rules.
     *
     */
    public static class EqualIgnoreCase extends ValuePredicate {
        final Object value;

        public EqualIgnoreCase(FeatureAccessor fa, Object value) {
            super(fa);
            this.value = value;
        }


        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);

            if(value instanceof String && v instanceof String) {
                return ((String) value).equalsIgnoreCase((String) v);
            }

            return Objects.equals(v, value);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            EqualIgnoreCase equal = (EqualIgnoreCase) o;

            if(value != null ? !value.equals(equal.value) : equal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s *== %s", fa, value);
        }
    }



    /**
     * Base class for predicates that embed other predicates
     */
    public static abstract class EmbeddedPredicates implements SymbolSpanPredicate {
        final List<SymbolSpanPredicate> preds;
        TIntArrayList predIds = new TIntArrayList();

        protected EmbeddedPredicates(List<SymbolSpanPredicate> preds) {
            this.preds = preds;
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            for(SymbolSpanPredicate pred : preds) {
                predIds.add(pred.compile(predicates));
            }

            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            EmbeddedPredicates that = (EmbeddedPredicates) o;

            if(!preds.equals(that.preds)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return preds.hashCode();
        }
    }

    /**
     * AND predicate.
     *
     * Return true, only if all embedded predicates return true.
     */
    public static class AndPredicate extends EmbeddedPredicates {


        public AndPredicate(List<SymbolSpanPredicate> preds) {
            super(preds);
        }

        public AndPredicate(SymbolSpanPredicate... preds) {
            super(new ArrayList<>(Arrays.asList(preds)));
        }


        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {

            for(int i = 0; i < predIds.size(); i++) {
                if(!eval.eval(predIds.get(i), span))
                    return false;
            }

            return true;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof AndPredicate && super.equals(o);
        }

    }

    /**
     * OR predicate.
     *
     * Returns true if any of the embedded predicates returns true.
     */
    public static class OrPredicate extends EmbeddedPredicates {

        public OrPredicate(List<SymbolSpanPredicate> preds) {
            super(preds);
        }

        public OrPredicate(SymbolSpanPredicate... preds) {
            super(new ArrayList<>(Arrays.asList(preds)));
        }


        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            for(int i = 0; i < predIds.size(); i++) {
                if(eval.eval(predIds.get(i), span))
                    return true;
            }

            return false;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof AndPredicate && super.equals(o);
        }

    }


    /**
     * Not  predicate.
     *
     * Return negated value of the embedded predicate
     */
    public static class NotPredicate implements SymbolSpanPredicate {
        SymbolSpanPredicate pred;
        int predId;

        public NotPredicate(SymbolSpanPredicate pred) {
            this.pred = pred;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            return !eval.eval(predId, span);
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            predId = pred.compile(predicates);
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            NotPredicate that = (NotPredicate) o;

            if(!pred.equals(that.pred)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return pred.hashCode();
        }
    }


    /**
     * Base class for context predicates,
     */
    public static abstract class ContextPredicate implements SymbolSpanPredicate {
        final FeatureAccessor annotationAccessor;
        final SymbolSpanPredicate pred;

        int predId;

        protected ContextPredicate(FeatureAccessor annotationAccessor, SymbolSpanPredicate pred) {
            this.annotationAccessor = annotationAccessor;
            this.pred = pred;
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            predId = pred.compile(predicates);
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            ContextPredicate that = (ContextPredicate) o;

            if(!pred.equals(that.pred)) {
                return false;
            }

            if(!annotationAccessor.equals(that.annotationAccessor)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return pred.hashCode();
        }
    }


    /**
     * Context predicate that returns true if there exist any symbol span that starts from the same offset
     * with given symbol span and matches the embedded predicate
     */
    public static class StartsWith extends ContextPredicate {

        public StartsWith(FeatureAccessor fa, SymbolSpanPredicate pred) {
            super(fa, pred);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            span = (SymbolSpan) annotationAccessor.get(eval, span);
            int spanId = span.id;
            if(spanId >= eval.input.size()) {
                spanId = eval.input.inputIndex(span.start);
            }

            if(spanId < 0)
                return false;

            // find word start
            while(spanId > 0) {
                SymbolSpan test = eval.input.get(spanId - 1);

                if(test.start != span.start)
                    break;
                spanId--;
            }

            while(spanId < eval.input.size()) {
                SymbolSpan test = eval.input.get(spanId);

                if(test.start != span.start)
                    break;

                if(eval.eval(predId, test))
                    return true;
                spanId++;
            }

            return false;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            StartsWith that = (StartsWith) o;

            if(!pred.equals(that.pred)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + pred.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return String.format("startsWith %s", pred);
        }
    }

	/**
	 * Context predicate that returns true if there exist any symbol span is coextensive
	 * with given symbol span and matches the embedded predicate
	 */
	public static class CoextensiveWith extends ContextPredicate {

		public CoextensiveWith(FeatureAccessor fa, SymbolSpanPredicate pred) {
			super(fa, pred);
		}

		@Override
		public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
			span = (SymbolSpan) annotationAccessor.get(eval, span);
			int spanId = span.id;

			if(spanId >= eval.input.size()) {
				spanId = eval.input.inputIndex(span.start);
			}

			if(spanId < 0)
				return false;

			// find word start
			while(spanId > 0) {
				SymbolSpan test = eval.input.get(spanId - 1);

				if(test.start != span.start)
					break;
				spanId--;
			}

			while(spanId < eval.input.size()) {
				SymbolSpan test = eval.input.get(spanId);

				if(test.start != span.start)
					break;

				if(test.end == span.end && eval.eval(predId, test))
					return true;

				spanId++;
			}

			return false;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) {
				return true;
			}
			if(o == null || getClass() != o.getClass()) {
				return false;
			}
			if(!super.equals(o)) {
				return false;
			}

			CoextensiveWith that = (CoextensiveWith) o;

			if(!pred.equals(that.pred)) {
				return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			int result = super.hashCode();
			result = 31 * result + pred.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return String.format("coextWith %s", pred);
		}
	}



    /**
     * Base class for Comparable-based predicates.
     * The predicate retrieves a value from symbol span and compares with the encoded value
     */
    public static abstract class ComparablePredicate extends ValuePredicate {
        final Comparable value;

        public ComparablePredicate(FeatureAccessor fa, Object value) {
            super(fa);

            this.value = value instanceof Comparable? (Comparable) value : value.toString();
        }


        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            ComparablePredicate equal = (ComparablePredicate) o;

            if(value != null ? !value.equals(equal.value) : equal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s ? %s", fa, value);
        }
    }

    /**
     * Comparable greater predicate (f.a > b)
     */
    public static class GreaterPredicate extends ComparablePredicate {

        public GreaterPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GreaterPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);

            return value.compareTo(v) < 0;
        }
    }

    /**
     * Comparable greater or equal predicate (f.a >= b)
     */
    public static class GreaterEqualsPredicate extends ComparablePredicate {

        public GreaterEqualsPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GreaterEqualsPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);

            return value.compareTo(v) <= 0;
        }
    }

    /**
     * Comparable less predicate (f.a < b)
     */
    public static class LesserPredicate extends ComparablePredicate {

        public LesserPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof LesserPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);

            return value.compareTo(v) > 0;
        }
    }

    /**
     * Comparable less or equal predicate (f.a <= b)
     */
    public static class LesserEqualsPredicate extends ComparablePredicate {

        public LesserEqualsPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof LesserEqualsPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);

            return value.compareTo(v) >= 0;
        }
    }

    /**
     * Base class for regex-based predicates
     */
    public static abstract class RegexPredicate extends ValuePredicate {
        final Pattern value;

        public RegexPredicate(FeatureAccessor fa, Object value) {
            super(fa);
            String pat = value.toString();
            this.value = Pattern.compile(pat);
        }


        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }
            if(!super.equals(o)) {
                return false;
            }

            RegexPredicate equal = (RegexPredicate) o;

            if(value != null ? !value.equals(equal.value) : equal.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + (value != null ? value.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            return String.format("%s ~ %s", fa, value);
        }
    }

    /**
     * Checks if given feature value strictly matches given regex
     */
    public static class RegexMatchPredicate extends RegexPredicate {

        public RegexMatchPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o ) {
            return o instanceof RegexMatchPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);
            return v != null && value.matcher(v.toString()).matches();
        }
    }

    /**
     * Checks if given feature value contains a regex
     */
    public static class RegexFindPredicate extends RegexPredicate {

        public RegexFindPredicate(FeatureAccessor fa, Object value) {
            super(fa, value);
        }

        @Override
        public boolean equals(Object o ) {
            return o instanceof RegexFindPredicate && super.equals(o);
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object v = fa.get(eval, span);
            return v != null && value.matcher(v.toString()).find();
        }
    }


    /**
     * Custom function predicate.
     * Evaluates a function graph against given symbol span and returns true only
     * if the result value is equal to Boolean.TRUE
     */
    public static class MemoizedValuePredicate implements SymbolSpanPredicate {
        Evaluator evaluator;

        public MemoizedValuePredicate(Evaluator evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {

            return evaluator.eval(eval, span) == Boolean.TRUE;
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            MemoizedValuePredicate that = (MemoizedValuePredicate) o;
            return Objects.equals(evaluator, that.evaluator);
        }

        @Override
        public int hashCode() {
            return Objects.hash(evaluator);
        }
    }


    public static class InPredicate extends ValuePredicate {
        FeatureAccessor fa;
        List<String> vals = new ArrayList<>();

        public InPredicate(FeatureAccessor fa, String value) {
            super(fa);
            for(String s : Arrays.asList(value.split("\\|"))) {
                s = s.trim();
                if(!s.isEmpty()) {
                    vals.add(s);
                }
            };

        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Object value = fa.get(eval, span);
            return vals.contains(value);
        }
    }


	/**
	 * Checks if given predicate contained
	 */
	public static class  ContainsPredicate  extends ContextPredicate {

		public ContainsPredicate(FeatureAccessor fa, SymbolSpanPredicate pred) {
			super(fa, pred);
		}

		@Override
		public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
			span = (SymbolSpan) annotationAccessor.get(eval, span);
			int spanId = span.id;

			if(spanId >= eval.input.size()) {
				spanId = eval.input.inputIndex(span.start);
			}

			if(spanId < 0)
				return false;

			// find word start
			while(spanId > 0) {
				SymbolSpan test = eval.input.get(spanId - 1);

				if(test.start != span.start)
					break;
				spanId--;
			}

			while(spanId < eval.input.size()) {
				SymbolSpan test = eval.input.get(spanId);

				// прошли все аннотации
				if(test.start > span.end)
					break;

				// пропускаем вылезающие за границы основной
				if(test.end > span.end) {
					spanId++;
					continue;
				}

				if(eval.eval(predId, test))
					return true;

				spanId++;
			}

			return false;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) {
				return true;
			}
			if(o == null || getClass() != o.getClass()) {
				return false;
			}
			if(!super.equals(o)) {
				return false;
			}

			ContainsPredicate that = (ContainsPredicate) o;

			if(!pred.equals(that.pred)) {
				return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			int result = super.hashCode();
			result = 31 * result + pred.hashCode();
			return result;
		}

		@Override
		public String toString() {
			return String.format("contains %s", pred);
		}
	}


	public static class NotNullFeaturePredicate implements SymbolSpanPredicate {

		String feature;

		public NotNullFeaturePredicate(String feature) {
			this.feature = feature;
		}

		@Override
		public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
			return span.features.get(feature) != null;
		}

		@Override
		public int compile(Alphabet<SymbolSpanPredicate> predicates) {
			return predicates.get(this);
		}

		@Override
		public boolean equals(Object o) {
			if(this == o)
				return true;

			if(o == null || getClass() != o.getClass())
				return false;

			NotNullFeaturePredicate that = (NotNullFeaturePredicate) o;
			return Objects.equals(feature, that.feature);
		}

		@Override
		public int hashCode() {
			return Objects.hash(feature);
		}
	}


    /**
     * Source code predicate name holder.
     * Used only in the Grammar, intended to be replaced with actual code in
     * compiled grammar code
     */
    public static class SourceCodeSymbolSpanPredicate implements SymbolSpanPredicate {

        String feature;

        public SourceCodeSymbolSpanPredicate(String feature) {
            this.feature = feature;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {

            throw new IllegalStateException("Evaluating non-compiled source predicate");
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            SourceCodeSymbolSpanPredicate that = (SourceCodeSymbolSpanPredicate) o;
            return Objects.equals(feature, that.feature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feature);
        }
    }

    public static class MethodHandleSimple implements SymbolSpanPredicate {
        MethodHandle mh;

        public MethodHandleSimple(MethodHandle mh) {
            this.mh = mh;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            try {
                return mh.invoke(span) == Boolean.TRUE;
            } catch(Throwable e) {
                return false;
            }
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            throw new IllegalStateException("predicate is not compilable");
        }
    }

    public static class MethodHandleFull implements SymbolSpanPredicate {
        MethodHandle mh;

        public MethodHandleFull(MethodHandle mh) {
            this.mh = mh;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            try {
                return mh.invoke(eval, span) == Boolean.TRUE;
            } catch(Throwable e) {
                return false;
            }
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            throw new IllegalStateException("predicate is not compilable");
        }
    }

    public static class MethodHandleByName implements SymbolSpanPredicate {
        String featureName;
        MethodHandle mh;

        public MethodHandleByName(MethodHandle mh, String featureName) {
            this.mh = mh;
            this.featureName = featureName;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            try {
                return mh.invoke(featureName, eval, span) == Boolean.TRUE;
            } catch(Throwable e) {
                return false;
            }
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            throw new IllegalStateException("predicate is not compilable");
        }
    }

    /**
     * Check if given symbol has no following words (is the last)
     */
    public static class LastWordPredicate implements SymbolSpanPredicate {

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            int sourceSpanId = span.id;

            if(sourceSpanId >= eval.input.size()) {
                sourceSpanId = eval.input.inputIndex(span.start);
            }

            InputData input = eval.getInput();


            return input.nextWords[sourceSpanId] == input.wordCount();
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }
    }

    /**
     *Check if given symbol has no preceding words (is the first)
     */
    public static class FirstWordPredicate implements SymbolSpanPredicate {

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            int sourceSpanId = span.id;

            if(sourceSpanId >= eval.input.size()) {
                sourceSpanId = eval.input.inputIndex(span.start);
            }

            InputData input = eval.getInput();


            return input.nextWords[sourceSpanId] == 0;
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }
    }


    public static class CheckFeaturePredicate implements SymbolSpanPredicate {
        String name;

        public CheckFeaturePredicate(String name) {
            this.name = name;
        }

        @Override
        public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span) {
            Set<String> feats = (Set<String>) span.features.get("_fs");
            if(feats == null)
                return false;

            return feats.contains(name);
        }

        @Override
        public int compile(Alphabet<SymbolSpanPredicate> predicates) {
            return predicates.get(this);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o)
                return true;
            if(o == null || getClass() != o.getClass())
                return false;
            CheckFeaturePredicate that = (CheckFeaturePredicate) o;

            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }





}
