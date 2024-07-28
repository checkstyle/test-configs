package com.example.extractor;

import org.yaml.snakeyaml.Yaml;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class YamlParserAndProjectHandler {

    public static final String YAML_FILE_PATH = "src/main/resources/projects-for-example.yml";
    public static final String ALL_PROJECTS_FILE_PATH = "src/main/resources/all-projects.properties";
    public static final String DEFAULT_PROJECTS_FILE_PATH = "src/main/resources/list-of-projects.properties";
    public static final String DEFAULT_COMMENTS =
            "# List of GIT repositories to clone / pull for checking with Checkstyle\n" +
                    "# File format: REPO_NAME|[local|git]|URL|[COMMIT_ID]|[EXCLUDE FOLDERS]\n" +
                    "# Please note that bash comments works in this file\n\n";

    public static void processProjectsForExamples(String testConfigPath) throws IOException {
        Map<String, Object> yamlData = parseYamlFile();
        List<String> allProjectLines = Files.readAllLines(Paths.get(ALL_PROJECTS_FILE_PATH));

        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            String checkName = entry.getKey();
            Map<String, Object> checkData = (Map<String, Object>) entry.getValue();

            for (Map.Entry<String, Object> exampleEntry : checkData.entrySet()) {
                String exampleName = exampleEntry.getKey();
                Map<String, Object> exampleData = (Map<String, Object>) exampleEntry.getValue();
                List<String> projectNames = (List<String>) exampleData.get("projects");

                Path examplePath = Paths.get(testConfigPath, checkName, exampleName);
                createProjectsFileForExample(examplePath, projectNames, allProjectLines, checkName);

                // Handle all-examples-in-one case
                if ("all-examples-in-one".equals(exampleName)) {
                    createAllInOneProjectsFile(Paths.get(testConfigPath, checkName), projectNames, allProjectLines, checkName);
                }
            }
        }
    }

    public static Map<String, Object> parseYamlFile() throws IOException {
        try (InputStream inputStream = new FileInputStream(YAML_FILE_PATH)) {
            Yaml yaml = new Yaml();
            return yaml.load(inputStream);
        }
    }

    public static void createProjectsFileForExample(Path examplePath, List<String> projectNames, List<String> allProjectLines, String checkName) throws IOException {
        Files.createDirectories(examplePath);
        Path projectsFilePath = examplePath.resolve("list-of-projects.properties");

        List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            for (String projectName : projectNames) {
                String projectInfo = findProjectInfo(projectName, allProjectLines);
                if (projectInfo != null) {
                    fileContents.add(projectInfo);
                } else {
                    throw new IllegalArgumentException("Project not found in all-projects.properties: " + projectName + " (Check: " + checkName + ")");
                }
            }
        } else {
            fileContents.addAll(Files.readAllLines(Paths.get(DEFAULT_PROJECTS_FILE_PATH)));
        }

        Files.write(projectsFilePath, fileContents);
    }

    public static void createAllInOneProjectsFile(Path modulePath, List<String> projectNames, List<String> allProjectLines, String checkName) throws IOException {
        Path allInOnePath = modulePath.resolve("all-examples-in-one");
        Files.createDirectories(allInOnePath);
        Path projectsFilePath = allInOnePath.resolve("list-of-projects.properties");

        List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            for (String projectName : projectNames) {
                String projectInfo = findProjectInfo(projectName, allProjectLines);
                if (projectInfo != null) {
                    fileContents.add(projectInfo);
                } else {
                    throw new IllegalArgumentException("Project not found in all-projects.properties: " + projectName + " (Check: " + checkName + ", Example: all-examples-in-one)");
                }
            }
        } else {
            fileContents.addAll(allProjectLines);
        }

        Files.write(projectsFilePath, fileContents);
    }

    public static String findProjectInfo(String projectName, List<String> allProjectLines) {
        for (String line : allProjectLines) {
            if (line.startsWith(projectName + "|")) {
                return line;
            }
        }
        return null;
    }
}