package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Production greediness filter.
 *
 * The filter is applied after all possible reductions at given position are computed.*
 * It deletes all parses that conflict with performed greedy reduction.

 * The filter simultaneously applies all greedy policies. A a parse tree state/node is valid
 * if it is accepted by all greedy policies. In all other cases the state/node is considered invalid
 * and all node/states that are derived from this state/node are considered invalid too.
 *
 *
 * The filter is applied sequentially to:
 * 1. pending shifts of the current position
 * 2. computed root nodes
 * 3. all 'word' positions for current one. It will filter out all nodes built by conflicting productions
 * at earlier stages of parsing as the input is a lattice, not a sequence of symbols.
 *
 *
 * @author Anton Kazennikov
 */
public class GreedyFilter {


	GLRParser parser;
	int currentPos = 0;
    List<GreedyVisitor> visitors = new ArrayList<>();
    GreedyPolicy.Result[] visitedSymbolNodes;
	GreedyPolicy.Result[] visitedStateNodes;



	public GreedyFilter(GLRParser parser, int currentPos) {
		this.parser = parser;

		visitedStateNodes = new GreedyPolicy.Result[stateNodeCount()];
		visitedSymbolNodes = new GreedyPolicy.Result[symbolNodeCount()];

		visitedStateNodes[parser.startNode.index] = GreedyPolicy.Result.ACCEPT;

		this.currentPos = currentPos;

        for(GreedyPolicy p : parser.grammar.policies) {
            GLRParser.PerformedReduction r = p.max(parser.reductions);
            if(r != null) {
                visitors.add(new GreedyVisitor(this, p, r));
            }
        }
	}

	public int symbolNodeCount() {
		return parser.lastSymbolNodeId + 1;
	}

	public int stateNodeCount() {
		return parser.lastStateNodeId + 1;
	}

    /**
     * Filter symbol node
     * @param n source symbol node
     * @return n, or null if the symbol node is filtered
     */
    public SymbolNode visit(SymbolNode n) {
        GreedyPolicy.Result res = visitedSymbolNodes[n.index];
        if(res != null) {
            return res == GreedyPolicy.Result.REMOVE? null : n;
        }

        for(GreedyVisitor v : visitors) {
            if(v.visit(n) == null) {
                visitedSymbolNodes[n.index] = GreedyPolicy.Result.REMOVE;
                return null;
            }
        }

        visitedSymbolNodes[n.index] = GreedyPolicy.Result.ACCEPT;
        return n;
    }


    /**
     * Filter state node
     * @param n state node
     * @return n, or null if the state node is filtered
     */
    public StateNode visit(StateNode n) {
        GreedyPolicy.Result res = visitedStateNodes[n.index];
        if(res != null) {
            return res == GreedyPolicy.Result.REMOVE? null : n;
        }

        for(GreedyVisitor v : visitors) {
            if(v.visit(n) == null) {
                visitedStateNodes[n.index] = GreedyPolicy.Result.REMOVE;
                return null;
            }
        }

        visitedStateNodes[n.index] = GreedyPolicy.Result.ACCEPT;
        return n;
    }


    /**
     * Apply filter to the parse state
     */
	public void filter() {
        if(visitors.isEmpty())
            return;

		List<GLRParser.PendingShift> shifts = new ArrayList<>(parser.pendingShifts.size());

        // filter pending shifts
		for(GLRParser.PendingShift shift : parser.pendingShifts) {
			StateNode n = visit(shift.stateNode);
			if(n != null) {
				shifts.add(shift);
			}
		}

		parser.pendingShifts.clear();
		parser.pendingShifts.addAll(shifts);


        List<SymbolNode> roots = new ArrayList<>(parser.roots.size());

		// filter already built root nodes
		for(SymbolNode root : parser.roots) {
			if(visit(root) != null)
				roots.add(root);
		}

        parser.roots.clear();
        parser.roots.addAll(roots);


		// filter next positions state, as the may be built earlier
		for(int i = currentPos; i <= parser.maxWordState; i++) {

			List<StateNode> wordNodes = parser.nodes4word.get(i);

			if(wordNodes.isEmpty())
				continue;

			List<StateNode> nodes = new ArrayList<>();


			for(StateNode n : wordNodes) {
				if(visit(n) != null)
					nodes.add(n);
			}

			wordNodes.clear();
			wordNodes.addAll(nodes);
		}
	}
}
