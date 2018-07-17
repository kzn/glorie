package name.kazennikov.glorie;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import name.kazennikov.logger.Logger;

import java.util.Map;

/**
 * Post-processor of output document annotations wrt symbol nodes and source rule
 *
 * The symbol may have several parses. All parses are processed. The process order
 * is undefined for now. (It will possibly change. E.g. for order in source file)
 */
public interface InterpAction {
    final Logger logger = name.kazennikov.logger.Logger.getLogger();

    /**
     * Post process symbol node
     * @param doc target GATE document
     * @param outputAS target GATE annotation set
     * @param rule rule that created targetNode
     * @param node mapped node
     * @param rhs actual right hand side symbol spans of the rule
     * @param rhsAnn mapped right hand side symbols
     * @param bindings rule bindings (binding -> symbol span)
     * @param bindingAnns rule annotation bindings (binding -> annotation)
     * @return result annotation or null, if the source node shouldn't map to the output
     * @throws Exception
     */
    public void apply(gate.Document doc, AnnotationSet outputAS, CompiledGrammar.Rule rule, SymbolNode node, Annotation ann, SymbolSpan[] rhs, Annotation[] rhsAnn, Map<String, SymbolSpan> bindings, Map<String, Annotation> bindingAnns) throws Exception;


    public static class Source {
        String source;

        public Source(String source) {
            this.source = source;
        }
    }

    /**
     * Post processor that enhances possible stack traces from rhs actions
     * RHS action code
     */
    public static class Friendly implements InterpAction {
        InterpAction pp;
        SourceInfo sourceInfo;

        public Friendly(InterpAction pp, SourceInfo sourceInfo) {
            this.pp = pp;
            this.sourceInfo = sourceInfo;
        }


        @Override
        public void apply(Document doc, AnnotationSet outputAS, CompiledGrammar.Rule rule, SymbolNode node, Annotation ann, SymbolSpan[] rhs, Annotation[] rhsAnn, Map<String, SymbolSpan> bindings, Map<String, Annotation> bindingAnns) throws Exception {
            try {
                pp.apply(doc, outputAS, rule, node, ann, rhs, rhsAnn, bindings, bindingAnns);
            } catch(Exception e) {
                sourceInfo.enhanceTheThrowable(e);
                logger.error("Symbol postprocessor error", e);
                throw e;
            }
        }
    }

}
