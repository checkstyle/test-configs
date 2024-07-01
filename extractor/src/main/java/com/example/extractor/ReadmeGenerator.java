package com.example.extractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadmeGenerator {
    private static final String GITHUB_URL = "https://github.com/checkstyle/test-configs/blob/main/";

    public static void generateIndividualReadme(Path exampleFolder, String moduleName) throws IOException {
        String folderName = exampleFolder.getFileName().toString();
        String readmeContent = String.format("""
            # %s Configs
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
                GITHUB_URL + moduleName + "/" + folderName + "/config.xml",
                GITHUB_URL + moduleName + "/" + folderName + "/list-of-projects.properties",
                moduleName,
                folderName
        );

        Path readmePath = exampleFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent);
    }

    public static void generateAllInOneReadme(Path allInOneFolder, String moduleName) throws IOException {
        String readmeContent = String.format("""
            # All Examples in One Configs
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
                GITHUB_URL + moduleName + "/all-examples-in-one/config-all-in-one.xml",
                GITHUB_URL + moduleName + "/all-examples-in-one/list-of-projects.properties",
                moduleName
        );

        Path readmePath = allInOneFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent);
    }
}