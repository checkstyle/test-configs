package com.example.extractor;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTest {

    @Test
    public void testParseConfigFromHeader() throws Exception {
        String filePath = "src/test/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname/Example1.java";
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        String config = extractConfigFromHeader(content);

        System.out.println("Extracted Config: " + config);
        assertNotNull(config, "Config should not be null");
        assertTrue(config.contains("<module name=\"Checker\">"), "Config should contain module name 'Checker'");
        assertTrue(config.contains("<module name=\"TreeWalker\">"), "Config should contain module name 'TreeWalker'");
        assertTrue(config.contains("<module name=\"AbbreviationAsWordInName\"/>"), "Config should contain module name 'AbbreviationAsWordInName'");
    }

    private String extractConfigFromHeader(String content) {
        Pattern pattern = Pattern.compile("/\\*\\s*xml\\s*(.*?)\\s*\\*/", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}