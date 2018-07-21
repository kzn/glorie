package name.kazennikov.glorie.groovy;

import groovy.lang.GroovyClassLoader;
import name.kazennikov.glorie.GLORIEBaseVisitor;
import name.kazennikov.glorie.GLORIEParser;
import org.antlr.v4.runtime.tree.ParseTree;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.m12n.SimpleExtensionModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created on 7/21/18.
 *
 * @author Anton Kazennikov
 */
public class ClassloaderBuilder {
    List<String> classpath = new ArrayList<>();

    List<String> instanceExtensions = new ArrayList<>();
    List<String> globalExtensions = new ArrayList<>();
    List<String> staticExtensions = new ArrayList<>();

    CompilerConfiguration cc;
    GroovyClassLoader classLoader;


    public ClassloaderBuilder() {
        instanceExtensions.add(GroovyExtensions.class.getName());
        staticExtensions.add(GroovyExtensions.Static.class.getName());
        globalExtensions.add(GroovyExtensions.Global.class.getName());

    }

    public void addClassPath(String path) {
        classpath.add(path);
    }

    public void build() throws Exception {
        cc = new CompilerConfiguration();
        classLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), cc);

        for(String classPath : classpath) {
            classLoader.addClasspath(classPath);
        }

        DynamicExtensionModule ext = new DynamicExtensionModule(classLoader, "groovy4glr", "1.0");




        for(String cn : instanceExtensions) {
            ext.addInstanceExtension(cn);
        }

        for(String cn : staticExtensions) {
            ext.addStaticExtension(cn);
        }

        for(String cn : globalExtensions) {
            ext.addGlobalExtension(cn);
        }

        ext.init();

        MethodResolver resolver = new SimpleMethodResolver(Arrays.asList((SimpleExtensionModule) ext));
        cc.addCompilationCustomizers(new SimpleASTCustomizer(new CustomizableStaticCompileTransformation(resolver)));
    }

    public CompilerConfiguration cc() {
        return cc;
    }

    public GroovyClassLoader classloader() {
        return classLoader;
    }

    public void visit(ParseTree pt) {
        Visitor v = new Visitor(this);
        v.visit(pt);
    }


    public static class Visitor extends GLORIEBaseVisitor<Void> {

        ClassloaderBuilder builder;

        public Visitor(ClassloaderBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Void visitInstanceExtension(GLORIEParser.InstanceExtensionContext ctx) {
            builder.instanceExtensions.add(ctx.className().getText());
            return super.visitInstanceExtension(ctx);
        }

        @Override
        public Void visitStaticExtension(GLORIEParser.StaticExtensionContext ctx) {
            builder.staticExtensions.add(ctx.className().getText());
            return super.visitStaticExtension(ctx);
        }

        @Override
        public Void visitGlobalExtension(GLORIEParser.GlobalExtensionContext ctx) {
            builder.globalExtensions.add(ctx.className().getText());
            return super.visitGlobalExtension(ctx);
        }




    }
}
