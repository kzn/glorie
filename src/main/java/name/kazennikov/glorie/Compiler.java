package name.kazennikov.glorie;

/**
 * Created on 14.12.15.
 *
 * @author Anton Kazennikov
 */
public interface Compiler {
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
				throw new IllegalStateException("compile() has not been called before result retrieval");

			return compiler.getClass(className);
		}
	}


	public boolean add(Unit unit) throws Exception;
	public void compile() throws Exception;
	public boolean hasCompiled();
	public Class<?> getClass(String name) throws Exception;



}
