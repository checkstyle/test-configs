package com.example.extractor;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

public class MainTest {

    @Test
    public void testParseConfigFromHeader() throws Exception {
        String filePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java";
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(filePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        assertNotNull(xmlConfig, "Config should not be null");
        assertTrue(containsModule(xmlConfig, "Checker"), "Config should contain module name 'Checker'");
        assertTrue(containsModule(xmlConfig, "TreeWalker"), "Config should contain module name 'TreeWalker'");
        assertTrue(containsModule(xmlConfig, "AbbreviationAsWordInName"), "Config should contain module name 'AbbreviationAsWordInName'");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample1() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example1-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample2() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example2.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example2-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample3() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example3.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example3-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample4() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example4.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example4-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample5() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example5.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example5-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample6() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example6.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example6-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample7() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example7.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-example7-expected.xml";

        String generatedContent = ConfigSerializer.serializeConfigToString(exampleFilePath, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    @Test
    public void testGeneratedConfigMatchesExpectedAllInOne() throws Exception {
        String[] exampleFiles = {
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example1.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example2.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example3.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example4.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example5.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example6.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Examples/Example7.java"
        };

        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/AbbreviationAsWordInName/Configs/config-examples-all-in-one-expected.xml";

        String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(exampleFiles, templateFilePath);
        String expectedContent = loadToString(expectedFilePath);

        assertThat(generatedContent).isEqualTo(expectedContent);
    }

    private boolean containsModule(Configuration config, String moduleName) {
        if (config.getName().equals(moduleName)) {
            return true;
        }

        for (Configuration child : config.getChildren()) {
            if (containsModule(child, moduleName)) {
                return true;
            }
        }

        return false;
    }

    private String loadToString(String filePath) throws Exception {
        return new String(Files.readAllBytes(Path.of(filePath)));
    }
}