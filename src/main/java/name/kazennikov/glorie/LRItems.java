package name.kazennikov.glorie;

import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class to compute LR-0 items of the grammar
 */
public class LRItems {

    public static class LRItem {
        public final int rule;
        public final int dot;

        public LRItem(int rule, int dot) {
            this.rule = rule;
            this.dot = dot;
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            LRItem lrItem = (LRItem) o;

            if(dot != lrItem.dot) {
                return false;
            }
            if(rule != lrItem.rule) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = rule;
            result = 31 * result + dot;
            return result;
        }

        @Override
        public String toString() {
            return String.format("[%d,%d]", rule, dot);
        }
    }

    CompiledGrammar g;
    TLongIntHashMap gotoMap = new TLongIntHashMap(100, 0.5f, -1, -1);
    List<Set<LRItem>> itemSets = new ArrayList<>();

    public LRItems(CompiledGrammar g) {
        this.g = g;
    }

    public boolean isFinished(LRItem item) {
        return g.rules[item.rule].rhs.length == item.dot;
    }

    public int symbol(LRItem item) {
        return g.rules[item.rule].rhs[item.dot];
    }


    /**
     * Adds initial items for productions that result in symbol
     * @param symbol LHS symbol (production result)
     * @param items target itemset
     * @return
     */
    public Set<LRItem> addToItemSet(int symbol, Set<LRItem> items) {
        for(CompiledGrammar.Rule p : g.rules) {
            if(p.lhs == symbol) {
                LRItem item = new LRItem(p.id, 0);
                items.add(item);
            }
        }

        return items;
    }

    /**
     * Close an item set.
     * if there is an item of the form A->v*Bw in an item set and  in the grammar there is a rule
     * of form B->w' then  the item B->*w' should also be in the item set.
     * @param itemSet source itemset
     *
     * @return closed itemset
     */
    public Set<LRItem> closeItemSet(Set<LRItem> itemSet) {
        int saveSize;
        Set<LRItem> set = new HashSet<>();
        TIntHashSet alreadyAdded = new TIntHashSet();
        do {
            saveSize = itemSet.size();
            set.clear();
            for(LRItem item : itemSet) {
                if(!isFinished(item)) {
                    int s = symbol(item);
                    if(g.isNT(s) && !alreadyAdded.contains(s)) {
                        addToItemSet(s, set);
                        alreadyAdded.add(s);
                    }
                }
            }

            itemSet.addAll(set);
            set.clear();
        } while(saveSize < itemSet.size());

        return itemSet;
    }





    public void compute() {
        Set<LRItem> itemSet0 = new HashSet<>();
        addToItemSet(g.lrStart, itemSet0);
        closeItemSet(itemSet0);
        itemSets.add(itemSet0);

        for(int i = 0; i < itemSets.size(); i++) {
            for(int s = 0; s <  g.symbols.size(); s++) {
                if(g.isEOF(s))
                    continue;

                Set<LRItem> newItemSet = new HashSet<>();
                for(LRItem item : itemSets.get(i)) {
                    if(!isFinished(item)) {
                        int currSymbol = symbol(item);

                        if(currSymbol == s) {
                            LRItem it = new LRItem(item.rule, item.dot + 1);
                            newItemSet.add(it);
                        }
                    }

                    if(!newItemSet.isEmpty()) {
                        closeItemSet(newItemSet);
                        int newStateNumber = itemSets.indexOf(newItemSet);
                        if(newStateNumber == -1) {
                            newStateNumber = itemSets.size();
                            itemSets.add(new HashSet<LRItem>(newItemSet));
                        }

                        setGoto(i, s, newStateNumber);
                    }

                }
            }

        }
    }

    public void setGoto(int state, int symbol, int dest) {
        long key = state;
        key = key << 32;
        key += symbol;
        gotoMap.put(key, dest);
    }

    public int getGoto(int state, int symbol) {
        long key = state;
        key = key << 32;
        key += symbol;

        return gotoMap.get(key);
    }
}
