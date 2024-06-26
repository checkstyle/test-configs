package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConfigSerializer {

    public static void serializeConfigToFile(String exampleFilePath, String templateFilePath, String outputFilePath) throws Exception {
        String configContent = serializeConfigToString(exampleFilePath, templateFilePath);
        Files.write(Path.of(outputFilePath), configContent.getBytes());
    }

    public static void serializeAllInOneConfigToFile(String[] exampleFilePaths, String templateFilePath, String outputFilePath) throws Exception {
        String configContent = serializeAllInOneConfigToString(exampleFilePaths, templateFilePath);
        Files.write(Path.of(outputFilePath), configContent.getBytes());
    }

    public static String serializeConfigToString(String exampleFilePath, String templateFilePath) throws Exception {
        System.out.println("Loading configuration from example file...");
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        String template = new String(Files.readAllBytes(Path.of(templateFilePath)));

        // Determine if it's a TreeWalker or non-TreeWalker configuration
        Configuration targetModule = getTargetModule(xmlConfig);
        String baseIndent = isTreeWalkerConfig(xmlConfig) ? "        " : "    ";
        String moduleContent = targetModule != null ? buildModuleContent(targetModule, baseIndent) : "";

        // Call replacePlaceholders to get the final config content
        String configContent = TemplateProcessor.replacePlaceholders(template, moduleContent, isTreeWalkerConfig(xmlConfig));

        return configContent;
    }

    public static String serializeAllInOneConfigToString(String[] exampleFilePaths, String templateFilePath) throws Exception {
        System.out.println("Generating all-in-one configuration...");

        List<Configuration> combinedChildren = new ArrayList<>();
        int exampleIndex = 1;
        boolean isTreeWalker = true; // Assume true initially and determine later

        for (String exampleFilePath : exampleFilePaths) {
            System.out.println("Loading configuration from example file: " + exampleFilePath);
            TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            Configuration targetModule = getTargetModule(xmlConfig);
            if (targetModule != null) {
                isTreeWalker &= isTreeWalkerConfig(xmlConfig); // Adjust based on actual config
                // Add an id property to each child module based on the example index
                for (Configuration child : targetModule.getChildren()) {
                    Configuration modifiedChild = addIdProperty(child, "example" + exampleIndex);
                    combinedChildren.add(modifiedChild);
                }
            }
            exampleIndex++;
        }

        // Build combined module content
        String baseIndent = isTreeWalker ? "        " : "    ";
        String combinedModuleContent = buildCombinedModuleChildren(combinedChildren, baseIndent);

        // Read the template and replace the placeholder
        String template = new String(Files.readAllBytes(Path.of(templateFilePath)));
        String configContent = TemplateProcessor.replacePlaceholders(template, combinedModuleContent, isTreeWalker);

        return configContent;
    }

    private static Configuration addIdProperty(Configuration config, String idValue) {
        return new Configuration() {
            @Override
            public String getName() {
                return config.getName();
            }

            @Override
            public Map<String, String> getMessages() {
                return null;
            }

            @Override
            public String[] getAttributeNames() {
                return new String[0];
            }

            @Override
            public String getAttribute(String name) throws CheckstyleException {
                return null;
            }

            @Override
            public String[] getPropertyNames() {
                List<String> propertyNames = new ArrayList<>(Arrays.asList(config.getPropertyNames()));
                propertyNames.add("id");
                return propertyNames.toArray(new String[0]);
            }

            @Override
            public String getProperty(String name) throws CheckstyleException {
                if ("id".equals(name)) {
                    return idValue;
                }
                return config.getProperty(name);
            }

            @Override
            public Configuration[] getChildren() {
                return config.getChildren();
            }
        };
    }

    private static String buildCombinedModuleChildren(List<Configuration> children, String indent) throws CheckstyleException {
        StringBuilder builder = new StringBuilder();

        for (Configuration child : children) {
            String childProperties = buildProperties(child, indent + "    ");
            if (!childProperties.isEmpty()) {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\">\n");
                builder.append(childProperties).append("\n");
                builder.append(indent).append("</module>\n\n");
            } else {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\"/>\n\n");
            }
        }

        return builder.toString().trim();
    }

    private static String buildProperties(Configuration config, String indent) throws CheckstyleException {
        StringBuilder builder = new StringBuilder();
        boolean firstProperty = true;
        List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(config.getPropertyNames()));
        Collections.sort(sortedPropertyNames);
        for (String propertyName : sortedPropertyNames) {
            String propertyValue = config.getProperty(propertyName);
            if (!firstProperty) {
                builder.append("\n");
            }
            builder.append(indent).append("<property name=\"").append(propertyName)
                    .append("\" value=\"").append(propertyValue).append("\"/>");
            firstProperty = false;
        }
        return builder.toString();
    }

    private static Configuration getTargetModule(Configuration config) {
        Configuration treeWalkerModule = getTreeWalkerModule(config);
        return treeWalkerModule != null ? treeWalkerModule : config;
    }

    private static Configuration getTreeWalkerModule(Configuration config) {
        for (Configuration child : config.getChildren()) {
            if ("TreeWalker".equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    public static boolean isTreeWalkerConfig(Configuration config) {
        return getTreeWalkerModule(config) != null;
    }

    public static String sortProperties(String configContent) {
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

    private static String buildModuleContent(Configuration config, String indent) throws CheckstyleException {
        StringBuilder builder = new StringBuilder();
        for (Configuration child : config.getChildren()) {
            String childProperties = buildProperties(child, indent + "    ");
            if (!childProperties.isEmpty()) {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\">\n");
                builder.append(childProperties).append("\n");
                builder.append(indent).append("</module>\n\n");
            } else {
                builder.append(indent).append("<module name=\"").append(child.getName()).append("\"/>\n\n");
            }
        }
        return builder.toString().trim();
    }

    public static String extractModuleName(String exampleFilePath) throws Exception {
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        return getSpecificModuleName(xmlConfig);
    }

    private static String getSpecificModuleName(Configuration config) {
        if (config.getChildren().length == 0) {
            return config.getName();
        }
        for (Configuration child : config.getChildren()) {
            // Skip Checker and TreeWalker, look deeper
            if (!"Checker".equals(child.getName()) && !"TreeWalker".equals(child.getName())) {
                return child.getName();
            } else {
                String moduleName = getSpecificModuleName(child);
                if (!moduleName.equals(child.getName())) {
                    return moduleName;
                }
            }
        }
        return config.getName();
    }
}
