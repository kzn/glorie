package name.kazennikov.glorie;

import gate.Document;
import gnu.trove.list.array.TIntArrayList;
import name.kazennikov.logger.Logger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * GLR Parser implementation
 */
public class GLRParser {

    private static final Logger logger = Logger.getLogger();

    // invalid span object, used in spanMaps cache
    public static final SymbolSpan INVALID_SPAN = new SymbolSpan(-1, null, -1, 0, 0, null, null, 0.0);


    /**
     * Single parse of a single of a non-terminal symbol
     */
    public static class ParsingChildrenSet {
        public final CompiledGrammar.Rule rule;
        public final List<SymbolNode> items;

        public ParsingChildrenSet(CompiledGrammar.Rule rule, List<SymbolNode> items) {
            this.rule = rule;
            this.items = items;
        }

        public int itemCount() {
            return items.size();
        }

        public SymbolNode item(int index) {
            return items.get(index);
        }
    }


    /**
     * Pending reduction action
     */
    public static class PendingReduction {
        SymbolNode symbolNode;      // symbol node (child of the lastStateNode) for the reduction
        StateNode lastStateNode;    // last state node (top of gss) for the reduction
        CompiledGrammar.Rule rule;  // reduction rule
		int currentPos = -1;        // current word position
    }


    /**
     * Performed reduction
     */
	public static class PerformedReduction {
		CompiledGrammar.Rule rule;          // reduction rule

		public final StateNode firstState;  // first reduced state
		public final StateNode lastState;   // last reduced state
		public final StateNode state;       // state created by this reduction
		public final SymbolNode sym;        // created symbol node

		public PerformedReduction(CompiledGrammar.Rule rule, StateNode firstState, StateNode lastState, StateNode state, SymbolNode sym) {
			this.rule = rule;
			this.firstState = firstState;
			this.lastState = lastState;
			this.state = state;
			this.sym = sym;
		}
	}

    /**
     * Pending shift action
     */
    public static class PendingShift {
        SymbolSpan symbol;      // symbol that triggered the shift
        StateNode stateNode;    // state that triggered the shift

        int nextState;          // next state
        int nextWord;           // next word index
    }

    // pending reductions of current word position
	List<PendingReduction> pendingReductions = new ArrayList<>();

    // pending shifts of current word position
	List<PendingShift> pendingShifts = new ArrayList<>();

    // active state nodes of current word positions
	List<StateNode> activeStates = new ArrayList<>();

    // performed reductions of current word position
	List<PerformedReduction> reductions = new ArrayList<>();


    // Graph Structured Stack
    StateNode startNode;
    SymbolSpan eof;

	int maxWordSymbolNodes = 4096; // max symbol node count for current position, 0 for unlimited

    // active states per word position
    List<List<StateNode>> nodes4word = new ArrayList<>();

    int maxWordState = 0; // current maximum word with at least one state

    // list of created root nodes
    List<SymbolNode> roots = new ArrayList<>();

    InputData inputData;
    SymbolSpanPredicateEvaluator predEval;
    boolean[] starts;

    final GLRTable table;
    final CompiledGrammar grammar;
    int[] reduceAttemptCount;
    int[] reduceSuccessCount;

    final int maxSynthSize; // maximum number of synth terminals for a grammar type

    byte[] synthCache;      // cache for synth terminals [spanId * maxSynthSize + synthId] = {true, false}

	// path of symbols, used in reducer()
	List<StackNode> reducePath = new ArrayList<>(256);

	// rhs spans used during process()
	List<SymbolSpan> reduceRHS = new ArrayList<>(256);

    int lastSymbolNodeId = 0;   // last id of a symbol node
	int lastStateNodeId = 1;	// last id, of a start node, start node has id = 0

	int wordSymbolNodes; // number of symbol nodes built for current word


    // reduction cache, minimizes of predicate checks and reduce action invocations
    // spanMaps[rule.id]: a map of TIntArrayList of SymbolSpan symbol ids to target SymbolSpan
	Map[] spanMaps;

    // reduction cache stats
    int reduceCacheHits = 0;
    int reducerCacheMisses = 0;




