package com.example.extractor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

public class MainTest {

    @Test
    public void testParseConfigFromHeader() throws Exception {
        String filePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/Example1.java";
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
    public void testGeneratedConfigMatchesExpected() throws Exception {
        String exampleFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/Example1.java";
        String templateFilePath = "src/main/resources/config-template-treewalker.xml";
        String outputFilePath = "src/test/resources/generated-config.xml";
        String expectedFilePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/config-example1-expected.xml";

        ConfigSerializer.serializeConfig(exampleFilePath, templateFilePath, outputFilePath);

        String generatedContent = new String(Files.readAllBytes(Path.of(outputFilePath)));
        String expectedContent = new String(Files.readAllBytes(Path.of(expectedFilePath)));

        // Sort the properties in both the generated and expected content
        String sortedGeneratedContent = ConfigSerializer.sortProperties(generatedContent);
        String sortedExpectedContent = ConfigSerializer.sortProperties(expectedContent);

        assertEquals(sortedExpectedContent.trim(), sortedGeneratedContent.trim(), "The generated configuration does not match the expected output.");    }

}