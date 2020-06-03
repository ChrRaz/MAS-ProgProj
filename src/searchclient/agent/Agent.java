package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Main;
import searchclient.Position;
import searchclient.util.Memory;

import java.util.*;

public class Agent {

	public static ArrayList<MAState> search(char agent, List<MAState> alreadyPlanned, Strategy strategy) {
		MAState initialState = alreadyPlanned.get(0);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - 1).goalCount();

		// System.err.format("Search starting (%c) with %d goals using strategy %s.\n", agent, origGoalCount,
				// strategy.toString());

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				// System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));
				// System.err.println("Search failed :(");

				return null;
			}
			;

			MAState leafState = strategy.getAndRemoveLeaf();

			int deltaG = leafState.g() - initialState.g();

			// if (iterations % 10_000 == 0)
				// System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
				// 		Memory.stringRep()));

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

					// System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
							// Memory.stringRep()));
					// System.err.printf("Found solution of length %d\n", plan.size() - 1);

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
		int oldMoves = moves;
		// System.err.format("goalPos is %s",goalPos);
		MAState initialState = alreadyPlanned.get(moves);
		char goalType = initialState.goals.get(goalPos);
		Position agentPos = initialState.getPositionOfAgent(agent);
		MAState startState = initialState.clone();
		// System.err.format("The state of the heuristic is \n%s\n",((Strategy.StrategyBestFirst) strategy).heuristic.contructerState);
		// Map<Position,Character> tempgoals = alreadyPlanned.get(0).goals;
		// for(MAState state : alreadyPlanned){
		// 	if(!state.goals.equals(tempgoals)){
				// System.err.println("Goal mismatch");
		// 		for(MAState state2: alreadyPlanned){
		// 			System.err.println(state2);
		// 		}
		// 		break;
		// 	}
		// }

		// System.err.println("begining alreadyplanned:");
		// for(MAState state : alreadyPlanned)
		// System.err.println(state);

		//goal is satisfied before search starts
		boolean isGoal;
		boolean isAgentGoal = Character.isDigit(goalType);
		if (isAgentGoal) {
			isGoal = initialState.goals.containsKey(agentPos) && initialState.goals.get(agentPos).equals(agent);
		}
		else{
			isGoal = initialState.isGoalSatisfied(goalPos);
		}

		if(isGoal){
			// System.err.println("Goal was allready satisfied returning")	;
			return alreadyPlanned;
		}

		// If not a agentGoal
		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - (1)).goalCount();
		if(!Character.isDigit(initialState.goals.get(goalPos))){
			// System.err.println("before move to box alreadyPlanned.size() = " + alreadyPlanned.size());

			//Find best box for the goal
			Position bestBoxPos = ((Strategy.StrategyBestFirst) strategy).heuristic.bestBox(initialState, initialState.goals.get(goalPos));
			List<MAState> moveAlreadyPlanned = alreadyPlanned.get(alreadyPlanned.size()-1).clone().extractPlanWithInitial();
			initialState = moveAlreadyPlanned.get(moves);
			Map<Position,Character> temp = initialState.satisfiedGoals();
			initialState.goals.clear();
			initialState.goals.putAll(temp);
			// removing initial agentgoals
			for(Position pos : initialState.goals.keySet()){
				if(initialState.goals.get(pos).equals(agent)){
					initialState.goals.remove(pos);
					break;	
				}
			}
			// initialState.goals.clear();
			// initialState.goals.remove(goalPos);
			Position anyGoal = null;
			for(Command.Dir d : Command.Dir.values()){
				Position pos = bestBoxPos.add(d);
				if(!initialState.walls.contains(pos)){
					initialState.goals.put(pos, agent);
					anyGoal = pos;
				}
			}
			String agentColor = initialState.color.get(agent);

			//Move agent to the best box.
			// System.err.println("finding best box calling");
			List<MAState> moveToBoxSolution = Agent.searchIgnore(agent, moveAlreadyPlanned,
								new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(initialState,agentColor,1,agent)), anyGoal, actionsPerformed.clone(),Collections.emptySet());
			
			//writing the moves into alreadyPlanned that gets the agent to the box
			MAState prevState = alreadyPlanned.get(moves);
			// System.err.format("g = %d and prevstate before merge: \n%s\n",prevState.g(),prevState);
			moveAlreadyPlanned = prevState.clone().extractPlanWithInitial();
			if (moveToBoxSolution == null) {
				// System.err.println("Failed to find move-to-box solution");
				return null;
			}

			for(MAState state : moveToBoxSolution.subList(moves+1, moveToBoxSolution.size())){
				List<Command> actions = state.actions;
				MAState nextState = new MAState(prevState,actions);
				prevState = nextState;
				moveAlreadyPlanned.add(nextState);
			}
			// System.err.format("g = %d and prevstate after merge: \n%s\n",prevState.g(),prevState);
			//writing the rest of the states back into alreadyPlanned
			if(alreadyPlanned.size()>moveToBoxSolution.size()){
				for(MAState state : alreadyPlanned.subList(moveToBoxSolution.size()+1, alreadyPlanned.size())){
					MAState nextState = new MAState(prevState,state.actions);
					prevState = nextState;
					moveAlreadyPlanned.add(nextState);
				}
			}
			// System.err.format("g = %d and prevstate after rest is merged: \n%s\n",prevState.g(),prevState);

			// alreadyPlanned = moveAlreadyPlanned;
			// System.err.println("after found box alreadyPlanned.size()=" + alreadyPlanned.size());
			// boolean allNoops = true;
			// for(Command a : alreadyPlanned.get(alreadyPlanned.size()-1).actions){
			// 	if(!(a instanceof Command.NoOp))
			// 	allNoops = false;
			// }
			// if(allNoops)
			// alreadyPlanned.remove(alreadyPlanned.size()-1);
			
			origGoalCount = moveAlreadyPlanned.get(moveAlreadyPlanned.size() - (1)).goalCount();
			int[] moveActionsPerformed = Agent.planToActions(moveAlreadyPlanned);
			moves = moveActionsPerformed[agentId];
			startState = moveAlreadyPlanned.get(moves);
		}
		strategy.addToFrontier(startState);
		moves = oldMoves;
		// System.err.format("last state of alreadyPlanned lookslike %s",alreadyPlanned.get(alreadyPlanned.size() - (1)));
		// System.err.format("agent %s has moved %d\n", agent, moves);
		// System.err.format("goalPos at %s=%c.\n", goalPos,initialState.goals.get(goalPos));
		// System.err.format("initialState has %s as goals and has %s as actions\n", initialState.goals,
				// initialState.actions);
		// System.err.format("alreadyPlanned.size() = %s and last state is \n%s with actions: %s\n", alreadyPlanned.size(),
				// alreadyPlanned.get(alreadyPlanned.size() - 1), alreadyPlanned.get(alreadyPlanned.size() - 1).actions);
		// System.err.format("goals: %s \n", initialState.goals);
		// System.err.format("Search starting (%c) -> %s with %d goals using strategy %s.\n", agent, goalPos,
				// origGoalCount, strategy.toString());
		MAState leafState = null;
		long iterations = 0;




		while (true) {
			// System.err.format("frontier is of size: %s\n",strategy.countFrontier());
			if (strategy.frontierIsEmpty()) {
				// System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));
				// System.err.format("Agent %s could not solve goal %s \n", agent, goalPos);
				// System.err.format("Search failed after %d iterations :(, last state was: %s\n with a g value of %d\n ",
				// 		iterations, leafState, leafState.g());
				// System.err.println("explored consists of:");
				for (MAState state : strategy.explored) {
					// System.err.println(state);
				}

				return null;
			}

			leafState = strategy.getAndRemoveLeaf();
			// System.err.format("leafState.g = %d and is \n%s \n",leafState.g(),leafState);

			// int deltaG = leafState.g() - initialState.g();

			// if (iterations % 5_000 == 0) {
			// 	System.err.println(String.join(" ",
			// 		strategy.searchStatus(),
			// 		strategy.describeState(leafState),
			// 		Memory.stringRep()));
			// 	// ((Strategy.StrategyBestFirst) strategy).heuristic.printH(leafState);
			// 	System.err.println(leafState);

			// 	System.err.format("The heuritic knows the goals %s\n", ((Strategy.StrategyBestFirst) strategy).heuristic.contructerState.goals);
			// }
			// if (iterations % 30_000 == 0){
			// }

			// if (((Strategy.StrategyBestFirst) strategy).heuristic.h(leafState)==0){
			// 	((Strategy.StrategyBestFirst) strategy).heuristic.printH(leafState);
			// 	// System.err.println("leafState has h==0 and goalPos is " + goalPos + " \n" + leafState);
			// }
			agentPos = leafState.getPositionOfAgent(agent);
			// System.err.format("Get a load of this: \n goalPos: %s leafState.boxes: %s
			// leafState.goals: %s \n", goalPos,leafState.boxes,leafState.goals);
			if(isAgentGoal){
				isGoal = leafState.goals.containsKey(agentPos) && leafState.goals.get(agentPos).equals(agent);
			}
			else{
				isGoal = leafState.isGoalSatisfied(goalPos);
			}
				
			// leafState.isGoalSatisfied(goalPos)
			if (isGoal && !oldPath.contains(agentPos)
					&& !leafState.boxes.containsKey(agentPos)) {
				
				// checking goalcount
				MAState endState = leafState;
				// int tempcount = leafState.goalCount();
				// System.err.format("endState looks like this before counting goals: \n%s\n",endState);


				boolean isApplicable = true;
				for (MAState state : alreadyPlanned.subList(Math.min(leafState.g()+1, alreadyPlanned.size()),
						alreadyPlanned.size())) {
					if (!endState.isApplicable(state.actions)) {
						// System.err.format("endState.g() = %d and alreadyPlanned.size() = %d    the actions %s was not applicable in \n%s \n",endState.g(),alreadyPlanned.size(),state.actions,endState);
						isApplicable = false;
						break;
					}
					endState = new MAState(endState, state.actions);
					// System.err.format("endState.actions = %s with g = %d, \nboxes: %s \ngoals %s\n",endState.actions,endState.g(),endState.boxes,endState.goals);
					// System.err.println("endState.isGoalSatisfied(goalPos) = "+endState.isGoalSatisfied(goalPos));
					// System.err.format("endState looks like this: \n%s\n",endState);
				}
				// System.err.println("Counting goals");
				// System.err.format("isApplicable=%s and endState.goalCount() = %d and origGoalCount is %d\n",isApplicable,endState.goalCount(),origGoalCount);
				// System.err.format("satisfied goals in leafstate: %s \n satisfied goals in endState : %s \n", leafState.satisfiedGoals(),endState.satisfiedGoals());
				if (isApplicable && endState.goalCount() < origGoalCount) {

					// System.err.println("goalPos = " + goalPos);
					// System.err.println("leafState =\n" + leafState);
					// System.err.println("initialState = " + initialState);
					// System.err.format("%s found goal after %d states\n", agent, leafState.g() - initialState.g());

					List<MAState> extractedPlans = leafState.extractPlanWithInitial();
					List<MAState> shortExtractedPlans = extractedPlans.subList(oldMoves+1, extractedPlans.size());
					int[] actionsSEP = Agent.planToActions(extractedPlans.subList(moves, extractedPlans.size()));
					// System.err.format("agent: %c started from %d moves\n", agent, moves);
					// System.err.format("shortExtractedPlans:\n");
					// for (MAState state : shortExtractedPlans) {
					// 	// System.err.println(strategy.describeState(state));
					// 	// System.err.println(state);
					// }

					List<MAState> helperPlan = alreadyPlanned;

					// int max = 0;
					// for (int value : actionsPerformed) {
					// max = Math.max(value, max);
					// }
					// List<MAState> helperPlan = new
					// ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));
					// if(moves!=max){
					// System.err.format("moves %d was not max %d\n",moves,max);
					// helperPlan = alreadyPlanned.subList(moves, alreadyPlanned.size());
					// }

					// System.err.format("helperplan is of length %d\n",helperPlan.size()-1);
					// for (int i = 0; i < helperPlan.size(); i++) {
					// 	MAState state = helperPlan.get(i);
					// 	// System.err.format("Action %d of helperPlan is %s applyed to\n%s\n\n", i,
					// 	// state.actions,
					// 	// state.parent);
					// }
					
					boolean notDone = true;
					Map<Position,Character> objectPositions = new HashMap<>();
					Map<Position, Character> fakeGoals = new HashMap<>();
					while (notDone) {

						MAState freshInitialState = new MAState(alreadyPlanned.get(0),
								Collections.nCopies(leafState.numAgents, new Command.NoOp()));

						for (MAState state : helperPlan) {
							if (state.actions == null)
							continue;
							// System.err.format("applying actions %s to freshInitialState from helperPlan\n", state.actions);
							freshInitialState = new MAState(freshInitialState, state.actions);
							// System.err.println(freshInitialState);
						}

						// System.err.format("freshInitialState looks like: \n%s\n", freshInitialState);
						if (freshInitialState.getPositionOfAgent(agent).equals(initialState.getPositionOfAgent(agent))) {
							// System.err.printf("Looking for agent %c\n", agent);
							objectPositions = Agent.lookAhead(shortExtractedPlans, freshInitialState, agent);
							// System.err.printf("Moving objects for agent %c\n", agent);
							fakeGoals = Agent.moveObjects(objectPositions, freshInitialState, MAState.getPath(shortExtractedPlans, agent));
							if (fakeGoals == null)
								return null;

						}
						// else{
						// 	System.err.format("relying on old objectPositions\n");
						// }
						MAState checkingState = freshInitialState.clone();
						checkingState.goals.clear();
						checkingState.goals.putAll(fakeGoals);
						int missingFakeGoals = 0;
						for (Position fakeGoalPos : checkingState.goals.keySet()) {
							if (!checkingState.isGoalSatisfied(fakeGoalPos)) {
								missingFakeGoals++;
							}
						}
						// System.err.format("missingFakeGoals is %d", missingFakeGoals);


						if (!freshInitialState.getPositionOfAgent(agent).equals(initialState.getPositionOfAgent(agent)) && missingFakeGoals == 0) {
							// System.err.println("Replaning because we helped our selfs");
							// System.err.format("fresh pos %s pre pos %s \n", freshInitialState.getPositionOfAgent(agent),
							// initialState.getPositionOfAgent(agent));
							// System.err.format("are they different: %b",
							// freshInitialState.getPositionOfAgent(agent) != initialState.getPositionOfAgent(agent));
							// System.err.format("agent is %s and initialState is \n%s\n and freshInitialState is \n%s\n",
							// agent, initialState, freshInitialState);

							List<MAState> newAlreadyPlanned = new ArrayList<>(helperPlan);
							int[] newActionsPerformed = Agent.planToActions(newAlreadyPlanned);
							MAState newInitialState = newAlreadyPlanned.get(newActionsPerformed[agentId]);
							newInitialState.goals.put(goalPos, goalType);
							Strategy newStrategy = new Strategy.StrategyBestFirst(
								new Heuristic.AStar(newInitialState, newInitialState.color.get(agent)));
							// System.err.println("We helped our self so we recalculate. newAlreadyPlanned.size() = " + newAlreadyPlanned.size());
							return Agent.searchIgnore(agent, newAlreadyPlanned, newStrategy, goalPos, newActionsPerformed,
								oldPath);
						}

						// System.err.format("fresh looks like this before lookahead: \n%s\n",freshInitialState);
						// objectPositions = Agent.lookAhead(shortExtractedPlans, freshInitialState, agent);

						// System.err.format("obejctPositions is %s found in \n%s\n", objectPositions, freshInitialState);

						// assert false;

						// If nothing needs to move we are done
						if (objectPositions.size() < 1) {
							// int [] actions2 = Agent.planToActions(shortExtractedPlans);
							// int [] actionHelperPlan = Agent.planToActions(helperPlan);
							// System.err.println("helperPlan actions: " +
							// Arrays.toString(actionHelperPlan));
							// System.err.println("shortExtractedPlans actions: " +
							// Arrays.toString(actions2));
							// System.err.println("helperPlan");
							// for(MAState state : helperPlan){
							// 	System.err.println(strategy.describeState(state));
							// 	System.err.println(state);
							// }
							// System.err.println("shortExtractedPlans");
							// for(MAState state : shortExtractedPlans){
							// 	System.err.println(strategy.describeState(state));
							// 	System.err.println(state);
							// }
							List<MAState> plan = Agent.solveConflicts(helperPlan, shortExtractedPlans, agent, actionsSEP);
							// System.err.println("Plan");
							// for(MAState state : plan){
							// 	System.err.println(strategy.describeState(state));
							// 	System.err.println(state);
							// }
							// System.err.println("helperPlan.size() = " + helperPlan.size());
							// System.err.println("shortextractedPlans.size() = " + shortExtractedPlans.size());
							// System.err.println("Plan.size() = " + plan.size());
							// System.err.println("no objects returning");
							// if(plan.size()>15)
							// assert false;
							return plan;
						}

						// System.err.println("objectPositions = " + objectPositions);
						// freshInitialState is final state
						// System.err.format("fresh looks like this before moveobjects: \n%s\n",freshInitialState);
						// Map<Position, Character> fakeGoals = Agent.moveObjects(objectPositions, freshInitialState, MAState.getPath(shortExtractedPlans, agent));
						// System.err.println("fakeGoals = " + fakeGoals);

						// actionsPerformed[agentId] = extractedPlans.size() - 1;

						List<MAState> fastestSASolution = null;
						int fastestAgent = -1;

						MAState orderState = helperPlan.get(helperPlan.size() - 1).clone();
						orderState.goals.clear();
						orderState.goals.putAll(fakeGoals);

						List<Position> goalOrder = Main.orderGoals(orderState);
						// System.err.printf("Goal order is: %s\n", goalOrder.stream().map(x -> x + " = " + orderState.goals.get(x)).collect(Collectors.toList()));

						Position fakeGoalPos = null;
						int nextGoalIndex = 0;
						for (int i = 0; i < goalOrder.size(); i++) {
							if (!orderState.isGoalSatisfied(goalOrder.get(i))) {
								fakeGoalPos = goalOrder.get(i);
								nextGoalIndex = i;
								break;
							}
						}

						Map<Position, Character> prevGoals = new HashMap<>();
						for (Position tempPos : goalOrder.subList(0, nextGoalIndex)) {
							prevGoals.put(tempPos, orderState.goals.get(tempPos));
						}

						char fakeGoalType = orderState.goals.get(fakeGoalPos);

						// Find single agent-goal pair such that agent fills goal fastest
						for (Map.Entry<Position, Character> helperAgent : initialState.agents.entrySet()) {
							// Position agentPos = helperAgent.getKey();
							char agentType = helperAgent.getValue();

							// Skip agent goals for other agents
							if (Character.isDigit(fakeGoalType) && fakeGoalType != agentType)
								continue;

							if (!initialState.color.get(agentType).equals(initialState.color.get(fakeGoalType))) {
								// System.err.println("helper was continued");
								// System.err.println("agent has color " + initialState.color.get(agentType));
								// System.err.println("fakeGoal has color " + initialState.color.get(fakeGoalType));
								continue;
							}

							int helperAgentId = Character.getNumericValue(agentType);
							String helperAgentColor = initialState.color.get(agentType);

							int helperMoves = actionsPerformed[helperAgentId];

							// System.err.format("actionsPerformed is %s\n", Arrays.toString(actionsPerformed));

							assert helperMoves < helperPlan.size() : "moves must be within helperPlan";

							// MAState state = helperPlan.get(helperMoves);

							List<MAState> fakeHelperPlan = helperPlan.get(helperPlan.size() - 1).clone()
								.extractPlanWithInitial();

							MAState state = fakeHelperPlan.get(helperMoves);
							state.goals.clear();
							state.goals.putAll(prevGoals);
							state.goals.put(fakeGoalPos, fakeGoalType);
							// System.err.format("fake: Heuristic knows the goals: %s\n", state.goals);
							// assert false;
							// ArrayList<MAState> saSolution = Agent.search(agentType,
							// helperPlan.subList(helperMoves, helperPlan.size()),
							// new Strategy.StrategyBestFirst(new Heuristic.AStar(state, agentColor)));
							// System.err.println("getting help from" + helperAgent);
							List<MAState> saSolution = Agent.searchIgnore(agentType, fakeHelperPlan,
								new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(state, helperAgentColor, 1)), fakeGoalPos,
								actionsPerformed, MAState.getPath(shortExtractedPlans, agent));

							// System.err.format("done getting help from %s with goalPos %s!!!!!11\n", helperAgent,
							// fakeGoalPos);
							// System.err.format("saSolution was of length: %d\n",saSolution.size());
							// for(MAState WTFstate : saSolution){
							// 	System.err.println(WTFstate);
							// }

							// System.err.format("all fakegoals are: %s\n", fakeGoals);
							// state.goals.remove(fakeGoalPos);
							if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
								fastestSASolution = saSolution;
								fastestAgent = helperAgentId;
								// System.err.println("if what mother fucker");
								// System.err.format("fastestSASolution: \n%s\n",fastestSASolution);
								// System.err.format("saSolution: \n%s\n",saSolution);
							}
							// System.err.println("inside fastestSASolution is : " + fastestSASolution);
							// System.err.println("inside fastestAgent is : " + fastestAgent);
						}
						// System.err.println("outside fastestSASolution is : " + fastestSASolution);
						// System.err.println("outside fastestAgent is : " + fastestAgent);

						if (fastestSASolution == null) {
							// System.err.println("no local solution");
							return null;
						}

						// System.err.format("Replacing %s with ", Arrays.toString(actionsPerformed));
						actionsPerformed = Agent.planToActions(fastestSASolution);
						// System.err.format("%s \n", Arrays.toString(actionsPerformed));

						// System.err.println();
						// System.err.format("fastestSASolution is: \n");

						// for (MAState state : fastestSASolution){
						// System.err.println(state);
						// }

						// System.err.format("and helperPlan is: \n");

						// for (MAState state : helperPlan){
						// System.err.println(state);
						// }
						// System.err.println();

						// System.err.format("helperPlan is of size %d", helperPlan.size());
						// System.err.format("fastestSASolution is of size %d",
						// fastestSASolution.size());
						// Agent.solveConflicts(fastestSASolution, extractedPlans);

						// helperPlan = Agent.extendSolution(helperPlan, fastestSASolution); //
						// Agent.extendSolution(helperPlan, fastestSASolution);

						helperPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));
						// System.err.println("building new helperplan");
						MAState prevState = helperPlan.get(0);
						for (MAState state : fastestSASolution.subList(1, fastestSASolution.size())) {
							helperPlan.add(new MAState(prevState, state.actions));
							prevState = helperPlan.get(helperPlan.size() - 1);
							// System.err.format("In new helperplanstate %d goals are: %s\n",prevState.g(),prevState.goals);
						}
						// System.err.println("helperPlan looks like:");
						// for(MAState state : helperPlan)
						// System.err.println(state);

						// // Expand SA solution
						// while (fastestSASolution.size() < helperPlan.size()) {
						// MAState lastState = fastestSASolution.get(fastestSASolution.size() - 1);
						// List<Command> actions = maSolution.get(lastState.g() + 1).actions;
						// fastestSASolution.add(new MAState(lastState, actions));
						// }
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

						// actionsPerformed[fastestAgent] = fastestSASolution.size() - 1;
					}
					extractedPlans = Agent.extendSolution(helperPlan, extractedPlans);
					// System.err.println("extractedPlans");
					// // System.err.println(extractedPlans);
					// for(MAState state : extractedPlans){
					// 	System.err.println(strategy.describeState(state));
					// 	System.err.println(state);
					// }
					// System.err.println("lul never happens but returning");
					return extractedPlans;
				}
			}

			strategy.addToExplored(leafState);

			// System.err.format("alreadyPlanned is of length
			// %s\n",alreadyPlanned.size()-1);
			// if(leafState.g()<alreadyPlanned.size()-1){

			// MAState nextState = alreadyPlanned.get(leafState.g()+1);
			// if(nextState.actions!=null){

			// if(!leafState.isApplicable(nextState.actions)){
			// System.err.format("we could not do what we had planed\n leafstate.g()=%d
			// alreadyPlanned.size()=%d\n",leafState.g(),alreadyPlanned.size());
			// continue;
			// } else{
			// leafState.apply
			// System.err.format("%s was applicable in \n %s
			// \n",nextState.actions,leafState);
			// }
			// }
			// }

			boolean insideList = leafState.g()+1 < (alreadyPlanned.size());
			int numAgents = leafState.numAgents;

			
			MAState state;
			if (insideList && leafState.g() >= moves) {
				// System.err.format("alreadyPlanned.size(): %s leafState.g(): %s",alreadyPlanned.size(),leafState.g());
				// System.err.println("insideList = " + insideList + " and nextState.actions will be " + alreadyPlanned.get(leafState.g()+1).actions);
				List<Command> actions = alreadyPlanned.get(leafState.g()+1).actions;
				if (!leafState.isApplicable(actions)) {

					// System.err.format("could not apply %s to \n%s\n", actions, leafState);
					// System.err.format("parent.actions: %s and
					// leafstate.g()=%d\n",leafState.parent.actions,leafState.g());
					// System.err.println("parent.extractPlanWithInitial is:");
					// for (MAState tempstate : leafState.parent.extractPlanWithInitial()) {
						// System.err.println(tempstate);
					// }
					continue;
				}

				state = new MAState(leafState, actions);
			} else {
				state = new MAState(alreadyPlanned.get(0), Collections.nCopies(numAgents, new Command.NoOp()));
			}
			if (state.actions != null && leafState.actions != null) {
				// System.err.format("state.actions is %s\n", state.actions);
			}

			for (MAState n : leafState.getExpandedStatesIgnore(agent,state)) {
				if (!strategy.isExplored(n) && !strategy.inFrontier(n)) {
					strategy.addToFrontier(n);
					// System.err.println("state was added with actions: " + n.actions);
				}
				// if (strategy.isExplored(n)){
				// System.err.println("Following state was in explored:\n" + state);
				// }
			}
			iterations++;
		}
	}

	public static int[] planToActions(List<MAState> extractedPlan) {
		// System.err.println("!!!!!!!PLANTOACTIONS!!!!!!!!!!!!! WITH EXTRACTEDPLAN.SIZE() = " + extractedPlan.size());
		int numAgents = extractedPlan.get(0).numAgents;
		int[] actionsPerformed = new int[numAgents];

		for (int i = 0; i < extractedPlan.size(); i++) {

			if (extractedPlan.get(i).actions == null)
				continue;

			List<Command> actions = extractedPlan.get(i).actions;

			for (int j = 0; j < numAgents; j++) {
				if (!(actions.get(j) instanceof Command.NoOp)){

					// System.err.println("action was " + actions.get(j) + " and i = " + i);	
					actionsPerformed[j] = i;
				}
			}
		}

		return actionsPerformed;
	}

	public static Map<Position, Character> moveObjects(Map<Position,Character> objectPositions, MAState state, Set<Position> path) {
		HashMap<Position, Character> fakeGoals = new HashMap<>();
		// System.err.format("The path is %s\n", path);

		// System.err.format("state has boxes at %s and agents at %s\n", state.boxes, state.agents);

		// Flood-fill. Update shortest distances from each goal in turn using BFS
		for (Map.Entry<Position,Character> object : objectPositions.entrySet()) {
			Position objectPosition = object.getKey();
			char objectName = object.getValue();
			ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(objectPosition));
			Set<Position> alreadyVisited = new HashSet<>();

			// char objectName = state.getCell(objectPosition);
			// System.err.format("found object at %s which was %s\n", objectPosition,objectName);
			// System.err.format("State looks like: \n%s \n",state);

			// if (state.boxAt(objectPosition)) {

			// objectName =
			// fakeGoals.put(p, state.boxes.get(objectPosition));

			// System.err.println("found a object to be a box at");
			// System.err.println(objectPosition);
			// System.err.println(state.boxes.get(objectPosition));
			// }
			// if (state.agentAt(objectPosition)) {
			// fakeGoals.put(p, state.agents.get(objectPosition));

			// System.err.println("found a object to be a agent at");
			// System.err.println(objectPosition);
			// System.err.println(state.agents.get(objectPosition));
			// }

			// System.err.println(path);

			while (true) {
				if (frontier.isEmpty())
					return null;

				Position p = frontier.pop();
				// System.err.format("In move objects found p = %s\n",p);

				if (p.within(0, 0, state.height - 1, state.width - 1) && !state.walls.contains(p)
						&& !alreadyVisited.contains(p)) {

					// We do not want to overwrite existing goals, except when empty goals of the correct type
					// as these goals are likely the correct goal anyway.
					if (!path.contains(p) && (!state.goals.containsKey(p) || state.goals.get(p).equals(objectName) && !state.isGoalSatisfied(p))) {
						// System.err.printf("Moved [%c] from %s to %s\n", objectName, objectPosition, p);

						if (state.boxAt(p)||state.agentAt(p)) {

							Set<Position> newPath = new HashSet<>(path);
							newPath.add(p);

							Map<Position, Character> tempSet = new HashMap<>();
							tempSet.put(p,state.getCell(p));

							Map<Position, Character> tmpGoals = Agent.moveObjects(tempSet, state,newPath);
							if (tmpGoals == null)
								return null;

							fakeGoals.putAll(tmpGoals);

						} else if (fakeGoals.containsKey(p)) {
							Set<Position> newPath = new HashSet<>(path);
							newPath.add(p);

							Map<Position, Character> tempSet = new HashMap<>();
							tempSet.put(p,fakeGoals.get(p));

							fakeGoals.remove(p);

							Map<Position, Character> tmpGoals = Agent.moveObjects(tempSet, state,newPath);
							if (tmpGoals == null)
								return null;

							fakeGoals.putAll(tmpGoals);
						}

						// System.err.format("The object %s in pos %s can be moved to %s in state: \n %s \n", objectName,
						// 		objectPosition, p, state);
						fakeGoals.put(p, objectName);

						// System.err.println("Here comes dat p & boxPosition");
						// System.err.println(p);
						// System.err.println(objectPosition);
						// System.err.println(state);

						break;
					}

					alreadyVisited.add(p);
					// System.err.format("adding to frontier from p = %s\n",p);
					for (Command.Dir dir : Command.Dir.values()) {
						frontier.add(p.add(dir));
					}
				} else{
					// System.err.format("p = %s was not a valid position\n",p);
				}
			}
			// if (frontier.isEmpty())
			// 	assert false;
		}
		assert fakeGoals.values().stream().allMatch(Character::isLetterOrDigit);
		// System.err.format("fakegoals look like: %s\n",fakeGoals);
		return fakeGoals;
	}

	public static ArrayList<MAState> saSearch(MAState initialState, Strategy strategy) {
		// System.err.format("Search starting with strategy %s.\n", strategy.toString());
		strategy.addToFrontier(initialState);

		Character agent = initialState.agents.values().iterator().next();

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				// System.err.println(String.join("\t", strategy.searchStatus(), Memory.stringRep()));

				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			// if (iterations % 10000 == 0)
			// 	System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
			// 			Memory.stringRep()));

			if (leafState.isGoalState()) {
				// System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
						// Memory.stringRep()));

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

	// public static ArrayList<MAState> replaceActions(List<MAState> alreadyPlanned,
	// List<MAState> newPlan)

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
			// System.err.format("applying %s to \n%s\n", newActions, prevState);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		return mergedPlan;

	}

	public static List<MAState> solveConflicts(List<MAState> alreadyPlanned, List<MAState> newPlan, char agent,
			int[] newActionsPlanned) {

		// TODO:: Make it able to do NoOps in the middel of the solution instead of only
		//  in the begining

		int numAgents = alreadyPlanned.get(0).numAgents;

		int[] actionsPerformed = Agent.planToActions(alreadyPlanned);

		int agentID = Character.getNumericValue(agent);

		// adding a state to the end of newPlan with all NoOp actions
		if (newPlan.size() < 1)
			return alreadyPlanned;

		MAState last = newPlan.get(newPlan.size() - 1);
		newPlan.add(new MAState(last, Collections.nCopies(numAgents, new Command.NoOp())));

		// int[] newActionsPlanned = Agent.planToActions(newPlan);

		// System.err.println("newPlans actions are : ");
		// for(MAState state : newPlan)
		// System.err.format("actions: %s\n",state.actions);

		// System.err.println("alreadyPlanned actions are : ");
		// for(MAState state : alreadyPlanned)
		// System.err.format("actions: %s\n",state.actions);

		// int[] newActionsPerformed = new int[numAgents];

		ArrayList<MAState> mergedPlan = new ArrayList<>(alreadyPlanned.subList(0, actionsPerformed[agentID] + 1));

		MAState state = null;
		List<Command> actions = null;
		int aPcount = actionsPerformed[agentID] + 1;
		int newCount = 0;
		int noOpNum = 0;

		while (true) {

			// System.err.format("i = %d newPlan.size() = %d alreadyPlanned.size() =
			// %d\n",newCount,newPlan.size(),alreadyPlanned.size());

			if (newCount >= (newPlan.size() - 1) && aPcount >= (alreadyPlanned.size() - 1)) {

				// System.err.format(
						// "SolveConflicts found mergedPlan of size %d, via AlreadyPlanned of size %d and newPlan of %d\n",
						// mergedPlan.size(), alreadyPlanned.size(), newPlan.size());

				return mergedPlan;
			}

			if (aPcount < alreadyPlanned.size()) {
				state = alreadyPlanned.get(aPcount);
				aPcount++;
			} else {
				// System.err.format("i>alreadyPlanned.size() and is %d\n",i);
				state = new MAState(alreadyPlanned.get(0), Collections.nCopies(numAgents, new Command.NoOp()));
			}

			actions = new ArrayList<>(state.actions);

			List<Command> testActions = new ArrayList<>(actions);

			if (newCount < newPlan.size())
				testActions.set(agentID, newPlan.get(newCount).actions.get(agentID));

			MAState prevState = mergedPlan.get(mergedPlan.size() - 1);


			if (prevState.isApplicable(testActions)) {

				mergedPlan.add(new MAState(prevState, testActions));
				newCount++;

			} else if (!prevState.isApplicable(actions)) {

				mergedPlan = new ArrayList<>(alreadyPlanned.subList(0, actionsPerformed[agentID] + 1));
				aPcount = actionsPerformed[agentID] + 1;
				newCount = 0;
				newPlan.add(0, new MAState(newPlan.get(0), Collections.nCopies(numAgents, new Command.NoOp())));

			} else {

				mergedPlan.add(new MAState(prevState, actions));

			}

		}

	}

	// for (MAState oldState : alreadyPlanned.subList(1, alreadyPlanned.size())) {

	// System.err.format("pre:: newActionsPerformed: %s newActionsPlanned:
	// %s\n",Arrays.toString(newActionsPerformed),Arrays.toString(newActionsPlanned));
	// List<Command> s1 = oldState.actions;
	// List<Command> newActions = new ArrayList<>(s1);
	// System.err.format("merged %s with ", s1);

	// for (int j = 0; j < numAgents; j++) {

	// List<Command> testActions = new ArrayList<>(s1);
	// int num = newActionsPerformed[j] + 1;

	// Command currentAction = newPlan.get(num).actions.get(j);
	// System.err.format("%s at %d", currentAction, j);

	// if (!(currentAction instanceof Command.NoOp)) {
	// testActions.set(j, currentAction);
	// if (mergedPlan.get(mergedPlan.size() - 1).isApplicable(testActions)) {
	// newActions.set(j, currentAction);
	// newActionsPerformed[j]++;
	// }
	// }
	// }
	// System.err.format("to %s\n", newActions);
	// MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
	// System.err.format("Trying to do %s in \n%s\n", newActions, prevState);
	// mergedPlan.add(new MAState(prevState, newActions));

	// }
	// while (!Arrays.equals(newActionsPerformed, newActionsPlanned)) {

	// List<Command> newActions = new ArrayList<>(Collections.nCopies(numAgents, new
	// Command.NoOp()));

	// for (int j = 0; j < numAgents; j++) {

	// int num = newActionsPerformed[j]+1;

	// if (num == 0) {
	// num = 1;
	// newActionsPerformed[j]++;
	// }

	// Command currentAction = num == 0 ? new Command.NoOp() :
	// newPlan.get(num).actions.get(j);
	// // Command currentAction =
	// newPlan.get(newActionsPerformed[j]).actions.get(j);

	// if (!(currentAction instanceof Command.NoOp)) {
	// newActions.set(j, currentAction);
	// newActionsPerformed[j]++;
	// }
	// }
	// MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
	// // System.err.format("solve Conflicts, applying %s to \n%s\n", newActions,
	// prevState);
	// System.err.format("newActionsPerformed: %s newActionsPlanned:
	// %s\n",Arrays.toString(newActionsPerformed),Arrays.toString(newActionsPlanned));
	// mergedPlan.add(new MAState(prevState, newActions));
	// // int j = 0;
	// // for(Command com : newActions){
	// // if(com instanceof Command.NoOp)
	// // j++;
	// // }
	// // if(j==numAgents)
	// // System.err.println("Printing newPlan!!!!!!!!!!!!!!");
	// // for(MAState state : newPlan){
	// // System.err.format("%s with actions %s \n",state,state.actions);
	// // }
	// // assert false;

	// }

	// return mergedPlan;

	public static Map<Position,Character> lookAhead(List<MAState> plan, MAState freshInitialState, char agent) {
		Map<Position,Character> objectPositions = new HashMap<>();
		// System.err.println("starting lookAhead");
		// System.err.format("plan is of lenght %d", plan.size());

		for (MAState state : plan) {

			int i = Character.getNumericValue(agent);
			Command action = state.actions.get(i);
			Position tempAgentPos = freshInitialState.getPositionOfAgent(Integer.toString(i).charAt(0));
			if (tempAgentPos == null) {
				continue;
			}
			// System.err.format("agent %d is at %s and wants to do %s in \n%s\n",i,tempAgentPos,action, state);

			if (action instanceof Command.NoOp) {
				continue;
			}
			if (action instanceof Command.Move) {

				Command.Move move = (Command.Move) action;
				// System.err.format("tempAgentPos is %s \n", tempAgentPos);
				tempAgentPos = tempAgentPos.add(move.getAgentDir());
				// System.err.format("tempAgentPos is now %s \n", tempAgentPos);

				if (freshInitialState.boxAt(tempAgentPos)) {
					char b = freshInitialState.boxes.get(tempAgentPos);
					objectPositions.put(tempAgentPos, b);
					// System.err.format("box at %s add after %s by %d\n",tempAgentPos,action,i);
				}

				if (freshInitialState.agentAt(tempAgentPos)) {
					char a = freshInitialState.agents.get(tempAgentPos);
					objectPositions.put(tempAgentPos, a);
					// System.err.format("agent at %s add after %s by %d\n",tempAgentPos,action,i);
				}

			}
			if (action instanceof Command.Push) {

				Command.Push push = (Command.Push) action;

				tempAgentPos = tempAgentPos.add(push.getAgentDir());

				Position tempBoxPos = tempAgentPos.add(push.getBoxDir());

				if (freshInitialState.boxAt(tempBoxPos)) {
					char b = freshInitialState.boxes.get(tempBoxPos);
					objectPositions.put(tempBoxPos, b);
					// System.err.format("box at %s add after %s by %d\n",tempAgentPos,action,i);
				}

				if (freshInitialState.agentAt(tempBoxPos)) {
					char a = freshInitialState.agents.get(tempBoxPos);
					objectPositions.put(tempBoxPos, a);
					// System.err.format("agent at %s add after %s by %d\n",tempAgentPos,action,i);
				}
			}
			if (action instanceof Command.Pull) {

				Command.Pull pull = (Command.Pull) action;

				tempAgentPos = tempAgentPos.add(pull.getAgentDir());
				// Position tempBoxPos = tempAgentPos.add(pull.getBoxDir());

				if (freshInitialState.boxAt(tempAgentPos)) {
					char b = freshInitialState.boxes.get(tempAgentPos);
					objectPositions.put(tempAgentPos, b);
					// System.err.format("box at %s add after %s by %d\n",tempAgentPos,action,i);
				}

				if (freshInitialState.agentAt(tempAgentPos)) {
					char a = freshInitialState.agents.get(tempAgentPos);
					objectPositions.put(tempAgentPos, a);
					// System.err.format("agent at %s add after %s by %d\n",tempAgentPos,action,i);
				}
			}
			List<Command> newActions = new ArrayList<>(Collections.nCopies(state.numAgents, new Command.NoOp()));
			newActions.set(i, action);
			// System.err.format("applying %s to \n%s\n",newActions,freshInitialState);
			freshInitialState = new MAState(freshInitialState, newActions, true);
			// System.err.format("freshInitialState now looks like
			// \n%s\n",freshInitialState);
			// System.err.format("after %s has been applyed. ObjectPositions is now
			// %s\n",state.actions,objectPositions);
		}
		assert objectPositions.values().stream().allMatch(Character::isLetterOrDigit);
		return objectPositions;
	}
}
