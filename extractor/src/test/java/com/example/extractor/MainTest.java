package com.example.extractor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

public class MainTest {

    @Test
    public void testParseConfigFromHeader() throws Exception {
        String filePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example1.java";
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(filePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        assertNotNull(xmlConfig, "Config should not be null");
        assertTrue(containsModule(xmlConfig, "Checker"), "Config should contain module name 'Checker'");
        assertTrue(containsModule(xmlConfig, "TreeWalker"), "Config should contain module name 'TreeWalker'");
        assertTrue(containsModule(xmlConfig, "AbbreviationAsWordInName"), "Config should contain module name 'AbbreviationAsWordInName'");
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

    @Test
    public void testGeneratedConfigMatchesExpectedExample1() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example1.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath = "src/test/resources/generated-config-example1.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example1-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample2() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example2.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath = "src/test/resources/generated-config-example2.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example2-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample3() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example3.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath = "src/test/resources/generated-config-example3.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example3-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }


    @Test
    public void testGeneratedConfigMatchesExpectedExample4() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example4.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath =  "src/test/resources/generated-config-example4.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example4-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample5() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example5.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath =  "src/test/resources/generated-config-example5.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example5-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample6() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example6.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath =  "src/test/resources/generated-config-example6.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example6-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedExample7() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example7.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath =  "src/test/resources/generated-config-example7.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-example7-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }

    @Test
    public void testGeneratedConfigMatchesExpectedAllInOne() throws Exception {
        // List of example files
        String[] exampleFiles = {
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example1.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example2.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example3.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example4.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example5.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example6.java",
                "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/examples/Example7.java"
        };

        // Template and output paths
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath = "src/test/resources/generated-config-all-in-one.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/configs/config-examples-all-in-one-expected.xml";

        // Clear the output file
        Files.deleteIfExists(Path.of(outputFilePath));

        // Generate the all-in-one configuration
        ConfigSerializer.serializeAllInOneConfig(exampleFiles, templateFilePath, outputFilePath);

        // Read the generated content
        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        // Read the expected content
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");
    }
}