import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class GitTreeLeaf {
	private byte[] mode;
	private Path path;
	private String sha;

	public GitTreeLeaf(byte[] mode2, Path path2, String sha2) {
		this.mode = mode2;
		this.path = path2;
		this.sha = sha2;
	}

	public static Tuple<Integer, GitTreeLeaf> treeParseOne(byte[] raw, int start) {
		ByteBuffer buffer = ByteBuffer.wrap(raw);

		// Set the buffer position to the start index
		buffer.position(start);

		// Find the space terminator of the mode
		int x = findByteIndex(buffer, (byte) ' ');

		if (x - start != 5 && x - start != 6) {
			throw new IllegalArgumentException("Invalid mode length");
		}

		// Read the mode
		byte[] mode = Arrays.copyOfRange(raw, start, x);
		if (mode.length == 5) {
			// Normalize to six bytes.
			byte[] normalizedMode = new byte[6];
			normalizedMode[0] = ' ';
			System.arraycopy(mode, 0, normalizedMode, 1, 5);
			mode = normalizedMode;
		}

		// Find the NULL terminator of the path
		int y = findByteIndex(buffer, (byte) '\0');

		// Read the path
		String path = new String(raw, x + 1, y - x - 1, StandardCharsets.UTF_8);

		// Read the SHA and convert to a hex string
		byte[] shaBytes = Arrays.copyOfRange(raw, y + 1, y + 21);
		String sha = bytesToHex(shaBytes);

		return new Tuple<Integer, GitTreeLeaf>(y + 21, new GitTreeLeaf(mode, Paths.get(path), sha));
	}

	private static int findByteIndex(ByteBuffer buffer, byte target) {
		for (int i = buffer.position(); i < buffer.limit(); i++) {
			if (buffer.get(i) == target) {
				return i;
			}
		}
		throw new IllegalArgumentException("Byte not found");
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	public byte[] getMode() {
		return this.mode;
	}

	public Path getPath() {
		return this.path;
	}

	public String getSha() {
		return this.sha;
	}

}