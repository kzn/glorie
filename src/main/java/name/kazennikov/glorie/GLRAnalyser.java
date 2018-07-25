package name.kazennikov.glorie;

import com.google.common.io.Files;
import gate.Annotation;
import gate.AnnotationSet;
import gate.Factory;
import gate.Resource;
import gate.creole.AbstractLanguageAnalyser;
import gate.creole.CustomDuplication;
import gate.creole.ExecutionException;
import gate.creole.ResourceInstantiationException;
import gate.gui.ActionsPublisher;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import name.kazennikov.glorie.groovy.*;
import name.kazennikov.logger.Logger;
import name.kazennikov.sort.BinarySearch;
import name.kazennikov.sort.FixedIntComparator;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.m12n.SimpleExtensionModule;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GLR Analyser for GATE. Applies GLR Grammar to the provided document.
 * This class is essentially a wrapper over GLRParser.
 */
public class GLRAnalyser extends AbstractLanguageAnalyser implements CustomDuplication, ActionsPublisher {
    private static final Logger logger = Logger.getLogger();


    protected CompiledGrammar grammar;
    protected GLRTable table;
    protected GLRParser parser;
    protected File grammarFile;
    protected String asName;

    protected List<String> instanceExtensionClasses = new ArrayList<>();
    protected List<String> staticExtensionClasses = new ArrayList<>();
    protected List<String> globalExtensionClasses = new ArrayList<>();
    protected List<String> groovyClassPath;


    protected CompilerConfiguration cc;
    protected GroovyClassLoader classLoader;
    protected ParserContext parserContext;
    protected boolean printGLRTableOnInit = false;

    @Override
    public Resource init() throws ResourceInstantiationException {
        if(grammarFile == null)
            throw new ResourceInstantiationException("Grammar file not specified");

        try {

            String src = Files.toString(grammarFile, Charset.forName("UTF-8"));
            ParseTree parseTree = parseAST(src);

            ClassloaderBuilder clBuilder = new ClassloaderBuilder();
            clBuilder.addClassPath(new File(grammarFile.getParent(), "groovy").getAbsolutePath());
            clBuilder.visit(parseTree);
            clBuilder.build();
            cc = clBuilder.cc();
            classLoader = clBuilder.classloader();

            parserContext = initParserContext(parseTree);

            if(parserContext.astTransformation() != null) {
                cc.addCompilationCustomizers(new SimpleASTCustomizer(parserContext.astTransformation()));
            }


            grammar = parseGrammar(src, parseTree);
            table = new GLRTable(grammar);
            table.buildGLRTable();

            if(printGLRTableOnInit) {
                table.print(new File("glrtable.txt"));
            }

            parser = new GLRParser(table);
        } catch(Exception e) {
            throw new ResourceInstantiationException(e);
        }

        return this;
    }

    private ParserContext initParserContext(ParseTree pt) throws Exception {
        if(parserContext != null) {
            logger.info("Using predefined parser context: %s", parserContext.getClass().getName());
            return parserContext;
        }

        ParserContextClassNameVisitor cnVisitor = new ParserContextClassNameVisitor();

        String parserContextClassName = cnVisitor.visit(pt);


        if(parserContextClassName == null || parserContextClassName.isEmpty()) {
            return new BasicParserContext();
        }

        try {
            Class c = classLoader.loadClass(parserContextClassName);

            if(Script.class.isAssignableFrom(c)) {
                return (ParserContext) ((Script) c.newInstance()).run();
            } else {
                return (ParserContext) c.newInstance();
            }

        } catch(Exception e) {
            logger.error(e);
            throw e;
        }
    }

