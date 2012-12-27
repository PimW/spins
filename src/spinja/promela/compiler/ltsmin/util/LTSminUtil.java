package spinja.promela.compiler.ltsmin.util;

import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.CHAN_FILL_VAR;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.bufferVar;
import static spinja.promela.compiler.ltsmin.state.LTSminTypeChanStruct.elemVar;
import static spinja.promela.compiler.parser.PromelaConstants.IDENTIFIER;

import java.util.Arrays;
import java.util.HashSet;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.actions.Action;
import spinja.promela.compiler.actions.AssignAction;
import spinja.promela.compiler.actions.ChannelReadAction;
import spinja.promela.compiler.actions.ChannelSendAction;
import spinja.promela.compiler.automaton.State;
import spinja.promela.compiler.automaton.Transition;
import spinja.promela.compiler.expression.AritmicExpression;
import spinja.promela.compiler.expression.BooleanExpression;
import spinja.promela.compiler.expression.ChannelLengthExpression;
import spinja.promela.compiler.expression.CompareExpression;
import spinja.promela.compiler.expression.ConstantExpression;
import spinja.promela.compiler.expression.Expression;
import spinja.promela.compiler.expression.Identifier;
import spinja.promela.compiler.ltsmin.LTSminPrinter.ExprPrinter;
import spinja.promela.compiler.ltsmin.matrix.LTSminPCGuard;
import spinja.promela.compiler.ltsmin.model.LTSminModel;
import spinja.promela.compiler.ltsmin.state.LTSminPointer;
import spinja.promela.compiler.ltsmin.state.LTSminStateVector;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.parser.PromelaConstants;
import spinja.promela.compiler.parser.Token;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.Variable;

public class LTSminUtil {

    public static class Pair<L,R> {
        public L left; public R right;
        public Pair(L l, R r) { this.left = l; this.right = r; }
    }

	/** Expressions **/
	public static Identifier channelBottom(Identifier id, int elem) {
		return channelIndex(id, constant(0), elem) ;
	}

	public static Identifier channelNext(Identifier id, int elem) {
		return channelIndex(id, chanLength(id), elem) ;
	}

	public static Identifier channelIndex(Identifier id, Expression index, int elem) {
		ChannelVariable cv = (ChannelVariable)id.getVariable();
		//int size = cv.getType().getBufferSize();
		//if (1 == size) index = constant(0);
		Identifier m = id(elemVar(elem));
		Identifier buf = id(bufferVar(cv), index, m);
		return new Identifier(id, buf);
	}

	public static AssignAction assign(Variable v, Expression expr) {
		return assign (id(v), expr);
	}

	public static AssignAction incr(Identifier id) {
		return new AssignAction(new Token(PromelaConstants.INCR,"++"), id, null);
	}

	public static AssignAction decr(Identifier id) {
		return new AssignAction(new Token(PromelaConstants.DECR,"--"), id, null);
	}

	public static AssignAction assign(Identifier id, Expression expr) {
		return new AssignAction(new Token(PromelaConstants.ASSIGN,"="), id, expr);
	}

	public static AssignAction assign(Variable v, int nr) {
		return assign(id(v), constant(nr));
	}

