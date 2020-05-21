package searchclient;

import java.util.*;
import java.util.stream.Collectors;

import searchclient.util.Sets;

public class MAState {
	private static final Random RNG = new Random(1);

	public int numAgents;
	public int height, width;
	public final String domain;

	// Arrays are indexed from the top-left of the level, with first index being row and second being column.
	// Row 0: (0,0) (0,1) (0,2) (0,3) ...
	// Row 1: (1,0) (1,1) (1,2) (1,3) ...
	// Row 2: (2,0) (2,1) (2,2) (2,3) ...
	// ...
	// (Start in the top left corner, first go down, then go right)
	// E.g. this.walls[2] is an array of booleans having size MAX_COL.
	// this.walls[row][col] is true if there's a wall at (row, col)
	//

	public final Set<Position> walls;
	public final TreeMap<Position, Character> boxes;
	public final Map<Position, Character> goals;
	public final Map<Position, Character> fakeGoals;

	public final TreeMap<Position, Character> agents;
	public final Map<Character, String> color;

	public MAState parent;
	public final List<Command> actions;

	public Set<Position> path;

	private final int g;

	public MAState(MAState parent, List<Command> actions) {
		this.actions = new ArrayList<>(actions);
		this.domain = parent.domain;
		this.parent = parent;
		this.g = parent.g() + 1;
		this.height = parent.height;
		this.width = parent.width;
		this.walls = parent.walls;
		this.boxes = new TreeMap<>(parent.boxes);
		this.goals = new HashMap<>(parent.goals);
		this.fakeGoals = parent.fakeGoals;
		this.agents = new TreeMap<>(parent.agents);
		this.color = parent.color;
		this.numAgents = parent.numAgents;
		this.path = new HashSet<>(parent.path);
		this.applyActions(actions);
	}

	public MAState(int width, int height, String domain) {
		this.domain = domain;
		this.parent = null;
		this.actions = null;
		this.g = 0;
		this.width = width;
		this.height = height;
		this.walls = new HashSet<>();
		this.boxes = new TreeMap<>();
		this.goals = new HashMap<>();
		this.fakeGoals = new HashMap<>();
		this.agents = new TreeMap<>();
		this.color = new HashMap<>();
		this.path = new HashSet<>();
	}

	public MAState(MAState parent, List<Command> actions, Boolean skrald) {
		this.actions = new ArrayList<>(actions);
		this.domain = parent.domain;
		this.parent = parent;
		this.g = parent.g() + 1;
		this.height = parent.height;
		this.width = parent.width;
		this.walls = parent.walls;
		this.boxes = new TreeMap<>(parent.boxes);
		this.goals = new HashMap<>(parent.goals);
		this.fakeGoals = parent.fakeGoals;
		this.agents = new TreeMap<>(parent.agents);
		this.color = parent.color;
		this.numAgents = parent.numAgents;
		this.path = new HashSet<>(parent.path);
		this.applyActionsIgnore(actions);
	}

	// helper function to clone
	private MAState(MAState state){
		if(state.actions==null)
			this.actions = null;
		else
			this.actions = new ArrayList<>(state.actions);
		this.domain = state.domain;
		if(state.parent==null)
			this.parent = null;
		else
			this.parent = new MAState(state.parent);
		this.g = state.g();
		this.height = state.height;
		this.width = state.width;
		this.walls = state.walls;
		this.boxes = new TreeMap<>(state.boxes);
		this.goals = new HashMap<>(state.goals);
		this.fakeGoals = state.fakeGoals;
		this.agents = new TreeMap<>(state.agents);
		this.color = state.color;
		this.numAgents = state.numAgents;
		this.path = new HashSet<>(state.path);
	}

	public int g() {
		return this.g;
	}

	public boolean isInitialState() {
		return this.parent == null;
	}

	public boolean isGoalState() {
		for (Position goalPos : this.goals.keySet())
			if (!this.isGoalSatisfied(goalPos))
				return false;
		return true;
	}

