package name.kazennikov.glorie.filters;

import gnu.trove.list.array.TIntArrayList;

/**
 * Algorithm to resolve overlapping spans.
 * The input spans must be sorted by:
 * - increasing start offsets
 * - decreasing span length (decreasing end offsets)
 * First, algorithm gathers groups of overlapping spans. A group can be processed
 * independently of other groups.
 *
 * Then, it resolves overlapping within groups:
 * 1. Select a non-overlapping set of nodes from the group.
 * The non-overlapping set is defined by the select() function.
 * 2. Remove any overlapping nodes with the non-overlapping set from the group
 * 3. if the group isn't empty then proceed to step 1
 *
 * @author Anton Kazennikov
 */
public abstract class OverlapFilter {

    public static final int UNPROCESSED = 0;
    public static final int RESOLVED = 1;
    public static final int REMOVED = 2;

    // input sequence size
	final int size;

    // 2 - removed
    // 1 - resolved
    // 0 - unprocessed (unresolved)
    final int[] flags;

	public OverlapFilter(int size) {
		this.size = size;
        this.flags = new int[size];
	}

    public TIntArrayList computePairs(TIntArrayList nodes) {
        TIntArrayList pairs = new TIntArrayList();

        for(int i = 0; i < nodes.size(); i++) {
            for(int j = i + 1; j < nodes.size(); j++) {
                int node1 = nodes.getQuick(i);
                int node2 = nodes.getQuick(j);

                if(!overlaps(node1, node2)) {
                    break;
                }

                if(shouldResolve(node1, node2)) {
                    pairs.add(node1);
                    pairs.add(node2);
                }
            }
        }

        return pairs;
    }

    public TIntArrayList computePairs(TIntArrayList pairs, TIntArrayList nodes1, TIntArrayList nodes2) {

        for(int i = 0; i < nodes1.size(); i++) {
            int node1 = nodes1.getQuick(i);
            for(int j = 0; j < nodes2.size(); j++) {
                int node2 = nodes2.getQuick(j);

                if(!overlaps(node1, node2)) {
                    break;
                }

                if(shouldResolve(node1, node2)) {
                    pairs.add(node1);
                    pairs.add(node2);
                }
            }
        }

        return pairs;
    }

	/**
	 * Resolve overlapping
	 *
     * @param nodes list of nodes to resolve
     * @param outNodes list of output nodes
	 *
	 * @return
	 */
	public void resolve(TIntArrayList nodes, TIntArrayList outNodes) {
        if(nodes.isEmpty())
            return;

        if(nodes.size() == 1) {
            outNodes.addAll(nodes);
        }

        TIntArrayList srcNodes = nodes;
        TIntArrayList restNodes = new TIntArrayList(nodes.size());
        TIntArrayList pairs = new TIntArrayList(nodes.size());

        while(!nodes.isEmpty()) {
            TIntArrayList resolved = select(nodes);
            outNodes.addAll(resolved);





            for(int i = 0; i < resolved.size(); i++) {
                flags[resolved.get(i)] = RESOLVED;
            }

            restNodes.resetQuick();
            for(int i = 0; i < nodes.size(); i++) {
                if(flags[nodes.get(i)] == UNPROCESSED) {
                    restNodes.add(nodes.get(i));
                }
            }
            pairs.resetQuick();
            computePairs(pairs, resolved, restNodes);


            // delete overlapping nodes wrt resolved
            for(int i = 0; i < pairs.size(); i+= 2) {
                int from = pairs.get(i);
                int to = pairs.get(i + 1);

                if(flags[from] == RESOLVED) {
                    flags[to] = REMOVED;
                }

                if(flags[to] == RESOLVED) {
                    flags[from] = REMOVED;
                }
            }

            nodes = new TIntArrayList(nodes.size());

            for(int i = 0; i < srcNodes.size(); i++) {
                int node = srcNodes.get(i);
                if(flags[node] == 0) {
                    nodes.add(node);
                }
            }
            srcNodes = nodes;
        }
    }



    /**
	 * Filter input sequence
	 * @return filtered nodes
	 */
	public TIntArrayList filter() {
		int pos = 0;

		TIntArrayList nodes = new TIntArrayList();
		TIntArrayList outNodes = new TIntArrayList();

		while(pos < size) {
            if(!shouldResolve(pos)) {
                pos++;
                continue;
            }


			int overlap = pos + 1;
			int curEnd = end(pos);
			nodes.resetQuick();

			nodes.add(pos);

			while(overlap < size) {

                if(!shouldResolve(overlap)) {
                    overlap++;
                    continue;
                }

				// found start of next group
				if(start(overlap) >= curEnd)
					break;

				curEnd = Math.max(curEnd, end(overlap));

                nodes.add(overlap);
				overlap++;
			}


			resolve(nodes, outNodes);



			pos = overlap;
		}

        if(!nodes.isEmpty()) {
            resolve(nodes, outNodes);
        }

		return outNodes;
	}

    /**
     * Check if two nodes overlap
     * @param i first node index
     * @param j second node index
     * @return
     */
    public boolean overlaps(int i, int j) {
        int start1 = start(i);
        int end1 = end(i);
        int start2 = start(j);
        int end2 = end(j);

        return (start1 <= start2 && end1 >= start2)
                ||
                (start1 <= end2 && end1 >= end2);
    }


    /**
     * Should we resolve the overlapping of two nodes
     * @param i first node index
     * @param j second node index
     * @return
     */
	public abstract boolean shouldResolve(int i, int j);

    /**
     * Should we resolve given node
     * @param i node inde
     * @return
     */
	public abstract boolean shouldResolve(int i);

    /**
     * Select result node from an overlapping node list
     *
     * @param input overlapping node list
     * @return selected nodes
     */
	public abstract TIntArrayList select(TIntArrayList input);

    /**
     * Start offset of node
     * @param i node index
     * @return
     */
	public abstract int start(int i);

    /**
     * End offset of node
     * @param i node index
     * @return
     */
	public abstract int end(int i);



}
