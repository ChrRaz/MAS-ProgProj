package searchclient;

import java.util.*;

public class MAState {
	private static final Random RNG = new Random(1);

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

	public final TreeMap<Position, Character> agents;
	public final Map<Character, String> color;

	public MAState parent;
	public final Command action;

	private int g;

	public MAState(MAState parent, Command action) {
		this.action = action;
		this.domain = parent.domain;
		this.parent = parent;
		this.g = parent.g() + 1;
		this.height = parent.height;
		this.width = parent.width;
		this.walls = parent.walls;
		this.boxes = new TreeMap<>(parent.boxes);
		this.goals = parent.goals;
		this.agents = new TreeMap<>(parent.agents);
		this.color = parent.color;
	}

	public MAState(int width, int height, String domain) {
		this.domain = domain;
		this.parent = null;
		this.action = null;
		this.g = 0;
		this.width = width;
		this.height = height;
		this.walls = new HashSet<>();
		this.boxes = new TreeMap<>();
		this.goals = new HashMap<>();
		this.agents = new TreeMap<>();
		this.color = new HashMap<>();
	}

	public int g() {
		return this.g;
	}

	public boolean isInitialState() {
		return this.parent == null;
	}

	public boolean isGoalState() {
		// Antagelse: Single agent states har ikke agent goals
		for (Map.Entry<Position, Character> goal : this.goals.entrySet()) {
			Position pos = goal.getKey();
			Character g = goal.getValue();
			Character b = this.boxes.get(pos);
			if (!g.equals(b)) return false;
		}
		return true;
	}

	public boolean isGoalStateForAgent(char agent) {
		String agentColor = this.color.get(agent);

		// Antagelse: Single agent states har ikke agent goals
		for (Map.Entry<Position, Character> goal : this.goals.entrySet()) {
			Position pos = goal.getKey();
			Character g = goal.getValue();
			Character b = this.boxes.get(pos);
			if (this.color.get(g).equals(agentColor) && !g.equals(b)) return false;
		}
		return true;
	}

	public ArrayList<MAState> getExpandedStates(char agent, MAState nextState) {
		Position agentPos = null;
		for (Map.Entry<Position, Character> entry : this.agents.entrySet()) {
			if (entry.getValue() == agent) {
				agentPos = entry.getKey();
			}
		}

		assert agentPos != null;

		String agentColor = this.color.get(agent);

		ArrayList<MAState> expandedStates = new ArrayList<>();

		// Move
		for (Command.Dir agentDir : Command.Dir.values()) {
			Position newAgentPos = agentPos.add(agentDir);

			if (this.cellIsFree(newAgentPos) && nextState.cellIsFree(newAgentPos)) {
				MAState newState = new MAState(this, new Command.Move(agentDir));

				newState.agents.remove(agentPos);
				newState.agents.put(newAgentPos, agent);

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
						MAState newState = new MAState(this, new Command.Push(agentDir, boxDir));

						newState.agents.remove(agentPos);
						newState.agents.put(newAgentPos, agent);

						Character box = newState.boxes.remove(boxPos);
						newState.boxes.put(newBoxPos, box);

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

					if (this.cellIsFree(newAgentPos) && this.cellIsFree(newAgentPos)) {
						MAState newState = new MAState(this, new Command.Pull(agentDir, boxDir));

						newState.agents.remove(agentPos);
						newState.agents.put(newAgentPos, agent);

						Character box = newState.boxes.remove(boxPos);
						newState.boxes.put(agentPos, box);

						expandedStates.add(newState);
					}
				}
			}
		}

		// NoOp
		expandedStates.add(new MAState(this, new Command.NoOp()));
		// Kommer sikkert til at fucke massivt med DFS og Greedy lol

		Collections.shuffle(expandedStates, RNG);
		return expandedStates;
	}

	public boolean cellIsFree(Position pos) {
		return !this.walls.contains(pos) && !this.boxAt(pos) && !this.agentAt(pos);
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
				} else if (this.goals.containsKey(pos)) {
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
