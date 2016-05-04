package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.runtime.m12n.SimpleExtensionModule;

import java.util.List;

/**
 * Created by kzn on 6/21/15.
 */
public class ManualModuleExtension extends SimpleExtensionModule {

    List<Class> instanceExtensionClasses;
    List<Class> staticExtensionClasses;
    List<Class> globalStaticMethodClasses;

    public ManualModuleExtension(String moduleName, String moduleVersion) {
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
