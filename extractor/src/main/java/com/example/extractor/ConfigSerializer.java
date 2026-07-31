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
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.ModuleInputConfiguration;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * Utility class for serializing Checkstyle configurations.
 * This class provides methods to serialize configurations to files or strings,
 * and to extract module names from configuration files.
 *
 * <p>Loading/inspecting {@link Configuration} objects is delegated to
 * {@link XmlConfigLoader}, and rendering a configuration back to XML text is
 * delegated to {@link XmlModuleContentBuilder}; this class retains only the
 * top-level serialize orchestration, reducing its own cyclomatic complexity
 * (PMD CyclomaticComplexity). All existing public method signatures are unchanged
 * and behave identically to before the split.
 */
public final class ConfigSerializer {

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
     * Default id prefix used when generating all-in-one configs
     * (e.g. "all-examples-in-one" -> ids "example1", "example2", ...).
     */
    private static final String DEFAULT_ID_PREFIX = "example";

    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(ConfigSerializer.class.getName());

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ConfigSerializer() {
        // Private constructor to prevent instantiation
    }

    /**
     * Determines if a given module name corresponds to a TreeWalker check.
     *
     * @param moduleName The name of the module to check
     * @return true if the module is a TreeWalker check, false otherwise
     */
    public static boolean isTreeWalkerCheck(final String moduleName) {
        return XmlConfigLoader.isTreeWalkerCheck(moduleName);
    }

    /**
     * Checks if the configuration is a TreeWalker configuration.
     *
     * @param config The configuration to check
     * @return true if it's a TreeWalker configuration, false otherwise
     */
    public static boolean isTreeWalkerConfig(final Configuration config) {
        return XmlConfigLoader.isTreeWalkerConfig(config);
    }

    /**
     * Loads XML configuration from inline or external XML example config.
     *
     * @param exampleFilePath Path to the example file
     * @return Loaded XML configuration
     * @throws Exception if an unexpected error occurs
     */
    public static Configuration loadXmlConfiguration(final String exampleFilePath)
            throws Exception {
        return XmlConfigLoader.loadXmlConfiguration(exampleFilePath);
    }

    /**
     * Gets config file path for examples with external XML config files.
     *
     * @param exampleFilePath Path to the example file
     * @return Path to the config file to parse
     */
    public static String getConfigFilePath(final String exampleFilePath) {
        return XmlConfigLoader.getConfigFilePath(exampleFilePath);
    }

    /**
     * Extracts the module name from a given example file.
     *
     * @param exampleFilePath Path to the example file
     * @return The extracted module name
     * @throws Exception If an Exception occurs
     */
    public static String extractModuleName(final String exampleFilePath) throws Exception {
        return XmlConfigLoader.extractModuleName(exampleFilePath);
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
        final boolean isTreeWalker = XmlConfigLoader.isTreeWalkerCheck(mainModule.getModuleName());
        final String baseIndent = selectBaseIndent(isTreeWalker);

        final String moduleContent = XmlModuleContentBuilder.buildSingleModuleContent(
                moduleConfig, baseIndent);
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
     * Selects the base indentation to use based on whether the module tree
     * is a TreeWalker-rooted configuration.
     *
     * @param isTreeWalker whether the configuration is TreeWalker-rooted.
     * @return the indentation string to use.
     */
    private static String selectBaseIndent(final boolean isTreeWalker) {
        final String baseIndent;
        if (isTreeWalker) {
            baseIndent = TREE_WALKER_INDENT;
        }
        else {
            baseIndent = NON_TREE_WALKER_INDENT;
        }
        return baseIndent;
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
        final Configuration xmlConfig = XmlConfigLoader.loadXmlConfiguration(exampleFilePath);

        final String template =
                Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        final Configuration targetModule = XmlConfigLoader.getTargetModule(xmlConfig);
        final String baseIndent = selectBaseIndent(XmlConfigLoader.isTreeWalkerConfig(xmlConfig));

        final String moduleContent;
        if (targetModule != null) {
            moduleContent = XmlModuleContentBuilder.buildModuleContent(targetModule, baseIndent);
        }
        else {
            moduleContent = "";
        }
        return TemplateProcessor.replacePlaceholders(template, moduleContent,
                XmlConfigLoader.isTreeWalkerConfig(xmlConfig));
    }

    /**
     * Serializes multiple configurations to a single string, using the default
     * "example" id prefix (ids will be example1, example2, ...).
     *
     * @param exampleFilePaths Array of paths to example files
     * @param templateFilePath Path to the template file
     * @return Serialized configuration as a string
     * @throws Exception If an Exception occurs
     */
    public static String serializeAllInOneConfigToString(
            final String[] exampleFilePaths,
            final String templateFilePath) throws Exception {
        return serializeAllInOneConfigToString(exampleFilePaths, templateFilePath,
                DEFAULT_ID_PREFIX);
    }

    /**
     * Serializes multiple configurations to a single string, assigning each module
     * an id of the form {@code idPrefix + (index + 1)} (e.g. "example1", "usecase1").
     *
     * @param exampleFilePaths Array of paths to example files
     * @param templateFilePath Path to the template file
     * @param idPrefix The prefix to use when assigning ids to each combined module
     *                 (e.g. "example" for all-examples-in-one, "usecase" for
     *                 all-usecases-in-one).
     * @return Serialized configuration as a string
     * @throws Exception If an Exception occurs
     */
    public static String serializeAllInOneConfigToString(
            final String[] exampleFilePaths,
            final String templateFilePath,
            final String idPrefix) throws Exception {
        final List<Configuration> combinedChildren = new ArrayList<>();
        final boolean isTreeWalker = collectCombinedChildren(
                exampleFilePaths, idPrefix, combinedChildren);

        final String baseIndent = selectBaseIndent(isTreeWalker);
        final String combinedModuleContent =
                XmlModuleContentBuilder.buildCombinedModuleChildren(combinedChildren, baseIndent);
        final String template =
                Files.readString(Path.of(templateFilePath), StandardCharsets.UTF_8);

        return TemplateProcessor.replacePlaceholders(template, combinedModuleContent, isTreeWalker);
    }

    /**
     * Loads each example file, extracts its target module's children, assigns
     * them ids based on {@code idPrefix}, and appends them to {@code combinedChildren}.
     *
     * @param exampleFilePaths Array of paths to example files.
     * @param idPrefix The prefix to use when assigning ids to each combined module.
     * @param combinedChildren The list to which copied child configurations are appended.
     * @return true if every example file with a target module is TreeWalker-rooted
     *         (vacuously true if none are found), false otherwise.
     * @throws Exception If an Exception occurs while loading a configuration.
     */
    private static boolean collectCombinedChildren(
            final String[] exampleFilePaths,
            final String idPrefix,
            final List<Configuration> combinedChildren) throws Exception {
        boolean isTreeWalker = true;

        for (int index = 0; index < exampleFilePaths.length; index++) {
            final String exampleFilePath = exampleFilePaths[index];
            final Configuration xmlConfig = XmlConfigLoader.loadXmlConfiguration(exampleFilePath);
            final Configuration targetModule = XmlConfigLoader.getTargetModule(xmlConfig);
            if (targetModule != null) {
                isTreeWalker &= XmlConfigLoader.isTreeWalkerConfig(xmlConfig);
                XmlModuleContentBuilder.appendCopiedChildren(
                        targetModule, idPrefix + (index + 1), combinedChildren);
            }
        }

        return isTreeWalker;
    }
}
