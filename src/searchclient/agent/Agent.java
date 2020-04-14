package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Position;
import searchclient.util.Memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Agent {

	public static ArrayList<MAState> search(char agent, Position goalPos, List<MAState> alreadyPlanned, Strategy strategy) {
		System.err.format("Search starting with strategy %s.\n", strategy.toString());

		MAState initialState = alreadyPlanned.get(0);
		strategy.addToFrontier(initialState);

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t",
					strategy.searchStatus(),
					Memory.stringRep()));

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 1000 == 0)
				System.err.println(String.join("\t",
					strategy.searchStatus(),
					strategy.describeState(leafState),
					Memory.stringRep()));

			if (leafState.isGoalSatisfied(goalPos)) {
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
			boolean insideList = (deltaG + 1) < (alreadyPlanned.size());
			int numAgents = leafState.agents.size();

			// TODO: Dynamically add NoOp states to fill alreadyPlanned enough to just index
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

}
