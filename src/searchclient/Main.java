package searchclient;

import searchclient.agent.Agent;
import searchclient.agent.Heuristic;
import searchclient.agent.SAState;
import searchclient.agent.Strategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

	public static SAState parseLevel(BufferedReader serverMessages) throws IOException {

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

		System.out.printf("Level is %dx%d\n", width, height);
		SAState initialState = new SAState(width, height, domain);

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
		SAState initialState = parseLevel(serverMessages);
		System.err.println(initialState);

		Communicator serverComm = new Communicator(serverMessages, System.out);

		// Construct initial MA state with only the walls of initial state.
		// while !isGoalState
		//   Choose single agent-goal pair such that agent fills goal fastest
		//     - Filter out the part of alreadyplanned that happens before the agent can move
		//     - Construct SA initial state with only relevant items
		//     - Later: Solve assignment of all agents to goals
		//   Update MA state
		//     Copy box and agent
		//



		// Goal -> Agent -> "time"
		Map<Position, Map<Character, List<SAState>>> solutions = new HashMap<>();

		for (Map.Entry<Position, Character> goal : initialState.goals.entrySet()) {
			Position goalPos = goal.getKey();
			Character goalType = goal.getValue();

			HashMap<Character, List<SAState>> currentSolutionMap = new HashMap<>();
			solutions.put(goalPos, currentSolutionMap);

			TreeMap<Position, Character> relevantBoxes = new TreeMap<>();
			Set<Position> newWalls = new HashSet<>(initialState.walls);

			for (Map.Entry<Position, Character> box : initialState.boxes.entrySet()) {
				Position boxPos = box.getKey();
				Character boxType = box.getValue();

				if (goalType.equals(boxType)) {
					relevantBoxes.put(boxPos, boxType);
				} else {
					newWalls.add(boxPos);
				}
			}

			// Wallify all agents
			newWalls.addAll(initialState.agents.keySet());

			for (Map.Entry<Position, Character> agent : initialState.agents.entrySet()) {
				Position agentPos = agent.getKey();
				Character agentType = agent.getValue();

				// Only agents that match the color of the boxes for the goal
				if (!initialState.color.get(agentType).equals(initialState.color.get(goalType)))
					continue;

				ArrayList<SAState> solution = Agent.search(agentType, initialState, null, new Strategy.StrategyBestFirst(new Heuristic.AStar(initialState)));
				// System.err.printf("Agent: %c -> %d (%s)\n", agentType, solution.size(), solution.stream().map(s -> s.action.toString()).collect(Collectors.toList()));

				currentSolutionMap.put(agentType, solution);
			}
		}

		// Find fastest agent for each goal and execute sequentially
		// Strong (and wrong) assumption that agents only win one goal!
		for (Map.Entry<Position, Map<Character, List<SAState>>> entry : solutions.entrySet()) {
			Position goalPos = entry.getKey();
			Map<Character, List<SAState>> agentSolutions = entry.getValue();
			Map.Entry<Character, List<SAState>> fastest = Collections.min(agentSolutions.entrySet(), Comparator.comparing(x -> x.getValue().size()));

			int fastestAgent = Character.getNumericValue(fastest.getKey());
			int numAgents = initialState.agents.size();

			for (SAState state : fastest.getValue()) {
				List<Command> jointAction = new ArrayList<>(Collections.nCopies(numAgents, new Command.NoOp()));
				jointAction.set(fastestAgent, state.action);

				System.err.println(jointAction + " => " + serverComm.send(jointAction));
			}
		}
	}
}
