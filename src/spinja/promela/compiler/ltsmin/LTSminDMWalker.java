package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.model.LTSminUtil.chanLength;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelBottom;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelIndex;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.channelNext;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.compare;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.constant;
import static spinja.promela.compiler.ltsmin.model.LTSminUtil.id;
import static spinja.promela.compiler.ltsmin.state.LTSminStateVector._NR_PR;

import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssertAction;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.BreakAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.actions.ElseAction;
import spinja.promela.compiler.actions.ExprAction;
import spinja.promela.compiler.actions.GotoAction;
import spinja.promela.compiler.actions.OptionAction;
import spinja.promela.compiler.actions.PrintAction;
import spinja.promela.compiler.actions.Sequence;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.ChannelOperation;
import spinja.promela.compiler.expression.ChannelReadExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.EvalExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.expression.MTypeReference;
import spinja.promela.compiler.expression.RemoteRef;
import spinja.promela.compiler.expression.RunExpression;
import spinja.promela.compiler.expression.TimeoutExpression;
import spinja.promela.compiler.ltsmin.matrix.DepMatrix;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuard;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardAnd;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardBase;
import spinja.promela.compiler.ltsmin.matrix.LTSminGuardContainer;
import spinja.promela.compiler.ltsmin.matrix.LTSminLocalGuard;
import spinja.promela.compiler.ltsmin.model.LTSminIdentifier;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.model.LTSminTransition;
import spinja.promela.compiler.ltsmin.model.ResetProcessAction;
import spinja.promela.compiler.ltsmin.state.LTSminSlot;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.ltsmin.state.LTSminSubVector;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableType;

/**
 * Traverses LTSminModel structure and records read/write dependencies of
 * variables (slots in LTSmin state vector) in a matrix.
 * 
 * The code heavily depends on the state vector's ability to be navigated upon
 * its type structure and subdivided into sub vectors.
 * 
 * @see LTSminSubVector
 * 
 * @see Blom, van de Pol, Weber - Bridging the Gap between Enumerative and Symbolic Model Checkers 
 *
 * @author FIB, Alfons Laarman
 */
public class LTSminDMWalker {

	// An expression without constant value, to be used as unknow array index: 
	static final Identifier STAR = new LTSminIdentifier(new Variable(VariableType.INT, "STAR", -1));
	
	static public class Params {
		public final LTSminModel model;
		public final LTSminStateVector sv;
		public final DepMatrix depMatrix;
		public int trans;

		public Params(LTSminModel model, DepMatrix depMatrix, int trans) {
			this.model = model;
			this.depMatrix = depMatrix;
			this.trans = trans;
			this.sv = model.sv;
		}
	}

	public static void walkOneGuard(LTSminModel model, DepMatrix dm,
									LTSminGuard g, int num) {
		Params p = new Params(model, dm, num); 
		walkExpression(p, g.expr, MarkAction.READ);
	}
	
	static void walkModel(LTSminModel model, LTSminDebug debug) {
		debug.say("Generating DM information ...");
		debug.say_indent++;

		if(model.getDepMatrix()==null) {
			model.setDepMatrix(new DepMatrix(model.getTransitions().size(), model.sv.size()));
		}
		Params params = new Params(model, model.getDepMatrix(), 0);
		walkTransitions(params);

		debug.say_indent--;
		debug.say("Generating DM information done");
		debug.say("");
	}

	static void walkTransitions(Params params) {
		for(LTSminTransition t: params.model.getTransitions()) {
			params.trans = t.getGroup();
			walkTransition(params,t);
		}
	}

	static void walkTransition(Params params, LTSminTransition t) {
		for(LTSminGuardBase g : t.getGuards())
			walkGuard(params,g);
		for(Action a : t.getActions())
			walkAction(params,a);
		for(LTSminTransition atomic : t.getTransitions()) {
			for(LTSminGuardBase g : atomic.getGuards())
				walkGuard(params,g);
			for(Action a : atomic.getActions()) {
				walkAction(params,a);
			}
		}
	}

