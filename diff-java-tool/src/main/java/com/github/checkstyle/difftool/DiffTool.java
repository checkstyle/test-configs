package com.github.checkstyle.difftool;

import java.io.*;
import java.nio.file.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.apache.commons.cli.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import org.apache.maven.shared.invoker.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DiffTool {

    public static void main(String[] args) {
        try {
            CommandLine cliOptions = getCliOptions(args);
            if (cliOptions != null) {
                if (areValidCliOptions(cliOptions)) {
                    Config cfg = new Config(cliOptions);
                    List<String> configFilesList = Arrays.asList(cfg.getConfig(), cfg.getBaseConfig(), cfg.getPatchConfig(), cfg.getListOfProjects());
                    copyConfigFilesAndUpdatePaths(configFilesList);

                    if (cfg.getLocalGitRepo() != null && hasUnstagedChanges(cfg.getLocalGitRepo())) {
                        String exMsg = "Error: git repository " + cfg.getLocalGitRepo().getPath() + " has unstaged changes!";
                        throw new IllegalStateException(exMsg);
                    }

                    // Delete work directories to avoid conflicts with previous reports generation
                    if (new File(cfg.getReportsDir()).exists()) {
                        deleteDir(cfg.getReportsDir());
                    }
                    if (new File(cfg.getTmpReportsDir()).exists()) {
                        deleteDir(cfg.getTmpReportsDir());
                    }

                    CheckstyleReportInfo checkstyleBaseReportInfo = null;
                    if (cfg.isDiffMode()) {
                        checkstyleBaseReportInfo = launchCheckstyleReport(cfg.getCheckstyleToolBaseConfig());
                    }

                    CheckstyleReportInfo checkstylePatchReportInfo = launchCheckstyleReport(cfg.getCheckstyleToolPatchConfig());

                    if (checkstylePatchReportInfo != null) {
                        deleteDir(cfg.getReportsDir());
                        moveDir(cfg.getTmpReportsDir(), cfg.getReportsDir());

                        generateDiffReport(cfg.getDiffToolConfig());
                        generateSummaryIndexHtml(cfg.getDiffDir(), checkstyleBaseReportInfo,
                                checkstylePatchReportInfo, configFilesList, cfg.isAllowExcludes());
                    }
                } else {
                    throw new IllegalArgumentException("Error: invalid command line arguments!");
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static CommandLine getCliOptions(String[] args) {
        Options options = new Options();
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

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("DiffTool", options);
            System.exit(1);
        }

        return cmd;
    }

    private static boolean areValidCliOptions(CommandLine cliOptions) {
        boolean valid = true;
        String baseConfig = cliOptions.getOptionValue("baseConfig");
        String patchConfig = cliOptions.getOptionValue("patchConfig");
        String config = cliOptions.getOptionValue("config");
        String toolMode = cliOptions.getOptionValue("mode");
        String patchBranch = cliOptions.getOptionValue("patchBranch");
        String baseBranch = cliOptions.getOptionValue("baseBranch");
        File listOfProjectsFile = new File(cliOptions.getOptionValue("listOfProjects"));
        String localGitRepo = cliOptions.getOptionValue("localGitRepo");

        if (toolMode != null && !("diff".equals(toolMode) || "single".equals(toolMode))) {
            System.err.println("Error: Invalid mode: '" + toolMode + "'. The mode should be 'single' or 'diff'!");
            valid = false;
        }
        else if (!isValidCheckstyleConfigsCombination(config, baseConfig, patchConfig, toolMode)) {
            valid = false;
        }
        else if (localGitRepo != null && !isValidGitRepo(new File(localGitRepo))) {
            System.err.println("Error: " + localGitRepo + " is not a valid git repository!");
            valid = false;
        }
        else if (localGitRepo != null && !isExistingGitBranch(new File(localGitRepo), patchBranch)) {
            System.err.println("Error: " + patchBranch + " is not an existing git branch!");
            valid = false;
        }
        else if (baseBranch != null && !isExistingGitBranch(new File(localGitRepo), baseBranch)) {
            System.err.println("Error: " + baseBranch + " is not an existing git branch!");
            valid = false;
        }
        else if (!listOfProjectsFile.exists()) {
            System.err.println("Error: file " + listOfProjectsFile.getName() + " does not exist!");
            valid = false;
        }

        return valid;
    }

    private static boolean isValidCheckstyleConfigsCombination(String config, String baseConfig, String patchConfig, String toolMode) {
        if (config == null && patchConfig == null && baseConfig == null) {
            System.err.println("Error: you should specify either 'config', or 'baseConfig', or 'patchConfig'!");
            return false;
        }
        else if (config != null && (patchConfig != null || baseConfig != null)) {
            System.err.println("Error: you should specify either 'config', or 'baseConfig' and 'patchConfig', or 'patchConfig' only!");
            return false;
        }
        else if ("diff".equals(toolMode) && baseConfig != null && patchConfig == null) {
            System.err.println("Error: 'patchConfig' should be specified!");
            return false;
        }
        else if ("diff".equals(toolMode) && patchConfig != null && baseConfig == null) {
            System.err.println("Error: 'baseConfig' should be specified!");
            return false;
        }
        else if ("single".equals(toolMode) && (baseConfig != null || config != null)) {
            System.err.println("Error: 'baseConfig' and/or 'config' should not be used in 'single' mode!");
            return false;
        }
        return true;
    }

    private static boolean isValidGitRepo(File gitRepoDir) {
        if (gitRepoDir.exists() && gitRepoDir.isDirectory()) {
            try {
                ProcessBuilder pb = new ProcessBuilder("git", "status");
                pb.directory(gitRepoDir);
                Process process = pb.start();
                int exitCode = process.waitFor();
                return exitCode == 0;
            } catch (IOException | InterruptedException e) {
                System.err.println("Error: '" + gitRepoDir.getPath() + "' is not a git repository!");
                return false;
            }
        } else {
            System.err.println("Error: '" + gitRepoDir.getPath() + "' does not exist or it is not a directory!");
            return false;
        }
    }

    private static boolean isExistingGitBranch(File gitRepo, String branchName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--verify", branchName);
            pb.directory(gitRepo);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Error: git repository " + gitRepo.getPath() + " does not have a branch with name '" + branchName + "'!");
                return false;
            }
            return true;
        } catch (IOException | InterruptedException e) {
            System.err.println("Error checking branch existence: " + e.getMessage());
            return false;
        }
    }

    private static void copyConfigFilesAndUpdatePaths(List<String> configFilesList) throws IOException {
        // Create a mutable copy of the list
        List<String> mutableList = new ArrayList<>(configFilesList);

        // Remove null or empty values
        mutableList.removeIf(file -> file == null || file.isEmpty());

        // Remove duplicates
        Set<String> uniqueFiles = new LinkedHashSet<>(mutableList);
        for (String filename : uniqueFiles) {
            if (filename != null && !filename.isEmpty()) {
                Path sourceFile = Paths.get(filename);
                File checkstyleTesterDir = new File("").getCanonicalFile();
                Path destFile = Paths.get(checkstyleTesterDir.getPath(), sourceFile.getFileName().toString());
                Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static boolean hasUnstagedChanges(File gitRepo) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "diff", "--exit-code");
            pb.directory(gitRepo);
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return false;
            } else {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                return true;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error checking for unstaged changes: " + e.getMessage());
            return true;
        }
    }

    private static String getCheckstyleVersionFromPomXml(String pathToPomXml, String xmlTagName) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(pathToPomXml));
            for (String line : lines) {
                if (line.matches("^.*<" + xmlTagName + ">.*-SNAPSHOT</" + xmlTagName + ">.*")) {
                    int start = line.indexOf('>') + 1;
                    int end = line.lastIndexOf('<');
                    return line.substring(start, end);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading POM file: " + e.getMessage());
        }
        return null;
    }

    private static CheckstyleReportInfo launchCheckstyleReport(Map<String, Object> cfg) throws IOException, InterruptedException {
        CheckstyleReportInfo reportInfo = null;
        boolean isRegressionTesting = cfg.get("branch") != null && cfg.get("localGitRepo") != null;

        if (isRegressionTesting) {
            System.out.println("Installing Checkstyle artifact (" + cfg.get("branch") + ") into local Maven repository ...");
            executeCmd("git checkout " + cfg.get("branch"), (File) cfg.get("localGitRepo"));
            executeCmd("git log -1 --pretty=MSG:%s%nSHA-1:%H", (File) cfg.get("localGitRepo"));
            executeCmd("mvn -e --no-transfer-progress --batch-mode -Pno-validations clean install", (File) cfg.get("localGitRepo"));
        }

        cfg.put("checkstyleVersion", getCheckstyleVersionFromPomXml(cfg.get("localGitRepo") + "/pom.xml", "version"));

        generateCheckstyleReport(cfg);

        System.out.println("Moving Checkstyle report into " + cfg.get("destDir") + " ...");
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

    private static void generateCheckstyleReport(Map<String, Object> cfg) throws InterruptedException {
        System.out.println("Testing Checkstyle started");

        String targetDir = "target";
        String srcDir = getOsSpecificPath("src", "main", "java");
        String reposDir = "repositories";
        String reportsDir = "reports";
        makeWorkDirsIfNotExist(srcDir, reposDir, reportsDir);

        final int repoNameParamNo = 0;
        final int repoTypeParamNo = 1;
        final int repoURLParamNo = 2;
        final int repoCommitIDParamNo = 3;
        final int repoExcludesParamNo = 4;
        final int fullParamListSize = 5;

        String checkstyleConfig = (String) cfg.get("checkstyleCfg");
        String checkstyleVersion = (String) cfg.get("checkstyleVersion");
        boolean allowExcludes = (boolean) cfg.get("allowExcludes");
        boolean useShallowClone = (boolean) cfg.get("useShallowClone");
        String listOfProjects = (String) cfg.get("listOfProjects");
        String extraMvnRegressionOptions = (String) cfg.get("extraMvnRegressionOptions");

        try {
            List<String> projects = Files.readAllLines(Paths.get(listOfProjects));
            for (String project : projects) {
                if (!project.startsWith("#") && !project.isEmpty()) {
                    String[] params = project.split("\\|", -1);
                    if (params.length < fullParamListSize) {
                        throw new IllegalArgumentException("Error: line '" + project + "' in file '" + listOfProjects + "' should have " + fullParamListSize + " pipe-delimited sections!");
                    }

                    String repoName = params[repoNameParamNo];
                    String repoType = params[repoTypeParamNo];
                    String repoUrl = params[repoURLParamNo];
                    String commitId = params[repoCommitIDParamNo];

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

            // restore empty_file to make src directory tracked by git
            new File(getOsSpecificPath(srcDir, "empty_file")).createNewFile();
        } catch (IOException e) {
            System.err.println("Error processing projects: " + e.getMessage());
        }
    }

    private static String getLastCheckstyleCommitSha(File gitRepo, String branch) throws IOException, InterruptedException {
        executeCmd("git checkout " + branch, gitRepo);
        try {
            Process process = Runtime.getRuntime().exec("git rev-parse HEAD", null, gitRepo);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim();
        } catch (IOException e) {
            System.err.println("Error getting last commit SHA: " + e.getMessage());
            return "";
        }
    }

    private static String getLastCommitMsg(File gitRepo, String branch) throws IOException, InterruptedException {
        executeCmd("git checkout " + branch, gitRepo);
        try {
            Process process = Runtime.getRuntime().exec("git log -1 --pretty=%B", null, gitRepo);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim();
        } catch (IOException e) {
            System.err.println("Error getting last commit message: " + e.getMessage());
            return "";
        }
    }

    private static String getLastCommitTime(File gitRepo, String branch) throws IOException, InterruptedException {
        executeCmd("git checkout " + branch, gitRepo);
        try {
            Process process = Runtime.getRuntime().exec("git log -1 --format=%cd", null, gitRepo);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            return reader.readLine().trim();
        } catch (IOException e) {
            System.err.println("Error getting last commit time: " + e.getMessage());
            return "";
        }
    }

    private static String getCommitSha(String commitId, String repoType, String srcDestinationDir) {
        String cmd;
        switch (repoType) {
            case "git":
                cmd = "git rev-parse " + commitId;
                break;
            default:
                throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
        }
        try {
            Process process = Runtime.getRuntime().exec(cmd, null, new File(srcDestinationDir));
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String sha = reader.readLine();
            return sha != null ? sha.replace("\n", "") : "";
        } catch (IOException e) {
            System.err.println("Error getting commit SHA: " + e.getMessage());
            return "";
        }
    }

    private static void shallowCloneRepository(String repoName, String repoType, String repoUrl, String commitId, String srcDir) throws IOException {
        String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            String cloneCmd = getCloneShallowCmd(repoType, repoUrl, srcDestinationDir, commitId);
            System.out.println("Shallow clone " + repoType + " repository '" + repoName + "' to " + srcDestinationDir + " folder ...");
            executeCmdWithRetry(cloneCmd);
            System.out.println("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
        }
        System.out.println(repoName + " is synchronized");
    }

    private static void cloneRepository(String repoName, String repoType, String repoUrl, String commitId, String srcDir) throws IOException, InterruptedException {
        String srcDestinationDir = getOsSpecificPath(srcDir, repoName);
        if (!Files.exists(Paths.get(srcDestinationDir))) {
            String cloneCmd = getCloneCmd(repoType, repoUrl, srcDestinationDir);
            System.out.println("Cloning " + repoType + " repository '" + repoName + "' to " + srcDestinationDir + " folder ...");
            executeCmdWithRetry(cloneCmd);
            System.out.println("Cloning " + repoType + " repository '" + repoName + "' - completed\n");
        }

        if (commitId != null && !commitId.isEmpty()) {
            String lastCommitSha = getLastProjectCommitSha(repoType, srcDestinationDir);
            String commitIdSha = getCommitSha(commitId, repoType, srcDestinationDir);
            if (!lastCommitSha.equals(commitIdSha)) {
                if (!isGitSha(commitId)) {
                    // If commitId is a branch or tag, fetch more data and then reset
                    fetchAdditionalData(repoType, srcDestinationDir, commitId);
                }
                String resetCmd = getResetCmd(repoType, commitId);
                System.out.println("Resetting " + repoType + " sources to commit '" + commitId + "'");
                executeCmd(resetCmd, new File(srcDestinationDir));
            }
        }
        System.out.println(repoName + " is synchronized");
    }

    private static String getCloneCmd(String repoType, String repoUrl, String srcDestinationDir) {
        if ("git".equals(repoType)) {
            return "git clone " + repoUrl + " " + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static String getCloneShallowCmd(String repoType, String repoUrl, String srcDestinationDir, String commitId) {
        if ("git".equals(repoType)) {
            return "git clone --depth 1 --branch " + commitId + " " + repoUrl + " " + srcDestinationDir;
        }
        throw new IllegalArgumentException("Error! Unknown repository type: " + repoType);
    }

    private static void fetchAdditionalData(String repoType, String srcDestinationDir, String commitId) throws IOException, InterruptedException {
        String fetchCmd;
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

    private static boolean isTag(String commitId, File gitRepo) {
        try {
            Process process = Runtime.getRuntime().exec("git tag -l " + commitId, null, gitRepo);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            return result != null && result.trim().equals(commitId);
        } catch (IOException e) {
            System.err.println("Error checking if commit is a tag: " + e.getMessage());
            return false;
        }
    }

    // it is not very accurate match, but in case of mismatch we will do full clone
    private static boolean isGitSha(String value) {
        return value.matches("[0-9a-f]{5,40}");
    }

    private static void executeCmdWithRetry(String cmd, File dir, int retry) {
        String osSpecificCmd = getOsSpecificCmd(cmd);
        int left = retry;
        while (true) {
            try {
                ProcessBuilder pb = new ProcessBuilder(osSpecificCmd.split("\\s+"));
                pb.directory(dir);
                pb.inheritIO();
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    if (left <= 0) {
                        throw new RuntimeException("Error executing command: " + cmd);
                    } else {
                        Thread.sleep(15000);
                        left--;
                    }
                } else {
                    break;
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error executing command: " + e.getMessage());
                if (left <= 0) {
                    throw new RuntimeException("Error executing command: " + cmd, e);
                } else {
                    left--;
                }
            }
        }
    }

    private static void executeCmdWithRetry(String cmd) {
        executeCmdWithRetry(cmd, new File("").getAbsoluteFile(), 5);
    }

    private static void generateDiffReport(Map<String, Object> cfg) throws Exception {
        Path diffToolDir = Paths.get("").toAbsolutePath().getParent().resolve("patch-diff-report-tool");

        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(new File(diffToolDir.toFile(), "pom.xml"));
        request.setGoals(Arrays.asList("clean", "package"));
        request.setProfiles(Collections.singletonList("no-validations"));
        request.setMavenOpts("-DskipTests=true");

        Invoker invoker = new DefaultInvoker();
        InvocationResult result = invoker.execute(request);

        if (result.getExitCode() != 0) {
            throw new IllegalStateException("Maven build failed");
        }

        String diffToolJarPath = getPathToDiffToolJar(diffToolDir.toFile());

        System.out.println("Starting diff report generation ...");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get((String) cfg.get("patchReportsDir")))) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    String projectName = path.getFileName().toString();
                    File patchReportDir = new File((String) cfg.get("patchReportsDir"), projectName);
                    if (patchReportDir.exists()) {
                        String patchReport = Paths.get((String) cfg.get("patchReportsDir"), projectName, "checkstyle-result.xml").toString();
                        String outputDir = Paths.get((String) cfg.get("reportsDir"), "diff", projectName).toString();
                        String diffCmd = String.format("java -jar %s --patchReport %s --output %s --patchConfig %s",
                                diffToolJarPath, patchReport, outputDir, cfg.get("patchConfig"));
                        if ("diff".equals(cfg.get("mode"))) {
                            String baseReport = Paths.get((String) cfg.get("masterReportsDir"), projectName, "checkstyle-result.xml").toString();
                            diffCmd += String.format(" --baseReport %s --baseConfig %s", baseReport, cfg.get("baseConfig"));
                        }
                        if ((boolean) cfg.get("shortFilePaths")) {
                            diffCmd += " --shortFilePaths";
                        }
                        executeCmd(diffCmd);
                    } else {
                        throw new FileNotFoundException("Error: patch report for project " + projectName + " is not found!");
                    }
                }
            }
        }
        System.out.println("Diff report generation finished ...");
    }

    private static String getPathToDiffToolJar(File diffToolDir) throws IOException {
        Path targetDir = Paths.get(diffToolDir.getAbsolutePath(), "target");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (fileName.matches("patch-diff-report-tool-.*.jar-with-dependencies.jar")) {
                    return path.toAbsolutePath().toString();
                }
            }
        }
        throw new FileNotFoundException("Error: diff tool jar file is not found!");
    }

    private static Object getTextTransform() {
        try {
            Path diffToolDir = Paths.get("").toAbsolutePath().getParent().resolve("patch-diff-report-tool");
            String diffToolJarPath = getPathToDiffToolJar(diffToolDir.toFile());
            URL[] urls = { new URL("file:" + diffToolJarPath) };
            ClassLoader cl = new URLClassLoader(urls);
            Class<?> clazz = cl.loadClass("com.github.checkstyle.site.TextTransform");
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.err.println("Error loading TextTransform: " + e.getMessage());
            return null;
        }
    }

    private static void generateSummaryIndexHtml(String diffDir, CheckstyleReportInfo checkstyleBaseReportInfo,
                                                 CheckstyleReportInfo checkstylePatchReportInfo, List<String> configFilesList, boolean allowExcludes) throws IOException {
        System.out.println("Starting creating report summary page ...");
        Map<String, int[]> projectsStatistic = getProjectsStatistic(diffDir);
        File summaryIndexHtml = new File(diffDir, "index.html");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(summaryIndexHtml))) {
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

            List<Map.Entry<String, int[]>> sortedProjects = new ArrayList<>(projectsStatistic.entrySet());
            sortedProjects.sort(Comparator
                    .comparing((Map.Entry<String, int[]> e) -> e.getKey().toLowerCase())
                    .thenComparing(e -> e.getValue()[0] == 0 ? 1 : 0));

            for (Map.Entry<String, int[]> entry : sortedProjects) {
                String project = entry.getKey();
                int[] diffCount = entry.getValue();
                writer.write("<a href='" + project + "/index.html'>" + project + "</a>");
                if (diffCount[0] != 0) {
                    if (diffCount[1] == 0) {
                        writer.write(String.format(" ( &#177;%d, <span style=\"color: red;\">-%d</span> )", diffCount[0], diffCount[2]));
                    } else if (diffCount[2] == 0) {
                        writer.write(String.format(" ( &#177;%d, <span style=\"color: green;\">+%d</span> )", diffCount[0], diffCount[1]));
                    } else {
                        writer.write(String.format(" ( &#177;%d, <span style=\"color: red;\">-%d</span>, <span style=\"color: green;\">+%d</span> )",
                                diffCount[0], diffCount[2], diffCount[1]));
                    }
                }
                writer.write("<br />");
                writer.write("\n");
            }
            writer.write("</body></html>");
        }

        System.out.println("Creating report summary page finished...");
    }

    private static void printConfigSection(String diffDir, List<String> configFilesList, BufferedWriter summaryIndexHtml) throws IOException {
        Object textTransform = getTextTransform();
        for (String filename : configFilesList) {
            File configFile = new File(filename);
            generateAndPrintConfigHtmlFile(diffDir, configFile, textTransform, summaryIndexHtml);
        }
    }

    private static void generateAndPrintConfigHtmlFile(String diffDir, File configFile, Object textTransform, BufferedWriter summaryIndexHtml) throws IOException {
        String configfilenameWithoutExtension = getFilenameWithoutExtension(configFile.getName());
        File configFileHtml = new File(diffDir, configfilenameWithoutExtension + ".html");

        if (textTransform != null) {
            try {
                textTransform.getClass().getMethod("transform", String.class, String.class, Locale.class, String.class, String.class)
                        .invoke(textTransform, configFile.getName(), configFileHtml.toPath().toString(), Locale.ENGLISH, "UTF-8", "UTF-8");
            } catch (Exception e) {
                System.err.println("Error transforming config file: " + e.getMessage());
            }
        }

        summaryIndexHtml.write("<h6>");
        summaryIndexHtml.write("<a href='" + configFileHtml.getName() + "'>" + configFile.getName() + " file</a>");
        summaryIndexHtml.write("</h6>");
    }

    private static String getFilenameWithoutExtension(String filename) {
        int pos = filename.lastIndexOf(".");
        if (pos > 0) {
            return filename.substring(0, pos);
        }
        return filename;
    }

    private static void makeWorkDirsIfNotExist(String srcDirPath, String repoDirPath, String reportsDirPath) {
        File srcDir = new File(srcDirPath);
        if (!srcDir.exists()) {
            srcDir.mkdirs();
        }
        File repoDir = new File(repoDirPath);
        if (!repoDir.exists()) {
            repoDir.mkdir();
        }
        File reportsDir = new File(reportsDirPath);
        if (!reportsDir.exists()) {
            reportsDir.mkdir();
        }
    }

    private static void printReportInfoSection(BufferedWriter summaryIndexHtml, CheckstyleReportInfo checkstyleBaseReportInfo,
                                               CheckstyleReportInfo checkstylePatchReportInfo, Map<String, int[]> projectsStatistic) throws IOException {
        Date date = new Date();
        summaryIndexHtml.write("<h6>");
        if (checkstyleBaseReportInfo != null) {
            summaryIndexHtml.write("Base branch: " + checkstyleBaseReportInfo.branch);
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit SHA: " + checkstyleBaseReportInfo.commitSha);
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit message: \"" + checkstyleBaseReportInfo.commitMsg + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("Base branch last commit timestamp: \"" + checkstyleBaseReportInfo.commitTime + "\"");
            summaryIndexHtml.write("<br />");
            summaryIndexHtml.write("<br />");
        }
        summaryIndexHtml.write("Patch branch: " + checkstylePatchReportInfo.branch);
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit SHA: " + checkstylePatchReportInfo.commitSha);
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit message: \"" + checkstylePatchReportInfo.commitMsg + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Patch branch last commit timestamp: \"" + checkstylePatchReportInfo.commitTime + "\"");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Tested projects: " + projectsStatistic.size());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("&#177; differences found: " + projectsStatistic.values().stream().mapToInt(arr -> arr[0]).sum());
        summaryIndexHtml.write("<br />");
        summaryIndexHtml.write("Time of report generation: " + date);
        summaryIndexHtml.write("</h6>");
    }

    private static Map<String, int[]> getProjectsStatistic(String diffDir) throws IOException {
        Map<String, int[]> projectsStatistic = new HashMap<>();
        int totalDiff = 0;
        int addedDiff = 0;
        int removedDiff = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(diffDir))) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    String projectName = path.getFileName().toString();
                    File indexHtmlFile = new File(path.toFile(), "index.html");
                    if (indexHtmlFile.exists()) {
                        List<String> lines = Files.readAllLines(indexHtmlFile.toPath());
                        for (String line : lines) {
                            Pattern totalPatch = Pattern.compile("id=\"totalPatch\">[0-9]++");
                            Matcher totalPatchMatcher = totalPatch.matcher(line);
                            if (totalPatchMatcher.find()) {
                                Pattern removedPatchLinePattern = Pattern.compile("(?<totalRemoved>[0-9]++) removed");
                                Matcher removedPatchLineMatcher = removedPatchLinePattern.matcher(line);
                                if (removedPatchLineMatcher.find()) {
                                    removedDiff = Integer.parseInt(removedPatchLineMatcher.group("totalRemoved"));
                                } else {
                                    removedDiff = 0;
                                }

                                Pattern addPatchLinePattern = Pattern.compile("(?<totalAdd>[0-9]++) added");
                                Matcher addPatchLineMatcher = addPatchLinePattern.matcher(line);
                                if (addPatchLineMatcher.find()) {
                                    addedDiff = Integer.parseInt(addPatchLineMatcher.group("totalAdd"));
                                } else {
                                    addedDiff = 0;
                                }
                            }

                            Pattern linePattern = Pattern.compile("totalDiff\">(?<totalDiff>[0-9]++)");
                            Matcher lineMatcher = linePattern.matcher(line);
                            if (lineMatcher.find()) {
                                totalDiff = Integer.parseInt(lineMatcher.group("totalDiff"));
                                int[] diffSummary = {totalDiff, addedDiff, removedDiff};
                                projectsStatistic.put(projectName, diffSummary);
                            }
                        }
                    }
                }
            }
        }
        return projectsStatistic;
    }

    private static void runMavenExecution(String srcDir, String excludes, String checkstyleConfig,
                                          String checkstyleVersion, String extraMvnRegressionOptions) throws IOException, InterruptedException {
        System.out.println("Running 'mvn clean' on " + srcDir + " ...");
        String mvnClean = "mvn -e --no-transfer-progress --batch-mode clean";
        executeCmd(mvnClean);
        System.out.println("Running Checkstyle on " + srcDir + " ... with excludes {" + excludes + "}");
        StringBuilder mvnSite = new StringBuilder("mvn -e --no-transfer-progress --batch-mode site " +
                "-Dcheckstyle.config.location=" + checkstyleConfig + " -Dcheckstyle.excludes=" + excludes);
        if (checkstyleVersion != null && !checkstyleVersion.isEmpty()) {
            mvnSite.append(" -Dcheckstyle.version=").append(checkstyleVersion);
        }
        if (extraMvnRegressionOptions != null && !extraMvnRegressionOptions.isEmpty()) {
            mvnSite.append(" ");
            if (!extraMvnRegressionOptions.startsWith("-")) {
                mvnSite.append("-");
            }
            mvnSite.append(extraMvnRegressionOptions);
        }
        System.out.println(mvnSite);
        executeCmd(mvnSite.toString());
        System.out.println("Running Checkstyle on " + srcDir + " - finished");
    }

    private static void postProcessCheckstyleReport(String targetDir, String repoName, String repoPath) throws IOException {
        Path reportPath = Paths.get(targetDir, "checkstyle-result.xml");
        String content = new String(Files.readAllBytes(reportPath));
        content = content.replace(new File(getOsSpecificPath("src", "main", "java", repoName)).getAbsolutePath(),
                getOsSpecificPath(repoPath));
        Files.write(reportPath, content.getBytes());
    }

    private static void copyDir(String source, String destination) throws IOException {
        Path sourcePath = Paths.get(source);
        Path destinationPath = Paths.get(destination);
        Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = destinationPath.resolve(sourcePath.relativize(dir));
                Files.createDirectories(targetPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, destinationPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void moveDir(String source, String destination) throws IOException {
        Path sourcePath = Paths.get(source);
        Path destinationPath = Paths.get(destination);

        // Create the destination directory if it doesn't exist
        Files.createDirectories(destinationPath);

        if (Files.exists(sourcePath)) {
            // If source exists, move its contents
            try (Stream<Path> stream = Files.list(sourcePath)) {
                stream.forEach(file -> {
                    try {
                        Files.move(file, destinationPath.resolve(sourcePath.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        System.err.println("Error moving file " + file + ": " + e.getMessage());
                    }
                });
            }
            // Delete the now-empty source directory
            Files.deleteIfExists(sourcePath);
        } else {
            System.out.println("Source directory " + source + " does not exist. Skipping move operation.");
        }
    }
    private static void deleteDir(String dir) throws IOException {
        Path directory = Paths.get(dir);
        if (Files.exists(directory)) {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void executeCmd(String cmd, File dir) throws IOException, InterruptedException {
        System.out.println("Running command: " + cmd);
        ProcessBuilder pb = new ProcessBuilder(getOsSpecificCmd(cmd).split("\\s+"));
        pb.directory(dir);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Error: Command execution failed with exit code " + exitCode);
        }
    }


    private static void executeCmd(String cmd) throws IOException, InterruptedException {
        executeCmd(cmd, new File("").getAbsoluteFile());
    }

    private static String getOsSpecificCmd(String cmd) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return "cmd /c " + cmd;
        }
        return cmd;
    }

    private static String getOsSpecificPath(String... name) {
        return String.join(File.separator, name);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private static String getResetCmd(String repoType, String commitId) {
        if ("git".equals(repoType)) {
            if (isGitSha(commitId)) {
                return "git reset --hard " + commitId;
            } else {
                return "git reset --hard refs/tags/" + commitId;
            }
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static String getLastProjectCommitSha(String repoType, String srcDestinationDir) throws IOException, InterruptedException {
        if ("git".equals(repoType)) {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "HEAD");
            pb.directory(new File(srcDestinationDir));
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String sha = reader.readLine();
                process.waitFor();
                return sha != null ? sha.trim() : "";
            }
        }
        throw new IllegalArgumentException("Error! Unknown " + repoType + " repository.");
    }

    private static class Config {
        File localGitRepo;
        boolean shortFilePaths;
        String listOfProjects;
        String mode;
        String baseBranch;
        String patchBranch;
        String baseConfig;
        String patchConfig;
        String config;
        String reportsDir;
        String masterReportsDir;
        String patchReportsDir;
        String tmpReportsDir;
        String tmpMasterReportsDir;
        String tmpPatchReportsDir;
        String diffDir;
        String extraMvnRegressionOptions;
        String checkstyleVersion;
        String sevntuVersion;
        boolean allowExcludes;
        boolean useShallowClone;

        Config(CommandLine cliOptions) {
            if (cliOptions.hasOption("localGitRepo")) {
                localGitRepo = new File(cliOptions.getOptionValue("localGitRepo"));
            }

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
            config = cliOptions.getOptionValue("config");
            if (config != null) {
                baseConfig = config;
                patchConfig = config;
            }

            reportsDir = "reports";
            masterReportsDir = reportsDir + "/" + baseBranch;
            patchReportsDir = reportsDir + "/" + patchBranch;

            tmpReportsDir = "tmp_reports";
            tmpMasterReportsDir = tmpReportsDir + "/" + baseBranch;
            tmpPatchReportsDir = tmpReportsDir + "/" + patchBranch;

            diffDir = reportsDir + "/diff";
        }

        // Getters and setters
        public File getLocalGitRepo() { return localGitRepo; }
        public void setLocalGitRepo(File localGitRepo) { this.localGitRepo = localGitRepo; }

        public boolean isShortFilePaths() { return shortFilePaths; }
        public void setShortFilePaths(boolean shortFilePaths) { this.shortFilePaths = shortFilePaths; }

        public String getListOfProjects() { return listOfProjects; }
        public void setListOfProjects(String listOfProjects) { this.listOfProjects = listOfProjects; }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public String getBaseBranch() { return baseBranch; }
        public void setBaseBranch(String baseBranch) { this.baseBranch = baseBranch; }

        public String getPatchBranch() { return patchBranch; }
        public void setPatchBranch(String patchBranch) { this.patchBranch = patchBranch; }

        public String getBaseConfig() { return baseConfig; }
        public void setBaseConfig(String baseConfig) { this.baseConfig = baseConfig; }

        public String getPatchConfig() { return patchConfig; }
        public void setPatchConfig(String patchConfig) { this.patchConfig = patchConfig; }

        public String getConfig() { return config; }
        public void setConfig(String config) { this.config = config; }

        public String getReportsDir() { return reportsDir; }
        public void setReportsDir(String reportsDir) { this.reportsDir = reportsDir; }

        public String getMasterReportsDir() { return masterReportsDir; }
        public void setMasterReportsDir(String masterReportsDir) { this.masterReportsDir = masterReportsDir; }

        public String getPatchReportsDir() { return patchReportsDir; }
        public void setPatchReportsDir(String patchReportsDir) { this.patchReportsDir = patchReportsDir; }

        public String getTmpReportsDir() { return tmpReportsDir; }
        public void setTmpReportsDir(String tmpReportsDir) { this.tmpReportsDir = tmpReportsDir; }

        public String getTmpMasterReportsDir() { return tmpMasterReportsDir; }
        public void setTmpMasterReportsDir(String tmpMasterReportsDir) { this.tmpMasterReportsDir = tmpMasterReportsDir; }

        public String getTmpPatchReportsDir() { return tmpPatchReportsDir; }
        public void setTmpPatchReportsDir(String tmpPatchReportsDir) { this.tmpPatchReportsDir = tmpPatchReportsDir; }

        public String getDiffDir() { return diffDir; }
        public void setDiffDir(String diffDir) { this.diffDir = diffDir; }

        public String getExtraMvnRegressionOptions() { return extraMvnRegressionOptions; }
        public void setExtraMvnRegressionOptions(String extraMvnRegressionOptions) { this.extraMvnRegressionOptions = extraMvnRegressionOptions; }

        public String getCheckstyleVersion() { return checkstyleVersion; }
        public void setCheckstyleVersion(String checkstyleVersion) { this.checkstyleVersion = checkstyleVersion; }

        public String getSevntuVersion() { return sevntuVersion; }
        public void setSevntuVersion(String sevntuVersion) { this.sevntuVersion = sevntuVersion; }

        public boolean isAllowExcludes() { return allowExcludes; }
        public void setAllowExcludes(boolean allowExcludes) { this.allowExcludes = allowExcludes; }

        public boolean isUseShallowClone() { return useShallowClone; }
        public void setUseShallowClone(boolean useShallowClone) { this.useShallowClone = useShallowClone; }

        // Additional methods
        public boolean isDiffMode() {
            return "diff".equals(mode);
        }

        public boolean isSingleMode() {
            return "single".equals(mode);
        }

        public Map<String, Object> getCheckstyleToolBaseConfig() {
            Map<String, Object> config = new HashMap<>();
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

        public Map<String, Object> getCheckstyleToolPatchConfig() {
            Map<String, Object> config = new HashMap<>();
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

        public Map<String, Object> getDiffToolConfig() {
            Map<String, Object> config = new HashMap<>();
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

    private static class CheckstyleReportInfo {
        String branch;
        String commitSha;
        String commitMsg;
        String commitTime;

        CheckstyleReportInfo(String branch, String commitSha, String commitMsg, String commitTime) {
            this.branch = branch;
            this.commitSha = commitSha;
            this.commitMsg = commitMsg;
            this.commitTime = commitTime;
        }

        // Getter methods
        public String getBranch() {
            return branch;
        }

        public String getCommitSha() {
            return commitSha;
        }

        public String getCommitMsg() {
            return commitMsg;
        }

        public String getCommitTime() {
            return commitTime;
        }
    }
}