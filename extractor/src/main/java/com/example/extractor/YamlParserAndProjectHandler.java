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

package com.example.extractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Utility class for handling YAML parsing and project file processing.
 */
public final class YamlParserAndProjectHandler {

    /**
     * Path to the YAML file containing all projects.
     */
    public static final String ALL_PROJECTS_YAML_PATH = "src/main/resources/all-projects.yml";

    /**
     * Path to the YAML file containing all projects for javadoc checks.
     */
    public static final String JAVADOC_PROJECTS_YAML_PATH =
            "src/main/resources/all-projects-for-javadoc-checks.yml";

    /**
     * Path to the default properties file containing a list of projects.
     */
    public static final String ALL_PROJECTS_PROPERTIES_PATH =
            "src/main/resources/all-projects.properties";

    /**
     * Path to the properties file containing all projects for javadoc checks.
     */
    public static final String JAVADOC_PROJECTS_PROPS_PATH =
            "src/main/resources/all-projects-for-javadoc-checks.properties";

    /**
     * Path to the default YAML file containing project configurations.
     */
    private static final String DEFAULT_LIST_YAML_PATH =
            "src/main/resources/list-of-projects.yml";

    /**
     * Path to the javadoc default YAML file containing project configurations.
     */
    private static final String JAVADOC_LIST_YAML_PATH =
            "src/main/resources/list-of-projects-for-javadoc-checks.yml";

    /**
     * Path to the default properties file containing project configurations.
     */
    private static final String DEFAULT_LIST_PROPS_PATH =
            "src/main/resources/list-of-projects.properties";

    /**
     * Path to the javadoc default properties file containing project configurations.
     */
    private static final String JAVADOC_LIST_PROPS_PATH =
            "src/main/resources/list-of-projects-for-javadoc-checks.properties";

    /**
     * Newline character used to ensure content ends with a newline.
     */
    private static final String NEWLINE = "\n";

    /**
     * Default newline to return when content is null or empty.
     */
    private static final String DEFAULT_NEWLINE = NEWLINE;

    /**
     * Default indentation level for YAML formatting.
     * Determines the number of spaces for each indentation level.
     */
    private static final int YAML_INDENT_LEVEL = 4;

    /**
     * Default indicator indentation level for YAML formatting.
     * Determines the number of spaces for indicators (e.g., lists) indentation.
     */
    private static final int YAML_INDICATOR_INDENT_LEVEL = 2;

    /**
     * Default comments for the project properties file.
     */
    private static final String DEFAULT_COMMENTS =
            "# List of GIT repositories to clone / pull for checking with Checkstyle\n"
                    + "# File format: REPO_NAME|[local|git]|URL|[COMMIT_ID]|[EXCLUDE FOLDERS]\n"
                    + "# Please note that bash comments work in this file\n\n";

    /**
     * Path to the YAML file containing project configurations for examples.
     */
    private static final String PROJECTS_FOR_EXAMPLE_YAML_PATH =
            "src/main/resources/projects-for-example.yml";

