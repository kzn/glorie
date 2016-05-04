package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Created on 16.03.16.
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


	public void filter() {
        if(visitors.isEmpty())
            return;

		List<GLRParser.PendingShift> shifts = new ArrayList<>(parser.pendingShifts.size());

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


		// filter next states
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

//	public SymbolNode visitSymbolNode(SymbolNode n) {
//		int res = visited.get(n);
//
//		// if visited, return it
//		if(res >= 0) {
//			return res == REMOVED? null : n;
//		}
//
//		int status = checkSymbolNode(n);
//
//		if(status == REMOVED) {
//			visited.put(n, REMOVED);
//			return null;
//		} else if(status == ALIVE_STOP) {
//			visited.put(n, ALIVE);
//			return n;
//		}
//
//
//		List<StackNode> filtered = new ArrayList<>(n.childCount());
//
//		for(int i = 0; i < n.childCount(); i++) {
//			StateNode child = n.getChild(i);
//			StateNode after = visitStateNode(child);
//			if(after != null) {
//				filtered.add(after);
//			}
//
//		}
//
//		if(filtered.isEmpty()) {
//			visited.put(n, REMOVED);
//			return null;
//		}
//
//		n.children = filtered;
//		visited.put(n, ALIVE);
//
//		return n;
//	}
//
//
//	public StateNode visitStateNode(StateNode n) {
//		int res = visited.get(n);
//
//		// if visited, return it
//		if(res >= 0) {
//			return res == REMOVED? null : n;
//		}
//
//		List<StackNode> filtered = new ArrayList<>(n.childCount());
//
//		for(int i = 0; i < n.childCount(); i++) {
//			SymbolNode child = n.getChild(i);
//			SymbolNode after = visitSymbolNode(child);
//
//			if(after != null) {
//				filtered.add(after);
//			}
//
//		}
//
//		if(filtered.isEmpty()) {
//			visited.put(n, REMOVED);
//			return null;
//		}
//
//		n.children = filtered;
//		visited.put(n, ALIVE);
//
//		return n;
//	}
//
//
//	/**
//	 * Check if symbol node pass the greedy filter
//	 *
//	 * @param n symbol node to check
//	 * @return true, if symbol node successfully passed the filter, false if it is removed
//	 */
//	public int checkSymbolNode(SymbolNode n) {
//
//		// terminals pass the filter unconditionally
//		if(n.parseChildren.isEmpty())
//			return ALIVE_STOP;
//
//		// symbols that end before greedy reduction also pass the filter
//		if(n.symbol.end <= maxReduction.sym.symbol.start)
//			return ALIVE_STOP;
//
//		boolean sameSymbol = n.symbol.symbol == maxReduction.sym.symbol.symbol;
//
//		// for now, greediness is applied only for symbols of same type
//		// for now, fail unconditionally any symbol that start after the greedy reduction start
//		if(sameSymbol && (n.symbol.start > maxReduction.sym.symbol.start || n.symbol.end < maxReduction.sym.symbol.end))
//			return REMOVED;
//
//		// for now we have only non-terminals that start before or strictly from greedy reduction start
//
//		List<GLRParser.ParsingChildrenSet> parses = new ArrayList<>(n.parseChildren.size());
//
//
//		boolean start = n.symbol.start == maxReduction.sym.symbol.start;
//
//		for(GLRParser.ParsingChildrenSet parse : n.parseChildren) {
//			CompiledGrammar.Rule r = parse.rule;
//
//			// if co-start && parse is greedy, pass
//			if(start && sameSymbol) {
//				if(r.production.greedy) {
//					parses.add(parse);
//				}
//
//				// skip non-greedy co-start
//				continue;
//			}
//
//			boolean pass = true;
//			for(SymbolNode child : parse.items) {
//				if(visitSymbolNode(child) == null) {
//					pass = false;
//					break;
//				}
//			}
//
//
//			if(pass) {
//				parses.add(parse);
//			}
//
//
//		}
//
//		n.parseChildren.clear();
//
//		if(parses.isEmpty())
//			return REMOVED;
//
//		n.parseChildren.addAll(parses);
//
//		return ALIVE;
//	}
//

}