    public GLRParser(GLRTable table) {
        this.table = table;
        this.grammar = table.g;
        eof = new SymbolSpan(grammar.eof, "Split", -1, Integer.MAX_VALUE, Integer.MAX_VALUE, null, null, 1.0);
        this.maxSynthSize = table.g.maxSynthSize;

        // stats
        reduceAttemptCount = new int[table.g.rules.length + 1];
        reduceSuccessCount = new int[table.g.rules.length + 1];
		spanMaps = new HashMap[this.table.g.rules.length + 1];
		for(int i = 0; i < spanMaps.length; i++) {
			spanMaps[i] = new HashMap();
		}
    }

	public int getMaxWordSymbolNodes() {
		return maxWordSymbolNodes;
	}

	public void setMaxWordSymbolNodes(int maxWordSymbolNodes) {
		this.maxWordSymbolNodes = maxWordSymbolNodes;
	}

	public void init(Document doc, List<SymbolSpan> input) {
        startNode = new StateNode(0);
		startNode.index = 0;
        lastSymbolNodeId = 0;
		lastStateNodeId = 1;
        inputData = new InputData(doc, input);

        for(int i = 0; i < inputData.wordStarts.size() + 1; i++) {
            nodes4word.add(new ArrayList<StateNode>(128)); // word[i] nodes
        }



        predEval = new SymbolSpanPredicateEvaluator(grammar, inputData);
        synthCache = new byte[inputData.size() * maxSynthSize];

        Arrays.fill(synthCache, (byte) -1);
        starts = walkPrefixTrie();
    }


    public int baseSynthCacheIndex(int spanId) {
        return spanId * maxSynthSize;
    }


    /**
     * Parse state for prefix FSA walker
     */
    public static class ParseState {
        int start;
        int state;
        int pos;

        public ParseState(int start, int state, int pos) {
            this.start = start;
            this.state = state;
            this.pos = pos;
        }
    }

    private static ParseState GUARD_STATE = new ParseState(-1, -1, -1);


    /**
     * Walk prefix Trie
     * @return array of boolean start[i] = true, if there is a prefix trie match from word i
     */
    boolean[] walkPrefixTrie() {
        boolean[] starts = new boolean[inputData.wordStarts.size()];
        int curr = 0;
        ArrayDeque<ParseState> prefixStates = new ArrayDeque<>(16);
        prefixStates.clear();


        while(curr < inputData.wordStarts.size()) {

            prefixStates.addLast(new ParseState(curr, 0, curr));
            prefixStates.addLast(GUARD_STATE);
            int start = inputData.wordStarts.get(curr);
            int end = inputData.wordEnds.get(curr);

            while(!prefixStates.isEmpty()) {

                ParseState state = prefixStates.pollFirst();

                if(state == GUARD_STATE) {
                    break;
                }

                if(state.pos != curr) {
                    prefixStates.addLast(state);
                    continue;
                }

                if(grammar.prefixFSA.isFinalState(state.state)) {
                    starts[state.start] = true;
                    continue;
                }


                if(starts[state.start]) {
                    continue;
                }

                for(int i = start; i < end; i++) {
                    SymbolSpan ss = inputData.get(i);
                    if(ss.symbol == -1)
                        continue;

                    // walk plain terminal
                    int input = ss.symbol;
                    int nextState = grammar.prefixFSA.next(state.state, input);
                    if(nextState != -1) {
                        if(grammar.prefixFSA.isFinalState(nextState)) {
                            starts[state.start] = true;
                            continue;
                        }
                        prefixStates.addLast(new ParseState(state.start, nextState, inputData.nextWords[i]));
                    }

                    // try to walk synth terminals

                    SynthTerminalEvaluator eval = grammar.evaluators[ss.symbol];
                    if(eval != null) {
                        int synthBaseIndex = baseSynthCacheIndex(ss.id);

                        for(int j = 0; j < eval.size(); j++) {
                            byte val = synthCache[synthBaseIndex + j];
                            // need to eval
                            if(val == -1) {
                                int predId = eval.predIds.get(j);
                                val = (byte) (predEval.eval(predId, ss)? 1 : 0);
                                synthCache[synthBaseIndex + j] = val;
                            }

                            if(val == 1) {
                                int symId = eval.typeIds.get(j);
                                int nextState1 = grammar.prefixFSA.next(state.state, symId);
                                if(nextState1 != -1) {
                                    if(grammar.prefixFSA.isFinalState(nextState1)) {
                                        starts[state.start] = true;
                                        continue;
                                    }
                                    prefixStates.addLast(new ParseState(state.start, nextState1, inputData.nextWords[i]));
                                }

                            }
                        }

                        // walk fsa
                        for(int j = 0; j < eval.accessors.size(); j++) {
                            int accId = eval.accessors.get(j);
                            FeatureAccessor fa = grammar.accessors.get(accId);
                            Object val = fa.get(predEval, ss);
                            if(val == null)
                                continue;
                            int objId = grammar.fsaPredValues.get(val, false);
                            // there is something
                            if(objId != 0) {
                                int s = 0;
                                s = grammar.predFSA.next(s, accId);
                                if(s == -1)
                                    continue;
                                s = grammar.predFSA.next(s, objId);
                                if(s == -1)
                                    continue;
                                int trStart = grammar.predFSA.stateStart(s);
                                int trEnd = grammar.predFSA.stateEnd(s);

                                for(int k = trStart; k < trEnd; k++) {
                                    int symId = grammar.predFSA.label(k);
                                    int nextState1 = grammar.prefixFSA.next(state.state, symId);
                                    if(nextState1 != -1) {
                                        if(grammar.prefixFSA.isFinalState(nextState1)) {
                                            starts[state.start] = true;
                                            continue;
                                        }
                                        prefixStates.addLast(new ParseState(state.start, nextState1, inputData.nextWords[i]));
                                    }


                                }
                            }
                        }
                    }
                }



            }
            curr++;
        }

        while(!prefixStates.isEmpty()) {
            ParseState state = prefixStates.pollFirst();

            if(state == GUARD_STATE)
                continue;
            if(grammar.prefixFSA.isFinalState(state.state)) {
                starts[state.start] = true;
            }
        }

        return starts;
    }



