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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.ModuleInputConfiguration;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

/**
 * CheckstyleExampleExtractor class for extracting and processing Checkstyle examples.
 *
 * <p>This class is the CLI entry point and orchestrator for the example-extraction
 * pipeline: it walks the Checkstyle repository for example directories, generates
 * per-example {@code config.xml} / README files, and delegates the header-copying and
 * "all-in-one" generation work to {@link ExampleGenerationSupport} to keep this class's
 * own object coupling low (PMD CouplingBetweenObjects).
 */
public final class CheckstyleExampleExtractor {

    /** Logger for this class. */
    private static final Logger LOGGER =
            Logger.getLogger(CheckstyleExampleExtractor.class.getName());

    /** The root directory of the project. */
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();

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
    // -@cs[UncommentedMain] CLI entry point.
    public static void main(final String[] args) throws Exception {
        // Entry point for the extractor CLI.
        if (args.length < 1) {
            throw new IllegalArgumentException(
                    "Usage: <checkstyle repo path> [--input-file <config content> "
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
            final List<Path> allExampleDirs =
                    ExampleGenerationSupport.findAllExampleDirs(checkstyleRepoPath);

            final Properties props = System.getProperties();
            props.setProperty("config.folder", "${config.folder}");

            final Map<String, List<Path>> moduleExamples =
                    ExampleGenerationSupport.processExampleDirs(allExampleDirs, checkstyleRepoPath);

            YamlParserAndProjectHandler.processProjectsForExamples(
                    PROJECT_ROOT.toString(),
                    YamlParserAndProjectHandler.getJavadocModuleNames(moduleExamples));

            for (final Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
                ExampleGenerationSupport.generateAllInOneConfig(new AllInOneGenerationRequest(
                        entry.getKey(), entry.getValue(), checkstyleRepoPath,
                        ExampleGenerationSupport.EXAMPLE_ONLY_FILE_PATTERN,
                        ExampleGenerationSupport.ALL_IN_ONE_SUBFOLDER));
                ExampleGenerationSupport.generateAllInOneConfig(new AllInOneGenerationRequest(
                        entry.getKey(), entry.getValue(), checkstyleRepoPath,
                        ExampleGenerationSupport.USECASE_ONLY_FILE_PATTERN,
                        ExampleGenerationSupport.ALL_USECASES_IN_ONE_SUBFOLDER));
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
    private static void outputProjectsList(final String outputPath, final String projectsResource) {
        try (InputStream inputStream =
                     CheckstyleExampleExtractor.class.getResourceAsStream(projectsResource);
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
     * @param inputFile The path to the input file
     * @param outputFile The path to the output file
     * @throws Exception If an error occurs during processing
     * @throws IllegalArgumentException if the argument is invalid.
     * @throws IOException if resource not found
     */
    public static void processInputFile(final Path inputFile, final Path outputFile)
            throws Exception {
        if (!Files.exists(inputFile)) {
            LOGGER.severe("Input file does not exist: " + inputFile);
            throw new IOException("Input file does not exist: " + inputFile);
        }

        final TestInputConfiguration testInputConfiguration =
                InlineConfigParser.parse(inputFile.toString());
        final List<ModuleInputConfiguration> modules = testInputConfiguration.getChildrenModules();

        if (modules.isEmpty()) {
            throw new IllegalArgumentException("No modules found in the input file");
        }

        final ModuleInputConfiguration mainModule = modules.get(0);
        final String moduleName = mainModule.getModuleName();
        final boolean isTreeWalker = ConfigSerializer.isTreeWalkerCheck(moduleName);

        final String templateFileName;
        if (isTreeWalker) {
            templateFileName = "config-template-treewalker.xml";
        }
        else {
            templateFileName = "config-template-non-treewalker.xml";
        }

        final String generatedContent =
                ConfigSerializer.serializeNonXmlConfigToString(
                        inputFile.toString(), templateFileName);

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
        final List<ModuleInputConfiguration> modules = testInputConfiguration.getChildrenModules();

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
     * Process a directory containing example files.
     *
     * @param inputDir Input directory path
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return Module name if processing was successful, null otherwise.
     * @throws Exception If an I/O error occurs
     */
    public static String processDirectory(final String inputDir, final String checkstyleRepoPath)
            throws Exception {
        String moduleName = null;

        final Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            final List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        return path.getFileName().toString()
                                .matches(ExampleGenerationSupport.EXAMPLE_FILE_PATTERN);
                    })
                    .collect(Collectors.toList());

            if (!exampleFiles.isEmpty()) {
                final Path firstExampleFile = exampleFiles.get(0);
                moduleName = ExampleGenerationSupport.getModuleName(inputPath, firstExampleFile);
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
     * @param outputPath The path where the processed file's subfolder will be created.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processExampleFile(final Path exampleFile, final Path outputPath,
                                           final String checkstyleRepoPath) throws Exception {
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
     * @param outputPath The path where the generated content (config.xml, etc.) will be stored.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @throws Exception If an unexpected error occurs.
     */
    private static void processFile(final String exampleFile, final Path outputPath,
                                    final String checkstyleRepoPath) throws Exception {
        if (exampleFile != null && outputPath != null) {
            try {
                final String templateFilePath = getTemplateFilePathForExamples(exampleFile);
                final String generatedContent =
                        ConfigSerializer.serializeConfigToString(
                                exampleFile, templateFilePath);
                writeConfigFile(outputPath, generatedContent);
                copyProjectFiles(outputPath,
                        YamlParserAndProjectHandler.isJavadocExamplePath(
                                Paths.get(exampleFile)));
                generateReadme(outputPath);
                ExampleGenerationSupport.handleHeaderFileIfNeeded(
                        outputPath, checkstyleRepoPath);
            }
            catch (IOException ex) {
                LOGGER.log(Level.SEVERE,
                        "Error reading or processing the file: " + exampleFile, ex);
            }
        }
    }

    /**
     * Writes the serialized configuration content to a config.xml.
     *
     * @param outputPath The path where the config.xml file will be created.
     * @param content The serialized configuration content to write.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeConfigFile(final Path outputPath, final String content)
            throws IOException {
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
            Files.copy(sourcePropertiesPath, targetPropertiesPath,
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
     * Generates README files for each example in the specified directories.
     *
     * @param moduleName The name of the module.
     * @param exampleDirs The directories containing the examples.
     * @throws IOException If an I/O error occurs during README generation.
     */
    private static void generateReadmes(final String moduleName, final List<Path> exampleDirs)
            throws IOException {
        final Path outputPath = PROJECT_ROOT.resolve(moduleName);

        for (final Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            return path.getFileName().toString()
                                    .matches(ExampleGenerationSupport.EXAMPLE_FILE_PATTERN);
                        })
                        .forEach(exampleFile -> {
                            generateIndividualReadme(exampleFile, outputPath, moduleName);
                        });
            }
        }

        final Path allExamplesSubfolderPath =
                outputPath.resolve(ExampleGenerationSupport.ALL_IN_ONE_SUBFOLDER);
        ExampleGenerationSupport.generateAllInOneReadme(
                allExamplesSubfolderPath, moduleName,
                ExampleGenerationSupport.ALL_IN_ONE_SUBFOLDER);

        final Path allUseCasesSubfolderPath =
                outputPath.resolve(ExampleGenerationSupport.ALL_USECASES_IN_ONE_SUBFOLDER);
        if (Files.exists(allUseCasesSubfolderPath)) {
            ExampleGenerationSupport.generateAllInOneReadme(allUseCasesSubfolderPath, moduleName,
                    ExampleGenerationSupport.ALL_USECASES_IN_ONE_SUBFOLDER);
        }
    }

    /**
     * Generates a README file for an individual example.
     *
     * @param exampleFile The path to the example file.
     * @param outputPath The path where the README file will be generated.
     * @param moduleName The name of the module.
     */
    private static void generateIndividualReadme(final Path exampleFile, final Path outputPath,
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

        final boolean hasFileName = Optional.ofNullable(exampleFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .isPresent();
        if (!hasFileName) {
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
    public static String getTemplateFilePathForExamples(final String exampleFilePath)
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
}
