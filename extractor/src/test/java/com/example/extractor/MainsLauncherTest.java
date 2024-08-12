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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test class for MainsLauncher.
 * This class contains tests for the main method of CheckstyleExampleExtractor.
 */
class MainsLauncherTest {

    /**
     * The base path to the Checkstyle repository.
     */
    private static final String CHECKSTYLE_REPO_PATH = "../.ci-temp/checkstyle";

    /**
     * Base path for Checkstyle checks package used in test resource files.
     */
    private static final String CHECKSTYLE_CHECKS_BASE_PATH =
            "com/puppycrawl/tools/checkstyle/checks";

    /**
     * Tests the main method of CheckstyleExampleExtractor.
     * This test ensures that the main method runs without throwing any exceptions.
     */
    @Test
    void testMain() {
        // Pass the base path as an argument and assert that it doesn't throw an exception.
        assertDoesNotThrow(() -> {
            CheckstyleExampleExtractor.main(new String[]{CHECKSTYLE_REPO_PATH});
        });
    }

    /**
     * Tests the main method with a specific input file.
     *
     * @param tempDir The temporary directory where the output file will be created.
     * @throws Exception if any error occurs during the test
     */
    @Test
    void testMainWithInputFile(@TempDir final Path tempDir) throws Exception {
        final String inputFilePath = "src/test/resources/"
                + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/methodparampad/InputFile/InputMethodParamPadWhitespaceAround.java";

        final String expectedInputFilePath = "src/test/resources/"
                + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/methodparampad/Config/expected-config.xml";

        final String expectedContent = loadToString(expectedInputFilePath);

        final Path outputFile = tempDir.resolve("output-config.xml");

        assertDoesNotThrow(() -> {
            CheckstyleExampleExtractor.main(new String[]{
                CHECKSTYLE_REPO_PATH,
                "--input-file",
                inputFilePath,
                outputFile.toString(),
            });
        });

        assertTrue(Files.exists(outputFile), "Output file should be created");
        final String generatedContent = Files.readString(outputFile);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests the getTemplateFilePathForInputFile method.
     *
     */
    @Test
    void testGetTemplateFilePathForInputFile() throws Exception {
        final String inputFilePath = "src/test/resources/"
                + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/methodparampad/InputFile/"
                + "InputMethodParamPadWhitespaceAround.java";
        // Retrieve the template file path, declared as final since it is not reassigned.
        final String templatePath =
                CheckstyleExampleExtractor.getTemplateFilePathForInputFile(inputFilePath);

        // Assert that the template path is not null and ends with the expected file name.
        assertThat(templatePath).isNotNull();
        assertThat(templatePath).endsWith("config-template-treewalker.xml");
    }

    private String loadToString(final String filePath) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
