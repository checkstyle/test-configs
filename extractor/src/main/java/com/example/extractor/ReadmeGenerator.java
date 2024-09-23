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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * This class generates README files for test configurations.
 */
public final class ReadmeGenerator {
    /**
     * The Constant GITHUB_RAW_URL.
     */
    private static final String GITHUB_RAW_URL =
            "https://raw.githubusercontent.com/checkstyle/test-configs/main/";

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @throws UnsupportedOperationException If this constructor is invoked.
     */
    private ReadmeGenerator() {
        throw new UnsupportedOperationException(
                "This is a utility class and cannot be instantiated"
        );
    }

    /**
     * Generates a README file for an individual example.
     *
     * @param exampleFolder The folder containing the example.
     * @param moduleName The name of the module.
     * @throws IOException If an I/O error occurs.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    public static void generateIndividualReadme(
            final Path exampleFolder,
            final String moduleName)
            throws IOException {
        if (exampleFolder == null || moduleName == null) {
            throw new IllegalArgumentException(
                    "exampleFolder and moduleName must not be null");
        }

        final Path fileName = exampleFolder.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("exampleFolder must have a valid file name");
        }

        final String folderName = fileName.toString();
        final String readmeContent = String.format(Locale.US,
            "# %s Configs%n"
            + "Make comment in PR:%n"
            + "```%n"
            + "Github, generate report for %s/%s%n"
            + "```%n"
            + "OR as alternate:%n"
            + "Paste below given to PR description to use such test configs:%n"
            + "```%n"
            + "Report label: %s/%s%n"
            + "Diff Regression config: %s%n"
            + "Diff Regression projects: %s%n"
            + "```%n"
            + "Make comment in PR:%n"
            + "```%n"
            + "Github, generate report%n"
            + "```%n"
            + "OR%n"
            + "```%n"
            + "Github, generate report for configs in PR description%n"
            + "```%n",
                folderName,
                moduleName,
                folderName,
                moduleName,
                folderName,
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/config.xml",
                GITHUB_RAW_URL + moduleName + "/" + folderName + "/list-of-projects.properties"
        );
        final Path readmePath = exampleFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent, StandardCharsets.UTF_8);
    }

    /**
     * Generates a README file for all-in-one examples.
     *
     * @param allInOneFolder The folder containing all examples.
     * @param moduleName The name of the module.
     * @throws IOException If an I/O error occurs.
     * @throws IllegalArgumentException if the argument is invalid.
     */
    public static void generateAllInOneReadme(
            final Path allInOneFolder,
            final String moduleName)
            throws IOException {
        if (allInOneFolder == null || moduleName == null) {
            throw new IllegalArgumentException(
                    "allInOneFolder and moduleName must not be null");
        }

        final String readmeContent = String.format(Locale.US,
            "# All Examples in One Configs%n"
            + "Make comment in PR:%n"
            + "```%n"
            + "Github, generate report for %s/all-examples-in-one%n"
            + "```%n"
            + "OR as alternate:%n"
            + "Paste below given to PR description to use such test configs:%n"
            + "```%n"
            + "Report label: %s/all-examples-in-one%n"
            + "Diff Regression config: %s%n"
            + "Diff Regression projects: %s%n"
            + "```%n"
            + "Make comment in PR:%n"
            + "```%n"
            + "Github, generate report%n"
            + "```%n",
                moduleName,
                moduleName,
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/config.xml",
                GITHUB_RAW_URL + moduleName + "/all-examples-in-one/list-of-projects.properties"
        );

        final Path readmePath = allInOneFolder.resolve("README.md");
        Files.writeString(readmePath, readmeContent, StandardCharsets.UTF_8);
    }
}
