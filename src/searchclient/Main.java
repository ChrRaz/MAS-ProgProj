package searchclient;

import searchclient.agent.Agent;
import searchclient.agent.Heuristic;
import searchclient.agent.Strategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

	public static State parseLevel(BufferedReader serverMessages) throws IOException {

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
		State initialState = new State(width, height, domain);

		for (int i = 0; i < lines.size(); i++) {
			// Make sure that input is rectangular
			lines.set(i, String.format("%-" + width + "s", lines.get(i)));
		}

		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				char chr = lines.get(row).charAt(col);

				if (chr == '+') { // Wall.
					initialState.walls[row][col] = true;
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
					assert initialState.walls[row][col];
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

		return initialState;
	}

	public static void main(String[] args) throws IOException {
		BufferedReader serverMessages = new BufferedReader(new InputStreamReader(System.in));

		// Identify ourselves
		System.out.println("Meme-o-tron 2000");

		// Test that we can solve a singe-agent level
		State initialState = parseLevel(serverMessages);
		System.err.println(initialState);

		if (initialState.agents.size() != 1) {
			System.err.println("Lol single agent only");
			System.exit(1);
		}

		Position agentPos = initialState.agents.keySet().iterator().next();

		searchclient.agent.State saState = new searchclient.agent.State(initialState.width, initialState.height, agentPos, initialState.domain);
		saState.walls = initialState.walls;
		saState.boxes.putAll(initialState.boxes);
		saState.goals.putAll(initialState.goals);


		ArrayList<searchclient.agent.State> solution = Agent.Search(saState, new Strategy.StrategyBestFirst(new Heuristic.AStar(saState)));
		if (solution != null) {
			for (searchclient.agent.State state : solution) {
				System.out.println(state.action);
			}
		} else {
			System.err.println("damn :/");
		}
	}
}
