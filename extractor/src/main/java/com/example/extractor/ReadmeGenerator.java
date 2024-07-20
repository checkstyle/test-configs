package com.example.extractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadmeGenerator {
    private static final String GITHUB_RAW_URL = "https://raw.githubusercontent.com/checkstyle/test-configs/main/";

    public static void generateIndividualReadme(Path exampleFolder, String moduleName) throws IOException {
        String folderName = exampleFolder.getFileName().toString();
        String readmeContent = String.format("""
            # %s Configs
            Make comment in PR:
            ```
            Github, generate report for %s/%s
            ```
            OR as alternate:
            Paste below given to PR description to use such test configs:
            ```
            Diff Regression config: %s
            Diff Regression projects: %s
            ```
            Make comment in PR:
            ```
            Github, generate report
            ```
            """,
                folderName,
                moduleName,
                folderName,
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/config.xml",
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/list-of-projects.properties"
        );

        Path readmePath = exampleFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent);
    }

    public static void generateAllInOneReadme(Path allInOneFolder, String moduleName) throws IOException {
        String readmeContent = String.format("""
            # All Examples in One Configs
            Make comment in PR:
            ```
            Github, generate report for %s/all-examples-in-one
            ```
            OR as alternate:
            Paste below given to PR description to use such test configs:
            ```
            Diff Regression config: %s
            Diff Regression projects: %s
            ```
            Make comment in PR:
            ```
            Github, generate report
            ```
            """,
                moduleName,
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/config.xml",
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/list-of-projects.properties"
        );

        Path readmePath = allInOneFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent);
    }
}