	public boolean isGoalSatisfied(Position goalPos) {
		Character g = this.goals.get(goalPos);
		Character a = this.agents.get(goalPos);
		Character b = this.boxes.get(goalPos);
		return g.equals(a) || g.equals(b);
	}

	public int goalCount() {
		int res = 0;
		for (Position goalPos : this.goals.keySet())
			if (!this.isGoalSatisfied(goalPos))
				res++;
		return res;
	}

	public int goalCount(String color) {
		int res = 0;
		for (Map.Entry<Position, Character> goal : this.goals.entrySet()) {
			Position goalPos = goal.getKey();
			Character goalType = goal.getValue();
			String goalColor = this.color.get(goalType);

			if (goalColor.equals(color) && !this.isGoalSatisfied(goalPos))
				res++;
		}
		return res;
	}

	public boolean agentAchievedGoal(char agent) {
		int agentId = Character.getNumericValue(agent);
		Position agentPos = this.getPositionOfAgent(agent);
		Command action = this.actions.get(agentId);

		if (action instanceof Command.Move) {
			if (this.goals.containsKey(agentPos) && this.isGoalSatisfied(agentPos))
				return true;

		} else if (action instanceof Command.Push) {
			Command.Push pushCommand = (Command.Push) action;

			if (this.goals.containsKey(agentPos) && this.isGoalSatisfied(agentPos))
				return true;

			Position boxPos = agentPos.add(pushCommand.getBoxDir());
			if (this.goals.containsKey(boxPos) && this.isGoalSatisfied(boxPos)) {
				return true;
			}
		} else if (action instanceof Command.Pull) {
			if (this.goals.containsKey(agentPos) && this.isGoalSatisfied(agentPos))
				return true;

			Position boxPos = this.parent.getPositionOfAgent(agent);
			if (this.goals.containsKey(boxPos) && this.isGoalSatisfied(boxPos)) {
				return true;
			}
		}

		return false;
	}

	public ArrayList<MAState> getExpandedStates(char agent, MAState nextState) {
		Position agentPos = this.getPositionOfAgent(agent);
		String agentColor = this.color.get(agent);

		ArrayList<MAState> expandedStates = new ArrayList<>();

		// Move
		for (Command.Dir agentDir : Command.Dir.values()) {
			Position newAgentPos = agentPos.add(agentDir);

			if (this.cellIsFree(newAgentPos) && nextState.cellIsFree(newAgentPos)) {
				ArrayList<Command> otherCommands = new ArrayList<>(nextState.actions);
				otherCommands.set(Character.getNumericValue(agent), new Command.Move(agentDir));

				MAState newState = new MAState(this, otherCommands);

				// newState.agents.remove(agentPos);
				// newState.agents.put(newAgentPos, agent);

				expandedStates.add(newState);
			}
		}

		// Push
		for (Command.Dir agentDir : Command.Dir.values()) {
			Position newAgentPos = agentPos.add(agentDir);

			// Make sure that there's actually a box to move
			if (this.boxAt(newAgentPos, agentColor) && nextState.boxAt(newAgentPos, agentColor)) {
				Position boxPos = newAgentPos;

				for (Command.Dir boxDir : Command.Dir.values()) {
					Position newBoxPos = boxPos.add(boxDir);

					// Check if there's something on the cell to which the agent is moving
					if (this.cellIsFree(newBoxPos) && nextState.cellIsFree(newBoxPos)) {
						ArrayList<Command> otherCommands = new ArrayList<>(nextState.actions);
						otherCommands.set(Character.getNumericValue(agent), new Command.Push(agentDir, boxDir));

						MAState newState = new MAState(this, otherCommands);

						// newState.agents.remove(agentPos);
						// newState.agents.put(newAgentPos, agent);

						// Character box = newState.boxes.remove(boxPos);
						// newState.boxes.put(newBoxPos, box);

						expandedStates.add(newState);
					}
				}
			}
		}

		// Pull
		for (Command.Dir boxDir : Command.Dir.values()) {
			Position boxPos = agentPos.add(boxDir);

			if (this.boxAt(boxPos, agentColor) && nextState.boxAt(boxPos, agentColor)) {

				for (Command.Dir agentDir : Command.Dir.values()) {
					Position newAgentPos = agentPos.add(agentDir);

					if (this.cellIsFree(newAgentPos) && nextState.cellIsFree(newAgentPos)) {
						ArrayList<Command> otherCommands = new ArrayList<>(nextState.actions);
						otherCommands.set(Character.getNumericValue(agent), new Command.Pull(agentDir, boxDir));

						MAState newState = new MAState(this, otherCommands);

						// newState.agents.remove(agentPos);
						// newState.agents.put(newAgentPos, agent);

						// Character box = newState.boxes.remove(boxPos);
						// newState.boxes.put(agentPos, box);

						expandedStates.add(newState);
					}
				}
			}
		}

		// NoOp
		ArrayList<Command> otherCommands = new ArrayList<>(nextState.actions);
		otherCommands.set(Character.getNumericValue(agent), new Command.NoOp());

		MAState newState = new MAState(this, otherCommands);
		expandedStates.add(newState);
		// Kommer sikkert til at fucke massivt med DFS og Greedy lol

		Collections.shuffle(expandedStates, RNG);
		return expandedStates;
	}

