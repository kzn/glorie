package name.kazennikov.glorie;


import name.kazennikov.features.Function;
import name.kazennikov.features.MemoizedValue;
import org.codehaus.groovy.transform.ASTTransformation;

/**
 * Parser Context for customization of parsing process of GLR rules.
 *
 *
 */
public interface ParserContext {

    /**
     * Get feature predicate on constant value for given operation name
     *
     * @param op  operator name
     * @param accessor accessor of the feature in the SymbolSpan
     * @param value test value
     * @return
     */
    public SymbolSpanPredicate getFeaturePredicate(String op, FeatureAccessor accessor, Object value);

    /**
     * Get feature accessor for meta feature
     *
     * @param type meta feature name
     * @return
     */
    public FeatureAccessor getMetaFeatureAccessor(String type);

    /**
     * Get context predicate by name
     *
     * @param op predicate name
     * @param fa feature accessor for the predicate
     * @param innerPredicate inner predicate (if any)
     * @return
     */
    public SymbolSpanPredicate getContextPredicate(String op, FeatureAccessor fa, SymbolSpanPredicate innerPredicate);

    /**
     * Convert typed string into grammar symbol
     * @param type symbol type
     * @param text raw text value
     * @param quote quote character (only \0, ', " or / for now)
     * @return
     */
    public Symbol typedStringSymbol(String type, String text, char quote);


    /**
     * Get function by name
     * @param name function name
     * @return
     */
    public Function getFunction(String name);

    /**
     * Optimize function evaluator (called before evaluator post processing, e.g. values injection)
     * @param value evaluator
     * @return optimized evaluator
     */
    public MemoizedValue optimize(MemoizedValue value);

    /**
     * Optimize symbol span predicate
     * @param predicate
     * @return
     */
    public SymbolSpanPredicate optimize(SymbolSpanPredicate predicate);

	/**
	 * Get symbol span predicate for feature (used if feature is given by name only)
	 * @param feature feature name
	 * @return symbol span predicate
	 */
	public SymbolSpanPredicate parseFeaturePredicate(String feature);

    /**
     * Custom AST Transformation for groovy code compiled within GLR.
     * Applies to: global grammar code, RHS actions
     * @return ast transformation, or null in none defined
     */
    public ASTTransformation astTransformation();

}
