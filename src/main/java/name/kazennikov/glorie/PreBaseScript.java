package name.kazennikov.glorie;

import groovy.lang.Script;

import java.util.List;

/**
 * Base class for Pre Script.
 * A pre block transforms an input symbol sequence prior to actual grammar input
 */
public abstract class PreBaseScript extends Script {

    /**
     * Dummy pre block that returns input symbol sequence as is
     */
    public static class Simple extends PreBaseScript {

        @Override
        public List<SymbolSpan> run() {
            return spans;
        }
    }

    // input symbol span sequence
    protected List<SymbolSpan> spans;

    // data for enhancing stack traces
    SourceInfo sourceInfo;

    /**
     * Executes the pre block on input data
     * @param spans input symbol span
     * @return
     */
    public List<SymbolSpan> exec(List<SymbolSpan> spans) {
        try {
            this.spans = spans;
            return (List<SymbolSpan>) run();
        } catch(Exception e) {
            if(sourceInfo != null)
                sourceInfo.enhanceTheThrowable(e);
            throw e;
        } finally {
            this.spans = null;
        }
    }

    public void setSourceInfo(SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
    }



}
