/**
 * 
 */
package spins.promela.compiler.ltsmin.model;

import spins.promela.compiler.Proctype;

public class TimeoutTransition {
	public int trans;
	public Proctype p;
	public LTSminTransition lt;

	public TimeoutTransition(int trans, Proctype p, LTSminTransition lt) {
		this.trans = trans;
		this.p = p;
		this.lt = lt;
	}

}