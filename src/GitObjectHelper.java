import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GitObjectHelper {

	public static GitObject objectRead(GitRepository repo, String sha) throws IOException {
		Path path = repoFile(repo, "objects", sha.substring(0, 2), sha.substring(2));

		if (!Files.isRegularFile(path)) {
			return null;
		}

		byte[] raw;
		try (InputStream fis = new FileInputStream(path.toFile());
				InflaterInputStream iis = new InflaterInputStream(fis)) {
			raw = iis.readAllBytes();
		}

		// Read object type
		int x = indexOf(raw, (byte) ' ');
		if (x == -1) {
			throw new IOException("Malformed object: missing object type");
		}
		String fmt = new String(raw, 0, x);

		// Read and validate object size
		int y = indexOf(raw, (byte) '\0', x);
		if (y == -1) {
			throw new IOException("Malformed object: missing object size separator");
		}
		int size;
		try {
			size = Integer.parseInt(new String(raw, x + 1, y - x - 1));
		} catch (NumberFormatException e) {
			throw new IOException("Malformed object: invalid size", e);
		}
		if (size != raw.length - y - 1) {
			throw new IOException(String.format("Malformed object %s: bad length", sha));
		}

		byte[] data = new byte[size];
		System.arraycopy(raw, y + 1, data, 0, size);

		// Pick constructor
		GitObject gitObject;
		switch (fmt) {
		case "commit":
			gitObject = new GitCommit(data);
			break;
		case "tree":
			gitObject = new GitTree(data);
			break;
		case "tag":
			gitObject = new GitTag(data);
			break;
		case "blob":
			gitObject = new GitBlob(data);
			break;
		default:
			throw new IOException(String.format("Unknown type %s for object %s", fmt, sha));
		}

		// Return object
		return gitObject;
	}

	public static String objectWrite(GitObject obj, GitRepository repo) throws IOException {
		// Serialize object data
		byte[] data = obj.serialize();

		// Add header
		String fmt = obj.getFmt(); // Assuming GitObject has a getFmt() method
		byte[] header = (fmt + " " + data.length + "\0").getBytes();
		byte[] result = new byte[header.length + data.length];
		System.arraycopy(header, 0, result, 0, header.length);
		System.arraycopy(data, 0, result, header.length, data.length);

		// Compute hash
		String sha = computeSha1(result);

		if (repo != null) {
			// Compute path
			Path path = repoFile(repo, "objects", sha.substring(0, 2), sha.substring(2));
			if (!Files.exists(path)) {
				// Ensure the parent directory exists
				Files.createDirectories(path.getParent());

				try (OutputStream fos = new FileOutputStream(path.toFile());
						DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
					dos.write(result);
				}
			}
		}
		return sha;
	}

	public static String objectHash(byte[] data, byte[] fmt, GitRepository repo)
			throws NoSuchAlgorithmException, IOException {
		// Choose constructor according to fmt argument
		GitObject obj;
		String fmtString = new String(fmt);

		switch (fmtString) {
		case "commit":
			// obj = new GitCommit(data);
			// break;
		case "tree":
			// obj = new GitTree(data);
			// break;
		case "tag":
			// obj = new GitTag(data);
			// break;
		case "blob":
			obj = new GitBlob(data);
			break;
		default:
			throw new IOException("Unknown type " + fmtString + "!");
		}

		return objectWrite(obj, repo);
	}

	public static void showRef(GitRepository repo, Map<String, Object> refs, boolean withHash) {
		showRef(repo, refs, withHash, "");
	}

	public static void showRef(GitRepository repo, Map<String, Object> refs) {
		showRef(repo, refs, true, "");
	}

	public static void showRef(GitRepository repo, Map<String, Object> refs, boolean withHash, String prefix) {
		for (Map.Entry<String, Object> entry : refs.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();

			if (value instanceof String) {
				System.out.println(String.format("%s%s%s", withHash ? (String) value + " " : "",
						!prefix.isEmpty() ? prefix + "/" : "", key));
			} else if (value instanceof Map<?, ?>) {
				// Recursive call for nested maps
				@SuppressWarnings("unchecked")
				Map<String, Object> nestedRefs = (Map<String, Object>) value;
				showRef(repo, nestedRefs, withHash, prefix + (!prefix.isEmpty() ? "/" : "") + key);
			}
		}
	}

	public static Map<String, Object> refList(GitRepository repo, String path) throws IOException {
		if (path == null) {
			path = repoFile(repo, "refs").toString();
		}

		Map<String, Object> ret = new LinkedHashMap<>();

		File dir = new File(path);
		if (!dir.exists() || !dir.isDirectory()) {
			throw new IOException("Directory does not exist: " + path);
		}

		// Git shows refs sorted, so we use a TreeMap to sort the output
		File[] files = dir.listFiles();
		if (files == null) {
			throw new IOException("Unable to list files in directory: " + path);
		}

		TreeMap<String, File> sortedFiles = new TreeMap<>();
		for (File file : files) {
			sortedFiles.put(file.getName(), file);
		}

		for (Map.Entry<String, File> entry : sortedFiles.entrySet()) {
			File file = entry.getValue();
			if (file.isDirectory()) {
				ret.put(file.getName(), refList(repo, file.getAbsolutePath()));
			} else {
				String resolved = refResolve(repo, file.getAbsolutePath());
				if (resolved != null) {
					ret.put(file.getName(), resolved);
				} else {
					throw new IOException("Failed to resolve reference for: " + file.getAbsolutePath());
				}
			}
		}

		return ret;
	}

	public static String refResolve(GitRepository repo, String ref) throws IOException {
		Path path = repoFile(repo, ref);

		// Sometimes, an indirect reference may be broken. This is normal
		// in one specific case: we're looking for HEAD on a new repository
		// with no commits. In that case, .git/HEAD points to "ref:
		// refs/heads/main", but .git/refs/heads/main doesn't exist yet.
		if (Files.notExists(path)) {
			return null;
		}

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String data = reader.readLine();

			if (data == null) {
				return null;
			}

			// If the reference is another reference, resolve it recursively
			if (data.startsWith("ref: ")) {
				return refResolve(repo, data.substring(5));
			} else {
				return data;
			}
		}
	}

	public static List<String> objectResolve(GitRepository repo, String name) throws Exception {
		List<String> candidates = new ArrayList<>();
		Pattern hashRE = Pattern.compile("^[0-9A-Fa-f]{4,40}$");

		// Empty string? Abort.
		if (name == null || name.trim().isEmpty()) {
			return null;
		}

		// Head is nonambiguous
		if ("HEAD".equals(name)) {
			String head = refResolve(repo, "HEAD");
			if (head == null) {
				throw new IllegalStateException("No HEAD found! Please create a commit first");
			}
			return List.of(head);
		}
		// If it's a hex string, try for a hash.
		if (hashRE.matcher(name).matches()) {
			// This may be a hash, either small or full.
			name = name.toLowerCase();
			String prefix = name.substring(0, 2);
			String path = repoDir(repo, false, "objects", prefix);
			if (path != null) {
				String rem = name.substring(2);
				File dir = new File(path);
				String[] files = dir.list();
				if (files != null) {
					for (String f : files) {
						if (f.startsWith(rem)) {
							candidates.add(prefix + f);
						}
					}
				}
			}
		}

		// Try for references.
		String asTag = refResolve(repo, "refs/tags/" + name);
		if (asTag != null) {
			candidates.add(asTag);
		}

		String asBranch = refResolve(repo, "refs/heads/" + name);
		if (asBranch != null) {
			candidates.add(asBranch);
		}

		return candidates.isEmpty() ? null : candidates;
	}

	public static String repoDir(GitRepository repo, boolean mkdir, String... pathTuple) {
		// Construct the full path using repoPath
		String path = repoPath(repo, pathTuple);
		File file = new File(path);

		if (file.exists()) {
			if (file.isDirectory()) {
				return path;
			} else {
				throw new IllegalStateException("Not a directory: " + path);
			}
		}

		if (mkdir) {
			if (file.mkdirs()) {
				return path;
			} else {
				throw new IllegalStateException("Failed to create directory: " + path);
			}
		} else {
			return null;
		}
	}

	// Assuming this method exists in your project:
	public static String repoPath(GitRepository repo, String... pathTuple) {
		// Implement this method to construct the full path string from the repository
		// and path tuple
		// This is a placeholder implementation
		return Paths.get(repo.getGitdir().toString(), pathTuple).toString();
	}

	public static String objectFind(GitRepository repo, String name) throws Exception {
		return objectFind(repo, name, null, true);
	}

	public static String objectFind(GitRepository repo, String name, String fmt) throws Exception {
		return objectFind(repo, name, fmt, true);
	}

	public static String objectFind(GitRepository repo, String name, String fmt, boolean follow) throws Exception {
		List<String> shaList = objectResolve(repo, name);

		if (shaList == null || shaList.isEmpty()) {
			throw new IllegalStateException(String.format("No such reference %s.", name));
		}

		if (shaList.size() > 1) {
			throw new IllegalStateException(String.format("Ambiguous reference %s: Candidates are:\n - %s.", name,
					String.join("\n - ", shaList)));
		}

		String sha = shaList.get(0);

		if (fmt == null) {
			return sha;
		}

		while (true) {
			GitObject obj = GitObjectHelper.objectRead(repo, sha);
			if (obj == null) {
				throw new IllegalStateException("Object not found: " + sha);
			}
			if (obj.getFmt().equals(fmt)) {
				return sha;
			}

			if (!follow) {
				return null;
			}

			// Follow tags
			if (obj.getFmt().equals("tag")) {
				if (!(obj instanceof GitTag)) {
					throw new IllegalStateException("Expected a GitTag object.");
				}
				GitTag tagObj = (GitTag) obj;
				sha = new String((byte[]) tagObj.getKvlm().get("object".getBytes()), StandardCharsets.US_ASCII);
			} else if (obj.getFmt().equals("commit") && fmt.equals("tree")) {
				if (!(obj instanceof GitCommit)) {
					throw new IllegalStateException("Expected a GitCommit object.");
				}
				GitCommit commitObj = (GitCommit) obj;
				sha = (String) commitObj.getValueFromKvlm("tree".getBytes());
				// commitObj.printKvlm()
			} else {
				return null;
			}
		}
	}

	private static String computeSha1(byte[] data) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			byte[] hashBytes = digest.digest(data);
			StringBuilder sb = new StringBuilder();
			for (byte b : hashBytes) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-1 algorithm not found", e);
		}
	}

	public static Path repoFile(GitRepository repo, String... parts) {
		Path path = repo.getGitdir(); // Assuming GitRepository has a method to get the base path
		for (String part : parts) {
			path = path.resolve(part);
		}
		return path;
	}

	private static int indexOf(byte[] array, byte value) {
		return indexOf(array, value, 0);
	}

	private static int indexOf(byte[] array, byte value, int startIndex) {
		for (int i = startIndex; i < array.length; i++) {
			if (array[i] == value) {
				return i;
			}
		}
		return -1;
	}

	public static void treeCheckout(GitRepository repo, GitTree tree, String path) throws Exception {
		for (GitTreeLeaf item : tree.getItems()) {
			GitObject obj = GitObjectHelper.objectRead(repo, item.getSha());
			Path dest = Paths.get(path, item.getPath().toString());

			if (obj.getFmt().equals("tree")) {
				// Create a directory and recursively checkout the tree
				Files.createDirectories(dest);
				GitObjectHelper.treeCheckout(repo, (GitTree) obj, dest.toString());
			} else if (obj.getFmt().equals("blob")) {
				// Write blob data to file
				try (FileOutputStream fos = new FileOutputStream(dest.toFile())) {
					fos.write(obj.serialize());
				}
			} else {
				throw new IOException("Unsupported object format: " + new String(obj.getFmt()));
			}
		}
	}
}
