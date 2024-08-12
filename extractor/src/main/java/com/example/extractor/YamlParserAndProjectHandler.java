package com.example.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Utility class for handling YAML parsing and project file processing.
 */
public final class YamlParserAndProjectHandler {

    /**
     * Path to the properties file containing all projects.
     */
    public static final String ALL_PROJECTS_PATH = "src/main/resources/all-projects.properties";

    /**
     * Path to the default properties file containing a list of projects.
     */
    public static final String DEFAULT_PROJECTS_PATH = "src/main/resources/list-of-projects.properties";

    /**
     * Default comments for the project properties file.
     */
    private static final String DEFAULT_COMMENTS =
            "# List of GIT repositories to clone / pull for checking with Checkstyle\n" +
                    "# File format: REPO_NAME|[local|git]|URL|[COMMIT_ID]|[EXCLUDE FOLDERS]\n" +
                    "# Please note that bash comments work in this file\n\n";

    /**
     * Path to the YAML file containing project configurations.
     */
    private static final String YAML_FILE_PATH = "src/main/resources/projects-for-example.yml";

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException if an attempt is made to instantiate this class
     */
    private YamlParserAndProjectHandler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Processes projects for examples and creates project files for each example.
     *
     * @param testConfigPath the path to the test configuration directory
     * @throws IOException if an I/O error occurs
     */
    public static void processProjectsForExamples(final String testConfigPath) throws IOException {
        final Map<String, Object> yamlData = parseYamlFile();
        final List<String> allProjectLines = Files.readAllLines(Paths.get(ALL_PROJECTS_PATH));

        for (final Map.Entry<String, Object> entry : yamlData.entrySet()) {
            final String checkName = entry.getKey();
            final Map<String, Object> checkData = (Map<String, Object>) entry.getValue();

            for (final Map.Entry<String, Object> exampleEntry : checkData.entrySet()) {
                final String exampleName = exampleEntry.getKey();
                final Map<String, Object> exampleData = (Map<String, Object>) exampleEntry.getValue();
                final List<String> projectNames = (List<String>) exampleData.get("projects");

                final Path examplePath = Paths.get(testConfigPath, checkName, exampleName);
                createProjectsFileForExample(examplePath, projectNames, allProjectLines, checkName);

                if ("all-examples-in-one".equals(exampleName)) {
                    createAllInOneProjectsFile(Paths.get(testConfigPath, checkName), projectNames, allProjectLines, checkName);
                }
            }
        }
    }

    /**
     * Parses the YAML file containing project configurations.
     *
     * @return a map representing the YAML data
     * @throws IOException if an I/O error occurs
     */
    @SuppressWarnings("AvoidFileStream")
    static Map<String, Object> parseYamlFile() throws IOException {
        try (InputStream inputStream = Files.newInputStream(Paths.get(YAML_FILE_PATH))) {
            return new Yaml().load(inputStream);
        }
    }

    /**
     * Creates a project properties file for a specific example.
     *
     * @param examplePath     the path to the example directory
     * @param projectNames    the list of project names
     * @param allProjectLines the list of all project lines
     * @param checkName       the name of the check
     * @throws IOException if an I/O error occurs
     */
    static void createProjectsFileForExample(final Path examplePath,
                                             final List<String> projectNames,
                                             final List<String> allProjectLines,
                                             final String checkName) throws IOException {
        Files.createDirectories(examplePath);
        final Path projectsFilePath = examplePath.resolve("list-of-projects.properties");

        final List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            addProjectInfos(projectNames, allProjectLines, fileContents, checkName);
        } else {
            fileContents.addAll(Files.readAllLines(Paths.get(DEFAULT_PROJECTS_PATH)));
        }

        Files.write(projectsFilePath, fileContents);
    }

    /**
     * Creates a project properties file
     * for the "all-examples-in-one" case.
     *
     * @param modulePath      the path to the module directory
     * @param projectNames    the list of project names
     * @param allProjectLines the list of all project lines
     * @param checkName       the name of the check
     * @throws IOException if an I/O error occurs
     */
    private static void createAllInOneProjectsFile(final Path modulePath,
                                                   final List<String> projectNames,
                                                   final List<String> allProjectLines,
                                                   final String checkName) throws IOException {
        final Path allInOnePath = modulePath.resolve("all-examples-in-one");
        Files.createDirectories(allInOnePath);
        final Path projectsFilePath = allInOnePath.resolve("list-of-projects.properties");

        final List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            addProjectInfos(projectNames, allProjectLines, fileContents, checkName + ", Example: all-examples-in-one");
        } else {
            fileContents.addAll(allProjectLines);
        }

        Files.write(projectsFilePath, fileContents);
    }

    /**
     * Adds project information to the file contents for a specific example.
     *
     * @param projectNames    the list of project names
     * @param allProjectLines the list of all project lines
     * @param fileContents    the file contents to which project information will be added
     * @param context         the context of the example or check
     * @throws IllegalArgumentException
     */
    private static void addProjectInfos(final List<String> projectNames,
                                        final List<String> allProjectLines,
                                        final List<String> fileContents,
                                        final String context) {
        for (final String projectName : projectNames) {
            final String projectInfo = findProjectInfo(projectName, allProjectLines);
            if (projectInfo != null) {
                fileContents.add(projectInfo);
            } else {
                throw new IllegalArgumentException("Project not found in all-projects.properties: " + projectName + " (Context: " + context + ")");
            }
        }
    }

    /**
     * Finds the project information for a given project name
     * in the list of all project lines.
     *
     * @param projectName     the name of the project
     * @param allProjectLines the list of all project lines
     * @return the project information line, or {@code null} if not found
     */
    private static String findProjectInfo(final String projectName, final List<String> allProjectLines) {
        return allProjectLines.stream()
                .filter(line -> line.startsWith(projectName + "|"))
                .findFirst()
                .orElse(null);
    }
}