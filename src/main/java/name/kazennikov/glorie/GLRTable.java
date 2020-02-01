package name.kazennikov.glorie;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * GLR Table. Built on top of SLR table algorithm
 */
public class GLRTable {
    private static final Logger logger = Logger.getLogger(GLRTable.class);

    public static class SLRCell {
        boolean shift;
        List<CompiledGrammar.Rule> reduceRules = new ArrayList<>();
        int gotoLine = -1;

        public boolean isEmpty() {
            return gotoLine == -1 && !shift && reduceRules.isEmpty();
        }
    }


    final CompiledGrammar g;

    SLRCell[] table;
    int[] stateIndex;
    int[] checkState;
    int[] checkSyms;

    final int lineSize;
    int stateCount;
    TIntObjectHashMap<SLRCell> cells;

    public GLRTable(CompiledGrammar g) {
        this.g = g;
        this.lineSize = g.symbols.size();
    }

    public int index(int state, int sym) {
        return state * lineSize + sym;
    }

    protected SLRCell getCellHashed(int state, int sym) {
        int key = index(state, sym);
        SLRCell o = cells.get(key);
        if(o == null) {
            o = new SLRCell();
            cells.put(key, o);
        }

        return o;
    }

    /**
     * Constructing the SLR parsing table
     *
     * Compute the canonical LR collection set of LR(0) items for grammar G.
     * let it be C = {I0, I1,...,In}
     * For terminal a, if A->X.aY in Ii anf goto(Ii,a) = Ij, then set action[i,a] to shift j
     *
     * if A->X. is in Ii and A != S',  for all terminals  a in Follow(A) set action [i,a]
     * to  reduce A->X (S' is a new specially  inserted root of the grammar)
     *
     * if S'->S. is in Ii, then set action[i,$]=accept (this is not implemented because we use
     * this grammar for longest match search)
     *
     * For non-terminal symbol A, if goto(Ii,A) = Ij, then set goto(i,A)=j
     * set all other table entries to "error"
     *
     * Since we should use this table for GLR, we should not stop when
     * a conflict occurs, to the contrary we should proceed collection all possible actions in one table  cell.
     */
    public boolean buildGLRTable() {
        TIntSet[] first = g.buildFIRST();
        TIntSet[] follow = g.buildFOLLOW(first);

        LRItems items = new LRItems(g);
        items.compute();

        cells = new TIntObjectHashMap<>();
        stateCount = items.itemSets.size();

        for(int i = 0;  i<items.itemSets.size(); i++) {
            Set<LRItems.LRItem> itemSet = items.itemSets.get(i);

            for(LRItems.LRItem item : itemSet) {
                // "For terminal a, if A->X.aY in Ii and goto(Ii,a) = Ij, then set action[i,a] to shift j"
                if(!items.isFinished(item) && !(g.isNT(items.symbol(item)))) {
                    int s = items.symbol(item);
                    int nextState = items.getGoto(i, s);

                    if(nextState != -1) {
                        SLRCell c = getCellHashed(i, s);//tableLine[s];
                        if(c.gotoLine != nextState) {
                            c.gotoLine = nextState;
                            c.shift = true;
                        }
                    }
                }

                // if A->X. is in Ii and A != S',  for all terminals  a in Follow(A) set action [i,a]
                // to  reduce A->X
                // "and A != S'"
                if(items.isFinished(item) && g.rules[item.rule].lhs != g.lrStart) {
                    TIntIterator it = follow[g.rules[item.rule].lhs].iterator();
                    while(it.hasNext()) {
                        int cId = it.next();
                        SLRCell c = getCellHashed(i, cId);
                        c.reduceRules.add(g.rules[item.rule]);
                    }
                }
            }

            //  going  through non-terminal symbols, initializing GOTO table  for non terminals
            for(int s  = 0; s <  g.symbols.size(); s++) {
                if(!g.isNT(s))
                    continue;

                int nextState = items.getGoto(i, s);
                if(nextState != -1) {
                    getCellHashed(i, s).gotoLine = nextState;
                }
            }
        }

        buildTable();


        logger.info(String.format("GLR table: %dx%d (total %d cells), %d non-empty, double array table: %d",
                items.itemSets.size(), g.symbols.size(),
                items.itemSets.size() * g.symbols.size(),
                cells.size(),
                table.length

                ));


        return true;
    }

