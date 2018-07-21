package name.kazennikov.glorie;

import name.kazennikov.logger.Logger;

import java.util.List;

/**
 * Executable RHS action.
 * An RHS action is an check/action procedure.
 *
 * The check part determines if the reduce action of GLR grammar is valid
 * and applicable.
 *
 * The action part sets relevant features of the newly reduced symbol.
 *
 */
public interface CompiledRHSAction {
	final Logger logger = Logger.getLogger();
	public static final Simple SIMPLE = new Simple();

	/**
	 * Execute the action
     *
	 * @param text document text
     * @param docFeats document features
	 * @param rule current rule
	 * @param target reduced (target) symbol
	 * @param rhs actual symbol spans of the RHS of the rule
     *
	 * @return true, if reduce action succeeded
	 */
	public boolean execute(String text, gate.FeatureMap docFeats, CompiledGrammar.Rule rule, SymbolSpan target, List<SymbolSpan> rhs);


	/**
	 * Compiled RHS action that enhances stack trace if an exception is occurred during the action execution
	 */
	public static class Friendly implements CompiledRHSAction {
		CompiledRHSAction action;
		SourceInfo sourceInfo;

        /**
         * Constructor
         * @param action wrapped action
         * @param sourceInfo source code info for stack trace enhancements
         */
		public Friendly(CompiledRHSAction action, SourceInfo sourceInfo) {
			this.action = action;
			this.sourceInfo = sourceInfo;
		}

		@Override
		public boolean execute(String text, gate.FeatureMap docFeats, CompiledGrammar.Rule rule, SymbolSpan target, List<SymbolSpan> rhs) {
			try {
				return action.execute(text, docFeats, rule, target, rhs);
			} catch(Exception e) {
				sourceInfo.enhanceTheThrowable(e);
				logger.error("RHS Action error", e);
				throw e;
			}
		}
	}

	/**
	 * Default RHS action.
     * It copies all features from the root symbol of the RHS to to reduced (target) symbol
	 */
	public static class Simple implements CompiledRHSAction {

		@Override
		public boolean execute(String text, gate.FeatureMap docFeats, CompiledGrammar.Rule rule, SymbolSpan target, List<SymbolSpan> rhs) {
			SymbolSpan root = rhs.get(rule.production.rootIndex);
			target.features = root.features;
			return true;
		}
	}
}
