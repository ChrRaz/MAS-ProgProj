package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Main;
import searchclient.Position;
import searchclient.util.Memory;

import java.util.*;
import java.util.stream.Collectors;

public class Agent {

	public static ArrayList<MAState> search(char agent, List<MAState> alreadyPlanned, Strategy strategy) {
		MAState initialState = alreadyPlanned.get(0);
		strategy.addToFrontier(initialState);

		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - 1).goalCount();

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
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
		MAState initialState = alreadyPlanned.get(moves);
		char goalType = initialState.goals.get(goalPos);
		Position agentPos = initialState.getPositionOfAgent(agent);
		MAState startState = initialState.clone();

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
			return alreadyPlanned;
		}

		// If not a agentGoal
		int origGoalCount = alreadyPlanned.get(alreadyPlanned.size() - (1)).goalCount();
		if(!Character.isDigit(initialState.goals.get(goalPos))){

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
			List<MAState> moveToBoxSolution = Agent.searchIgnore(agent, moveAlreadyPlanned,
								new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(initialState,agentColor,1,agent)), anyGoal, actionsPerformed.clone(),Collections.emptySet());
			
			//writing the moves into alreadyPlanned that gets the agent to the box
			MAState prevState = alreadyPlanned.get(moves);
			moveAlreadyPlanned = prevState.clone().extractPlanWithInitial();
			if (moveToBoxSolution == null) {
				return null;
			}

			for(MAState state : moveToBoxSolution.subList(moves+1, moveToBoxSolution.size())){
				List<Command> actions = state.actions;
				MAState nextState = new MAState(prevState,actions);
				prevState = nextState;
				moveAlreadyPlanned.add(nextState);
			}

			//writing the rest of the states back into alreadyPlanned
			if(alreadyPlanned.size()>moveToBoxSolution.size()){
				for(MAState state : alreadyPlanned.subList(moveToBoxSolution.size()+1, alreadyPlanned.size())){
					MAState nextState = new MAState(prevState,state.actions);
					prevState = nextState;
					moveAlreadyPlanned.add(nextState);
				}
			}
			
			origGoalCount = moveAlreadyPlanned.get(moveAlreadyPlanned.size() - (1)).goalCount();
			int[] moveActionsPerformed = Agent.planToActions(moveAlreadyPlanned);
			moves = moveActionsPerformed[agentId];
			startState = moveAlreadyPlanned.get(moves);
		}
		strategy.addToFrontier(startState);
		moves = oldMoves;
		MAState leafState = null;
		long iterations = 0;

		while (true) {
			if (strategy.frontierIsEmpty()) {
				return null;
			}

			leafState = strategy.getAndRemoveLeaf();

			// if (iterations % 5_000 == 0) {
			// System.err.println(String.join(" ",
			// 		strategy.searchStatus(),
			// 		strategy.describeState(leafState),
			// 		Memory.stringRep()));
			// 	// ((Strategy.StrategyBestFirst) strategy).heuristic.printH(leafState);
			// System.err.println(leafState);

			agentPos = leafState.getPositionOfAgent(agent);
			if(isAgentGoal){
				isGoal = leafState.goals.containsKey(agentPos) && leafState.goals.get(agentPos).equals(agent);
			}
			else{
				isGoal = leafState.isGoalSatisfied(goalPos);
			}
				
			if (isGoal && !oldPath.contains(agentPos)
					&& !leafState.boxes.containsKey(agentPos)) {
				
				// checking goalcount
				MAState endState = leafState;


				boolean isApplicable = true;
				for (MAState state : alreadyPlanned.subList(Math.min(leafState.g()+1, alreadyPlanned.size()),
						alreadyPlanned.size())) {
					if (!endState.isApplicable(state.actions)) {
						isApplicable = false;
						break;
					}
					endState = new MAState(endState, state.actions);
				}
				if (isApplicable && endState.goalCount() < origGoalCount) {

					List<MAState> extractedPlans = leafState.extractPlanWithInitial();
					List<MAState> shortExtractedPlans = extractedPlans.subList(oldMoves+1, extractedPlans.size());
					int[] actionsSEP = Agent.planToActions(extractedPlans.subList(moves, extractedPlans.size()));

					List<MAState> helperPlan = alreadyPlanned;

					boolean notDone = true;
					Map<Position,Character> objectPositions = new HashMap<>();
					Map<Position, Character> fakeGoals = new HashMap<>();
					while (notDone) {

						MAState freshInitialState = new MAState(alreadyPlanned.get(0),
								Collections.nCopies(leafState.numAgents, new Command.NoOp()));

						for (MAState state : helperPlan) {
							if (state.actions == null)
							continue;
							freshInitialState = new MAState(freshInitialState, state.actions);
						}

						if (Agent.planToActions(helperPlan)[agentId] == moves) {
							objectPositions = Agent.lookAhead(shortExtractedPlans, freshInitialState, agent);
							fakeGoals = Agent.moveObjects(objectPositions, freshInitialState, MAState.getPath(shortExtractedPlans, agent));
							if (fakeGoals == null)
								return null;

						}

						MAState checkingState = freshInitialState.clone();
						checkingState.goals.clear();
						checkingState.goals.putAll(fakeGoals);
						int missingFakeGoals = 0;
						for (Position fakeGoalPos : checkingState.goals.keySet()) {
							if (!checkingState.isGoalSatisfied(fakeGoalPos)) {
								missingFakeGoals++;
							}
						}

						if (Agent.planToActions(helperPlan)[agentId] != moves && missingFakeGoals == 0) {
							List<MAState> newAlreadyPlanned = new ArrayList<>(helperPlan);
							int[] newActionsPerformed = Agent.planToActions(newAlreadyPlanned);
							MAState newInitialState = newAlreadyPlanned.get(newActionsPerformed[agentId]);
							newInitialState.goals.put(goalPos, goalType);
							Strategy newStrategy = new Strategy.StrategyBestFirst(
								new Heuristic.AStar(newInitialState, newInitialState.color.get(agent)));
							return Agent.searchIgnore(agent, newAlreadyPlanned, newStrategy, goalPos, newActionsPerformed,
								oldPath);
						}

						// If nothing needs to move we are done
						if (objectPositions.size() < 1) {
							List<MAState> plan = Agent.solveConflicts(helperPlan, shortExtractedPlans, agent, actionsSEP);
							return plan;
						}

						List<MAState> fastestSASolution = null;
						int fastestAgent = -1;

						MAState orderState = helperPlan.get(helperPlan.size() - 1).clone();
						orderState.goals.clear();
						orderState.goals.putAll(fakeGoals);

						List<Position> goalOrder = Main.orderGoals(orderState);

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
								continue;
							}

							int helperAgentId = Character.getNumericValue(agentType);
							String helperAgentColor = initialState.color.get(agentType);

							int helperMoves = actionsPerformed[helperAgentId];

							assert helperMoves < helperPlan.size() : "moves must be within helperPlan";

							List<MAState> fakeHelperPlan = helperPlan.get(helperPlan.size() - 1).clone()
								.extractPlanWithInitial();

							MAState state = fakeHelperPlan.get(helperMoves);
							state.goals.clear();
							state.goals.putAll(prevGoals);
							state.goals.put(fakeGoalPos, fakeGoalType);

							List<MAState> saSolution = Agent.searchIgnore(agentType, fakeHelperPlan,
								new Strategy.StrategyBestFirst(new Heuristic.WeightedAStar(state, helperAgentColor, 1)), fakeGoalPos,
								actionsPerformed, MAState.getPath(shortExtractedPlans, agent));

							if (fastestSASolution == null || (saSolution != null && saSolution.size() < fastestSASolution.size())) {
								fastestSASolution = saSolution;
								fastestAgent = helperAgentId;
							}
						}

						if (fastestSASolution == null) {
							return null;
						}

						actionsPerformed = Agent.planToActions(fastestSASolution);

						helperPlan = new ArrayList<>(Collections.singletonList(alreadyPlanned.get(0)));

						MAState prevState = helperPlan.get(0);
						for (MAState state : fastestSASolution.subList(1, fastestSASolution.size())) {
							helperPlan.add(new MAState(prevState, state.actions));
							prevState = helperPlan.get(helperPlan.size() - 1);
						}
					}
					extractedPlans = Agent.extendSolution(helperPlan, extractedPlans);
					return extractedPlans;
				}
			}

			strategy.addToExplored(leafState);

			boolean insideList = leafState.g()+1 < (alreadyPlanned.size());
			int numAgents = leafState.numAgents;

			
			MAState state;
			if (insideList && leafState.g() >= moves) {
				List<Command> actions = alreadyPlanned.get(leafState.g()+1).actions;
				if (!leafState.isApplicable(actions)) {
					continue;
				}

				state = new MAState(leafState, actions);
			} else {
				state = new MAState(alreadyPlanned.get(0), Collections.nCopies(numAgents, new Command.NoOp()));
			}

			for (MAState n : leafState.getExpandedStatesIgnore(agent,state)) {
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

		for (int i = 0; i < extractedPlan.size(); i++) {

			if (extractedPlan.get(i).actions == null)
				continue;

			List<Command> actions = extractedPlan.get(i).actions;

			for (int j = 0; j < numAgents; j++) {
				if (!(actions.get(j) instanceof Command.NoOp)){
					actionsPerformed[j] = i;
				}
			}
		}

		return actionsPerformed;
	}

	public static Map<Position, Character> moveObjects(Map<Position,Character> objectPositions, MAState state, Set<Position> path) {
		HashMap<Position, Character> fakeGoals = new HashMap<>();

		// Flood-fill. Update shortest distances from each goal in turn using BFS
		for (Map.Entry<Position,Character> object : objectPositions.entrySet()) {
			Position objectPosition = object.getKey();
			char objectName = object.getValue();
			ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(objectPosition));
			Set<Position> alreadyVisited = new HashSet<>();

			while (true) {
				if (frontier.isEmpty())
					return null;

				Position p = frontier.pop();

				if (p.within(0, 0, state.height - 1, state.width - 1) && !state.walls.contains(p)
						&& !alreadyVisited.contains(p)) {

					// We do not want to overwrite existing goals, except when empty goals of the correct type
					// as these goals are likely the correct goal anyway.
					if (!path.contains(p) && (!state.goals.containsKey(p) || state.goals.get(p).equals(objectName) && !state.isGoalSatisfied(p))) {

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

						fakeGoals.put(p, objectName);

						break;
					}

					alreadyVisited.add(p);
					for (Command.Dir dir : Command.Dir.values()) {
						frontier.add(p.add(dir));
					}
				}
			}
		}
		assert fakeGoals.values().stream().allMatch(Character::isLetterOrDigit);

		return fakeGoals;
	}

	public static ArrayList<MAState> saSearch(MAState initialState, Strategy strategy) {

		strategy.addToFrontier(initialState);

		Character agent = initialState.agents.values().iterator().next();

		long iterations = 0;
		while (true) {
			if (strategy.frontierIsEmpty()) {
				return null;
			}

			MAState leafState = strategy.getAndRemoveLeaf();

			// if (iterations % 10000 == 0)
				// System.err.println(String.join("\t", strategy.searchStatus(), strategy.describeState(leafState),
			// 			Memory.stringRep()));

			if (leafState.isGoalState()) {
				return leafState.extractPlanWithInitial();
			}

			// Pick out state based on leafState.g() and alreadyPlanned list.
			// If index out of bounds just get last state as nothing will change yet.

			strategy.addToExplored(leafState);

			// Get nextState from alreadyPlanned
			// vs construct nextState from leafState and move from alreadyPlanned.

			// Need to identify agents by character rather than position
			// as position changes across states.

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

			for (int j = 0; j < numAgents; j++) {

				if (newActionsPerformed[j] < i) {
					List<Command> testActions = new ArrayList<>(s1);
					Command currentAction = newPlan.get(newActionsPerformed[j]).actions.get(j);

					if (!(currentAction instanceof Command.NoOp)) {

						testActions.set(j, currentAction);
						if (oldState.isApplicable(testActions)) {
							newActions.set(j, currentAction);
							newActionsPerformed[j]++;
						}
					}
				}
			}
			MAState prevState = oldState.parent;
			
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

				if (!(currentAction instanceof Command.NoOp)) {
					newActions.set(j, currentAction);
					newActionsPerformed[j]++;
				}
			}
			MAState prevState = mergedPlan.get(mergedPlan.size() - 1);
			mergedPlan.add(new MAState(prevState, newActions));

		}

		return mergedPlan;

	}

	public static List<MAState> solveConflicts(List<MAState> alreadyPlanned, List<MAState> newPlan, char agent,
			int[] newActionsPlanned) {


		int numAgents = alreadyPlanned.get(0).numAgents;

		int[] actionsPerformed = Agent.planToActions(alreadyPlanned);

		int agentID = Character.getNumericValue(agent);

		// adding a state to the end of newPlan with all NoOp actions
		if (newPlan.size() < 1)
			return alreadyPlanned;

		MAState last = newPlan.get(newPlan.size() - 1);
		newPlan.add(new MAState(last, Collections.nCopies(numAgents, new Command.NoOp())));

		ArrayList<MAState> mergedPlan = new ArrayList<>(alreadyPlanned.subList(0, actionsPerformed[agentID] + 1));

		MAState state = null;
		List<Command> actions = null;
		int aPcount = actionsPerformed[agentID] + 1;
		int newCount = 0;
		int noOpNum = 0;

		while (true) {

			if (newCount >= (newPlan.size() - 1) && aPcount >= (alreadyPlanned.size() - 1)) {
				return mergedPlan;
			}

			if (aPcount < alreadyPlanned.size()) {
				state = alreadyPlanned.get(aPcount);
				aPcount++;
			} else {
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


	public static Map<Position,Character> lookAhead(List<MAState> plan, MAState freshInitialState, char agent) {
		Map<Position,Character> objectPositions = new HashMap<>();

		for (MAState state : plan) {

			int i = Character.getNumericValue(agent);
			Command action = state.actions.get(i);
			Position tempAgentPos = freshInitialState.getPositionOfAgent(Integer.toString(i).charAt(0));
			if (tempAgentPos == null) {
				continue;
			}

			if (action instanceof Command.NoOp) {
				continue;
			}
			if (action instanceof Command.Move) {

				Command.Move move = (Command.Move) action;
				tempAgentPos = tempAgentPos.add(move.getAgentDir());

				if (freshInitialState.boxAt(tempAgentPos)) {
					char b = freshInitialState.boxes.get(tempAgentPos);
					objectPositions.put(tempAgentPos, b);
				}

				if (freshInitialState.agentAt(tempAgentPos)) {
					char a = freshInitialState.agents.get(tempAgentPos);
					objectPositions.put(tempAgentPos, a);
				}

			}
			if (action instanceof Command.Push) {

				Command.Push push = (Command.Push) action;

				tempAgentPos = tempAgentPos.add(push.getAgentDir());

				Position tempBoxPos = tempAgentPos.add(push.getBoxDir());

				if (freshInitialState.boxAt(tempBoxPos)) {
					char b = freshInitialState.boxes.get(tempBoxPos);
					objectPositions.put(tempBoxPos, b);
				}

				if (freshInitialState.agentAt(tempBoxPos)) {
					char a = freshInitialState.agents.get(tempBoxPos);
					objectPositions.put(tempBoxPos, a);
				}
			}
			if (action instanceof Command.Pull) {

				Command.Pull pull = (Command.Pull) action;

				tempAgentPos = tempAgentPos.add(pull.getAgentDir());
				// Position tempBoxPos = tempAgentPos.add(pull.getBoxDir());

				if (freshInitialState.boxAt(tempAgentPos)) {
					char b = freshInitialState.boxes.get(tempAgentPos);
					objectPositions.put(tempAgentPos, b);
				}

				if (freshInitialState.agentAt(tempAgentPos)) {
					char a = freshInitialState.agents.get(tempAgentPos);
					objectPositions.put(tempAgentPos, a);
				}
			}
			List<Command> newActions = new ArrayList<>(Collections.nCopies(state.numAgents, new Command.NoOp()));
			newActions.set(i, action);
		
			freshInitialState = new MAState(freshInitialState, newActions, true);
		}
		assert objectPositions.values().stream().allMatch(Character::isLetterOrDigit);
		return objectPositions;
	}
}
