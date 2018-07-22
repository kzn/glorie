package name.kazennikov.glorie;

import name.kazennikov.alphabet.Alphabet;
import name.kazennikov.fsa.walk.WalkFSABoolean;

import java.util.BitSet;
import java.util.List;

/**
 * Symbol span predicate evaluator
 */
public class SymbolSpanPredicateEvaluator {
    CompiledGrammar grammar;
    InputData input;
    BitSet predicateCache;
    List<SymbolSpanPredicate> predicates;
    Grammar.PredInfo[] predInfos;

    Alphabet<FeatureAccessor> faAlphabet;
    Alphabet<Object> objectAlphabet;
    WalkFSABoolean fsa;

    public SymbolSpanPredicateEvaluator(CompiledGrammar grammar, InputData input) {
        this.grammar = grammar;
        this.input = input;
        this.predicates = grammar.predicates;

        faAlphabet = grammar.grammar.accessorAlphabet;
        objectAlphabet = grammar.grammar.objectAlphabet;
        predInfos = grammar.grammar.predInfos;
        fsa = grammar.grammar.predFSA;

        predicateCache = new BitSet(2 * input.size() * predicates.size());

    }


    public int bitIndex(int predId, int spanId) {
        return 2*(spanId * predicates.size() + predId);
    }

    public boolean eval(SymbolSpanPredicate pred, SymbolSpan span) {
        return pred.match(this, span);
    }


    public boolean eval(int predicateId, SymbolSpan span) {
        int index = bitIndex(predicateId, span.id);

        if(predicateCache.get(index))
            return predicateCache.get(index + 1);

        Grammar.PredInfo pi = predInfos[predicateId];

        if(pi.fsa) {
            FeatureAccessor fa = faAlphabet.get(pi.fa);
            Object o = fa.get(this, span);

            if(o == null) {
                // set all predicate with this equals to 'false'
                for(int i = 0; i < pi.alsoFalse.size(); i++) {
                    int converseIndex = bitIndex(pi.alsoFalse.get(i), span.id);
                    predicateCache.set(converseIndex);
                    predicateCache.clear(converseIndex + 1);
                }
                return setResult(pi, index, span.id, false);
            }

            int objId = objectAlphabet.get(o, false);

            if(objId == 0) {
                // set all predicate with this equals to 'false'
                for(int i = 0; i < pi.alsoFalse.size(); i++) {
                    int converseIndex = bitIndex(pi.alsoFalse.get(i), span.id);
                    predicateCache.set(converseIndex);
                    predicateCache.clear(converseIndex + 1);
                }
                return setResult(pi, index, span.id, false);
            }

            int s = fsa.next(0, pi.fa);
            s = fsa.next(s, objId);

            if(s == -1) {
                return setResult(pi, index, span.id, false);
            }

            int trStart = fsa.stateStart(s);
            int trEnd = fsa.stateEnd(s);

            for(int k = trStart; k < trEnd; k++) {
                int predId = fsa.label(k);
                setResult(predInfos[predId], bitIndex(predId, span.id), span.id, true);
            }

            return predicateCache.get(index + 1);
        }


        boolean value = eval(predicates.get(predicateId), span);
        setResult(pi, index, span.id, value);
        return value;
    }

    public boolean setResult(Grammar.PredInfo pi, int index, int spanId, boolean value) {
        predicateCache.set(index);
        predicateCache.set(index + 1, value);


        if(value) {

            for(int i = 0; i < pi.alsoFalse.size(); i++) {
                int converseIndex = bitIndex(pi.alsoFalse.get(i), spanId);
                predicateCache.set(converseIndex);
                predicateCache.clear(converseIndex + 1);
            }

            for(int i = 0; i < pi.alsoTrue.size(); i++) {
                int converseIndex = bitIndex(pi.alsoTrue.get(i), spanId);
                predicateCache.set(converseIndex);
                predicateCache.set(converseIndex + 1);
            }


        } else {
            for(int i = 0; i < pi.converseFalse.size(); i++) {
                int converseIndex = bitIndex(pi.converseFalse.get(i), spanId);
                predicateCache.set(converseIndex);
                predicateCache.clear(converseIndex + 1);
            }

            for(int i = 0; i < pi.converseTrue.size(); i++) {
                int converseIndex = bitIndex(pi.converseTrue.get(i), spanId);
                predicateCache.set(converseIndex);
                predicateCache.set(converseIndex + 1);
            }
        }

        return value;
    }

	public InputData getInput() {
		return input;
	}
}
