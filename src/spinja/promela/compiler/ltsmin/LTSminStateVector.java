package spinja.promela.compiler.ltsmin;

import static spinja.promela.compiler.ltsmin.LTSminTreeWalker.constant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import spinja.promela.compiler.Proctype;
import spinja.promela.compiler.Specification;
import spinja.promela.compiler.parser.ParseException;
import spinja.promela.compiler.variable.ChannelType;
import spinja.promela.compiler.variable.ChannelVariable;
import spinja.promela.compiler.variable.CustomVariableType;
import spinja.promela.compiler.variable.Variable;
import spinja.promela.compiler.variable.VariableStore;
import spinja.promela.compiler.variable.VariableType;

/**
 * This class is responsible for:
 * - Adding meta variables to the model (channel read/filled, process pic/pc)
 * - Creating a tree of type structures: state_t
 * - Flattening the state type into a fixed-length vector with slots repr. tree
 *   leafs (NativeTypes)
 * - Translation: c code names <-- vector slots <--> model variables 
 *
 * @author Freark van der Berg, Alfons Laarman
 */
public class LTSminStateVector extends LTSminSubVectorStruct {

	public static String C_STATE;
	public static final String C_STATE_NAME = "state";
	public static final String C_STATE_GLOBALS = "globals";
	public static final String C_STATE_PROC_COUNTER = "_pc";
	public static final String C_STATE_PID = "_pid";
	public static final String C_NUM_PROCS_VAR = "_nr_pr";
	public static final String C_STATE_INITIAL = "initial";

	public static final VariableType C_TYPE_PROC_COUNTER 	= VariableType.BYTE;
	public static final VariableType C_TYPE_PID 			= VariableType.PID;

	public static final Variable _NR_PR = new Variable(VariableType.BYTE, C_NUM_PROCS_VAR, 1);

	private List<LTSminSlot> 			stateVector;// the flattened vector
	LTSminTypeStruct 					state_t;	// tree of structs

	/**
	 * Creates a new StateVector
	 */
	public LTSminStateVector() {
		super();
		super.setRoot(this);
		state_t = new LTSminTypeStruct(C_STATE_NAME);
		C_STATE = state_t.getName();
		super.setType(state_t);
		stateVector = new ArrayList<LTSminSlot>();
	}

	/**
	 * Creates the state vector and required types
	 */
	public void createVectorStructs(Specification spec, LTSminDebug debug) {
		addSpecification(state_t, spec, debug);	
		flattenStateVector(state_t, "");
		state_t.fix();
	}
	
	List<LTSminTypeStruct> types = null;
	public List<LTSminTypeStruct> getTypes() {
		if (null == types) {
			types = new ArrayList<LTSminTypeStruct>();
			extractStructs(types, state_t);
		}
		return types;
	}

	private void extractStructs(List<LTSminTypeStruct> list, LTSminTypeStruct struct) {
		for (LTSminVariable v : struct) {
			if (v.getType() instanceof LTSminTypeStruct) {
				extractStructs(list, (LTSminTypeStruct)v.getType());
			}
		}
		list.add(struct);
	}

	/**
	 * Flattens the state vector into a fixed-length array of slots.
	 * @param type the state vector
	 */
	private void flattenStateVector(LTSminTypeStruct type, String fullName) {
		for (LTSminVariable v : type) {
			// DFS pre-order
			//v.setOffset(stateVector.size());

			// recursion
			for (int i = 0; i < max(v.array(), 1); i++) {
				String fn = fullName +"."+ v.getName() +
						(v.array()>1 ? "["+i+"]" : "");
				if (v.getType() instanceof LTSminTypeStruct) {
					flattenStateVector ((LTSminTypeStruct)v.getType(), fn);
				} else {
					// Leafs (NativeTypes) in DFS order
					System.out.println(stateVector.size() +"\t"+ fn +"");
					stateVector.add(new LTSminSlot(v, fn, stateVector.size()));
				}
			}
		}
	}

	private static int max(int a, int b) {
		return a > b ? a : b;
	}

