package searchclient;

public abstract class Command {
	public enum Dir {
		N(-1, 0),
		W(0, -1),
		E(0, 1),
		S(1, 0);

		private final int deltaRow, deltaCol;

		Dir(int deltaRow, int deltaCol) {
			this.deltaRow = deltaRow;
			this.deltaCol = deltaCol;
		}

		public int getDeltaRow() {
			return this.deltaRow;
		}

		public int getDeltaCol() {
			return this.deltaCol;
		}
	}

	public static class Move extends Command {
		private final Dir agentDir;

		public Move(Dir agentDir) {
			this.agentDir = agentDir;
		}

		public Dir getAgentDir() {
			return this.agentDir;
		}

		@Override
		public String toString() {
			return String.format("Move(%s)", this.agentDir);
		}
	}

	public static class Push extends Command {
		private final Dir agentDir;
		private final Dir boxDir;

		public Push(Dir agentDir, Dir boxDir) {
			this.agentDir = agentDir;
			this.boxDir = boxDir;
		}

		public Dir getAgentDir() {
			return this.agentDir;
		}

		public Dir getBoxDir() {
			return this.boxDir;
		}

		@Override
		public String toString() {
			return String.format("Push(%s,%s)", this.agentDir, this.boxDir);
		}
	}

	public static class Pull extends Command {
		private final Dir agentDir;
		private final Dir boxDir;

		public Pull(Dir agentDir, Dir boxDir) {
			this.agentDir = agentDir;
			this.boxDir = boxDir;
		}

		public Dir getAgentDir() {
			return this.agentDir;
		}

		public Dir getBoxDir() {
			return this.boxDir;
		}

		@Override
		public String toString() {
			return String.format("Pull(%s,%s)", this.agentDir, this.boxDir);
		}

	}
}