    public void actor(List<SymbolSpan> symbols, int currentPos) {

        StateNode node = activeStates.get(activeStates.size() - 1);
		activeStates.remove(activeStates.size() - 1);
        node.active = false;

        for(SymbolSpan symbol : symbols) {

            // skip non-grammar symbols
            if(symbol.symbol == -1)
                continue;

            GLRTable.SLRCell cell = table.getCell(node.state, symbol.symbol);

            if(cell == null) {
                continue;
            }

            if(cell.shift) {
                PendingShift shift = new PendingShift();
                shift.stateNode = node;
                shift.nextState = cell.gotoLine;
                shift.symbol = symbol;
                shift.nextWord = inputData.nextWords[symbol.id];
                pendingShifts.add(shift);
            }

            for(CompiledGrammar.Rule rule : cell.reduceRules) {
                for(int childIdx = 0; childIdx < node.childCount(); childIdx++) {
					SymbolNode child = node.getChild(childIdx);
                    PendingReduction reduction = new PendingReduction();
                    reduction.rule = rule;
                    reduction.symbolNode = child;
                    reduction.lastStateNode = node;
					reduction.currentPos = currentPos;
                    pendingReductions.add(reduction);
                }
            }
        }

    }

    /**
	SHIFTER(i)
	while (pendingShifts != 0) {
		remove an element (v,k) from pendingShifts
		if there is no node, w,  labelled k  in the last input related closure create one
		if w does not have a successor mode,u, labelled SymbolNo create one
		if u is not already a predecessor of v, make it one
	};

    */
    public void shifter() {

        while(!pendingShifts.isEmpty()) {
            PendingShift shift = pendingShifts.get(pendingShifts.size() - 1);
			pendingShifts.remove(pendingShifts.size() - 1);

            StateNode node = createOrReuseStateNode(shift.nextState, shift.nextWord, false);
            SymbolNode symbolNode = null;

            for(int childIdx = 0; childIdx < node.childCount(); childIdx++) {
				SymbolNode child = node.getChild(childIdx);

                if(child.symbol == shift.symbol) {
                    symbolNode = child;
                    break;
                }
            }

            if(symbolNode == null) {
                symbolNode = new SymbolNode(shift.symbol);
                symbolNode.index = lastSymbolNodeId++;


                node.children.add(symbolNode);

            }

            if(symbolNode.children.indexOf(shift.stateNode) == -1) {
                symbolNode.children.add(shift.stateNode);
            }
        }
    }

