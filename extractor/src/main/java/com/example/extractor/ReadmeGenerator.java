package com.example.extractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadmeGenerator {
    private static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/checkstyle/test-configs/main/";

    public static void generateIndividualReadme(Path exampleFolder, String moduleName) throws IOException {
        if (exampleFolder == null || moduleName == null) {
            throw new IllegalArgumentException("exampleFolder and moduleName must not be null");
        }

        Path fileName = exampleFolder.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("exampleFolder must have a valid file name");
        }

        String folderName = fileName.toString();
        String readmeContent = String.format(
                "# %s Configs%n" +
                        "Make comment in PR:%n" +
                        "```%n" +
                        "Github, generate report for %s/%s%n" +
                        "```%n" +
                        "OR as alternate:%n" +
                        "Paste below given to PR description to use such test configs:%n" +
                        "```%n" +
                        "Diff Regression config: %s%n" +
                        "Diff Regression projects: %s%n" +
                        "```%n" +
                        "Make comment in PR:%n" +
                        "```%n" +
                        "Github, generate report%n" +
                        "```%n",
                folderName,
                moduleName,
                folderName,
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/config.xml",
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/list-of-projects.properties"
        );

        Path readmePath = exampleFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent, StandardCharsets.UTF_8);
    }

    public static void generateAllInOneReadme(Path allInOneFolder, String moduleName) throws IOException {
        if (allInOneFolder == null || moduleName == null) {
            throw new IllegalArgumentException("allInOneFolder and moduleName must not be null");
        }

        String readmeContent = String.format(
                "# All Examples in One Configs%n" +
                        "Make comment in PR:%n" +
                        "```%n" +
                        "Github, generate report for %s/all-examples-in-one%n" +
                        "```%n" +
                        "OR as alternate:%n" +
                        "Paste below given to PR description to use such test configs:%n" +
                        "```%n" +
                        "Diff Regression config: %s%n" +
                        "Diff Regression projects: %s%n" +
                        "```%n" +
                        "Make comment in PR:%n" +
                        "```%n" +
                        "Github, generate report%n" +
                        "```%n",
                moduleName,
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/config.xml",
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/list-of-projects.properties"
        );

        Path readmePath = allInOneFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent, StandardCharsets.UTF_8);
    }
}