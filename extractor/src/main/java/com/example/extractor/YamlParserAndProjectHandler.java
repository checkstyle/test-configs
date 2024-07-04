package com.example.extractor;

import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class YamlParserAndProjectHandler {

    private static final String YAML_FILE_PATH = "src/main/resources/projects-for-example.yml";
    private static final String ALL_PROJECTS_FILE_PATH = "src/main/resources/all-projects.properties";
    private static final String DEFAULT_PROJECTS_FILE_PATH = "src/main/resources/list-of-projects.properties";
    private static final String DEFAULT_COMMENTS =
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
                createProjectsFileForExample(examplePath, projectNames, allProjectLines);
            }
        }
    }

    private static Map<String, Object> parseYamlFile() throws IOException {
        try (InputStream inputStream = new FileInputStream(YAML_FILE_PATH)) {
            Yaml yaml = new Yaml();
            return yaml.load(inputStream);
        }
    }

    private static void createProjectsFileForExample(Path examplePath, List<String> projectNames, List<String> allProjectLines) throws IOException {
        Files.createDirectories(examplePath);
        Path projectsFilePath = examplePath.resolve("list-of-projects.properties");

        List<String> fileContents = new ArrayList<>();
        fileContents.add(DEFAULT_COMMENTS);

        if (projectNames != null && !projectNames.isEmpty()) {
            for (String projectName : projectNames) {
                String projectInfo = findProjectInfo(projectName, allProjectLines);
                if (projectInfo != null) {
                    fileContents.add(projectInfo);
                }
            }
        } else {
            fileContents.addAll(Files.readAllLines(Paths.get(DEFAULT_PROJECTS_FILE_PATH)));
        }

        try {
            Files.write(projectsFilePath, fileContents);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing file: " + e.getMessage(), e);
        }
    }

    private static String findProjectInfo(String projectName, List<String> allProjectLines) {
        for (String line : allProjectLines) {
            if (line.startsWith(projectName + "|")) {
                return line;
            }
        }
        return null;
    }
}