	public static Identifier id(Variable v) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, null);
	}

	public static Identifier id(Variable v, int c) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, constant(c), null);
	}

	public static Identifier id(Variable v, Expression arrayExpr, Identifier sub) {
		return new Identifier(new Token(IDENTIFIER,v.getName()), v, arrayExpr, sub);
	}

	public static CompareExpression compare(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new CompareExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}
	
	public static BooleanExpression bool(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}
	 
	public static AritmicExpression calc(int m, Expression e1, Expression e2) {
		String name = PromelaConstants.tokenImage[m];
		return new AritmicExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static BooleanExpression not(Expression e1) {
		int m = PromelaConstants.LNOT;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1);
	}

	public static BooleanExpression or(Expression e1, Expression e2) {
		int m = PromelaConstants.LOR;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static BooleanExpression and(Expression e1, Expression e2) {
		int m = PromelaConstants.LAND;
		String name = PromelaConstants.tokenImage[m];
		return new BooleanExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static CompareExpression eq(Expression e1, Expression e2) {
		int m = PromelaConstants.EQ;
		String name = PromelaConstants.tokenImage[m];
		return new CompareExpression(new Token(m,name.substring(1,name.length()-1)), e1, e2);
	}

	public static Expression compare(int m, Expression e1, int nr) {
		return compare(m, e1, constant(nr));
	}

    public static ConstantExpression bool(boolean b) {
        return new ConstantExpression(new Token(PromelaConstants.BOOL, ""+b), b?1:0);
    }
	
	public static ConstantExpression constant(int nr) {
		return new ConstantExpression(new Token(PromelaConstants.NUMBER, ""+nr), nr);
	}

	public static Identifier chanLength(Identifier id) {
		return new Identifier(id, CHAN_FILL_VAR);
	}
	
	/** Guards **/
	public static LTSminPCGuard pcGuard(LTSminModel model, State s, Proctype p) {
		Variable pc = model.sv.getPC(p);
		Expression left = id(pc);
		Expression right = constant(s.getStateId());
		Expression e = compare(PromelaConstants.EQ, left, right);
		return new LTSminPCGuard(e);
	}

	public static Expression dieGuard(LTSminModel model, Proctype p) {
		Variable pid = model.sv.getPID(p);
		Expression left = calc(PromelaConstants.PLUS, id(pid), constant(1)); 
		return compare (PromelaConstants.EQ, left, id(LTSminStateVector._NR_PR));
	}

	public static Expression chanEmptyGuard(Identifier id) {
		Expression left;
		try {
			left = new ChannelLengthExpression(null, id);
		} catch (ParseException e1) {
			throw new AssertionError(e1);
		}
		Expression right = constant(((ChannelType)id.getVariable().getType()).getBufferSize());
		Expression e = compare(PromelaConstants.LT, left, right);
		return e;
	}

	public static Expression chanContentsGuard(Identifier id) {
		return chanContentsGuard(id, 0);
	}

	public static Expression chanContentsGuard(Identifier id, int i) {
		Expression left;
		try {
			left = new ChannelLengthExpression(null, id);
		} catch (ParseException e1) {
			throw new AssertionError(e1);
		}
		Expression e = compare(PromelaConstants.GT, left, constant(i));
		return e;
	}

	/** Strings **/
	public static String printPC(Proctype process, LTSminPointer out) {
		Variable var = out.getPC(process);
		return printVar(var, out);
	}

	public static String printPID(Proctype process, LTSminPointer out) {
		Variable var = out.getPID(process);
		return printVar(var, out);
	}

	public static String printVar(Variable var, LTSminPointer out) {
		return print(new Identifier(var), out);
	}

	public static String print(Expression id, LTSminPointer out) {
		ExprPrinter printer = new ExprPrinter(out);
		return printer.print(id);
	}

	/** others **/
	public static Iterable<Transition> getOutTransitionsOrNullSet(State s) {
		if (s == null)
			return new HashSet<Transition>(Arrays.asList((Transition)null));
		return s.output;
	}

	public static boolean isRendezVousReadAction(Action a) {
		return a instanceof ChannelReadAction && 
				((ChannelReadAction)a).isRendezVous();
	}

	public static boolean isRendezVousSendAction(Action a) {
		return a instanceof ChannelSendAction &&
				((ChannelSendAction)a).isRendezVous();
	}
	
	/** Errors **/
	public static ParseException exception(String string, Token token) {
		return new ParseException(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}

	public static AssertionError error(String string, Token token) {
		return new AssertionError(string + " At line "+token.beginLine +"column "+ token.beginColumn +".");
	}
}