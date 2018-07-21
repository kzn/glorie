package name.kazennikov.glorie;

/**
 * An abstract interface to compile source code to Java classes
 *
 * @author Anton Kazennikov
 */
public interface Compiler {
    /**
     * A compilation unit is a source code
     * that results in a single Java class
     */
	public static class Unit {
		public final String type;
		public final String className;
		public final String source;
		protected Compiler compiler;


		public Unit(String type, String className, String source) {
			this.type = type;
			this.source = source;
			this.className = className;
		}

		public Class<?> result() throws Exception {
			if(!compiler.hasCompiled())
				throw new IllegalStateException("compile() has not been called yet");

			return compiler.getClass(className);
		}
	}


    /**
     * Adds source code units to compiler
     * @param unit source code unit
     * @return true, if unit successfully added to this compiler
     */
	public boolean add(Unit unit) throws Exception;

    /**
     * Invoke compiler on all added source code units
     * Could be invoked at most once
     */
	public void compile() throws Exception;

    /**
     * Checks if the compiler has been already invoked
     */
	public boolean hasCompiled();

    /**
     * Get compiled class by name
     */
	public Class<?> getClass(String name) throws Exception;



}