    /**
     * The path fragment for javadoc check examples.
     */
    private static final String JAVADOC_PATH_FRAGMENT = "/checks/javadoc/";

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
     * @param javadocModuleNames module names that should use javadoc project lists
     * @throws IOException if an I/O error occurs
     */
    public static void processProjectsForExamples(
            final String testConfigPath,
            final Set<String> javadocModuleNames) throws IOException {
        final Map<String, Object> yamlData = parseYamlFile();
        final List<Map<String, Object>> allProjectData = parseAllProjectsYaml();
        final List<Map<String, Object>> javadocProjectData =
                parseAllProjectsForJavadocChecksYaml();
        final List<String> allProjectLines = loadAllProjectsProperties();
        final List<String> javadocProjectLines =
                loadAllProjectsForJavadocChecksProperties();

        for (final Map.Entry<String, Object> entry : yamlData.entrySet()) {
            final String checkName = entry.getKey();
            final Map<String, Object> checkData = (Map<String, Object>) entry.getValue();
            final boolean javadocModule = javadocModuleNames.contains(checkName);
            final List<Map<String, Object>> yamlProjectData;
            final List<String> propertiesProjectLines;
            final String yamlSourceName;
            if (javadocModule) {
                yamlProjectData = javadocProjectData;
                propertiesProjectLines = javadocProjectLines;
                yamlSourceName = JAVADOC_PROJECTS_YAML_PATH;
            }
            else {
                yamlProjectData = allProjectData;
                propertiesProjectLines = allProjectLines;
                yamlSourceName = ALL_PROJECTS_YAML_PATH;
            }

            for (final Map.Entry<String, Object> exampleEntry : checkData.entrySet()) {
                final String exampleName = exampleEntry.getKey();
                final Map<String, Object> exampleData =
                        (Map<String, Object>) exampleEntry.getValue();
                final List<String> projectNames =
                        (List<String>) exampleData.get("projects");

                final Path examplePath = Paths.get(testConfigPath, checkName, exampleName);
                createProjectsYmlFileForExample(examplePath, projectNames,
                        yamlProjectData, checkName, yamlSourceName);
                createProjectsPropertiesFileForExample(examplePath, projectNames,
                        propertiesProjectLines, checkName);

                if ("all-examples-in-one".equals(exampleName)) {
                    createAllInOneProjectsFile(Paths.get(testConfigPath, checkName),
                            projectNames, yamlProjectData, propertiesProjectLines,
                            checkName, yamlSourceName);
                }
            }
        }
    }

    /**
     * Parses the YAML file containing project configurations for examples.
     *
     * @return a map representing the YAML data
     * @throws IOException if an I/O error occurs
     */
    static Map<String, Object> parseYamlFile() throws IOException {
        final Path yamlFilePath = Paths.get(PROJECTS_FOR_EXAMPLE_YAML_PATH);
        try (InputStream inputStream = Files.newInputStream(yamlFilePath)) {
            return new Yaml().load(inputStream);
        }
    }

    /**
     * Parses the YAML file containing all projects.
     *
     * @return a list of maps representing project data
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if project not found
     */
    public static List<Map<String, Object>> parseAllProjectsYaml() throws IOException {
        return parseProjectsYamlFile(ALL_PROJECTS_YAML_PATH);
    }

    /**
     * Parses the YAML file containing all projects for javadoc checks.
     *
     * @return a list of maps representing project data
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if project not found
     */
    public static List<Map<String, Object>> parseAllProjectsForJavadocChecksYaml()
            throws IOException {
        return parseProjectsYamlFile(JAVADOC_PROJECTS_YAML_PATH);
    }

    /**
     * Parses a project-list YAML file.
     *
     * @param projectsYamlPathString the path to the YAML file containing projects
     * @return a list of maps representing project data
     * @throws IOException if an I/O error occurs
     * @throws IllegalStateException if project not found
     */
    private static List<Map<String, Object>> parseProjectsYamlFile(
            final String projectsYamlPathString) throws IOException {
        final Path projectsYamlPath = Paths.get(projectsYamlPathString);
        try (InputStream inputStream = Files.newInputStream(projectsYamlPath)) {
            final Map<String, Object> yamlData = new Yaml().load(inputStream);
            final Object projectsObj = yamlData.get("projects");
            if (projectsObj instanceof List) {
                return (List<Map<String, Object>>) projectsObj;
            }
            else {
                throw new IllegalStateException(
                        "Expected 'projects' to be a list in " + projectsYamlPathString);
            }
        }
    }

    /**
     * Loads all project lines from all-projects.properties.
     *
     * @return a list of project lines
     * @throws IOException if an I/O error occurs
     */
    public static List<String> loadAllProjectsProperties() throws IOException {
        return loadProjectsPropertiesFile(ALL_PROJECTS_PROPERTIES_PATH);
    }

