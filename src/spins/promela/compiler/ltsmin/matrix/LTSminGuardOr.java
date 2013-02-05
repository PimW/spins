package spins.promela.compiler.ltsmin.matrix;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spins.promela.compiler.expression.Expression;
import spins.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminGuardOr extends LTSminGuardBase implements LTSminGuardContainer {
	public List<LTSminGuardBase> guards;

	public LTSminGuardOr() {
		guards = new ArrayList<LTSminGuardBase>();
	}

	public void addGuard(Expression e) {
		addGuard(new LTSminGuard(e));
	}

	public void addGuard(LTSminGuardBase guard) {
//		if(!guard.isDefinitelyFalse()) {
			guards.add(guard);
//		}
	}

	public void prettyPrint(StringWriter w) {
		if(guards.size()>0) {
			boolean first = true;
			w.appendLine("(");
			w.indent();
			for(LTSminGuardBase g: guards) {
				if(!first) w.append(" || ");
				first = false;
				g.prettyPrint(w);
			}
			w.outdent();
			w.appendLine(")");
		} else {
			w.appendLine("false _GOR_ ");
		}
	}

	public boolean isDefinitelyTrue() {
		for(LTSminGuardBase g: guards) {
			if(g.isDefinitelyTrue()) {
				return true;
			}
		}
		return false;
	}

	public boolean isDefinitelyFalse() {
		for(LTSminGuardBase g: guards) {
			if(!g.isDefinitelyFalse()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Iterator<LTSminGuardBase> iterator() {
		return guards.iterator();
	}

	@Override
	public int guardCount() {
		return guards.size();
	}
}