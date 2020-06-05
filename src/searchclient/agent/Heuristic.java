package searchclient.agent;

import searchclient.Command;
import searchclient.MAState;
import searchclient.Position;

import java.util.*;
import java.util.stream.Collectors;

public abstract class Heuristic implements Comparator<MAState> {
    private final List<List<Map<Character, Integer>>> distToGoal;
    private final HashSet<Character> chars = new HashSet<>();
    private final int numBoxes;
    private final int numAgents;
    private final String color;
    private final Character type;
    private final Map<Character, List<Map.Entry<Position, Character>>> goals;
    public MAState contructerState = null;

    public Heuristic(MAState initialState, String color){   
        this(initialState, color, null);
    }

    public Heuristic(MAState initialState, String color,Character type) {
        this.contructerState = initialState;
        // Here's a chance to pre-process the static parts of the level.
        this.type = type;
        this.color = color;
        this.numBoxes = initialState.boxes.size();
        this.numAgents = initialState.agents.size();
        this.goals = initialState.goals.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue));
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

                if (p.within(0, 0, initialState.height - 1, initialState.width - 1)
                        && this.distToGoal.get(row).get(col).getOrDefault(g, Integer.MAX_VALUE) > dist
                        && !initialState.walls.contains(p)) {

                    this.distToGoal.get(row).get(col).put(g, dist);

                    for (Command.Dir dir : Command.Dir.values()) {
                        frontier.add(p.add(dir));
                        dists.add(dist + 1);
                    }
                }
            }
        }

        // Print the distance fields
        // for (Character c : this.chars) {
        // System.err.println(c);
        // System.err.println(this.distToGoal.stream().map(
        // row -> row.stream().map(
        // x -> String.format("%3d", x.getOrDefault(c, 0))
        // ).collect(Collectors.joining(" "))
        // ).collect(Collectors.joining("\n")));
        // System.err.println();
        // }
    }

    public int h(MAState n) {
        int totalDistance = 0;

        for (Map.Entry<Position, Character> entry : n.agents.entrySet()) {
            Position agentPos = entry.getKey();
            Character a = entry.getValue();

            if (this.color.equals(n.color.get(a))) {
                totalDistance += this.distToGoal.get(agentPos.getRow()).get(agentPos.getCol()).getOrDefault(a, 0);
            }

        }

        // for (Map.Entry<Position, Character> entry : n.boxes.entrySet()) {
        //     Position boxPos = entry.getKey();
        //     Character b = entry.getValue();
        //     if (this.color.equals(n.color.get(b))) {
        //         totalDistance += this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0);
        //     }

        // }

        // Group goals by type
        // Map<Character, List<Map.Entry<Position, Character>>> goals = n.goals.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue));
        Map<Character, PriorityQueue<Integer>> boxDists = new HashMap<>();

        for (Character goalType : this.goals.keySet()) {
            boxDists.put(goalType, new PriorityQueue<>(Comparator.reverseOrder()));
        }

        // Find #goals closest boxes by repeatedly polling out the most expensive ones
        for (Map.Entry<Position, Character> entry : n.boxes.entrySet()) {
            Position boxPos = entry.getKey();
            Character b = entry.getValue();

            if (this.color.equals(n.color.get(b)) && boxDists.containsKey(b)) {
                PriorityQueue<Integer> dists = boxDists.get(b);
                // System.err.println("b is " + b);
                dists.add(this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0));
                if (dists.size() > this.goals.get(b).size())
                    dists.poll();
            }
        }

        for (Map.Entry<Position, List<Character>> entry : n.backUpBoxes.entrySet()) {
            Position boxPos = entry.getKey();
            List<Character> bList = entry.getValue();
            for(Character b : bList){
                if (this.color.equals(n.color.get(b)) && boxDists.containsKey(b)) {
                    PriorityQueue<Integer> dists = boxDists.get(b);
                    // System.err.println("b is " + b);
                    dists.add(this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0));
                    if (dists.size() > this.goals.get(b).size())
                    dists.poll();
                }
            }
        }


        // for (Character goalType : goals.keySet()) {
        //     PriorityQueue<Integer> dists = boxDists.get(goalType);

        //     for(int i=0;i<dists.size() - goals.get(goalType).size();i++){
        //         dists.poll();
        //     }
        // }




        for (PriorityQueue<Integer> dists : boxDists.values()) {
            for (int dist : dists) {
                totalDistance += dist;
            }
        }

        // if(type!=null){

        //     for (Map.Entry<Position, Character> agent : n.agents.entrySet()) {
        //         Position agentPos = agent.getKey();
        //         Character agentType = agent.getValue();
                
        //         int minAgentDist = Integer.MAX_VALUE;
        //         if (!this.color.equals(n.color.get(agentType)))
        //         continue;

        //         for (Map.Entry<Position, Character> box : n.boxes.entrySet()) {
        //             Position boxPos = box.getKey();
        //             Character boxType = box.getValue();
                    
        //             // box not of type
        //             if(!boxType.equals(type))
        //             continue;
                    
        //             if (!this.color.equals(n.color.get(boxType)))
        //             continue;
                    
        //             // box does not have a goal
        //             if (!n.goals.containsValue(boxType))
        //             continue;
                    
        //             // Ignore boxes already on goals
        //             if (n.goals.containsKey(boxPos) && n.goals.get(boxPos).equals(boxType))
        //             continue;
                     
        //             if (this.chars.contains(boxType) && n.color.get(agentType).equals(n.color.get(boxType))) {
                        
        //                 int boxDist = distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).get(boxType);
        //                 int agentDist = distToGoal.get(agentPos.getRow()).get(agentPos.getCol()).get(boxType);
                        
        //                 int dist = Math.abs(boxDist-agentDist);
        //                 // int dist = Position.distance(agentPos, boxPos);

        //                 if (dist < minAgentDist)
        //                 minAgentDist = dist;
        //             }
        //         }
        //         if (minAgentDist != Integer.MAX_VALUE)
        //         totalDistance += minAgentDist - 1;
        //     }
        // }
        int temp = 0;
        for (List<Map.Entry<Position, Character>> value : this.goals.values()) {
            temp += value.size();
            for (Map.Entry<Position, Character> goal : value) {
                Position goalPos = goal.getKey();
                Character goalType = goal.getValue();
                if(n.boxAt(goalPos) && n.boxes.get(goalPos).equals(goalType)){
                    temp -=1;
                }
            }
        }
        if(temp==0)
        return 0;


        return totalDistance;
    }

    public void printH(MAState n) {
        int totalDistance = 0;

        for (Map.Entry<Position, Character> entry : n.agents.entrySet()) {
            Position agentPos = entry.getKey();
            Character a = entry.getValue();

            if (this.color.equals(n.color.get(a))) {
                totalDistance += this.distToGoal.get(agentPos.getRow()).get(agentPos.getCol()).getOrDefault(a, 0);
                
            }

        }

        for (Map.Entry<Position, Character> entry : n.boxes.entrySet()) {
            Position boxPos = entry.getKey();
            Character b = entry.getValue();
            if (this.color.equals(n.color.get(b))) {
                totalDistance += this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0);
            }

        }

        // Group goals by type
        Map<Character, List<Map.Entry<Position, Character>>> goals = n.goals.entrySet().stream().collect(Collectors.groupingBy(Map.Entry::getValue));
        Map<Character, PriorityQueue<Integer>> boxDists = new HashMap<>();

        for (Character goalType : goals.keySet()) {
            boxDists.put(goalType, new PriorityQueue<>(Comparator.reverseOrder()));
        }

        // Find #goals closest boxes by repeatedly polling out the most expensive ones
        for (Map.Entry<Position, Character> entry : n.boxes.entrySet()) {
            Position boxPos = entry.getKey();
            Character b = entry.getValue();

            if (this.color.equals(n.color.get(b)) && boxDists.containsKey(b)) {
                PriorityQueue<Integer> dists = boxDists.get(b);
                // System.err.println("b is " + b);
                dists.add(this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0));
                if (dists.size() > goals.get(b).size())
                    dists.poll();
            }
        }

        for (Map.Entry<Position, List<Character>> entry : n.backUpBoxes.entrySet()) {
            Position boxPos = entry.getKey();
            List<Character> bList = entry.getValue();
            for(Character b : bList){
                if (this.color.equals(n.color.get(b)) && boxDists.containsKey(b)) {
                    PriorityQueue<Integer> dists = boxDists.get(b);
                    // System.err.println("b is " + b);
                    dists.add(this.distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).getOrDefault(b, 0));
                    if (dists.size() > goals.get(b).size())
                    dists.poll();
                }
            }
        }
        for(Map.Entry<Character, PriorityQueue<Integer>> e :boxDists.entrySet()){
            Character type = e.getKey();
            // System.err.println("the goal " + type + " has the dists:");
            // for(int d : e.getValue())
            //     System.err.println(d);
        }

        for (PriorityQueue<Integer> dists : boxDists.values()) {
            for (int dist : dists) {
                totalDistance += dist;
            }
        }
        if(type!=null){

            for (Map.Entry<Position, Character> agent : n.agents.entrySet()) {
                Position agentPos = agent.getKey();
                Character agentType = agent.getValue();
                
                int minAgentDist = Integer.MAX_VALUE;
                if (!this.color.equals(n.color.get(agentType)))
                continue;

                for (Map.Entry<Position, Character> box : n.boxes.entrySet()) {
                    Position boxPos = box.getKey();
                    Character boxType = box.getValue();
                    
                    // box not of type
                    if(!boxType.equals(type))
                    continue;
                    
                    if (!this.color.equals(n.color.get(boxType)))
                    continue;
                    
                    // box does not have a goal
                    if (!n.goals.containsValue(boxType))
                    continue;
                    
                    // Ignore boxes already on goals
                    if (n.goals.containsKey(boxPos) && n.goals.get(boxPos).equals(boxType))
                    continue;
                    
                    if (this.chars.contains(boxType) && n.color.get(agentType).equals(n.color.get(boxType))) {
                        int boxDist = distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).get(boxType);
                        int agentDist = distToGoal.get(agentPos.getRow()).get(agentPos.getCol()).get(boxType);
                        
                        int dist = Math.abs(boxDist-agentDist);
                        
                        if (dist < minAgentDist)
                        minAgentDist = dist;
                    }
                }
                // System.err.println("agent " + agent + " was " + minAgentDist + " from a box of type " + type);
                // if (minAgentDist != Integer.MAX_VALUE)
            }
        }
        // System.err.println("type = " + type);
    }


    public Position bestBox(MAState n, Character type){
        
        Position bestPos = null;
        int bestDist = 100000;
        for(Map.Entry<Position,Character> box : n.boxes.entrySet()){
            Character boxType = box.getValue();
            Position boxPos = box.getKey();
            if(type.equals(boxType)){
                int boxDist = distToGoal.get(boxPos.getRow()).get(boxPos.getCol()).get(type);
                if(boxDist<bestDist && boxDist!=0){
                    bestDist = boxDist;
                    bestPos = boxPos;
                }
            }
        }

        return bestPos;
    }

    public abstract int f(MAState n);

    @Override
    public int compare(MAState n1, MAState n2) {
        return this.f(n1) - this.f(n2);
    }

    public static class AStar extends Heuristic {
        public AStar(MAState initialState, String color) {
            super(initialState, color);
        }

        @Override
        public int f(MAState n) {
            return n.cost() + this.h(n);
        }

        @Override
        public String toString() {
            return "A* evaluation";
        }
    }

    public static class WeightedAStar extends Heuristic {
        private int W;

        public WeightedAStar(MAState initialState, String color, int W) {
            this(initialState,color, W, null);
        }

        public WeightedAStar(MAState initialState, String color, int W,Character type) {
            super(initialState, color, type);
            this.W = W;
        }

        @Override
        public int f(MAState n) {
            return n.cost() + this.W * this.h(n);
        }

        @Override
        public String toString() {
            return String.format("WA*(%d) evaluation", this.W);
        }
    }

    public static class Greedy extends Heuristic {
        public Greedy(MAState initialState, String color) {
            super(initialState, color);
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
