package com.example.extractor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
     *
     * @throws Exception if any error occurs during the test
     */
    @Test
    void testMain() throws Exception {
        // Pass the base path as argument and assert that it doesn't throw an exception
        assertDoesNotThrow(() -> CheckstyleExampleExtractor.main(new String[]{CHECKSTYLE_REPO_PATH}));
    }
}