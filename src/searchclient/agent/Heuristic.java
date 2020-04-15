package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Position;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Heuristic implements Comparator<MAState> {
    private final List<List<Map<Character, Integer>>> distToGoal;
    private final HashSet<Character> chars = new HashSet<>();

    public Heuristic(MAState initialState) {
        // Here's a chance to pre-process the static parts of the level.


        // Initialise distance map
        this.distToGoal = new ArrayList<>(initialState.height);
        for (int i = 0; i < initialState.height; i++) {
            ArrayList<Map<Character, Integer>> row = new ArrayList<>(initialState.width);

            for (int j = 0; j < initialState.width; j++)
                row.add(new HashMap<>());
            this.distToGoal.add(row);
        }

        // Update shortest distances from each goal in turn using BFS
        for (Map.Entry<Position, Character> goal : initialState.goals.entrySet()) {
            Position pos = goal.getKey();
            char g = goal.getValue();
            this.chars.add(g);

            ArrayDeque<Position> frontier = new ArrayDeque<>(Collections.singletonList(pos));
            ArrayDeque<Integer> dists = new ArrayDeque<>(Collections.singletonList(0));

            while (!frontier.isEmpty()) {
                Position p = frontier.pop();
                int dist = dists.pop();

                int row = p.getRow(), col = p.getCol();

                if (p.within(0, 0, initialState.height - 1, initialState.width - 1) &&
                        this.distToGoal.get(row).get(col).getOrDefault(g, Integer.MAX_VALUE) > dist && !initialState.walls.contains(p)) {

                    this.distToGoal.get(row).get(col).put(g, dist);

                    for (Command.Dir dir : Command.Dir.values()) {
                        frontier.add(p.add(dir));
                        dists.add(dist + 1);
                    }
                }
            }
        }

        // Print the distance fields
        for (Character c : this.chars) {
            System.err.println(c);
            System.err.println(this.distToGoal.stream().map(
                    row -> row.stream().map(
                            x -> String.format("%3d", x.getOrDefault(c, 0))
                    ).collect(Collectors.joining(" "))
            ).collect(Collectors.joining("\n")));
            System.err.println();
        }
    }

    public int h(MAState n) {
        int totalDistance = 0;

        for (Map.Entry<Position, Character> entry : n.boxes.entrySet()) {
            Position boxPos = entry.getKey();
            Character b = entry.getValue();

            totalDistance += this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0);
        }

        int minAgentDist = Integer.MAX_VALUE;
        for (Map.Entry<Position, Character> agent : n.agents.entrySet()) {
            Position agentPos = agent.getKey();
            Character agentType = agent.getValue();

            for (Map.Entry<Position, Character> box : n.boxes.entrySet()) {
                Position boxPos = box.getKey();
                Character boxType = box.getValue();

                if (this.chars.contains(boxType) && n.color.get(agentType).equals(n.color.get(boxType))) {
                    int dist = Position.distance(agentPos, boxPos);

                    if (dist < minAgentDist)
                        minAgentDist = dist;
                }
            }
        }

        totalDistance += minAgentDist - 1;

        return totalDistance;
    }

    public abstract int f(MAState n);

    @Override
    public int compare(MAState n1, MAState n2) {
        return this.f(n1) - this.f(n2);
    }

    public static class AStar extends Heuristic {
        public AStar(MAState initialState) {
            super(initialState);
        }

        @Override
        public int f(MAState n) {
            return n.g() + this.h(n);
        }

        @Override
        public String toString() {
            return "A* evaluation";
        }
    }

    public static class WeightedAStar extends Heuristic {
        private int W;

        public WeightedAStar(MAState initialState, int W) {
            super(initialState);
            this.W = W;
        }

        @Override
        public int f(MAState n) {
            return n.g() + this.W * this.h(n);
        }

        @Override
        public String toString() {
            return String.format("WA*(%d) evaluation", this.W);
        }
    }

    public static class Greedy extends Heuristic {
        public Greedy(MAState initialState) {
            super(initialState);
        }

        @Override
        public int f(MAState n) {
            return this.h(n);
        }

        @Override
        public String toString() {
            return "Greedy evaluation";
        }
    }
}