    /**
     * This function creates or re-uses a  node which should be in the current input related closure set
     * and which should have StateNo associated with it.
     *
     * @param state state number
     * @param word word index
     * @param addToActive if true, and if there was created a new node, then add that node to the active set
     * @return +idx on new node, -idx on reuse
     */
    StateNode createOrReuseStateNode(int state, int word, boolean addToActive) {
        // always return the global start node
        if(startNode.state == state) {
            return startNode;
        }

        List<StateNode> nodes = nodes4word.get(word);
        for(int index = 0; index < nodes.size(); index++) {
            if(nodes.get(index).state == state)
                return nodes.get(index);
        }

        maxWordState = Math.max(maxWordState, word);
        StateNode node = new StateNode(state);
		node.index = lastStateNodeId++;
        nodes.add(node);

        if(addToActive) {
            activeStates.add(node);
            node.active = true;
        }

        return node;
    }


    /**
     * This  function is a recursive depth-first search in GSS (see Internet for documentation).
     * when a reducePath of length 2*RuleLength-1 is found the reduction action (procedure ReduceOnePath) is called.
     *
     * Here is description of this procedure.
     * We use DFS-stack which is a sequence of pairs
     * <first[1],end[1]>, <first[2],end[2]>,...,<first[n],end[n]>,  where  for each 1<i<=n,
     *  <first[i],end[i]> are all unobserved children of node first[i-1]. Each iteration we made
     * the following:
     * 1. the current  reducePath is not long enough  and  the current top of the DFS stack has children
     *    push to DFS stack all children of current top.
     * 2. otherwise  we go to the neighbour or to the parent's neighbour of the current top, if a neighbour doesn't exist
     *
     */

    public void reducer(List<SymbolSpan> symbols) {
        PendingReduction reduction = pendingReductions.get(pendingReductions.size() - 1);
		pendingReductions.remove(pendingReductions.size() - 1);

        // adding the first item of the DFS-stack
        reducePath.add(reduction.lastStateNode);
        reducePath.add(reduction.symbolNode);

        reduce(reduction, symbols, reduction.symbolNode, reducePath);
        reducePath.clear();
    }

    public boolean reduce(PendingReduction reduction, List<SymbolSpan> symbols, StackNode current, List<StackNode> path) {

        if(path.size() == reduction.rule.reductionPathSize) {
            return reduceOnePath(symbols, reduction, path);
        }

        for(int i = 0; i < current.childCount(); i++) {
            StackNode child = current.getChild(i);

            path.add(child);

            if(!reduce(reduction, symbols, child, path)) {
				return false;
			}

            path.remove(path.size() - 1);
        }

		return true;
    }


