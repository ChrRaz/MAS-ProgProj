package searchclient;

import java.util.Comparator;
import java.util.Objects;

public class Position implements Comparable<Position> {
	private final int row;
	private final int col;

	public Position(int row, int col) {
		this.row = row;
		this.col = col;
	}

	public static int distance(Position p1, Position p2) {
		return Math.abs(p1.getRow() - p2.getRow()) + Math.abs(p1.getCol() - p2.getCol());
	}

	public int getRow() {
		return this.row;
	}

	public int getCol() {
		return this.col;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;
		Position position = (Position) o;
		return this.row == position.row &&
			this.col == position.col;
	}

	public Position add(Command.Dir dir) {
		return new Position(this.row + dir.getDeltaRow(), this.col + dir.getDeltaCol());
	}

	public boolean within(int minRow, int minCol, int maxRow, int maxCol) {
		return this.getRow() >= minRow && this.getRow() <= maxRow && this.getCol() >= minCol && this.getCol() <= maxCol;
	}

	@Override
	public String toString() {
		return String.format("<%s, %s>", this.row, this.col);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.row, this.col);
	}

	private static final Comparator<Position> comparator = Comparator.comparingInt(Position::getRow).thenComparingInt(Position::getCol);

	@Override
	public int compareTo(Position other) {
		return comparator.compare(this, other);
	}
}
