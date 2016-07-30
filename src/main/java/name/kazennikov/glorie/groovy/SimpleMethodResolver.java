package name.kazennikov.glorie.groovy;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.runtime.m12n.SimpleExtensionModule;
import org.codehaus.groovy.transform.stc.ExtensionMethodNode;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingSupport;

import java.util.*;

import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.makeWithoutCaching;

/**
 * Created by kzn on 6/21/15.
 */
public class SimpleMethodResolver implements MethodResolver {

    protected static final ClassNode Deprecated_TYPE = makeWithoutCaching(Deprecated.class);


    Map<String, List<MethodNode>> methodMap = new HashMap<>();
    List<MethodNode> globalMethods = new ArrayList<>();


    public SimpleMethodResolver(List<SimpleExtensionModule> modules) {
        for(SimpleExtensionModule module : modules) {
            add(module);
        }
    }

    public void add(SimpleExtensionModule module) {
        scanClassForExtensionMethods(module.getInstanceMethodsExtensionClasses(), false);
        scanClassForExtensionMethods(module.getStaticMethodsExtensionClasses(), true);
        if(module instanceof CustomExtensionModule) {
            for(Class c : ((CustomExtensionModule) module).getGlobalStaticMethodClasses()) {
                ClassNode cn = new ClassNode(c);
                for(MethodNode methodNode : cn.getMethods()) {
                    if(methodNode.isStatic() && methodNode.isPublic()
                            && methodNode.getAnnotations(Deprecated_TYPE).isEmpty()) {
                        globalMethods.add(methodNode);
                    }
                }
            }

        }
    }

    /**
     * This comparator is used when we return the list of methods from DGM which name correspond to a given
     * name. As we also lookup for DGM methods of superclasses or interfaces, it may be possible to find
     * two methods which have the same name and the same arguments. In that case, we should not add the method
     * from superclass or interface otherwise the system won't be able to select the correct method, resulting
     * in an ambiguous method selection for similar methods.
     */
    protected static final Comparator<MethodNode> DGM_METHOD_NODE_COMPARATOR = new Comparator<MethodNode>() {
        public int compare(final MethodNode o1, final MethodNode o2) {
            if (o1.getName().equals(o2.getName())) {
                Parameter[] o1ps = o1.getParameters();
                Parameter[] o2ps = o2.getParameters();
                if (o1ps.length == o2ps.length) {
                    boolean allEqual = true;
                    for (int i = 0; i < o1ps.length && allEqual; i++) {
                        allEqual = o1ps[i].getType().equals(o2ps[i].getType());
                    }
                    if (allEqual) {
                        if (o1 instanceof ExtensionMethodNode && o2 instanceof ExtensionMethodNode) {
                            return compare(((ExtensionMethodNode) o1).getExtensionMethodNode(), ((ExtensionMethodNode) o2).getExtensionMethodNode());
                        }
                        return 0;
                    }
                } else {
                    return o1ps.length - o2ps.length;
                }
            }
            return 1;
        }
    };

    protected Set<MethodNode> findMethods(ClassNode receiver, String methodName) {
        TreeSet<MethodNode> accumulator = new TreeSet<MethodNode>(DGM_METHOD_NODE_COMPARATOR);
        findMethodsForClassNode(receiver, methodName, accumulator);
        findGlobalMethods(methodName, accumulator);

        return accumulator;
    }


    @Override
    public List<MethodNode> resolve(ClassNode receiver, String name, ClassNode... args) {
        List<MethodNode> methods = new ArrayList<>(findMethods(receiver, name));

        if(methods.isEmpty())
            return methods;

        methods = StaticTypeCheckingSupport.chooseBestMethod(receiver, methods, args);

        return methods;
    }

    protected void findGlobalMethods(String name, TreeSet<MethodNode> accumulator) {
        List<MethodNode> fromDGM = globalMethods;

        if(fromDGM != null) {
            fromDGM = new ArrayList<>(fromDGM);
            Collections.reverse(fromDGM);
        }

        if(fromDGM != null) {
            for (MethodNode node : fromDGM) {
                if (node.getName().equals(name))
                    accumulator.add(node);
            }
        }
    }


    protected void findMethodsForClassNode(ClassNode clazz, String name, TreeSet<MethodNode> accumulator) {
        List<MethodNode> fromDGM = methodMap.get(clazz.getName());

        if(fromDGM != null) {
            fromDGM = new ArrayList<>(fromDGM);
            Collections.reverse(fromDGM);
        }


        if(fromDGM != null) {
            for (MethodNode node : fromDGM) {
                if (node.getName().equals(name))
                    accumulator.add(node);
            }
        }

        for(ClassNode node : clazz.getInterfaces()) {
            findMethodsForClassNode(node, name, accumulator);
        }

        if (clazz.isArray()) {
            ClassNode componentClass = clazz.getComponentType();
            if (!componentClass.equals(OBJECT_TYPE) && !ClassHelper.isPrimitiveType(componentClass)) {
                if (componentClass.isInterface()) {
                    findMethodsForClassNode(OBJECT_TYPE.makeArray(), name, accumulator);
                } else {
                    findMethodsForClassNode(componentClass.getSuperClass().makeArray(), name, accumulator);
                }
            }
        }

        if (clazz.getSuperClass() != null) {
            findMethodsForClassNode(clazz.getSuperClass(), name, accumulator);
        } else if (!clazz.equals(ClassHelper.OBJECT_TYPE)) {
            findMethodsForClassNode(ClassHelper.OBJECT_TYPE, name, accumulator);
        }
    }

    private void scanClassForExtensionMethods(Iterable<Class> allClasses, boolean isStatic) {
        for (Class dgmLikeClass : allClasses) {
            ClassNode cn = ClassHelper.makeWithoutCaching(dgmLikeClass, true);
            for (MethodNode metaMethod : cn.getMethods()) {
                Parameter[] types = metaMethod.getParameters();
                if (metaMethod.isStatic() && metaMethod.isPublic() && types.length > 0
                        && metaMethod.getAnnotations(Deprecated_TYPE).isEmpty()) {
                    Parameter[] parameters = new Parameter[types.length - 1];
                    System.arraycopy(types, 1, parameters, 0, parameters.length);

                    ExtensionMethodNode node = new ExtensionMethodNode(
                            metaMethod,
                            metaMethod.getName(),
                            metaMethod.getModifiers(),
                            metaMethod.getReturnType(),
                            parameters,
                            ClassNode.EMPTY_ARRAY, null,
                            isStatic);
                    node.setGenericsTypes(metaMethod.getGenericsTypes());


                    ClassNode declaringClass = types[0].getType();
                    String declaringClassName = declaringClass.getName();
                    node.setDeclaringClass(declaringClass);

                    List<MethodNode> nodes = methodMap.get(declaringClassName);

                    if(nodes == null) {
                        nodes = new ArrayList<>();
                        methodMap.put(declaringClassName, nodes);
                    }

                    nodes.add(node);
                }
            }
        }
    }
}