    /**
     * Remove (u,j) from R, where  u is a symbol node, and j is a rule number
     * let RuleLength be the length of the right hand side od rule j
     * let LeftPart be the symbol on the left hand side of rule j
     * foreach  stateNode node w which can be reached from u along a reducePath of length (2*RuleLength-1) do
     * 	{
     * 	    let k be the label of the w( = "ChildNode.m_StateNo")
     * 	    let "goto l" be the entry in position (k, LeftPart) in the parsing table (="Cell.m_GotoLine")
     * 	    if there is no node in the last input related closure set labeled l then
     * 	    create a new stateNode node in the GSS labelled l and  add it to  the last input related closure set
     * 	    and to activeStates
     * 	    let  v be the node in the last input related closure labelled l (==NewNodeNo)
     * 	    if there is a reducePath of length 2  in the GSS from v to w then do nothing
     *   	    else
     *   	    {
     *   	        create a new symbol node u' in the GSS labelled LeftPart
     *   	        make u' successor of v and a predecessor of w
     *   	        if v  is not in A
     *   	        {
     *   	        for all reductions rk in position  (l, SymbolNo) of the table add (u,k) to  R
     *   	        }
     *   	    };
     * };
     */
	/**
	 *
	 * @param symbols reduced symbols
	 * @param reduction reduction to apply
	 * @param path path of the reduction in the GSS
	 * @return true, if search for possible reductions path should be continued
	 */
    public boolean reduceOnePath(List<SymbolSpan> symbols, PendingReduction reduction, List<StackNode> path) {
        reduceAttemptCount[reduction.rule.id]++;
        StateNode firstStateNode = (StateNode) path.get(path.size() - 1);
        int leftPart  = reduction.rule.lhs;

        SymbolSpan newSpan = process(reduction, path);

        if(newSpan == null)
            return true;

		wordSymbolNodes++;

		if(maxWordSymbolNodes > 0 && wordSymbolNodes >= maxWordSymbolNodes) {
			logger.info("Max Symbol Node count reached for word reached");
			pendingReductions.clear();
			return false;
		}

        reduceSuccessCount[reduction.rule.id]++;



        // initializes  properties of the symbol node
        SymbolNode symbolNode = new SymbolNode(newSpan);
        symbolNode.children.add(firstStateNode);



        // ChildNodeNo  is the first node of the sequence nodes which should be reduced
        GLRTable.SLRCell cell = table.getCell(firstStateNode.state, leftPart);

        StateNode newStateNode = createOrReuseStateNode(cell.gotoLine, reduction.currentPos, true);


        SymbolNode newSymbolNode = hasPathOfLengthTwo(newStateNode, symbolNode);

		SymbolNode actualSymbolNode = newSymbolNode == null? symbolNode : newSymbolNode;

		if(leftPart == grammar.start) {
			roots.add(actualSymbolNode);
		}

		reductions.add(new PerformedReduction(reduction.rule, firstStateNode, reduction.lastStateNode, newStateNode, actualSymbolNode));

        if(newSymbolNode == null) {
            symbolNode.index = lastSymbolNodeId++;
            newSymbolNode = symbolNode;
            newStateNode.children.add(newSymbolNode);

		    /*
			 * if NewNodeNo is not in activeStates, it means that it was already created before
			 * we have got in this  procedure. And it means that for this node
			 * on the next iteration the Actor would be called.
			 * Since we have just created a new reducePath from NewNode we should reprocess all reductions
			 * from this node, since some reductions can use this new reducePath.
		     */
            if(!newStateNode.active) {
                for(SymbolSpan symbol : symbols) {

                    if(symbol.symbol == -1)
                        continue;

                    GLRTable.SLRCell c = table.getCell(cell.gotoLine, symbol.symbol);
                    if(c == null)
                        continue;

                    for(CompiledGrammar.Rule rule : c.reduceRules) {
                        PendingReduction pendingReduction = new PendingReduction();
                        pendingReduction.rule = rule;
                        pendingReduction.symbolNode = newSymbolNode;
                        pendingReduction.lastStateNode = newStateNode;
						pendingReduction.currentPos = reduction.currentPos;
                        pendingReductions.add(pendingReduction);
                    }
                }
            }
        }

        //  storing parse tree

        List<SymbolNode> items = new ArrayList<>(path.size() / 2 + 1);

        for(int i = path.size() - 2; i > 0; i -= 2) {
            items.add((SymbolNode) path.get(i));
        }

        ParsingChildrenSet parse = new ParsingChildrenSet(reduction.rule, items);
        newSymbolNode.addParse(parse);
		return true;
    }



