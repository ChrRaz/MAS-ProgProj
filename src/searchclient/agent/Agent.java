package searchclient.agent;

import searchclient.util.Memory;

import java.util.ArrayList;

public class Agent {

	public static ArrayList<State> search(State initialState, Strategy strategy) {
		System.err.format("Search starting with strategy %s.\n", strategy.toString());
		strategy.addToFrontier(initialState);

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t",
					strategy.searchStatus(),
					Memory.stringRep()));

				return null;
			}

			State leafState = strategy.getAndRemoveLeaf();

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

				return leafState.extractPlan();
			}

			strategy.addToExplored(leafState);
			for (State n : leafState.getExpandedStates()) { // The list of expanded states is shuffled randomly; see State.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

}
