package com.example.extractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ReadmeGenerator {

    private static final String GITHUB_BASE_URL = "https://github.com/checkstyle/test-configs/blob/main/";

    public static void generateIndividualReadme(Path exampleFolder, String moduleName) throws IOException {
        // ... (keep the existing method as is)
    }

    public static void generateRootReadme(Path rootFolder, List<Path> exampleFolders, String moduleName) throws IOException {
        StringBuilder contentBuilder = new StringBuilder("# ").append(moduleName).append("\n\n");
        contentBuilder.append("## Description\n\n");
        contentBuilder.append("The `").append(moduleName).append("` check\n\n");
        contentBuilder.append("## Examples\n\n");

        for (Path exampleFolder : exampleFolders) {
            String folderName = exampleFolder.getFileName().toString();
            if (folderName.equals("all-examples-in-one")) {
                continue; // Skip the all-examples-in-one folder in the main list
            }
            contentBuilder.append(String.format("%s. %s\n", folderName.replace("Example", ""), folderName));
            contentBuilder.append(String.format("   - Configuration: `%s`\n", GITHUB_BASE_URL + moduleName + "/" + folderName + "/config.xml"));
            contentBuilder.append(String.format("   - Projects: `%s`\n\n", GITHUB_BASE_URL + moduleName + "/" + folderName + "/list-of-projects.properties"));
        }

        contentBuilder.append("## All Examples in One\n\n");
        contentBuilder.append(String.format("- Configuration file: `%s`\n", GITHUB_BASE_URL + moduleName + "/all-examples-in-one/config-all-in-one.xml"));
        contentBuilder.append(String.format("- List of projects: `%s`\n\n", GITHUB_BASE_URL + moduleName + "/all-examples-in-one/list-of-projects.properties"));
        
        contentBuilder.append("\nChoose the example that best fits your project's needs, including all-in-one configuration");

        Path readmePath = rootFolder.resolve("README.md");
        Files.writeString(readmePath, contentBuilder.toString());
    }
}