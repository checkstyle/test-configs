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
     * Path to the default properties file containing a list of projects.
     */
    public static final String ALL_PROJECTS_PROPERTIES_PATH =
            "src/main/resources/all-projects.properties";

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
        final List<Map<String, Object>> allProjectData = parseAllProjectsYaml();
        final List<String> allProjectLines = loadAllProjectsProperties();

        for (final Map.Entry<String, Object> entry : yamlData.entrySet()) {
            final String checkName = entry.getKey();
            final Map<String, Object> checkData = (Map<String, Object>) entry.getValue();

            for (final Map.Entry<String, Object> exampleEntry : checkData.entrySet()) {
                final String exampleName = exampleEntry.getKey();
                final Map<String, Object> exampleData =
                        (Map<String, Object>) exampleEntry.getValue();
                final List<String> projectNames =
                        (List<String>) exampleData.get("projects");

                final Path examplePath = Paths.get(testConfigPath, checkName, exampleName);
                createProjectsYmlFileForExample(examplePath, projectNames,
                        allProjectData, checkName);
                createProjectsPropertiesFileForExample(examplePath, projectNames,
                        allProjectLines, checkName);

                if ("all-examples-in-one".equals(exampleName)) {
                    createAllInOneProjectsFile(Paths.get(testConfigPath, checkName),
                            projectNames, allProjectData, allProjectLines, checkName);
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
        final Path allProjectsYamlPath = Paths.get(ALL_PROJECTS_YAML_PATH);
        try (InputStream inputStream = Files.newInputStream(allProjectsYamlPath)) {
            final Map<String, Object> yamlData = new Yaml().load(inputStream);
            final Object projectsObj = yamlData.get("projects");
            if (projectsObj instanceof List) {
                return (List<Map<String, Object>>) projectsObj;
            }
            else {
                throw new IllegalStateException(
                        "Expected 'projects' to be a list in " + ALL_PROJECTS_YAML_PATH);
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
        final Path allProjectsPropertiesPath = Paths.get(ALL_PROJECTS_PROPERTIES_PATH);
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
     * @throws IOException if an I/O error occurs
     * @throws IllegalArgumentException if a project name is not found in all-projects.yml
     */
    static void createProjectsYmlFileForExample(final Path examplePath,
                                                final List<String> projectNames,
                                                final List<Map<String, Object>> allProjectData,
                                                final String checkName) throws IOException {
        Files.createDirectories(examplePath);
        final Path projectsFilePath =
                examplePath.resolve("list-of-projects.yml");

        final List<Map<String, Object>> projects =
                filterProjects(projectNames, allProjectData, checkName, "all-projects.yml");

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
     * @throws IOException if an I/O error occurs
     */
    private static void createAllInOneProjectsFile(final Path modulePath,
                                                   final List<String> projectNames,
                                                   final List<Map<String, Object>> allProjectData,
                                                   final List<String> allProjectLines,
                                                   final String checkName) throws IOException {
        final Path allInOnePath = prepareAllInOnePath(modulePath);

        // Create YAML and Properties files
        generateProjectsYaml(allInOnePath, projectNames, allProjectData, checkName);
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
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static void generateProjectsYaml(final Path allInOnePath,
                                             final List<String> projectNames,
                                             final List<Map<String, Object>> allProjectData,
                                             final String checkName) throws IOException {
        final Path projectsYamlFilePath =
                allInOnePath.resolve("list-of-projects.yml");
        final List<Map<String, Object>> projectsYaml =
                filterProjects(projectNames, allProjectData, checkName, "all-projects.yml");

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
