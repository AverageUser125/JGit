import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class GitRepository {

	private Path worktree;
	private Path gitdir;
	private Properties conf;

	// Constructor
	public GitRepository(String path, boolean force) throws IOException {
		this.worktree = Paths.get(path).toAbsolutePath();
		this.gitdir = worktree.resolve(".git");

		if (!(force || Files.isDirectory(gitdir))) {
			throw new IOException("Not a Git repository " + path);
		}

		// Initialize configuration
		this.conf = new Properties();
		Path configFilePath = worktree.resolve(".git/config");

		if (Files.exists(configFilePath)) {
			try (var reader = Files.newBufferedReader(configFilePath)) {
				conf.load(reader);
			}
		} else if (!force) {
			throw new IOException("Configuration file missing");
		}

		if (!force) {
			String versStr = conf.getProperty("core.repositoryformatversion");
			if (versStr != null) {
				int vers = Integer.parseInt(versStr);
				if (vers != 0) {
					throw new IOException("Unsupported repositoryformatversion " + vers);
				}
			}
		}
	}

	// Overloaded constructor with default force=false
	public GitRepository(String path) throws IOException {
		this(path, false);
	}

	// Static method to find an existing Git repository
	public static GitRepository repoFind(String path) throws IOException {
		Path realPath = Paths.get(path).toRealPath();

		while (realPath != null) {
			if (Files.isDirectory(realPath.resolve(".git"))) {
				return new GitRepository(realPath.toString(), false);
			}

			Path parentPath = realPath.getParent();
			if (parentPath == null || parentPath.equals(realPath)) {
				break; // No parent directory left to check
			}

			realPath = parentPath; // Move to the parent directory
		}
		throw new IOException("No Git Repository detected");
	}

	public static GitRepository repoFind() throws IOException {
		return GitRepository.repoFind(".");
	}

	// Instance method for creating a new repository
	public static GitRepository createRepo(String filepath) throws IOException {
		GitRepository repo = new GitRepository(filepath, true);
		Path worktree = repo.getWorktree();
		Path gitdir = repo.getGitdir();

		if (Files.exists(worktree)) {
			if (!Files.isDirectory(worktree)) {
				throw new IOException(filepath + " is not a directory!");
			}
			if (Files.exists(gitdir) && Files.list(gitdir).findAny().isPresent()) {
				throw new IOException(filepath + " is not empty!");
			}
		} else {
			Files.createDirectories(worktree);
		}

		repo.createDirectory("branches");
		repo.createDirectory("objects");
		repo.createDirectory("refs/tags");
		repo.createDirectory("refs/heads");

		// .git/description
		repo.writeFile("description", "Unnamed repository; edit this file 'description' to name the repository.\n");

		// .git/HEAD
		repo.writeFile("HEAD", "ref: refs/heads/master\n");

		// .git/config
		Properties config = repo.defaultConfig();
		repo.writeConfig("config", config);

		return repo;
	}

	// Helper method to compute the path under the repo's gitdir
	public Path computeRepoPath(String... path) {
		return gitdir.resolve(Paths.get(String.join("/", path)));
	}

	// Helper method to create directories if they do not exist
	public Path createDirectory(String... path) throws IOException {
		Path dirPath = computeRepoPath(path);
		if (Files.exists(dirPath)) {
			if (Files.isDirectory(dirPath)) {
				return dirPath;
			} else {
				throw new IOException("Not a directory " + dirPath);
			}
		}
		return Files.createDirectories(dirPath);
	}

	// Helper method to write content to a file
	private void writeFile(String path, String content) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(computeRepoPath(path))) {
			writer.write(content);
		}
	}

	// Helper method to write the config file
	private void writeConfig(String path, Properties config) throws IOException {
		try (BufferedWriter writer = Files.newBufferedWriter(computeRepoPath(path))) {
			writer.write("[core]\n");
			for (String key : config.stringPropertyNames()) {
				String value = config.getProperty(key);
				// Remove "core." prefix before writing to the file
				String formattedKey = key.replace("core.", "");
				writer.write("\t" + formattedKey + " = " + value + "\n");
			}
		}
	}

	// Helper method to get default configuration
	public Properties defaultConfig() {
		Properties properties = new Properties();
		properties.setProperty("core.repositoryformatversion", "0");
		properties.setProperty("core.filemode", "false");
		properties.setProperty("core.bare", "false");
		properties.setProperty("core.symlinks", "false");
		return properties;
	}

	// Getters
	public Path getWorktree() {
		return worktree;
	}

	public Path getGitdir() {
		return gitdir;
	}

	public Properties getConf() {
		return conf;
	}
}
