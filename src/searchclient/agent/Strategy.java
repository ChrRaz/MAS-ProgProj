package searchclient.agent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.PriorityQueue;

public abstract class Strategy {
    private HashSet<State> explored;
    private final long startTime;

    public Strategy() {
        this.explored = new HashSet<>();
        this.startTime = System.currentTimeMillis();
    }

    public void addToExplored(State n) {
        this.explored.add(n);
    }

    public boolean isExplored(State n) {
        return this.explored.contains(n);
    }

    public int countExplored() {
        return this.explored.size();
    }

    public String searchStatus() {
        return String.format("#Explored: %,6d, #Frontier: %,6d (%.2f%%), #Generated: %,6d, Time: %3.2f s (%,.2f/s)",
                this.countExplored(),
                this.countFrontier(),
                100f * this.countFrontier() / (this.countExplored() + this.countFrontier()),
                this.countExplored() + this.countFrontier(),
                this.timeSpent(),
                this.countExplored() / this.timeSpent());
    }

    public float timeSpent() {
        return (System.currentTimeMillis() - this.startTime) / 1000f;
    }

    public abstract State getAndRemoveLeaf();

    public abstract void addToFrontier(State n);

    public abstract boolean inFrontier(State n);

    public abstract int countFrontier();

    public abstract boolean frontierIsEmpty();

    public abstract String describeState(State n);

    @Override
    public abstract String toString();

    public static class StrategyBFS extends Strategy {
        private ArrayDeque<State> frontier;
        private HashSet<State> frontierSet;

        public StrategyBFS() {
            super();
            this.frontier = new ArrayDeque<>();
            this.frontierSet = new HashSet<>();
        }

        @Override
        public State getAndRemoveLeaf() {
            State n = this.frontier.pollFirst();
            this.frontierSet.remove(n);
            return n;
        }

        @Override
        public void addToFrontier(State n) {
            this.frontier.addLast(n);
            this.frontierSet.add(n);
        }

        @Override
        public int countFrontier() {
            return this.frontier.size();
        }

        @Override
        public boolean frontierIsEmpty() {
            return this.frontier.isEmpty();
        }

        @Override
        public boolean inFrontier(State n) {
            return this.frontierSet.contains(n);
        }

        @Override
        public String describeState(State n) {
            return String.format("[g: %d]", n.g());
        }

        @Override
        public String toString() {
            return "Breadth-first Search";
        }
    }

    public static class StrategyDFS extends Strategy {
        private ArrayDeque<State> frontier;
        private HashSet<State> frontierSet;

        public StrategyDFS() {
            super();
            this.frontier = new ArrayDeque<>();
            this.frontierSet = new HashSet<>();
        }

        @Override
        public State getAndRemoveLeaf() {
            State n = this.frontier.pollLast();
            this.frontierSet.remove(n);
            return n;
        }

        @Override
        public void addToFrontier(State n) {
            this.frontier.addLast(n);
            this.frontierSet.add(n);
        }

        @Override
        public int countFrontier() {
            return this.frontier.size();
        }

        @Override
        public boolean frontierIsEmpty() {
            return this.frontier.isEmpty();
        }

        @Override
        public boolean inFrontier(State n) {
            return this.frontierSet.contains(n);
        }

        @Override
        public String describeState(State n) {
            return String.format("[g: %d]", n.g());
        }

        @Override
        public String toString() {
            return "Depth-first Search";
        }
    }

    public static class StrategyBestFirst extends Strategy {
        private PriorityQueue<State> frontier;
        private HashSet<State> frontierSet;
        private Heuristic heuristic;

        public StrategyBestFirst(Heuristic h) {
            super();
            this.heuristic = h;
            this.frontier = new PriorityQueue<>(this.heuristic);
            this.frontierSet = new HashSet<>();
        }

        @Override
        public State getAndRemoveLeaf() {
            State n = this.frontier.poll();
            this.frontierSet.remove(n);
            return n;
        }

        @Override
        public void addToFrontier(State n) {
            this.frontier.add(n);
            this.frontierSet.add(n);
        }

        @Override
        public int countFrontier() {
            return this.frontier.size();
        }

        @Override
        public boolean frontierIsEmpty() {
            return this.frontier.isEmpty();
        }

        @Override
        public boolean inFrontier(State n) {
            return this.frontierSet.contains(n);
        }

        @Override
        public String describeState(State n) {
            return String.format("[f: %d, g: %d, h: %d]", this.heuristic.f(n), n.g(), this.heuristic.h(n));
        }

        @Override
        public String toString() {
            return "Best-first Search using " + this.heuristic.toString();
        }
    }
}