	public Position getPositionOfAgent(char agent) {
		for (Map.Entry<Position, Character> entry : this.agents.entrySet()) {
			if (entry.getValue() == agent) {
				return entry.getKey();
			}
		}
		return null;
		// throw new RuntimeException("Agent not found: " + agent);
	}


	public ArrayList<MAState> getExpandedStatesIgnore(char agent) {
		Position agentPos = this.getPositionOfAgent(agent);
		String agentColor = this.color.get(agent);

		ArrayList<MAState> expandedStates = new ArrayList<>();

		// Move
		for (Command.Dir agentDir : Command.Dir.values()) {
			Position newAgentPos = agentPos.add(agentDir);

			if (this.cellIsFreeIgnore(newAgentPos)) {
				ArrayList<Command> otherCommands = new ArrayList<>(Collections.nCopies(this.numAgents, new Command.NoOp()));
				otherCommands.set(Character.getNumericValue(agent), new Command.Move(agentDir));

				MAState newState = new MAState(this, otherCommands, true);
				newState.path.add(newAgentPos);
				expandedStates.add(newState);
			}
		}

		// Push
		for (Command.Dir agentDir : Command.Dir.values()) {
			Position newAgentPos = agentPos.add(agentDir);

			// Make sure that there's actually a box to move
			if (this.boxAt(newAgentPos, agentColor)) {
				Position boxPos = newAgentPos;

				for (Command.Dir boxDir : Command.Dir.values()) {
					Position newBoxPos = boxPos.add(boxDir);

					// Check if there's something on the cell to which the agent is moving
					if (this.cellIsFreeIgnore(newBoxPos) && !newBoxPos.equals(agentPos)) {
						ArrayList<Command> otherCommands = new ArrayList<>(Collections.nCopies(this.numAgents, new Command.NoOp()));
						otherCommands.set(Character.getNumericValue(agent), new Command.Push(agentDir, boxDir));

						MAState newState = new MAState(this, otherCommands, true);
						newState.path.add(newAgentPos);
						newState.path.add(newBoxPos);
						expandedStates.add(newState);
					}
				}
			}
		}

		// Pull
		for (Command.Dir boxDir : Command.Dir.values()) {
			Position boxPos = agentPos.add(boxDir);

			if (this.boxAt(boxPos, agentColor)) {

				for (Command.Dir agentDir : Command.Dir.values()) {
					Position newAgentPos = agentPos.add(agentDir);

					if (this.cellIsFreeIgnore(newAgentPos) && !newAgentPos.equals(boxPos)) {
						ArrayList<Command> otherCommands = new ArrayList<>(Collections.nCopies(this.numAgents, new Command.NoOp()));
						otherCommands.set(Character.getNumericValue(agent), new Command.Pull(agentDir, boxDir));

						MAState newState = new MAState(this, otherCommands, true);
						newState.path.add(newAgentPos);
						expandedStates.add(newState);
					}
				}
			}
		}

		// NoOp
		ArrayList<Command> otherCommands = new ArrayList<>(Collections.nCopies(this.numAgents, new Command.NoOp()));

		MAState newState = new MAState(this, otherCommands);
		newState.path.add(agentPos);
		expandedStates.add(newState);
		// Kommer sikkert til at fucke massivt med DFS og Greedy lol

		Collections.shuffle(expandedStates, RNG);
		return expandedStates;
	}

