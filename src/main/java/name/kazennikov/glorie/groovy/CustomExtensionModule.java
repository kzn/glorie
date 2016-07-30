package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.runtime.m12n.SimpleExtensionModule;

import java.util.List;

/**
 * Groovy extension module that provides user-set extension classes (see {@code SimpleExtensionModule}
 */
public class CustomExtensionModule extends SimpleExtensionModule {

    protected List<Class> instanceExtensionClasses;
    protected List<Class> staticExtensionClasses;
    protected List<Class> globalStaticMethodClasses;

    public CustomExtensionModule(String moduleName, String moduleVersion) {
        super(moduleName, moduleVersion);
    }

    public void setInstanceExtensionClasses(List<Class> instanceExtensionClasses) {
        this.instanceExtensionClasses = instanceExtensionClasses;
    }

    public void setStaticExtensionClasses(List<Class> staticExtensionClasses) {
        this.staticExtensionClasses = staticExtensionClasses;
    }

    public void setGlobalStaticMethodClasses(List<Class> globalStaticMethodClasses) {
        this.globalStaticMethodClasses = globalStaticMethodClasses;
    }

    @Override
    public List<Class> getInstanceMethodsExtensionClasses() {
        return instanceExtensionClasses;
    }

    @Override
    public List<Class> getStaticMethodsExtensionClasses() {
        return staticExtensionClasses;
    }

    public List<Class> getGlobalStaticMethodClasses() {
        return globalStaticMethodClasses;
    }
}
