import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GitTree extends GitObject {
	List<GitTreeLeaf> items;

	public GitTree(byte[] data) {
		super(data);
	}

	@Override
	protected void init() {
		items = new ArrayList<>();
	}

	public byte[] serialize() {
		// TODO Auto-generated method stub
		return treeSerialize();
	}

	protected void deserialize(byte[] data) {
		int pos = 0;
		int max = data.length;
		items = new ArrayList<>();

		while (pos < max) {
			// Call the static treeParseOne method to get the updated position and
			// GitTreeLeaf
			Tuple<Integer, GitTreeLeaf> result = GitTreeLeaf.treeParseOne(data, pos);
			pos = result.getFirst(); // Update position
			items.add(result.getSecond()); // Add GitTreeLeaf to the list
		}
	}

	// Serialization method similar to tree_serialize in Python
	public byte[] treeSerialize() {
		// Sort the items using the conversion function
		items.sort(Comparator.comparing(GitTree::treeLeafSortKey));

		ByteBuffer buffer = ByteBuffer.allocate(1024); // Assuming a large enough buffer
		for (GitTreeLeaf leaf : items) {
			buffer.put(leaf.getMode());
			buffer.put((byte) ' ');
			buffer.put(leaf.getPath().toString().getBytes(StandardCharsets.UTF_8));
			buffer.put((byte) 0); // Null byte separator

			// Convert SHA to bytes
			ByteBuffer shaBuffer = ByteBuffer.allocate(20);
			shaBuffer.putLong(Long.parseUnsignedLong(leaf.getSha(), 16));
			buffer.put(shaBuffer.array());
		}

		// Resize the buffer to the actual data length
		byte[] result = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(result);
		return result;
	}

	// Conversion function similar to tree_leaf_sort_key in Python
	private static String treeLeafSortKey(GitTreeLeaf leaf) {
		if (new String(leaf.getMode()).startsWith("10")) {
			return leaf.getPath().toString();
		} else {
			return leaf.getPath() + "/";
		}
	}

	public String getFmt() {
		return "tree";
	}

	public static void lsTree(GitRepository repo, String ref) throws Exception {
		lsTree(repo, ref, false, "");
	}

	public static void lsTree(GitRepository repo, String ref, boolean recursive) throws Exception {
		lsTree(repo, ref, recursive, "");
	}

	public static void lsTree(GitRepository repo, String ref, boolean recursive, String prefix) throws Exception {
		// Use the GitObjectHelper to find the object with the "tree" format
		String sha = GitObjectHelper.objectFind(repo, ref, "tree");

		if (sha == null) {
			throw new IllegalStateException(String.format("No such object %s", ref));
		}

		// Use the GitObjectHelper to read the object
		GitObject obj = GitObjectHelper.objectRead(repo, sha);

		if (obj == null || !(obj instanceof GitTree)) {
			throw new IllegalStateException("Invalid object type: expected GitTree.");
		}

		GitTree tree = (GitTree) obj;

		for (GitTreeLeaf item : tree.getItems()) {
			String typeStr;
			String mode = new String(item.getMode());

			// Determine the type based on the mode
			if (mode.length() == 6) {
				String type = mode.substring(0, 2);
				switch (type) {
				case "04":
					typeStr = "tree";
					break;
				case "10":
				case "12":
					typeStr = "blob";
					break;
				case "16":
					typeStr = "commit";
					break;
				default:
					throw new IllegalStateException(String.format("Weird tree leaf mode %s", mode));
				}
			} else {
				throw new IllegalStateException(String.format("Unexpected mode length: %d", mode.length()));
			}

			if (!(recursive && typeStr.equals("tree"))) {
				// This is a leaf
				String modeStr = String.format("%06d", Integer.parseInt(mode, 16));
				System.out.printf("%s %s %s\t%s%n", modeStr, typeStr, item.getSha(),
						Paths.get(prefix, item.getPath().toString()));
			} else {
				// This is a branch, recurse
				lsTree(repo, item.getSha(), recursive, Paths.get(prefix, item.getPath().toString()).toString());
			}
		}
	}

	public List<GitTreeLeaf> getItems() {
		return this.items;
	}

}
