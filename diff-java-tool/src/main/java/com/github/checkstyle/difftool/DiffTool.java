///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2024 the original author or authors.
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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Main class for the DiffTool application.
 */
public final class DiffTool {

    /**
     * Logger instance for logging messages specific to DiffTool operations.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(DiffTool.class);

    /**
     * The minimum number of fields required in a project properties line.
     * Lines with fewer fields are considered invalid and will be skipped.
     */
    private static final int MIN_FIELD_COUNT = 3;

    /**
     * The index of the repository name in the properties line.
     */
    private static final int REPO_NAME_INDEX = 0;

    /**
     * The index of the SCM (Source Control Management) type in the properties line.
     */
    private static final int SCM_INDEX = 1;

    /**
     * The index of the URL in the properties line.
     */
    private static final int URL_INDEX = 2;

    /**
     * The index of the reference in the properties line.
     * This field is optional and may not be present in all lines.
     */
    private static final int REFERENCE_INDEX = 3;

    /**
     * The index of the exclude folders in the properties line.
     * This field is optional and may not be present in all lines.
     */
    private static final int EXCLUDE_FOLDERS_INDEX = 4;

    /**
     * The delimiter used to split fields in a properties line.
     * Fields are separated by a pipe character "|".
     */
    private static final String FIELD_DELIMITER = "\\|";

    /**
     * The delimiter used to split exclude folders.
     * Exclude folders are separated by commas ",".
     */
    private static final String EXCLUDE_DELIMITER = ",";

    /**
     * Number of times a command will be retried if it fails.
     */
    private static final int DEFAULT_RETRY_COUNT = 5;

    /**
     * Time (in seconds) to wait between retry attempts.
     */
    private static final int SLEEP_DURATION_SECONDS = 15;

    /**
     * Initial capacity for the StringBuilder to build the Maven command.
     */
    private static final int STRING_BUILDER_CAPACITY = 200;

    /** Private constructor to prevent instantiation of this utility class. */
    private DiffTool() {
        // Utility class, hide constructor
    }

    /**
     * Main method to run the DiffTool application.
     *
     * @param args Command line arguments
     * @throws Exception if an error occurs during the execution of the DiffTool
     */
    public static void main(final String[] args) throws Exception {
        try {
            final CommandLine cliOptions = getCliOptions(args);
            if (cliOptions == null || !areValidCliOptions(cliOptions)) {
                LOGGER.error("Error: invalid command line arguments!");
                System.exit(1);
            }

            final Config cfg = new Config(cliOptions);
            final List<String> configFilesList =
                    Arrays.asList(cfg.getConfigFile(),
                            cfg.getBaseConfig(),
                            cfg.getPatchConfig(),
                            cfg.getListOfProjects());
            copyConfigFilesAndUpdatePaths(configFilesList);

            if (cfg.getLocalGitRepo() != null && hasUnstagedChanges(cfg.getLocalGitRepo())) {
                LOGGER.error("Error: git repository "
                        + cfg.getLocalGitRepo().getPath()
                        + " has unstaged changes!");
                System.exit(1);
            }

            // Delete work directories to avoid conflicts with previous reports generation
            deleteWorkDirectories(cfg);

            CheckstyleReportInfo checkstyleBaseReportInfo = null;
            if (cfg.isDiffMode()) {
                checkstyleBaseReportInfo =
                        launchCheckstyleReport(cfg.getCheckstyleToolBaseConfig());
            }

            final CheckstyleReportInfo checkstylePatchReportInfo =
                    launchCheckstyleReport(cfg.getCheckstyleToolPatchConfig());

            if (checkstylePatchReportInfo != null) {
                deleteDir(cfg.getReportsDir());
                moveDir(cfg.getTmpReportsDir(), cfg.getReportsDir());

                generateDiffReport(cfg.getDiffToolConfig());
                generateSummaryIndexHtml(
                        cfg.getDiffDir(),
                        checkstyleBaseReportInfo,
                        checkstylePatchReportInfo,
                        configFilesList,
                        cfg.isAllowExcludes(),
                        cfg.getDiffToolJarPath());
            }
        }
        catch (IOException | InterruptedException ex) {
            LOGGER.error("Error: " + ex.getMessage(), ex);
            System.exit(1);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            LOGGER.error("Error in application state or arguments: " + ex.getMessage(), ex);
            System.exit(1);
        }
    }

    /**
     * Parses the projects file (YAML or properties) and returns a list of projects.
     *
     * @param filePath The path to the projects file.
     * @return A list of Project instances.
     * @throws IOException If an I/O error occurs while reading the file.
     * @throws IllegalArgumentException If unsupported project found.
     */
    private static List<Project> parseProjects(final String filePath) throws IOException {
        if (filePath.endsWith(".yml") || filePath.endsWith(".yaml")) {
            return parseProjectsFromYaml(filePath);
        }
        else if (filePath.endsWith(".properties")) {
            return parseProjectsFromProperties(filePath);
        }
        else {
            throw new IllegalArgumentException("Unsupported projects file format: " + filePath);
        }
    }

    /**
     * Parses the YAML file containing the list of projects.
     *
     * @param filePath The path to the YAML file.
     * @return A list of Project instances parsed from the YAML file.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    private static List<Project> parseProjectsFromYaml(final String filePath) throws IOException {
        final Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            final Map<String, Object> yamlData = yaml.load(inputStream);
            @SuppressWarnings("unchecked")
            final List<Map<String, Object>> projectsData =
                    (List<Map<String, Object>>) yamlData.get("projects");
            final List<Project> projects = new ArrayList<>();
            for (final Map<String, Object> projectData : projectsData) {
                final Project project = convertYamlProjectToProject(projectData);
                projects.add(project);
            }
            return projects;
        }
    }

    /**
     * Parses the projects from a properties file.
     *
     * @param filePath The path to the properties file.
     * @return A list of Project instances.
     * @throws IOException If an I/O error occurs while reading the file.
     */
    private static List<Project> parseProjectsFromProperties(final String filePath)
            throws IOException {
        final List<Project> projects = new ArrayList<>();
        final Path path = Paths.get(filePath);
        final List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);

        for (final String originalLine : lines) {
            final String trimmedLine = originalLine.trim();

            // Skip empty lines and comments
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                continue;
            }

            final String[] fields = trimmedLine.split(FIELD_DELIMITER);

            if (fields.length < MIN_FIELD_COUNT) {
                LOGGER.warn("Skipping invalid line in projects.properties: " + trimmedLine);
                continue;
            }

            final String repoName = fields[REPO_NAME_INDEX].trim();
            final String scm = fields[SCM_INDEX].trim();
            final String url = fields[URL_INDEX].trim();
            final String reference =
                fields.length > REFERENCE_INDEX ? fields[REFERENCE_INDEX].trim() : "";
            final String excludeFolders =
                fields.length > EXCLUDE_FOLDERS_INDEX ? fields[EXCLUDE_FOLDERS_INDEX].trim() : "";

            final Project project = new Project();
            project.setName(repoName);
            project.setScm(scm);
            project.setUrl(url);
            project.setReference(reference);

