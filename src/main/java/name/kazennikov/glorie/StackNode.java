package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Node of Graph Structured Stack of GLR Parsing algorithm
 *
 * @author Anton Kazennikov
 */
public abstract class StackNode {
	public int index = -1;
	protected List<StackNode> children = new ArrayList<>(4);
    /**
     * Get child node by index
     * @param index
     * @return
     */
	public StackNode getChild(int index) {
		return children.get(index);
	}

    /**
     * Get number of childs
     * @return
     */
    public int childCount() {
		return children.size();
	}


}
