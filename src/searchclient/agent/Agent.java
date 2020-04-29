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

		System.err.format("Search starting (%c) with %d goals using strategy %s.\n", agent, origGoalCount,
				strategy.toString());

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));
				System.err.println("Search failed :(");

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 10_000 == 0)
				System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
						Memory.stringRep()));

			if (!leafState.isInitialState() && leafState.agentAchievedGoal(agent)) {
				MAState endState = leafState;

				boolean isApplicable = true;
				for (MAState state : alreadyPlanned.subList(Math.min(deltaG + 1, alreadyPlanned.size()),
						alreadyPlanned.size())) {
					if (!endState.isApplicable(state.actions)) {
						isApplicable = false;
						break;
					}
					endState = new MAState(endState, state.actions);
				}

				if (isApplicable && endState.goalCount() < origGoalCount) {
					ArrayList<MAState> plan = leafState.extractPlanWithInitial();

					System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
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

			for (MAState n : leafState.getExpandedStates(agent, state)) { // The list of expanded states is shuffled
																			// randomly; see State.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static List<MAState> searchIgnore(char agent, List<MAState> alreadyPlanned, Strategy strategy,
			Position goalPos, int[] actionsPerformed) {
		int agentId = Character.getNumericValue(agent);
		int moves = actionsPerformed[agentId];
		MAState initialState = alreadyPlanned.get(moves);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - (1 + moves)).goalCount();

		System.err.format("goalPos at (%d, %d).\n", goalPos.getCol(), goalPos.getRow());
		System.err.format("Search starting (%c) with %d goals using strategy %s.\n", agent, origGoalCount,
				strategy.toString());

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));
				System.err.println("Search failed :(");

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 10_000 == 0)
				System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
						Memory.stringRep()));

			if (!leafState.isInitialState() && leafState.isGoalSatisfied(goalPos)) {
				System.err.println("leafState.path");
				System.err.println(leafState.path);
				System.err.println("goalPos");
				System.err.println(goalPos);
				System.err.println("leafState");
				System.err.println(leafState);
				System.err.println("initialState");
				System.err.println(initialState);
				// System.err.format("LeafState.path is of size %d",leafState.path.size());
				if (leafState.path.size() > 0)
					leafState.path.remove(leafState.path.size() - 1);

				List<MAState> extractedPlans = leafState.extractPlanWithInitial();
				List<MAState> shortExtractedPlans = extractedPlans.subList(Math.max(1,actionsPerformed[agentId]), extractedPlans.size()-1);
				List<MAState> helperPlan = alreadyPlanned;
				boolean notDone = true;
				while (notDone) {

					MAState freshInitialState = new MAState(initialState,
							Collections.nCopies(leafState.numAgents, new Command.NoOp()));
					freshInitialState.path = leafState.path;
					for (MAState state : shortExtractedPlans) {
						System.err.format("applying actions %s to freshInitialState from extractedPlans\n", state.actions);
						freshInitialState = new MAState(freshInitialState, state.actions,true);
						System.err.println(freshInitialState);
					}

					for (MAState state : helperPlan.subList(1, helperPlan.size())) {
						System.err.format("applying actions %s to freshInitialState from helperPlan \n", state.actions);
						freshInitialState = new MAState(freshInitialState, state.actions);
						System.err.println(freshInitialState);
					}


					System.err.format("freshInitialState looks like: \n%s \n",freshInitialState);

					// assert false;

					List<Position> objectPositions = new ArrayList<>();
					for (Position pos : new HashSet<>(leafState.path)) {
						if (freshInitialState.boxAt(pos) && !pos.equals(goalPos)|| freshInitialState.agentAt(pos) && !freshInitialState.agents.get(pos).equals(agent)) {
							objectPositions.add(pos);
						}
					}

					// Nothing needs to move
					if (objectPositions.size() < 1) {
						return leafState.extractPlanWithInitial();
					}


					Map<Position, Character> fakeGoals = Agent.moveObjects(objectPositions, freshInitialState);

					System.err.format("objectPositions is %s\n", objectPositions);
					System.err.format("fakegoals is : %s\n", fakeGoals.toString());
					// actionsPerformed[agentId] = extractedPlans.size() - 1;

					List<MAState> fastestSASolution = null;
					int fastestAgent = -1;

					for (Map.Entry<Position, Character> fakeGoal : fakeGoals.entrySet()) {
						Position fakeGoalPos = fakeGoal.getKey();
						Character fakeGoalType = fakeGoal.getValue();

						// Find single agent-goal pair such that agent fills goal fastest
						for (Map.Entry<Position, Character> helperAgent : initialState.agents.entrySet()) {
							Position agentPos = helperAgent.getKey();
							char agentType = helperAgent.getValue();

							if (!initialState.color.get(agentType).equals(initialState.color.get(fakeGoalType))) {
								continue;
							}

							int helperAgentId = Character.getNumericValue(agentType);
							String agentColor = initialState.color.get(agentType);

							int helperMoves = actionsPerformed[helperAgentId];
							System.err.format("Path is %s, fakeGoal is %s and helperAgent is %s\n", leafState.path,fakeGoal,helperAgent);
							System.err.format("actionsPerformed is %s\n", Arrays.toString(actionsPerformed));
							MAState state = helperPlan.get(helperMoves);
							state.goals.put(fakeGoalPos, fakeGoalType);

							// ArrayList<MAState> saSolution = Agent.search(agentType,
							// helperPlan.subList(helperMoves, helperPlan.size()),
							// new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)));
							List<MAState> saSolution = Agent.searchIgnore(agentType, helperPlan,
									new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)), fakeGoalPos,
									actionsPerformed);

							if (fastestSASolution == null
									|| (saSolution != null && saSolution.size() < fastestSASolution.size())) {
								fastestSASolution = saSolution;
								fastestAgent = helperAgentId;
							}
						}

						assert fastestSASolution != null; // one layer deep quick fiks
						actionsPerformed = Agent.planToActions(fastestSASolution);

						// System.err.format("helperPlan is of size %d", helperPlan.size());
						// System.err.format("fastestSASolution is of size %d", fastestSASolution.size());
						// Agent.solveConflicts(fastestSASolution, extractedPlans);

						helperPlan = Agent.extendSolution(helperPlan, fastestSASolution);

						// System.err.format("combining helperPlan of size %d with fasterSASolution of size %d\n",helperPlan.size(),fastestSASolution.size());
						// if(helperPlan.size()<3){
						// 	System.err.println("helperPlan was replaced by fasterSASolution");
						// 	helperPlan = fastestSASolution;
						// }
						// else{
						// 	System.err.println("helperPlan and fasterSASolution was merged");
						// 	System.err.format("actionsPerformed for helperPlan %s\n",Arrays.toString(planToActions(helperPlan)));
						// 	System.err.format("actionsPerformed for fastestSASolution %s\n",Arrays.toString(planToActions(fastestSASolution)));
						// 	System.err.format("actions for fastestSASolution.get(last) %s\n",fastestSASolution.get(fastestSASolution.size()-1).actions);
						// 	for(MAState state : helperPlan)
						// 		System.err.format("%s",state.actions);
						// 	helperPlan = Agent.solveConflicts(helperPlan, fastestSASolution);
						// }
						// for (int i = 1; i < fastestSASolution.size(); i++) {
						// 	MAState fastestSolution = fastestSASolution.get(i);
						// 	MAState exPlan = extractedPlans.get(i);
						// 	MAState prevPlan = extractedPlans.get(i - 1);

						// 	List<Command> exActions = exPlan.actions;
						// 	exActions.set(fastestAgent, fastestSolution.actions.get(fastestAgent));
						// 	extractedPlans.set(i, new MAState(prevPlan, exActions));
						// }
					}

					

						// actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;
				}
				extractedPlans = Agent.extendSolution(helperPlan,extractedPlans);
				System.err.println("extractedPlans");
				System.err.println(extractedPlans);
				return extractedPlans;
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


	public static int[] planToActions(List<MAState> extractedPlan) {
		int numAgents = extractedPlan.get(0).numAgents;
		int[] actionsPerformed = new int[numAgents];

		Iterator<MAState> planIterator = extractedPlan.iterator();

		for (int i = 1; i < extractedPlan.size(); i++) {
			List<Command> actions = extractedPlan.get(i).actions;

			for (int j = 0; j < numAgents; j++) {
				if (!(actions.get(j) instanceof Command.NoOp))
					actionsPerformed[j] = i;
			}
		}

		return actionsPerformed;
	}

	public static Map<Position, Character> moveObjects(List<Position> objectPositions, MAState state) {
		HashMap<Position, Character> fakeGoals = new HashMap<>();
		List<Position> path = state.path;
		System.err.format("The path is %s\n",path);

		// Flood-fill. Update shortest distances from each goal in turn using BFS
		for (Position objectPosition : objectPositions) {
			ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(objectPosition));
			Set<Position> alreadyVisited = new HashSet<>();

			System.err.println(path);

			while (!frontier.isEmpty()) {
				Position p = frontier.pop();

				int row = p.getRow(), col = p.getCol();

				if (p.within(0, 0, state.height - 1, state.width - 1) && !state.walls.contains(p)
						&& !alreadyVisited.contains(p)) {

					if (!path.contains(p)) {

						if (state.boxAt(p)){
							MAState newState = new MAState(state, Collections.nCopies(state.numAgents, new Command.NoOp()), true);
							newState.path.add(p);

							Map<Position, Character> tmpGoals = Agent.moveObjects(Collections.singletonList(p),
									newState);
							fakeGoals.putAll(tmpGoals);
						}else if(fakeGoals.containsKey(p)) {
							fakeGoals.remove(p);

							MAState newState = new MAState(state, Collections.nCopies(state.numAgents, new Command.NoOp()), true);
							newState.path.add(p);

							Map<Position, Character> tmpGoals = Agent.moveObjects(Collections.singletonList(p),
									newState);
							fakeGoals.putAll(tmpGoals);
						}

						System.err.println("Here comes dat p & boxPosition");
						System.err.println(p);
						System.err.println(objectPosition);
						System.err.println(state);
						if (state.boxAt(objectPosition)) {
							fakeGoals.put(p, state.boxes.get(objectPosition));

							System.err.println("found a object to be a box at");
							System.err.println(objectPosition);
							System.err.println(state.boxes.get(objectPosition));
						}
						if (state.agentAt(objectPosition)) {
							fakeGoals.put(p, state.agents.get(objectPosition));

							System.err.println("found a object to be a agent at");
							System.err.println(objectPosition);
							System.err.println(state.agents.get(objectPosition));
						}
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
				System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			if (iterations % 1000 == 0)
				System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
						Memory.stringRep()));

			if (leafState.isGoalState()) {
				System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
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
			for (MAState n : leafState.getExpandedStates(agent, nextState)) { // The list of expanded states is shuffled
																				// randomly; see State.java.
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
				}
			}
			iterations++;
		}
	}

	public static ArrayList<MAState> extendSolution(List<MAState> alreadyPlanned, List<MAState> newPlan) {

		int numAgents = alreadyPlanned.get(0).numAgents;

		// adding a state to the end of newPlan with all NoOp actions
		MAState last = newPlan.get(newPlan.size()-1);
		newPlan.add(new MAState(last,Collections.nCopies(numAgents, new Command.NoOp())));

		int[] oldActionsPlanned = Agent.planToActions(alreadyPlanned);
		int[] newActionsPlanned = Agent.planToActions(newPlan);

		int[] newActionsPerformed = oldActionsPlanned;

		ArrayList<MAState> mergedPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));


		for(int i = 1;i<alreadyPlanned.size()-1;i++){

			MAState oldState = alreadyPlanned.get(i);
			List<Command> s1 = oldState.actions;
			List<Command> newActions = new ArrayList<>(s1);
			// System.err.format("merged %s with ",s1);

			for(int j = 0;j<numAgents;j++){

				if(newActionsPerformed[j]<i){
					List<Command> testActions = new ArrayList<>(s1);
					Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);
					// System.err.format("%s ",currentAction);
					if (!(currentAction instanceof Command.NoOp)){
						
						testActions.set(j,currentAction);
						if(oldState.isApplicable(testActions)){
							newActions.set(j,currentAction);
							newActionsPerformed[j]++;
						}
					}
				}
			}
			// System.err.format("to %s\n",newActions);
			MAState prevState = oldState.parent;
			// System.err.format("Trying to do %s in \n%s",newActions,prevState);
			mergedPlan.add(new MAState(prevState,newActions));

		}

		while(!Arrays.equals(newActionsPerformed,newActionsPlanned)){

			List<Command> newActions = new ArrayList<>(Collections.nCopies(numAgents, new Command.NoOp()));

			for(int j = 0;j<numAgents;j++){

				Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);

				if (!(currentAction instanceof Command.NoOp)){
					newActions.set(j,currentAction);
					newActionsPerformed[j]++;
				}
			}
			MAState prevState = mergedPlan.get(mergedPlan.size()-1).parent;
			mergedPlan.add(new MAState(prevState,newActions));

		}

		return mergedPlan;
	

	}


	public static ArrayList<MAState> solveConflicts(List<MAState> alreadyPlanned, List<MAState> newPlan) {

		int numAgents = alreadyPlanned.get(0).numAgents;

		// adding a state to the end of newPlan with all NoOp actions
		MAState last = newPlan.get(newPlan.size()-1);
		newPlan.add(new MAState(last,Collections.nCopies(numAgents, new Command.NoOp())));

		int[] newActionsPlanned = Agent.planToActions(newPlan);

		int[] newActionsPerformed = new int[numAgents];

		ArrayList<MAState> mergedPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));


		for(MAState oldState : alreadyPlanned.subList(1, alreadyPlanned.size()-1)){

			List<Command> s1 = oldState.actions;
			List<Command> newActions = new ArrayList<>(s1);
			System.err.format("merged %s with ",s1);

			for(int j = 0;j<numAgents;j++){

				List<Command> testActions = new ArrayList<>(s1);
				Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);
				System.err.format("%s ",currentAction);
				if (!(currentAction instanceof Command.NoOp)){

					testActions.set(j,currentAction);
					if(oldState.isApplicable(testActions)){
						newActions.set(j,currentAction);
						newActionsPerformed[j]++;
					}
				}
			}
			System.err.format("to %s\n",newActions);
			MAState prevState = oldState.parent;
			System.err.format("Trying to do %s in \n%s",newActions,prevState);
			mergedPlan.add(new MAState(prevState,newActions));

		}

		while(!Arrays.equals(newActionsPerformed,newActionsPlanned)){

			List<Command> newActions = new ArrayList<>(Collections.nCopies(numAgents, new Command.NoOp()));

			for(int j = 0;j<numAgents;j++){

				Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);

				if (!(currentAction instanceof Command.NoOp)){
					newActions.set(j,currentAction);
					newActionsPerformed[j]++;
				}
			}
			MAState prevState = mergedPlan.get(mergedPlan.size()-1).parent;
			mergedPlan.add(new MAState(prevState,newActions));

		}

		return mergedPlan;
	

	}
}
