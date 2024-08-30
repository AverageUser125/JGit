import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class GitCommit extends GitObject {
	private static final byte[] COMMIT_FMT = "commit".getBytes(StandardCharsets.UTF_8);
	private LinkedHashMap<byte[], Object> kvlm;

	public GitCommit(byte[] data) {
		super(data);
	}

	public Map<byte[], Object> getKvlm() {
		return this.kvlm;
	}

	@Override
	public byte[] serialize() {
		return kvlmSerialize(kvlm);
	}

	@Override
	protected void deserialize(byte[] data) {
		kvlm = new LinkedHashMap<>();
		this.kvlm = kvlmParse(data, 0, kvlm);
		// this.printKvlm();
	}

	@Override
	public String getFmt() {
		return "commit";
	}

	@Override
	protected void init() {
		kvlm = new LinkedHashMap<>();
	}

	private LinkedHashMap<byte[], Object> kvlmParse(byte[] raw, int start, LinkedHashMap<byte[], Object> dct) {
		if (start >= raw.length) {
			return dct;
		}

		// Find the next space and newline
		int spc = indexOf(raw, (byte) ' ', start);
		int nl = indexOf(raw, (byte) '\n', start);

		// Base case: if newline appears first or no space is found
		if (spc < 0 || nl < spc) {
			if (nl == start) {
				dct.put(null, extract(raw, start + 1, raw.length));
				return dct;
			}
		}

		// Recursive case: read key-value pair
		byte[] key = extract(raw, start, spc);
		int end = start;

		// Find the end of the value
		while (true) {
			end = indexOf(raw, (byte) '\n', end + 1);
			if (end < 0 || (end + 1 >= raw.length) || raw[end + 1] != ' ') {
				break;
			}
		}

		// Extract value and handle continuation lines
		byte[] valueBytes = extract(raw, spc + 1, end);
		byte[] value = replace(valueBytes, new byte[] { (byte) '\n', (byte) ' ' }, new byte[] { (byte) '\n' });

		String keyStr = new String(key);
		String valueStr = new String(value);

		if (dct.containsKey(keyStr.getBytes())) {
			Object existing = dct.get(keyStr.getBytes());
			if (existing instanceof java.util.List) {
				@SuppressWarnings("unchecked")
				java.util.List<String> list = (java.util.List<String>) existing;
				list.add(valueStr);
			} else {
				java.util.List<String> list = new java.util.ArrayList<>();
				list.add((String) existing);
				list.add(valueStr);
				dct.put(keyStr.getBytes(), list);
			}
		} else {
			dct.put(keyStr.getBytes(), valueStr);
		}

		return kvlmParse(raw, end + 1, dct);
	}

	public void printKvlm() {
		printKvlm(this.kvlm);
	}

	public static void printKvlm(Map<byte[], Object> kvlm) {
		for (Map.Entry<byte[], Object> entry : kvlm.entrySet()) {
			String key;
			if (entry.getKey() == null) {
				key = "null";
			} else {
				key = new String(entry.getKey());
			}
			Object value = entry.getValue();
			if (value instanceof java.util.List) {
				java.util.List<?> list = (java.util.List<?>) value;
				System.out.println("Key: " + key + " -> Values: " + list);
			} else if (value instanceof byte[]) {
				String s = new String((byte[]) value);
				System.out.println("Key: " + key + " -> Values: " + s);
			} else {
				System.out.println("Key: " + key + " -> Value: " + value);
			}
		}

	}

	public Object getValueFromKvlm(byte[] key) {
		for (Map.Entry<byte[], Object> entry : kvlm.entrySet()) {
			if (Arrays.equals(entry.getKey(), key)) {
				return entry.getValue();
			}
		}
		return null;
	}

	private static byte[] extract(byte[] array, int start, int end) {
		byte[] result = new byte[end - start];
		System.arraycopy(array, start, result, 0, end - start);
		return result;
	}

	private static byte[] replace(byte[] source, byte[] target, byte[] replacement) {
		String sourceStr = new String(source);
		String targetStr = new String(target);
		String replacementStr = new String(replacement);
		String resultStr = sourceStr.replace(targetStr, replacementStr);
		return resultStr.getBytes();
	}

	private static byte[] kvlmSerialize(Map<byte[], Object> kvlm) {
		StringBuilder ret = new StringBuilder();

		for (Map.Entry<byte[], Object> entry : kvlm.entrySet()) {
			byte[] key = entry.getKey();
			if (key == null)
				continue;
			Object val = entry.getValue();
			byte[][] values = val instanceof byte[][] ? (byte[][]) val : new byte[][] { (byte[]) val };

			for (byte[] value : values) {
				String valueStr = new String(value, StandardCharsets.UTF_8);
				valueStr = valueStr.replace("\n", "\n ");
				ret.append(new String(key, StandardCharsets.UTF_8)).append(' ').append(valueStr).append('\n');
			}
		}

		byte[] message = (byte[]) kvlm.get(null);
		if (message != null) {
			ret.append('\n').append(new String(message, StandardCharsets.UTF_8)).append('\n');
		}

		return ret.toString().getBytes(StandardCharsets.UTF_8);
	}

	private static int indexOf(byte[] array, byte value, int fromIndex) {
		for (int i = fromIndex; i < array.length; i++) {
			if (array[i] == value) {
				return i;
			}
		}
		return -1;
	}

	public static void logGraphviz(GitRepository repo, String sha, Set<String> seen) throws Exception {
		if (seen.contains(sha)) {
			return;
		}
		seen.add(sha);

		GitObject commit = GitObjectHelper.objectRead(repo, sha);
		System.out.println(commit.getFmt());
		if (!(commit instanceof GitCommit)) {
			return;
		}
		GitCommit gitCommit = (GitCommit) commit;

		String shortHash = sha.substring(0, 8);
		String message = new String((byte[]) gitCommit.kvlm.get(null), StandardCharsets.UTF_8).trim();
		message = message.replace("\\", "\\\\").replace("\"", "\\\"");

		if (message.contains("\n")) {
			message = message.substring(0, message.indexOf("\n"));
		}

		System.out.println("\t" + sha + " [label=\"" + shortHash + ": " + message + "\"]");
		assert new String(COMMIT_FMT, StandardCharsets.UTF_8).equals(gitCommit.getFmt());

		if (!gitCommit.kvlm.containsKey("parent".getBytes(StandardCharsets.UTF_8))) {
			return;
		}

		Object parentsObj = gitCommit.kvlm.get("parent".getBytes(StandardCharsets.UTF_8));
		byte[][] parents;
		if (parentsObj instanceof byte[]) {
			parents = new byte[][] { (byte[]) parentsObj };
		} else {
			parents = (byte[][]) parentsObj;
		}

		for (byte[] parent : parents) {
			String parentSha = new String(parent, StandardCharsets.US_ASCII);
			System.out.println("\t" + sha + " ->" + parentSha + ";");
			logGraphviz(repo, parentSha, seen);
		}
	}
}
