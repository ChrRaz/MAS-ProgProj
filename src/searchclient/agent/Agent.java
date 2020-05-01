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
			Position goalPos, int[] actionsPerformed, Set<Position> oldPath) {
		int agentId = Character.getNumericValue(agent);
		int moves = actionsPerformed[agentId];
		MAState initialState = alreadyPlanned.get(moves);
		// initialState.path.addAll(path);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - (1 + moves)).goalCount();
		System.err.format("agent %s has moved %d\n",agent,moves);
		System.err.format("goalPos at (%d, %d).\n", goalPos.getCol(), goalPos.getRow());
		System.err.format("goals: %s \n",initialState.goals.entrySet());
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
			// System.err.format("leafState.g = %d and is \n%s \n",leafState.g(),leafState);

			int deltaG = leafState.g() - initialState.g();

			if (iterations % 10_000 == 0)
				System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
						Memory.stringRep()));

			Position agentPos = leafState.getPositionOfAgent(agent);

			if (leafState.isGoalSatisfied(goalPos) && !oldPath.contains(agentPos)
					&& !leafState.boxes.containsKey(agentPos)) {
				System.err.println("leafState.path");
				System.err.println(leafState.path);
				System.err.println("goalPos");
				System.err.println(goalPos);
				System.err.println("leafState");
				System.err.println(leafState);
				System.err.println("initialState");
				System.err.println(initialState);
				System.err.format("found goal after %d states\n",leafState.g());
				// System.err.format("LeafState.path is of size %d",leafState.path.size());
				// if (leafState.path.size() > 0)
				// leafState.path.remove(leafState.path.size() - 1);
				
				List<MAState> extractedPlans = leafState.extractPlanWithInitial();
				List<MAState> shortExtractedPlans = extractedPlans.subList(Math.max(1, actionsPerformed[agentId]+1),
				extractedPlans.size());

				for(MAState state : extractedPlans){
					System.err.println(strategy.describeState(state));
					System.err.println(state);
				}

				int max = 0;
				for( int i = 0; i< actionsPerformed.length;i++){
					max = Math.max(actionsPerformed[i],max);
				}
				List<MAState> helperPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));
				if(moves!=max){
					System.err.format("moves %d was not max %d\n",moves,max);
					helperPlan = alreadyPlanned.subList(moves, alreadyPlanned.size());
				}

				// System.err.format("helperplan is of length %d\n",helperPlan.size()-1);
				for(int i = 0; i < helperPlan.size();i++){

					MAState state = helperPlan.get(i);
					System.err.format("The %d action of helperPlan is %s applyed to \n%s \n",i,state.actions,state.parent);
				}

				boolean notDone = true;
				while (notDone) {

					MAState freshInitialState = new MAState(initialState,
							Collections.nCopies(leafState.numAgents, new Command.NoOp()));
					freshInitialState.path = leafState.path;

					Set<Position> objectPositions = new HashSet<>();

					for (MAState state : helperPlan) {
						if(state.actions==null)
							continue;
						System.err.format("applying actions %s to freshInitialState from helperPlan \n", state.actions);
						freshInitialState = new MAState(freshInitialState, state.actions);
						System.err.println(freshInitialState);
					}

					objectPositions = Agent.lookAhead(shortExtractedPlans, freshInitialState);

					
					System.err.format("obejctPositions is %s \n", objectPositions);

					// assert false;

					// for (Position pos : new HashSet<>(leafState.path)) {
					// 	if (freshInitialState.boxAt(pos) && !pos.equals(goalPos)
					// 			|| freshInitialState.agentAt(pos) && !freshInitialState.agents.get(pos).equals(agent)) {
					// 		objectPositions.add(pos);
					// 	}
					// }

					// If nothing needs to move we are done
					if (objectPositions.size() < 1) {
						return Agent.solveConflicts(helperPlan, extractedPlans);
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
							// Position agentPos = helperAgent.getKey();
							char agentType = helperAgent.getValue();

							if (!initialState.color.get(agentType).equals(initialState.color.get(fakeGoalType))) {
								continue;
							}

							int helperAgentId = Character.getNumericValue(agentType);
							String agentColor = initialState.color.get(agentType);

							int helperMoves = actionsPerformed[helperAgentId];

							System.err.format("Path is %s, fakeGoal is %s and helperAgent is %s\n", leafState.path,
									fakeGoal, helperAgent);
							System.err.format("actionsPerformed is %s\n", Arrays.toString(actionsPerformed));
							MAState state = null;
							
							state = helperPlan.get(helperMoves);
							state.goals.put(fakeGoalPos, fakeGoalType);
							
							// assert false;
							// ArrayList<MAState> saSolution = Agent.search(agentType,
							// helperPlan.subList(helperMoves, helperPlan.size()),
							// new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)));

							List<MAState> saSolution = Agent.searchIgnore(agentType, helperPlan,
									new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)), fakeGoalPos,
									actionsPerformed, leafState.path);

							// state.goals.remove(fakeGoalPos);

							if (fastestSASolution == null
									|| (saSolution != null && saSolution.size() < fastestSASolution.size())) {
								fastestSASolution = saSolution;
								fastestAgent = helperAgentId;
							}
						}

						assert fastestSASolution != null; // one layer deep quick fiks
						System.err.format("Replacing %s with ", Arrays.toString(actionsPerformed));
						actionsPerformed = Agent.planToActions(fastestSASolution);
						System.err.format("%s \n", Arrays.toString(actionsPerformed));
						// System.err.format("helperPlan is of size %d", helperPlan.size());
						// System.err.format("fastestSASolution is of size %d",
						// fastestSASolution.size());
						// Agent.solveConflicts(fastestSASolution, extractedPlans);

						helperPlan = fastestSASolution; // Agent.extendSolution(helperPlan, fastestSASolution);

						// System.err.format("combining helperPlan of size %d with fasterSASolution of
						// size %d\n",helperPlan.size(),fastestSASolution.size());
						// if(helperPlan.size()<3){
						// System.err.println("helperPlan was replaced by fasterSASolution");
						// helperPlan = fastestSASolution;
						// }
						// else{
						// System.err.println("helperPlan and fasterSASolution was merged");
						// System.err.format("actionsPerformed for helperPlan
						// %s\n",Arrays.toString(planToActions(helperPlan)));
						// System.err.format("actionsPerformed for fastestSASolution
						// %s\n",Arrays.toString(planToActions(fastestSASolution)));
						// System.err.format("actions for fastestSASolution.get(last)
						// %s\n",fastestSASolution.get(fastestSASolution.size()-1).actions);
						// for(MAState state : helperPlan)
						// System.err.format("%s",state.actions);
						// helperPlan = Agent.solveConflicts(helperPlan, fastestSASolution);
						// }
						// for (int i = 1; i < fastestSASolution.size(); i++) {
						// MAState fastestSolution = fastestSASolution.get(i);
						// MAState exPlan = extractedPlans.get(i);
						// MAState prevPlan = extractedPlans.get(i - 1);

						// List<Command> exActions = exPlan.actions;
						// exActions.set(fastestAgent, fastestSolution.actions.get(fastestAgent));
						// extractedPlans.set(i, new MAState(prevPlan, exActions));
						// }
					}

					// actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;
				}
				extractedPlans = Agent.extendSolution(helperPlan, extractedPlans);
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

	public static Map<Position, Character> moveObjects(Set<Position> objectPositions, MAState state) {
		HashMap<Position, Character> fakeGoals = new HashMap<>();
		Set<Position> path = state.path;
		System.err.format("The path is %s\n", path);

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

						if (state.boxAt(p)) {

							MAState newState = new MAState(state,
									Collections.nCopies(state.numAgents, new Command.NoOp()), true);
							newState.path.add(p);

							Set<Position> tempSet = new HashSet<>();
							tempSet.add(p);

							Map<Position, Character> tmpGoals = Agent.moveObjects(tempSet, newState);

							fakeGoals.putAll(tmpGoals);

						} else if (fakeGoals.containsKey(p)) {
							fakeGoals.remove(p);

							MAState newState = new MAState(state,
									Collections.nCopies(state.numAgents, new Command.NoOp()), true);
							newState.path.add(p);

							Set<Position> tempSet = new HashSet<>();
							tempSet.add(p);

							Map<Position, Character> tmpGoals = Agent.moveObjects(tempSet, newState);

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
		MAState last = newPlan.get(newPlan.size() - 1);
		newPlan.add(new MAState(last, Collections.nCopies(numAgents, new Command.NoOp())));

		int[] newActionsPerformed = Agent.planToActions(alreadyPlanned);

		int[] newActionsPlanned = Agent.planToActions(newPlan);

		ArrayList<MAState> mergedPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));

		for (int i = 1; i < alreadyPlanned.size() - 1; i++) {

			MAState oldState = alreadyPlanned.get(i);
			List<Command> s1 = oldState.actions;
			List<Command> newActions = new ArrayList<>(s1);
			// System.err.format("merged %s with ",s1);

			for (int j = 0; j < numAgents; j++) {

				if (newActionsPerformed[j] < i) {
					List<Command> testActions = new ArrayList<>(s1);
					Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);
					// System.err.format("%s ",currentAction);
					if (!(currentAction instanceof Command.NoOp)) {

						testActions.set(j, currentAction);
						if (oldState.isApplicable(testActions)) {
							newActions.set(j, currentAction);
							newActionsPerformed[j]++;
						}
					}
				}
			}
			// System.err.format("to %s\n",newActions);
			MAState prevState = oldState.parent;
			// System.err.format("Trying to do %s in \n%s",newActions,prevState);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		while (!Arrays.equals(newActionsPerformed, newActionsPlanned)) {

			List<Command> newActions = new ArrayList<>(Collections.nCopies(numAgents, new Command.NoOp()));

			for (int j = 0; j < numAgents; j++) {

				int num = newActionsPerformed[j] + 1;

				if (num == 0 && newActionsPlanned[j] != 0) {
					num = 1;
					newActionsPerformed[j]++;
				}

				Command currentAction = num == 0 ? new Command.NoOp() : newPlan.get(num).actions.get(j);

				// System.err.format("newActionsPerformed is %s and newActionsPlanned is %s and
				// j=%d
				// ",Arrays.toString(newActionsPerformed),Arrays.toString(newActionsPlanned),j);
				// System.err.format("and currentActions is %s\n",currentAction);
				// System.err.format("This is newPlan \n %s",newPlan);

				if (!(currentAction instanceof Command.NoOp)) {
					newActions.set(j, currentAction);
					newActionsPerformed[j]++;
				}
			}
			MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
			System.err.format("applying %s to \n%s", newActions, prevState);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		// mergedPlan.get(mergedPlan.size()-1).path.addAll(newPlan.get(newPlan.size()-1).path);
		// mergedPlan.get(mergedPlan.size()-1).path.addAll(alreadyPlanned.get(alreadyPlanned.size()-1).path);
		return mergedPlan;

	}

	public static ArrayList<MAState> solveConflicts(List<MAState> alreadyPlanned, List<MAState> newPlan) {

		int numAgents = alreadyPlanned.get(0).numAgents;

		// adding a state to the end of newPlan with all NoOp actions
		MAState last = newPlan.get(newPlan.size() - 1);
		newPlan.add(new MAState(last, Collections.nCopies(numAgents, new Command.NoOp())));

		int[] newActionsPlanned = Agent.planToActions(newPlan);

		int[] newActionsPerformed = new int[numAgents];

		ArrayList<MAState> mergedPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));

		for (MAState oldState : alreadyPlanned.subList(1, alreadyPlanned.size())) {

			List<Command> s1 = oldState.actions;
			List<Command> newActions = new ArrayList<>(s1);
			System.err.format("merged %s with ", s1);

			for (int j = 0; j < numAgents; j++) {

				List<Command> testActions = new ArrayList<>(s1);
				int num = newActionsPerformed[j] + 1;

				Command currentAction = newPlan.get(num).actions.get(j);
				System.err.format("%s at %d", currentAction, j);
				if (!(currentAction instanceof Command.NoOp)) {

					testActions.set(j, currentAction);
					if (mergedPlan.get(mergedPlan.size() - 1).isApplicable(testActions)) {
						newActions.set(j, currentAction);
						newActionsPerformed[j]++;
					}
				}
			}
			System.err.format("to %s\n", newActions);
			MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
			System.err.format("Trying to do %s in \n%s", newActions, prevState);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		while (!Arrays.equals(newActionsPerformed, newActionsPlanned)) {

			List<Command> newActions = new ArrayList<>(Collections.nCopies(numAgents, new Command.NoOp()));

			for (int j = 0; j < numAgents; j++) {

				int num = newActionsPerformed[j] + 1;

				if (num == 0 && newActionsPlanned[j] != 0) {
					num = 1;
					newActionsPerformed[j]++;
				}

				Command currentAction = num == 0 ? new Command.NoOp() : newPlan.get(num).actions.get(j);
				// Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);

				if (!(currentAction instanceof Command.NoOp)) {
					newActions.set(j, currentAction);
					newActionsPerformed[j]++;
				}
			}
			MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
			System.err.format("applying %s to \n%s", newActions, prevState);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		return mergedPlan;

	}

	public static Set<Position> lookAhead(List<MAState> plan, MAState freshInitialState) {
		Set<Position> objectPositions = new HashSet<>();

		for (MAState state : plan) {

			for (int i = 0; i < state.numAgents; i++) {

				Command action = state.actions.get(i);
				Position tempAgentPos = freshInitialState.getPositionOfAgent(Integer.toString(i).charAt(0));
				if(tempAgentPos==null){
					continue;
				}
				// System.err.format("agent %d is at %s and wants to do %s in \n%s \n",i,tempAgentPos,action, state);

				if (action instanceof Command.NoOp) {
					continue;
				}
				if (action instanceof Command.Move) {

					Command.Move move = (Command.Move) action;
					// System.err.format("tempAgentPos is %s \n", tempAgentPos);
					tempAgentPos = tempAgentPos.add(move.getAgentDir());
					// System.err.format("tempAgentPos is now %s \n", tempAgentPos);

					if (freshInitialState.boxAt(tempAgentPos)) {
						objectPositions.add(tempAgentPos);
						// System.err.format("%s add after %s by %d\n",tempAgentPos,action,i);
					}

					if (freshInitialState.agentAt(tempAgentPos)) {
						objectPositions.add(tempAgentPos);
						// System.err.format("%s add after %s by %d\n",tempAgentPos,action,i);
					}

				}
				if (action instanceof Command.Push) {

					Command.Push push = (Command.Push) action;

					tempAgentPos = tempAgentPos.add(push.getAgentDir());

					Position tempBoxPos = tempAgentPos.add(push.getBoxDir());

					if (freshInitialState.boxAt(tempBoxPos)) {
						objectPositions.add(tempBoxPos);
						// System.err.format("%s add after %s by %d\n",tempBoxPos,action,i);
					}

					if (freshInitialState.agentAt(tempBoxPos)) {
						objectPositions.add(tempBoxPos);
						// System.err.format("%s add after %s by %d\n",tempBoxPos,action,i);
					}
				}
				if (action instanceof Command.Pull) {

					Command.Pull pull = (Command.Pull) action;

					tempAgentPos = tempAgentPos.add(pull.getAgentDir());
					// Position tempBoxPos = tempAgentPos.add(pull.getBoxDir());

					if (freshInitialState.boxAt(tempAgentPos)) {
						objectPositions.add(tempAgentPos);
						// System.err.format("%s add after %s by %d\n",tempAgentPos,action,i);
					}

					if (freshInitialState.agentAt(tempAgentPos)) {
						objectPositions.add(tempAgentPos);
						// System.err.format("%s add after %s by %d\n",tempAgentPos,action,i);
					}
				}
			}
			freshInitialState = new MAState(freshInitialState, state.actions,true);
			// System.err.format("freshInitialState now looks like \n %s\n",freshInitialState);
			// System.err.format("after %s has been applyed. ObjectPositions is now %s \n",state.actions,objectPositions);
		}
		return objectPositions;
	}
}
