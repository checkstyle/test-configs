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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * Main test class for configuration generation and validation.
 */
class MainTest {

    /**
     * The constant for Treewalker template file.
     */
    private static final String TREEWALKER_TEMPLATE_FILE =
            "src/main/resources/config-template-treewalker.xml";

    /**
     * The constant for Treewalker template file for input java file conditions.
     */
    private static final String TW_TEMPLATE_FILE_INPUT_JAVA =
            "config-template-treewalker.xml";

    /**
     * The constant for non-Treewalker template file for input java file conditions.
     */
    private static final String NTW_TEMPLATE_FILE_INPUT_JAVA =
            "config-template-non-treewalker.xml";

    /**
     * The constant for base path of test resources.
     */
    private static final String BASE_PATH = "src/test/resources/";

    /**
     * Base path for Checkstyle checks package used in test resource files.
     */
    private static final String CHECKSTYLE_CHECKS_BASE_PATH =
            "com/puppycrawl/tools/checkstyle/checks";

    /**
     * The constant for AbbreviationAsWordInName check path.
     */
    private static final String ABBREVIATION_PATH =
            CHECKSTYLE_CHECKS_BASE_PATH + "/naming/AbbreviationAsWordInName";

    /**
     * The constant for examples path.
     */
    private static final String EXAMPLES_PATH = ABBREVIATION_PATH + "/Examples";

    /**
     * The constant for configs path.
     */
    private static final String CONFIGS_PATH = ABBREVIATION_PATH + "/Configs";

    /**
     * Tests parsing the configuration from the header.
     *
     * @throws Exception if an error occurs during parsing.
     */
    @Test
    void testParseConfigFromHeader() throws Exception {
        final String filePath = BASE_PATH + EXAMPLES_PATH + "/Example1.java";
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parseWithXmlHeader(filePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        assertNotNull(xmlConfig, "Config should not be null");
        assertThat(containsModule(xmlConfig, "Checker")).isTrue();
        assertThat(containsModule(xmlConfig, "TreeWalker")).isTrue();
        assertThat(containsModule(xmlConfig, "AbbreviationAsWordInName")).isTrue();
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example1.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample1() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example1.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example1.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example2.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample2() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example2.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example2.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example3.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample3() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example3.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example3.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example4.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample4() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example4.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example4.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example5.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample5() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example5.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example5.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example6.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample6() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example6.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example6.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example7.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample7() throws Exception {
        final String exampleFilePath = BASE_PATH + EXAMPLES_PATH + "/Example7.java";
        final String expectedFilePath = BASE_PATH + CONFIGS_PATH + "/expected-config-example7.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(
                exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for all examples.
     *
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedAllInOne() throws Exception {
        final String[] exampleFiles = {
            BASE_PATH + EXAMPLES_PATH + "/Example1.java",
            BASE_PATH + EXAMPLES_PATH + "/Example2.java",
            BASE_PATH + EXAMPLES_PATH + "/Example3.java",
            BASE_PATH + EXAMPLES_PATH + "/Example4.java",
            BASE_PATH + EXAMPLES_PATH + "/Example5.java",
            BASE_PATH + EXAMPLES_PATH + "/Example6.java",
            BASE_PATH + EXAMPLES_PATH + "/Example7.java",
        };

        final String expectedFilePath =
                BASE_PATH + CONFIGS_PATH + "/expected-config-all-in-one.xml";

        final String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                exampleFiles, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests that the generated configuration matches the expected XML output
     * for a non-XML input file which is Treewalker.
     *
     * @throws Exception if an error occurs during configuration serialization or file reading.
     */
    @Test
    void testGeneratedConfigForNonXmlInputWithTreewalker() throws Exception {
        final String inputFilePath = BASE_PATH + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/methodparampad/InputFile/InputMethodParamPadWhitespaceAround.java";
        final String expectedInputFilePath = BASE_PATH + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/methodparampad/Config/expected-config.xml";

        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(
                inputFilePath, TW_TEMPLATE_FILE_INPUT_JAVA);
        final String expectedContent = loadToString(expectedInputFilePath);
        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests that the generated configuration matches the expected XML output
     * for a non-XML input file which is Non-Treewalker.
     *
     * @throws Exception if an error occurs during configuration serialization or file reading.
     */
    @Test
    void testGeneratedConfigForNonXmlInputWithNonTreewalker() throws Exception {
        final String inputFilePath = BASE_PATH + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/filetabcharacter/InputFile/InputFileTabCharacterSimple.java";
        final String expectedInputFilePath = BASE_PATH + CHECKSTYLE_CHECKS_BASE_PATH
                + "/whitespace/filetabcharacter/Config/expected-config.xml";

        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(
                inputFilePath, NTW_TEMPLATE_FILE_INPUT_JAVA);
        final String expectedContent = loadToString(expectedInputFilePath);
        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Checks if the given configuration contains a module with the specified name.
     *
     * @param config     The configuration to check.
     * @param moduleName The name of the module to look for.
     * @return true if the module is found, false otherwise.
     */
    private boolean containsModule(final Configuration config, final String moduleName) {
        boolean found = false;

        if (config.getName().equals(moduleName)) {
            found = true;
        }
        else {
            for (final Configuration child : config.getChildren()) {
                if (containsModule(child, moduleName)) {
                    found = true;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Loads the content of a file into a string.
     *
     * @param filePath The path of the file to load.
     * @return The content of the file as a string.
     * @throws IOException If an I/O error occurs reading from the file.
     */
    private String loadToString(final String filePath) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