	static void walkGuard(Params params, LTSminGuardBase guard) {
		if (guard instanceof LTSminLocalGuard) { // Nothing
		} else if(guard instanceof LTSminGuard) {
			LTSminGuard g = (LTSminGuard)guard;
			walkExpression(params, g.expr, MarkAction.READ);
		} else if(guard instanceof LTSminGuardContainer) {
			LTSminGuardContainer g = (LTSminGuardContainer)guard;
			for(LTSminGuardBase gb : g) {
				walkGuard(params,gb);
			}
		} else {
			throw new AssertionError("UNSUPPORTED: " + guard.getClass().getSimpleName());
		}
	}

	static void walkAction(Params params, Action a) {
		if(a instanceof AssignAction) {
			AssignAction as = (AssignAction)a;
			Identifier id = as.getIdentifier();
			walkExpression(params, id, MarkAction.WRITE); // read
			switch (as.getToken().kind) {
				case PromelaConstants.ASSIGN:
					walkExpression(params, as.getExpr(), MarkAction.READ); // read
					break;
				case PromelaConstants.DECR:
				case PromelaConstants.INCR:
					walkExpression(params, id, MarkAction.READ);
					break;
				default:
					throw new AssertionError("unknown assignment type");
			}
		} else if(a instanceof ResetProcessAction) {
			ResetProcessAction rpa = (ResetProcessAction)a;
			DMIncWritePC(params, rpa.getProcess());
			DMIncWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR); // also done by guard, just to be sure
			DMIncWriteEntire(params, params.model.sv.sub(rpa.getProcess()));
		} else if(a instanceof AssertAction) {
			AssertAction as = (AssertAction)a;
			Expression e = as.getExpr();
			walkExpression(params,e, MarkAction.READ);
		} else if(a instanceof PrintAction) {
			PrintAction pa = (PrintAction)a;
			List<Expression> exprs = pa.getExprs();
			for (final Expression expr : exprs) {
				walkExpression(params,expr, MarkAction.READ);
			}
		} else if(a instanceof ExprAction) {
			ExprAction ea = (ExprAction)a;
			Expression expr = ea.getExpression();
			if (expr.getSideEffect() == null) return; // simple expressions are guards
			//a RunExpression has side effects... yet it does not block if less than 255 processes are started atm
			assert (expr instanceof RunExpression);
			DMIncWrite(params, _NR_PR);
			DMIncRead(params, _NR_PR);
			RunExpression re = (RunExpression)expr;
			for (Proctype p : re.getInstances()) {
				DMIncWritePC(params, p);
				DMIncReadPC(params, p); // we also read to check multiple instantiations
				DMIncWritePID(params, p);
				//write to the arguments of the target process
				for (Variable v : p.getArguments()) {
					if (v.getType() instanceof ChannelType || v.isStatic())
						continue; //passed by reference or in initial state 
					DMIncWrite(params, v);
				}
			}
			for (Action rea : re.getActions()) {
				walkAction(params, rea);
			}
		} else if(a instanceof OptionAction) { // options in a d_step sequence
			OptionAction oa = (OptionAction)a;
			LTSminGuardAnd orc = new LTSminGuardAnd();
			for (Sequence seq : oa) {
				Action act = seq.iterator().next();
				LTSminTreeWalker.createEnabledGuard(act, orc);
				for (Action sa : seq) {
					walkAction(params, sa);
				}
			}
			walkGuard(params, orc);
		} else if(a instanceof BreakAction) {
			// noop
		} else if(a instanceof ElseAction) {
			// noop
		} else if(a instanceof GotoAction) { // only in d_steps (not a transition)
			// noop
		} else if(a instanceof ChannelSendAction) {
			ChannelSendAction csa = (ChannelSendAction)a;			
			Identifier id = csa.getIdentifier();
			int m = 0;
			for (Expression e : csa.getExprs()) {
				Expression top = channelNext(id, m++);
				walkExpression(params, top, MarkAction.WRITE);
				walkExpression(params, e, MarkAction.READ);
			}
			walkExpression(params, chanLength(id), MarkAction.BOTH);
		} else if(a instanceof ChannelReadAction) {
			ChannelReadAction cra = (ChannelReadAction)a;			
			Identifier id = cra.getIdentifier();
			int m = 0;
			// the read action itself:
			for (Expression e : cra.getExprs()) {
				Expression read = cra.isRandom() ? channelIndex(id, STAR, m) :
									channelBottom(id, m);
				if (e instanceof Identifier) { // otherwise it is a guard!
					walkExpression(params, e, MarkAction.WRITE);
					walkExpression(params, read, MarkAction.READ);
				}
				m++;
			}
			if (!cra.isPoll()) {
				walkExpression(params, chanLength(id), MarkAction.BOTH);
				// the replacement action:
				m = 0;
				for (@SuppressWarnings("unused") Expression e : cra.getExprs()) {
					Expression buf = channelIndex(id, STAR, m++);
					walkExpression(params, buf, MarkAction.BOTH);
				}
			}
		} else { // Handle not yet implemented action
			throw new AssertionError("LTSMinPrinter: Not yet implemented: "+a.getClass().getName());
		}
	}
	
	/**
	 * Uses Identifier expressions to walk over LTSmin sub vectors. Complete
	 * identifiers, i.e. those that point to (an index of) a native type,
	 * will be marked in the DM using the associated MarkAction. Incomplete ones
	 * throw an AssertionError.
	 */
	public static class IdMarker {
		MarkAction mark;
		Params params;
		public IdMarker(Params params, MarkAction mark) {
			this.params = params;
			this.mark = mark;
		}
		public IdMarker(IdMarker idMarker, MarkAction mark) {
			this.params = idMarker.params;
			this.mark = mark;
		}

		public void mark(Expression e) {
			if (null == e) return;
			if (e instanceof LTSminIdentifier) {
			} else if (e instanceof Identifier) {
				Identifier id = (Identifier)e;
				if (id.getVariable().isHidden()) {
					if (MarkAction.WRITE != mark) throw new AssertionError("Error reading from a hidden variable.");
					return;
				}
				LTSminSubVector sub = params.sv.sub(id.getVariable());
				try {
					sub.mark(this, id);
				} catch (AssertionError ae) {
					throw new AssertionError("Marking expression "+ id +" failed with: "+ ae);
				}
			} else {
				walkExpression(params, e, mark);
			}
		}

		public void doMark(LTSminSubVector sub) {
			mark.DMIncMark(params, sub);
		}
	}

	/**
	 * A marker for SLOTS in the state vector: READ, WRITE or BOTH.
	 */
	public static enum	MarkAction {
		READ,
		WRITE,
		BOTH;
		private void DMIncMark(Params params, LTSminSubVector sub) {
			switch (this) {
			case READ: DMIncRead (params, sub); break;
			case WRITE:DMIncWrite(params, sub); break;
			case BOTH: DMIncRead (params, sub);
					   DMIncWrite(params, sub); break;
			}
		}
	}

	static void walkExpression(Params params, Expression e, MarkAction mark) {
		if (mark == MarkAction.WRITE && !(e instanceof Identifier))
			throw new AssertionError("Only identifiers and TopExpressions can be written to!");
		if(e instanceof LTSminIdentifier) { //nothing
		} else if(e instanceof Identifier) {
			new IdMarker(params, mark).mark(e);
		} else if(e instanceof AritmicExpression) {
			AritmicExpression ae = (AritmicExpression)e;
			Expression ex1 = ae.getExpr1();
			Expression ex2 = ae.getExpr2();
			Expression ex3 = ae.getExpr3();
			if (ex2 == null) {
				walkExpression(params,ex1, mark);
			} else if (ex3 == null) {
				if (ae.getToken().image.equals("%")) {
					// Modulo takes a special notation to make sure that it
					// returns a positive value
					walkExpression(params,ex1, mark);
					walkExpression(params,ex2, mark);
				} else {
					walkExpression(params,ex1, mark);
					walkExpression(params,ex2, mark);
				}
			} else {
				walkExpression(params,ex1, mark);
				walkExpression(params,ex2, mark);
				walkExpression(params,ex3, mark);
			}
		} else if(e instanceof BooleanExpression) {
			BooleanExpression be = (BooleanExpression)e;
			Expression ex1 = be.getExpr1();
			Expression ex2 = be.getExpr2();
			if (ex2 == null) {
				walkExpression(params,ex1, mark);
			} else {
				walkExpression(params,ex1, mark);
				walkExpression(params,ex2, mark);
			}
		} else if(e instanceof CompareExpression) {
			CompareExpression ce = (CompareExpression)e;
			walkExpression(params,ce.getExpr1(), mark);
			walkExpression(params,ce.getExpr2(), mark);
		} else if(e instanceof ChannelLengthExpression) {
			ChannelLengthExpression cle = (ChannelLengthExpression)e;
			Identifier id = (Identifier)cle.getExpression();
			walkExpression(params, chanLength(id), mark);
		} else if(e instanceof ChannelReadExpression) {
			ChannelReadExpression cre = (ChannelReadExpression)e;
			Identifier id = cre.getIdentifier();
			// mark variables as read
			int m = 0;
			for (Expression expr : cre.getExprs()) {
				Expression read = cre.isRandom() ? channelIndex(id, STAR, m) :
													channelBottom(id, m);
				walkExpression(params, read, mark);
				walkExpression(params, expr, mark);
				m++;
			}
			walkExpression(params, chanLength(id), mark);
		} else if(e instanceof ChannelOperation) {
			ChannelOperation co = (ChannelOperation)e;
			Identifier id = (Identifier)co.getExpression();
			ChannelType ct = (ChannelType)id.getVariable().getType();
			if (!ct.isRendezVous()) // chanops on rendez-vous return true
				walkExpression(params, chanLength(id), mark);
		} else if(e instanceof RunExpression) {
			DMIncRead(params, _NR_PR); // only the guard!
		} else if(e instanceof MTypeReference) {
		} else if(e instanceof ConstantExpression) {
		} else if (e instanceof RemoteRef) {
			RemoteRef rr = (RemoteRef)e;
			Variable pc = rr.getPC(params.model);
			int num = rr.getLabelId();
			Expression comp = compare(PromelaConstants.EQ, id(pc), constant(num));
			walkExpression(params, comp, mark);
		} else if(e instanceof EvalExpression) {
			EvalExpression eval = (EvalExpression)e;
			walkExpression(params, eval.getExpression(), mark);
		} else if(e instanceof TimeoutExpression) {
			throw new AssertionError("LTSminDMWalker: Not yet implemented: "+e.getClass().getName());
		} else {
			throw new AssertionError("LTSminDMWalker: Not yet implemented: "+e.getClass().getName());
		}
	}

	static void DMIncWrite(Params params, LTSminSubVector sub) {
		if (!(sub instanceof LTSminSlot))
			throw new AssertionError("Variable is not a native type: "+ sub);
		int offset = ((LTSminSlot)sub).getIndex();
		params.depMatrix.incWrite(params.trans, offset);
	}

	static void DMIncRead(Params params, LTSminSubVector sub) {
		if (!(sub instanceof LTSminSlot))
			throw new AssertionError("Variable is not a native type: "+ sub);
		int offset = ((LTSminSlot)sub).getIndex();
		params.depMatrix.incRead(params.trans, offset);
	}

	static void DMIncWriteEntire(Params params, LTSminSubVector sub) {
		for (LTSminSlot slot : sub) {
			DMIncWrite(params, slot);
		}
	}

	private static void DMIncReadPC(Params params, Proctype p) {
		Variable pc = params.sv.getPC(p);
		DMIncRead(params, pc);
	}

	private static void DMIncWritePC(Params params, Proctype p) {
		Variable pc = params.sv.getPC(p);
		DMIncWrite(params, pc);
	}

	private static void DMIncWritePID(Params params, Proctype p) {
		Variable pc = params.sv.getPID(p);
		DMIncWrite(params, pc);
	}
	
	private static void DMIncRead(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.READ);
	}

	private static void DMIncWrite(Params params, Variable var) {
		walkExpression(params, id(var), MarkAction.WRITE);
	}
}
