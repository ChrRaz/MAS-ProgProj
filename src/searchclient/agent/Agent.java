package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Main;
import searchclient.Position;
import searchclient.util.Memory;

import javax.print.CancelablePrintJob;
import java.util.*;
import java.util.stream.Collectors;

public class Agent {

	public static ArrayList<MAState> search(char agent, List<MAState> alreadyPlanned, Strategy strategy) {
		MAState initialState = alreadyPlanned.get(0);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - 1).goalCount();

		System.err.format("Search starting (%c) with %d goals using strategy %s.\n", agent, origGoalCount, strategy.toString());

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t",
					strategy.searchStatus(),
					Memory.stringRep()));
				System.err.println("Search failed :(");

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 10_000 == 0)
				System.err.println(String.join("\t",
					strategy.searchStatus(),
					strategy.describeState(leafState),
					Memory.stringRep()));

			if (!leafState.isInitialState() && leafState.agentAchievedGoal(agent)) {
				MAState endState = leafState;

				boolean isApplicable = true;
				for (MAState state : alreadyPlanned.subList(Math.min(deltaG + 1, alreadyPlanned.size()), alreadyPlanned.size())) {
					if (!endState.isApplicable(state.actions)) {
						isApplicable = false;
						break;
					}
					endState = new MAState(endState, state.actions);
				}

				if (isApplicable && endState.goalCount() < origGoalCount) {
					ArrayList<MAState> plan = leafState.extractPlanWithInitial();

					System.err.println(String.join("\t",
						strategy.searchStatus(),
						strategy.describeState(leafState),
						Memory.stringRep()));
					System.err.printf("Found solution of length %d\n", plan.size() - 1);

					return plan;
				}
			}

			// Pick out state based on leafState.g() and alreadyPlanned list.
			// If index out of bounds just get last state as nothing will change yet.

			strategy.addToExplored(leafState);

			// Get nextState from alreadyPlanned
			// vs construct nextState from leafState and move from alreadyPlanned.

			// Need to identify agents by character rather than position
			// as position changes across states.
			boolean insideList = (deltaG + 1) < (alreadyPlanned.size());
			int numAgents = leafState.numAgents;

			MAState state;
			if (insideList) {
				List<Command> actions = alreadyPlanned.get(deltaG + 1).actions;
				if (!leafState.isApplicable(actions))
					continue;

				state = new MAState(leafState, actions);
			} else {
				state = new MAState(leafState, Collections.nCopies(numAgents, new Command.NoOp()));
			}

			for (MAState n : leafState.getExpandedStates(agent, state)) { // The list of expanded states is shuffled randomly; see State.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}


	public static List<Objects> searchIgnore(char agent, List<MAState> alreadyPlanned, Strategy strategy, Position goalPos, int[] actionsPerformed) {
		int agentId = Character.getNumericValue(agent);
		int moves = actionsPerformed[agentId];
		MAState initialState = alreadyPlanned.get(moves);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - (1 + moves)).goalCount();

		System.err.format("Search starting (%c) with %d goals using strategy %s.\n", agent, origGoalCount, strategy.toString());

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t",
						strategy.searchStatus(),
						Memory.stringRep()));
				System.err.println("Search failed :(");

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 10_000 == 0)
				System.err.println(String.join("\t",
						strategy.searchStatus(),
						strategy.describeState(leafState),
						Memory.stringRep()));

			if (!leafState.isInitialState() && leafState.isGoalSatisfied(goalPos)) {
				List<Position> boxPositions = new ArrayList<>();

				for (Position pos : leafState.path) {
					if (leafState.boxAt(pos)){
						boxPositions.add(pos);
					}
				}

				if (boxPositions.size() < 1) {
					return leafState.extractPlanWithInitial();
				}

				ArrayList<MAState> extractedPlans = leafState.extractPlanWithInitial();
				Map<Position, Character> fakeGoals = Agent.moveBoxes(boxPositions, leafState);

				actionsPerformed[agentId] = extractedPlans.size() - 1;

				List<MAState> fastestSASolution = null;
				int fastestAgent = -1;

				for (Map.Entry<Position, Character> fakeGoal : fakeGoals.entrySet()) {
					Position fakeGoalPos = fakeGoal.getKey();
					Character fakeGoalType = fakeGoal.getValue();

					// Find single agent-goal pair such that agent fills goal fastest
					for (Map.Entry<Position, Character> helperAgent : initialState.agents.entrySet()) {
						Position agentPos = helperAgent.getKey();
						char agentType = helperAgent.getValue();

						if (initialState.color.get(agentType).equals(initialState.color.get(fakeGoalType))) {
							continue;
						}

						int helperAgentId = Character.getNumericValue(agentType);
						String agentColor = initialState.color.get(agentType);

						int helperMoves = actionsPerformed[helperAgentId];
						MAState state = alreadyPlanned.get(helperMoves);
						state.goals.put(fakeGoalPos, fakeGoalType);

						ArrayList<MAState> saSolution = Agent.searchIgnore(agentType, alreadyPlanned,
								new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)), fakeGoalPos, actionsPerformed);

						if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
							fastestSASolution = saSolution;
							fastestAgent = helperAgentId;
						}
					}

					assert fastestSASolution != null; // one layer deep quick fiks

					for (int i = 1; i < fastestSASolution.size(); i++) {
						MAState fastestSolution = fastestSASolution.get(i);
						MAState exPlan = extractedPlans.get(i);
						MAState prevPlan = extractedPlans.get(i - 1);

						List<Command> exActions = exPlan.actions;
						exActions.set(fastestAgent, fastestSolution.actions.get(fastestAgent));
						extractedPlans.set(i, new MAState(prevPlan, exActions));
					}

					actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;
				}
				return Arrays.asList(extractedPlans, actionsPerformed);
			}

			strategy.addToExplored(leafState);

			for (MAState n : leafState.getExpandedStatesIgnore(agent)) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}


	public static Map<Position, Character> moveBoxes(List<Position> boxPositions, MAState state) {
		HashMap<Position, Character> fakeGoals = new HashMap<>();
		Set<Position> path = state.path;

		// Flood-fill. Update shortest distances from each goal in turn using BFS
		for (Position boxPosition : boxPositions) {
			ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(boxPosition));
			Set<Position> alreadyVisited = new HashSet<>();

			while (!frontier.isEmpty()) {
				Position p = frontier.pop();

				int row = p.getRow(), col = p.getCol();

				if (p.within(0, 0, state.height - 1, state.width - 1) &&
						!state.walls.contains(p) && !alreadyVisited.contains(p)) {

					if (!path.contains(p)) {

						if (fakeGoals.containsKey(p)){
							fakeGoals.remove(p);

							MAState newState = new MAState(state);
							newState.path.add(p);

							Map<Position, Character> tmpGoals = Agent.moveBoxes(Collections.singletonList(p), newState);
							fakeGoals.putAll(tmpGoals);
						}

						fakeGoals.put(p, state.boxes.get(boxPosition));
						break;
					}

					alreadyVisited.add(p);

					for (Command.Dir dir : Command.Dir.values()) {
						frontier.add(p.add(dir));
					}
				}
			}
		}
		return fakeGoals;
	}


	public static ArrayList<MAState> saSearch(MAState initialState, Strategy strategy) {
		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		strategy.addToFrontier(initialState);

		Character agent = initialState.agents.values().iterator().next();

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t",
						strategy.searchStatus(),
						Memory.stringRep()));

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			if (iterations % 1000 == 0)
				System.err.println(String.join("\t",
						strategy.searchStatus(),
						strategy.describeState(leafState),
						Memory.stringRep()));

			if (leafState.isGoalState()) {
				System.err.println(String.join("\t",
						strategy.searchStatus(),
						strategy.describeState(leafState),
						Memory.stringRep()));

				return leafState.extractPlanWithInitial();
			}

			// Pick out state based on leafState.g() and alreadyPlanned list.
			// If index out of bounds just get last state as nothing will change yet.

			strategy.addToExplored(leafState);

			// Get nextState from alreadyPlanned
			// vs construct nextState from leafState and move from alreadyPlanned.

			// Need to identify agents by character rather than position
			// as position changes across states.

			// TODO: Dynamically add NoOp states to fill alreadyPlanned enough to just index

			MAState nextState = new MAState(leafState, Collections.nCopies(initialState.numAgents, new Command.NoOp()));
			for (MAState n : leafState.getExpandedStates(agent, nextState)) { // The list of expanded states is shuffled randomly; see State.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

}
