package name.kazennikov.glorie;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;

import java.util.Map;

/**
 * Created on 14.12.15.
 *
 * @author Anton Kazennikov
 */
public abstract class FieldedSymbolNodePostProcessor implements SymbolNodePostProcessor {

	protected Document doc;
	protected AnnotationSet outputAS;
	protected CompiledGrammar.Rule rule;
	protected SymbolNode node;
	protected Annotation ann;
	protected SymbolSpan[] rhs;
	protected Annotation[] rhsAnn;
	protected Map<String, SymbolSpan> bindings;
	protected Map<String, Annotation> bindingAnns;

	@Override
	public void apply(Document doc, AnnotationSet outputAS, CompiledGrammar.Rule rule, SymbolNode node, Annotation ann, SymbolSpan[] rhs, Annotation[] rhsAnn, Map<String, SymbolSpan> bindings, Map<String, Annotation> bindingAnns) throws Exception {
		try {
			this.doc = doc;
			this.outputAS = outputAS;
			this.rule = rule;
			this.node = node;
			this.ann = ann;
			this.rhs = rhs;
			this.rhsAnn = rhsAnn;
			this.bindings = bindings;
			this.bindingAnns = bindingAnns;
			apply();
		} finally {
			this.doc = null;
			this.outputAS = null;
			this.rule = null;
			this.node = null;
			this.ann = null;
			this.rhs = null;
			this.rhsAnn = null;
			this.bindings = null;
			this.bindingAnns = null;
		}
	}

	/**
	 * Apply post processing to the fields
	 * @return
	 */
	public abstract void apply();


}
