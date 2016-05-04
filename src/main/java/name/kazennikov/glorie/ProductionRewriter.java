package name.kazennikov.glorie;

import java.util.List;

/**
 * GLR grammar production rewriter.
 *
 * The rewriters are used to transform a grammar from EBNF to BNF
 */
public interface ProductionRewriter {
	public List<Production> rewriter(Grammar g, Production p);

}
