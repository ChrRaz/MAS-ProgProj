package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.util.Memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
