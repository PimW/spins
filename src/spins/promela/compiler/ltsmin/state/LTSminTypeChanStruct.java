package spins.promela.compiler.ltsmin.state;

import spins.promela.compiler.ltsmin.util.LTSminDebug;
import spins.promela.compiler.variable.ChannelVariable;
import spins.promela.compiler.variable.CustomVariableType;
import spins.promela.compiler.variable.Variable;
import spins.promela.compiler.variable.VariableType;

/**
 * A type struct that has three fixed members.
 * 
 * @author laarman
 */
public class LTSminTypeChanStruct extends LTSminTypeStruct {

	public static final Variable CHAN_FILL_VAR = new Variable(VariableType.SHORT, "filled", -1);
	public static final String CHAN_BUF = "buffer";

	public static Variable bufferVar(ChannelVariable cv) {
		int size = cv.getType().getBufferSize();
		assert (size > 0);
		return new Variable(null, CHAN_BUF, size);
	}

	public static Variable elemVar(int index) {
		return new Variable(null, elemName(index), -1);
	}
	
	private static final String CHAN_PREFIX 	= "channel_";
	private static final String CHAN_BUF_PREFIX = "buffer_";
	private int elements = 0;
	
	public LTSminTypeChanStruct(ChannelVariable cv, LTSminDebug debug) {
		super(wrapName(cv.getName()));
		addMember(new LTSminVariable(CHAN_FILL_VAR, this));
		LTSminTypeStruct buf = new LTSminTypeStruct(CHAN_BUF_PREFIX + cv.getName());
		for (Variable var : cv.getType().getVariableStore().getVariables()) {
			if (var instanceof ChannelVariable)
				throw new AssertionError("Channeltypes not supported in channel buffer.");
			if (var.getType() instanceof CustomVariableType) {
				CustomVariableType cvt = (CustomVariableType)var.getType();
				LTSminTypeStruct type = new LTSminTypeStruct(cvt.getName(), true);
				for (Variable v : cvt.getVariableStore().getVariables())
					LTSminStateVector.addVariable(type, v, debug);
			    buf.addMember(new LTSminVariable(type, var, elemName(), this));
			} else {
			    buf.addMember(new LTSminVariable(LTSminTypeNative.get(var), var, elemName(), this));
			}
		}
		addMember(new LTSminVariable(buf, CHAN_BUF, cv.getType().getBufferSize(), this));
	}

	private static String elemName(int index) {
		return "m"+ index;
	}
	
	private String elemName() {
		return elemName(elements++);
	}

	protected static String wrapName(String name) {
		return TYPE_PREFIX + CHAN_PREFIX + name +"_t";
	}
}