	public boolean cellIsFree(Position pos) {
		return !this.walls.contains(pos) && !this.boxAt(pos) && !this.agentAt(pos);
	}

	public boolean cellIsFreeIgnore(Position pos) {
		return !this.walls.contains(pos);
	}


	public boolean boxAt(Position pos) {
		return this.boxes.containsKey(pos);
	}

	public boolean boxAt(Position pos, String color) {
		return this.boxAt(pos) && this.color.get(this.boxes.get(pos)).equals(color);
	}

	public boolean agentAt(Position pos) {
		return this.agents.containsKey(pos);
	}

	public ArrayList<MAState> extractPlan() {
		ArrayList<MAState> plan = new ArrayList<>();
		MAState n = this;
		while (!n.isInitialState()) {
			plan.add(n);
			n = n.parent;
		}
		Collections.reverse(plan);
		return plan;
	}

	public ArrayList<MAState> extractPlanWithInitial() {
		ArrayList<MAState> plan = new ArrayList<>();
		for (MAState state = this; state != null; state = state.parent)
			plan.add(state);
		Collections.reverse(plan);
		return plan;
	}

	public boolean isApplicable(List<Command> actions) {
		for (Map.Entry<Position, Character> agent : this.agents.entrySet()) {
			Position agentPos = agent.getKey();
			char agentType = agent.getValue();
			int agentId = Character.getNumericValue(agentType);

			Command command = actions.get(agentId);
			if (command instanceof Command.Move) {
				Position newAgentPos = agentPos.add(((Command.Move) command).getAgentDir());

				if (!this.cellIsFree(newAgentPos))
					return false;

			} else if (command instanceof Command.Push) {
				Position newAgentPos = agentPos.add(((Command.Push) command).getAgentDir());
				Position boxPos = newAgentPos;
				Position newBoxPos = boxPos.add(((Command.Push) command).getBoxDir());
				String agentColor = this.color.get(agentType);

				if (!this.boxAt(newAgentPos, agentColor))
					return false;
				if (!this.cellIsFree(newBoxPos))
					return false;

			} else if (command instanceof Command.Pull) {
				Position boxPos = agentPos.add(((Command.Pull) command).getBoxDir());
				Position newAgentPos = agentPos.add(((Command.Pull) command).getAgentDir());
				Position newBoxPos = agentPos;
				String agentColor = this.color.get(agentType);

				if (!this.boxAt(boxPos, agentColor))
					return false;
				if (!this.cellIsFree(newAgentPos))
					return false;

			}
		}

		return true;
	}

