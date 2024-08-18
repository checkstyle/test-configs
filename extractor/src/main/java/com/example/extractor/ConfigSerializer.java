///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2024 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

/**
 * This package contains classes and utilities for extracting and processing
 * Checkstyle configurations and examples.
 */

package com.example.extractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

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

    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(ConfigSerializer.class.getName());

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
     * @param templateFileName Path to the template file
     * @return Serialized configuration as a string
     * @throws IllegalArgumentException If no modules are found in the input file
     * @throws Exception  If an unexpected error occurs
     */
    public static String serializeNonXmlConfigToString(
            final String inputFilePath,
            final String templateFileName) throws Exception {
        if (inputFilePath == null || templateFileName == null) {
            throw new IllegalArgumentException(
                    "Input file path and template resource name must not be null"
            );
        }

        final ModuleInputConfiguration mainModule = parseAndValidateInputFile(inputFilePath);

        final String moduleName = extractSimpleModuleName(mainModule.getModuleName());
        final Map<String, String> properties = mainModule.getNonDefaultProperties();

        LOGGER.info("Reading template resource: " + templateFileName);
        final String template = readResourceAsString(templateFileName);
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException(
                    "Failed to read template resource: " + templateFileName
            );
        }

        final Configuration moduleConfig = createConfigurationFromModule(moduleName, properties);
        final boolean isTreeWalker = isTreeWalkerCheck(mainModule.getModuleName());

        final String baseIndent;
        if (isTreeWalker) {
            baseIndent = TREE_WALKER_INDENT;
        }
        else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }

        final String moduleContent = buildSingleModuleContent(moduleConfig, baseIndent);
        return TemplateProcessor.replacePlaceholders(template, moduleContent, isTreeWalker) + "\n";
    }

    /**
     * Parses and validates the input file to extract the main module configuration.
     *
     * @param inputFilePath the path to the input file.
     * @return the main {@link ModuleInputConfiguration} extracted from the input file.
     * @throws IllegalArgumentException if parsing fails or no valid modules are found.
     * @throws Exception if an unexpected error occurs.
     */
    private static ModuleInputConfiguration parseAndValidateInputFile(final String inputFilePath)
            throws Exception {
        LOGGER.info("Parsing input file: " + inputFilePath);
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parse(inputFilePath);

        if (testInputConfiguration == null) {
            throw new IllegalArgumentException("Failed to parse input file: " + inputFilePath);
        }

        final List<ModuleInputConfiguration> modules = testInputConfiguration.getChildrenModules();

        if (modules == null || modules.isEmpty()) {
            throw new IllegalArgumentException(
                    "No modules found in the input file: " + inputFilePath
            );
        }

        final ModuleInputConfiguration mainModule = modules.get(0);
        if (mainModule == null) {
            throw new IllegalArgumentException(
                    "Main module is null in the input file: " + inputFilePath
            );
        }

        return mainModule;
    }

    /**
     * Reads the content of a resource file as a string.
     *
     * @param resourceName the name of the resource to be read.
     * @return the content of the resource file as a string.
     * @throws IOException if the resource is not found or if an I/O error occurs.
     */
    private static String readResourceAsString(final String resourceName) throws IOException {
        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourceName);
            }
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(inputStream,
                                 StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Determines if a given module name corresponds to a TreeWalker check.
     *
     * @param moduleName The name of the module to check
     * @return true if the module is a TreeWalker check, false otherwise
     */
    public static boolean isTreeWalkerCheck(final String moduleName) {
        boolean isTreeWalkerCheck = false;
        try {
            final Class<?> moduleClass = Class.forName(moduleName);
            isTreeWalkerCheck = AbstractCheck.class.isAssignableFrom(moduleClass);
        }
        catch (ClassNotFoundException ex) {
            LOGGER.severe("Class not found: " + moduleName);
        }
        return isTreeWalkerCheck;
    }

    /**
     * Builds the XML content for a single Checkstyle module, including its properties.
     *
     * @param config The Checkstyle configuration to convert into XML content.
     * @param indent The indentation to apply to the XML elements.
     * @return The XML content of the module as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    private static String buildSingleModuleContent(final Configuration config,
                                                   final String indent) throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        builder.append(MODULE_TAG).append(config.getName()).append("\">\n");
        final String properties = buildProperties(config, indent + "    ");
        if (properties.isEmpty()) {
            // If there are no properties, we can use a self-closing tag
            builder.setLength(builder.length() - 2);
            builder.append("/>");
        }
        else {
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
        String simpleModuleName;

        if (lastDotIndex == -1) {
            simpleModuleName = fullModuleName;
        }
        else {
            simpleModuleName = fullModuleName.substring(lastDotIndex + 1);
        }

        // Remove the "Check" suffix if present
        if (simpleModuleName.endsWith("Check")) {
            simpleModuleName =
                    simpleModuleName.substring(0, simpleModuleName.length() - CHECK_SUFFIX_LENGTH);
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
    private static Configuration createConfigurationFromModule(
            final String moduleName,
            final Map<String, String> properties) {
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
    public static String serializeConfigToString(final String exampleFilePath,
                                                 final String templateFilePath)
            throws Exception {
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();

        final String template = Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        final Configuration targetModule = getTargetModule(xmlConfig);

        final String baseIndent;
        if (isTreeWalkerConfig(xmlConfig)) {
            baseIndent = TREE_WALKER_INDENT;
        }
        else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }

        final String moduleContent;
        if (targetModule != null) {
            moduleContent = buildModuleContent(targetModule, baseIndent);
        }
        else {
            moduleContent = "";
        }
        return TemplateProcessor.replacePlaceholders(template, moduleContent,
                isTreeWalkerConfig(xmlConfig));
    }

    /**
     * Serializes multiple configurations to a single string.
     *
     * @param exampleFilePaths Array of paths to example files
     * @param templateFilePath Path to the template file
     * @return Serialized configuration as a string
     * @throws Exception If an Exception occurs
     */
    public static String serializeAllInOneConfigToString(
            final String[] exampleFilePaths,
            final String templateFilePath) throws Exception {
        final List<Configuration> combinedChildren = new ArrayList<>();
        boolean isTreeWalker = true;

        for (int index = 0; index < exampleFilePaths.length; index++) {
            final String exampleFilePath = exampleFilePaths[index];
            final TestInputConfiguration testInputConfiguration =
                    InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            final Configuration targetModule = getTargetModule(xmlConfig);
            if (targetModule != null) {
                isTreeWalker &= isTreeWalkerConfig(xmlConfig);
                for (final Configuration child : targetModule.getChildren()) {
                    // Use the new copyConfiguration method
                    final Configuration newChild =
                            copyConfiguration(child, "example" + (index + 1));
                    combinedChildren.add(newChild);
                }
            }
        }

        final String baseIndent;
        if (isTreeWalker) {
            baseIndent = TREE_WALKER_INDENT;
        }
        else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }

        final String combinedModuleContent =
                buildCombinedModuleChildren(combinedChildren, baseIndent);
        final String template =
                Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        return TemplateProcessor.replacePlaceholders(template, combinedModuleContent, isTreeWalker);
    }

    /**
     * Creates a deep copy of the given configuration.
     *
     * @param config the configuration to copy
     * @param newId the new ID to assign to the copied configuration
     * @return a new {@link Configuration} that is a deep copy of the provided configuration
     * @throws CheckstyleException if copying fails
     */
    private static Configuration copyConfiguration(final Configuration config, final String newId) {
        final DefaultConfiguration newConfig = new DefaultConfiguration(config.getName());

        for (final String name : config.getPropertyNames()) {
            try {
                final String value = config.getProperty(name);
                newConfig.addProperty(name, value);
            }
            catch (CheckstyleException ex) {
                // Property not found, skipping
            }
        }

        // Set the new ID
        newConfig.addProperty("id", newId);

        return newConfig;
    }

    /**
     * Builds the combined XML content for multiple Checkstyle module children.
     *
     * @param children The list of child configurations to combine.
     * @param indent   The indentation to apply to the XML elements.
     * @return The combined XML content of the module children as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    private static String buildCombinedModuleChildren(
            final List<Configuration> children,
            final String indent)
            throws CheckstyleException {
        final StringBuilder builder = new StringBuilder(children.size() * 300);

        for (final Configuration child : children) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent)
                        .append(MODULE_TAG)
                        .append(child.getName())
                        .append("\"/>\n\n");
            }
            else {
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
    private static String buildProperties(
            final Configuration config,
            final String indent) throws CheckstyleException {
        final String[] propertyNames = config.getPropertyNames();
        final StringBuilder builder = new StringBuilder(propertyNames.length * 50);
        final List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(propertyNames));
        Collections.sort(sortedPropertyNames);

        for (final String propertyName : sortedPropertyNames) {
            final String propertyValue = config.getProperty(propertyName);
            if ("id".equals(propertyName)) {
                final List<String> idValues =
                        new ArrayList<>(Arrays.asList(propertyValue.split(",")));
                Collections.sort(idValues);
                for (final String value : idValues) {
                    appendProperty(builder, indent, propertyName, value.trim());
                }
            }
            else {
                appendProperty(builder, indent, propertyName, propertyValue);
            }
        }
        return builder.toString();
    }

    /**
     * Appends a property in XML format to the provided StringBuilder.
     *
     * @param builder the StringBuilder to append to
     * @param indent the indentation to apply before the property tag
     * @param name the name of the property
     * @param value the value of the property
     */
    private static void appendProperty(final StringBuilder builder, final String indent,
                                       final String name, final String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(indent).append("<property name=\"").append(name)
                .append("\" value=\"").append(value).append("\"/>");
    }

    /**
     * Retrieves the target module from the given configuration,
     * prioritizing the TreeWalker module if present.
     *
     * @param config The configuration from which to retrieve the target module.
     * @return The TreeWalker module if present, otherwise the original configuration.
     */
    private static Configuration getTargetModule(final Configuration config) {
        final Configuration targetModule;

        final Configuration treeWalkerModule = getTreeWalkerModule(config);
        if (treeWalkerModule == null) {
            targetModule = config;
        }
        else {
            targetModule = treeWalkerModule;
        }

        return targetModule;
    }

    /**
     * Retrieves the TreeWalker module from the given configuration.
     *
     * @param config The configuration to search for the TreeWalker module.
     * @return The TreeWalker module if found, otherwise null.
     */
    private static Configuration getTreeWalkerModule(final Configuration config) {
        Configuration treeWalkerModule = null;

        for (final Configuration child : config.getChildren()) {
            if (TREE_WALKER.equals(child.getName())) {
                treeWalkerModule = child;
                break;
            }
        }

        return treeWalkerModule;
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
    private static String buildModuleContent(
            final Configuration config,
            final String indent)
            throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        for (final Configuration child : config.getChildren()) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent)
                        .append(MODULE_TAG)
                        .append(child.getName())
                        .append("\"/>\n\n");
            }
            else {
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
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        return getSpecificModuleName(xmlConfig);
    }

    /**
     * Recursively retrieves the specific module name from the configuration.
     * It skips "Checker" and "TreeWalker" modules
     * and returns the name of the first specific module found.
     *
     * @param config The configuration to search for the specific module name.
     * @return The name of the specific module, or the name of the given configuration
     *         if no specific module is found.
     */
    private static String getSpecificModuleName(final Configuration config) {
        String result = config.getName();

        if (config.getChildren().length > 0) {
            for (final Configuration child : config.getChildren()) {
                if (!CHECKER.equals(child.getName()) && !TREE_WALKER.equals(child.getName())) {
                    result = child.getName();
                    break;
                }

                final String moduleName = getSpecificModuleName(child);
                if (!moduleName.equals(child.getName())) {
                    result = moduleName;
                    break;
                }
            }
        }

        return result;
    }
}
