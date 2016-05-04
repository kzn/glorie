package name.kazennikov.glorie;

/**
 * State node of graph structured stack
 */
public class StateNode extends StackNode {
	public final int state;
	boolean active;	// indicate that the node is in activeStates list


	public StateNode(int state) {
		this.state = state;
	}

	@Override
	public SymbolNode getChild(int index) {
		return (SymbolNode) super.getChild(index);
	}


	@Override
	public String toString() {
		return "{" + state + "}";
	}

}
