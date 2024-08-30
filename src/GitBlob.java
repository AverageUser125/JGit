
public class GitBlob extends GitObject {

	private byte[] blobdata;

	public GitBlob(byte[] data) {
		super(data);
	}

	public byte[] serialize() {
		return this.blobdata;
	}

	protected void deserialize(byte[] data) {
		this.blobdata = data;

	}

	public String getFmt() {
		return "blob";
	}

}
