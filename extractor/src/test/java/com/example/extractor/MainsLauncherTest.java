package com.example.extractor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * Tests the main method of CheckstyleExampleExtractor.
     * This test ensures that the main method runs without throwing any exceptions.

     */
    @Test
    void testMain() {
        // Pass the base path as an argument and assert that it doesn't throw an exception.
        assertDoesNotThrow(() -> CheckstyleExampleExtractor.main(new String[]{CHECKSTYLE_REPO_PATH}));
    }

    /**
     * Tests the main method with a specific input file and verifies that the output file is generated correctly.
     *
     * @param tempDir The temporary directory where the output file will be created.
     * @throws Exception if any error occurs during the test
     */
    @Test
    void testMainWithInputFile(@TempDir final Path tempDir) throws Exception {
        final String inputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.methodparampad.InputFile/InputMethodParamPadWhitespaceAround.java";

        final String expectedInputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.methodparampad.Config/expected-config.xml";

        final String expectedContent = loadToString(expectedInputFilePath);

        final Path outputFile = tempDir.resolve("output-config.xml");

        assertDoesNotThrow(() -> CheckstyleExampleExtractor.main(new String[]{
                CHECKSTYLE_REPO_PATH,
                "--input-file",
                inputFilePath,
                outputFile.toString()
        }));

        assertTrue(Files.exists(outputFile), "Output file should be created");
        final String generatedContent = Files.readString(outputFile);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests the getTemplateFilePathForInputFile method to verify that it returns the correct template file path.
     *
     */
    @Test
    void testGetTemplateFilePathForInputFile() {
        // The input file path is declared as final because it is not modified after initialization.
        final String inputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.methodparampad.InputFile/InputMethodParamPadWhitespaceAround.java";

        // Retrieve the template file path, declared as final since it is not reassigned.
        final String templatePath = CheckstyleExampleExtractor.getTemplateFilePathForInputFile(inputFilePath);

        // Assert that the template path is not null and ends with the expected file name.
        assertThat(templatePath).isNotNull();
        assertThat(templatePath).endsWith("config-template-treewalker.xml");
    }

    private String loadToString(final String filePath) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}