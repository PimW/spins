package spinja.promela.compiler.ltsmin.model;

import java.util.ArrayList;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.ltsmin.LTSminTreeWalker;
import spinja.util.StringWriter;

/**
 *
 * @author FIB
 */
public class LTSminTransitionCombo extends LTSminTransition {
	public List<LTSminTransition> transitions;
	
	private String name;
	private Transition realTransition;

	public LTSminTransitionCombo(int group, String name,Proctype p, Transition real) {
		super(group, name, p);
		transitions = new ArrayList<LTSminTransition>();
		this.setRealTransition(real);
	}	
	public void addTransition(LTSminTransition t) {
		transitions.add(t);
	}

	public void prettyPrint(StringWriter w, LTSminTreeWalker printer) {
		w.appendLine("[",name,"]");
		w.indent();
		for(LTSminTransition t : transitions) {
			t.prettyPrint(w,printer);
		}
		w.outdent();
	}

	public Transition getRealTransition() {
		return realTransition;
	}

	public void setRealTransition(Transition realTransition) {
		this.realTransition = realTransition;
	}
}