	public boolean isCompatible(MAState nextState){

		// if(this.extractPlan().size()>10 && nextState.extractPlan().size()>10){
		// 	System.err.println("extractPlan from this");
		// 	for( MAState state : this.extractPlan().subList(this.extractPlan().size()-5, this.extractPlan().size()))
		// 		System.err.format("%s  actions: %s\n",state,state.actions);

		// 	System.err.println("extractPlan from nextState");
		// 	for( MAState state : nextState.extractPlan().subList(nextState.extractPlan().size()-5, nextState.extractPlan().size()))
		// 		System.err.format("%s actions: %s\n",state,state.actions);
		// }


		for(int i = 0 ; i<this.numAgents; i++){
			Command com = this.actions.get(i);
			char c = Integer.toString(i).charAt(0);
			if(!(com instanceof Command.NoOp) && this.agents.get(this.getPositionOfAgent(c))==null)
				return false;

			Command com2 = nextState.actions.get(i);
			if(!(com2 instanceof Command.NoOp)){
				if(nextState.getPositionOfAgent(c) == null || nextState.agents.get(nextState.getPositionOfAgent(c))==null)
				return false;
			}
		}
		// if(!this.parent.isApplicable(this.actions)){
		// 	System.err.format("%s could not be applied to \n%s\n",this.actions,this.parent);
		// 	assert false;
		// }
		// if(!nextState.parent.isApplicable(nextState.actions)){
		// 	assert false;
		// }

		// System.err.format("this has %s actions and is \n%s\n nextState has %s actions and is \n%s\n",this.actions,this,nextState.actions,nextState);

		Set<Position> thisNew = this.getNewPositions();
		// System.err.println("getting thatNew");
		Set<Position> thatNew = nextState.getNewPositions();
		// System.err.println("got thatNew");
		Set<Position> thisOld = this.getOldPositions();
		// System.err.println("getting thatOld");
		Set<Position> thatOld = nextState.getOldPositions();
		// System.err.println("got thatOld");
		// System.err.format("this.actions %s  nextState.actions %s\n",this.actions,nextState.actions);
		// System.err.format("thisNew: %s  thatNew: %s  thisOld: %s  thatOld: %s\n", thisNew, thatNew,thisOld,thatOld);

		if(!Sets.intersection(thisNew,thatNew).isEmpty()){
			System.err.println("thisNew and thatNew was not compatible");
			System.err.format("thisNew: %s    thatNew: %s  this:\n%s\n that: \n%s\n",thisNew,thatNew,this,nextState);	
			return false;
		}
		if(!Sets.intersection(thisNew,thatOld).isEmpty()){
			System.err.println("thisNew and thatOld was not compatible");	
			return false;
		}
		if(!Sets.intersection(thisOld,thatNew).isEmpty()){
			System.err.println("thisOld and thatNew was not compatible");	
			return false;
		}
		if(!Sets.intersection(thisOld,thatOld).isEmpty()){
			System.err.println("thisOld and thatOld was not compatible");	
			return false;
		}

		return true;
	}

	public char getCell(Position pos){
		if (this.agents.containsKey(pos)) {
			return this.agents.get(pos);
		} else if (this.boxAt(pos)) {
			return this.boxes.get(pos);
		} else if (this.walls.contains(pos)) {
			return '+';
		} else {
			return ' ';
		}
	}

	public Set<Position> getOldPositions(){
		MAState parent = this.parent;
		Set<Position> oldPositions = new HashSet<>();
		// System.err.format("parent agents is %s\n",parent.agents.entrySet());

		for (Map.Entry<Position, Character> agent : parent.agents.entrySet()) {
			Position agentPos = agent.getKey();
			char agentType = agent.getValue();
			int agentId = Character.getNumericValue(agentType);

			Command command = this.actions.get(agentId);
			if (command instanceof Command.Move) {
				oldPositions.add(agentPos);
				// System.err.format("move oldPosition is %s\n",oldPositions);

			} else if (command instanceof Command.Push) {

				Position newAgentPos = agentPos.add(((Command.Push) command).getAgentDir());

				oldPositions.add(agentPos);
				oldPositions.add(newAgentPos);
				// System.err.format("push oldPosition is %s\n",oldPositions);
			} else if (command instanceof Command.Pull) {

				Position boxPos = agentPos.add(((Command.Pull) command).getBoxDir());
				oldPositions.add(boxPos);
				oldPositions.add(agentPos);
				// System.err.format("pull oldPosition is %s\n",oldPositions);
			} else {
				// System.err.format("%s was not one of the other commands\n",command);
			}
		}

		return oldPositions;
	}

