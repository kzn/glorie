package name.kazennikov.glorie.filters;

import java.util.List;

/**
 * Resolver as an interface
 * @param <E>
 */
public interface OverlapResolver<E> {

    public boolean shouldResolve(E node);

	/**
	 * Test if these tho nodes should be resolved. If not, they remain "as is"
	 * @param node1 first node
	 * @param node2 second node
	 * @return
	 */
	public boolean shouldResolve(E node1, E node2);

	/**
	 * Select highest ranking nodes from a list of overlapped nodes.
	 * The function returns a list, allowing to select several equally ranked overlapping nodes.
	 *
	 * @param nodeList node list
	 * @return list of resolved nodes, if the return list is null or empty then the algorithm assumes that all nodes
	 * are selected
	 */
	public List<E> select(List<E> nodeList);

    public int start(E node);
    public int end(E node);
}
