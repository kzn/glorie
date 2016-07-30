package name.kazennikov.glorie.groovy;

import groovy.lang.GroovyClassLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Extension module that accepts class names and loads them
 */
public class DynamicExtensionModule extends CustomExtensionModule {
    GroovyClassLoader classLoader;

    protected Set<String> instanceExtensionClassNames;
    protected Set<String> staticExtensionClassNames;
    protected Set<String> globalExtensionClassNames;


    public DynamicExtensionModule(GroovyClassLoader classLoader, String moduleName, String moduleVersion) {
        super(moduleName, moduleVersion);
        this.classLoader = classLoader;
    }

    public Set<String> getInstanceExtensionClassNames() {
        return instanceExtensionClassNames;
    }

    public void addInstanceExtension(String className) {
        instanceExtensionClassNames.add(className);
    }

    public Set<String> getStaticExtensionClassNames() {
        return staticExtensionClassNames;
    }

    public void addStaticExtension(String className) {
        staticExtensionClassNames.add(className);
    }

    public Set<String> getGlobalExtensionClassNames() {
        return globalExtensionClassNames;
    }

    public void addGlobalExtension(String className) {
        globalExtensionClassNames.add(className);
    }

    public List<Class> loadClasses(Collection<String> classNames) throws ClassNotFoundException {
        List<Class> classes = new ArrayList<>();
        for(String className : classNames) {
            classes.add(classLoader.loadClass(className));
        }

        return classes;
    }

    public void init() throws Exception {
        instanceExtensionClasses = loadClasses(instanceExtensionClassNames);
        staticExtensionClasses = loadClasses(staticExtensionClassNames);
        globalStaticMethodClasses = loadClasses(globalExtensionClassNames);
    }
}
