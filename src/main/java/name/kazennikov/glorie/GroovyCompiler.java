package name.kazennikov.glorie;


import com.google.common.io.Files;
import groovy.lang.GroovyClassLoader;
import name.kazennikov.logger.Logger;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.tools.GroovyClass;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Groovy compiler
 *
 * @author Anton Kazennikov
 */
public class GroovyCompiler implements Compiler {
	List<Unit> units = new ArrayList<>();

	private static final Logger logger = Logger.getLogger();

	public static final String GROOVY = "groovy";
    public static boolean DEBUG = false;


	GroovyClassLoader groovyClassLoader;
	CompilationUnit cu;
	boolean compiled;


	public GroovyCompiler(CompilerConfiguration cc, GroovyClassLoader groovyClassLoader) {
		this.groovyClassLoader = groovyClassLoader;
		this.cu = new CompilationUnit(cc, null, groovyClassLoader);
	}


	@Override
	public boolean add(Unit unit) throws Exception {
		if(unit == null)
			return false;

		if(unit.compiler == this)
			return true;

		if(unit.compiler != null)
			throw new IllegalStateException("Unit already added to another compiler");

		if(!Objects.equals(unit.type, GROOVY))
			return false;

		unit.compiler = this;
		cu.addSource(unit.className, unit.source);
		units.add(unit);


		return true;
	}

	public boolean addSource(String className, String source) throws Exception{
		return add(new Unit(GROOVY, className, source));
	}

	@Override
	public void compile() throws Exception {
		try {
			cu.compile(Phases.CLASS_GENERATION);

			List<GroovyClass> cls = cu.getClasses();

			for(GroovyClass c : cls) {
				groovyClassLoader.defineClass(c.getName(), c.getBytes());
                if(DEBUG) {
                    Files.write(c.getBytes(), new File("tmpGroovyClasses", c.getName() + ".class"));
                }
			}

			compiled = true;
		} catch(Exception e) {
			logger.error("Couldn't compile reduce action", e);
			throw e;
		}
	}

	@Override
	public boolean hasCompiled() {
		return compiled;
	}

	@Override
	public Class<?> getClass(String name) throws Exception {
		if(!compiled)
			throw new IllegalStateException("Trying to get class before compile() was invoked");

		return groovyClassLoader.loadClass(name);
	}
}
