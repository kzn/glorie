package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kzn on 3/17/16.
 */
public class GreedyVisitor {

    GLRParser.PerformedReduction r;
    GreedyFilter filter;
    GreedyPolicy policy;
	GreedyPolicy.Result[] visitedSymbolNodes;
	GreedyPolicy.Result[] visitedStateNodes;


    public GreedyVisitor(GreedyFilter filter, GreedyPolicy policy, GLRParser.PerformedReduction r) {
        this.filter = filter;
        this.policy = policy;
        this.r = r;
		visitedStateNodes = new GreedyPolicy.Result[filter.stateNodeCount()];
		visitedSymbolNodes = new GreedyPolicy.Result[filter.symbolNodeCount()];
    }

    public SymbolNode visit(SymbolNode n) {
        if(filter.visitedSymbolNodes[n.index] == GreedyPolicy.Result.REMOVE)
            return null;

        GreedyPolicy.Result res = visitedSymbolNodes[n.index];

        // if visited, return it
        if(res != null) {
            return res == GreedyPolicy.Result.REMOVE? null : n;
        }

        GreedyPolicy.Result status = policy.check(r, this, n);

        if(status == GreedyPolicy.Result.REMOVE) {
            visitedSymbolNodes[n.index] = GreedyPolicy.Result.REMOVE;

            return null;
        } else if(status == GreedyPolicy.Result.ACCEPT) {
            visitedSymbolNodes[n.index] = GreedyPolicy.Result.ACCEPT;
            return n;
        }


        List<StackNode> filtered = new ArrayList<>(n.childCount());

        for(int i = 0; i < n.childCount(); i++) {
            StateNode child = n.getChild(i);
            StateNode after = visit(child);

			if(after != null) {
                filtered.add(after);
            }

        }

        if(filtered.isEmpty()) {
            visitedSymbolNodes[n.index] = GreedyPolicy.Result.REMOVE;
            return null;
        }

        n.children = filtered;
        visitedSymbolNodes[n.index] = GreedyPolicy.Result.ACCEPT;

        return n;
    }


    public StateNode visit(StateNode n) {

        if(n.state == 0)
            return n;

        if(filter.visitedStateNodes[n.index] == GreedyPolicy.Result.REMOVE)
            return null;

        GreedyPolicy.Result res = visitedStateNodes[n.index];

        // if visited, return it
        if(res != null) {
            return res == GreedyPolicy.Result.REMOVE? null : n;
        }

        List<StackNode> filtered = new ArrayList<>(n.childCount());

        for(int i = 0; i < n.childCount(); i++) {
            SymbolNode child = n.getChild(i);
            SymbolNode after = visit(child);

            if(after != null) {
                filtered.add(after);
            }

        }

        if(filtered.isEmpty()) {
            visitedStateNodes[n.index] = GreedyPolicy.Result.REMOVE;
            return null;
        }

        n.children = filtered;
        visitedStateNodes[n.index] = GreedyPolicy.Result.ACCEPT;

        return n;
    }


}
