import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class main {
	private static String repoPath = ".\\repo\\";

	public static void main(String[] fullArgs) {
		fullArgs = new String[] { "ls-files" };
		if (fullArgs.length == 0) {
			// System.out.println("Please enter some command");
			// return;
		}
		String command = fullArgs[0];
		final String[] args = Arrays.copyOfRange(fullArgs, 1, fullArgs.length);

		try {
			if (command.equals("add")) {
			} else if (command.equals("cat-file")) {
				cmdCatFile(args);
			} else if (command.equals("check-ignore")) {
				cmdCheckIgnore(args);
			} else if (command.equals("checkout")) {
				cmdCheckout(args);
			} else if (command.equals("commit")) {

			} else if (command.equals("hash-object")) {
				cmdHashObject(args);
			} else if (command.equals("init")) {
				cmdInitRepo(args);
			} else if (command.equals("log")) {
				cmdLog(args);
			} else if (command.equals("ls-files")) {
				cmdLsFiles(args);
			} else if (command.equals("ls-tree")) {
				cmdLsTree(args);
			} else if (command.equals("rev-parse")) {
				cmdRevParse(args);
			} else if (command.equals("rm")) {

			} else if (command.equals("show-ref")) {
				cmdShowRef(args);
			} else if (command.equals("status")) {

			} else if (command.equals("tag")) {
				cmdTag(args);
			} else {
				System.out.println("Bad command.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void cmdCheckIgnore(final String[] args) throws IOException {
		String[] paths = new String[] { "." };
		GitRepository repo = GitRepository.repoFind(repoPath);
		// Object rules = gitignoreRead(repo);
		for (String path : paths) {
		}

	}

	public static void cmdLsFiles(final String[] args) throws IOException {
		boolean verbose = false;

		GitRepository repo = GitRepository.repoFind(repoPath);

		GitIndex index = GitIndex.indexRead(repo);

		if (verbose) {
			System.out.println(String.format("Index file format v%d, containing %d entries.", index.getVersion(),
					index.getEntries().size()));
		}

		for (GitIndexEntry e : index.getEntries()) {
			System.out.println(e.name());
			if (verbose) {
				String modeType = GitIndex.MODE_TYPE_MAP.get(e.modeType());
				System.out.println(String.format("  %s with perms: %o", modeType, e.modePerms()));
				System.out.println(String.format("  on blob: %s", e.sha()));
				System.out.println(String.format("  created: %s.%d, modified: %s.%d",
						GitIndex.formatTimestamp(e.ctime().getFirst()), e.ctime().getSecond(),
						GitIndex.formatTimestamp(e.mtime().getFirst()), e.mtime().getSecond()));
				System.out.println(String.format("  device: %d, inode: %d", e.dev(), e.ino()));
				System.out.println(String.format("  user: %s (%d)  group: %s (%d)", GitIndex.getUsernameById(e.uid()),
						e.uid(), GitIndex.getGroupnameById(e.gid()), e.gid()));
				System.out.println(
						String.format("  flags: stage=%d assume_valid=%b", e.flagStage(), e.flagAssumeValid()));
			}
		}
	}

	private static void cmdRevParse(final String[] args) throws Exception {
		String type = "blob";
		String name = "HEAD";
		GitRepository repo = GitRepository.repoFind(repoPath);

		System.out.println(GitObjectHelper.objectFind(repo, name, type, true));
	}

	private static void cmdTag(final String[] args) throws Exception {
		String name = null;
		String objectName;
		boolean createTagObject = false; // Change this to `true` if you want an annotated tag

		// Determine the name and object based on the arguments
		if (args.length == 1) {
			name = args[0];
			objectName = "HEAD"; // Default to HEAD if only the tag name is provided
		} else if (args.length == 2) {
			name = args[0];
			objectName = args[1];
		} else {
			throw new Exception("Need name and optionally an object reference");
		}

		// Find the Git repository
		GitRepository repo = GitRepository.repoFind(repoPath);

		// Create the tag (lightweight or annotated)
		GitTag.tagCreate(repo, name, objectName, createTagObject);
	}

	private static void cmdShowRef(final String[] args) throws Exception {
		String path = null;
		if (args.length != 0) {
			path = args[0];
		}
		GitRepository repo = GitRepository.repoFind(repoPath);

		Map<String, Object> refs = GitObjectHelper.refList(repo, path);
		GitObjectHelper.showRef(repo, refs);

	}

	private static void cmdCheckout(final String[] args) throws Exception {
		String commit = "HEAD";
		GitRepository repo = GitRepository.repoFind(repoPath);

		String commitSha = GitObjectHelper.objectFind(repo, commit);
		GitObject obj = GitObjectHelper.objectRead(repo, commitSha);

		// If the object is a commit, we grab its tree
		if (obj.getFmt().equals("commit")) {
			GitCommit objCommit = (GitCommit) obj;
			Object value = objCommit.getValueFromKvlm("tree".getBytes());
			if (value == null) {
				throw new Exception("Tree doesn't exist");

			}
			String treeSha = (String) value;
			obj = GitObjectHelper.objectRead(repo, treeSha);
		}
		GitTree objTree = (GitTree) obj;

		// Verify that path is an empty directory
		String filepath = args[0];
		File path = new File(filepath);
		if (path.exists()) {
			if (!path.isDirectory()) {
				throw new Exception("Not a directory " + filepath + "!");
			}
			if (path.list().length > 0) {
				throw new Exception("Not empty " + filepath + "!");
			}
		} else {
			if (!path.mkdirs()) {
				throw new IOException("Failed to create directory " + filepath);
			}
		}

		GitObjectHelper.treeCheckout(repo, objTree, path.getAbsolutePath());
	}

	private static void cmdLsTree(final String[] args) throws Exception {
		String tree = "HEAD";
		boolean recursive = false;
		GitRepository repo = GitRepository.repoFind(repoPath);

		GitTree.lsTree(repo, tree, recursive);
	}

	private static void cmdLog(final String args[]) throws Exception {
		// String commit = "8b16a2e683abf2abc34d5e0cab3e6e2af2468cba";
		String commit = "master";
		GitRepository repo = GitRepository.repoFind(repoPath);

		String foundObj = GitObjectHelper.objectFind(repo, commit);
		if (foundObj == null) {
			System.out.println("Object not found");
		}
		Set<String> emptySet = new HashSet<>();
		GitCommit.logGraphviz(repo, foundObj, emptySet);
		// Set<String>);
		System.out.println("}");
	}

	private static void cmdHashObject(final String args[]) throws Exception {
		String path = args[1];
		boolean write = false;
		String type = "blob";

		GitRepository repo = null;
		if (write) {
			repo = GitRepository.repoFind(path);
		}

		Path filePath = Paths.get(path);
		if (!Files.isRegularFile(filePath)) {
			System.err.println("File does not exist: " + path);
			return;
		}

		byte[] fileData;
		try (InputStream is = new FileInputStream(filePath.toFile())) {
			fileData = is.readAllBytes();
		}

		String sha = GitObjectHelper.objectHash(fileData, type.getBytes(), repo);
		System.out.println(sha);
	}

	private static void cmdCatFile(final String args[]) throws Exception {
		String type = "blob";
		String objectIdentifier = "idk";

		GitRepository repo = GitRepository.repoFind(repoPath);

		String foundObjectName = GitObjectHelper.objectFind(repo, objectIdentifier, type);
		if (foundObjectName == null) {
			System.out.println("Object not found");
			return;
		}
		GitObject obj = GitObjectHelper.objectRead(repo, foundObjectName);
		if (obj == null) {
			System.out.println("Cannot read object with name: " + foundObjectName);
			return;
		}
		System.out.print(obj.serialize());
	}

	private static void cmdInitRepo(final String args[]) throws Exception {

		String repoPath;
		if (args.length == 0) {
			repoPath = ".";
		} else if (args.length == 1) {
			repoPath = args[0];
		} else {
			System.out.println("Too many arguments");
			return;
		}
		GitRepository.createRepo(repoPath);
	}

}
