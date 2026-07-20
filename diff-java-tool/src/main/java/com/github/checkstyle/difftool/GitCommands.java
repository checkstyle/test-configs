package com.github.checkstyle.difftool;

import static com.github.checkstyle.difftool.DiffTool.LOGGER;
import static com.github.checkstyle.difftool.DiffTool.getOsSpecificPath;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class GitCommands {

    /**
     * Launches the Checkstyle report generation process.
     *
     * @param cfg The configuration map for Checkstyle.
     * @param isRegressionTesting whether regression testing mode is enabled. When true,
     *     the branch from {@code cfg} is checked out and its Checkstyle artifact is built
     *     and installed into the local Maven repository, which is required for the report
     *     generation to resolve the SNAPSHOT version. Must be true for both base and patch
     *     branches in diff mode.
     * @return CheckstyleReportInfo containing report details if regression testing, otherwise null.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted.
     */
    public static CheckstyleReportInfo launchCheckstyleReport(
        final Map<String, Object> cfg,
        final boolean isRegressionTesting)
            throws IOException, InterruptedException {
        CheckstyleReportInfo reportInfo = null;

        if (isRegressionTesting) {
            LOGGER.info("Installing Checkstyle artifact ("
                    + cfg.get("branch")
                    + ") into local Maven repository ...");
            DiffTool.executeCmd("git checkout " + cfg.get("branch"),
                (File) cfg.get("localGitRepo"));
            DiffTool.executeCmd("git log -1 --pretty=MSG:%s%nSHA-1:%H", (File) cfg.get("localGitRepo"));
            DiffTool.executeCmd(
                    "./mvnw -e --no-transfer-progress --batch-mode -Pno-validations clean install",
                    (File) cfg.get("localGitRepo")
            );
        }

        cfg.put("checkstyleVersion",
                DiffTool.getCheckstyleVersionFromPomXml(cfg.get("localGitRepo") + "/pom.xml",
                "version"));

        DiffTool.generateCheckstyleReport(cfg);

        LOGGER.info("Moving Checkstyle report into "
                + cfg.get("destDir")
                + " ...");
        DiffTool.moveDir("reports", (String) cfg.get("destDir"));

        if (isRegressionTesting) {
            reportInfo = new CheckstyleReportInfo(
                    (String) cfg.get("branch"),
                    getLastCheckstyleCommitSha((File) cfg.get("localGitRepo"),
                            (String) cfg.get("branch")),
                    getLastCommitMsg((File) cfg.get("localGitRepo"), (String) cfg.get("branch")),
                    getLastCommitTime((File) cfg.get("localGitRepo"), (String) cfg.get("branch"))
            );
        }
        return reportInfo;
    }

    /**
     * Performs a shallow clone of the specified repository.
     *
     * @param repoName       The name of the repository.
     * @param repoType       The type of repository (e.g., "git").
     * @param repoUrl        The URL of the repository.
     * @param commitId       The commit ID or reference.
     * @param srcDir         The source directory for cloning.
     * @throws IOException If an I/O error occurs.
     */
    public static void shallowCloneRepository(final String repoName, final String repoType,
                        final String repoUrl, final String commitId, final String srcDir)
                        throws IOException {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd = getCloneShallowCmd(
                            repoType,
                            repoUrl,
                            srcDestinationDir,
                            commitId);
            LOGGER.info("Shallow clone "
                    + repoType
                    + " repository '"
                    + repoName
                    + "' to "
                    + srcDestinationDir
                    + " folder ...");
            DiffTool.executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
        }
        LOGGER.info(repoName + " is synchronized");
    }



    /**
     * Generates the shallow clone command based on the repository type and commit ID.
     *
     * @param repoType The type of repository (e.g., "git").
     * @param repoUrl The URL of the repository.
     * @param srcDestinationDir The destination directory for cloning.
     * @param commitId The commit ID or reference.
     * @return The command to perform a shallow clone.
     * @throws IllegalArgumentException if the repository type is unknown.
     */
    public static String getCloneShallowCmd(final String repoType, final String repoUrl,
                          final String srcDestinationDir, final String commitId) {
        if ("git".equals(repoType)) {
            return "git clone --depth 1 --branch "
                    + commitId
                    + " "
                    + repoUrl
                    + " "
                    + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown repository type: " + repoType);
    }

    /**
     * Clones the specified repository and resets to a specific commit if needed.
     *
     * @param repoName       The name of the repository.
     * @param repoType       The type of repository (e.g., "git").
     * @param repoUrl        The URL of the repository.
     * @param commitId       The commit ID or reference.
     * @param srcDir         The source directory for cloning.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the process is interrupted.
     */
    public static void cloneRepository(final String repoName, final String repoType,
                        final String repoUrl,
                        final String commitId,
                        final String srcDir)
                        throws IOException, InterruptedException {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd =
                    getCloneCmd(repoType, repoUrl, srcDestinationDir);
            LOGGER.info("Cloning "
                    + repoType
                    + " repository '"
                    + repoName
                    + "' to "
                    + srcDestinationDir
                    + " folder ...");
            DiffTool.executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning "
                    + repoType
                    + " repository '"
                    + repoName
                    + "' - completed\n");
        }

        if (commitId != null && !commitId.isEmpty()) {
            final String lastCommitSha = getLastProjectCommitSha(repoType, srcDestinationDir);
            final String commitIdSha = getCommitSha(commitId, repoType, srcDestinationDir);
            if (!lastCommitSha.equals(commitIdSha)) {
                if (!isGitSha(commitId)) {
                    // If commitId is a branch or tag, fetch more data and then reset
                    fetchAdditionalData(repoType, srcDestinationDir, commitId);
                }
                final String resetCmd = getResetCmd(repoType, commitId);
                LOGGER.info("Resetting " + repoType + " sources to commit '" + commitId + "'");
                DiffTool.executeCmd(resetCmd, new File(srcDestinationDir));
            }
        }
        LOGGER.info(repoName + " is synchronized");
    }

    /**
     * Retrieves the SHA of the most recent commit in the specified repository directory.
     *
     * @param repoType the type of repository (e.g., "git")
     * @param srcDestinationDir the directory of the repository
     * @return the SHA of the most recent commit, or an empty string if not found
     * @throws IOException if an I/O error occurs while executing the command
     * @throws InterruptedException if the process is interrupted while waiting
     * @throws IllegalArgumentException if the repository type is unknown
     */
    public static String getLastProjectCommitSha(final String repoType,
                                                  final String srcDestinationDir)
                                                  throws IOException, InterruptedException {
        if ("git".equals(repoType)) {
            final ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "rev-parse", "HEAD");
            processBuilder.directory(new File(srcDestinationDir));
            final Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),
                    StandardCharsets.UTF_8))) {
                final String sha = reader.readLine();
                process.waitFor();
                return sha != null ? sha.trim() : "";
            }
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    /**
     * Gets the SHA of a commit based on the provided commit ID.
     *
     * @param commitId The commit ID or reference.
     * @param repoType The type of repository (e.g., "git").
     * @param srcDestinationDir The source directory of the repository.
     * @return The commit SHA, or an empty string if an error occurs.
     * @throws IllegalArgumentException if the repository type is unknown.
     * @throws IOException if an I/O error occurs while executing the command.
     */
    public static String getCommitSha(final String commitId,
                                       final String repoType,
                                       final String srcDestinationDir) {
        final String cmd;
        if ("git".equals(repoType)) {
            cmd = "git rev-parse " + commitId;
        }
        else {
            throw new IllegalArgumentException("Error! Unknown "
                    + repoType
                    + " repository.");
        }

        try {
            // Use ProcessBuilder instead of Runtime#exec
            final ProcessBuilder processBuilder =
                    new ProcessBuilder(cmd.split("\\s+"));
            processBuilder.directory(new File(srcDestinationDir));
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            // Use InputStreamReader with explicit charset
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(),
                    StandardCharsets.UTF_8))) {
                final String sha = reader.readLine();
                return sha != null ? sha.replace("\n", "") : "";
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error getting commit SHA: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Checks if the provided value matches the format of a Git SHA.
     *
     * @param value The value to check.
     * @return True if the value matches a valid Git SHA format, false otherwise.
     */
    public static boolean isGitSha(final String value) {
        return value.matches("[0-9a-f]{5,40}");
    }

    /**
     * Fetches additional data for a specific commit if needed.
     *
     * @param repoType The type of repository (e.g., "git").
     * @param srcDestinationDir The source directory of the repository.
     * @param commitId The commit ID or reference to fetch data for.
     * @throws IOException If an I/O error occurs during the fetch operation.
     * @throws IllegalArgumentException If the repository type is unknown.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    public static void fetchAdditionalData(final String repoType, final String srcDestinationDir,
                                            final String commitId)
                                            throws IOException, InterruptedException {
        final String fetchCmd;
        if ("git".equals(repoType)) {
            if (isGitSha(commitId)) {
                fetchCmd = "git fetch";
            }
            else {
                // Check if commitId is a tag and handle accordingly
                if (isTag(commitId, new File(srcDestinationDir))) {
                    fetchCmd = "git fetch --tags";
                }
                else {
                    fetchCmd = "git fetch origin " + commitId + ":" + commitId;
                }
            }
        }
        else {
            throw new IllegalArgumentException("Error! Unknown "
                    + repoType
                    + " repository.");
        }

        DiffTool.executeCmd(fetchCmd, new File(srcDestinationDir));
    }

    /**
     * Generates the reset command for a specific commit ID.
     *
     * @param repoType  The type of repository (e.g., "git").
     * @param commitId  The commit ID or tag name.
     * @return The reset command for the specified commit.
     * @throws IllegalArgumentException if the repository type is unknown.
     */
    public static String getResetCmd(final String repoType, final String commitId) {
        if ("git".equals(repoType)) {
            if (isGitSha(commitId)) {
                return "git reset --hard " + commitId;
            }
            else {
                return "git reset --hard refs/tags/" + commitId;
            }
        }
        throw new IllegalArgumentException("Error! Unknown "
                + repoType + " repository.");
    }

    /**
     * Checks if the provided commit ID is a tag in the repository.
     *
     * @param commitId   The commit ID to check.
     * @param gitRepo    The repository directory.
     * @return True if the commit ID is a tag, false otherwise.
     */
    public static boolean isTag(final String commitId, final File gitRepo) {
        try {
            // Use ProcessBuilder instead of Runtime#exec
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "tag", "-l", commitId);
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            // Use InputStreamReader with explicit charset
            try (BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(process.getInputStream(),
                                 StandardCharsets.UTF_8))) {
                final String result = reader.readLine();
                return result != null && result.trim().equals(commitId);
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error checking if commit is a tag: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Generates the clone command based on the repository type.
     *
     * @param repoType The type of repository (e.g., "git").
     * @param repoUrl The URL of the repository.
     * @param srcDestinationDir The destination directory for cloning.
     * @return The command to clone the repository.
     * @throws IllegalArgumentException if the repository type is unknown.
     */
    public static String getCloneCmd(final String repoType,
                                      final String repoUrl,
                                      final String srcDestinationDir) {
        if ("git".equals(repoType)) {
            return "git clone " + repoUrl + " " + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown "
                + repoType
                + " repository.");
    }

    /**
     * Retrieves the SHA of the last Checkstyle commit on the given branch.
     *
     * @param gitRepo The Git repository directory.
     * @param branch The branch name.
     * @return The SHA of the last commit.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted.
     */
    public static String getLastCheckstyleCommitSha(final File gitRepo, final String branch)
            throws IOException, InterruptedException {
        // Checkout the specified branch
        DiffTool.executeCmd("git checkout " + branch, gitRepo);

        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "rev-parse", "HEAD");
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
                else {
                    LOGGER.error("No output from git rev-parse HEAD");
                    return "";
                }
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error getting last commit SHA: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Retrieves the message of the most recent commit on the specified branch.
     *
     * @param gitRepo the directory of the Git repository
     * @param branch the branch to check out and retrieve the commit message from
     * @return the message of the most recent commit
     * @throws IOException if an I/O error occurs while executing commands or reading output
     * @throws InterruptedException if the process is interrupted while waiting
     */
    public static String getLastCommitMsg(final File gitRepo, final String branch)
            throws IOException, InterruptedException {
        DiffTool.executeCmd("git checkout " + branch, gitRepo);

        try {
            final ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "log", "-1", "--pretty=%B");
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
                else {
                    return "";
                }
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error getting last commit message: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Retrieves the timestamp of the last commit on the specified branch.
     *
     * @param gitRepo The repository directory.
     * @param branch  The branch name to check out.
     * @return The timestamp of the last commit, or an empty string if an error occurs.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the process is interrupted.
     */
    public static String getLastCommitTime(final File gitRepo, final String branch)
            throws IOException, InterruptedException {
        DiffTool.executeCmd("git checkout " + branch, gitRepo);

        try {
            final ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "log", "-1", "--format=%cd");
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                }
                else {
                    return "";
                }
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error getting last commit time: " + ex.getMessage());
            return "";
        }
    }

    /**
     * Checks if the given directory is a valid Git repository.
     *
     * @param gitRepoDir The directory to check.
     * @return True if it is a valid Git repository, otherwise false.
     */
    public static boolean isValidGitRepo(final File gitRepoDir) {
        if (gitRepoDir.exists() && gitRepoDir.isDirectory()) {
            try {
                final ProcessBuilder processBuilder = new ProcessBuilder("git", "status");
                processBuilder.directory(gitRepoDir);
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                return exitCode == 0;
            }
            catch (IOException | InterruptedException ex) {
                LOGGER.error("Error: '"
                        + gitRepoDir.getPath()
                        + "' is not a git repository!");
                return false;
            }
        }
        else {
            LOGGER.error("Error: '"
                    + gitRepoDir.getPath()
                    + "' does not exist or it is not a directory!");
            return false;
        }
    }

    /**
     * Checks if the given branch exists in the specified Git repository.
     *
     * @param gitRepo The Git repository directory.
     * @param branchName The branch name to check.
     * @return True if the branch exists, otherwise false.
     */
    public static boolean isExistingGitBranch(final File gitRepo, final String branchName) {
        try {
            final ProcessBuilder processBuilder =
                    new ProcessBuilder("git", "rev-parse", "--verify", branchName);
            processBuilder.directory(gitRepo);
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.error("Error: git repository "
                        + gitRepo.getPath()
                        + " does not have a branch with name '"
                        + branchName
                        + "'!");
                return false;
            }
            return true;
        }
        catch (IOException | InterruptedException ex) {
            LOGGER.error("Error checking branch existence: " + ex.getMessage());
            return false;
        }
    }

    /**
     * Checks if there are unstaged changes in the specified Git repository.
     *
     * @param gitRepo the directory of the Git repository
     * @return {@code true} if there are unstaged changes, {@code false} otherwise
     */
    public static boolean hasUnstagedChanges(final File gitRepo) {
        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "--exit-code");
            processBuilder.directory(gitRepo);
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();
            if (exitCode == 0) {
                return false;
            }
            else {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(),
                        StandardCharsets.UTF_8))) {

                    String line;
                    while (true) {
                        line = reader.readLine();
                        if (line == null) {
                            break;
                        }
                        LOGGER.info(line);
                    }
                }
                return true;
            }
        }
        catch (IOException | InterruptedException ex) {
            LOGGER.error("Error checking for unstaged changes: " + ex.getMessage());
            return true;
        }
    }

    /**
     * Represents the Checkstyle report information.
     */
    public static class CheckstyleReportInfo {
        /** The name of the branch where the commit resides. */
        private final String branch;

        /** The SHA hash of the commit for unique identification. */
        private final String commitSha;

        /** The message associated with the commit. */
        private final String commitMsg;

        /** The timestamp when the commit was made. */
        private final String commitTime;

        /**
         * Constructs a {@code CheckstyleReportInfo} instance with the specified details.
         *
         * @param branch     the name of the branch where the commit resides
         * @param commitSha  the SHA hash of the commit for unique identification
         * @param commitMsg  the message associated with the commit
         * @param commitTime the timestamp when the commit was made
         */
        CheckstyleReportInfo(final String branch, final String commitSha,
                             final String commitMsg, final String commitTime) {
            this.branch = branch;
            this.commitSha = commitSha;
            this.commitMsg = commitMsg;
            this.commitTime = commitTime;
        }

        /**
         * Returns the name of the branch where the commit resides.
         *
         * @return the branch name
         */
        public String getBranch() {
            return branch;
        }

        /**
         * Returns the SHA hash of the commit for unique identification.
         *
         * @return the commit SHA
         */
        public String getCommitSha() {
            return commitSha;
        }

        /**
         * Returns the message associated with the commit.
         *
         * @return the commit message
         */
        public String getCommitMsg() {
            return commitMsg;
        }

        /**
         * Returns the timestamp when the commit was made.
         *
         * @return the commit timestamp
         */
        public String getCommitTime() {
            return commitTime;
        }
    }
}