            if (!excludeFolders.isEmpty()) {
                // Exclude folders are separated by commas
                final String[] excludesArray = excludeFolders.split(EXCLUDE_DELIMITER);
                project.setExcludes(Arrays.asList(excludesArray));
            }

            projects.add(project);
        }

        return projects;
    }

    /**
     * Converts a YAML project data map into a Project instance.
     *
     * @param projectData The map containing project data from YAML.
     * @return A Project instance populated with the data.
     */
    @SuppressWarnings("unchecked")
    private static Project convertYamlProjectToProject(final Map<String, Object> projectData) {
        final Project project = new Project();
        project.setName((String) projectData.get("name"));
        project.setScm((String) projectData.get("scm"));
        project.setUrl((String) projectData.get("url"));

        final Object referenceObj = projectData.getOrDefault("reference", "");
        project.setReference(String.valueOf(referenceObj));

        project.setExcludes((List<String>) projectData.getOrDefault("excludes", new ArrayList<>()));
        return project;
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
        options.addOption("r", "localGitRepo", true,
                "Path to local git repository (required)");

        options.addOption("b", "baseBranch", true,
                "Base branch name. Default is master (optional, default is master)");

        options.addOption("p", "patchBranch", true,
                "Name of the patch branch in local git repository (required)");

        options.addOption("bc", "baseConfig", true,
            "Path to the base checkstyle config file (optional, "
                    + "if absent then the tool will use only patchBranch in case "
                    + "the tool mode is 'single', otherwise baseBranch will be set to 'master')");

        options.addOption("pc", "patchConfig", true,
            "Path to the patch checkstyle config file "
                    + "(required if baseConfig is specified)");

        options.addOption("c", "config", true,
            "Path to the checkstyle config file "
                    + "(required if baseConfig and patchConfig are not specified)");

        options.addOption("g", "allowExcludes", false,
            "Whether to allow excludes specified in the list of projects "
                    + "(optional, default is false)");

        options.addOption("h", "useShallowClone", false,
                "Enable shallow cloning");

        options.addOption("l", "listOfProjects", true,
                "Path to file which contains projects to test on (required)");

        options.addOption("s", "shortFilePaths", false,
            "Whether to save report file paths as a shorter version to prevent long paths. "
                    + "(optional, default is false)");

        options.addOption("m", "mode", true,
                "The mode of the tool: 'diff' or 'single'. (optional, default is 'diff')");

        options.addOption("xm", "extraMvnRegressionOptions", true,
                "Extra arguments to pass to Maven for Checkstyle Regression run (optional, "
                        + "ex: -Dmaven.prop=true)");

        options.addOption("dt", "diffToolJarPath", true,
                "Path to the patch-diff-report-tool JAR file (required)");

        final CommandLineParser parser = new DefaultParser();
        final HelpFormatter formatter = new HelpFormatter();

        try {
            return parser.parse(options, args);
        }
        catch (ParseException ex) {
            LOGGER.info(ex.getMessage());
            formatter.printHelp("DiffTool", options);
            throw new IllegalArgumentException("Failed to parse command line arguments", ex);
        }
    }

    /**
     * Validates the CLI options.
     *
     * @param cliOptions The command line options.
     * @return True if options are valid, otherwise false.
     */
    private static boolean areValidCliOptions(final CommandLine cliOptions) {
        final String baseConfig = cliOptions.getOptionValue("baseConfig");
        final String patchConfig = cliOptions.getOptionValue("patchConfig");
        final String config = cliOptions.getOptionValue("config");
        final String toolMode = cliOptions.getOptionValue("mode");
        final String patchBranch = cliOptions.getOptionValue("patchBranch");
        final String baseBranch = cliOptions.getOptionValue("baseBranch");
        final File listOfProjectsFile = new File(cliOptions.getOptionValue("listOfProjects"));
        final String localGitRepo = cliOptions.getOptionValue("localGitRepo");
        final String diffToolJarPath = cliOptions.getOptionValue("diffToolJarPath");

        if (toolMode != null && !("diff".equals(toolMode) || "single".equals(toolMode))) {
            LOGGER.error("Error: Invalid mode: '"
                    + toolMode
                    + "'. The mode should be 'single' or 'diff'!");
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
        if (!listOfProjectsFile.getName().endsWith(".yml")
                && !listOfProjectsFile.getName().endsWith(".yaml")
                && !listOfProjectsFile.getName().endsWith(".properties")) {
            LOGGER.error("Error: file " + listOfProjectsFile.getName()
                    + " is not a supported projects file!");
            return false;
        }
        if (diffToolJarPath == null || diffToolJarPath.isEmpty()) {
            LOGGER.error("Error: diffToolJarPath is required!");
            return false;
        }
        else {
            final File diffToolJarFile = new File(diffToolJarPath);
            if (!diffToolJarFile.exists() || !diffToolJarFile.isFile()) {
                LOGGER.error("Error: diffToolJarPath '"
                        + diffToolJarPath
                        + "' does not exist or is not a file!");
                return false;
            }
        }

        return true;
    }

    /**
     * Validates the combination of Checkstyle configuration options.
     *
     * @param config The checkstyle config file.
     * @param baseConfig The base checkstyle config file.
     * @param patchConfig The patch checkstyle config file.
     * @param toolMode The tool mode ('diff' or 'single').
     * @return True if configuration is valid, otherwise false.
     */
    private static boolean isValidCheckstyleConfigsCombination(final String config,
                                                               final String baseConfig,
                                                               final String patchConfig,
                                                               final String toolMode) {
        if (config == null && patchConfig == null && baseConfig == null) {
            LOGGER.error("Error: you should specify either 'config', or 'baseConfig', "
                    + "or 'patchConfig'!");
            return false;
        }
        if (config != null && (patchConfig != null || baseConfig != null)) {
            LOGGER.error("Error: you should specify either 'config', or 'baseConfig' and "
                    + "'patchConfig', or 'patchConfig' only!");
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
            LOGGER.error("Error: 'baseConfig' and/or 'config' shouldn't be used in 'single' mode!");
            return false;
        }
        return true;
    }

    /**
     * Checks if the given directory is a valid Git repository.
     *
     * @param gitRepoDir The directory to check.
     * @return True if it is a valid Git repository, otherwise false.
     */
    private static boolean isValidGitRepo(final File gitRepoDir) {
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
    private static boolean isExistingGitBranch(final File gitRepo, final String branchName) {
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
     * Copies unique configuration files from the specified list to the current working directory,
     * updating file paths as needed.
     *
     * @param configFilesList the list of configuration file paths to copy
     * @throws IOException if an I/O error occurs during file operations
     * @throws IllegalArgumentException if {@code configFilesList} is {@code null}
     */
    private static void copyConfigFilesAndUpdatePaths(final List<String> configFilesList)
            throws IOException {
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
            }
            else {
                LOGGER.error("Skipping invalid file path: " + filename);
            }
        }
    }

    /**
     * Checks if there are unstaged changes in the specified Git repository.
     *
     * @param gitRepo the directory of the Git repository
     * @return {@code true} if there are unstaged changes, {@code false} otherwise
     */
    private static boolean hasUnstagedChanges(final File gitRepo) {
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
     * Extracts the Checkstyle version from the provided POM XML file.
     *
     * @param pathToPomXml The path to the POM XML file.
     * @param xmlTagName The XML tag name used to find the Checkstyle version.
     * @return The Checkstyle version as a string, or {@code null} if not found or an error occurs.
     * @throws IOException If an error occurs while reading the POM XML file.
     */
    private static String getCheckstyleVersionFromPomXml(
            final String pathToPomXml, final String xmlTagName) {
        try {
            final List<String> lines = Files.readAllLines(Paths.get(pathToPomXml));
            for (final String line : lines) {
                if (line.matches("^.*<" + xmlTagName + ">.*-SNAPSHOT</" + xmlTagName + ">.*")) {
                    final int start = line.indexOf('>') + 1;
                    final int end = line.lastIndexOf('<');
                    return line.substring(start, end);
                }
            }
        }
        catch (IOException ex) {
            LOGGER.error("Error reading POM file: " + ex.getMessage());
        }
        return null;
    }

    /**
     * Launches the Checkstyle report generation process.
     *
     * @param cfg The configuration map for Checkstyle.
     * @return CheckstyleReportInfo containing report details if regression testing, otherwise null.
     * @throws IOException If an I/O error occurs during command execution.
     * @throws InterruptedException If the process is interrupted.
     */
    private static CheckstyleReportInfo launchCheckstyleReport(final Map<String, Object> cfg)
            throws IOException, InterruptedException {
        CheckstyleReportInfo reportInfo = null;
        final boolean isRegressionTesting =
                cfg.get("branch") != null && cfg.get("localGitRepo") != null;

        if (isRegressionTesting) {
            LOGGER.info("Installing Checkstyle artifact ("
                    + cfg.get("branch")
                    + ") into local Maven repository ...");
            executeCmd("git checkout " + cfg.get("branch"), (File) cfg.get("localGitRepo"));
            executeCmd("git log -1 --pretty=MSG:%s%nSHA-1:%H", (File) cfg.get("localGitRepo"));
            executeCmd(
                    "mvn -e --no-transfer-progress --batch-mode -Pno-validations clean install",
                    (File) cfg.get("localGitRepo")
            );
        }

        cfg.put("checkstyleVersion",
                getCheckstyleVersionFromPomXml(cfg.get("localGitRepo") + "/pom.xml",
                "version"));

        generateCheckstyleReport(cfg);

        LOGGER.info("Moving Checkstyle report into "
                + cfg.get("destDir")
                + " ...");
        moveDir("reports", (String) cfg.get("destDir"));

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
     * Generates the Checkstyle report based on the configuration.
     *
     * @param cfg The configuration map for Checkstyle.
     * @throws InterruptedException If the process is interrupted during report generation.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static void generateCheckstyleReport(final Map<String, Object> cfg)
            throws InterruptedException, IOException {
        LOGGER.info("Testing Checkstyle started");

        final String targetDir = "target";
        final String srcDir = getOsSpecificPath("src", "main", "java");
        final String reposDir = "repositories";
        final String reportsDir = "reports";
        makeWorkDirsIfNotExist(srcDir, reposDir, reportsDir);

        final String checkstyleConfig = (String) cfg.get("checkstyleCfg");
        final String checkstyleVersion = (String) cfg.get("checkstyleVersion");
        final boolean allowExcludes = (boolean) cfg.get("allowExcludes");
        final boolean useShallowClone = (boolean) cfg.get("useShallowClone");
        final String listOfProjects = (String) cfg.get("listOfProjects");
        final String extraMvnRegressionOptions = (String) cfg.get("extraMvnRegressionOptions");

        final List<Project> projects = parseProjects(listOfProjects);
        for (final Project project : projects) {
            final String repoName = project.getName();
            final String repoType = project.getScm();
            final String repoUrl = project.getUrl();
            final String commitId = project.getReference();

            String excludes = "";
            if (allowExcludes && project.getExcludes() != null) {
                excludes = String.join(",", project.getExcludes());
            }

            deleteDir(srcDir);
            if ("local".equals(repoType)) {
                copyDir(repoUrl, getOsSpecificPath(srcDir, repoName));
            }
            else {
                if (useShallowClone && !isGitSha(commitId)) {
                    shallowCloneRepository(repoName, repoType, repoUrl, commitId, reposDir);
                }
                else {
                    cloneRepository(repoName, repoType, repoUrl, commitId, reposDir);
                }
                copyDir(getOsSpecificPath(reposDir, repoName), getOsSpecificPath(srcDir, repoName));
            }
            runMavenExecution(srcDir, excludes, checkstyleConfig,
                    checkstyleVersion, extraMvnRegressionOptions);
            String repoPath = repoUrl;
            if (!"local".equals(repoType)) {
                repoPath = new File(getOsSpecificPath(reposDir, repoName)).getAbsolutePath();
            }
            postProcessCheckstyleReport(targetDir, repoName, repoPath);
            deleteDir(getOsSpecificPath(srcDir, repoName));
            moveDir(targetDir, getOsSpecificPath(reportsDir, repoName));
        }

        // Restore empty_file to make src directory tracked by git
        final File emptyFile = new File(getOsSpecificPath(srcDir, "empty_file"));
        if (!emptyFile.createNewFile()) {
            LOGGER.warn("Failed to create or already existing 'empty_file' in " + srcDir);
        }
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
    private static String getLastCommitMsg(final File gitRepo, final String branch)
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
    private static String getLastCommitTime(final File gitRepo, final String branch)
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
     * Gets the SHA of a commit based on the provided commit ID.
     *
     * @param commitId The commit ID or reference.
     * @param repoType The type of repository (e.g., "git").
     * @param srcDestinationDir The source directory of the repository.
     * @return The commit SHA, or an empty string if an error occurs.
     * @throws IllegalArgumentException if the repository type is unknown.
     * @throws IOException if an I/O error occurs while executing the command.
     */
    private static String getCommitSha(final String commitId,
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
     * Performs a shallow clone of the specified repository.
     *
     * @param repoName       The name of the repository.
     * @param repoType       The type of repository (e.g., "git").
     * @param repoUrl        The URL of the repository.
     * @param commitId       The commit ID or reference.
     * @param srcDir         The source directory for cloning.
     * @throws IOException If an I/O error occurs.
     */
    private static void shallowCloneRepository(final String repoName, final String repoType,
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
            executeCmdWithRetry(cloneCmd);
            LOGGER.info("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
        }
        LOGGER.info(repoName + " is synchronized");
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
    private static void cloneRepository(final String repoName, final String repoType,
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
            executeCmdWithRetry(cloneCmd);
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
                executeCmd(resetCmd, new File(srcDestinationDir));
            }
        }
        LOGGER.info(repoName + " is synchronized");
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
    private static String getCloneCmd(final String repoType,
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
     * Generates the shallow clone command based on the repository type and commit ID.
     *
     * @param repoType The type of repository (e.g., "git").
     * @param repoUrl The URL of the repository.
     * @param srcDestinationDir The destination directory for cloning.
     * @param commitId The commit ID or reference.
     * @return The command to perform a shallow clone.
     * @throws IllegalArgumentException if the repository type is unknown.
     */
    private static String getCloneShallowCmd(final String repoType, final String repoUrl,
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
     * Fetches additional data for a specific commit if needed.
     *
     * @param repoType The type of repository (e.g., "git").
     * @param srcDestinationDir The source directory of the repository.
     * @param commitId The commit ID or reference to fetch data for.
     * @throws IOException If an I/O error occurs during the fetch operation.
     * @throws IllegalArgumentException If the repository type is unknown.
     * @throws InterruptedException If the process is interrupted during execution.
     */
    private static void fetchAdditionalData(final String repoType, final String srcDestinationDir,
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

        executeCmd(fetchCmd, new File(srcDestinationDir));
    }

    /**
     * Checks if the provided commit ID is a tag in the repository.
     *
     * @param commitId   The commit ID to check.
     * @param gitRepo    The repository directory.
     * @return True if the commit ID is a tag, false otherwise.
     */
    private static boolean isTag(final String commitId, final File gitRepo) {
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
     * Checks if the provided value matches the format of a Git SHA.
     *
     * @param value The value to check.
     * @return True if the value matches a valid Git SHA format, false otherwise.
     */
    private static boolean isGitSha(final String value) {
        return value.matches("[0-9a-f]{5,40}");
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
     * Generates a diff report based on the provided configuration.
     *
     * @param cfg A map containing configuration parameters.
     * @throws IllegalStateException if the Maven build fails or the parent directory is not found.
     * @throws IllegalArgumentException if the patch reports directory is not provided.
     * @throws IOException if an I/O error occurs while reading the patch reports directory.
     * @throws Exception if any other error occurs during the report generation.
     */
    private static void generateDiffReport(final Map<String, Object> cfg) throws Exception {
        final Path currentDir = Paths.get("").toAbsolutePath();
        final Path parentDir = currentDir.getParent();
        if (parentDir == null) {
            throw new IllegalStateException("Unable to locate parent directory");
        }

        final String patchReportsDir = (String) cfg.get("patchReportsDir");
        if (patchReportsDir == null) {
            throw new IllegalArgumentException(
                    "Patch reports directory path is not provided in the configuration."
            );
        }
        final String diffToolJarPath = (String) cfg.get("diffToolJarPath");
        LOGGER.info("Starting diff report generation ...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(patchReportsDir))) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    processProjectDirectory(cfg, diffToolJarPath, path);
                }
            }
        }
        catch (IOException ex) {
            LOGGER.error("Failed to read the patch reports directory: " + ex.getMessage());
            throw ex;
        }
        LOGGER.info("Diff report generation finished ...");
    }

    /**
     * Processes a project directory to generate a diff report.
     *
     * @param cfg Configuration parameters.
     * @param diffToolJarPath Path to the diff tool JAR.
     * @param path The project directory to process.
     * @throws FileNotFoundException if the target directory does not exist.
     * @throws Exception if an error occurs while processing the project directory.
     */
    private static void processProjectDirectory(final Map<String, Object> cfg,
                                                final String diffToolJarPath,
                                                final Path path) throws Exception {
        final Path fileNamePath = path.getFileName();
        if (fileNamePath != null) {
            final String projectName = fileNamePath.toString();
            final File patchReportDir = new File((String) cfg.get("patchReportsDir"), projectName);
            if (patchReportDir.exists()) {
                generateProjectDiffReport(cfg, diffToolJarPath, projectName);
            }
            else {
                throw new FileNotFoundException(
                        "Error: patch report for project "
                                + projectName
                                + " is not found!");
            }
        }
        else {
            throw new FileNotFoundException("Error: Path does not have a file name.");
        }
    }

    /**
     * Generates a diff report for a specific project.
     *
     * @param cfg Configuration parameters.
     * @param diffToolJarPath Path to the diff tool JAR.
     * @param projectName The name of the project to generate the report for.
     * @throws Exception if an error occurs while generating the project diff report.
     */
    private static void generateProjectDiffReport(final Map<String, Object> cfg,
                                                  final String diffToolJarPath,
                                                  final String projectName) throws Exception {
        final String patchReport =
                Paths.get((String) cfg.get("patchReportsDir"),
                        projectName,
                        "checkstyle-result.xml").toString();
        final String outputDir =
                Paths.get((String) cfg.get("reportsDir"),
                        "diff",
                        projectName).toString();

        logConfigContents((String) cfg.get("patchConfig"));
        if ("diff".equals(cfg.get("mode"))) {
            logConfigContents((String) cfg.get("baseConfig"));
        }

        // Specify the locale explicitly (Locale.getDefault() or a specific one like Locale.US)
        final StringBuilder diffCmdBuilder = new StringBuilder(String.format(Locale.getDefault(),
                "java -jar %s --patchReport %s --output %s --patchConfig %s",
                diffToolJarPath,
                patchReport,
                outputDir,
                new File((String) cfg.get("patchConfig")).getName()));

        if ("diff".equals(cfg.get("mode"))) {
            final String baseReport = Paths.get((String) cfg.get("masterReportsDir"),
                    projectName,
                    "checkstyle-result.xml").toString();
            diffCmdBuilder.append(String.format(Locale.getDefault(),
                    " --baseReport %s --baseConfig %s",
                    baseReport,
                    new File((String) cfg.get("baseConfig")).getName()));
        }

        if ((boolean) cfg.get("shortFilePaths")) {
            diffCmdBuilder.append(" --shortFilePaths");
        }

        executeCmd(diffCmdBuilder.toString());
    }

    /**
     * Logs the contents of a configuration file.
     *
     * @param configPath The path to the configuration file.
     */
    private static void logConfigContents(final String configPath) {
        try {
            LOGGER.info("Contents of " + configPath + ":");
            final List<String> lines = Files.readAllLines(Paths.get(configPath));
            lines.forEach(System.out::println);
        }
        catch (IOException ex) {
            LOGGER.error("Error reading config file: " + ex.getMessage());
        }
    }

    /**
     * Creates an instance of the TextTransform class from the diff tool JAR.
     *
     * @param diffToolJarPath Path to the diff tool JAR.
     * @return An instance of the TextTransform class, or null if an error occurs.
     * @throws InvocationTargetException if an error occurs while invoking the constructor.
     * @throws InstantiationException if an error occurs while instantiating the class.
     * @throws IllegalAccessException if access to the class or constructor is illegal.
     * @throws ClassNotFoundException if the class cannot be found.
     */
    private static Object getTextTransform(final String diffToolJarPath)
                                           throws InvocationTargetException,
            InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            final URL[] urls = {new File(diffToolJarPath).toURI().toURL()};
            try (URLClassLoader classLoader = new URLClassLoader(urls)) {
                final Class<?> clazz =
                        classLoader.loadClass("com.github.checkstyle.site.TextTransform");
                return clazz.getDeclaredConstructor().newInstance();
            }
        }
        catch (IOException | NoSuchMethodException ex) {
            LOGGER.error("Error loading TextTransform: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Generates a summary index HTML file for the diff report.
     *
     * @param diffDir The directory containing the diff reports.
     * @param checkstyleBaseReportInfo Information about the base Checkstyle report.
     * @param checkstylePatchReportInfo Information about the patch Checkstyle report.
     * @param configFilesList List of configuration files.
     * @param allowExcludes Whether excludes are allowed.
     * @param diffToolJarPath patch diff tool jar path.
     * @throws IOException if an I/O error occurs while writing the summary index HTML file.
     * @throws InvocationTargetException if an error occurs while invoking methods via reflection.
     * @throws InstantiationException if an error occurs while instantiating classes via reflection.
     * @throws IllegalAccessException if access to classes or methods is illegal via reflection.
     * @throws ClassNotFoundException if a class cannot be found.
     */
    private static void generateSummaryIndexHtml(final String diffDir,
                         final CheckstyleReportInfo checkstyleBaseReportInfo,
                         final CheckstyleReportInfo checkstylePatchReportInfo,
                         final List<String> configFilesList,
                         final boolean allowExcludes,
                         final String diffToolJarPath)
                         throws IOException, InvocationTargetException,
                                InstantiationException, IllegalAccessException,
                                ClassNotFoundException {
        LOGGER.info("Starting creating report summary page ...");
        final Map<String, int[]> projectsStatistic = getProjectsStatistic(diffDir);
        final Path summaryIndexHtmlPath = Paths.get(diffDir, "index.html");

        try (BufferedWriter writer =
                     Files.newBufferedWriter(
                             summaryIndexHtmlPath,
                             StandardCharsets.UTF_8)) {
            writer.write("<html><head>");
            writer.write(
                    "<link rel=\"icon\" "
                            + "href=\"https://checkstyle.org/images/favicon.png\" "
                            + "type=\"image/x-icon\" />"
            );
            writer.write("<title>Checkstyle Tester Report Diff Summary</title>");
            writer.write("</head><body>");
            writer.write("\n");
            if (!allowExcludes) {
                writer.write("<h3><span style=\"color: #ff0000;\">");
                writer.write("<strong>WARNING: Excludes are ignored by diff.groovy.</strong>");
                writer.write("</span></h3>");
            }
            printReportInfoSection(writer,
                    checkstyleBaseReportInfo,
                    checkstylePatchReportInfo,
                    projectsStatistic);
            printConfigSection(diffDir, configFilesList, writer, diffToolJarPath);

            final List<Map.Entry<String, int[]>> sortedProjects =
                    new ArrayList<>(projectsStatistic.entrySet());
            sortedProjects.sort(Comparator
                    .comparing((Map.Entry<String, int[]> entry) -> {
                        return entry.getKey().toLowerCase(Locale.getDefault());
                    })
                    .thenComparing(entry -> {
                        return entry.getValue()[0] == 0 ? 1 : 0;
                    }));

            for (final Map.Entry<String, int[]> entry : sortedProjects) {
                final String project = entry.getKey();
                final int[] diffCount = entry.getValue();
                writer.write("<a href='" + project + "/index.html'>" + project + "</a>");
                if (diffCount[0] != 0) {
                    if (diffCount[1] == 0) {
                        writer.write(String.format(Locale.getDefault(),
                                " ( &#177;%d, <span style=\"color: red;\">-%d</span> )",
                                diffCount[0],
                                diffCount[2]));
                    }
                    else if (diffCount[2] == 0) {
                        writer.write(String.format(Locale.getDefault(), " "
                                 + "( &#177;%d, <span style=\"color: green;\">+%d</span> )",
                                 diffCount[0],
                                 diffCount[1]));
                    }
                    else {
                        writer.write(String.format(Locale.getDefault(),
                                " ( &#177;%d, "
                                        + "<span style=\"color: red;\">-%d</span>, "
                                        + "<span style=\"color: green;\">+%d</span> )",
                                diffCount[0],
                                diffCount[2],
                                diffCount[1]));
                    }
                }
                writer.write("<br />");
                writer.write("\n");
            }
            writer.write("</body></html>");
        }

        LOGGER.info("Creating report summary page finished...");
    }

    /**
     * Prints configuration sections to an HTML file.
     *
     * @param diffToolJarPath Path to the diff tool JAR.
     * @param diffDir The directory for the HTML output.
     * @param configFilesList List of configuration files.
     * @param summaryIndexHtml Writer for the HTML summary index.
     * @throws IOException If an I/O error occurs.
     * @throws InvocationTargetException If an exception occurs during method invocation.
     * @throws InstantiationException If an instantiation error occurs.
     * @throws IllegalAccessException If access to the method is illegal.
     * @throws ClassNotFoundException If the class cannot be found.
     */
    private static void printConfigSection(final String diffDir,
                                           final List<String> configFilesList,
                                           final BufferedWriter summaryIndexHtml,
                                           final String diffToolJarPath)
                                           throws IOException, InvocationTargetException,
                                                  InstantiationException, IllegalAccessException,
                                                  ClassNotFoundException {
        final Object textTransform = getTextTransform(diffToolJarPath);
        final Set<String> processedConfigs = new HashSet<>();
        for (final String filename : configFilesList) {
            if (filename != null && !filename.isEmpty() && !processedConfigs.contains(filename)) {
                final File configFile = new File(filename);
                generateAndPrintConfigHtmlFile(diffDir,
                        configFile,
                        textTransform,
                        summaryIndexHtml);
                processedConfigs.add(filename);
            }
        }
    }

    /**
     * Generates an HTML file from a config file and prints a link to it.
     *
     * @param diffDir Directory for HTML output.
     * @param configFile The config file to process.
     * @param textTransform Object for transformation.
     * @param summaryIndexHtml Writer for the HTML summary index.
     * @throws IOException If an I/O error occurs.
     */
    private static void generateAndPrintConfigHtmlFile(final String diffDir,
                                                       final File configFile,
                                                       final Object textTransform,
                                                       final BufferedWriter summaryIndexHtml)
                                                       throws IOException {
        if (!configFile.exists()) {
            return;
        }

        final String configFileNameWithoutExtension =
                getFilenameWithoutExtension(configFile.getName());
        final File configFileHtml =
                new File(diffDir, configFileNameWithoutExtension + ".html");

        if (textTransform != null) {
            try {
                textTransform.getClass()
                        .getMethod("transform", String.class,
                                String.class,
                                Locale.class,
                                String.class,
                                String.class)
                        .invoke(textTransform,
                                configFile.getAbsolutePath(),
                                configFileHtml.getAbsolutePath(),
                                Locale.ENGLISH,
                                "UTF-8",
                                "UTF-8");
            }
            catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
                LOGGER.error("Error transforming config file: " + ex.getMessage());
            }
        }
        else {
            LOGGER.error("TextTransform object is null. Skipping transformation.");
            // Fallback: copy the content of the config file to the HTML file
            Files.copy(configFile.toPath(),
                    configFileHtml.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        summaryIndexHtml.write("<h6>");
        summaryIndexHtml.write("<a href='"
                + configFileHtml.getName()
                + "'>" + configFile.getName()
                + " file</a>");
        summaryIndexHtml.write("</h6>");
    }

    /**
     * Returns the filename without its extension.
     *
     * @param filename The filename to process.
     * @return Filename without extension.
     */
    private static String getFilenameWithoutExtension(final String filename) {
        final int pos = filename.lastIndexOf('.');
        if (pos > 0) {
            return filename.substring(0, pos);
        }
        return filename;
    }

    /**
     * Creates directories if they do not exist.
     *
     * @param srcDirPath Source directory path.
     * @param repoDirPath Repository directory path.
     * @param reportsDirPath Reports directory path.
     */
    private static void makeWorkDirsIfNotExist(final String srcDirPath,
                                               final String repoDirPath,
                                               final String reportsDirPath) {
        final File srcDir = new File(srcDirPath);
        if (!srcDir.mkdirs()) {
            LOGGER.error("Failed to create source directory: " + srcDirPath);
        }
        final File repoDir = new File(repoDirPath);
        if (!repoDir.mkdir()) {
            LOGGER.error("Failed to create repository directory: " + repoDirPath);
        }
        final File reportsDir = new File(reportsDirPath);
        if (!reportsDir.mkdir()) {
            LOGGER.error("Failed to create reports directory: " + reportsDirPath);
        }
    }

    /**
     * Prints report information to an HTML file.
     *
     * @param summaryIndexHtml Writer for the HTML summary index.
     * @param checkstyleBaseReportInfo Base branch report info.
     * @param checkstylePatchReportInfo Patch branch report info.
     * @param projectsStatistic Project statistics.
     * @throws IOException If an I/O error occurs.
     */
    private static void printReportInfoSection(final BufferedWriter summaryIndexHtml,
                                               final CheckstyleReportInfo checkstyleBaseReportInfo,
                                               final CheckstyleReportInfo checkstylePatchReportInfo,
                                               final Map<String, int[]> projectsStatistic)
                                               throws IOException {
        final Date date = new Date();
        summaryIndexHtml.write("<h6>");
        if (checkstyleBaseReportInfo != null) {
            summaryIndexHtml.write("Base branch: "
                    + checkstyleBaseReportInfo.getBranch());
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit SHA: "
                    + checkstyleBaseReportInfo.getCommitSha());
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit message: \""
                    + checkstyleBaseReportInfo.getCommitMsg() + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit timestamp: \""
                    + checkstyleBaseReportInfo.getCommitTime() + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("<br />");
        }
        summaryIndexHtml.write("Patch branch: "
                + checkstylePatchReportInfo.getBranch());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit SHA: "
                + checkstylePatchReportInfo.getCommitSha());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit message: \""
                + checkstylePatchReportInfo.getCommitMsg() + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit timestamp: \""
                + checkstylePatchReportInfo.getCommitTime() + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Tested projects: " + projectsStatistic.size());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("&#177; differences found: "
                + projectsStatistic.values().stream().mapToInt(arr -> arr[0]).sum());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Time of report generation: " + date);
        summaryIndexHtml.write("</h6>");
    }

    /**
     * Retrieves project statistics from the diff directory.
     *
     * @param diffDir The directory containing project statistics.
     * @return Map of project statistics.
     * @throws IOException If an I/O error occurs.
     */
    private static Map<String, int[]> getProjectsStatistic(final String diffDir)
            throws IOException {
        final Map<String, int[]> projectsStatistic = new HashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(diffDir))) {
            for (final Path path : stream) {
                if (Files.isDirectory(path)) {
                    processProjectStatistics(path, projectsStatistic);
                }
            }
        }
        return projectsStatistic;
    }

    /**
     * Processes project statistics from an index.html file.
     *
     * @param path Path to the project directory.
     * @param projectsStatistic Map to store project statistics.
     * @throws IOException If an I/O error occurs.
     */
    private static void processProjectStatistics(final Path path,
                                                 final Map<String,
                                                 int[]> projectsStatistic) throws IOException {
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

    /**
     * Extracts difference statistics from lines of text.
     *
     * @param lines Lines of text to process.
     * @return Array of difference statistics.
     */
    private static int[] extractDiffStats(final List<String> lines) {
        int addedDiff = 0;
        int removedDiff = 0;
        int totalDiff = 0;
        boolean totalDiffFound = false;
        for (final String line : lines) {
            if (line.contains("id=\"totalPatch\"")) {
                addedDiff = extractDiffCount(line, "(?<totalAdd>[0-9]++) added");
                removedDiff = extractDiffCount(line, "(?<totalRemoved>[0-9]++) removed");
            }
            else if (line.contains("totalDiff")) {
                totalDiff = extractTotalDiff(line);
                totalDiffFound = true;
                break;
            }
        }
        return totalDiffFound ? new int[]{totalDiff, addedDiff, removedDiff} : new int[]{0, 0, 0};
    }

    /**
     * Extracts a difference count from a line of text.
     *
     * @param line The line to process.
     * @param regex The regex pattern to use.
     * @return The difference count.
     */
    private static int extractDiffCount(final String line, final String regex) {
        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(line);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    /**
     * Extracts the total difference from a line of text.
     *
     * @param line The line to process.
     * @return The total difference.
     */
    private static int extractTotalDiff(final String line) {
        final Pattern linePattern = Pattern.compile("totalDiff\">(?<totalDiff>[0-9]++)");
        final Matcher lineMatcher = linePattern.matcher(line);
        return lineMatcher.find() ? Integer.parseInt(lineMatcher.group("totalDiff")) : 0;
    }

    /**
     * Runs Maven commands for clean and Checkstyle.
     *
     * @param srcDir Source directory.
     * @param excludes Excluded files.
     * @param checkstyleConfig Checkstyle configuration file.
     * @param checkstyleVersion Checkstyle version.
     * @param extraMvnRegressionOptions Additional Maven options.
     * @throws IOException If an I/O error occurs.
     * @throws InterruptedException If the process is interrupted.
     */
    private static void runMavenExecution(final String srcDir,
                                          final String excludes,
                                          final String checkstyleConfig,
                                          final String checkstyleVersion,
                                          final String extraMvnRegressionOptions)
                                          throws IOException, InterruptedException {
        LOGGER.info("Running 'mvn clean' on " + srcDir + " ...");

        // Generate the pom.xml file in the project root
        generatePomXml(".");

        final String mvnClean = "mvn -e --no-transfer-progress --batch-mode clean";
        executeCmd(mvnClean, new File("."));

        LOGGER.info("Running Checkstyle on " + srcDir + " ... with excludes {" + excludes + "}");
        final StringBuilder mvnSite =
                new StringBuilder(STRING_BUILDER_CAPACITY)
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
        executeCmd(mvnSite.toString(), new File("."));
        LOGGER.info("Running Checkstyle on " + srcDir + " - finished");
    }

    /**
     * Generates the pom.xml file required for Maven execution.
     *
     * @param destinationDir The directory where the pom.xml will be created.
     * @throws IOException If an I/O error occurs during file writing.
     * @throws FileNotFoundException If an pom's template file not found.
     */
    private static void generatePomXml(final String destinationDir) throws IOException {
        LOGGER.info("Attempting to load pom_template.xml from classpath.");
        try (InputStream inputStream = DiffTool.class.getResourceAsStream("/pom_template.xml")) {
            if (inputStream == null) {
                LOGGER.error("pom_template.xml not found in classpath.");
                throw new FileNotFoundException(
                        "Resource 'pom_template.xml' not found in classpath"
                );
            }
            LOGGER.info("pom_template.xml successfully loaded from classpath.");
            final String pomContent =
                    new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            final Path pomFilePath = Paths.get(destinationDir, "pom.xml");
            Files.writeString(pomFilePath, pomContent, StandardCharsets.UTF_8);
        }
    }

    /**
     * Post-processes the Checkstyle report by adjusting paths.
     *
     * @param targetDir Target directory for the report.
     * @param repoName Repository name.
     * @param repoPath Repository path.
     * @throws IOException If an I/O error occurs.
     */
    private static void postProcessCheckstyleReport(final String targetDir,
                    final String repoName, final String repoPath) throws IOException {
        final Path reportPath = Paths.get(targetDir, "checkstyle-result.xml");
        String content = new String(Files.readAllBytes(reportPath), StandardCharsets.UTF_8);
        content = content
                .replace(new File(getOsSpecificPath("src", "main", "java", repoName))
                .getAbsolutePath(), getOsSpecificPath(repoPath));
        Files.write(reportPath, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Copies a directory and its contents.
     *
     * @param source Source directory.
     * @param destination Destination directory.
     * @throws IOException If an I/O error occurs.
     */
    private static void copyDir(final String source, final String destination)
            throws IOException {
        final Path sourcePath = Paths.get(source);
        final Path destinationPath = Paths.get(destination);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                                                     final BasicFileAttributes attrs)
                                                     throws IOException {
                final Path targetPath =
                        destinationPath.resolve(sourcePath.relativize(dir));
                Files.createDirectories(targetPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                                             BasicFileAttributes attrs) throws IOException {
                Files.copy(file,
                           destinationPath.resolve(sourcePath.relativize(file)),
                           StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Moves a directory and its contents.
     *
     * @param source Source directory.
     * @param destination Destination directory.
     * @throws IOException If an I/O error occurs.
     */
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
                        Files.move(file,
                                destinationPath.resolve(sourcePath.relativize(file)),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (IOException ex) {
                        LOGGER.error("Error moving file " + file + ": " + ex.getMessage());
                    }
                });
            }
            // Delete the now-empty source directory
            Files.deleteIfExists(sourcePath);
        }
        else {
            LOGGER.info("Source directory " + source + " does not exist. Skipping move operation.");
        }
    }

    /**
     * Deletes a directory and its contents.
     *
     * @param dir Directory to delete.
     * @throws IOException If an I/O error occurs.
     */
    private static void deleteDir(final String dir) throws IOException {
        final Path directory = Paths.get(dir);
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file,
                                                 BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path dir,
                                                          IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
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
     * Executes a command in the current working directory.
     *
     * @param cmd the command to execute
     * @throws IOException if an I/O error occurs while executing the command
     * @throws InterruptedException if the process is interrupted while waiting
     */
    private static void executeCmd(final String cmd) throws IOException, InterruptedException {
        executeCmd(cmd, new File("").getAbsoluteFile());
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
     * Generates the reset command for a specific commit ID.
     *
     * @param repoType  The type of repository (e.g., "git").
     * @param commitId  The commit ID or tag name.
     * @return The reset command for the specified commit.
     * @throws IllegalArgumentException if the repository type is unknown.
     */
    private static String getResetCmd(final String repoType, final String commitId) {
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
     * Retrieves the SHA of the most recent commit in the specified repository directory.
     *
     * @param repoType the type of repository (e.g., "git")
     * @param srcDestinationDir the directory of the repository
     * @return the SHA of the most recent commit, or an empty string if not found
     * @throws IOException if an I/O error occurs while executing the command
     * @throws InterruptedException if the process is interrupted while waiting
     * @throws IllegalArgumentException if the repository type is unknown
     */
    private static String getLastProjectCommitSha(final String repoType,
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
     * Deletes the specified work directories if they exist.
     *
     * @param cfg the configuration containing the paths of directories to be deleted
     * @throws IOException if an I/O error occurs during deletion
     */
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
        /** The local Git repository location. */
        private final File localGitRepo;

        /** Whether to use shortened file paths. */
        private final boolean shortFilePaths;

        /** The list of projects to analyze. */
        private final String listOfProjects;

        /** The mode of operation (e.g., "diff" or "single"). */
        private final String mode;

        /** The name of the base branch to compare. */
        private final String baseBranch;

        /** The name of the patch branch to compare. */
        private final String patchBranch;

        /** The configuration file for the base branch. */
        private final String baseConfig;

        /** The configuration file for the patch branch. */
        private final String patchConfig;

        /** The general configuration setting. */
        private final String configFile;

        /** The directory for storing reports. */
        private final String reportsDir;

        /** The directory for storing master branch report files. */
        private final String masterReportsDir;

        /** The directory for storing patch branch report files. */
        private final String patchReportsDir;

        /** The temporary directory for storing report files. */
        private final String tmpReportsDir;

        /** The temporary directory for storing master branch report files. */
        private final String tmpMasterReportsDir;

        /** The temporary directory for storing patch branch report files. */
        private final String tmpPatchReportsDir;

        /** The directory for storing diff results. */
        private final String diffDir;

        /** Additional options for Maven regression. */
        private final String extraMvnRegressionOptions;

        /** The version of Checkstyle being used. */
        private final String checkstyleVersion;

        /** The version of Sevntu used (if applicable). */
        private final String sevntuVersion;

        /** Whether to allow exclusion rules in the analysis. */
        private final boolean allowExcludes;

        /** Whether to use shallow cloning in Git operations. */
        private final boolean useShallowClone;

        /** The path to path diff tool jar path. */
        private final String diffToolJarPath;

        /**
         * Constructs a {@code Config} instance based on the provided command-line options.
         *
         * @param cliOptions the CLI options from which configuration values are extracted
         */
        Config(final CommandLine cliOptions) {
            localGitRepo =
                    cliOptions.hasOption("localGitRepo")
                            ? new File(cliOptions.getOptionValue("localGitRepo"))
                            : new File("");
            shortFilePaths = cliOptions.hasOption("shortFilePaths");
            listOfProjects = cliOptions.getOptionValue("listOfProjects");
            extraMvnRegressionOptions = cliOptions.getOptionValue("extraMvnRegressionOptions");
            checkstyleVersion = cliOptions.getOptionValue("checkstyleVersion");
            allowExcludes = cliOptions.hasOption("allowExcludes");
            useShallowClone = cliOptions.hasOption("useShallowClone");
            mode = cliOptions.getOptionValue("mode", "diff");
            baseBranch = cliOptions.getOptionValue("baseBranch", "master");
            patchBranch = cliOptions.getOptionValue("patchBranch");
            baseConfig = initializeConfig(cliOptions, "baseConfig");
            patchConfig = initializeConfig(cliOptions, "patchConfig");
            configFile = cliOptions.getOptionValue("config");
            diffToolJarPath = cliOptions.getOptionValue("diffToolJarPath");

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
        /**
         * Returns the local Git repository file.
         *
         * @return the local Git repository file
         */
        public File getLocalGitRepo() {
            return localGitRepo;
        }

        /**
         * Checks if short file paths are used.
         *
         * @return {@code true} if short file paths are used; {@code false} otherwise
         */
        public boolean isShortFilePaths() {
            return shortFilePaths;
        }

        /**
         * Retrieves the file path of the Diff Tool JAR.
         *
         * @return the path to the Diff Tool JAR as a {@code String}.
         */
        public String getDiffToolJarPath() {
            return diffToolJarPath;
        }

        /**
         * Returns the list of projects.
         *
         * @return the list of projects
         */
        public String getListOfProjects() {
            return listOfProjects;
        }

        /**
         * Returns the mode of operation.
         *
         * @return the mode of operation
         */
        public String getMode() {
            return mode;
        }

        /**
         * Returns the base branch name.
         *
         * @return the base branch name
         */
        public String getBaseBranch() {
            return baseBranch;
        }

        /**
         * Returns the patch branch name.
         *
         * @return the patch branch name
         */
        public String getPatchBranch() {
            return patchBranch;
        }

        /**
         * Returns the base configuration file path.
         *
         * @return the base configuration file path
         */
        public String getBaseConfig() {
            return baseConfig;
        }

        /**
         * Returns the patch configuration file path.
         *
         * @return the patch configuration file path
         */
        public String getPatchConfig() {
            return patchConfig;
        }

        /**
         * Returns the configuration file path.
         *
         * @return the configuration file path
         */
        public String getConfigFile() {
            return configFile;
        }

        /**
         * Returns the reports directory path.
         *
         * @return the reports directory path
         */
        public String getReportsDir() {
            return reportsDir;
        }

        /**
         * Returns the master reports directory path.
         *
         * @return the master reports directory path
         */
        public String getMasterReportsDir() {
            return masterReportsDir;
        }

        /**
         * Returns the patch reports directory path.
         *
         * @return the patch reports directory path
         */
        public String getPatchReportsDir() {
            return patchReportsDir;
        }

        /**
         * Returns the temporary reports directory path.
         *
         * @return the temporary reports directory path
         */
        public String getTmpReportsDir() {
            return tmpReportsDir;
        }

        /**
         * Returns the temporary master reports directory path.
         *
         * @return the temporary master reports directory path
         */
        public String getTmpMasterReportsDir() {
            return tmpMasterReportsDir;
        }

        /**
         * Returns the temporary patch reports directory path.
         *
         * @return the temporary patch reports directory path
         */
        public String getTmpPatchReportsDir() {
            return tmpPatchReportsDir;
        }

        /**
         * Returns the diff directory path.
         *
         * @return the diff directory path
         */
        public String getDiffDir() {
            return diffDir;
        }

        /**
         * Returns additional Maven regression options.
         *
         * @return additional Maven regression options
         */
        public String getExtraMvnRegressionOptions() {
            return extraMvnRegressionOptions;
        }

        /**
         * Returns the Checkstyle version.
         *
         * @return the Checkstyle version
         */
        public String getCheckstyleVersion() {
            return checkstyleVersion;
        }

        /**
         * Returns the Sevntu version.
         *
         * @return the Sevntu version
         */
        public String getSevntuVersion() {
            return sevntuVersion;
        }

        /**
         * Checks if excludes are allowed.
         *
         * @return {@code true} if excludes are allowed; {@code false} otherwise
         */
        public boolean isAllowExcludes() {
            return allowExcludes;
        }

        /**
         * Checks if shallow cloning is used.
         *
         * @return {@code true} if shallow cloning is used; {@code false} otherwise
         */
        public boolean isUseShallowClone() {
            return useShallowClone;
        }

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
         * Initializes the configuration based on the specific type or general config.
         *
         * @param cliOptions  The command line options.
         * @param configType  The specific configuration type (e.g., baseConfig, patchConfig).
         * @return The config value or general config if the specific config is not provided.
         */
        private String initializeConfig(final CommandLine cliOptions, final String configType) {
            final String config = cliOptions.getOptionValue(configType);
            final String generalConfig = cliOptions.getOptionValue("config");

            if (config == null && generalConfig != null) {
                return generalConfig;
            }
            return config;
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
            final Map<String, Object> config = new HashMap<>();
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
            final Map<String, Object> config = new HashMap<>();
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
            final Map<String, Object> config = new HashMap<>();
            config.put("reportsDir", reportsDir);
            config.put("masterReportsDir", masterReportsDir);
            config.put("patchReportsDir", patchReportsDir);
            config.put("baseConfig", baseConfig);
            config.put("patchConfig", patchConfig);
            config.put("shortFilePaths", shortFilePaths);
            config.put("mode", mode);
            config.put("allowExcludes", allowExcludes);
            config.put("useShallowClone", useShallowClone);
            config.put("diffToolJarPath", diffToolJarPath);
            return config;
        }
    }

    /**
     * Represents the Checkstyle report information.
     */
    private static class CheckstyleReportInfo {
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

    /**
     * Represents a project with its SCM details and optional excludes.
     */
    public static class Project {
        /** The name of the project. */
        private String name;

        /** The SCM type (e.g., git). */
        private String scm;

        /** The repository URL. */
        private String url;

        /** The branch or commit reference. */
        private String reference;

        /** The list of excludes patterns. */
        private List<String> excludes = new ArrayList<>();

        /**
         * Gets the project name.
         *
         * @return the name of the project.
         */
        public String getName() {
            return name;
        }

        /**
         * Sets the project name.
         *
         * @param name the name to set.
         */
        public void setName(final String name) {
            this.name = name;
        }

        /**
         * Gets the SCM type (e.g., git).
         *
         * @return the SCM type.
         */
        public String getScm() {
            return scm;
        }

        /**
         * Sets the SCM type.
         *
         * @param scm the SCM type to set.
         */
        public void setScm(final String scm) {
            this.scm = scm;
        }

        /**
         * Gets the repository URL.
         *
         * @return the repository URL.
         */
        public String getUrl() {
            return url;
        }

        /**
         * Sets the repository URL.
         *
         * @param url the URL to set.
         */
        public void setUrl(final String url) {
            this.url = url;
        }

        /**
         * Gets the branch or commit reference.
         *
         * @return the reference.
         */
        public String getReference() {
            return reference;
        }

        /**
         * Sets the branch or commit reference.
         *
         * @param reference the reference to set.
         */
        public void setReference(final String reference) {
            this.reference = reference;
        }

        /**
         * Gets the list of exclude patterns.
         *
         * @return a new list containing the exclude patterns.
         */
        public List<String> getExcludes() {
            return new ArrayList<>(this.excludes);
        }

        /**
         * Sets the list of exclude patterns.
         *
         * @param excludes the list of excludes to set.
         */
        public void setExcludes(final List<String> excludes) {
            if (excludes != null) {
                this.excludes = new ArrayList<>(excludes);
            }
            else {
                this.excludes.clear();
            }
        }
    }
}

