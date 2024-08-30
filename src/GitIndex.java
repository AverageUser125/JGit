import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class GitIndex {
	private int version;
	private List<GitIndexEntry> entries;

	public static final Map<Integer, String> MODE_TYPE_MAP;
	static {
		MODE_TYPE_MAP = new HashMap<>();
		MODE_TYPE_MAP.put(0b1000, "regular file");
		MODE_TYPE_MAP.put(0b1010, "symlink");
		MODE_TYPE_MAP.put(0b1110, "git link");
	}

	public GitIndex(int version, List<GitIndexEntry> entries) {
		if (entries == null) {
			entries = new ArrayList<>();
		}
		this.version = version;
		this.entries = entries;
	}

	public static GitIndex indexRead(GitRepository repo) throws IOException {
		Path indexFile = GitObjectHelper.repoFile(repo, "index");
		System.out.println(indexFile);
		// New repositories have no index!
		if (!Files.exists(indexFile)) {
			return new GitIndex(2, new ArrayList<>());
		}

		byte[] raw = Files.readAllBytes(indexFile);

		// Read header
		ByteBuffer header = ByteBuffer.wrap(raw, 0, 12).order(ByteOrder.BIG_ENDIAN);
		byte[] signature = new byte[4];
		header.get(signature);
		int version = header.getInt();
		int count = header.getInt();

		// Check signature and version
		if (!new String(signature).equals("DIRC")) {
			throw new IOException("Invalid index file signature");
		}
		if (version != 2) {
			throw new IOException("Only index file version 2 is supported");
		}

		List<GitIndexEntry> entries = new ArrayList<>();
		ByteBuffer content = ByteBuffer.wrap(raw, 12, raw.length - 12).order(ByteOrder.BIG_ENDIAN);

		for (int i = 0; i < count; i++) {
			// Read fields
			long ctimeS = content.getInt() & 0xFFFFFFFFL;
			long ctimeNs = content.getInt() & 0xFFFFFFFFL;
			long mtimeS = content.getInt() & 0xFFFFFFFFL;
			long mtimeNs = content.getInt() & 0xFFFFFFFFL;
			int dev = content.getInt();
			int ino = content.getInt();
			int unused = content.getShort() & 0xFFFF;
			if (unused != 0) {
				throw new IOException("Unused field is not zero");
			}
			int mode = content.getShort();
			int modeType = mode >> 12;
			int modePerms = mode & 0x1FF;
			int uid = content.getInt();
			int gid = content.getInt();
			int fsize = content.getInt();
			byte[] shaBytes = new byte[20];
			content.get(shaBytes);
			String sha = String.format("%040x", new BigInteger(1, shaBytes));
			int flags = content.getShort();
			boolean flagAssumeValid = (flags & 0x8000) != 0;
			boolean flagExtended = (flags & 0x4000) != 0;
			if (flagExtended) {
				throw new IOException("Extended flags are not supported");
			}
			int flagStage = flags & 0x3000;
			int nameLength = flags & 0xFFF;

			// Read name
			byte[] nameBytes;
			if (nameLength < 0xFFF) {
				nameBytes = new byte[nameLength];
				content.get(nameBytes);
				content.get(); // skip null byte
			} else {
				nameBytes = new byte[0xFFF];
				content.get(nameBytes);
				// find null byte and adjust index
				int nullIndex = findNullByteIndex(content.array(), content.position() + 0xFFF);
				nameBytes = Arrays.copyOfRange(content.array(), content.position(), nullIndex);
				content.position(nullIndex + 1);
			}
			String name = new String(nameBytes, "UTF-8");

			// Skip padding bytes
			int align = (8 - (content.position() % 8)) % 8;
			content.position(content.position() + align);

			entries.add(new GitIndexEntry(new Tuple<>((int) ctimeS, (int) ctimeNs),
					new Tuple<>((int) mtimeS, (int) mtimeNs), dev, ino, modeType, modePerms, uid, gid, fsize, sha,
					flagAssumeValid, flagStage, name));
		}

		return new GitIndex(version, entries);
	}

	private static int findNullByteIndex(byte[] array, int start) {
		for (int i = start; i < array.length; i++) {
			if (array[i] == 0x00) {
				return i;
			}
		}
		return -1;
	}

	public int getVersion() {
		return this.version;
	}

	public List<GitIndexEntry> getEntries() {
		return this.entries;
	}

	public static String formatTimestamp(long seconds) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
		return sdf.format(new Date(seconds * 1000L));
	}

	public static String getUsernameById(int uid) {
		// Implement this method to get the username by UID
		return "user" + uid; // Placeholder implementation
	}

	public static String getGroupnameById(int gid) {
		// Implement this method to get the group name by GID
		return "group" + gid; // Placeholder implementation
	}
}
