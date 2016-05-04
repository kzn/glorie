package name.kazennikov.glorie.groovy;

import groovy.lang.GroovyClassLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by kzn on 6/29/15.
 */
public class DynamicModuleExtension extends ManualModuleExtension {
    GroovyClassLoader classLoader;

    List<String> instanceExtensionClassNames;
    List<String> staticExtensionClassNames;
    List<String> globalExtensionClassNames;


    public DynamicModuleExtension(GroovyClassLoader classLoader, String moduleName, String moduleVersion) {
        super(moduleName, moduleVersion);
        this.classLoader = classLoader;
    }

    public List<String> getInstanceExtensionClassNames() {
        return instanceExtensionClassNames;
    }

    public void setInstanceExtensionClassNames(List<String> instanceExtensionClassNames) {
        this.instanceExtensionClassNames = instanceExtensionClassNames;
    }

    public List<String> getStaticExtensionClassNames() {
        return staticExtensionClassNames;
    }

    public void setStaticExtensionClassNames(List<String> staticExtensionClassNames) {
        this.staticExtensionClassNames = staticExtensionClassNames;
    }

    public List<String> getGlobalExtensionClassNames() {
        return globalExtensionClassNames;
    }

    public void setGlobalExtensionClassNames(List<String> globalExtensionClassNames) {
        this.globalExtensionClassNames = globalExtensionClassNames;
    }

    public List<Class> loadClasses(List<String> classNames) throws ClassNotFoundException {
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