    /**
     * Checks that the reduction could be performed. If all checks completed successfully,
     * then execute a reduce action of the reduced production
     *
     * @param reduction reduction
     * @param path stack reducePath (consists of symbol nodes and prefixStates)
     * @return true, if all checks successfully completed and the reduce action was performed
     */
    public SymbolSpan process(PendingReduction reduction, List<StackNode> path) {


        Production p = reduction.rule.production;
		TIntArrayList l = new TIntArrayList(path.size());


		for(int i = path.size() - 2; i >= 0; i -= 2) {
			StackNode n = path.get(i);
			SymbolSpan sym = ((SymbolNode) n).symbol;
			l.add(sym.id);

		}

		SymbolSpan ssym = (SymbolSpan) spanMaps[reduction.rule.id].get(l);
		if(ssym != null) {
			if(ssym == INVALID_SPAN)
				return null;
			reduceCacheHits++;
			return ssym;
		}

		reduceRHS.clear();
		int index = 0;
		for(int i = path.size() - 2; i >= 0; i -= 2) {
			StackNode n = path.get(i);
			SymbolSpan sym = ((SymbolNode) n).symbol;

			if(!predEval.eval(p.predIds.get(index), sym)) {
				return null;
			}

			index++;
			reduceRHS.add(sym);

		}


		int leftPart  = reduction.rule.lhs;
		String leftPartId = grammar.symbols.get(leftPart).id;

		int startOffset = reduceRHS.get(0).start;
		int endOffset = reduceRHS.get(reduceRHS.size() - 1).end;
		SymbolSpan symbol = new SymbolSpan(leftPart, leftPartId, -1, startOffset, endOffset, null, null, 0.0);
        symbol.head = reduceRHS.get(reduction.rule.production.rootIndex);

        if(!grammar.actions[reduction.rule.id].execute(inputData.text, inputData.docFeats, reduction.rule, symbol, reduceRHS)) {
			spanMaps[reduction.rule.id].put(l, INVALID_SPAN);
			return null;
		}

		symbol.id = inputData.lastSpanId++;

		if(symbol.weight == 0.0) {
			symbol.weight = reduction.rule.production.weight;
			for(SymbolSpan ss : reduceRHS) {
				symbol.weight *= ss.weight;
			}
		}

		reducerCacheMisses++;

		spanMaps[reduction.rule.id].put(l, symbol);

		return symbol;

    }

    public SymbolNode hasPathOfLengthTwo(StateNode stateNode, SymbolNode betweenNode) {


        StateNode targetNode = betweenNode.getChild(0);

        for(int j = 0; j < stateNode.childCount(); j++) {
            SymbolNode symbolNode  = stateNode.getChild(j);
			// TODO: заменить на equals?
            if(betweenNode.symbol.start == symbolNode.symbol.start
                    && betweenNode.symbol.end == symbolNode.symbol.end
                    && betweenNode.symbol.symbol == symbolNode.symbol.symbol
                    && betweenNode.symbol.head == symbolNode.symbol.head
					&& betweenNode.symbol.weight == symbolNode.symbol.weight
                    && symbolNode.children.contains(targetNode)
                    && (betweenNode.symbol.features == symbolNode.symbol.features
                        ||
                        betweenNode.symbol.features.equals(symbolNode.symbol.features)))
                return symbolNode;
        }

        return null;
    }

    /**
     * Parse another symbol from current state of the GLR parser
     *
     * @param spans symbols to parse
     * @param activeStates list of active prefixStates for current position
	 * @param pos current work index
     * @param addStart true, if a start state should be added to active prefixStates
     */
    public void parseSymbol(List<SymbolSpan> spans, List<StateNode> activeStates, int pos, boolean addStart) {

        this.activeStates.clear();
		wordSymbolNodes = 0;

        if((activeStates == null || activeStates.isEmpty()) && !addStart)
            return;

        if(addStart) {
            this.activeStates.add(startNode);
        }

        if(activeStates != null)
            this.activeStates.addAll(activeStates);

        for(StateNode active : activeStates) {
            active.active = true;
        }



        while(!this.activeStates.isEmpty() || !pendingReductions.isEmpty()) {
            if (!this.activeStates.isEmpty()) {
                actor(spans, pos);
            } else {
                reducer(spans);
            }
        }

		greedyFilter(pos);	// apply greedy filter, if possible

        shifter();
		reductions.clear();	 // clear performed reductions


    }

