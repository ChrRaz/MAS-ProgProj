package searchclient;

import searchclient.agent.Agent;
import searchclient.agent.Heuristic;
import searchclient.agent.Strategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Main {

	public static MAState parseLevel(BufferedReader serverMessages) throws IOException {

		String line;

		line = serverMessages.readLine();
		assert "#domain".equals(line);
		String domain = serverMessages.readLine();

		line = serverMessages.readLine();
		assert "#levelname".equals(line);
		String name = serverMessages.readLine();

		line = serverMessages.readLine();
		assert "#colors".equals(line);

		HashMap<Character, String> colors = new HashMap<>();
		Pattern pattern = Pattern.compile("[A-Z0-9]");

		// Colour
		while (true) {
			line = serverMessages.readLine();
			// Note: this
			if (line.startsWith("#")) break;

			String[] split = line.split(":");
			String color = split[0];

			// Find agents and boxes
			Matcher matcher = pattern.matcher(split[1]);
			while (matcher.find()) {
				colors.put(matcher.group().charAt(0), color);
			}
		}

		assert "#initial".equals(line);

		ArrayList<String> lines = new ArrayList<>();
		int height = 0, width = 0;

		while (true) {
			line = serverMessages.readLine();
			if (line.startsWith("#")) break;

			lines.add(line);
			width = Math.max(width, line.length());
			height++;
		}

		System.err.printf("Level is %dx%d\n", width, height);
		MAState initialState = new MAState(width, height, domain);

		for (int i = 0; i < lines.size(); i++) {
			// Make sure that input is rectangular
			lines.set(i, String.format("%-" + width + "s", lines.get(i)));
		}

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				char chr = lines.get(row).charAt(col);

				if (chr == '+') { // Wall.
					initialState.walls.add(new Position(row, col));
				} else if ('0' <= chr && chr <= '9') { // Agent.
					initialState.agents.put(new Position(row, col), chr);
				} else if ('A' <= chr && chr <= 'Z') { // Box.
					initialState.boxes.put(new Position(row, col), chr);
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.println("Error, read invalid level character: " + (int) chr);
					System.exit(1);
				}

			}
		}

		assert "#goal".equals(line);

		lines.clear();
		int goalHeight = 0, goalWidth = 0;

		while (true) {
			line = serverMessages.readLine();
			if (line.startsWith("#")) break;

			lines.add(line);
			goalWidth = Math.max(goalWidth, line.length());
			goalHeight++;
		}

		assert height == goalHeight && width == goalWidth;

		for (int i = 0; i < lines.size(); i++) {
			// Make sure that input is rectangular
			lines.set(i, String.format("%-" + width + "s", lines.get(i)));
		}

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				char chr = lines.get(row).charAt(col);

				if (chr == '+') { // Wall.

				} else if (('0' <= chr && chr <= '9') || ('A' <= chr && chr <= 'Z')) { // Goal / agent
					initialState.goals.put(new Position(row, col), chr);
				} else if (chr == ' ') {
					// Free space.
				} else {
					System.err.printf("Error, read invalid level character: %c (%#x)\n", chr, (int) chr);
					System.exit(1);
				}

			}
		}

		initialState.color.putAll(colors);

		assert "#end".equals(line);

		// TODO: Convert boxes with no agents to walls

		initialState.numAgents = initialState.agents.size();
		return initialState;
	}

	public static List<MAState> splitLevel(MAState initialState){ // :)

		initialState.wallifyBoxes();

		// Initialise distance map aka np.zeros :)
		int[][] sectionIndex = new int[initialState.height][initialState.width];

		int goalIndex = 1;
		int height = initialState.height, width = initialState.width;
		String domain = initialState.domain;

		List<MAState> subLevels = new ArrayList<>();
		for (Map.Entry<Position, Character> goal : initialState.goals.entrySet()) {
			Position goalPosition = goal.getKey();

			if (sectionIndex[goalPosition.getRow()][goalPosition.getCol()] == 0) // if goal is not already seen fill from this goal
			{

				MAState newState = new MAState(width, height, domain);
				newState.numAgents = initialState.numAgents;
				newState.color.putAll(initialState.color);
				ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(goalPosition));

				while (!frontier.isEmpty()) {
					Position p = frontier.pop();
					int row = p.getRow(), col = p.getCol();

					if (initialState.walls.contains(p)) {
						newState.walls.add(p);
						continue; // pls continue while loop
					}

					if (sectionIndex[row][col] == 0) {
						sectionIndex[row][col] = goalIndex; // :)

						if (initialState.goals.containsKey(p)) {
							newState.goals.put(p, initialState.goals.get(p));
						}
						if (initialState.boxes.containsKey(p)) {
							newState.boxes.put(p, initialState.boxes.get(p));
						}
						if (initialState.agents.containsKey(p)) {
							newState.agents.put(p, initialState.agents.get(p));
						}

						for (Command.Dir dir : Command.Dir.values()) {
							frontier.add(p.add(dir));
						}
					}
				}
				goalIndex++;
				MAState tmpState = new MAState(newState, Collections.nCopies(newState.numAgents, new Command.NoOp()));

				subLevels.add(newState);

			}
		}

		List<MAState> result;

		if (subLevels.size() == 1)
			result = subLevels;
		else {
			result = new ArrayList<>();
			for (MAState subLevel : subLevels)
				result.addAll(splitLevel(subLevel));
		}

		return result;
	}

	public static List<Position> orderGoals(MAState initialState) {
		LinkedHashSet<Position> explored = new LinkedHashSet<>();
		PriorityQueue<Map.Entry<Position, Integer>> frontier = new PriorityQueue<>(Map.Entry.comparingByValue());

		for (int i = 1; i < initialState.height - 1; i++) {
			for (int j = 1; j < initialState.width - 1; j++) {
				Position p = new Position(i, j);

				if (!initialState.walls.contains(p)) {
					int count = 4;

					for (Command.Dir dir : Command.Dir.values()) {
						if (initialState.walls.contains(p.add(dir)))
							count--;
					}
					int agentExtra = 0;
					if(initialState.goals.containsKey(p)){
						Character goalType = initialState.goals.get(p);
						if(Character.isDigit(goalType))
						agentExtra += 100000;
					}
						


					int dist2Agent = 0;
					if(initialState.goals.containsKey(p)){
						int temp = 1000;
						for(Map.Entry<Position,Character> agent : initialState.agents.entrySet()){
							temp = Math.min(temp,Position.distance(agent.getKey(), p));
						}
						dist2Agent -= temp;
					}
					frontier.add(Map.entry(p, count*10000 + dist2Agent + agentExtra));
				}
			}
		}

		// System.err.println(frontier);

		while (!frontier.isEmpty()) {
			Map.Entry<Position, Integer> entry = frontier.poll();
			Position p = entry.getKey();

			if (!p.within(0, 0, initialState.height, initialState.width))
				continue;

			if (explored.add(p)) {
				// System.err.println(entry);

				for (Command.Dir dir : Command.Dir.values()) {
					Position p2 = p.add(dir);

					if (!initialState.walls.contains(p2)) {
						int count = 4;

						for (Command.Dir dir2 : Command.Dir.values()) {
							Position p3 = p2.add(dir2);
							if (explored.contains(p3) || initialState.walls.contains(p3)) {
								count--;
							}
						}
						int agentExtra = 0;
						if(initialState.goals.containsKey(p)){
							Character goalType = initialState.goals.get(p);
							if(Character.isDigit(goalType))
							agentExtra += 100000;
						}
						int dist2Agent = 0;
						if(initialState.goals.containsKey(p2)){
							int temp = 1000;
							for(Map.Entry<Position,Character> agent : initialState.agents.entrySet()){
								temp = Math.min(temp,Position.distance(agent.getKey(), p2));
							}
							dist2Agent -= temp;
						}
						frontier.add(Map.entry(p2, count*10000 + dist2Agent+agentExtra));// M²
					}
				}
			}
		}

		ArrayList<Position> positions = new ArrayList<>(explored);
		positions.retainAll(initialState.goals.keySet());

		return positions;
	}

	public static List<MAState> maSolve(MAState initialState) { // :)
		int numAgents = initialState.numAgents;

		List<MAState> maSolution = new ArrayList<>(Collections.singletonList(initialState));

		// Keep track of how many actions each agent has already performed
		int[] actionsPerformed = new int[numAgents];
		// All agents have initially performed 0 actions
		Arrays.fill(actionsPerformed, 0);

		while (!maSolution.get(maSolution.size() - 1).isGoalState()) {

			List<MAState> fastestSASolution = null;
			int fastestAgent = -1;


			System.err.printf("Moves: %s\n", Arrays.toString(actionsPerformed));

			// Find single agent-goal pair such that agent fills goal fastest
			for (Map.Entry<Position, Character> agent : initialState.agents.entrySet()) {
				Position agentPos = agent.getKey();
				char agentType = agent.getValue();
				int agentId = Character.getNumericValue(agentType);
				String agentColor = initialState.color.get(agentType);

				if (maSolution.get(maSolution.size() - 1).goalCount(agentColor) == 0)
					continue;


				int moves = actionsPerformed[agentId];
				MAState state = maSolution.get(moves);

				ArrayList<MAState> saSolution = Agent.search(agentType, maSolution.subList(moves, maSolution.size()),
					new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)));

				if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
					fastestSASolution = saSolution;
					fastestAgent = agentId;
				}
			}


			assert fastestSASolution != null;

			System.err.printf("Fastest agent was (%d) with %d moves\n", fastestAgent, fastestSASolution.size() - 1);
			// System.err.println(fastestSASolution.get(fastestSASolution.size() - 1));
			System.err.println(fastestSASolution.stream().map(x -> (x.actions != null ? x.actions.toString() : "(None)")).collect(Collectors.joining(" ")));
			System.err.println();

			// Note how much the agent has moved
			actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;

			// Expand SA solution
			while (fastestSASolution.size() < maSolution.size()) {
				MAState lastState = fastestSASolution.get(fastestSASolution.size() - 1);
				List<Command> actions = maSolution.get(lastState.g() + 1).actions;
				fastestSASolution.add(new MAState(lastState, actions));
			}

			maSolution = fastestSASolution;
			System.err.println(maSolution.get(maSolution.size() - 1));

		}
		return maSolution;
	}

	public static List<MAState> maSolveIgnore(MAState initialState) {
		int numAgents = initialState.numAgents;

		List<MAState> maSolution = new ArrayList<>(Collections.singletonList(initialState));
		// System.err.format("actions: %s",initialState.actions);
		// assert false;

		// Keep track of how many actions each agent has already performed
		int[] actionsPerformed = new int[numAgents];
		// All agents have initially performed 0 actions
		Arrays.fill(actionsPerformed, 0);

		Map<Position, Character> goals = new HashMap<>(initialState.goals);

		while (!maSolution.get(maSolution.size() - 1).isGoalState()) {

			
			
			// System.err.printf("Moves: %s\n", Arrays.toString(actionsPerformed));
			
			// System.err.println("Goal order is:");
			for(Position goalPos : orderGoals(initialState)){
				char goalType = initialState.goals.get(goalPos);
				System.err.println(goalPos + " = " + goalType);
			}
				
			for (Position goalPos : orderGoals(initialState)) {
				// System.err.println("initialState.goals = " + initialState.goals);
				char goalType = initialState.goals.get(goalPos);
				if(maSolution.get(maSolution.size()-1).isGoalSatisfied(goalPos))
				continue;
				
				// System.err.format("goalPos at %s\n", goalPos);
				
				// Find single agent-goal pair such that agent fills goal fastest
				List<MAState> fastestSASolution = null;
				int fastestAgent = -1;
				for (Map.Entry<Position, Character> agent : initialState.agents.entrySet()) {
					Position agentPos = agent.getKey();
					char agentType = agent.getValue();

					if (!initialState.color.get(agentType).equals(initialState.color.get(goalType))){
						continue;
					}

					// Skip agent goals for other agents
					if (Character.isDigit(goalType) && goalType != agentType)
						continue;

					int agentId = Character.getNumericValue(agentType);
					String agentColor = initialState.color.get(agentType);

					// XXX Should never be true?
					if (maSolution.get(maSolution.size() - 1).goalCount(agentColor) == 0)
						continue;

					int moves = actionsPerformed[agentId];
					MAState state = maSolution.get(moves).clone();
					// System.err.format("actionsPerformed: %s maSolution.size()= %d\n", Arrays.toString(actionsPerformed), maSolution.size());
					// if(maSolution.size()>5)
					// System.err.format("the second to last actions is %s and the last actions is %s\n",maSolution.get(maSolution.size()-2).actions,maSolution.get(maSolution.size()-1).actions);
					
					// removing goals that are not satisfied but not the goal we are looking for
					Map<Position,Character> temp = state.satisfiedGoals();
					state.goals.clear();
					state.goals.putAll(temp);
					state.goals.put(goalPos,goalType);
					System.err.println("heuristic knows of the goals " + state.goals);
					// System.err.format("agent %s has done %d moves before search start\n",agent,moves);
					List<MAState> saSolution = Agent.searchIgnore(agentType, maSolution,
							new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(state, agentColor,1,goalType)), goalPos, actionsPerformed.clone(),Collections.emptySet());
					// System.err.format("agent %s has solve goal %s at %s with %d moves\n",agent, goalPos, goalType, saSolution.size());
					if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
						fastestSASolution = saSolution;
						fastestAgent = agentId;
					}

				}
				// System.err.format("goal now look like %s and last state looks like \n%s \n",initialState.goals.entrySet(),fastestSASolution.get(fastestSASolution.size()-1));
				
				assert fastestSASolution != null : "could not find solution for goal at " + goalPos;// :)
				
				// System.err.printf("Fastest agent was (%d) with %d moves\n", fastestAgent, fastestSASolution.size() - 1);
				// // System.err.println(fastestSASolution.get(fastestSASolution.size() - 1));
				// System.err.println(fastestSASolution.stream().map(x -> (x.actions != null ? x.actions.toString() : "(None)")).collect(Collectors.joining(" ")));
				// System.err.println();
				
				// Note how much the agent has moved
				//			actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;
				
				// Expand SA solution
				
				while (fastestSASolution.size() < maSolution.size()) {
					MAState lastState = fastestSASolution.get(fastestSASolution.size() - 1);
					List<Command> actions = maSolution.get(lastState.g() + 1).actions;
					fastestSASolution.add(new MAState(lastState, actions));
				}
				actionsPerformed = Agent.planToActions(fastestSASolution);
				maSolution = fastestSASolution;
				System.err.println(maSolution.get(maSolution.size() - 1));
			}


		}
		return maSolution;
	}

	public static List<List<Command>> mergeSolutions(List<List<Command>> solution1, List<List<Command>> solution2) {

		// System.err.println("solution1.size()");
		// System.err.println(solution1.size());
		// System.err.println(solution2.size());

		if (solution2.size() > solution1.size()) {
			List<List<Command>> tmp = solution1;
			solution1 = solution2;
			solution2 = tmp;
		}
		// System.err.println("solution1.size() efter");
		// System.err.println(solution1);
		// System.err.println("solution2.size() efter");
		// System.err.println(solution2);

		List<Command> initial = solution1.get(0);
		List<List<Command>> mergedSolutionStates = new ArrayList<>(Collections.singletonList(initial)); // yolo

		Iterator<List<Command>> it1 = solution1.iterator();
		Iterator<List<Command>> it2 = solution2.iterator();

		it1.next();
		it2.next(); // lul iterate first to remove initial state

		while (it1.hasNext() && it2.hasNext()) {
			List<Command> s1 = it1.next();
			List<Command> s2 = it2.next();
			List<Command> newActions = new ArrayList<>(s1);

			for (int i = 0; i < s2.size(); i++) {
				if (!(s2.get(i) instanceof Command.NoOp))
					newActions.set(i, s2.get(i));
			}

			mergedSolutionStates.add(newActions);
		}

		while (it1.hasNext()) {
			mergedSolutionStates.add(it1.next());
		}

		return mergedSolutionStates;
	}

	public static void main(String[] args) throws IOException {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Identify ourselves
		System.out.println("HoldUd");

		// Test that we can solve a singe-agent level
		MAState initialState = parseLevel(serverMessages);
		System.err.println(initialState);

		Communicator serverComm = new Communicator(serverMessages, System.out);

		// Construct initial MA solution consisting only of the initial state
		// while !isGoalState
		//   Choose single agent-goal pair such that agent fills goal fastest
		//     - Filter out the part of alreadyplanned that happens before the agent can move
		//     - Construct SA initial state with only relevant items
		//     - Later: Solve assignment of all agents to goals
		//   Update MA state
		//     Copy box and agent
		//


		List<MAState> subLevels = splitLevel(initialState);
		System.err.printf("Found %d sub-levels.\n", subLevels.size());

		List<List<Command>> maSolution = new ArrayList<>(Collections.singletonList(Collections.nCopies(initialState.numAgents, new Command.NoOp())));

		for (MAState subLevel : subLevels) {
			System.err.println(subLevel);
			List<MAState> subSolution;

			if (subLevel.isSAState()){
				subSolution = maSolveIgnore(subLevel);
				// Character agentType = subLevel.agents.values().iterator().next();
				// String agentColor = initialState.color.get(agentType);
				// Strategy.StrategyBestFirst strategy = new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(subLevel,agentColor,10));
				// subSolution = Agent.saSearch(subLevel, strategy);
			}
			else {
				subSolution = maSolveIgnore(subLevel);
			}
			System.err.println(maSolution);
			// System.err.println("noget tekst");
			System.err.println(subSolution);
			assert subSolution != null;

			List<List<Command>> solutionActions = subSolution.stream().map(x -> x.actions).collect(Collectors.toList());
			maSolution = mergeSolutions(maSolution, solutionActions);
			// System.err.println("subSolution");
			// System.err.println(solutionActions);
		}

		MAState state = initialState;
        for (List<Command> actions : maSolution.subList(1, maSolution.size())) {
            if (state.isGoalState()) break;
            
            List<Boolean> res = serverComm.send(actions);
            for (Boolean ok : res) {
                if (!ok) {
                    System.err.printf("Illegal move: %s", actions);
                    break;
                }
            }
            
            state = new MAState(state, actions);
        }

	}
}