    /**
     * Loads all project lines from all-projects-for-javadoc-checks.properties.
     *
     * @return a list of project lines
     * @throws IOException if an I/O error occurs
     */
    public static List<String> loadAllProjectsForJavadocChecksProperties() throws IOException {
        return loadProjectsPropertiesFile(JAVADOC_PROJECTS_PROPS_PATH);
    }

    /**
     * Loads project YAML data for a generated module.
     *
     * @param javadocModule whether to use javadoc-specific project lists
     * @return a list of maps representing project data
     * @throws IOException if an I/O error occurs
     */
    static List<Map<String, Object>> loadProjectDataForModule(final boolean javadocModule)
            throws IOException {
        return parseProjectsYamlFile(getProjectDataSourceName(javadocModule));
    }

    /**
     * Loads project property lines for a generated module.
     *
     * @param javadocModule whether to use javadoc-specific project lists
     * @return a list of project lines
     * @throws IOException if an I/O error occurs
     */
    static List<String> loadProjectPropertiesForModule(final boolean javadocModule)
            throws IOException {
        final String result;
        if (javadocModule) {
            result = JAVADOC_PROJECTS_PROPS_PATH;
        }
        else {
            result = ALL_PROJECTS_PROPERTIES_PATH;
        }
        return loadProjectsPropertiesFile(result);
    }

    /**
     * Gets the YAML source name for a generated module.
     *
     * @param javadocModule whether to use javadoc-specific project lists
     * @return the YAML source name
     */
    static String getProjectDataSourceName(final boolean javadocModule) {
        final String result;
        if (javadocModule) {
            result = JAVADOC_PROJECTS_YAML_PATH;
        }
        else {
            result = ALL_PROJECTS_YAML_PATH;
        }
        return result;
    }

    /**
     * Gets the default project YAML resource for an input example.
     *
     * @param inputFilePath the input example path
     * @return the classpath resource path
     */
    static String getDefaultProjectsYamlResource(final Path inputFilePath) {
        final String result;
        if (isJavadocExamplePath(inputFilePath)) {
            result = "/" + Paths.get(JAVADOC_LIST_YAML_PATH).getFileName();
        }
        else {
            result = "/" + Paths.get(DEFAULT_LIST_YAML_PATH).getFileName();
        }
        return result;
    }

    /**
     * Gets the default project YAML source path.
     *
     * @param javadocModule whether to use javadoc-specific project lists
     * @return the project YAML source path
     */
    static Path getDefaultProjectsYamlPath(final boolean javadocModule) {
        final String result;
        if (javadocModule) {
            result = JAVADOC_LIST_YAML_PATH;
        }
        else {
            result = DEFAULT_LIST_YAML_PATH;
        }
        return Paths.get(result).toAbsolutePath();
    }

    /**
     * Gets the default project properties source path.
     *
     * @param javadocModule whether to use javadoc-specific project lists
     * @return the project properties source path
     */
    static Path getDefaultProjectsPropertiesPath(final boolean javadocModule) {
        final String result;
        if (javadocModule) {
            result = JAVADOC_LIST_PROPS_PATH;
        }
        else {
            result = DEFAULT_LIST_PROPS_PATH;
        }
        return Paths.get(result).toAbsolutePath();
    }