    private GroovyClassLoader initGroovyClassLoader() throws Exception {
        cc = new CompilerConfiguration();
        classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), cc);
        List<String> groovyClassPath = new ArrayList<>(this.groovyClassPath == null? 1 : this.groovyClassPath.size() + 1);

        if(this.groovyClassPath != null) {
            groovyClassPath.addAll(this.groovyClassPath);
        }

        // look into basename(grammarFile)/groovy as groovy class path by default
        groovyClassPath.add(new File(grammarFile.getParent(), "groovy").getAbsolutePath());



        for(String classPath : groovyClassPath) {
            classLoader.addClasspath(classPath);
        }

        DynamicExtensionModule ext = new DynamicExtensionModule(classLoader, "groovy4glr", "1.0");

        // add default extensions
        if(instanceExtensionClasses == null)
            instanceExtensionClasses = new ArrayList<>();

        if(staticExtensionClasses == null)
            staticExtensionClasses = new ArrayList<>();

        if(globalExtensionClasses == null)
            globalExtensionClasses = new ArrayList<>();

        instanceExtensionClasses.add(GroovyExtensions.class.getName());
        staticExtensionClasses.add(GroovyExtensions.Static.class.getName());
        globalExtensionClasses.add(GroovyExtensions.Global.class.getName());


        for(String cn : instanceExtensionClasses) {
            ext.addInstanceExtension(cn);
        }

        for(String cn : staticExtensionClasses) {
            ext.addStaticExtension(cn);
        }

        for(String cn : globalExtensionClasses) {
            ext.addGlobalExtension(cn);
        }

        ext.init();

        MethodResolver resolver = new SimpleMethodResolver(Arrays.asList((SimpleExtensionModule) ext));
        cc.addCompilationCustomizers(new SimpleASTCustomizer(new CustomizableStaticCompileTransformation(resolver)));

        return classLoader;
    }

    public ParseTree parseAST(String src) throws Exception {
        ANTLRInputStream charStream = new ANTLRInputStream(src);
        GLORIELexer lexer = new GLORIELexer(charStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        GLORIEParser parser = new GLORIEParser(tokenStream);
        return parser.glr();
    }


    public CompiledGrammar parseGrammar(String src, ParseTree pt) throws Exception {

        GrammarParser p = new GrammarParser(getGrammarURL(), parserContext, src);

        Grammar g = p.visit(pt);

        if(g.productions.isEmpty()) {
            throw new IllegalStateException("Grammar '" + g.name + "' has no productions");
        }

        if(g.start == null) {
            Symbol firstLHS = g.productions.get(0).lhs;
            logger.info("Grammar root for '%s' not set. Using LHS of the first production '%s' as head", g.name, firstLHS.id);
            g.start = firstLHS;
        }

        if(g.output.isEmpty()) {
            logger.info("Output Nonterminals not specified, using grammar root '%s' as output", g.start.id);
            g.output.add(g.start.id);
        }


        FlattenProductionRewriter seq = new FlattenProductionRewriter();
        g.rewrite(seq);
        g.rewriteTopLevelOr();
        g.rewriteTopLevelSeq();

        // check bindings
        for(Production prod : g.productions) {
            prod.validateBindings();
        }

        GroupRewriter groupRewriter = new GroupRewriter();
        RangeRewriter rangeRewriter = new RangeRewriter();
        g.rewrite(groupRewriter);
        g.rewrite(rangeRewriter);

        g.rewriteTopLevelSeq();
        ReachabilityFilter reachabilityFilter = new ReachabilityFilter(g);
        reachabilityFilter.filter();
        g.removeEpsilon();
        g.augmentGrammar();



		g.transformPredicateNT();
        for(Production prod : g.productions) {
            prod.bindings(g);
            prod.initHeadIndex();
        }


        g.computeReduceInfo();

        g.computeEvaluators();
        g.computePredInfo();
        g.computePredFSA();


        BasicReduceActionCompiler reduceActionCompiler = new BasicReduceActionCompiler(new GroovyCompiler(cc, classLoader));
        InterpCompiler interpCompiler = new InterpCompiler(new GroovyCompiler(cc, classLoader));

        return new CompiledGrammar(g, classLoader, reduceActionCompiler, interpCompiler);
    }

    @Override
    public void execute() throws ExecutionException {
        List<Annotation> context = null;
        AnnotationSet as = document.getAnnotations(asName);

        if(grammar.context() != null) {
            context = new ArrayList<>(as.get(grammar.context()));
        }

        final List<SymbolSpan> spans = new ArrayList<>(as.size());

        for(Annotation a : as) {
            if(!grammar.hasInput(a.getType()))
                continue;

            int symId = grammar.terminals.get(a.getType());

            SymbolSpan ss = new SymbolSpan(
                    symId,
                    a.getType(),
                    -1,
                    a.getStartNode().getOffset().intValue(),
                    a.getEndNode().getOffset().intValue(),
                    a.getFeatures(),
                    a,
					1.0

            );
            spans.add(ss);
        }


        Collections.sort(spans, SymbolSpan.COMPARATOR);

        if(context == null) {

            try {
                List<SymbolSpan> filteredSpans = grammar.pre.exec(spans);
                parser.init(document, filteredSpans);
                parser.parse();
                grammar.post.exec(document, as, table, parser.roots);
            } finally {
                parser.clear();
            }
        } else {
            for(Annotation ctx : context) {
                List<SymbolSpan> workSet = new ArrayList<>(spans.size());

                final int ctxStart = ctx.getStartNode().getOffset().intValue();
                final int ctxEnd = ctx.getEndNode().getOffset().intValue();

                int start = BinarySearch.binarySearch(0, spans.size(), new FixedIntComparator() {
                    @Override
                    public int compare(int i) {
                        SymbolSpan ss = spans.get(i);
                        return Integer.compare(ss.start, ctxStart);
                    }
                });

                if(start < 0) {
                    start = -start - 1;
                } else {
                    // backoff to the first span starting from the same offset
                    while(start > 0) {
                        if(spans.get(start - 1).start != spans.get(start).start)
                            break;
                        start--;

                    }
                }

                for(int i = start; i < spans.size(); i++) {

                    SymbolSpan ss = spans.get(i);

                    if(ss.start >= ctxEnd)
                        break;

                    workSet.add(ss);
                }

                workSet.add(new SymbolSpan(grammar.eof, "Split", -1, Integer.MAX_VALUE, Integer.MAX_VALUE, null, null, 1.0));

                try {
                    workSet = grammar.pre.exec(workSet);
                    parser.init(document, workSet);
                    parser.parse();
					grammar.post.exec(document, as, table, parser.roots);
                } finally {
                    parser.clear();
                }
            }
        }
    }

    public URL getGrammarURL() {
        try {
            return grammarFile.toURL();
        } catch(Exception e) {
            return null;
        }
    }

    public void setGrammarURL(URL grammarURL) {
        this.grammarFile = new File(grammarURL.getFile());
    }


    public List<String> getInstanceExtensionClasses() {
        return instanceExtensionClasses;
    }

    public void setInstanceExtensionClasses(List<String> instanceExtensionClasses) {
        this.instanceExtensionClasses = instanceExtensionClasses;
    }

    public List<String> getStaticExtensionClasses() {
        return staticExtensionClasses;
    }

    public void setStaticExtensionClasses(List<String> staticExtensionClasses) {
        this.staticExtensionClasses = staticExtensionClasses;
    }

    public List<String> getGlobalExtensionClasses() {
        return globalExtensionClasses;
    }

    public void setGlobalExtensionClasses(List<String> globalExtensionClasses) {
        this.globalExtensionClasses = globalExtensionClasses;
    }

    public List<String> getGroovyClassPath() {
        return groovyClassPath;
    }

    public void setGroovyClassPath(List<String> groovyClassPath) {
        this.groovyClassPath = groovyClassPath;
    }

    @Override
    public void reInit() throws ResourceInstantiationException {
        parserContext = null;
        init();
    }

    public void setParserContext(ParserContext parserContext) {
        this.parserContext = parserContext;
    }

    @Override
    public Resource duplicate(Factory.DuplicationContext ctx) throws ResourceInstantiationException {
        try {
            GLRAnalyser copy = new GLRAnalyser();
            copy.grammarFile = grammarFile;
            copy.globalExtensionClasses = globalExtensionClasses;
            copy.asName = asName;
            copy.groovyClassPath = groovyClassPath;
            copy.staticExtensionClasses = staticExtensionClasses;
            copy.instanceExtensionClasses = instanceExtensionClasses;
            copy.parserContext = parserContext;
            copy.printGLRTableOnInit = printGLRTableOnInit;
            copy.cc = cc;
            copy.classLoader = classLoader;


            copy.grammar = grammar.copy();
            copy.table = new GLRTable(copy.grammar);
            table.copy(copy.table);
            copy.parser = new GLRParser(copy.table);


            return copy;
        } catch(Exception e) {
            throw new ResourceInstantiationException(e);
        }
    }

    @Override
    public List<Action> getActions() {
        return Arrays.asList((Action) new AbstractAction("Reload grammar") {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    init();
                } catch(ResourceInstantiationException e1) {
                    logger.error(e1);
                }
            }
        });
    }

    public static class ParserContextClassNameVisitor extends GLORIEBaseVisitor<String> {

        String className;

        @Override
        public String visitGlr(GLORIEParser.GlrContext ctx) {
            super.visitGlr(ctx);
            return className;
        }

        @Override
        public String visitParserContext(GLORIEParser.ParserContextContext ctx) {
            className = ctx.className().getText();
            return className;
        }
    }
}
