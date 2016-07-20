package name.kazennikov.glorie;


import name.kazennikov.features.Function;
import name.kazennikov.features.MemoizedValue;
import org.codehaus.groovy.transform.ASTTransformation;

/**
 * Interface for customization of parsing process of GLR rules.
 *
 * Allows to customize:
 *
 * 1. Predicate-related customizations
 *    - Meta features.
 *    - Feature value operators
 *    - Context predicates
 *    - Typed strings transformations
 *    - Feature predicates
 *    - Predicate functions
 *
 * 2. Optimization and transformation customizations
 *    - optimize symbol predicates
 *    - optimize predicate function
 *
 * 3. Groovy code AST transformation
 *
 *
 */
public interface ParserContext {

    /**
     * Get feature predicate on constant value by operator name
     *
     * @param op  operator name
     * @param accessor accessor of the feature in the SymbolSpan
     * @param value test value
     */
    public SymbolSpanPredicate getFeaturePredicate(String op, FeatureAccessor accessor, Object value);

    /**
     * Get feature accessor for meta feature by name
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
     *
     * @param type symbol type
     * @param text raw text value
     * @param quote quote character (only \0, ', " or / supported for now)
     * @return
     */
    public Symbol typedStringSymbol(String type, String text, char quote);


    /**
     * Get function by name
     *
     * @param name function name
     * @return
     */
    public Function getFunction(String name);

    /**
     * Optimize function evaluator (called before evaluator post processing, e.g. values injection)
     *
     * @param value evaluator
     * @return optimized evaluator
     */
    public MemoizedValue optimize(MemoizedValue value);

    /**
     * Optimize symbol span predicate
     *
     * @param predicate
     * @return
     */
    public SymbolSpanPredicate optimize(SymbolSpanPredicate predicate);

	/**
	 * Get predicate feature by name
     *
	 * @param feature feature name
	 * @return symbol span predicate
	 */
	public SymbolSpanPredicate parseFeaturePredicate(String feature);

    /**
     * Custom AST Transformation for groovy code compiled within GLR.
     * Applies to: global grammar code, RHS actions, post-process RHS actions
     *
     * @return ast transformation, or null in none defined
     */
    public ASTTransformation astTransformation();

}
