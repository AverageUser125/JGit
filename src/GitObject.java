abstract class GitObject {
	public GitObject(byte[] data) {
		if (data != null) {
			deserialize(data);
		} else {
			init();
		}
	}

	public GitObject() {
		init();
	}

	public abstract byte[] serialize();

	protected abstract void deserialize(byte[] data);

	public abstract String getFmt();

	protected void init() {
		// Just do nothing. This is a reasonable default!
	}
}