package name.kazennikov.glorie;

import java.util.ArrayList;
import java.util.List;

/**
 * Symbol node of Graph structured stack
 */
public class SymbolNode extends StackNode {
	public final SymbolSpan symbol;


	public SymbolNode(SymbolSpan symbol) {
		this.symbol = symbol;
	}

	// parseChildren[i] is one possible set of children of this symbol node in the parse tree
	// if parseChildren.size() > 1, then a "local ambiguity packing" has occurred.
	// if parseChildren.isEmpty(), then this node is a leaf in the parsing tree.
	public List<GLRParser.ParsingChildrenSet> parseChildren = new ArrayList<>(1);

	@Override
	public StateNode getChild(int index) {
		return (StateNode) super.getChild(index);
	}

	@Override
	public String toString() {
		return String.format("{%s[%d,%d]}", symbol.symbol, symbol.start, symbol.end);
	}

	public void addParse(GLRParser.ParsingChildrenSet parse) {
		parseChildren.add(parse);
	}
}
