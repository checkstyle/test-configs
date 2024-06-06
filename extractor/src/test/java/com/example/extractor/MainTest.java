package com.example.extractor;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
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
}
