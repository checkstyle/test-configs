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
 * Unit tests for the Main class.
 */
class MainTest {

    /**
     * The constant TREEWALKER_TEMPLATE_FILE.
     */
    private static final String TREEWALKER_TEMPLATE_FILE = "src/main/resources/config-template-treewalker.xml";

    /**
     * The constant NON_TREEWALKER_TEMPLATE_FILE.
     */
    private static final String NON_TREEWALKER_TEMPLATE_FILE = "src/main/resources/config-template-non-treewalker.xml";


    /**
     * Default constructor.
     */
    public MainTest() {
        // No-argument constructor
    }

    /**
     * Tests parsing the configuration from the header.
     * @throws Exception if an error occurs during parsing.
     */
    @Test
    void testParseConfigFromHeader() throws Exception {
        final String filePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java";
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(filePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        assertNotNull(xmlConfig, "Config should not be null");
        assertThat(containsModule(xmlConfig, "Checker")).isTrue();
        assertThat(containsModule(xmlConfig, "TreeWalker")).isTrue();
        assertThat(containsModule(xmlConfig, "AbbreviationAsWordInName")).isTrue();
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example1.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample1() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example1.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example2.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample2() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example2.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example2.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example3.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample3() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example3.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example3.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example4.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample4() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example4.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example4.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example5.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample5() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example5.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example5.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example6.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample6() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example6.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example6.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for Example7.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedExample7() throws Exception {
        final String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example7.java";
        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-example7.xml";

        final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests if the generated configuration matches the expected configuration for all examples.
     * @throws Exception if an error occurs during processing.
     */
    @Test
    void testGeneratedConfigMatchesExpectedAllInOne() throws Exception {
        final String[] exampleFiles = {
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example2.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example3.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example4.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example5.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example6.java",
            "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example7.java",
        };

        final String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/expected-config-all-in-one.xml";

        final String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(exampleFiles, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests that the generated configuration matches the expected XML output for a non-XML input file which is Treewalker.
     *
     * @throws Exception if an error occurs during configuration serialization or file reading.
     */
    @Test
    void testGeneratedConfigMatchesExpectedForNonXmlInput() throws Exception {
        final String inputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.methodparampad.InputFile/InputMethodParamPadWhitespaceAround.java";
        final String expectedInputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.methodparampad.Config/expected-config.xml";

        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(inputFilePath, TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedInputFilePath);
        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    /**
     * Tests that the generated configuration matches the expected XML output for a non-XML input file which is Non-Treewalker.
     *
     * @throws Exception if an error occurs during configuration serialization or file reading.
     */
    @Test
    void testGeneratedConfigMatchesExpectedForNonXmlInputWithNonTreewalker() throws Exception {
        final String inputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.filetabcharacter.InputFile/InputFileTabCharacterSimple.java";
        final String expectedInputFilePath = "src/test/resources/com.puppycrawl.tools.checkstyle.checks.whitespace.filetabcharacter.Config/expected-config.xml";

        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(inputFilePath, NON_TREEWALKER_TEMPLATE_FILE);
        final String expectedContent = loadToString(expectedInputFilePath);
        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    private boolean containsModule(final Configuration config, final String moduleName) {
        if (config.getName().equals(moduleName)) {
            return true;
        }

        for (final Configuration child : config.getChildren()) {
            if (containsModule(child, moduleName)) {
                return true;
            }
        }

        return false;
    }

    private String loadToString(final String filePath) throws IOException {
        final byte[] encoded = Files.readAllBytes(Paths.get(filePath));
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
