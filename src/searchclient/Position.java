package searchclient;

import java.util.Comparator;
import java.util.Objects;

public class Position implements Comparable<Position> {
	private final int l;
	private final int r;

	public Position(int l, int r) {
		this.l = l;
		this.r = r;
	}

	public int getL() {
		return this.l;
	}

	public int getR() {
		return this.r;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || this.getClass() != o.getClass()) return false;
		Position position = (Position) o;
		return this.l == position.l &&
			this.r == position.r;
	}

	public Position add(Command.Dir dir) {
		return new Position(this.l + dir.getDeltaRow(), this.r + dir.getDeltaCol());
	}

	@Override
	public String toString() {
		return String.format("<%s, %s>", this.l, this.r);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.l, this.r);
	}

	private static final Comparator<Position> comparator = Comparator.comparingInt(Position::getL).thenComparingInt(Position::getR);

	@Override
	public int compareTo(Position other) {
		return comparator.compare(this, other);
	}
}
