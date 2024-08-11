/**
 * This package contains classes and utilities for extracting and processing
 * Checkstyle configurations and examples.
 */

package com.example.extractor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.ModuleInputConfiguration;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * Utility class for serializing Checkstyle configurations.
 * This class provides methods to serialize configurations to files or strings,
 * and to extract module names from configuration files.
 */
public final class ConfigSerializer {

    /**
     * Constant for the TreeWalker module name.
     */
    private static final String TREE_WALKER = "TreeWalker";

    /**
     * Constant for the Checker module name.
     */
    private static final String CHECKER = "Checker";

    /**
     * XML tag for module elements.
     */
    private static final String MODULE_TAG = "<module name=\"";

    /**
     * Indentation for TreeWalker modules.
     */
    private static final String TREE_WALKER_INDENT = "        ";

    /**
     * Indentation for non-TreeWalker modules.
     */
    private static final String NON_TREE_WALKER_INDENT = "    ";

    /**
     * Constant for the CHECK_SUFFIX_LENGTH.
     */
    private static final int CHECK_SUFFIX_LENGTH = 5;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ConfigSerializer() {
        // Private constructor to prevent instantiation
    }

    /**
     * Serializes a configuration to a string from a non-XML format input file.
     *
     * @param inputFilePath    Path to the input file
     * @param templateFilePath Path to the template file
     * @return Serialized configuration as a string
     * @throws IllegalArgumentException If no modules are found in the input file
     * @throws Exception  If an unexpected error occurs
     */
    public static String serializeNonXmlConfigToString(final String inputFilePath, final String templateFilePath)
            throws Exception {
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parse(inputFilePath);
        final List<ModuleInputConfiguration> modules = testInputConfiguration.getChildrenModules();

        if (modules.isEmpty()) {
            throw new IllegalArgumentException("No modules found in the input file");
        }

        final ModuleInputConfiguration mainModule = modules.get(0);
        final String moduleName = extractSimpleModuleName(mainModule.getModuleName());
        final Map<String, String> properties = mainModule.getNonDefaultProperties();

        final String template = Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        final Configuration moduleConfig = createConfigurationFromModule(moduleName, properties);
        final boolean isTreeWalker = isTreeWalkerCheck(mainModule.getModuleName());
        final String baseIndent;
        if (isTreeWalker) {
            baseIndent = TREE_WALKER_INDENT;
        } else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }        final String moduleContent = buildSingleModuleContent(moduleConfig, baseIndent);
        return TemplateProcessor.replacePlaceholders(template, moduleContent, isTreeWalker);
    }

    /**
     * Determines if a given module name corresponds to a TreeWalker check.
     *
     * @param moduleName The name of the module to check
     * @return true if the module is a TreeWalker check, false otherwise
     */
    public static boolean isTreeWalkerCheck(final String moduleName) {
        try {
            final Class<?> moduleClass = Class.forName(moduleName);
            return AbstractCheck.class.isAssignableFrom(moduleClass);
        } catch (ClassNotFoundException e) {
            // If the class is not found, we can't determine if it's a TreeWalker check
            // You might want to log this or handle it differently
            return false;
        }
    }

    /**
     * Builds the XML content for a single Checkstyle module, including its properties.
     *
     * @param config The Checkstyle configuration to convert into XML content.
     * @param indent The indentation to apply to the XML elements.
     * @return The XML content of the module as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    private static String buildSingleModuleContent(final Configuration config, final String indent) throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        builder.append(MODULE_TAG).append(config.getName()).append("\">\n");
        final String properties = buildProperties(config, indent + "    ");
        if (properties.isEmpty()) {
            // If there are no properties, we can use a self-closing tag
            builder.setLength(builder.length() - 2); // Remove the ">\n"
            builder.append("/>");
        } else {
            builder.append(properties)
                    .append('\n')
                    .append(indent)
                    .append("</module>");
        }
        return builder.toString();
    }

    /**
     * Extracts the simple module name from the fully qualified module name.
     *
     * @param fullModuleName The fully qualified module name.
     * @return The simple module name without the package and "Check" suffix.
     */
    private static String extractSimpleModuleName(final String fullModuleName) {
        final int lastDotIndex = fullModuleName.lastIndexOf('.');
        final String simpleModuleName;

        if (lastDotIndex == -1) {
            simpleModuleName = fullModuleName;
        } else {
            simpleModuleName = fullModuleName.substring(lastDotIndex + 1);
        }

        // Remove the "Check" suffix if present
        if (simpleModuleName.endsWith("Check")) {
            return simpleModuleName.substring(0, simpleModuleName.length() - CHECK_SUFFIX_LENGTH);
        }

        return simpleModuleName;
    }

    /**
     * Creates a Checkstyle configuration from a module name and its properties.
     *
     * @param moduleName The name of the module.
     * @param properties The properties to include in the configuration.
     * @return The constructed Checkstyle configuration.
     */
    private static Configuration createConfigurationFromModule(final String moduleName, final Map<String, String> properties) {
        final DefaultConfiguration config = new DefaultConfiguration(moduleName);
        for (final Map.Entry<String, String> entry : properties.entrySet()) {
            config.addProperty(entry.getKey(), entry.getValue());
        }
        return config;
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

        final String baseIndent;
        if (isTreeWalkerConfig(xmlConfig)) {
            baseIndent = TREE_WALKER_INDENT;
        } else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }

        final String moduleContent;
        if (targetModule != null) {
            moduleContent = buildModuleContent(targetModule, baseIndent);
        } else {
            moduleContent = "";
        }
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
    public static String serializeAllInOneConfigToString(final String[] exampleFilePaths, final String templateFilePath)
            throws Exception {
        final List<Configuration> combinedChildren = new ArrayList<>();
        boolean isTreeWalker = true;

        for (int index = 0; index < exampleFilePaths.length; index++) {
            final String exampleFilePath = exampleFilePaths[index];
            final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            final Configuration targetModule = getTargetModule(xmlConfig);
            if (targetModule != null) {
                isTreeWalker &= isTreeWalkerConfig(xmlConfig);
                for (final Configuration child : targetModule.getChildren()) {
                    final Configuration modifiedChild = addIdProperty(child, "example" + (index + 1));
                    combinedChildren.add(modifiedChild);
                }
            }
        }

        final String baseIndent;
        if (isTreeWalker) {
            baseIndent = TREE_WALKER_INDENT;
        } else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }

        final String combinedModuleContent = buildCombinedModuleChildren(combinedChildren, baseIndent);
        final String template = Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);
        return TemplateProcessor.replacePlaceholders(template, combinedModuleContent, isTreeWalker);
    }

    /**
     * Builds the combined XML content for multiple Checkstyle module children.
     *
     * @param children The list of child configurations to combine.
     * @param indent   The indentation to apply to the XML elements.
     * @return The combined XML content of the module children as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
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

    /**
     * Builds the XML properties for a given Checkstyle configuration.
     *
     * @param config The Checkstyle configuration whose properties are to be built.
     * @param indent The indentation to apply to each property.
     * @return The XML content of the properties as a string.
     * @throws CheckstyleException If an error occurs while retrieving properties.
     */
    private static String buildProperties(final Configuration config, final String indent) throws CheckstyleException {
        final String[] propertyNames = config.getPropertyNames();
        // Estimate 50 characters per property (name, value, XML tags)
        final StringBuilder builder = new StringBuilder(propertyNames.length * 50);
        final List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(propertyNames));
        Collections.sort(sortedPropertyNames);
        for (final String propertyName : sortedPropertyNames) {
            final String propertyValue = config.getProperty(propertyName);
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(indent).append("<property name=\"").append(propertyName)
                    .append("\" value=\"").append(propertyValue).append("\"/>");
        }
        return builder.toString();
    }

    /**
     * Retrieves the target module from the given configuration,
     * prioritizing the TreeWalker module if present.
     *
     * @param config The configuration from which to retrieve the target module.
     * @return The TreeWalker module if present, otherwise the original configuration.
     */
    private static Configuration getTargetModule(final Configuration config) {
        final Configuration treeWalkerModule = getTreeWalkerModule(config);

        if (treeWalkerModule == null) {
            return config;
        } else {
            return treeWalkerModule;
        }
    }

    /**
     * Retrieves the TreeWalker module from the given configuration.
     *
     * @param config The configuration to search for the TreeWalker module.
     * @return The TreeWalker module if found, otherwise null.
     */
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

    /**
     * Builds the XML content for a Checkstyle module and its child modules.
     *
     * @param config The configuration containing the module and its children.
     * @param indent The indentation to apply to the XML elements.
     * @return The XML content of the module and its children as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
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
    public static String extractModuleName(final String exampleFilePath) throws Exception {
        final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        return getSpecificModuleName(xmlConfig);
    }

    /**
     * Recursively retrieves the specific module name from the configuration.
     * It skips "Checker" and "TreeWalker" modules and returns the name of the first specific module found.
     *
     * @param config The configuration to search for the specific module name.
     * @return The name of the specific module, or the name of the given configuration if no specific module is found.
     */
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

    /**
     * Adds an "id" property to the given configuration.
     *
     * @param config  The original configuration to be modified.
     * @param idValue The value of the "id" property to be added.
     * @return A new configuration with the added "id" property.
     */
    private static Configuration addIdProperty(final Configuration config, final String idValue) {
        return new IdPropertyAddingConfiguration(config, idValue);
    }

    /**
     * This class is a decorator for the Configuration object.
     * It adds an "id" property to the existing Configuration.
     */
    private static class IdPropertyAddingConfiguration implements Configuration {
        /**
         * Identifier for serialized class version.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The original Configuration object that is being decorated.
         */
        private final Configuration config;

        /**
         * The value of the "id" property to be added.
         */
        private final String idValue;

        /**
         * Constructs an IdPropertyAddingConfiguration instance.
         *
         * @param config  The original Configuration object to be decorated.
         * @param idValue The value of the "id" property to be added.
         */
        public IdPropertyAddingConfiguration(final Configuration config, final String idValue) {
            this.config = config;
            this.idValue = idValue;
        }

        @Override
        public String getName() {
            return config.getName();
        }

        @Override
        public Map<String, String> getMessages() {
            return Collections.emptyMap();
        }

        @Override
        public String[] getAttributeNames() {
            return new String[0];
        }

        @Override
        public String getAttribute(final String name) {
            return null;
        }

        @Override
        public String[] getPropertyNames() {
            final List<String> propertyNames = new ArrayList<>(Arrays.asList(config.getPropertyNames()));
            propertyNames.add("id");
            return propertyNames.toArray(new String[0]);
        }

        @Override
        public String getProperty(final String name) throws CheckstyleException {
            if ("id".equals(name)) {
                return idValue;
            }
            return config.getProperty(name);
        }

        @Override
        public Configuration[] getChildren() {
            return config.getChildren();
        }
    }
}