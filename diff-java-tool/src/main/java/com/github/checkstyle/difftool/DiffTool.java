package com.github.checkstyle.difftool;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for the DiffTool application.
 */
public final class DiffTool {

    /** Logger instance for logging messages specific to DiffTool operations. */
    private static final Logger LOGGER = LoggerFactory.getLogger(DiffTool.class);

    /** Private constructor to prevent instantiation of this utility class. */
    private DiffTool() {
        // Utility class, hide constructor
    }

    /**
     * Main method to run the DiffTool application.
     *
     * @param args Command line arguments
     */
    public static void main(final String[] args) {
        try {
            final CommandLine cliOptions = getCliOptions(args);
            if (cliOptions == null || !areValidCliOptions(cliOptions)) {
                LOGGER.error("Error: invalid command line arguments!");
                System.exit(1);
            }

            final Config cfg = new Config(cliOptions);
            final List<String> configFilesList = Arrays.asList(cfg.getConfigFile(), cfg.getBaseConfig(), cfg.getPatchConfig(), cfg.getListOfProjects());
            copyConfigFilesAndUpdatePaths(configFilesList);

            if (cfg.getLocalGitRepo() != null && hasUnstagedChanges(cfg.getLocalGitRepo())) {
                LOGGER.error("Error: git repository " + cfg.getLocalGitRepo().getPath() + " has unstaged changes!");
                System.exit(1);
            }

            // Delete work directories to avoid conflicts with previous reports generation
            deleteWorkDirectories(cfg);

            CheckstyleReportInfo checkstyleBaseReportInfo = null;
            if (cfg.isDiffMode()) {
                checkstyleBaseReportInfo = launchCheckstyleReport(cfg.getCheckstyleToolBaseConfig());
            }

            final CheckstyleReportInfo checkstylePatchReportInfo = launchCheckstyleReport(cfg.getCheckstyleToolPatchConfig());

            if (checkstylePatchReportInfo != null) {
                deleteDir(cfg.getReportsDir());
                moveDir(cfg.getTmpReportsDir(), cfg.getReportsDir());

                generateDiffReport(cfg.getDiffToolConfig());
                generateSummaryIndexHtml(cfg.getDiffDir(), checkstyleBaseReportInfo,
                        checkstylePatchReportInfo, configFilesList, cfg.isAllowExcludes());
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error: " + e.getMessage(), e);
            System.exit(1);
        } catch (Exception e) {
            LOGGER.error("Unexpected error: " + e.getMessage(), e);
            System.exit(1);
        }
    }

    /**
     * Parses command line arguments and returns a CommandLine object.
     *
     * @param args Command line arguments
     * @return CommandLine object containing parsed options
     * @throws IllegalArgumentException if parsing fails
     */
    public static CommandLine getCliOptions(final String... args) {
        final Options options = new Options();
        options.addOption("r", "localGitRepo", true, "Path to local git repository (required)");
        options.addOption("b", "baseBranch", true, "Base branch name. Default is master (optional, default is master)");
        options.addOption("p", "patchBranch", true, "Name of the patch branch in local git repository (required)");
        options.addOption("bc", "baseConfig", true, "Path to the base checkstyle config file (optional, if absent then the tool will use only patchBranch in case the tool mode is 'single', otherwise baseBranch will be set to 'master')");
        options.addOption("pc", "patchConfig", true, "Path to the patch checkstyle config file (required if baseConfig is specified)");
        options.addOption("c", "config", true, "Path to the checkstyle config file (required if baseConfig and patchConfig are not specified)");
        options.addOption("g", "allowExcludes", false, "Whether to allow excludes specified in the list of projects (optional, default is false)");
        options.addOption("h", "useShallowClone", false, "Enable shallow cloning");
        options.addOption("l", "listOfProjects", true, "Path to file which contains projects to test on (required)");
        options.addOption("s", "shortFilePaths", false, "Whether to save report file paths as a shorter version to prevent long paths. (optional, default is false)");
        options.addOption("m", "mode", true, "The mode of the tool: 'diff' or 'single'. (optional, default is 'diff')");
        options.addOption("xm", "extraMvnRegressionOptions", true, "Extra arguments to pass to Maven for Checkstyle Regression run (optional, ex: -Dmaven.prop=true)");

        final CommandLineParser parser = new DefaultParser();
        final HelpFormatter formatter = new HelpFormatter();

        try {
            return parser.parse(options, args);
        }
        catch (ParseException e) {
            LOGGER.info(e.getMessage());
            formatter.printHelp("DiffTool", options);
            throw new IllegalArgumentException("Failed to parse command line arguments", e);
        }
    }

    private static boolean areValidCliOptions(final CommandLine cliOptions) {
        final String baseConfig = cliOptions.getOptionValue("baseConfig");
        final String patchConfig = cliOptions.getOptionValue("patchConfig");
        final String config = cliOptions.getOptionValue("config");
        final String toolMode = cliOptions.getOptionValue("mode");
        final String patchBranch = cliOptions.getOptionValue("patchBranch");
        final String baseBranch = cliOptions.getOptionValue("baseBranch");
        final File listOfProjectsFile = new File(cliOptions.getOptionValue("listOfProjects"));
        final String localGitRepo = cliOptions.getOptionValue("localGitRepo");

        if (toolMode != null && !("diff".equals(toolMode) || "single".equals(toolMode))) {
            LOGGER.error("Error: Invalid mode: '" + toolMode + "'. The mode should be 'single' or 'diff'!");
            return false;
        }
        if (!isValidCheckstyleConfigsCombination(config, baseConfig, patchConfig, toolMode)) {
            return false;
        }
        if (localGitRepo != null && !isValidGitRepo(new File(localGitRepo))) {
            LOGGER.error("Error: " + localGitRepo + " is not a valid git repository!");
            return false;
        }
        if (localGitRepo != null && !isExistingGitBranch(new File(localGitRepo), patchBranch)) {
            LOGGER.error("Error: " + patchBranch + " is not an existing git branch!");
            return false;
        }
        if (baseBranch != null && !isExistingGitBranch(new File(localGitRepo), baseBranch)) {
            LOGGER.error("Error: " + baseBranch + " is not an existing git branch!");
            return false;
        }
        if (!listOfProjectsFile.exists()) {
            LOGGER.error("Error: file " + listOfProjectsFile.getName() + " does not exist!");
            return false;
        }

        return true;
    }

    private static boolean isValidCheckstyleConfigsCombination(final String config, final String baseConfig,
                                                               final String patchConfig, final String toolMode) {
        if (config == null && patchConfig == null && baseConfig == null) {
            LOGGER.error("Error: you should specify either 'config', or 'baseConfig', or 'patchConfig'!");
            return false;
        }
        if (config != null && (patchConfig != null || baseConfig != null)) {
            LOGGER.error("Error: you should specify either 'config', or 'baseConfig' and 'patchConfig', or 'patchConfig' only!");
            return false;
        }
        if ("diff".equals(toolMode) && baseConfig != null && patchConfig == null) {
            LOGGER.error("Error: 'patchConfig' should be specified!");
            return false;
        }
        if ("diff".equals(toolMode) && patchConfig != null && baseConfig == null) {
            LOGGER.error("Error: 'baseConfig' should be specified!");
            return false;
        }
        if ("single".equals(toolMode) && (baseConfig != null || config != null)) {
            LOGGER.error("Error: 'baseConfig' and/or 'config' should not be used in 'single' mode!");
            return false;
        }
        return true;
    }

    private static boolean isValidGitRepo(final File gitRepoDir) {
        if (gitRepoDir.exists() && gitRepoDir.isDirectory()) {
            try {
                final ProcessBuilder processBuilder = new ProcessBuilder("git", "status");
                processBuilder.directory(gitRepoDir);
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                return exitCode == 0;
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error: '" + gitRepoDir.getPath() + "' is not a git repository!");
                return false;
            }
        } else {
            LOGGER.error("Error: '" + gitRepoDir.getPath() + "' does not exist or it is not a directory!");
            return false;
        }
    }

    private static boolean isExistingGitBranch(final File gitRepo, final String branchName) {
        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "rev-parse", "--verify", branchName);
            processBuilder.directory(gitRepo);
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.error("Error: git repository " + gitRepo.getPath() + " does not have a branch with name '" + branchName + "'!");
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error checking branch existence: " + e.getMessage());
            return false;
        }
    }

    private static void copyConfigFilesAndUpdatePaths(final List<String> configFilesList) throws IOException {
        if (configFilesList == null) {
            throw new IllegalArgumentException("configFilesList is null");
        }
        final Set<String> uniqueFiles = new LinkedHashSet<>();
        for (final String filename : configFilesList) {
            if (filename != null && !filename.isEmpty()) {
                uniqueFiles.add(filename);
            }
        }
        final File checkstyleTesterDir = new File("").getCanonicalFile();
        for (final String filename : uniqueFiles) {
            final Path sourceFile = Paths.get(filename);
            final Path fileName = sourceFile.getFileName();
            if (fileName != null) {
                final Path destFile = Paths.get(checkstyleTesterDir.getPath(), fileName.toString());
                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                LOGGER.error("Skipping invalid file path: " + filename);
            }
        }
    }

    private static boolean hasUnstagedChanges(final File gitRepo) {
        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "diff", "--exit-code");
            processBuilder.directory(gitRepo);
            final Process process = processBuilder.start();
            final int exitCode = process.waitFor();
            if (exitCode == 0) {
                return false;
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

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
        } catch (IOException | InterruptedException e) {
            LOGGER.error("Error checking for unstaged changes: " + e.getMessage());
            return true;
        }
    }

    private static String getCheckstyleVersionFromPomXml(final String pathToPomXml, final String xmlTagName) {
        try {
            final List<String> lines = Files.readAllLines(Paths.get(pathToPomXml));
            for (final String line : lines) {
                if (line.matches("^.*<" + xmlTagName + ">.*-SNAPSHOT</" + xmlTagName + ">.*")) {
                    final int start = line.indexOf('>') + 1;
                    final int end = line.lastIndexOf('<');
                    return line.substring(start, end);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error reading POM file: " + e.getMessage());
        }
        return null;
    }

    private static CheckstyleReportInfo launchCheckstyleReport(final Map<String, Object> cfg) throws IOException, InterruptedException {
        CheckstyleReportInfo reportInfo = null;
        final boolean isRegressionTesting = cfg.get("branch") != null && cfg.get("localGitRepo") != null;

        if (isRegressionTesting) {
            LOGGER.info("Installing Checkstyle artifact (" + cfg.get("branch") + ") into local Maven repository ...");
            executeCmd("git checkout " + cfg.get("branch"), (File) cfg.get("localGitRepo"));
            executeCmd("git log -1 --pretty=MSG:%s%nSHA-1:%H", (File) cfg.get("localGitRepo"));
            executeCmd("mvn -e --no-transfer-progress --batch-mode -Pno-validations clean install", (File) cfg.get("localGitRepo"));
        }

        cfg.put("checkstyleVersion", getCheckstyleVersionFromPomXml(cfg.get("localGitRepo") + "/pom.xml", "version"));

        generateCheckstyleReport(cfg);

        LOGGER.info("Moving Checkstyle report into " + cfg.get("destDir") + " ...");
        moveDir("reports", (String) cfg.get("destDir"));

        if (isRegressionTesting) {
            reportInfo = new CheckstyleReportInfo(
                    (String) cfg.get("branch"),
                    getLastCheckstyleCommitSha((File) cfg.get("localGitRepo"), (String) cfg.get("branch")),
                    getLastCommitMsg((File) cfg.get("localGitRepo"), (String) cfg.get("branch")),
                    getLastCommitTime((File) cfg.get("localGitRepo"), (String) cfg.get("branch"))
            );
        }
        return reportInfo;
    }

    private static void generateCheckstyleReport(final Map<String, Object> cfg) throws InterruptedException {
        LOGGER.info("Testing Checkstyle started");

        final String targetDir = "target";
        final String srcDir = getOsSpecificPath("src", "main", "java");
        final String reposDir = "repositories";
        final String reportsDir = "reports";
        makeWorkDirsIfNotExist(srcDir, reposDir, reportsDir);
        final int repoNameParamNo = 0;
        final int repoTypeParamNo = 1;
        final int repoURLParamNo = 2;
        final int repoCommitIDParamNo = 3;
        final int repoExcludesParamNo = 4;
        final int fullParamListSize = 5;

        final String checkstyleConfig = (String) cfg.get("checkstyleCfg");
        final String checkstyleVersion = (String) cfg.get("checkstyleVersion");
        final boolean allowExcludes = (boolean) cfg.get("allowExcludes");
        final boolean useShallowClone = (boolean) cfg.get("useShallowClone");
        final String listOfProjects = (String) cfg.get("listOfProjects");
        final String extraMvnRegressionOptions = (String) cfg.get("extraMvnRegressionOptions");

        try {
            final List<String> projects = Files.readAllLines(Paths.get(listOfProjects));
            for (final String project : projects) {
                if (!project.startsWith("#") && !project.isEmpty()) {
                    final String[] params = project.split("\\|", -1);
                    if (params.length < fullParamListSize) {
                        throw new IllegalArgumentException("Error: line '" + project + "' in file '" + listOfProjects + "' should have " + fullParamListSize + " pipe-delimited sections!");
                    }

                    final String repoName = params[repoNameParamNo];
                    final String repoType = params[repoTypeParamNo];
                    final String repoUrl = params[repoURLParamNo];
                    final String commitId = params[repoCommitIDParamNo];

                    String excludes = "";
                    if (allowExcludes) {
                        excludes = params[repoExcludesParamNo];
                    }

                    deleteDir(srcDir);
                    if ("local".equals(repoType)) {
                        copyDir(repoUrl, getOsSpecificPath(srcDir, repoName));
                    } else {
                        if (useShallowClone && !isGitSha(commitId)) {
                            shallowCloneRepository(repoName, repoType, repoUrl, commitId, reposDir);
                        } else {
                            cloneRepository(repoName, repoType, repoUrl, commitId, reposDir);
                        }
                        copyDir(getOsSpecificPath(reposDir, repoName), getOsSpecificPath(srcDir, repoName));
                    }
                    runMavenExecution(srcDir, excludes, checkstyleConfig, checkstyleVersion, extraMvnRegressionOptions);
                    String repoPath = repoUrl;
                    if (!"local".equals(repoType)) {
                        repoPath = new File(getOsSpecificPath(reposDir, repoName)).getAbsolutePath();
                    }
                    postProcessCheckstyleReport(targetDir, repoName, repoPath);
                    deleteDir(getOsSpecificPath(srcDir, repoName));
                    moveDir(targetDir, getOsSpecificPath(reportsDir, repoName));
                }
            }

            // Restore empty_file to make src directory tracked by git
            final File emptyFile = new File(getOsSpecificPath(srcDir, "empty_file"));
            if (!emptyFile.createNewFile()) {
                LOGGER.warn("Failed to create or already existing 'empty_file' in " + srcDir);
            }
        } catch (IOException e) {
            LOGGER.error("Error processing projects: " + e.getMessage());
        }
    }

    private static String getLastCheckstyleCommitSha(final File gitRepo, final String branch)
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
                } else {
                    LOGGER.error("No output from git rev-parse HEAD");
                    return "";
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error getting last commit SHA: " + e.getMessage());
            return "";
        }
    }


    private static String getLastCommitMsg(final File gitRepo, final String branch)
            throws IOException, InterruptedException {
        executeCmd("git checkout " + branch, gitRepo);

        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "log", "-1", "--pretty=%B");
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                } else {
                    return "";
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error getting last commit message: " + e.getMessage());
            return "";
        }
    }


    private static String getLastCommitTime(final File gitRepo, final String branch)
            throws IOException, InterruptedException {
        executeCmd("git checkout " + branch, gitRepo);

        try {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "log", "-1", "--format=%cd");
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true);
            final Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String line = reader.readLine();
                if (line != null) {
                    return line.trim();
                } else {
                    return "";
                }
            }
        } catch (IOException e) {
            LOGGER.error("Error getting last commit time: " + e.getMessage());
            return "";
        }
    }


    private static String getCommitSha(final String commitId, final String repoType, final String srcDestinationDir) {
        final String cmd;
        if ("git".equals(repoType)) {
            cmd = "git rev-parse " + commitId;
        } else {
            throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
        }

        try {
            // Use ProcessBuilder instead of Runtime#exec
            final ProcessBuilder processBuilder = new ProcessBuilder(cmd.split("\\s+"));
            processBuilder.directory(new File(srcDestinationDir));
            processBuilder.redirectErrorStream(true); // Redirect error stream to output stream
            final Process process = processBuilder.start();

            // Use InputStreamReader with explicit charset
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String sha = reader.readLine();
                return sha != null ? sha.replace("\n", "") : "";
            }
        } catch (IOException e) {
            LOGGER.error("Error getting commit SHA: " + e.getMessage());
            return "";
        }
    }

    private static void shallowCloneRepository(final String repoName, final String repoType,
                        final String repoUrl, final String commitId, final String srcDir)
                        throws IOException {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd = getCloneShallowCmd(repoType, repoUrl, srcDestinationDir, commitId);
            LOGGER.info("Shallow clone " + repoType + " repository '" + repoName + "' to " + srcDestinationDir + " folder ...");
            executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
        }
        LOGGER.info(repoName + " is synchronized");
    }

    private static void cloneRepository(final String repoName, final String repoType,
                        final String repoUrl, final String commitId, final String srcDir)
                        throws IOException, InterruptedException {
        final String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            final String cloneCmd = getCloneCmd(repoType, repoUrl, srcDestinationDir);
            LOGGER.info("Cloning " + repoType + " repository '" + repoName + "' to " + srcDestinationDir + " folder ...");
            executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
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
                executeCmd(resetCmd, new File(srcDestinationDir));
            }
        }
        LOGGER.info(repoName + " is synchronized");
    }

    private static String getCloneCmd(final String repoType, final String repoUrl, final String srcDestinationDir) {
        if ("git".equals(repoType)) {
            return "git clone " + repoUrl + " " + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static String getCloneShallowCmd(final String repoType, final String repoUrl,
                          final String srcDestinationDir, final String commitId) {
        if ("git".equals(repoType)) {
            return "git clone --depth 1 --branch " + commitId + " " + repoUrl + " " + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown repository type: " + repoType);
    }

    private static void fetchAdditionalData(final String repoType, final String srcDestinationDir,
                                            final String commitId) throws IOException, InterruptedException {
        final String fetchCmd;
        if ("git".equals(repoType)) {
            if (isGitSha(commitId)) {
                fetchCmd = "git fetch";
            } else {
                // Check if commitId is a tag and handle accordingly
                if (isTag(commitId, new File(srcDestinationDir))) {
                    fetchCmd = "git fetch --tags";
                } else {
                    fetchCmd = "git fetch origin " + commitId + ":" + commitId;
                }
            }
        } else {
            throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
        }

        executeCmd(fetchCmd, new File(srcDestinationDir));
    }

    private static boolean isTag(final String commitId, final File gitRepo) {
        try {
            // Use ProcessBuilder instead of Runtime#exec
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "tag", "-l", commitId);
            processBuilder.directory(gitRepo);
            processBuilder.redirectErrorStream(true); // Redirect error stream to output stream
            final Process process = processBuilder.start();

            // Use InputStreamReader with explicit charset
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String result = reader.readLine();
                return result != null && result.trim().equals(commitId);
            }
        } catch (IOException e) {
            LOGGER.error("Error checking if commit is a tag: " + e.getMessage());
            return false;
        }
    }

    // it is not very accurate match, but in case of mismatch we will do full clone
    private static boolean isGitSha(final String value) {
        return value.matches("[0-9a-f]{5,40}");
    }

    private static void executeCmdWithRetry(final String cmd, final File dir, final int retry) {
        final String osSpecificCmd = getOsSpecificCmd(cmd);
        int left = retry;
        while (left > 0) {
            try {
                final ProcessBuilder processBuilder = new ProcessBuilder(osSpecificCmd.split("\\s+"));
                processBuilder.directory(dir);
                processBuilder.inheritIO();
                final Process process = processBuilder.start();
                final int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return;
                }
                left--;
                if (left > 0) {
                    TimeUnit.SECONDS.sleep(15);
                }
            } catch (IOException | InterruptedException e) {
                LOGGER.error("Error executing command: " + e.getMessage());
                left--;
            }
        }
        throw new IllegalStateException("Error executing command: " + cmd);
    }

    private static void executeCmdWithRetry(final String cmd) {
        executeCmdWithRetry(cmd, new File("").getAbsoluteFile(), 5);
    }

    private static void generateDiffReport(final Map<String, Object> cfg) throws Exception {
        final Path currentDir = Paths.get("").toAbsolutePath();
        final Path parentDir = currentDir.getParent();
        if (parentDir == null) {
            throw new IllegalStateException("Unable to locate parent directory");
        }
        final Path diffToolDir = parentDir.resolve("patch-diff-report-tool");
        final InvocationResult result = executeMavenBuild(diffToolDir);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Maven build failed");
        }

        final String diffToolJarPath = getPathToDiffToolJar(diffToolDir.toFile());
        // The null check here is removed as getPathToDiffToolJar should throw an exception if the JAR is not found

        final String patchReportsDir = (String) cfg.get("patchReportsDir");
        if (patchReportsDir == null) {
            throw new IllegalArgumentException("Patch reports directory path is not provided in the configuration.");
        }

        LOGGER.info("Starting diff report generation ...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(patchReportsDir))) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    processProjectDirectory(cfg, diffToolJarPath, path);
                }
            }
        } catch (IOException ex) {
            LOGGER.error("Failed to read the patch reports directory: " + ex.getMessage());
            throw ex;
        }
        LOGGER.info("Diff report generation finished ...");
    }

    private static InvocationResult executeMavenBuild(final Path diffToolDir) throws Exception {
        final InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(diffToolDir.toFile(), "pom.xml"));
        request.setGoals(Collections.singletonList("package"));
        request.setProfiles(Collections.singletonList("no-validations"));
        request.setMavenOpts("-DskipTests=true");

        final Invoker invoker = new DefaultInvoker();
        return invoker.execute(request);
    }

    private static void processProjectDirectory(final Map<String, Object> cfg, final String diffToolJarPath, final Path path) throws Exception {
        final Path fileNamePath = path.getFileName();
        if (fileNamePath != null) {
            final String projectName = fileNamePath.toString();
            final File patchReportDir = new File((String) cfg.get("patchReportsDir"), projectName);
            if (patchReportDir.exists()) {
                generateProjectDiffReport(cfg, diffToolJarPath, projectName);
            } else {
                throw new FileNotFoundException("Error: patch report for project " + projectName + " is not found!");
            }
        } else {
            throw new FileNotFoundException("Error: Path does not have a file name.");
        }
    }


    private static void generateProjectDiffReport(final Map<String, Object> cfg, final String diffToolJarPath, final String projectName) throws Exception {
        final String patchReport = Paths.get((String) cfg.get("patchReportsDir"), projectName, "checkstyle-result.xml").toString();
        final String outputDir = Paths.get((String) cfg.get("reportsDir"), "diff", projectName).toString();

        logConfigContents((String) cfg.get("patchConfig"));
        if ("diff".equals(cfg.get("mode"))) {
            logConfigContents((String) cfg.get("baseConfig"));
        }

        // Specify the locale explicitly (Locale.getDefault() or a specific one like Locale.US)
        final StringBuilder diffCmdBuilder = new StringBuilder(String.format(Locale.getDefault(),
                "java -jar %s --patchReport %s --output %s --patchConfig %s",
                diffToolJarPath, patchReport, outputDir, new File((String) cfg.get("patchConfig")).getName()));

        if ("diff".equals(cfg.get("mode"))) {
            final String baseReport = Paths.get((String) cfg.get("masterReportsDir"), projectName, "checkstyle-result.xml").toString();
            diffCmdBuilder.append(String.format(Locale.getDefault(),
                    " --baseReport %s --baseConfig %s", baseReport, new File((String) cfg.get("baseConfig")).getName()));
        }

        if ((boolean) cfg.get("shortFilePaths")) {
            diffCmdBuilder.append(" --shortFilePaths");
        }

        executeCmd(diffCmdBuilder.toString());
    }

    // Method to log the contents of config files
    private static void logConfigContents(final String configPath) {
        try {
            LOGGER.info("Contents of " + configPath + ":");
            final List<String> lines = Files.readAllLines(Paths.get(configPath));
            lines.forEach(System.out::println);
        } catch (IOException e) {
            LOGGER.error("Error reading config file: " + e.getMessage());
        }
    }

    private static String getPathToDiffToolJar(final File diffToolDir) throws IOException {
        final Path targetDir = Paths.get(diffToolDir.getAbsolutePath(), "target");
        if (!Files.exists(targetDir)) {
            throw new FileNotFoundException("Error: target directory does not exist!");
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (final Path path : stream) {
                final Path fileNamePath = path.getFileName();
                if (fileNamePath != null) {
                    final String fileName = fileNamePath.toString();
                    if (fileName.matches("patch-diff-report-tool-.*.jar-with-dependencies.jar")) {
                        return path.toAbsolutePath().toString();
                    }
                }
            }
        }
        throw new FileNotFoundException("Error: diff tool jar file is not found!");
    }

    private static Object getTextTransform() throws IOException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        final Path currentDir = Paths.get("").toAbsolutePath();
        final Path parentDir = currentDir.getParent();
        if (parentDir == null) {
            LOGGER.error("Unable to locate parent directory");
            return null;
        }

        final Path diffToolDir = parentDir.resolve("patch-diff-report-tool");
        final String diffToolJarPath = getPathToDiffToolJar(diffToolDir.toFile());

        try {
            final URL[] urls = { URI.create("file:" + diffToolJarPath).toURL() };
            try (URLClassLoader classLoader = new URLClassLoader(urls)) {
                final Class<?> clazz = classLoader.loadClass("com.github.checkstyle.site.TextTransform");
                return clazz.getDeclaredConstructor().newInstance();
            }
        } catch (IOException | NoSuchMethodException ex) {
            LOGGER.error("Error loading TextTransform: " + ex.getMessage());
            return null;
        }
    }

    private static void generateSummaryIndexHtml(final String diffDir, final CheckstyleReportInfo checkstyleBaseReportInfo,
                                                 final CheckstyleReportInfo checkstylePatchReportInfo, final List<String> configFilesList,
                                                 final boolean allowExcludes) throws IOException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        LOGGER.info("Starting creating report summary page ...");
        final Map<String, int[]> projectsStatistic = getProjectsStatistic(diffDir);
        final Path summaryIndexHtmlPath = Paths.get(diffDir, "index.html");

        try (BufferedWriter writer = Files.newBufferedWriter(summaryIndexHtmlPath, StandardCharsets.UTF_8)) {
            writer.write("<html><head>");
            writer.write("<link rel=\"icon\" href=\"https://checkstyle.org/images/favicon.png\" type=\"image/x-icon\" />");
            writer.write("<title>Checkstyle Tester Report Diff Summary</title>");
            writer.write("</head><body>");
            writer.write("\n");
            if (!allowExcludes) {
                writer.write("<h3><span style=\"color: #ff0000;\">");
                writer.write("<strong>WARNING: Excludes are ignored by diff.groovy.</strong>");
                writer.write("</span></h3>");
            }
            printReportInfoSection(writer, checkstyleBaseReportInfo, checkstylePatchReportInfo, projectsStatistic);
            printConfigSection(diffDir, configFilesList, writer);

            final List<Map.Entry<String, int[]>> sortedProjects = new ArrayList<>(projectsStatistic.entrySet());
            sortedProjects.sort(Comparator
                    .comparing((Map.Entry<String, int[]> e) -> e.getKey().toLowerCase(Locale.getDefault()))
                    .thenComparing(e -> e.getValue()[0] == 0 ? 1 : 0));

            for (final Map.Entry<String, int[]> entry : sortedProjects) {
                final String project = entry.getKey();
                final int[] diffCount = entry.getValue();
                writer.write("<a href='" + project + "/index.html'>" + project + "</a>");
                if (diffCount[0] != 0) {
                    if (diffCount[1] == 0) {
                        writer.write(String.format(Locale.getDefault(), " ( &#177;%d, <span style=\"color: red;\">-%d</span> )", diffCount[0], diffCount[2]));
                    } else if (diffCount[2] == 0) {
                        writer.write(String.format(Locale.getDefault(), " ( &#177;%d, <span style=\"color: green;\">+%d</span> )", diffCount[0], diffCount[1]));
                    } else {
                        writer.write(String.format(Locale.getDefault(), " ( &#177;%d, <span style=\"color: red;\">-%d</span>, <span style=\"color: green;\">+%d</span> )",
                                diffCount[0], diffCount[2], diffCount[1]));
                    }
                }
                writer.write("<br />");
                writer.write("\n");
            }
            writer.write("</body></html>");
        }

        LOGGER.info("Creating report summary page finished...");
    }

    private static void printConfigSection(final String diffDir, final List<String> configFilesList,
                        final BufferedWriter summaryIndexHtml) throws IOException, InvocationTargetException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        final Object textTransform = getTextTransform();
        final Set<String> processedConfigs = new HashSet<>();
        for (final String filename : configFilesList) {
            if (filename != null && !filename.isEmpty() && !processedConfigs.contains(filename)) {
                final File configFile = new File(filename);
                generateAndPrintConfigHtmlFile(diffDir, configFile, textTransform, summaryIndexHtml);
                processedConfigs.add(filename);
            }
        }
    }

    private static void generateAndPrintConfigHtmlFile(final String diffDir, final File configFile,
                        final Object textTransform, final BufferedWriter summaryIndexHtml) throws IOException {
        if (!configFile.exists()) {
            return;
        }

        final String configFileNameWithoutExtension = getFilenameWithoutExtension(configFile.getName());
        final File configFileHtml = new File(diffDir, configFileNameWithoutExtension + ".html");

        if (textTransform != null) {
            try {
                textTransform.getClass().getMethod("transform", String.class, String.class, Locale.class, String.class, String.class)
                        .invoke(textTransform, configFile.getAbsolutePath(), configFileHtml.getAbsolutePath(), Locale.ENGLISH, "UTF-8", "UTF-8");
            } catch (Exception e) {
                LOGGER.error("Error transforming config file: " + e.getMessage());
            }
        } else {
            LOGGER.error("TextTransform object is null. Skipping transformation.");
            // Fallback: copy the content of the config file to the HTML file
            Files.copy(configFile.toPath(), configFileHtml.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        summaryIndexHtml.write("<h6>");
        summaryIndexHtml.write("<a href='" + configFileHtml.getName() + "'>" + configFile.getName() + " file</a>");
        summaryIndexHtml.write("</h6>");
    }

    private static String getFilenameWithoutExtension(final String filename) {
        final int pos = filename.lastIndexOf('.');
        if (pos > 0) {
            return filename.substring(0, pos);
        }
        return filename;
    }

    private static void makeWorkDirsIfNotExist(final String srcDirPath,
                                               final String repoDirPath, final String reportsDirPath) {
        final File srcDir = new File(srcDirPath);
            if (!srcDir.mkdirs()) {
                LOGGER.error("Failed to create source directory: " + srcDirPath);
                // Optionally throw an exception or handle the error
            }
        final File repoDir = new File(repoDirPath);
            if (!repoDir.mkdir()) {
                LOGGER.error("Failed to create repository directory: " + repoDirPath);
                // Optionally throw an exception or handle the error
            }
        final File reportsDir = new File(reportsDirPath);
            if (!reportsDir.mkdir()) {
                LOGGER.error("Failed to create reports directory: " + reportsDirPath);
                // Optionally throw an exception or handle the error
            }
    }

    private static void printReportInfoSection(final BufferedWriter summaryIndexHtml, final CheckstyleReportInfo checkstyleBaseReportInfo,
                       final CheckstyleReportInfo checkstylePatchReportInfo, final Map<String, int[]> projectsStatistic) throws IOException {
        final Date date = new Date();
        summaryIndexHtml.write("<h6>");
        if (checkstyleBaseReportInfo != null) {
            summaryIndexHtml.write("Base branch: " + checkstyleBaseReportInfo.getBranch());
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit SHA: " + checkstyleBaseReportInfo.getCommitSha());
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit message: \"" + checkstyleBaseReportInfo.getCommitMsg() + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit timestamp: \"" + checkstyleBaseReportInfo.getCommitTime() + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("<br />");
        }
        summaryIndexHtml.write("Patch branch: " + checkstylePatchReportInfo.getBranch());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit SHA: " + checkstylePatchReportInfo.getCommitSha());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit message: \"" + checkstylePatchReportInfo.getCommitMsg() + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit timestamp: \"" + checkstylePatchReportInfo.getCommitTime() + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Tested projects: " + projectsStatistic.size());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("&#177; differences found: " + projectsStatistic.values().stream().mapToInt(arr -> arr[0]).sum());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Time of report generation: " + date);
        summaryIndexHtml.write("</h6>");
    }

    private static Map<String, int[]> getProjectsStatistic(final String diffDir) throws IOException {
        final Map<String, int[]> projectsStatistic = new ConcurrentHashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(diffDir))) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    processProjectStatistics(path, projectsStatistic);
                }
            }
        }
        return projectsStatistic;
    }

    private static void processProjectStatistics(final Path path, final Map<String, int[]> projectsStatistic) throws IOException {
        final Path fileNamePath = path.getFileName();
        if (fileNamePath != null) {
            final String projectName = fileNamePath.toString();
            final File indexHtmlFile = new File(path.toFile(), "index.html");
            if (indexHtmlFile.exists()) {
                final List<String> lines = Files.readAllLines(indexHtmlFile.toPath());
                final int[] diffStats = extractDiffStats(lines);
                if (diffStats != null) {
                    projectsStatistic.put(projectName, diffStats);
                }
            }
        }
    }


    private static int[] extractDiffStats(final List<String> lines) {
        int addedDiff = 0;
        int removedDiff = 0;
        int totalDiff = 0;
        boolean totalDiffFound = false;
        for (final String line : lines) {
            if (line.contains("id=\"totalPatch\"")) {
                addedDiff = extractDiffCount(line, "(?<totalAdd>[0-9]++) added");
                removedDiff = extractDiffCount(line, "(?<totalRemoved>[0-9]++) removed");
            } else if (line.contains("totalDiff")) {
                totalDiff = extractTotalDiff(line);
                totalDiffFound = true;
                break;
            }
        }
        return totalDiffFound ? new int[]{totalDiff, addedDiff, removedDiff} : new int[]{0, 0, 0};
    }

    private static int extractDiffCount(final String line, final String regex) {
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static int extractTotalDiff(final String line) {
        final Pattern linePattern = Pattern.compile("totalDiff\">(?<totalDiff>[0-9]++)");
        final Matcher lineMatcher = linePattern.matcher(line);
        return lineMatcher.find() ? Integer.parseInt(lineMatcher.group("totalDiff")) : 0;
    }

    private static void runMavenExecution(final String srcDir, final String excludes, final String checkstyleConfig, final String checkstyleVersion, final String extraMvnRegressionOptions) throws IOException, InterruptedException {
        LOGGER.info("Running 'mvn clean' on " + srcDir + " ...");
        final String mvnClean = "mvn -e --no-transfer-progress --batch-mode clean";
        executeCmd(mvnClean);
        LOGGER.info("Running Checkstyle on " + srcDir + " ... with excludes {" + excludes + "}");
        final StringBuilder mvnSite = new StringBuilder(200)
                .append("mvn -e --no-transfer-progress --batch-mode site ")
                .append("-Dcheckstyle.config.location=").append(checkstyleConfig)
                .append(" -Dcheckstyle.excludes=").append(excludes);
        if (checkstyleVersion != null && !checkstyleVersion.isEmpty()) {
            mvnSite.append(" -Dcheckstyle.version=").append(checkstyleVersion);
        }
        if (extraMvnRegressionOptions != null && !extraMvnRegressionOptions.isEmpty()) {
            mvnSite.append(' ');
            if (!extraMvnRegressionOptions.startsWith("-")) {
                mvnSite.append('-');
            }
            mvnSite.append(extraMvnRegressionOptions);
        }
        LOGGER.info(mvnSite.toString());
        executeCmd(mvnSite.toString());
        LOGGER.info("Running Checkstyle on " + srcDir + " - finished");
    }

    private static void postProcessCheckstyleReport(final String targetDir,
                    final String repoName, final String repoPath) throws IOException {
        final Path reportPath = Paths.get(targetDir, "checkstyle-result.xml");
        String content = new String(Files.readAllBytes(reportPath), StandardCharsets.UTF_8);
        content = content.replace(new File(getOsSpecificPath("src", "main", "java", repoName)).getAbsolutePath(),
                getOsSpecificPath(repoPath));
        Files.write(reportPath, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyDir(final String source, final String destination) throws IOException {
        final Path sourcePath = Paths.get(source);
        final Path destinationPath = Paths.get(destination);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                final Path targetPath = destinationPath.resolve(sourcePath.relativize(dir));
                Files.createDirectories(targetPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destinationPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveDir(final String source, final String destination) throws IOException {
        final Path sourcePath = Paths.get(source);
        final Path destinationPath = Paths.get(destination);

        // Create the destination directory if it doesn't exist
        Files.createDirectories(destinationPath);

        if (Files.exists(sourcePath)) {
            // If source exists, move its contents
            try (Stream<Path> stream = Files.list(sourcePath)) {
                stream.forEach(file -> {
                    try {
                        Files.move(file, destinationPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        LOGGER.error("Error moving file " + file + ": " + e.getMessage());
                    }
                });
            }
            // Delete the now-empty source directory
            Files.deleteIfExists(sourcePath);
        } else {
            LOGGER.info("Source directory " + source + " does not exist. Skipping move operation.");
        }
    }

    private static void deleteDir(final String dir) throws IOException {
        final Path directory = Paths.get(dir);
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void executeCmd(final String cmd, final File dir) throws IOException, InterruptedException {
        LOGGER.info("Running command: " + cmd);
        final ProcessBuilder processBuilder = new ProcessBuilder(getOsSpecificCmd(cmd).split("\\s+"));
        processBuilder.directory(dir);
        processBuilder.inheritIO();
        final Process process = processBuilder.start();
        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new CommandExecutionException("Command execution failed", exitCode);
        }
    }

    private static void executeCmd(final String cmd) throws IOException, InterruptedException {
        executeCmd(cmd, new File("").getAbsoluteFile());
    }

    private static String getOsSpecificCmd(final String cmd) {
        if (System.getProperty("os.name").toLowerCase(Locale.getDefault()).contains("windows")) {
            return "cmd /c " + cmd;
        }
        return cmd;
    }

    private static String getOsSpecificPath(final String... name) {
        return String.join(File.separator, name);
    }

    private static String getResetCmd(final String repoType, final String commitId) {
        if ("git".equals(repoType)) {
            if (isGitSha(commitId)) {
                return "git reset --hard " + commitId;
            } else {
                return "git reset --hard refs/tags/" + commitId;
            }
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static String getLastProjectCommitSha(final String repoType, final String srcDestinationDir) throws IOException, InterruptedException {
        if ("git".equals(repoType)) {
            final ProcessBuilder processBuilder = new ProcessBuilder("git", "rev-parse", "HEAD");
            processBuilder.directory(new File(srcDestinationDir));
            final Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                final String sha = reader.readLine();
                process.waitFor();
                return sha != null ? sha.trim() : "";
            }
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static void deleteWorkDirectories(final Config cfg) throws IOException {
        if (new File(cfg.getReportsDir()).exists()) {
            deleteDir(cfg.getReportsDir());
        }
        if (new File(cfg.getTmpReportsDir()).exists()) {
            deleteDir(cfg.getTmpReportsDir());
        }
    }

    /**
     * Represents the configuration for the DiffTool.
     */
    private static class Config {
        /** The local Git repository location */
        private final File localGitRepo;

        /** Whether to use shortened file paths */
        private final boolean shortFilePaths;

        /** The list of projects to analyze */
        private final String listOfProjects;

        /** The mode of operation (e.g., "diff" or "single") */
        private final String mode;

        /** The name of the base branch to compare */
        private final String baseBranch;

        /** The name of the patch branch to compare */
        private final String patchBranch;

        /** The configuration file for the base branch */
        private final String baseConfig;

        /** The configuration file for the patch branch */
        private final String patchConfig;

        /** The general configuration setting */
        private final String configFile;

        /** The directory for storing reports */
        private final String reportsDir;

        /** The directory for storing master branch report files */
        private final String masterReportsDir;

        /** The directory for storing patch branch report files */
        private final String patchReportsDir;

        /** The temporary directory for storing report files */
        private final String tmpReportsDir;

        /** The temporary directory for storing master branch report files */
        private final String tmpMasterReportsDir;

        /** The temporary directory for storing patch branch report files */
        private final String tmpPatchReportsDir;

        /** The directory for storing diff results */
        private final String diffDir;

        /** Additional options for Maven regression */
        private final String extraMvnRegressionOptions;

        /** The version of Checkstyle being used */
        private final String checkstyleVersion;

        /** The version of Sevntu used (if applicable) */
        private final String sevntuVersion;

        /** Whether to allow exclusion rules in the analysis */
        private final boolean allowExcludes;

        /** Whether to use shallow cloning in Git operations */
        private final boolean useShallowClone;

        Config(final CommandLine cliOptions) {
            localGitRepo = cliOptions.hasOption("localGitRepo") ? new File(cliOptions.getOptionValue("localGitRepo")) : new File("");
            shortFilePaths = cliOptions.hasOption("shortFilePaths");
            listOfProjects = cliOptions.getOptionValue("listOfProjects");
            extraMvnRegressionOptions = cliOptions.getOptionValue("extraMvnRegressionOptions");
            checkstyleVersion = cliOptions.getOptionValue("checkstyleVersion");
            allowExcludes = cliOptions.hasOption("allowExcludes");
            useShallowClone = cliOptions.hasOption("useShallowClone");
            mode = cliOptions.getOptionValue("mode", "diff");
            baseBranch = cliOptions.getOptionValue("baseBranch", "master");
            patchBranch = cliOptions.getOptionValue("patchBranch");
            baseConfig = cliOptions.getOptionValue("baseConfig");
            patchConfig = cliOptions.getOptionValue("patchConfig");
            configFile = cliOptions.getOptionValue("config");

            reportsDir = "reports";
            masterReportsDir = reportsDir + "/" + baseBranch;
            patchReportsDir = reportsDir + "/" + patchBranch;
            tmpReportsDir = "tmp_reports";
            tmpMasterReportsDir = tmpReportsDir + "/" + baseBranch;
            tmpPatchReportsDir = tmpReportsDir + "/" + patchBranch;
            diffDir = reportsDir + "/diff";
            sevntuVersion = "";
        }

        // Getters
        public File getLocalGitRepo() { return localGitRepo; }
        public boolean isShortFilePaths() { return shortFilePaths; }
        public String getListOfProjects() { return listOfProjects; }
        public String getMode() { return mode; }
        public String getBaseBranch() { return baseBranch; }
        public String getPatchBranch() { return patchBranch; }
        public String getBaseConfig() { return baseConfig; }
        public String getPatchConfig() { return patchConfig; }
        public String getConfigFile() { return configFile; }
        public String getReportsDir() { return reportsDir; }
        public String getMasterReportsDir() { return masterReportsDir; }
        public String getPatchReportsDir() { return patchReportsDir; }
        public String getTmpReportsDir() { return tmpReportsDir; }
        public String getTmpMasterReportsDir() { return tmpMasterReportsDir; }
        public String getTmpPatchReportsDir() { return tmpPatchReportsDir; }
        public String getDiffDir() { return diffDir; }
        public String getExtraMvnRegressionOptions() { return extraMvnRegressionOptions; }
        public String getCheckstyleVersion() { return checkstyleVersion; }
        public String getSevntuVersion() { return sevntuVersion; }
        public boolean isAllowExcludes() { return allowExcludes; }
        public boolean isUseShallowClone() { return useShallowClone; }


        /**
         * Checks if the tool is in "diff" mode.
         *
         * @return true if mode is "diff", false otherwise.
         */
        public boolean isDiffMode() {
            return "diff".equals(mode);
        }

        /**
         * Checks if the current mode is set to "single".
         *
         * @return true if the mode is "single", false otherwise
         */
        public boolean isSingleMode() {
            return "single".equals(mode);
        }

        /**
         * Generates and returns the base configuration for the Checkstyle tool as a map.
         *
         * @return a map containing key-value pairs for the Checkstyle tool configuration:
         *         - localGitRepo: the local Git repository path
         *         - branch: the base branch to compare
         *         - checkstyleCfg: the Checkstyle configuration file
         *         - listOfProjects: the list of projects to analyze
         *         - destDir: the directory for storing report files
         *         - extraMvnRegressionOptions: additional options for Maven regression
         *         - allowExcludes: whether exclusion rules are allowed
         *         - useShallowClone: whether to use shallow cloning in Git operations
         */
        public Map<String, Object> getCheckstyleToolBaseConfig() {
            final Map<String, Object> config = new ConcurrentHashMap<>();
            config.put("localGitRepo", localGitRepo);
            config.put("branch", baseBranch);
            config.put("checkstyleCfg", baseConfig);
            config.put("listOfProjects", listOfProjects);
            config.put("destDir", tmpMasterReportsDir);
            config.put("extraMvnRegressionOptions", extraMvnRegressionOptions);
            config.put("allowExcludes", allowExcludes);
            config.put("useShallowClone", useShallowClone);
            return config;
        }

        /**
         * Generates and returns the patch configuration for the Checkstyle tool as a map.
         *
         * @return a map containing key-value pairs for the Checkstyle patch configuration:
         *         - localGitRepo: the local Git repository path
         *         - branch: the patch branch to compare
         *         - checkstyleCfg: the Checkstyle configuration file for the patch
         *         - listOfProjects: the list of projects to analyze
         *         - destDir: the directory for storing patch report files
         *         - extraMvnRegressionOptions: additional options for Maven regression
         *         - allowExcludes: whether exclusion rules are allowed
         *         - useShallowClone: whether to use shallow cloning in Git operations
         */
        public Map<String, Object> getCheckstyleToolPatchConfig() {
            final Map<String, Object> config = new ConcurrentHashMap<>();
            config.put("localGitRepo", localGitRepo);
            config.put("branch", patchBranch);
            config.put("checkstyleCfg", patchConfig);
            config.put("listOfProjects", listOfProjects);
            config.put("destDir", tmpPatchReportsDir);
            config.put("extraMvnRegressionOptions", extraMvnRegressionOptions);
            config.put("allowExcludes", allowExcludes);
            config.put("useShallowClone", useShallowClone);
            return config;
        }

        /**
         * Returns a configuration map for the DiffTool.
         * Includes report directories, configs, file paths, mode, and flags.
         *
         * @return map of DiffTool configuration.
         */
        public Map<String, Object> getDiffToolConfig() {
            final Map<String, Object> config = new ConcurrentHashMap<>();
            config.put("reportsDir", reportsDir);
            config.put("masterReportsDir", masterReportsDir);
            config.put("patchReportsDir", patchReportsDir);
            config.put("baseConfig", baseConfig);
            config.put("patchConfig", patchConfig);
            config.put("shortFilePaths", shortFilePaths);
            config.put("mode", mode);
            config.put("allowExcludes", allowExcludes);
            config.put("useShallowClone", useShallowClone);
            return config;
        }
    }

    /**
     * Represents the Checkstyle report information.
     */
    private static class CheckstyleReportInfo {
        /** The name of the branch where the commit resides */
        private final String branch;

        /** The SHA hash of the commit for unique identification */
        private final String commitSha;

        /** The message associated with the commit */
        private final String commitMsg;

        /** The timestamp when the commit was made */
        private final String commitTime;

        CheckstyleReportInfo(final String branch, final String commitSha,
                             final String commitMsg, final String commitTime) {
            this.branch = branch;
            this.commitSha = commitSha;
            this.commitMsg = commitMsg;
            this.commitTime = commitTime;
        }

        public String getBranch() { return branch; }
        public String getCommitSha() { return commitSha; }
        public String getCommitMsg() { return commitMsg; }
        public String getCommitTime() { return commitTime; }
    }

    /**
     * Custom runtime exception for handling command execution failures.
     * Includes the exit code in the error message.
     */
    public static class CommandExecutionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a CommandExecutionException with a message and exit code.
         */
        public CommandExecutionException(final String message, final int exitCode) {
            super(message + " (Exit code: " + exitCode + ")");
        }
    }
}

