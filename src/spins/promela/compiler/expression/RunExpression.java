// Copyright 2010, University of Twente, Formal Methods and Tools group
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package spins.promela.compiler.expression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spins.promela.compiler.ProcInstance;
import spins.promela.compiler.Proctype;
import spins.promela.compiler.actions.Action;
import spins.promela.compiler.parser.ParseException;
import spins.promela.compiler.parser.Token;
import spins.promela.compiler.variable.VariableAccess;
import spins.promela.compiler.variable.VariableType;
import spins.util.StringWriter;

/**
 * The run expression represents the starting of a new proctype. The run expression can be used to
 * read the number of the proctype that has be created (and is therefor the only expression with a
 * sideeffect).
 * 
 * @author Marc de Jonge
 */
public class RunExpression extends Expression implements CompoundExpression {

	private final String id;

	private final Proctype proc;

	private final List<Expression> exprs;

	private ProcInstance instance = null;

	/**
	 * Creates a new RunExpression using the identifier specified to run the proctype.
	 * 
	 * @param token
	 *            The token that is stored for debug reasons.
	 * @param id
	 *            The name of the proctype that is to be started.
	 */
	public RunExpression(final Token token, final String id) {
		super(token);
		this.id = id;
		this.proc = null;
		exprs = new ArrayList<Expression>();
	}

    public final boolean equals(Object o) {
        if (!(o instanceof RunExpression))
            return false;
        RunExpression other = (RunExpression)o;
        return (proc == null || proc.equals(other.proc)) &&
               id.equals(other.id) && 
               exprs.equals(other.exprs);
    }

    public final int hashCode() {
        return (proc == null ? 0 : proc.hashCode() * 37) +
               id.hashCode() * 13 +
               exprs.hashCode();
    }
	
	public RunExpression(final Token token, final Proctype proc) {
		super(token);
		this.id = proc.getName();
		this.proc = proc;
		exprs = new ArrayList<Expression>();
	}

	public void addExpression(final Expression expr) throws ParseException {
		exprs.add(expr);
	}

	private String getArgs() throws ParseException {
		final StringWriter w = new StringWriter();
		for (final Expression expr : exprs) {
			w.append(expr == exprs.get(0) ? "" : ", ").append(expr.getIntExpression());
		}
		return w.toString();
	}

	@Override
	public String getBoolExpression() {
		return "true";
	}

	@Override
	public String getIntExpression() throws ParseException {
		return "run (new " + getId() + "(" + getArgs() + "))";
	}

	@Override
	public VariableType getResultType() {
		return VariableType.PID;
	}

	@Override
	public String getSideEffect() {
	    try {
	        return getIntExpression();
	    } catch (ParseException e) {
	        throw new AssertionError();
	    }
	}

	@Override
	public Set<VariableAccess> readVariables() {
		final Set<VariableAccess> rv = new HashSet<VariableAccess>();
		for (final Expression expr : exprs) {
			rv.addAll(expr.readVariables());
		}
		return rv;
	}

	@Override
	public String toString() {
		try {
			return "run " + id + "(" + getArgs() + ")";
		} catch (final Exception ex) {
			return "run " + id + "()";
		}
	}

	public String getId() {
		return id;
	}

	public Proctype getProctype() {
		return proc;
	}

	public List<Expression> getExpressions() {
		return exprs;
	}

	public void setInstance(ProcInstance pi) {
		instance = pi;
	}

	public List<ProcInstance> getInstances() {
		if (instance == null) return proc.getInstances();
		return Arrays.asList(instance);
	}

	private List<Action> actions = new ArrayList<Action>();

	public void addInitAction(Action action) {
		actions.add(action);
	}

	public List<Action> getInitActions() {
		return actions;
	}
}
