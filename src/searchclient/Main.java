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

		return initialState;
	}

	public static void main(String[] args) throws IOException {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Identify ourselves
		System.out.println("Meme-o-tron 2000");

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

		int numAgents = initialState.agents.size();

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
			for (Map.Entry<Position, Character> goal : initialState.goals.entrySet()) {
				Position goalPos = goal.getKey();
				Character goalType = goal.getValue();
				String goalColor = initialState.color.get(goalType);

				if (maSolution.get(maSolution.size() - 1).isGoalSatisfied(goalPos)) {
					continue;
				}
				else {
					System.err.printf(maSolution.get(maSolution.size() - 1).toString());
				}

				for (Map.Entry<Position, Character> agent : initialState.agents.entrySet()) {
					Position agentPos = agent.getKey();
					char agentType = agent.getValue();
					int agentId = Character.getNumericValue(agentType);
					String agentColor = initialState.color.get(agentType);

					if (!agentColor.equals(goalColor))
						continue;

					int moves = actionsPerformed[agentId];
					MAState state = maSolution.get(moves);

					if (moves > 1){
					 for (MAState i : maSolution.subList(moves, maSolution.size()) ) {
						 System.err.printf(i.actions.toString());
					}}

					ArrayList<MAState> saSolution = Agent.search(agentType, goalPos, maSolution.subList(moves, maSolution.size()),
						new Strategy.StrategyBestFirst(new Heuristic.AStar(state)));

					if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
						fastestSASolution = saSolution;
						fastestAgent = agentId;
					}
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

		}

		for (MAState state : maSolution) {
			if (!state.isInitialState()) {
				List<Boolean> res = serverComm.send(state.actions);
				for (Boolean ok : res) {
					if (!ok) {
						System.err.println("Illegal move!");
						System.err.println(state.parent);
						System.err.println(state);
						break;
					}
				}
			}
		}
	}
}
