package name.kazennikov.glorie;

import gate.FeatureMap;

import java.util.List;

/**
 * Basic class for RHS Actions for which action arguments are passed through object fields
 */
public abstract class FieldedRHSAction implements CompiledRHSAction {

    protected String text;
    protected FeatureMap docFeats;
	protected CompiledGrammar.Rule rule;
	protected Production production;
	protected SymbolSpan target;
	protected List<SymbolSpan> rhs;


	@Override
	public boolean execute(String text, gate.FeatureMap docFeats, CompiledGrammar.Rule rule, SymbolSpan target, List<SymbolSpan> rhs) {
		try {
			this.rule = rule;
			this.target = target;
			this.rhs = rhs;
			this.text = text;
            this.docFeats = docFeats;
			this.production = rule.production;
            target.features = gate.Factory.newFeatureMap();

			return exec() != Boolean.FALSE;
		} finally {
			this.rule = null;
			this.target = null;
			this.rhs = null;
			this.text = null;
            this.docFeats = null;
			this.production = null;
		}
	}

	/**
	 * Execute the action with arguments set in the object fields
	 * @return
	 */
	public abstract Object exec();
}
