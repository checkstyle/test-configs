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

package com.example.extractor;

import java.util.List;
import java.util.logging.Logger;

import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * Utility class for loading Checkstyle {@link Configuration} objects from example files
 * and for inspecting/navigating those configurations (TreeWalker detection, module name
 * lookup). Split out of {@link ConfigSerializer} to reduce that class's cyclomatic
 * complexity (PMD CyclomaticComplexity); no behavior was changed during the split.
 */
public final class XmlConfigLoader {

    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(XmlConfigLoader.class.getName());

    /** Constant for the TreeWalker module name. */
    private static final String TREE_WALKER = "TreeWalker";

    /** Constant for the Checker module name. */
    private static final String CHECKER = "Checker";

    /** Known source path fragments for header examples with external XML config. */
    private static final List<String> EXTERNAL_XML_CONFIG_PATHS = List.of(
            "checks/header/header",
            "checks/header/regexpheader",
            "checks/header/multifileregexpheader"
    );

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private XmlConfigLoader() {
        // Private constructor to prevent instantiation
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
     * Loads XML configuration from inline or external XML example config.
     *
     * @param exampleFilePath Path to the example file
     * @return Loaded XML configuration
     * @throws Exception if an unexpected error occurs
     */
    public static Configuration loadXmlConfiguration(final String exampleFilePath)
            throws Exception {
        final Configuration result;
        if (isExternalXmlConfigPath(exampleFilePath)) {
            result = ConfigurationLoader.loadConfiguration(
                    getConfigFilePath(exampleFilePath),
                    new PropertiesExpander(System.getProperties()),
                    ConfigurationLoader.IgnoredModulesOptions.EXECUTE
            );
        }
        else {
            final TestInputConfiguration testInputConfiguration =
                    InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            result = testInputConfiguration.getXmlConfiguration();
        }
        return result;
    }

    /**
     * Gets config file path for examples with external XML config files.
     *
     * @param exampleFilePath Path to the example file
     * @return Path to the config file to parse
     */
    public static String getConfigFilePath(final String exampleFilePath) {
        String configFilePath = exampleFilePath;
        if (isExternalXmlConfigPath(exampleFilePath)) {
            configFilePath = exampleFilePath.replaceFirst("\\.(java|txt)$", ".xml");
        }
        return configFilePath;
    }

    /**
     * Checks if example uses external XML config.
     *
     * @param exampleFilePath Path to the example file
     * @return true if example uses external XML config
     */
    private static boolean isExternalXmlConfigPath(final String exampleFilePath) {
        final String normalizedPath = exampleFilePath.replace('\\', '/');
        return EXTERNAL_XML_CONFIG_PATHS.stream().anyMatch(normalizedPath::contains);
    }

    /**
     * Retrieves the target module from the given configuration,
     * prioritizing the TreeWalker module if present.
     *
     * @param config The configuration from which to retrieve the target module.
     * @return The TreeWalker module if present, otherwise the original configuration.
     */
    public static Configuration getTargetModule(final Configuration config) {
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
     * Extracts the module name from a given example file.
     *
     * @param exampleFilePath Path to the example file
     * @return The extracted module name
     * @throws Exception If an Exception occurs
     */
    public static String extractModuleName(final String exampleFilePath) throws Exception {
        final Configuration xmlConfig = loadXmlConfiguration(exampleFilePath);
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
