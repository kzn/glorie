package name.kazennikov.glorie;

import name.kazennikov.logger.Logger;

import java.util.List;

/**
 * Actual executable RHS action code.
 * An RHS action is an check/action procedure.
 *
 * The check part determines that the reduce action of GLR grammar is valid
 * and could be applied.
 *
 * The action part is used to set relevant feature of the newly reduced symbol.
 *
 */
public interface CompiledRHSAction {
	final Logger logger = Logger.getLogger();
	public static final Simple SIMPLE = new Simple();

	/**
	 * Execute the action
	 * @param text document text
     * @param  docFeats document feats
	 * @param rule current rule
	 * @param target reduced (target) symbol
	 * @param rhs actual symbol spans of the RHS of the rule
	 * @return true, if reduce action is successful
	 */
	public boolean execute(String text, gate.FeatureMap docFeats, CompiledGrammar.Rule rule, SymbolSpan target, List<SymbolSpan> rhs);


	/**
	 * Simple compiled RHS action class that enhanced the stacktrace of an exception occurred during execution of
	 * RHS action code
	 */
	public static class Friendly implements CompiledRHSAction {
		CompiledRHSAction action;
		SourceInfo sourceInfo;

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
	 * Simple RHS action that copies to the reduced symbol all features from the root symbol of the RHS
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
