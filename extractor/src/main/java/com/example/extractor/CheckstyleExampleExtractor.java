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
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Properties;
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
    private static final String PROJ_PROP_FILENAME = "list-of-projects.properties";

    /** The filename for project yml. */
    private static final String PROJ_YML_PROP_FILENAME = "list-of-projects.yml";

    /** The regular expression pattern for example files. */
    private static final String EXAMPLE_FILE_PATTERN = "(Example|UseCase)\\d+\\.(java|txt)";

    /** The subfolder name for all-in-one examples. */
    private static final String ALL_IN_ONE_SUBFOLDER = "all-examples-in-one";

    /** The filename for the Java header file. */
    private static final String JAVA_HEADER_FILENAME = "java.header";

    /** The filename for the Apache header file. */
    private static final String APACHE_HEADER_FILENAME = "apache.header";

    /** The filename for the copyright header file. */
    private static final String COPYRIGHT_HEADER_FILENAME = "copyright.header";

    /** The filename for the universal header file. */
    private static final String UNIVERSAL_HEADER_FILENAME = "universal.header";

    /** The name of the Example2 directory. */
    private static final String EXAMPLE2_DIR = "Example2";

    /** The name of the Example3 directory. */
    private static final String EXAMPLE3_DIR = "Example3";

    /** The name of the Example4 directory. */
    private static final String EXAMPLE4_DIR = "Example4";

    /** The name of the Header directory. */
    private static final String HEADER_MODULE = "Header";

    /** The name of the RegexpHeader directory. */
    private static final String REGEXP_HEADER_MODULE = "RegexpHeader";

    /** The name of the MultiFileRegexpHeader directory. */
    private static final String MF_REGEXP_HEADER_MODULE = "MultiFileRegexpHeader";

    /** Number of expected arguments when processing a single input file. */
    private static final int SINGLE_INPUT_FILE_ARG_COUNT = 5;

    /** Index of the "--input-file" flag in the argument array. */
    private static final int INPUT_FILE_FLAG_INDEX = 1;

    /** Index of the input file path in the argument array. */
    private static final int INPUT_FILE_PATH_INDEX = 2;

    /** Index of the output file path in the argument array. */
    private static final int OUTPUT_FILE_PATH_INDEX = 3;

    /** Index of the output file path in the argument array. */
    private static final int PROJECT_OUTPUT_PATH_INDEX = 4;

    /** The buffer size for reading and writing files. */
    private static final int BUFFER_SIZE = 1024;

    /** The constant for xdocs header check resources path. */
    private static final String XDOCS_HEADER_CHECKS_PATH =
            "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/header";

    /** The xdocs resource directory for Header examples. */
    private static final String HEADER_RESOURCE_DIR = "header";

    /** The xdocs resource directory for RegexpHeader examples. */
    private static final String REGEXP_HEADER_RESOURCE_DIR = "regexpheader";

    /** The xdocs resource directory for MultiFileRegexpHeader examples. */
    private static final String MF_REGEXP_HEADER_DIR = "multifileregexpheader";

    /** Separator for xdocs header resource references. */
    private static final String RESOURCE_SEPARATOR = "/";

    /** The source path fragment for xdocs header check resources. */
    private static final String CHECKS_HEADER_PATH = "checks/header";

    /** Module names to use for xdocs header example source paths. */
    private static final Map<String, String> MODULE_BY_SOURCE_PATH = Map.of(
            getHeaderRef(CHECKS_HEADER_PATH, HEADER_RESOURCE_DIR), HEADER_MODULE,
            getHeaderRef(CHECKS_HEADER_PATH, REGEXP_HEADER_RESOURCE_DIR), REGEXP_HEADER_MODULE,
            getHeaderRef(CHECKS_HEADER_PATH, MF_REGEXP_HEADER_DIR), MF_REGEXP_HEADER_MODULE
    );

    /** Header files to copy for individual generated example folders. */
    private static final Map<String, List<String>> HEADER_FILES_BY_OUTPUT_PATH = Map.ofEntries(
            Map.entry(getHeaderKey(HEADER_MODULE, EXAMPLE2_DIR),
                    List.of(getHeaderRef(HEADER_RESOURCE_DIR, JAVA_HEADER_FILENAME))),
            Map.entry(getHeaderKey(HEADER_MODULE, EXAMPLE4_DIR),
                    List.of(getHeaderRef(HEADER_RESOURCE_DIR, JAVA_HEADER_FILENAME))),
            Map.entry(getHeaderKey(REGEXP_HEADER_MODULE, EXAMPLE2_DIR),
                    List.of(getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, JAVA_HEADER_FILENAME))),
            Map.entry(getHeaderKey(REGEXP_HEADER_MODULE, EXAMPLE3_DIR),
                    List.of(getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, COPYRIGHT_HEADER_FILENAME))),
            Map.entry(getHeaderKey(REGEXP_HEADER_MODULE, EXAMPLE4_DIR),
                    List.of(getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, UNIVERSAL_HEADER_FILENAME))),
            Map.entry(getHeaderKey(MF_REGEXP_HEADER_MODULE, EXAMPLE2_DIR),
                    List.of(getHeaderRef(MF_REGEXP_HEADER_DIR, JAVA_HEADER_FILENAME),
                            getHeaderRef(MF_REGEXP_HEADER_DIR, APACHE_HEADER_FILENAME))),
            Map.entry(getHeaderKey(MF_REGEXP_HEADER_MODULE, EXAMPLE3_DIR),
                    List.of(getHeaderRef(MF_REGEXP_HEADER_DIR, JAVA_HEADER_FILENAME),
                            getHeaderRef(MF_REGEXP_HEADER_DIR, APACHE_HEADER_FILENAME))),
            Map.entry(getHeaderKey(MF_REGEXP_HEADER_MODULE, EXAMPLE4_DIR),
                    List.of(getHeaderRef(MF_REGEXP_HEADER_DIR, UNIVERSAL_HEADER_FILENAME)))
    );

    /** Header files to copy for generated all-examples-in-one folders. */
    private static final Map<String, List<String>> ALL_EXAMPLES_HEADER_FILES = Map.of(
            HEADER_MODULE,
            List.of(getHeaderRef(HEADER_RESOURCE_DIR, JAVA_HEADER_FILENAME)),
            REGEXP_HEADER_MODULE,
            List.of(getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, JAVA_HEADER_FILENAME),
                    getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, COPYRIGHT_HEADER_FILENAME),
                    getHeaderRef(REGEXP_HEADER_RESOURCE_DIR, UNIVERSAL_HEADER_FILENAME)),
            MF_REGEXP_HEADER_MODULE,
            List.of(getHeaderRef(MF_REGEXP_HEADER_DIR, JAVA_HEADER_FILENAME),
                    getHeaderRef(MF_REGEXP_HEADER_DIR, APACHE_HEADER_FILENAME),
                    getHeaderRef(MF_REGEXP_HEADER_DIR, UNIVERSAL_HEADER_FILENAME))
    );

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
            final String configOutputPath = args[OUTPUT_FILE_PATH_INDEX];
            final String projectsOutputPath = args[PROJECT_OUTPUT_PATH_INDEX];

            // Process input file and generate config
            processInputFile(Paths.get(inputFilePath), Paths.get(configOutputPath));

            final String projectsResource =
                    YamlParserAndProjectHandler.getDefaultProjectsYamlResource(
                            Paths.get(inputFilePath));
            outputProjectsList(projectsOutputPath, projectsResource);
        }
        else {
            // Functionality: process all examples
            final String checkstyleRepoPath = args[0];
            final List<Path> allExampleDirs = findAllExampleDirs(checkstyleRepoPath);

            final Properties props = System.getProperties();
            props.setProperty("config.folder", "${config.folder}");

            final Map<String, List<Path>> moduleExamples =
                    processExampleDirs(allExampleDirs, checkstyleRepoPath);

            YamlParserAndProjectHandler.processProjectsForExamples(
                    PROJECT_ROOT.toString(),
                    YamlParserAndProjectHandler.getJavadocModuleNames(moduleExamples));

            for (final Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
                generateAllInOneConfig(entry.getKey(), entry.getValue(), checkstyleRepoPath);
                generateReadmes(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Writes the selected projects list to the specified file.
     *
     * @param outputPath the file path to write the list.
     * @param projectsResource the classpath resource to write.
     * @throws IllegalStateException if I/O error occurs while reading or writing to file
     */
    private static void outputProjectsList(final String outputPath,
                                           final String projectsResource) {
        try (InputStream inputStream = CheckstyleExampleExtractor.class
                .getResourceAsStream(projectsResource);
             OutputStream outputStream = Files.newOutputStream(Path.of(outputPath))) {

            final byte[] buffer = new byte[BUFFER_SIZE];
            int length = inputStream.read(buffer);
            while (length > 0) {
                outputStream.write(buffer, 0, length);
                length = inputStream.read(buffer);
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("Error outputting default projects list", ex);
        }
    }

    /**
     * Processes an input file and generates an output file.
     *
     * @param inputFile  The path to the input file
     * @param outputFile The path to the output file
     * @throws Exception If an error occurs during processing
     * @throws IllegalArgumentException if the argument is invalid.
     * @throws IOException if resource not found
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

        // Parse the input file to determine if it's a TreeWalker check
        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parse(inputFile.toString());
        final List<ModuleInputConfiguration> modules =
                testInputConfiguration.getChildrenModules();

        if (modules.isEmpty()) {
            throw new IllegalArgumentException("No modules found in the input file");
        }

        final ModuleInputConfiguration mainModule = modules.get(0);
        final String moduleName = mainModule.getModuleName();
        final boolean isTreeWalker = ConfigSerializer.isTreeWalkerCheck(moduleName);

        // Get the template file name based on whether it's a TreeWalker check or not
        final String templateFileName;
        if (isTreeWalker) {
            templateFileName = "config-template-treewalker.xml";
        }
        else {
            templateFileName = "config-template-non-treewalker.xml";
        }

        // Serialize the configuration
        final String generatedContent = ConfigSerializer.serializeNonXmlConfigToString(
                inputFile.toString(),
                templateFileName
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
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return A map associating module names with their example directories.
     * @throws Exception If an unexpected error occurs.
     */
    private static Map<String, List<Path>> processExampleDirs(
            final List<Path> allExampleDirs, final String checkstyleRepoPath)
            throws Exception {
        final Map<String, List<Path>> moduleExamples = new ConcurrentHashMap<>();
        for (final Path dir : allExampleDirs) {
            final String moduleName = processDirectory(dir.toString(), checkstyleRepoPath);
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
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return Module name if processing was successful, null otherwise.
     * @throws Exception If an I/O error occurs
     */
    public static String processDirectory(final String inputDir,
                                          final String checkstyleRepoPath) throws Exception {
        String moduleName = null;

        final Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            final List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                    .collect(Collectors.toList());

            if (!exampleFiles.isEmpty()) {
                final Path firstExampleFile = exampleFiles.get(0);
                moduleName = getModuleName(inputPath, firstExampleFile);
                if (moduleName != null) {
                    final Path outputPath = PROJECT_ROOT.resolve(moduleName);
                    Files.createDirectories(outputPath);

                    for (final Path exampleFile : exampleFiles) {
                        processExampleFile(exampleFile, outputPath, checkstyleRepoPath);
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
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @param outputPath  The path where the processed file's subfolder will be created.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processExampleFile(
            final Path exampleFile,
            final Path outputPath,
            final String checkstyleRepoPath)
            throws Exception {
        final Path fileName = exampleFile.getFileName();
        if (fileName != null) {
            final String fileNameStr = fileName.toString().replaceFirst("\\.(java|txt)$", "");
            final Path subfolderPath = outputPath.resolve(fileNameStr);
            Files.createDirectories(subfolderPath);
            processFile(exampleFile.toString(), subfolderPath, checkstyleRepoPath);
        }
    }

    /**
     * Processes an example file and generates its configuration, properties, and README.
     * Also copies any known extra files if present in the same folder.
     *
     * @param exampleFile The path to the example file (.java or .txt).
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @param outputPath  The path where the generated content (config.xml, etc.) will be stored.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processFile(
            final String exampleFile,
            final Path outputPath,
            final String checkstyleRepoPath)
            throws Exception {
        if (exampleFile != null
                && outputPath != null) {
            try {
                final String templateFilePath = getTemplateFilePathForExamples(exampleFile);
                if (templateFilePath != null) {
                    final String generatedContent =
                            ConfigSerializer.serializeConfigToString(exampleFile, templateFilePath);
                    writeConfigFile(outputPath, generatedContent);
                    copyProjectFiles(outputPath,
                            YamlParserAndProjectHandler.isJavadocExamplePath(
                                    Paths.get(exampleFile)));
                    generateReadme(outputPath);
                    handleHeaderFileIfNeeded(outputPath, checkstyleRepoPath);
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
     * Gets module name from hardcoded path mapping or from example file config.
     *
     * @param inputPath The path to the example directory.
     * @param firstExampleFile The first example file in the example directory.
     * @return The module name for the example directory.
     * @throws Exception if an unexpected error occurs.
     */
    private static String getModuleName(final Path inputPath,
                                        final Path firstExampleFile)
            throws Exception {
        final String inputPathString = inputPath.toString();
        final Optional<String> moduleName = MODULE_BY_SOURCE_PATH.entrySet().stream()
                .filter(entry -> inputPathString.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();

        final String result;
        if (moduleName.isPresent()) {
            result = moduleName.get();
        }
        else {
            result = ConfigSerializer.extractModuleName(firstExampleFile.toString());
        }
        return result;
    }

    /**
     * Creates a lookup key for header file mappings.
     *
     * @param moduleName The generated module folder name.
     * @param folderName The generated example folder name.
     * @return A lookup key for header file mappings.
     */
    private static String getHeaderKey(final String moduleName, final String folderName) {
        return moduleName + RESOURCE_SEPARATOR + folderName;
    }

    /**
     * Creates a relative xdocs header resource reference.
     *
     * @param resourceDir The xdocs resource directory.
     * @param headerFileName The header file name.
     * @return A relative xdocs header resource reference.
     */
    private static String getHeaderRef(final String resourceDir,
                                       final String headerFileName) {
        return resourceDir + RESOURCE_SEPARATOR + headerFileName;
    }

    /**
     * Gets the header file name from a relative xdocs header resource reference.
     *
     * @param headerResourcePath The relative xdocs header resource reference.
     * @return The header file name.
     */
    private static String getHeaderFileName(final String headerResourcePath) {
        final int separatorIndex = headerResourcePath.lastIndexOf(RESOURCE_SEPARATOR);
        return headerResourcePath.substring(separatorIndex + 1);
    }

    /**
     * Copies header file from Checkstyle xdocs examples into the output folder.
     *
     * @param outputPath The folder where config.xml is placed.
     * @param checkstyleRepoPath The path to Checkstyle repository.
     * @param headerResourcePath The relative xdocs header resource reference.
     * @throws IOException if an I/O error occurs.
     */
    private static void copyXdocsHeaderIfNeeded(final Path outputPath,
                                                final String checkstyleRepoPath,
                                                final String headerResourcePath)
            throws IOException {
        final Path source = Paths.get(checkstyleRepoPath, XDOCS_HEADER_CHECKS_PATH,
                headerResourcePath);
        final String headerFileName = getHeaderFileName(headerResourcePath);

        if (Files.exists(source)) {
            Files.copy(source,
                    outputPath.resolve(headerFileName),
                    StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Copied " + headerFileName
                    + " from " + source + " to " + outputPath);
        }
        else {
            LOGGER.warning("No " + headerFileName
                    + " found at " + source + ". Skipping.");
        }
    }

    /**
     * Copies xdocs header files into the output folder.
     *
     * @param outputPath The folder where config.xml is placed.
     * @param checkstyleRepoPath The path to Checkstyle repository.
     * @param headerResourcePaths The relative xdocs header resource references.
     * @throws IOException if an I/O error occurs.
     */
    private static void copyXdocsHeadersIfNeeded(final Path outputPath,
                                                 final String checkstyleRepoPath,
                                                 final List<String> headerResourcePaths)
            throws IOException {
        for (final String headerResourcePath : headerResourcePaths) {
            copyXdocsHeaderIfNeeded(outputPath, checkstyleRepoPath, headerResourcePath);
        }
    }

    /**
     * Copies known all-examples-in-one header files if needed.
     *
     * @param moduleName The name of the module.
     * @param outputPath The path where config.xml is placed.
     * @param checkstyleRepoPath The path to Checkstyle repository.
     * @throws IOException if an I/O error occurs.
     */
    private static void handleAllInOneHeaderFilesIfNeeded(final String moduleName,
                                                          final Path outputPath,
                                                          final String checkstyleRepoPath)
            throws IOException {
        copyXdocsHeadersIfNeeded(outputPath, checkstyleRepoPath,
                ALL_EXAMPLES_HEADER_FILES.getOrDefault(moduleName, Collections.emptyList()));
    }

    /**
     * Checks if the output path requires a java.header file and copies it if needed.
     *
     * @param outputPath The path where config.xml is placed.
     * @param checkstyleRepoPath The path to Checkstyle repository.
     * @throws IOException if an I/O error occurs.
     */
    private static void handleHeaderFileIfNeeded(final Path outputPath,
                                                 final String checkstyleRepoPath)
            throws IOException {
        final Path parentDir = outputPath.getParent();
        final String parentName = Optional.ofNullable(parentDir)
                .map(Path::getFileName)
                .map(Path::toString)
                .orElse("");

        final String folderName = Optional.ofNullable(outputPath.getFileName())
                .map(Path::toString)
                .orElse("");

        final String headerKey = getHeaderKey(parentName, folderName);
        copyXdocsHeadersIfNeeded(outputPath, checkstyleRepoPath,
                HEADER_FILES_BY_OUTPUT_PATH.getOrDefault(headerKey, Collections.emptyList()));
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
     * Copies the project files to the specified output path.
     *
     * @param outputPath The path where project files will be copied.
     * @param javadocModule whether to use javadoc-specific project lists.
     * @throws IOException If an I/O error occurs.
     */
    private static void copyProjectFiles(final Path outputPath, final boolean javadocModule)
            throws IOException {
        final Path sourceYamlPath =
                YamlParserAndProjectHandler.getDefaultProjectsYamlPath(javadocModule);
        final Path sourcePropertiesPath =
                YamlParserAndProjectHandler.getDefaultProjectsPropertiesPath(javadocModule);

        if (Files.exists(sourceYamlPath)) {
            final Path targetYamlPath = outputPath.resolve("list-of-projects.yml");
            Files.copy(sourceYamlPath, targetYamlPath, StandardCopyOption.REPLACE_EXISTING);
        }

        if (Files.exists(sourcePropertiesPath)) {
            final Path targetPropertiesPath = outputPath.resolve("list-of-projects.properties");
            Files.copy(sourcePropertiesPath,
                    targetPropertiesPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
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
     * @param exampleDirs List of example directories.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @throws Exception If an I/O error occurs during generation.
     */
    public static void generateAllInOneConfig(
            final String moduleName,
            final List<Path> exampleDirs,
            final String checkstyleRepoPath)
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
            handleAllExamplesInOne(moduleName, allInOneSubfolderPath,
                    checkstyleRepoPath, YamlParserAndProjectHandler.isJavadocModule(exampleDirs));
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
     * @param moduleName The name of the module.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @param allInOneSubfolderPath  The path to the "all-examples-in-one" subfolder.
     * @param javadocModule whether to use javadoc-specific project lists.
     */
    private static void handleAllExamplesInOne(
            final String moduleName,
            final Path allInOneSubfolderPath,
            final String checkstyleRepoPath,
            final boolean javadocModule) {
        try {
            final Map<String, Object> yamlData = YamlParserAndProjectHandler.parseYamlFile();
            final Map<String, Object> moduleConfig = (Map<String, Object>) yamlData.get(moduleName);

            if (moduleConfig != null && moduleConfig.containsKey(ALL_IN_ONE_SUBFOLDER)) {
                final Map<String, Object> allInOneConfig =
                        (Map<String, Object>) moduleConfig.get(ALL_IN_ONE_SUBFOLDER);
                final List<String> projectNames =
                        (List<String>) allInOneConfig.get("projects");

                final List<Map<String, Object>> projectData =
                        YamlParserAndProjectHandler.loadProjectDataForModule(javadocModule);
                final String yamlSourceName =
                        YamlParserAndProjectHandler.getProjectDataSourceName(javadocModule);

                // Generate YAML projects file
                YamlParserAndProjectHandler.createProjectsYmlFileForExample(
                        allInOneSubfolderPath,
                        projectNames,
                        projectData,
                        moduleName,
                        yamlSourceName
                );

                final List<String> projectLines =
                        YamlParserAndProjectHandler.loadProjectPropertiesForModule(javadocModule);

                // Generate properties projects file
                YamlParserAndProjectHandler.createProjectsPropertiesFileForExample(
                        allInOneSubfolderPath,
                        projectNames,
                        projectLines,
                        moduleName
                );

                handleAllInOneHeaderFilesIfNeeded(moduleName,
                        allInOneSubfolderPath, checkstyleRepoPath);
            }
            else {
                // Copy default properties and YAML files
                copyDefaultPropertiesFile(allInOneSubfolderPath, javadocModule);
                copyDefaultYamlFile(allInOneSubfolderPath, javadocModule);

                handleAllInOneHeaderFilesIfNeeded(moduleName,
                        allInOneSubfolderPath, checkstyleRepoPath);
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "Error processing YAML file for all-examples-in-one: "
                            + ex.getMessage(), ex);
            copyDefaultPropertiesFile(allInOneSubfolderPath, javadocModule);
            copyDefaultYamlFile(allInOneSubfolderPath, javadocModule);
        }
    }

    /**
     * Copies the default project properties file to the specified subfolder.
     *
     * @param allInOneSubfolderPath The path where the properties file will be copied.
     * @param javadocModule whether to use javadoc-specific project lists.
     */
    private static void copyDefaultPropertiesFile(final Path allInOneSubfolderPath,
                                                  final boolean javadocModule) {
        try {
            final Path sourcePropertiesPath =
                    YamlParserAndProjectHandler.getDefaultProjectsPropertiesPath(javadocModule);
            final Path targetPropertiesPath =
                    allInOneSubfolderPath.resolve(PROJ_PROP_FILENAME);
            Files.copy(sourcePropertiesPath,
                    targetPropertiesPath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Error copying default properties file", ex);
        }
    }

    /**
     * Copies the default YAML file to the specified subfolder path.
     *
     * @param allInOneSubfolderPath the target directory to copy the YAML file into.
     * @param javadocModule whether to use javadoc-specific project lists.
     */
    private static void copyDefaultYamlFile(final Path allInOneSubfolderPath,
                                            final boolean javadocModule) {
        try {
            final Path sourceYamlPath = YamlParserAndProjectHandler.getDefaultProjectsYamlPath(
                    javadocModule);
            final Path targetYamlPath = allInOneSubfolderPath.resolve(PROJ_YML_PROP_FILENAME);
            Files.copy(sourceYamlPath, targetYamlPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "Error copying default YAML file", ex);
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
        final Configuration xmlConfig = ConfigSerializer.loadXmlConfiguration(exampleFilePath);
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
        final Matcher matcher =
            Pattern.compile("(?:Example|UseCase)(\\d+)\\.(java|txt)").matcher(filename);
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
