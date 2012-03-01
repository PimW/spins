/**
 * 
 */
package spinja.promela.compiler.ltsmin.model;

import java.util.HashSet;
import java.util.Set;

import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.VariableAccess;
import spinja.promela.compiler.variable.VariableType;

public class ChannelTopExpression extends Expression {
	/**
	 * 
	 */
	private ChannelReadAction cra;
	private int elem;

	public ChannelTopExpression(ChannelReadAction cra, int elem) {
		super(new Token(PromelaConstants.NUMBER,"[" + cra.getIdentifier().getVariable().getName() +".nextRead].m"+elem));
		this.cra = cra;
		this.elem = elem;
	}

	@Override
	public Set<VariableAccess> readVariables() {
		return new HashSet<VariableAccess>();
	}

	public VariableType getResultType() throws ParseException {
		return null;
	}

	public ChannelReadAction getChannelReadAction() {
		return cra;
	}

	public int getElem() {
		return elem;
	}

}