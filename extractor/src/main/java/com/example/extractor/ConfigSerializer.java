package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigSerializer {

    public static void serializeConfig(String exampleFilePath, String templateFilePath, String outputFilePath) throws Exception {
        System.out.println("Loading configuration from example file...");
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        String template = new String(Files.readAllBytes(Path.of(templateFilePath)));

        // Accumulate values
        Configuration treeWalkerModule = getTreeWalkerModule(xmlConfig);
        String moduleContent = treeWalkerModule != null ? buildModuleContent(treeWalkerModule, "        ") : "";

        // Call replacePlaceholders to get the final config content
        String configContent = TemplateProcessor.replacePlaceholders(template, moduleContent);

        // Write the final config content to the output file
        Files.write(Path.of(outputFilePath), configContent.getBytes());
    }

    private static String buildProperties(Configuration config, String indent) {
        StringBuilder builder = new StringBuilder();
        boolean firstProperty = true;
        for (String propertyName : config.getPropertyNames()) {
            try {
                String propertyValue = config.getProperty(propertyName);
                if (!firstProperty) {
                    builder.append("\n");
                }
                builder.append(indent).append("<property name=\"").append(propertyName)
                        .append("\" value=\"").append(propertyValue).append("\"/>");
                firstProperty = false;
            } catch (CheckstyleException e) {
                System.err.println("Error retrieving property: " + e.getMessage());
            }
        }
        return builder.toString();
    }

    private static String buildModuleContent(Configuration config, String indent) {
        StringBuilder builder = new StringBuilder();
        for (Configuration child : config.getChildren()) {
            String childProperties = buildProperties(child, indent + "    ");
            if (!childProperties.isEmpty()) {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\">\n");
                builder.append(childProperties).append("\n");
                builder.append(indent).append("</module>\n");
            } else {
                // Generate self-closing tag if there are no properties
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\"/>\n");
            }
        }
        return builder.toString().trim();
    }

    private static Configuration getTreeWalkerModule(Configuration config) {
        for (Configuration child : config.getChildren()) {
            if ("TreeWalker".equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    public static String sortProperties(String configContent) {
        // Split the config content into lines
        String[] lines = configContent.split("\n");
        List<String> propertyLines = new ArrayList<>();
        StringBuilder sortedContent = new StringBuilder();

        for (String line : lines) {
            if (line.trim().startsWith("<property")) {
                propertyLines.add(line);
            } else {
                if (!propertyLines.isEmpty()) {
                    Collections.sort(propertyLines);
                    for (String propertyLine : propertyLines) {
                        sortedContent.append(propertyLine).append("\n");
                    }
                    propertyLines.clear();
                }
                sortedContent.append(line).append("\n");
            }
        }
        if (!propertyLines.isEmpty()) {
            Collections.sort(propertyLines);
            for (String propertyLine : propertyLines) {
                sortedContent.append(propertyLine).append("\n");
            }
        }
        return sortedContent.toString().trim();
    }
}