    /**
     * Gets javadoc module names from generated example directories.
     *
     * @param moduleExamples module names mapped to their example directories
     * @return module names that use javadoc-specific project lists
     */
    static Set<String> getJavadocModuleNames(final Map<String, List<Path>> moduleExamples) {
        return moduleExamples.entrySet().stream()
                .filter(entry -> isJavadocModule(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Checks whether a generated module comes from javadoc examples.
     *
     * @param exampleDirs module example directories
     * @return true if any example directory is under javadoc checks
     */
    static boolean isJavadocModule(final List<Path> exampleDirs) {
        return exampleDirs.stream().anyMatch(YamlParserAndProjectHandler::isJavadocExamplePath);
    }

    /**
     * Checks whether a path is under javadoc checks examples.
     *
     * @param path the path to inspect
     * @return true if the path is under javadoc checks examples
     */
    static boolean isJavadocExamplePath(final Path path) {
        final String normalizedPath = path.toString().replace('\\', '/');
        return normalizedPath.contains(JAVADOC_PATH_FRAGMENT)
                || normalizedPath.startsWith(JAVADOC_PATH_FRAGMENT.substring(1));
    }

    /**
     * Loads project lines from a properties project-list file.
     *
     * @param projectsPropertiesPathString the path to the properties file containing projects
     * @return a list of project lines
     * @throws IOException if an I/O error occurs
     */
    private static List<String> loadProjectsPropertiesFile(
            final String projectsPropertiesPathString) throws IOException {
        final Path allProjectsPropertiesPath = Paths.get(projectsPropertiesPathString);
        try (BufferedReader reader = Files.newBufferedReader(
                allProjectsPropertiesPath, StandardCharsets.UTF_8)) {
            return reader.lines()
                    .map(String::strip)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Creates a project YAML file for a specific example.
     *
     * @param examplePath     the path to the example directory
     * @param projectNames    the list of project names
     * @param allProjectData  the list of all project data
     * @param checkName       the name of the check
     * @param yamlSourceName  the YAML source name for error messages
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if a project name is not found in all-projects.yml
     */
    static void createProjectsYmlFileForExample(final Path examplePath,
                                                final List<String> projectNames,
                                                final List<Map<String, Object>> allProjectData,
                                                final String checkName,
                                                final String yamlSourceName) throws IOException {
        Files.createDirectories(examplePath);
        final Path projectsFilePath =
                examplePath.resolve("list-of-projects.yml");

        final List<Map<String, Object>> projects =
                filterProjects(projectNames, allProjectData, checkName, yamlSourceName);

        final Map<String, Object> yamlData = new ConcurrentHashMap<>();
        yamlData.put("projects", projects);

        // Configure DumperOptions
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(YAML_INDENT_LEVEL);
        options.setIndicatorIndent(YAML_INDICATOR_INDENT_LEVEL);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        final Yaml yaml = new Yaml(options);

        // Dump YAML to a String
        final StringWriter stringWriter = new StringWriter();
        yaml.dump(yamlData, stringWriter);
        final String yamlContent = stringWriter.toString();

        // Insert blank lines between projects
        final String yamlContentWithBlankLines = insertBlankLinesBetweenProjects(yamlContent);

        // Ensure the content ends with a newline
        final String finalYamlContent = ensureEndsWithNewline(yamlContentWithBlankLines);

        // Write to file
        try (Writer writer = Files.newBufferedWriter(projectsFilePath, StandardCharsets.UTF_8)) {
            writer.write(finalYamlContent);
        }
    }

    /**
     * Ensures that the given content ends with a newline character.
     *
     * @param content the original content
     * @return the content guaranteed to end with a newline
     */
    private static String ensureEndsWithNewline(final String content) {
        final String result;

        if (content == null || content.isEmpty()) {
            result = DEFAULT_NEWLINE;
        }
        else {
            if (content.endsWith(NEWLINE)) {
                result = content;
            }
            else {
                result = content + NEWLINE;
            }
        }
        return result;
    }

    /**
     * Adds blank lines between project entries in the provided YAML content.
     *
     * @param yamlContent the YAML content as a string
     * @return the formatted YAML content with blank lines between project entries
     */
    private static String insertBlankLinesBetweenProjects(final String yamlContent) {
        final Pattern pattern = Pattern.compile("^\\s*- name:.*");

        // Split the content into lines
        final List<String> lines = Arrays.asList(yamlContent.split("\\r?\\n"));

        // Use a stream to process lines and insert blank lines where necessary
        final List<String> processedLines = new ArrayList<>();
        final boolean[] firstProject = {true};

        lines.forEach(line -> {
            if (pattern.matcher(line).matches()) {
                if (!firstProject[0]) {
                    processedLines.add("");
                }
                firstProject[0] = false;
            }
            processedLines.add(line);
        });

        // Join the processed lines back into a single string
        return String.join("\n", processedLines);
    }

    /**
     * Creates a project properties file for a specific example.
     *
     * @param examplePath     the path to the example directory
     * @param projectNames    the list of project names
     * @param allProjectLines the list of all project lines from all-projects.properties
     * @param checkName       the name of the check
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if project not found
     */
    static void createProjectsPropertiesFileForExample(final Path examplePath,
                                                       final List<String> projectNames,
                                                       final List<String> allProjectLines,
                                                       final String checkName) throws IOException {
        Files.createDirectories(examplePath);
        final Path projectsFilePath = examplePath.resolve("list-of-projects.properties");

        final List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            for (final String projectName : projectNames) {
                final String projectInfo =
                        findProjectInfoInProperties(projectName, allProjectLines);
                if (projectInfo != null) {
                    fileContents.add(projectInfo);
                }
                else {
                    throw new IllegalArgumentException(
                            "Project not found in all-projects.properties: "
                                    + projectName
                                    + " (Context: "
                                    + checkName
                                    + ")");
                }
            }
        }
        else {
            fileContents.addAll(allProjectLines);
        }

        Files.write(projectsFilePath, fileContents, StandardCharsets.UTF_8);
    }

    /**
     * Creates a project YAML and properties file for the "all-examples-in-one" case.
     *
     * @param modulePath      the path to the module directory
     * @param projectNames    the list of project names
     * @param allProjectData  the list of all project data
     * @param allProjectLines the list of all project lines from all-projects.properties
     * @param checkName       the name of the check
     * @param yamlSourceName  the YAML source name for error messages
     * @throws IOException if an I/O error occurs
     */
    private static void createAllInOneProjectsFile(final Path modulePath,
                                                   final List<String> projectNames,
                                                   final List<Map<String, Object>> allProjectData,
                                                   final List<String> allProjectLines,
                                                   final String checkName,
                                                   final String yamlSourceName)
            throws IOException {
        final Path allInOnePath = prepareAllInOnePath(modulePath);

        // Create YAML and Properties files
        generateProjectsYaml(allInOnePath, projectNames, allProjectData,
                checkName, yamlSourceName);
        generateProjectsProperties(allInOnePath, projectNames, allProjectLines, checkName);
    }

    /**
     * Prepares the all-in-one directory path by creating necessary directories.
     *
     * @param modulePath The base module path.
     * @return The path to the all-in-one directory.
     * @throws IOException If an I/O error occurs during directory creation.
     */
    private static Path prepareAllInOnePath(final Path modulePath) throws IOException {
        final Path allInOnePath = modulePath.resolve("all-examples-in-one");
        Files.createDirectories(allInOnePath);
        return allInOnePath;
    }

    /**
     * Generates the YAML file containing project information.
     *
     * @param allInOnePath    The directory path where the YAML file will be created.
     * @param projectNames    A list of project names to include.
     * @param allProjectData  A list of project data maps.
     * @param checkName       The context name for error messages.
     * @param yamlSourceName  the YAML source name for error messages.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static void generateProjectsYaml(final Path allInOnePath,
                                             final List<String> projectNames,
                                             final List<Map<String, Object>> allProjectData,
                                             final String checkName,
                                             final String yamlSourceName) throws IOException {
        final Path projectsYamlFilePath =
                allInOnePath.resolve("list-of-projects.yml");
        final List<Map<String, Object>> projectsYaml =
                filterProjects(projectNames, allProjectData, checkName, yamlSourceName);

        final Map<String, Object> yamlData = new ConcurrentHashMap<>();
        yamlData.put("projects", projectsYaml);

        final Yaml yaml = new Yaml();
        try (Writer writer =
                     Files.newBufferedWriter(projectsYamlFilePath, StandardCharsets.UTF_8)) {
            yaml.dump(yamlData, writer);
        }
    }

    /**
     * Generates the properties file containing project information.
     *
     * @param allInOnePath     The directory path where the properties file will be created.
     * @param projectNames     A list of project names to include.
     * @param allProjectLines  A list of project lines.
     * @param checkName        The context name for error messages.
     * @throws IOException If an I/O error occurs during file operations.
     * @throws IllegalArgumentException If project not found
     */
    private static void generateProjectsProperties(final Path allInOnePath,
                                                   final List<String> projectNames,
                                                   final List<String> allProjectLines,
                                                   final String checkName) throws IOException {
        final Path projectsPropertiesFilePath = allInOnePath.resolve("list-of-projects.properties");
        final List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            for (final String projectName : projectNames) {
                final String projectInfo =
                        findProjectInfoInProperties(projectName, allProjectLines);
                if (projectInfo != null) {
                    fileContents.add(projectInfo);
                }
                else {
                    throw new IllegalArgumentException(
                            "Project not found in all-projects.properties: "
                                    + projectName
                                    + " (Context: "
                                    + checkName
                                    + ", Example: all-examples-in-one"
                                    + ")");
                }
            }
        }
        else {
            fileContents.addAll(allProjectLines);
        }

        Files.write(projectsPropertiesFilePath, fileContents, StandardCharsets.UTF_8);
    }

    /**
     * Filters and retrieves project data based on the provided project names.
     *
     * @param projectNames   A list of project names to include.
     * @param allProjectData A list of all project data maps.
     * @param checkName      The context name for error messages.
     * @param dataSource     The data source identifier for error messages.
     * @return A filtered list of project data maps.
     * @throws IllegalArgumentException If a project name is not found in the data source.
     */
    private static List<Map<String, Object>> filterProjects(
            final List<String> projectNames,
            final List<Map<String, Object>> allProjectData,
            final String checkName,
            final String dataSource
    ) {
        final List<Map<String, Object>> filteredProjects;

        if (projectNames == null || projectNames.isEmpty()) {
            filteredProjects = new ArrayList<>(allProjectData);
        }
        else {
            filteredProjects = new ArrayList<>();
            for (final String projectName : projectNames) {
                final Map<String, Object> projectInfo =
                        findProjectInfo(projectName, allProjectData);
                if (projectInfo != null) {
                    filteredProjects.add(projectInfo);
                }
                else {
                    throw new IllegalArgumentException(
                            "Project not found in " + dataSource + ": " + projectName
                                    + " (Context: "
                                    + checkName + ", Example: all-examples-in-one" + ")");
                }
            }
        }

        return filteredProjects;
    }

    /**
     * Finds the project information for a given project name in the YAML data.
     *
     * @param projectName    the name of the project
     * @param allProjectData the list of all project data
     * @return the project information map, or {@code null} if not found
     */
    private static Map<String, Object> findProjectInfo(
            final String projectName,
            final List<Map<String, Object>> allProjectData) {
        return allProjectData.stream()
                .filter(project -> projectName.equals(project.get("name")))
                .findFirst()
                .orElse(null);
    }

    /**
     * Finds the project information for a given project name in the properties data.
     *
     * @param projectName     the name of the project
     * @param allProjectLines the list of all project lines
     * @return the project information line, or {@code null} if not found
     */
    private static String findProjectInfoInProperties(
            final String projectName,
            final List<String> allProjectLines) {
        final String prefix = projectName + "|";
        return allProjectLines.stream()
                .filter(line -> line.startsWith(prefix))
                .findFirst()
                .orElse(null);
    }
}
