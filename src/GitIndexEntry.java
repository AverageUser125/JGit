public record GitIndexEntry(
		// The last time a file's metadata changed
		Tuple<Integer, Integer> ctime,
		// The last time a file's data changed
		Tuple<Integer, Integer> mtime,
		// The ID of device containing this file
		int dev,
		// The file's inode number
		int ino,
		// The object type, either b1000 (regular), b1010 (symlink), b1110 (gitlink)
		int modeType,
		// The object permissions, an integer
		int modePerms,
		// User ID of owner
		int uid,
		// Group ID of owner
		int gid,
		// Size of this object, in bytes
		int fsize,
		// The object's SHA
		String sha,
		// Flag indicating if the object is assumed to be valid
		boolean flagAssumeValid,
		// Flag indicating the stage of the object
		int flagStage,
		// Name of the object (full path this time!)
		String name) {
	// Additional methods or constructors can be added if needed
}