	/**
	 * Extract processes and globals from spec and add it to state_t
	 */
	private void addSpecification(LTSminTypeStruct state_t, Specification spec,
			LTSminDebug debug) {
		// Globals: initialise globals state struct and add to main state struct
		debug.say("== Globals");
		LTSminTypeStruct global_t = new LTSminTypeStruct(C_STATE_GLOBALS);
		VariableStore globals = spec.getVariableStore();
		globals.addVariable(_NR_PR);
		for (Variable var : globals.getVariables())
			addVariable(global_t, var, debug);
		// Add global state struct to main state struct
		state_t.addMember(new LTSminVariable(global_t, C_STATE_GLOBALS));

		// Add Never process
		if (spec.getNever()!=null) {
			debug.say("== Never");
			Proctype p = spec.getNever();
			addProcess (state_t, p, debug);
		}

		// Processes:
		debug.say("== Processes");
		int nr_active = 0;
		for (Proctype p : spec) {
			// Add PID
			Variable pid = new Variable(C_TYPE_PID, C_STATE_PID, 0, p);
			try { pid.setInitExpr(constant(p.getID()));
			} catch (ParseException e) { assert (false); }
			p.prependVariable(pid);
			
			addProcess (state_t, p, debug);
			nr_active += p.getNrActive();
		}
		// set number of processes to initial number of active processes.
		try { _NR_PR.setInitExpr(constant(nr_active));
		} catch (ParseException e) {assert (false);}
	}

	/**
	 * Add a variable declarations of proctype p to struct
	 */
	private void addProcess(LTSminTypeStruct state_t, Proctype p, LTSminDebug debug) {
		String name = p.getName();
		
		// Initialise process state struct and add to main state struct
		debug.say("[Proc] " + name);
		LTSminTypeStruct process_t = new LTSminTypeStruct(name);

		// Add PC
		Variable pc = new Variable(C_TYPE_PROC_COUNTER, C_STATE_PROC_COUNTER, 0, p);
		int initial_pc = (p.getNrActive() == 0 ? -1 : 0);
		try { pc.setInitExpr(constant(initial_pc));
		} catch (ParseException e) { assert (false); }
		p.prependVariable(pc);
	
		// Locals: add locals to the process state struct
		List<Variable> proc_vars = p.getVariables();
		for (Variable var : proc_vars) {
			addVariable(process_t, var, debug);
		}

		// Add process state struct to main state struct
		state_t.addMember(new LTSminVariable(process_t, name));
	}
	
	/**
	 * Add a variable declaration to struct
	 */
	private void addVariable(LTSminTypeStruct struct, Variable var, LTSminDebug debug) {
		String name = var.getName();
		LTSminVariable lvar = null;
		
		// Create LTSminType for the Variable
		if(var instanceof ChannelVariable) {
			ChannelVariable cv = (ChannelVariable)var;
			ChannelType ct = cv.getType();
			//skip channels references (ie proc arguments) and rendez-vous channels
			if (ct.getBufferSize() == -1 || ct.getBufferSize() == 0 ) return;
			debug.say("\t"+ var.getName() + "["+ var.getArraySize() +"]" +
					" of {"+ ct.getTypes().size() +"} ["+ ct.getBufferSize() +"]");
			LTSminType infoType = new LTSminTypeChanStruct(cv);
			lvar = new LTSminVariable(infoType, var);
		} else if(var.getType() instanceof VariableType) {
		   	assert (var.getType().getJavaName().equals("int"));
			debug.say("\t"+ var.getType().getName() +" "+ name);
			lvar = new LTSminVariable(new LTSminTypeNative(var), var);
		} else if (var.getType() instanceof CustomVariableType) {
			CustomVariableType cvt = (CustomVariableType)var.getType();
			LTSminTypeStruct type = new LTSminTypeStruct(cvt.getName());
			for (Variable v : cvt.getVariableStore().getVariables())
				addVariable(type, v, debug);
			lvar = new LTSminVariable(type, var);
		} else {
			throw new AssertionError("ERROR: Unable to handle: " + var.getType().getName());
		}

		// Add it to the struct
		struct.addMember(lvar);
	}

	@Override
	public Iterator<LTSminSlot> iterator() {
		return stateVector.iterator();
	}

	public int size() {
		return stateVector.size();
	}

	public LTSminSlot get(int i) {
		return stateVector.get(i);
	}

	public LTSminSubVectorArray sub(Proctype proc) {
		String name = (null == proc ? C_STATE_GLOBALS : proc.getName());
		return getSubVector(name);
	}

	public LTSminSubVector sub(Variable v) {
		LTSminSubVectorArray ar = sub(v.getOwner());
		return ar.sub(v.getName());
	}

	public Variable getPC(Proctype process) {
		return process.getVariable(C_STATE_PROC_COUNTER);
	}

	public Variable getPID(Proctype p) {
		return p.getVariable(C_STATE_PID);
	}
}
