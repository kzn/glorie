package name.kazennikov.glorie;

import gate.Document;
import gate.FeatureMap;
import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.sort.BinarySearch;
import name.kazennikov.sort.FixedIntComparator;

import java.util.List;

/**
 * Input spans and additional data for GLR Parser
 */
public class InputData {
    Document doc;

    // document text
    String text;

    // document features
    FeatureMap docFeats;

    // list of input (source) symbol spans
    List<SymbolSpan> input;

    // nextIndex[i] -  minimal index of symbol span that follows i-th symbol span
    int[] nextIndex;

    // wordStarts[i] - index of the first symbol span of i-th word
    TIntArrayList wordStarts = new TIntArrayList();

    // wordEnds[i] - index of the next of the last symbol span of i-th word
    // the word range is [wordsStarts[i], wordEnds[i])
    TIntArrayList wordEnds = new TIntArrayList();

    // wordOffsets[i] - char offset of i-th word
    TIntArrayList wordOffsets = new TIntArrayList();

    // words[i] - word index of i-th input symbol span
    int[] words;
    // next word index of i-th input symbol span
    int[] nextWords;

    // id of the last created symbol span
    int lastSpanId;



    public InputData(Document doc, List<SymbolSpan> input) {
        this.text = doc.getContent().toString();
        this.docFeats = doc.getFeatures();
        this.doc = doc;
        this.input = input;
        this.lastSpanId = this.input.size();
        words = new int[input.size()];
        nextWords = new int[input.size()];


        init();


    }

    public void init() {
        for(int i = 0; i < input.size(); i++) {
            input.get(i).id = i;
        }

        computeNextIndex();
        computeWordPositions();
        computeNextWords();
    }

    /**
     * Compute next span index for each span in the input
     */
    public void computeNextIndex() {
        nextIndex = new int[input.size()];


        for(int i = 0; i < input.size(); i++) {
            nextIndex[i] = input.size();

            long current = input.get(i).end;
            for(int j = i + 1; j < input.size(); j++) {
                long next = input.get(j).start;

                if(next >= current) {
                    nextIndex[i] = j;
                    break;
                }
            }
        }
    }

    /**
     * Compute word positions, a word a set of spans with the same start offset
     */
    public void computeWordPositions() {

        int pos = 0;

        while(pos < input.size()) {
            int end = pos + 1;
            int wordIndex = wordStarts.size();
            words[pos] = wordIndex;

            while(end < input.size()) {
                if(input.get(pos).start != input.get(end).start)
                    break;

                words[end] = wordIndex;
                end++;
            }

            wordOffsets.add(input.get(pos).start);
            wordStarts.add(pos);
            wordEnds.add(end);
            pos = end;
        }
    }

    /**
     * Compute next word indexes for each input span
     */
    public void computeNextWords() {
        for(int i = 0; i < input.size(); i++) {
            int nextPos = nextIndex[i];
            nextWords[i] = nextPos < input.size()? words[nextPos] : wordStarts.size();
        }
    }

    /**
     * Input size, number of span in the input
     * @return
     */
    public int size() {
        return input.size();
    }

    /**
     * Get span by index
     * @param index span index
     * @return
     */
    public SymbolSpan get(int index) {
        return input.get(index);
    }

    /**
     * Find the first input span that starts at the given (or least) offset
     * @param pos offset
     * @return
     */
    public int inputIndex(final int pos) {
        int index = BinarySearch.binarySearch(0, input.size(), new FixedIntComparator() {
            @Override
            public int compare(int i) {
                return Integer.compare(input.get(i).start, pos);
            }
        });

        if(index < 0) {
            return -index - 1;
        }

        while(index > 0) {
            if(input.get(index - 1).start != pos)
                break;
            index--;
        }

        return index;
    }

    public int wordCount() {
        return wordStarts.size();
    }


}
