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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.ModuleInputConfiguration;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * CheckstyleExampleExtractor class for extracting and processing Checkstyle examples.
 */
public final class CheckstyleExampleExtractor {
    /** Logger for this class. */
    private static final Logger LOGGER =
            Logger.getLogger(CheckstyleExampleExtractor.class.getName());

    /** The root directory of the project. */
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();

    /** The filename for project properties. */
    private static final String PROJECT_PROPERTIES_FILENAME = "list-of-projects.properties";

    /** The file path for project properties. */
    private static final String PROJECT_PROPERTIES_FILE_PATH =
            "src/main/resources/" + PROJECT_PROPERTIES_FILENAME;

    /** The regular expression pattern for excluded file paths. */
    private static final String EXCLUDED_FILE_PATTERN =
            "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/regexp/"
                    + "regexpmultiline/Example7.txt";

    /** The regular expression pattern for example files. */
    private static final String EXAMPLE_FILE_PATTERN = "Example\\d+\\.(java|txt)";

    /** The subfolder name for all-in-one examples. */
    private static final String ALL_IN_ONE_SUBFOLDER = "all-examples-in-one";

    /**
     * Number of expected arguments when processing a single input file.
     */
    private static final int SINGLE_INPUT_FILE_ARG_COUNT = 4;

    /**
     * Index of the "--input-file" flag in the argument array.
     */
    private static final int INPUT_FILE_FLAG_INDEX = 1;

    /**
     * Index of the input file path in the argument array.
     */
    private static final int INPUT_FILE_PATH_INDEX = 2;

    /**
     * Index of the output file path in the argument array.
     */
    private static final int OUTPUT_FILE_PATH_INDEX = 3;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CheckstyleExampleExtractor() {
        // Utility class, no instances
    }

    /**
     * Main method to process Checkstyle examples.
     *
     * @param args Command line arguments
     * @throws Exception If an error occurs during processing
     * @throws IllegalArgumentException if the argument is invalid.
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: <checkstyle repo path> [--input-config <config content> "
                            + "<output file path>]"
            );
        }

        if (args.length == SINGLE_INPUT_FILE_ARG_COUNT
                && "--input-file".equals(args[INPUT_FILE_FLAG_INDEX])) {
            // New functionality: process single input file
            final String inputFilePath = args[INPUT_FILE_PATH_INDEX];
            final String outputFilePath = args[OUTPUT_FILE_PATH_INDEX];
            processInputFile(Paths.get(inputFilePath), Paths.get(outputFilePath));
        }
        else {
            // Functionality: process all examples
            final String checkstyleRepoPath = args[0];
            final List<Path> allExampleDirs = findAllExampleDirs(checkstyleRepoPath);
            final Map<String, List<Path>> moduleExamples = processExampleDirs(allExampleDirs);

            YamlParserAndProjectHandler.processProjectsForExamples(PROJECT_ROOT.toString());

            for (final Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
                generateAllInOneConfig(entry.getKey(), entry.getValue());
                generateReadmes(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Processes an input file and generates an output file.
     *
     * @param inputFile  The path to the input file
     * @param outputFile The path to the output file
     * @throws Exception If an error occurs during processing
     * @throws IOException if the argument is invalid.
     */
    public static void processInputFile(
            final Path inputFile,
            final Path outputFile)
            throws Exception {
        // Check if the input file exists
        if (!Files.exists(inputFile)) {
            LOGGER.severe("Input file does not exist: " + inputFile);
            throw new IOException("Input file does not exist: " + inputFile);
        }

        // Get the template file path and serialize the configuration
        final String templateFilePath = getTemplateFilePathForInputFile(inputFile.toString());
        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(
                inputFile.toString(),
                templateFilePath
        );

        // Write the generated content to the output file
        Files.writeString(outputFile, generatedContent, StandardCharsets.UTF_8);

        LOGGER.info("Generated configuration at " + outputFile);
    }

    /**
     * Retrieves the template file path based on the input file path.
     *
     * @param inputFilePath The path to the input file
     * @return The template file path
     * @throws Exception if an unexpected error occurs.
     */
    public static String getTemplateFilePathForInputFile(final String inputFilePath)
            throws Exception {
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parse(inputFilePath);
        final List<ModuleInputConfiguration> modules =
                testInputConfiguration.getChildrenModules();

        final ModuleInputConfiguration mainModule = modules.get(0);
        final String moduleName = mainModule.getModuleName();

        final boolean isTreeWalker = ConfigSerializer.isTreeWalkerCheck(moduleName);

        final String resourceName;

        if (isTreeWalker) {
            resourceName = "config-template-treewalker.xml";
        }
        else {
            resourceName = "config-template-non-treewalker.xml";
        }

        return ResourceLoader.getResourcePath(resourceName);
    }

