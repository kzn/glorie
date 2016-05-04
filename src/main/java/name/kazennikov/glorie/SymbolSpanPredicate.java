package name.kazennikov.glorie;

import name.kazennikov.alphabet.Alphabet;

/**
 * Predicate on SymbolSpans
 */
public interface SymbolSpanPredicate {
    public static SymbolSpanPredicates.TruePredicate TRUE = new SymbolSpanPredicates.TruePredicate();

    /**
     * Evaluate the predicate against given symbol span
     * @param eval evaluator
     * @param span symbol span to evaluate against
     * @return
     */
    public boolean match(SymbolSpanPredicateEvaluator eval, SymbolSpan span);

    /**
     * Compile given predicate against given predicates alphabet.
     * Used for deduplication of predicates in the grammar.
     *
     * @param predicates predicates alphabet
     * @return index of the equivalent predicate in the alphabet
     */
    public int compile(Alphabet<SymbolSpanPredicate> predicates);


    /**
     * Rewriter for symbol span predicate
     */
    public interface Rewriter {
        /**
         * Rewrite predicate
         * @param predicate predicate
         * @return
         */
        public SymbolSpanPredicate rewrite(SymbolSpanPredicate predicate);
    }
}