    /**
     * Double-array trie style parsing table packing
     *
     * Lookup:
     * cell(state, sym):
     * 1. int offset = stateIndex[state] + sym;
     * 2. if(checkState[offset] != state || checkSym[offset] != sym) => return false
     * 3. return states[offset]
     *
     */
    public void buildTable() {
        int[] lineOffset = new int[lineSize];
        List<SLRCell> table = new ArrayList<>();
        TIntArrayList tableStates = new TIntArrayList();
        TIntArrayList tableSyms = new TIntArrayList();

        stateIndex = new int[stateCount];


        TIntArrayList keys = new TIntArrayList(cells.keys());
        keys.sort();
        Arrays.fill(lineOffset, -1);

        // by state table filling
        int pos = 0;
        int startState = keys.get(0) / lineSize;
        for(int i = 0; i < startState; i++) {
            lineOffset[i] = 0;
        }


        while(pos < keys.size()) {
            int end = pos + 1;
            int state = keys.get(pos) / lineSize;
            int lastCol = keys.get(pos) % lineSize;

            while(end < keys.size()) {
                if(keys.get(end) / lineSize != state)
                    break;
                lastCol = keys.get(end) % lineSize;
                end++;
            }



            // state i = [pos...end)
            boolean fitted = false;
            outer:
            for(int i = 0; i < table.size() - lastCol; i++) {
                // fit cells
                for(int st = pos; st < end; st++) {
                    int col = keys.get(st) % lineSize;
                    if(table.get(i + col) != null) {
                        continue outer;
                    }
                }
                fitted = true;
                stateIndex[state] = i;

                for(int st = pos; st < end; st++) {
                    int col = keys.get(st) % lineSize;
                    table.set(i + col, cells.get(keys.get(st)));
                    tableStates.set(i + col, keys.get(st) / lineSize);
                    tableSyms.set(i + col, keys.get(st) % lineSize);
                }
                break;
            }

            if(!fitted) {
                int i = table.size();
                stateIndex[state] = i;
                expand(table, table.size() + lineSize);
                expand(tableStates, tableStates.size() + lineSize, -1);
                expand(tableSyms, tableSyms.size() + lineSize, -1);

                for(int st = pos; st < end; st++) {
                    int col = keys.get(st) % lineSize;
                    table.set(i + col, cells.get(keys.get(st)));
                    tableStates.set(i + col, keys.get(st) / lineSize);
                    tableSyms.set(i + col, keys.get(st) % lineSize);
                }

            }



            // start from next state
            pos = end;
        }

        this.table = table.toArray(new SLRCell[table.size()]);
        this.checkState = tableStates.toArray();
        this.checkSyms = tableSyms.toArray();

        logger.info(String.format("Fitted table size: %d, cells: %d", table.size(), cells.size()));
    }


    public SLRCell getCell(int state, int sym) {
        int offset = stateIndex[state] + sym;

        if(offset >= checkState.length)
            return null;

        if(checkState[offset] != state || checkSyms[offset] != sym)
            return null;

        return table[offset];
    }

    public static <E> void expand(List<E> l, int newSize) {
        if(l.size() >= newSize)
            return;
        int count = newSize - l.size();
        for(int i = 0; i < count; i++) {
            l.add(null);
        }
    }

    public static void expand(TIntArrayList l, int newSize, int fill) {
        if(l.size() >= newSize);
        int count = newSize - l.size();
        for(int i = 0; i < count; i++) {
            l.add(fill);
        }
    }

    /**
     * Print table
     * @param f output file
     */
    public void print(File f) throws IOException {
        try(PrintWriter pw = new PrintWriter(f, "UTF-8")) {
            pw.printf("Grammar:%n");

            for(CompiledGrammar.Rule r : g.rules) {
                pw.printf("%d. %s ->", r.id, g.symbols.get(r.lhs).id);
                for(int i = 0; i < r.rhs.length; i++) {
                    pw.printf(" %s", g.symbols.get(r.rhs[i]).id);
                }
                pw.println();
            }

            pw.println();

            pw.printf("Table:%n");
            pw.printf("state");
            for(Symbol s : g.symbols.entries()) {
                if(!s.nt)
                    pw.printf("\t%s", s.id);
            }

            for(Symbol s : g.symbols.entries()) {
                if(s.nt)
                    pw.printf("\t%s", s.id);
            }

            pw.println();

            for(int i = 0; i < stateCount; i++) {
                pw.print(i);
                for(int j = 0; j < lineSize; j++) {
                    if(!g.symbols.get(j).nt) {
                        pw.print("\t");
                        SLRCell cell = getCell(i, j);
                        if(cell == null)
                            continue;
                        if(cell.gotoLine != -1) {
                            pw.printf("S" + cell.gotoLine);
                        }

                        for(CompiledGrammar.Rule r : cell.reduceRules) {
                            pw.printf("/R%d", r.id);
                        }
                    }
                }

                for(int j = 0; j < g.symbols.size(); j++) {
                    if(g.symbols.get(j).nt) {
                        pw.print("\t");
                        SLRCell cell = getCell(i, j);
                        if(cell == null)
                            continue;
                        if(cell.gotoLine != -1) {
                            pw.print(cell.gotoLine);
                        }

                        for(CompiledGrammar.Rule r : cell.reduceRules) {
                            pw.printf("/R%d", r.id);
                        }
                    }
                }
                pw.println();

            }

        }


    }

    /**
     * Copy table data
     * @param dest destination
     */
    public void copy(GLRTable dest) {
        dest.table = this.table;
        dest.stateIndex = stateIndex;
        dest.checkState = checkState;
        dest.checkSyms = checkSyms;
        dest.stateCount = stateCount;
        dest.cells = cells;
    }

}