    /**
     * Retrieves all example directories within the specified Checkstyle repository path.
     *
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return A list of paths to the example directories.
     * @throws IOException If an I/O error occurs.
     */
    private static List<Path> findAllExampleDirs(final String checkstyleRepoPath)
            throws IOException {
        final List<Path> allExampleDirs = new ArrayList<>();
        allExampleDirs.addAll(
                findNonFilterExampleDirs(
                        Paths.get(checkstyleRepoPath,
                                "src", "xdocs-examples", "resources")
                )
        );

        allExampleDirs.addAll(
                findNonFilterExampleDirs(
                        Paths.get(checkstyleRepoPath,
                                "src", "xdocs-examples", "resources-noncompilable")
                )
        );
        return allExampleDirs;
    }

    /**
     * Processes example directories to map module names to their corresponding directories.
     *
     * @param allExampleDirs A list of paths to example directories.
     * @return A map associating module names with their example directories.
     * @throws Exception If an unexpected error occurs.
     */
    private static Map<String, List<Path>> processExampleDirs(
            final List<Path> allExampleDirs)
            throws Exception {
        final Map<String, List<Path>> moduleExamples = new ConcurrentHashMap<>();
        for (final Path dir : allExampleDirs) {
            final String moduleName = processDirectory(dir.toString());
            if (moduleName != null) {
                moduleExamples.computeIfAbsent(moduleName, moduleKey -> new ArrayList<>()).add(dir);
            }
        }
        return moduleExamples;
    }

    /**
     * Finds example directories within the specified base path, excluding certain directories.
     *
     * @param basePath The base path to search for example directories.
     * @return A list of paths to non-filtered example directories.
     * @throws IOException If an I/O error occurs.
     */
    private static List<Path> findNonFilterExampleDirs(final Path basePath) throws IOException {
        try (Stream<Path> pathStream = Files.walk(basePath)) {
            return pathStream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.toString().contains("suppresswarningsholder"))
                    .filter(path -> {
                        return !path.toString().contains("filters")
                                && !path.toString().contains("filfilters");
                    })
                    .filter(CheckstyleExampleExtractor::containsExampleFile)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Checks if the specified path contains any example files.
     *
     * @param path The path to check for example files.
     * @return true if the path contains example files; false otherwise.
     */
    private static boolean containsExampleFile(final Path path) {
        boolean result = false;
        try (Stream<Path> files = Files.list(path)) {
            result = files.anyMatch(file -> {
                return file.getFileName().toString().matches(EXAMPLE_FILE_PATTERN);
            });
        }
        catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Error listing files in directory: " + path, ex);
        }
        return result;
    }

