package name.kazennikov.glorie;

import name.kazennikov.glorie.func.Evaluator;

import java.util.Objects;

/**
 * A Feature Accessor is used to retrieve some value object from given symbol span.
 *
 * A Feature can be an "inner" feature of the symbol span, or could be some value
 * that is computed from the span and the underlying document
 */
public interface FeatureAccessor {
    /**
     * Get feature value from given symbol span.
     *
     * The input data could be used to get some data that is not associated
     * with the symbol span directly. For example, the document text
     * @param evaluator evaluator
     * @param span target symbol span
     * @return value
     */
    public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span);


    /**
     * Simple accessor that returns the span itself
     */
    public static class AnnotationAccessor implements FeatureAccessor {

        @Override
        public SymbolSpan get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span;
        }

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof AnnotationAccessor;
        }
    }

    /**
     * Accessor that returns the n-th head of given symbol span.
     *
     * n-th head that it retrieves the
     * head of the head of the head and so on n times.
     */
    public static class HeadAnnotationAccessor implements FeatureAccessor {
        int head;

        public HeadAnnotationAccessor(int head) {
            this.head = head;
        }


        @Override
        public SymbolSpan get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span.head(head);

        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            HeadAnnotationAccessor that = (HeadAnnotationAccessor) o;

            if(head != that.head) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return head;
        }
    }

    /**
     * Accessor the the n-th head feature. It retrieves the n-th head first, and then
     * if it is not null retrieves specified value using the inner feature accessor
     */
    public static class HeadFeatureAccessor implements FeatureAccessor {
        int head;
        FeatureAccessor base;

        public HeadFeatureAccessor(int head, FeatureAccessor base) {
            this.head = head;
            this.base = base;
        }


        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            SymbolSpan s = span.head(head);
            return s == null? null : base.get(evaluator, s);
        }
    }


    /**
     * Simple feature accessor that retrieves a value by name from the symbol span "features" field
     */
    public static class Simple implements FeatureAccessor {
        String name;

        public Simple(String name) {
            this.name = name;
        }

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span.features != null? span.features.get(name) : null;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            Simple simple = (Simple) o;

            if(!name.equals(simple.name)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }


    /**
     * Feature Accessor that returns the type of the symbol span
     */
    public static class Type implements FeatureAccessor {

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span.type;
        }

        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Type;
        }

        @Override
        public String toString() {
            return "@type";
        }
    }


    /**
     * Feature accessor to retrieve the start offset of a SymbolSpan
     */
    public static class Start implements FeatureAccessor {

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span.start;
        }

        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Start;
        }

        @Override
        public String toString() {
            return "@start";
        }

    }

    /**
     * Feature accessor to retrieve the end offset of a SymbolSpan
     */
    public static class End implements FeatureAccessor {

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return span.end;
        }

        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof End;
        }

        @Override
        public String toString() {
            return "@end";
        }

    }

    public static class Length implements FeatureAccessor {

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            int length = span.end - span.start;
            return length;
        }

        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Length;
        }

        @Override
        public String toString() {
            return "@length";
        }
    }


    /**
     * Feature accessor to retrieve covered text as string.
     * The text is returned "as is" - without postprocessing
     */
    public static class StringValue implements FeatureAccessor {

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            try {
                return evaluator.input.doc.getContent().getContent((long) span.start, (long) span.end).toString();
            } catch (Exception e) {
                return "";
            }
        }

        @Override
        public int hashCode() {
            return 31;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof String;
        }

        @Override
        public String toString() {
            return "@string";
        }
    }

    /**
     * Feature accessor evaluates given complex function on symbol span and returns its result
     */
    public static class EvaluatorAccessor implements FeatureAccessor {

        Evaluator eval;

        public EvaluatorAccessor(Evaluator eval) {
            this.eval = eval;
        }

        @Override
        public Object get(SymbolSpanPredicateEvaluator evaluator, SymbolSpan span) {
            return eval.eval(evaluator, span);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) return true;
            if(o == null || getClass() != o.getClass()) return false;
            EvaluatorAccessor that = (EvaluatorAccessor) o;
            return Objects.equals(eval, that.eval);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eval);
        }
    }
}
