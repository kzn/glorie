package name.kazennikov.glorie;

import gnu.trove.list.array.TIntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created on 05.01.16.
 *
 * @author Anton Kazennikov
 */
public class ReductionTrie {

	public static class State {
		//TIntObjectHashMap<State> next = new TIntObjectHashMap<>();
		TIntArrayList input = new TIntArrayList(256);
		List<State> next = new ArrayList<>(256);

		public State next(int input) {
			int pos = this.input.indexOf(input);
			if(pos == -1)
				return null;

			return next.get(pos);
		}

		public void next(int input, State next) {
			this.input.add(input);
			this.next.add(next);
		}

		public void clear() {
			input.resetQuick();
			next.clear();
		}

	}

	//State start;

	State[] starts;

	List<State> states = new ArrayList<>(1024);
	List<State> free = new ArrayList<>(1024);

	public ReductionTrie(int ruleCount) {
		//start = new State();
		starts = new State[ruleCount];
	}

	public State addState() {
		State state;

		if(!free.isEmpty()) {
			state = free.get(free.size() - 1);
			free.remove(free.size() - 1);
		} else {
			state = new State();
		}

		states.add(state);
		return state;
	}

	public void clear() {
		Arrays.fill(starts, null);
		for(State s : states) {
			s.clear();
		}

		free.addAll(states);
		states.clear();
	}

	public void add(TIntArrayList l) {

		State state = starts[l.get(0)];
		if(state == null) {
			state = addState();
			starts[l.get(0)] = state;
		}

		int pos = 1;
		while(pos < l.size()) {
			int input = l.getQuick(pos);
			State next = state.next(input);

			if(next == null) {
				next = addState();
				state.next(input, next);
			}

			state = next;

			pos++;
		}
	}





}