    /**
     * Process a directory containing example files.
     *
     * @param inputDir Input directory path
     * @return Module name if processing was successful, null otherwise
     * @throws Exception If an I/O error occurs
     */
    public static String processDirectory(final String inputDir) throws Exception {
        String moduleName = null;

        final Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            final List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                    .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATTERN))
                    .collect(Collectors.toList());

            if (!exampleFiles.isEmpty()) {
                final Path firstExampleFile = exampleFiles.get(0);
                moduleName = ConfigSerializer.extractModuleName(firstExampleFile.toString());
                if (moduleName != null) {
                    final Path outputPath = PROJECT_ROOT.resolve(moduleName);
                    Files.createDirectories(outputPath);

                    for (final Path exampleFile : exampleFiles) {
                        processExampleFile(exampleFile, outputPath);
                    }
                }
            }
        }

        return moduleName;
    }

    /**
     * Processes an example file and creates a corresponding subfolder in the output path.
     *
     * @param exampleFile The example file to process.
     * @param outputPath  The path where the processed file's subfolder will be created.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processExampleFile(
            final Path exampleFile,
            final Path outputPath)
            throws Exception {
        final Path fileName = exampleFile.getFileName();
        if (fileName != null) {
            final String fileNameStr = fileName.toString().replaceFirst("\\.(java|txt)$", "");
            final Path subfolderPath = outputPath.resolve(fileNameStr);
            Files.createDirectories(subfolderPath);
            processFile(exampleFile.toString(), subfolderPath);
        }
    }

    /**
     * Processes an example file and generates its configuration, properties, and README.
     *
     * @param exampleFile The path to the example file.
     * @param outputPath  The path where the generated content will be stored.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processFile(
            final String exampleFile,
            final Path outputPath)
            throws Exception {
        if (exampleFile != null
                && outputPath != null
                && !exampleFile.endsWith(EXCLUDED_FILE_PATTERN)) {
            try {
                final String templateFilePath = getTemplateFilePathForExamples(exampleFile);
                if (templateFilePath != null) {
                    final String generatedContent =
                            ConfigSerializer.serializeConfigToString(exampleFile, templateFilePath);
                    writeConfigFile(outputPath, generatedContent);
                    copyPropertiesFile(outputPath);
                    generateReadme(outputPath);
                }
                else {
                    LOGGER.log(Level.WARNING,
                            "Unable to get template file path for: "
                                    + exampleFile);
                }
            }
            catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                        "Error reading or processing the file: "
                                + exampleFile, ex);
            }
        }
    }

    /**
     * Writes the serialized configuration content to a config.xml.
     *
     * @param outputPath The path where the config.xml file will be created.
     * @param content    The serialized configuration content to write.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeConfigFile(
            final Path outputPath,
            final String content) throws IOException {
        final Path outputFilePath = outputPath.resolve("config.xml");
        Files.writeString(outputFilePath, content, StandardCharsets.UTF_8);
    }

    /**
     * Copies the project properties file to the specified output path.
     *
     * @param outputPath The path where the properties file will be copied.
     * @throws IOException If an I/O error occurs.
     */
    private static void copyPropertiesFile(final Path outputPath) throws IOException {
        final Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
        final Path targetPropertiesPath = outputPath.resolve(PROJECT_PROPERTIES_FILENAME);
        Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Generates a README file in the specified output path based on the module name.
     *
     * @param outputPath The path where the README file will be generated.
     * @throws Exception If an unexpected error occurs.
     */
    private static void generateReadme(final Path outputPath) throws Exception {
        final Path parentPath = outputPath.getParent();
        if (parentPath != null) {
            final Path moduleNamePath = parentPath.getFileName();
            if (moduleNamePath != null) {
                final String moduleName = moduleNamePath.toString();
                ReadmeGenerator.generateIndividualReadme(outputPath, moduleName);
            }
        }
    }

    /**
     * Generate all-in-one configuration for a module.
     *
     * @param moduleName Module name
     * @param exampleDirs List of example directories
     * @throws Exception If an I/O error occurs during generation
     */
    public static void generateAllInOneConfig(
            final String moduleName,
            final List<Path> exampleDirs)
            throws Exception {
        final List<String> allExampleFiles = getAllExampleFiles(exampleDirs);
        final boolean shouldProceed = !allExampleFiles.isEmpty();

        if (shouldProceed) {
            Collections.sort(
                    allExampleFiles,
                    Comparator.comparingInt(CheckstyleExampleExtractor::extractExampleNumber)
            );

            final Path outputPath = PROJECT_ROOT.resolve(moduleName);
            final Path allInOneSubfolderPath = outputPath.resolve(ALL_IN_ONE_SUBFOLDER);
            Files.createDirectories(allInOneSubfolderPath);

            generateAllInOneContent(allExampleFiles, allInOneSubfolderPath);
            handleAllExamplesInOne(moduleName, allInOneSubfolderPath);
            generateAllInOneReadme(allInOneSubfolderPath, moduleName);
        }
    }

    /**
     * Retrieves all example files from the provided directories.
     *
     * @param exampleDirs The directories to search for example files.
     * @return A list of paths to the example files.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static List<String> getAllExampleFiles(final List<Path> exampleDirs)
            throws IOException {
        final List<String> allExampleFiles = new ArrayList<>();
        for (final Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                        .map(Path::toString)
                        .filter(file -> !file.endsWith(EXCLUDED_FILE_PATTERN))
                        .forEach(allExampleFiles::add);
            }
        }
        return allExampleFiles;
    }

    /**
     * Generates the "all-in-one" configuration content and writes it to a config.xml file.
     *
     * @param allExampleFiles      A list of all example file paths.
     * @param allInOneSubfolderPath The path where the "all-in-one" content will be stored.
     * @throws Exception If an unexpected error occurs during generation.
     */
    private static void generateAllInOneContent(
            final List<String> allExampleFiles,
            final Path allInOneSubfolderPath)
            throws Exception {
        final String templateFilePath = getTemplateFilePathForExamples(allExampleFiles.get(0));
        final Path outputFilePath = allInOneSubfolderPath.resolve("config.xml");

        final String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                allExampleFiles.toArray(new String[0]), templateFilePath);
        Files.writeString(outputFilePath, generatedContent);
    }

    /**
     * Handles the creation and copying of project files for the "all-examples-in-one" case.
     *
     * @param moduleName             The name of the module.
     * @param allInOneSubfolderPath  The path to the "all-examples-in-one" subfolder.
     */
    private static void handleAllExamplesInOne(
            final String moduleName,
            final Path allInOneSubfolderPath) {
        try {
            final Map<String, Object> yamlData = YamlParserAndProjectHandler.parseYamlFile();
            final Map<String, Object> moduleConfig = (Map<String, Object>) yamlData.get(moduleName);

            if (moduleConfig != null && moduleConfig.containsKey(ALL_IN_ONE_SUBFOLDER)) {
                final Map<String, Object> allInOneConfig =
                        (Map<String, Object>) moduleConfig.get(ALL_IN_ONE_SUBFOLDER);
                final List<String> projectNames =
                        (List<String>) allInOneConfig.get("projects");
                YamlParserAndProjectHandler.createProjectsFileForExample(
                        allInOneSubfolderPath,
                        projectNames,
                        Files.readAllLines(
                                Paths.get(YamlParserAndProjectHandler.ALL_PROJECTS_PATH)
                        ),
                        moduleName
                );
            }
            else {
                copyDefaultPropertiesFile(allInOneSubfolderPath);
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "Error processing YAML file for all-examples-in-one: "
                            + ex.getMessage(), ex);
            copyDefaultPropertiesFile(allInOneSubfolderPath);
        }
    }

    /**
     * Copies the default project properties file to the specified subfolder.
     *
     * @param allInOneSubfolderPath The path where the properties file will be copied.
     */
    private static void copyDefaultPropertiesFile(final Path allInOneSubfolderPath) {
        try {
            final Path sourcePropertiesPath = Paths
                    .get(YamlParserAndProjectHandler.DEFAULT_PROJECTS_PATH)
                    .toAbsolutePath();
            final Path targetPropertiesPath =
                    allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
            Files.copy(sourcePropertiesPath,
                    targetPropertiesPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error copying default properties file", ex);
        }
    }

    /**
     * Generates a README file for the "all-examples-in-one" case.
     *
     * @param allInOneSubfolderPath The path where the README file will be generated.
     * @param moduleName            The name of the module.
     * @throws IOException If an I/O error occurs during README generation.
     */
    private static void generateAllInOneReadme(
            final Path allInOneSubfolderPath,
            final String moduleName)
            throws IOException {
        ReadmeGenerator.generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    /**
     * Generates README files for each example in the specified directories.
     *
     * @param moduleName The name of the module.
     * @param exampleDirs The directories containing the examples.
     * @throws IOException If an I/O error occurs during README generation.
     */
    private static void generateReadmes(
            final String moduleName,
            final List<Path> exampleDirs)
            throws IOException {
        final Path outputPath = PROJECT_ROOT.resolve(moduleName);

        for (final Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                        .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATTERN))
                        .forEach(exampleFile -> {
                            generateIndividualReadme(exampleFile, outputPath, moduleName);
                        });
            }
        }

        final Path allInOneSubfolderPath = outputPath.resolve(ALL_IN_ONE_SUBFOLDER);
        generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    /**
     * Generates a README file for an individual example.
     *
     * @param exampleFile The path to the example file.
     * @param outputPath  The path where the README file will be generated.
     * @param moduleName  The name of the module.
     */
    private static void generateIndividualReadme(
            final Path exampleFile,
            final Path outputPath,
            final String moduleName) {
        Optional.ofNullable(exampleFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(name -> name.replaceFirst("\\.(java|txt)$", ""))
                .ifPresent(fileName -> {
                    final Path subfolderPath = outputPath.resolve(fileName);
                    try {
                        ReadmeGenerator.generateIndividualReadme(subfolderPath, moduleName);
                    }
                    catch (IOException ex) {
                        LOGGER.log(Level.SEVERE,
                                "Error generating individual README for: " + subfolderPath, ex);
                    }
                });

        if (!Optional.ofNullable(exampleFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .isPresent()) {
            LOGGER.log(Level.WARNING, "Invalid or null file name for: " + exampleFile);
        }
    }

    /**
     * Retrieves the template file path based on the example file's configuration.
     *
     * @param exampleFilePath The path to the example file.
     * @return The template file path.
     * @throws Exception If an unexpected error occurs.
     */
    private static String getTemplateFilePathForExamples(
            final String exampleFilePath)
            throws Exception {
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        final boolean isTreeWalker = ConfigSerializer.isTreeWalkerConfig(xmlConfig);

        final String resourceName;
        if (isTreeWalker) {
            resourceName = "config-template-treewalker.xml";
        }
        else {
            resourceName = "config-template-non-treewalker.xml";
        }

        return ResourceLoader.getResourcePath(resourceName);
    }

    /**
     * Extracts the example number from the filename.
     *
     * @param filename The filename to extract the number from.
     * @return The extracted example number, or {@code Integer.MAX_VALUE} if not found.
     */
    private static int extractExampleNumber(final String filename) {
        final Matcher matcher = Pattern.compile("Example(\\d+)\\.(java|txt)").matcher(filename);
        final int exampleNumber;

        if (matcher.find()) {
            exampleNumber = Integer.parseInt(matcher.group(1));
        }
        else {
            exampleNumber = Integer.MAX_VALUE;
        }

        return exampleNumber;
    }
}
