package name.kazennikov.glorie;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.creole.ExecutionException;
import groovy.lang.Script;
import name.kazennikov.glorie.filters.ListOverlapFilter;
import name.kazennikov.glorie.filters.OverlapResolver;

import java.util.*;

/**
 * Post grammar actions.
 *
 * Performs filtering and transformation of symbol nodes to the annotations in the target document
 *
 */
public abstract class PostBaseScript extends Script {

    /**
     * Dummy script that adds all generated symbol spans (that are descendants of a root symbol) as annotations
     * to the document
     */
    public static class Simple extends PostBaseScript {

        @Override
        public Object run() {
            try {
				computeNodes();
                convert();
            } catch(ExecutionException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

	public static class FilterOverlapping extends PostBaseScript {

		@Override
		public Object run() {
			try {
				filterOverlapping(new BasicOverlapResolver() {

					@Override
					public boolean shouldResolve(SymbolNode ss1, SymbolNode ss2) {
						return ss1.symbol.symbol == ss2.symbol.symbol;
					}

					@Override
					public boolean shouldResolve(SymbolNode node) {
						return true;
					}
				});
			} catch(Exception e) {
				throw new RuntimeException(e);
			}


			return null;
		}
	}


    protected GLRTable table;
    protected List<SymbolNode> rootNodes;
    protected List<SymbolNode> symbolNodes;
    protected AnnotationSet outputAS;
    protected Document doc;
    SourceInfo sourceInfo;

    public static final Comparator<SymbolSpan> LENGTH = new Comparator<SymbolSpan>() {
        @Override
        public int compare(SymbolSpan o1, SymbolSpan o2) {
            int len1 = o1.end - o1.start;
            int len2 = o2.end - o2.start;
            return len2 - len1;
        }
    };

    public static final Comparator<SymbolSpan> LENGTH_START = new Comparator<SymbolSpan>() {
        @Override
        public int compare(SymbolSpan o1, SymbolSpan o2) {
            int res = LENGTH.compare(o1, o2);
            if(res != 0)
                return res;

            return o1.start - o2.start;
        }
    };

    public static class SymbolNodeComparator implements Comparator<SymbolNode> {
        Comparator<SymbolSpan> c;

        public SymbolNodeComparator(Comparator<SymbolSpan> c) {
            this.c = c;
        }

        @Override
        public int compare(SymbolNode o1, SymbolNode o2) {
            return c.compare(o1.symbol, o2.symbol);
        }
    }

    public static class BasicOverlapResolver implements OverlapResolver<SymbolNode> {

		public static final Comparator<SymbolNode> c = new Comparator<SymbolNode>() {
			@Override
			public int compare(SymbolNode o1, SymbolNode o2) {
				int res = Double.compare(o2.symbol.weight, o1.symbol.weight);
				if(res != 0)
					return res;
				res = Integer.compare(o2.symbol.end - o2.symbol.start, o1.symbol.end - o1.symbol.start);
				if(res != 0)
					return res;
				res = Integer.compare(o1.symbol.start, o2.symbol.start);
				return res;
			}
		};

        @Override
        public boolean shouldResolve(SymbolNode node) {
            return true;
        }

        @Override
        public boolean shouldResolve(SymbolNode ss1, SymbolNode ss2) {
            return true;
        }

        @Override
        public List<SymbolNode> select(List<SymbolNode> list) {

			if(list == null || list.isEmpty() || list.size() == 1)
				return list;

			list = new ArrayList<>(list);

			Collections.sort(list, c);
			List<SymbolNode> out = new ArrayList<>();
			out.add(list.get(0));

			for(int i = 1; i < list.size(); i++) {
				if(c.compare(list.get(0), list.get(i)) != 0)
					break;
				out.add(list.get(i));
			}

            return out;
        }

        @Override
        public int start(SymbolNode node) {
            return node.symbol.start;
        }

        @Override
        public int end(SymbolNode node) {
            return node.symbol.end;
        }

    }


    /**
     * Compute all symbol nodes that are descendants of a root
     * @return a out[i] == 1 that respective symbol span is a descendant of a root
     */
    public void computeNodes() {
        BitSet status = new BitSet();
        symbolNodes = new ArrayList<>(128);

        for(SymbolNode root : rootNodes) {
            visit(status, root, symbolNodes);
        }

        for(int i = 0; i < symbolNodes.size(); i++) {
            symbolNodes.get(i).index = i;
        }

    }

    /**
     * Check if symbol is a root
     * @param symbol symbol id
     * @return
     */
    public boolean isRoot(int symbol) {
        return symbol == table.g.start;
    }

    /**
     * Check if symbol is a root
     * @param s symbol span
     * @return
     */
    public boolean isRoot(SymbolSpan s) {
        return isRoot(s.symbol);
    }

    /**
     * Check if symbol is a root
     * @param symbolNode symbol node
     * @return
     */
    public boolean isRoot(SymbolNode symbolNode) {
        return isRoot(symbolNode.symbol);
    }

    /**
     * Checks if for given symbol an annotation should be generated
     * @param symbol symbol id
     * @return
     */
    public boolean isOutput(int symbol) {
        return table.g.output[symbol] == 1;
    }


    /**
     * Checks if for given symbol an annotation should be generated
     * @param s symbol span
     * @return
     */
    public boolean isOutput(SymbolSpan s) {
        return isOutput(s.symbol);
    }

    /**
     * Checks if for given symbol an annotation should be generated
     * @param symbolNode symbol node
     * @return
     */
    public boolean isOutput(SymbolNode symbolNode) {
        return isOutput(symbolNode.symbol);
    }

    /**
     * Retrieve all nodes that those symbol id is a root symbol
     * @param nodes contained of root nodes
     * @return
     */
    public List<SymbolNode> roots(List<SymbolNode> nodes) {
        List<SymbolNode> l = new ArrayList<>();
        for(SymbolNode node : nodes) {
            if(isRoot(node))
                l.add(node);
        }

        return l;
    }



    private void visit(BitSet status, SymbolNode root, List<SymbolNode> symbolNodes) {
        // already visited
        if(status.get(root.index))
            return;

        status.set(root.index);
        symbolNodes.add(root);


        for(GLRParser.ParsingChildrenSet childSet : root.parseChildren) {
            for(SymbolNode child :childSet.items) {
                visit(status, child, symbolNodes);
            }
        }
    }

    public void convertSpans() throws ExecutionException {
        try {
            for(SymbolNode node : symbolNodes) {
                if(table.g.output[node.symbol.symbol] != 1 || node.symbol.data != null)
                    continue;

                SymbolSpan span = node.symbol;
				span.features.put("@weight", span.weight);
                Integer annId = outputAS.add((long) span.start, (long) span.end, span.type, span.features);
				span.data = outputAS.get(annId);
            }
        } catch(Exception e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Default conversion procedure.
     * Adds all annotations for symbol nodes with output symbols which descend from a grammar root
     * @throws ExecutionException
     */
    public void convert() throws ExecutionException {
        convertSpans();
        postprocess();
    }

    public void postprocess() throws ExecutionException {
        for(SymbolNode root : rootNodes) {
            int[] status = new int[symbolNodes.size()];
            try {
                postprocessNodes(root, status);
            } catch(Exception e) {
                throw new ExecutionException(e);
            }
        }
    }

    public void postprocessNodes(SymbolNode node, int[] status) throws Exception {
        if(status[node.index] == 1) {
            return;
        }


        status[node.index] = 1;

        // TODO: define visiting order of parses
        for(GLRParser.ParsingChildrenSet parse : node.parseChildren) {
            SymbolSpan[] rhs = new SymbolSpan[parse.itemCount()];
            Annotation[] rhsAnns = new Annotation[parse.itemCount()];

            for(int i = 0; i < parse.itemCount(); i++) {
                SymbolNode item = parse.item(i);
                postprocessNodes(item, status);
                rhs[i] = item.symbol;
                rhsAnns[i] = (Annotation) item.symbol.data;
            }

            if(node.symbol.data != null && parse.rule.production.postProcessor != null) {
                Map<String, SymbolSpan> bindings = new HashMap<>();
                Map<String, Annotation> bindingAnns = new HashMap<>();
                extractBindings(parse.rule.production, rhs, bindings, bindingAnns);
                Annotation a = (Annotation) node.symbol.data;
                SymbolNodePostProcessor pp = table.g.terminalPostproc[parse.rule.id];
                pp.apply(doc, outputAS, parse.rule, node, a, rhs, rhsAnns, bindings, bindingAnns);
            }
        }
    }

    protected void extractBindings(Production p, SymbolSpan[] rhs, Map<String, SymbolSpan> bindings, Map<String, Annotation> bindingAnns) {
        for(Production.BindingInfo info : p.bindings) {
            if(info.type) {
                SymbolSpan span = rhs[info.path.get(0)];
                bindings.put(info.name, span);
                if(span.data != null) {
                    bindingAnns.put(info.name, (Annotation) span.data);
                }
            }
        }
    }




    public void addAll() throws ExecutionException {
        int[] filtered = new int[symbolNodes.size()];
        Arrays.fill(filtered, 1);
        addFiltered(filtered);
    }

    public void addFiltered(int[] status) throws ExecutionException {
        try {
            for(int i = 0; i < status.length; i++) {
                if(status[i] == 0)
                    continue;

                SymbolNode symbolNode = symbolNodes.get(i);
                SymbolSpan span = symbolNode.symbol;

                if(table.g.output[span.symbol] == 1 && span.data == null) {
					span.features.put("@weight", span.weight);
                    Integer id = outputAS.add((long) span.start, (long) span.end, span.type, span.features);
                    span.data = outputAS.get(id);
                }
            }
        } catch(Exception e) {
            throw new ExecutionException(e);
        }
    }


    public void filterOverlapping(BasicOverlapResolver resolver) throws ExecutionException {

        if(resolver == null) {
            resolver = new BasicOverlapResolver();
        }

        List<SymbolNode> nodes4overlap = new ArrayList<>(rootNodes);
        Collections.sort(nodes4overlap, new SymbolNodeComparator(SymbolSpan.COMPARATOR));

        ListOverlapFilter<SymbolNode> filter = new ListOverlapFilter<>(nodes4overlap, resolver);

		rootNodes = filter.apply();
		computeNodes();

        int[] status = new int[symbolNodes.size()];
		Arrays.fill(status, 1);

        addFiltered(status);
        postprocess();
    }


    public List<SymbolNode> removeSelfContained(List<SymbolNode> nodes, List<String> types) {
        List<SymbolNode> res = new ArrayList<>();
        nodes = new ArrayList<>(nodes);

        Collections.sort(nodes, new Comparator<SymbolNode>() {
            @Override
            public int compare(SymbolNode o1, SymbolNode o2) {
                int res = o1.symbol.start - o2.symbol.start;
                if(res != 0)
                    return res;

                return o2.symbol.end - o1.symbol.end;
            }
        });

        for(String type : types) {
            int cur = 0;
            SymbolNode prev = null;
            for(SymbolNode n : nodes) {
                if(n.symbol.type.equals(type)) {
                    if(prev == null || !prev.symbol.contains(n.symbol)) {
                        res.add(n);
                        prev = n;
                    }
                }
            }
        }

        return res;
    }


    /**
     * Post process resulting symbols
     * @param doc document
     * @param outputAS output AnnotationSet
     * @param table GLR table
     * @param rootNodes list of resulting root nodes
     */
    public void exec(Document doc, AnnotationSet outputAS, GLRTable table, List<SymbolNode> rootNodes) {
        try {
            this.doc = doc;
            this.outputAS = outputAS;
            this.table = table;
            this.rootNodes = rootNodes;
            run();
        } catch(Exception e) {
            if(sourceInfo != null)
                sourceInfo.enhanceTheThrowable(e);
            throw e;
        } finally {
            this.doc = null;
            this.outputAS = null;
            this.table = null;
            this.symbolNodes = null;
            this.rootNodes = null;
        }
    }

}
