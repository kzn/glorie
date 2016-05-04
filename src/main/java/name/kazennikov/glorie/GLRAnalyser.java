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


    CompiledGrammar grammar;
    GLRTable table;
    GLRParser parser;
    File grammarFile;
    String asName;

    List<String> instanceExtensionClasses = new ArrayList<>();
    List<String> staticExtensionClasses = new ArrayList<>();
    List<String> globalExtensionClasses = new ArrayList<>();
    List<String> groovyClassPath;

    String parserContextClassName;

    CompilerConfiguration cc;
    GroovyClassLoader classLoader;
    ParserContext parserContext;
    boolean printTableOnInit = false;

    @Override
    public Resource init() throws ResourceInstantiationException {
        if(grammarFile == null)
            throw new ResourceInstantiationException("Grammar file is null");
        try {
            classLoader = initGroovyClassLoader();
            parserContext = initParserContext();


            grammar = parseGrammar();
            table = new GLRTable(grammar);
            table.buildGLRTable();

            if(printTableOnInit) {
                table.print(new File("glrtable.txt"));
            }

            parser = new GLRParser(table);
        } catch(Exception e) {
            throw new ResourceInstantiationException(e);
        }

        return this;
    }

    private ParserContext initParserContext() throws Exception {
        if(parserContext != null)
            return parserContext;
        if(parserContextClassName == null) {
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

        groovyClassPath.add(new File(grammarFile.getParent(), "groovy").getAbsolutePath());



        for(String classPath : groovyClassPath) {
            classLoader.addClasspath(classPath);
        }

        DynamicModuleExtension ext = new DynamicModuleExtension(classLoader, "groovy4glr", "1.0");

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

        ext.setInstanceExtensionClassNames(instanceExtensionClasses);
        ext.setStaticExtensionClassNames(staticExtensionClasses);
        ext.setGlobalExtensionClassNames(globalExtensionClasses);
        ext.init();

        MethodResolver resolver = new SimpleMethodResolver(Arrays.asList((SimpleExtensionModule) ext));
        cc.addCompilationCustomizers(new SimpleASTCustomizer(new ParametrizedStaticCompileTransformation(resolver)));
        return classLoader;
    }

    public CompiledGrammar parseGrammar() throws Exception {
        String src = Files.toString(grammarFile, Charset.forName("UTF-8"));
        ANTLRInputStream charStream = new ANTLRInputStream(src);
        GLORIELexer lexer = new GLORIELexer(charStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        GLORIEParser parser = new GLORIEParser(tokenStream);

        GrammarParser p = new GrammarParser(getGrammarURL(), parserContext, src);

        ParseTree pt = parser.glr();
        Grammar g = p.visit(pt);
        FlattenProductionRewriter seq = new FlattenProductionRewriter();
        g.rewrite(seq);
        g.rewriteTopLevelOr();
        g.rewriteTopLevelSeq();

        // check bindings
        for(Production prod : g.productions) {
            prod.validateBindings();
        }
        // TODO: check root index

        GroupRewriter groupRewriter = new GroupRewriter();
        RangeRewriter rangeRewriter = new RangeRewriter();
        g.rewrite(groupRewriter);
        g.rewrite(rangeRewriter);

        g.rewriteTopLevelSeq();
        g.removeEpsilon();
        g.augmentGrammar();



		g.transformPredicateNT();
        for(Production prod : g.productions) {
            prod.bindings(g);
            prod.findRootIndex();
        }


        g.computeReduceInfo();

        g.computeEvaluators();
        g.computePredInfo();
        g.computePredFSA();


        BasicRHSActionCompiler rhsCompiler = new BasicRHSActionCompiler(new GroovyCompiler(cc, classLoader));
        SymbolNodePostProcCompiler ppCompiler = new SymbolNodePostProcCompiler(new GroovyCompiler(cc, classLoader));

        return new CompiledGrammar(g, classLoader, rhsCompiler, ppCompiler);
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

    public String getParserContextClassName() {
        return parserContextClassName;
    }

    public void setParserContextClassName(String parserContextClassName) {
        this.parserContextClassName = parserContextClassName;
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
            copy.parserContextClassName = parserContextClassName;
            copy.globalExtensionClasses = globalExtensionClasses;
            copy.asName = asName;
            copy.groovyClassPath = groovyClassPath;
            copy.staticExtensionClasses = staticExtensionClasses;
            copy.instanceExtensionClasses = instanceExtensionClasses;
            copy.parserContext = parserContext;


        /*FIXME: еще предикаты надо скопировать
        copy.classLoader = classLoader;
        copy.parserContext = parserContext;

        copy.table = table.copy();
        copy.grammar = copy.table.g;
        copy.parser = new GLRParser(copy.table);*/

            return copy.init();
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
}
