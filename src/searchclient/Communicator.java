package searchclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Communicator {

	private final BufferedReader serverReader;
	private final PrintStream serverWriter;

	public Communicator(BufferedReader serverReader, PrintStream serverWriter) {
		this.serverReader = serverReader;
		this.serverWriter = serverWriter;
	}

	public List<Boolean> send(List<Command> jointAction) throws IOException {
		this.serverWriter.println(
			jointAction.stream()
				.map(Command::toString)
				.collect(Collectors.joining(";")));

		return Arrays.stream(this.serverReader.readLine().split(";")).map(Boolean::parseBoolean).collect(Collectors.toList());
	}
}