    private void expandSpans(List<SymbolSpan> input, int start, int end, List<SymbolSpan> spans) {

        for(int i = start; i < end; i++) {
            SymbolSpan ss = input.get(i);
            if(ss.symbol == -1)
                continue;

            spans.add(ss);
            SynthTerminalEvaluator eval = grammar.evaluators[ss.symbol];

            if(eval == null)
                continue;
            int base = baseSynthCacheIndex(ss.id);

            for(int j = 0; j < eval.typeIds.size(); j++) {
                byte val = synthCache[base + j];
                // need to eval
                if(val == -1) {
                    int predId = eval.predIds.get(j);
                    val = (byte) (predEval.eval(predId, ss)? 1 : 0);
                    synthCache[base + j] = val;
                }

                if(val == 1) {
                    int symId = eval.typeIds.get(j);
                    spans.add(new SymbolSpan(symId, ss.type, ss.id, ss.start, ss.end, ss.features, ss.data, 1.0));

                }
            }

            // TODO: в walkFSA() устанавливать флаги предикатов?? для этого случая кстати должны быть все предикаты в eval
            for(int j = 0; j < eval.accessors.size(); j++) {
                int accId = eval.accessors.get(j);
                FeatureAccessor fa = grammar.accessors.get(accId);
                Object val = fa.get(predEval, ss);
                if(val == null)
                    continue;
                int objId = grammar.fsaPredValues.get(val, false);
                // there is something
                if(objId != 0) {
                    int s = 0;
                    s = grammar.predFSA.next(s, accId);
                    if(s == -1)
                        continue;
                    s = grammar.predFSA.next(s, objId);
                    if(s == -1)
                        continue;
                    int trStart = grammar.predFSA.stateStart(s);
                    int trEnd = grammar.predFSA.stateEnd(s);

                    for(int k = trStart; k < trEnd; k++) {
                        int symId = grammar.predFSA.label(k);
                        spans.add(new SymbolSpan(symId, ss.type, ss.id, ss.start, ss.end, ss.features, ss.data, 1.0));

                    }
                }
            }
        }
    }


    /**
     * Run parser on input data
     */
    public void parse() {
        int pos = 0;

        List<SymbolSpan> spans = new ArrayList<>(inputData.size());

        while(pos < inputData.wordStarts.size()) {
            int start = inputData.wordStarts.get(pos);
            int end = inputData.wordEnds.get(pos);

            List<StateNode> nodes = nodes4word.get(pos);
            boolean hasStart = starts[pos];

            if((nodes != null &&  !nodes.isEmpty()) || hasStart) {
                spans.clear();
                expandSpans(inputData.input, start, end, spans);

                if(pos != inputData.wordStarts.size() - 1)
                    spans.add(eof);

                parseSymbol(spans, nodes, pos, starts[pos]);
            }

            pos++;
        }



    }

    /**
     * Extract RHS bindings for production. Only bindings from RHS are collected (no nested bindings)
     *
     * @param p production
     * @param rhs rhs symbols
     * @return bindings map
     */
    private Map<String, SymbolSpan> extractBindings(Production p, List<SymbolSpan> rhs) {
        Map<String, SymbolSpan> bindings = new HashMap<>();

        for(Production.BindingInfo info : p.bindings) {
            if(info.type) {
                bindings.put(info.name, rhs.get(info.path.get(0)));
            }
        }

        return bindings;
    }

    /**
     * Write reduce stats
     * @param f output file
     * @throws IOException
     */
    public void writeReduceStats(File f) throws IOException {
        try(PrintWriter pw = new PrintWriter(f)) {
            for(int i = 0; i < grammar.rules.length; i++) {
                pw.printf("%d\t%d\t%s -> ",
                        reduceSuccessCount[i],
                        reduceAttemptCount[i],
                        grammar.rules[i].production.lhs.id);

                for(int j = 0; j < grammar.rules[i].production.rhs.size(); j++) {
                    if(j != 0)
                        pw.print(" ");
                    pw.print(grammar.rules[i].production.rhs.get(j).id);
                }
                pw.println();
            }
        }
    }

    /**
     * Clear parser state
     */
    public void clear() {
        startNode = null;
        pendingReductions.clear();
        pendingShifts.clear();
        activeStates.clear();
        reducePath.clear();
        inputData = null;
        synthCache = null;
        starts = null;
        roots.clear();
		reduceRHS.clear();
        maxWordState = 0;
        nodes4word.clear();


		for(int i = 0; i < spanMaps.length; i++) {
			spanMaps[i] = new HashMap();
		}

    }

	// filter greedy filtering pending shifts
	public void greedyFilter(int currentPos) {
		GreedyFilter filter = new GreedyFilter(this, currentPos);
		filter.filter();
	}

}