	public Set<Position> getNewPositions(){
		MAState parent = this.parent;
		Set<Position> newPositions = new HashSet<>();

		for (Map.Entry<Position, Character> agent : parent.agents.entrySet()) {
			Position agentPos = agent.getKey();
			char agentType = agent.getValue();
			int agentId = Character.getNumericValue(agentType);

			Command command = this.actions.get(agentId);
			if (command instanceof Command.Move) {

				Position newAgentPos = agentPos.add(((Command.Move) command).getAgentDir());

				newPositions.add(newAgentPos);

			} else if (command instanceof Command.Push) {

				Position newAgentPos = agentPos.add(((Command.Push) command).getAgentDir());
				Position boxPos = newAgentPos;
				Position newBoxPos = boxPos.add(((Command.Push) command).getBoxDir());

				newPositions.add(newBoxPos);
				newPositions.add(newAgentPos);

			} else if (command instanceof Command.Pull) {

				Position boxPos = agentPos.add(((Command.Pull) command).getBoxDir());
				Position newAgentPos = agentPos.add(((Command.Pull) command).getAgentDir());
				Position newBoxPos = agentPos;

				newPositions.add(newAgentPos);
				newPositions.add(newBoxPos);
			}
		}

		return newPositions;
	}

	public void applyActions(List<Command> actions) {
		TreeMap<Position, Character> agents = new TreeMap<>(this.agents);

		for (Map.Entry<Position, Character> agent : agents.entrySet()) {
			Position agentPos = agent.getKey();
			char agentType = agent.getValue();
			int agentId = Character.getNumericValue(agentType);

			Command command = actions.get(agentId);
			if (command instanceof Command.Move) {
				Position newAgentPos = agentPos.add(((Command.Move) command).getAgentDir());

				assert this.cellIsFree(newAgentPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

			} else if (command instanceof Command.Push) {
				Position newAgentPos = agentPos.add(((Command.Push) command).getAgentDir());
				Position boxPos = newAgentPos;
				Position newBoxPos = boxPos.add(((Command.Push) command).getBoxDir());
				String agentColor = this.color.get(agentType);

				assert this.boxAt(newAgentPos, agentColor) : String.format("Cannot apply %s to\n%s", actions, this.parent);
				assert this.cellIsFree(newBoxPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

				Character box = this.boxes.remove(boxPos);
				this.boxes.put(newBoxPos, box);

			} else if (command instanceof Command.Pull) {
				Position boxPos = agentPos.add(((Command.Pull) command).getBoxDir());
				Position newAgentPos = agentPos.add(((Command.Pull) command).getAgentDir());
				Position newBoxPos = agentPos;
				String agentColor = this.color.get(agentType);

				assert this.boxAt(boxPos, agentColor) : String.format("Cannot apply %s to\n%s", actions, this.parent);
				assert this.cellIsFree(newAgentPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

				Character box = this.boxes.remove(boxPos);
				this.boxes.put(newBoxPos, box);

			}
		}
	}

	private void applyActionsIgnore(List<Command> actions) {
		TreeMap<Position, Character> agents = new TreeMap<>(this.agents);

		for (Map.Entry<Position, Character> agent : agents.entrySet()) {
			Position agentPos = agent.getKey();
			char agentType = agent.getValue();
			int agentId = Character.getNumericValue(agentType);

			Command command = actions.get(agentId);
			if (command instanceof Command.Move) {
				Position newAgentPos = agentPos.add(((Command.Move) command).getAgentDir());

				assert this.cellIsFreeIgnore(newAgentPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

			} else if (command instanceof Command.Push) {
				Position newAgentPos = agentPos.add(((Command.Push) command).getAgentDir());
				Position newBoxPos = newAgentPos.add(((Command.Push) command).getBoxDir());
				String agentColor = this.color.get(agentType);

				assert this.boxAt(newAgentPos, agentColor) : String.format("Cannot apply %s to\n%s", actions, this.parent);
				assert this.cellIsFreeIgnore(newBoxPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

				Character box = this.boxes.remove(newAgentPos);
				this.boxes.put(newBoxPos, box);

			} else if (command instanceof Command.Pull) {
				Position boxPos = agentPos.add(((Command.Pull) command).getBoxDir());
				Position newAgentPos = agentPos.add(((Command.Pull) command).getAgentDir());
				String agentColor = this.color.get(agentType);

				assert this.boxAt(boxPos, agentColor) : String.format("Cannot apply %s to\n%s", actions, this.parent);
				assert this.cellIsFreeIgnore(newAgentPos) : String.format("Cannot apply %s to\n%s", actions, this.parent);

				this.agents.remove(agentPos);
				this.agents.put(newAgentPos, agentType);

				Character box = this.boxes.remove(boxPos);
				this.boxes.put(agentPos, box);

			}
		}
	}


	public MAState clone() {
		return new MAState(this);
	}


	public boolean isSAState() {
		return this.agents.size() == 1;
	}

	public void wallifyBoxes() {
		Set<String> agentColors = this.color.entrySet().stream().filter(entry -> Character.isDigit(entry.getKey())).map(Map.Entry::getValue).collect(Collectors.toSet());
		TreeMap<Position, Character> boxes = new TreeMap<>(this.boxes);

		for (Map.Entry<Position, Character> box : boxes.entrySet()) {
			Position boxPos = box.getKey();
			Character boxType = box.getValue();

			if (!agentColors.contains(this.color.get(boxType))) {
				this.boxes.remove(boxPos);
				this.walls.add(boxPos);
			}
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int hash = 0;

		// Requires sorted collection to guarantee iteration order
		for (Map.Entry<Position, Character> box : this.boxes.entrySet()) {
			Position pos = box.getKey();
			Character type = box.getValue();

			hash = hash * prime + pos.getRow();
			hash = hash * prime + pos.getCol();
			hash = hash * prime + type;
		}

		for (Map.Entry<Position, Character> agent : this.agents.entrySet()) {
			Position pos = agent.getKey();
			Character type = agent.getValue();

			hash = hash * prime + pos.getRow();
			hash = hash * prime + pos.getCol();
			hash = hash * prime + type;
		}

		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (this.getClass() != obj.getClass())
			return false;

		MAState other = (MAState) obj;
		if (!this.agents.equals(other.agents))
			return false;
		if (!this.boxes.equals(other.boxes))
			return false;
		if (!this.goals.equals(other.goals))
			return false;
		return this.walls.equals(other.walls);
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (int row = 0; row < this.height; row++) {
			for (int col = 0; col < this.width; col++) {
				Position pos = new Position(row, col);

				if (this.agents.containsKey(pos)) {
					s.append(this.agents.get(pos));
				} else if (this.boxAt(pos)) {
					s.append(Character.toLowerCase(this.boxes.get(pos)));
				} else if (this.walls.contains(pos)) {
					s.append("+");
				} else {
					s.append(" ");
				}
			}

			s.append(" | ");

			for (int col = 0; col < this.width; col++) {
				Position pos = new Position(row, col);

				if (this.goals.containsKey(pos)) {
					s.append(this.goals.get(pos));
				} else if (this.walls.contains(pos)) {
					s.append("+");
				} else {
					s.append(" ");
				}
			}
			if (row < this.height - 1) {
				s.append("\n");
			}
		}
		return s.toString();
	}

}
