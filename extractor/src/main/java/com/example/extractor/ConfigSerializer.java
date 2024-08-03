package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * Utility class for serializing Checkstyle configurations.
 * This class provides methods to serialize configurations to files or strings,
 * and to extract module names from configuration files.
 */
public final class ConfigSerializer {

    /** Constant for the TreeWalker module name. */
    private static final String TREE_WALKER = "TreeWalker";

    /** Constant for the Checker module name. */
    private static final String CHECKER = "Checker";

    /** XML tag for module elements. */
    private static final String MODULE_TAG = "<module name=\"";

    /** Indentation for TreeWalker modules. */
    private static final String TREE_WALKER_INDENT = "        ";

    /** Indentation for non-TreeWalker modules. */
    private static final String NON_TREE_WALKER_INDENT = "    ";

    private ConfigSerializer() {
        // Private constructor to prevent instantiation
    }

    /**
     * Serializes a configuration to a string.
     *
     * @param exampleFilePath  Path to the example file
     * @param templateFilePath Path to the template file
     * @return Serialized configuration as a string
     * @throws Exception If an Exception occurs
     */
    public static String serializeConfigToString(final String exampleFilePath, final String templateFilePath)
            throws Exception {
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        final String template = Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        final Configuration targetModule = getTargetModule(xmlConfig);
        final String baseIndent = isTreeWalkerConfig(xmlConfig) ? TREE_WALKER_INDENT : NON_TREE_WALKER_INDENT;
        final String moduleContent = targetModule != null ? buildModuleContent(targetModule, baseIndent) : "";

        return TemplateProcessor.replacePlaceholders(template, moduleContent, isTreeWalkerConfig(xmlConfig));
    }

    /**
     * Serializes multiple configurations to a single string.
     *
     * @param exampleFilePaths Array of paths to example files
     * @param templateFilePath Path to the template file
     * @return Serialized configuration as a string
     * @throws Exception If an Exception occurs
     */
    public static String serializeAllInOneConfigToString(final String[] exampleFilePaths, final String templateFilePath) throws Exception {
        final List<Configuration> combinedChildren = new ArrayList<>();
        boolean isTreeWalker = true;

        for (int i = 0; i < exampleFilePaths.length; i++) {
            final String exampleFilePath = exampleFilePaths[i];
            final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            final Configuration targetModule = getTargetModule(xmlConfig);

            if (targetModule != null) {
                isTreeWalker &= isTreeWalkerConfig(xmlConfig);
                for (final Configuration child : targetModule.getChildren()) {
                    final MultiPropertyConfiguration newChild = copyConfiguration(child);
                    setIdProperty(newChild, "example" + (i + 1));
                    combinedChildren.add(newChild);
                }
            }
        }

        final String baseIndent = isTreeWalker ? TREE_WALKER_INDENT : NON_TREE_WALKER_INDENT;
        final String combinedModuleContent = buildCombinedModuleChildren(combinedChildren, baseIndent);
        final String template = Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        return TemplateProcessor.replacePlaceholders(template, combinedModuleContent, isTreeWalker);
    }

    private static MultiPropertyConfiguration copyConfiguration(final Configuration config) throws CheckstyleException {
        final MultiPropertyConfiguration newConfig = new MultiPropertyConfiguration(config.getName());
        for (final String propertyName : config.getPropertyNames()) {
            final String propertyValue = config.getProperty(propertyName);
            if ("id".equals(propertyName)) {
                for (final String value : propertyValue.split(",")) {
                    newConfig.addMultiProperty(propertyName, value.trim());
                }
            } else {
                newConfig.addProperty(propertyName, propertyValue);
            }
        }
        for (final Configuration child : config.getChildren()) {
            newConfig.addChild(copyConfiguration(child));
        }
        return newConfig;
    }

    private static void setIdProperty(final MultiPropertyConfiguration config, final String idValue) {
        config.addMultiProperty("id", idValue);
    }

    private static String buildCombinedModuleChildren(final List<Configuration> children, final String indent)
            throws CheckstyleException {
        final StringBuilder builder = new StringBuilder(children.size() * 300);

        for (final Configuration child : children) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\"/>\n\n");
            } else {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\">\n")
                        .append(childProperties).append('\n')
                        .append(indent).append("</module>\n\n");
            }
        }

        return builder.toString().trim();
    }

    private static String buildProperties(final Configuration config, final String indent) throws CheckstyleException {
        final String[] propertyNames = config.getPropertyNames();
        final StringBuilder builder = new StringBuilder(propertyNames.length * 50);
        final List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(propertyNames));
        Collections.sort(sortedPropertyNames);

        for (final String propertyName : sortedPropertyNames) {
            final String propertyValue = config.getProperty(propertyName);
            if ("id".equals(propertyName)) {
                final List<String> idValues = new ArrayList<>(Arrays.asList(propertyValue.split(",")));
                Collections.sort(idValues);  // Sort the id values
                for (final String value : idValues) {
                    appendProperty(builder, indent, propertyName, value.trim());
                }
            } else {
                appendProperty(builder, indent, propertyName, propertyValue);
            }
        }
        return builder.toString();
    }

    private static void appendProperty(final StringBuilder builder, final String indent, final String name, final String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(indent).append("<property name=\"").append(name)
                .append("\" value=\"").append(value).append("\"/>");
    }

    private static Configuration getTargetModule(final Configuration config) {
        final Configuration treeWalkerModule = getTreeWalkerModule(config);
        return treeWalkerModule == null ? config : treeWalkerModule;
    }

    private static Configuration getTreeWalkerModule(final Configuration config) {
        for (final Configuration child : config.getChildren()) {
            if (TREE_WALKER.equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    /**
     * Checks if the configuration is a TreeWalker configuration.
     *
     * @param config The configuration to check
     * @return true if it's a TreeWalker configuration, false otherwise
     */
    public static boolean isTreeWalkerConfig(final Configuration config) {
        return getTreeWalkerModule(config) != null;
    }

    private static String buildModuleContent(final Configuration config, final String indent) throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        for (final Configuration child : config.getChildren()) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\"/>\n\n");
            } else {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\">\n")
                        .append(childProperties).append('\n')
                        .append(indent).append("</module>\n\n");
            }
        }
        return builder.toString().trim();
    }

    /**
     * Extracts the module name from a given example file.
     *
     * @param exampleFilePath Path to the example file
     * @return The extracted module name
     * @throws Exception If an Exception occurs
     */
    public static String extractModuleName(final String exampleFilePath) throws Exception  {
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        return getSpecificModuleName(xmlConfig);
    }

    private static String getSpecificModuleName(final Configuration config) {
        if (config.getChildren().length == 0) {
            return config.getName();
        }
        for (final Configuration child : config.getChildren()) {
            if (!CHECKER.equals(child.getName()) && !TREE_WALKER.equals(child.getName())) {
                return child.getName();
            }
            final String moduleName = getSpecificModuleName(child);
            if (!moduleName.equals(child.getName())) {
                return moduleName;
            }
        }
        return config.getName();
    }
}