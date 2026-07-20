///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.github.checkstyle.difftool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class consisting of methods that perform various git operations.
 */
public final class GitCommands {

    /**
     * Logger instance for logging messages specific to GitCommands operations.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(GitCommands.class);

    /**
     * Number of times a command will be retried if it fails.
     */
    private static final int DEFAULT_RETRY_COUNT = 5;

    /**
     * Time (in seconds) to wait between retry attempts.
     */
    private static final int SLEEP_DURATION_SECONDS = 15;

    /** Private constructor to prevent instantiation of this utility class. */
    private GitCommands() {
        // Utility class, hide constructor
    }

    /**
     * Prints message and SHA of the latest commit in a certain branch and git repository.
     *
     * @param branchName name of the git branch to inquire.
     * @param localGitRepo local git repository.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted.
     */
    public static void printLatestCommitMessageSha(final String branchName,
                                                   final File localGitRepo)
            throws IOException, InterruptedException {

        executeCmd("git checkout " + branchName, localGitRepo);
        executeCmd("git log -1 --pretty=MSG:%s%nSHA-1:%H", localGitRepo);
    }

    /**
     * Performs a shallow clone of the specified repository.
     *
     * @param repoName       The name of the repository.
     * @param repoUrl        The URL of the repository.
     * @param commitId       The commit ID or reference.
     * @param srcDir         The source directory for cloning.
     */
    public static void shallowCloneRepository(final String repoName, final String repoUrl,
                                              final String commitId, final String srcDir) {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd;

            cloneCmd = getCloneShallowCmd(
                repoUrl,
                srcDestinationDir,
                commitId);

            LOGGER.info("Shallow clone "
                    + "git repository '"
                    + repoName
                    + "' to "
                    + srcDestinationDir
                    + " folder ...");
            executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning git repository '" + repoName + "' - completed\n");
        }
        LOGGER.info(repoName + " is synchronized");
    }

    /**
     * Clones the specified repository and resets to a specific commit if needed.
     *
     * @param repoName       The name of the repository.
     * @param repoUrl        The URL of the repository.
     * @param commitId       The commit ID or reference.
     * @param srcDir         The source directory for cloning.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the process is interrupted.
     */
    public static void cloneRepository(final String repoName, final String repoUrl,
                                       final String commitId, final String srcDir)
                        throws IOException, InterruptedException {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd;

            cloneCmd = getCloneCmd(repoUrl, srcDestinationDir);

            LOGGER.info("Cloning "
                    + "git repository '"
                    + repoName
                    + "' to "
                    + srcDestinationDir
                    + " folder ...");
            executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning "
                    + "git repository '"
                    + repoName
                    + "' - completed\n");
        }

        if (commitId != null && !commitId.isEmpty()) {
            final String lastCommitSha;
            final String commitIdSha;

            lastCommitSha = getLastProjectCommitSha(srcDestinationDir);
            commitIdSha = getCommitSha(commitId, srcDestinationDir);

            if (!lastCommitSha.equals(commitIdSha)) {
                resetRepositoryToCommit(commitId, srcDestinationDir);
            }
        }
        LOGGER.info(repoName + " is synchronized");
    }

    /**
     * Generates the shallow clone command based on the repository type and commit ID.
     *
     * @param repoUrl The URL of the repository.
     * @param srcDestinationDir The destination directory for cloning.
     * @param commitId The commit ID or reference.
     * @return The command to perform a shallow clone.
     */
    public static String getCloneShallowCmd(final String repoUrl,
                                            final String srcDestinationDir,
                                            final String commitId) {
        return "git clone --depth 1 --branch "
                + commitId
                + " "
                + repoUrl
                + " "
                + srcDestinationDir;
    }

    /**
     * Retrieves the SHA of the most recent commit in the specified repository directory.
     *
     * @param srcDestinationDir the directory of the repository
     * @return the SHA of the most recent commit, or an empty string if not found
     * @throws IOException if an I/O error occurs while executing the command
     * @throws InterruptedException if the process is interrupted while waiting
     */
    public static String getLastProjectCommitSha(final String srcDestinationDir)
                                                  throws IOException, InterruptedException {
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

    /**
     * Gets the SHA of a commit based on the provided commit ID.
     *
     * @param commitId The commit ID or reference.
     * @param srcDestinationDir The source directory of the repository.
     * @return The commit SHA, or an empty string if an error occurs.
     */
    public static String getCommitSha(final String commitId, final String srcDestinationDir) {
        final String cmd = "git rev-parse " + commitId;

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
     * Resets the repository to the specified commit reference. If the reference is
     * a branch or tag rather than a commit SHA, additional Git data is fetched
     * before performing the reset.
     *
     * @param commitId The commit reference to reset to.
     * @param srcDestinationDir The local path to the cloned repository.
     * @throws IOException If an I/O error occurs while executing Git commands.
     * @throws InterruptedException If the current thread is interrupted while
     *     waiting for the Git command to complete.
     */
    public static void resetRepositoryToCommit(final String commitId,
                                               final String srcDestinationDir)
            throws IOException, InterruptedException {
        if (!isGitSha(commitId)) {
            // If commitId is a branch or tag, fetch more data and then reset
            fetchAdditionalData(srcDestinationDir, commitId);
        }
        final String resetCmd = getResetCmd(commitId);
        LOGGER.info("Resetting git sources to commit '" + commitId + "'");
        executeCmd(resetCmd, new File(srcDestinationDir));
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
     * @param srcDestinationDir The source directory of the repository.
     * @param commitId The commit ID or reference to fetch data for.
     * @throws IOException If an I/O error occurs during the fetch operation.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    public static void fetchAdditionalData(final String srcDestinationDir, final String commitId)
                                            throws IOException, InterruptedException {
        final String fetchCmd;

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

        executeCmd(fetchCmd, new File(srcDestinationDir));
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
     * Generates the reset command for a specific commit ID.
     *
     * @param commitId  The commit ID or tag name.
     * @return The reset command for the specified commit.
     */
    public static String getResetCmd(final String commitId) {
        if (isGitSha(commitId)) {
            return "git reset --hard " + commitId;
        }
        else {
            return "git reset --hard refs/tags/" + commitId;
        }
    }

    /**
     * Generates the clone command based on the repository type.
     *
     * @param repoUrl The URL of the repository.
     * @param srcDestinationDir The destination directory for cloning.
     * @return The command to clone the repository.
     */
    public static String getCloneCmd(final String repoUrl, final String srcDestinationDir) {
        return "git clone " + repoUrl + " " + srcDestinationDir;
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
        executeCmd("git checkout " + branch, gitRepo);

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
        executeCmd("git checkout " + branch, gitRepo);

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
        executeCmd("git checkout " + branch, gitRepo);

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
     * Executes a command in the specified directory.
     *
     * @param cmd The command to execute.
     * @param dir The directory in which to execute the command.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted while waiting.
     * @throws CommandExecutionException If the command exits with a non-zero status.
     */
    private static void executeCmd(final String cmd, final File dir)
            throws IOException, InterruptedException {
        LOGGER.info("Running command: " + cmd);
        final ProcessBuilder processBuilder =
                new ProcessBuilder(getOsSpecificCmd(cmd).split("\\s+"));
        processBuilder.directory(dir);
        processBuilder.inheritIO();
        final Process process = processBuilder.start();
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException("Command execution failed", exitCode);
        }
    }

    /**
     * Returns the command string adjusted for the operating system.
     *
     * @param cmd the original command
     * @return the OS-specific command string
     */
    private static String getOsSpecificCmd(final String cmd) {
        if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
            return "cmd /c " + cmd;
        }
        return cmd;
    }

    /**
     * Constructs a path string using the appropriate file separator for the operating system.
     *
     * @param name the path components
     * @return the OS-specific path string
     */
    private static String getOsSpecificPath(final String... name) {
        return String.join(File.separator, name);
    }

    /**
     * Executes a command with retry logic.
     *
     * @param cmd   The command to execute.
     * @param dir   The directory to execute the command in.
     * @param retry The number of retry attempts.
     * @throws IllegalStateException If the command fails after all retries.
     */
    private static void executeCmdWithRetry(final String cmd, final File dir, final int retry) {
        final String osSpecificCmd = getOsSpecificCmd(cmd);
        int left = retry;
        while (left > 0) {
            try {
                final ProcessBuilder processBuilder =
                        new ProcessBuilder(osSpecificCmd.split("\\s+"));
                processBuilder.directory(dir);
                processBuilder.inheritIO();
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return;
                }
                left--;
                if (left > 0) {
                    TimeUnit.SECONDS.sleep(SLEEP_DURATION_SECONDS);
                }
            }
            catch (IOException | InterruptedException ex) {
                LOGGER.error("Error executing command: " + ex.getMessage());
                left--;
            }
        }
        throw new IllegalStateException("Error executing command: " + cmd);
    }

    /**
     * Executes a command with retry mechanism.
     *
     * @param cmd The command to execute.
     * @throws IllegalStateException if the command fails after retries.
     */
    private static void executeCmdWithRetry(final String cmd) {
        executeCmdWithRetry(cmd, new File("").getAbsoluteFile(), DEFAULT_RETRY_COUNT);
    }

    /**
     * Custom runtime exception for handling command execution failures.
     * Includes the exit code in the error message.
     */
    public static class CommandExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a CommandExecutionException with a message and exit code.
         *
         * @param message the detail message to be included in the exception
         * @param exitCode the exit code associated with the command execution failure
         */
        public CommandExecutionException(final String message, final int exitCode) {
            super(message + " (Exit code: " + exitCode + ")");
        }
    }
}
