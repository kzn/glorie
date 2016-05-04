package name.kazennikov.glorie.filters;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.List;

/**
 * List-based input overlap filter
 *
 * @author Anton Kazennikov
 */
public class ListOverlapFilter<E> extends OverlapFilter {
	List<E> input;
	OverlapResolver<E> resolver;

	public ListOverlapFilter(List<E> input, OverlapResolver<E> resolver) {
		super(input.size());
		this.input = input;
		this.resolver = resolver;
	}

	public List<E> apply() {
		TIntArrayList out = filter();
		List<E> res = new ArrayList<>(out.size());
		for(int i = 0; i < out.size(); i++) {
			res.add(input.get(out.get(i)));
		}

		return res;
	}


	@Override
	public boolean shouldResolve(int i, int j) {
		return resolver.shouldResolve(input.get(i), input.get(j));
	}

    @Override
    public boolean shouldResolve(int i) {
        return resolver.shouldResolve(input.get(i));
    }

    @Override
	public TIntArrayList select(TIntArrayList input) {
		List<E> elements = new ArrayList<>(input.size());
		for(int i = 0; i < input.size(); i++) {
			elements.add(this.input.get(input.get(i)));
		}

		List<E> out = resolver.select(elements);
		TIntArrayList selected = new TIntArrayList(out.size());
		for(E e : out) {
			selected.add(input.get(elements.indexOf(e)));
		}

		return selected;
	}

    @Override
    public int start(int i) {
        return resolver.start(input.get(i));
    }

    @Override
    public int end(int i) {
        return resolver.end(input.get(i));
    }
}
