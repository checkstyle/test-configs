package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import javax.naming.ConfigurationException;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * CheckstyleExampleExtractor class for extracting and processing Checkstyle examples.
 */
public final class CheckstyleExampleExtractor {
    /** Logger for this class. */
    private static final Logger LOGGER = Logger.getLogger(CheckstyleExampleExtractor.class.getName());

    /** The root directory of the project. */
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();

    /** The filename for project properties. */
    private static final String PROJECT_PROPERTIES_FILENAME = "list-of-projects.properties";

    /** The file path for project properties. */
    private static final String PROJECT_PROPERTIES_FILE_PATH = "src/main/resources/" + PROJECT_PROPERTIES_FILENAME;

    /** The regular expression pattern for excluded file paths. */
    private static final String EXCLUDED_FILE_PATTERN =
            "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/regexp/regexpmultiline/Example7.txt";

    /** The regular expression pattern for example files. */
    private static final String EXAMPLE_FILE_PATTERN = "Example\\d+\\.(java|txt)";

    /** The subfolder name for all-in-one examples. */
    private static final String ALL_IN_ONE_SUBFOLDER = "all-examples-in-one";

    private CheckstyleExampleExtractor() {
        // Utility class, no instances
    }

    /**
     * Main method to process Checkstyle examples.
     *
     * @param args Command line arguments
     * @throws Exception If an error occurs during processing
     */
    public static void main(final String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Base path for Checkstyle repo must be provided as argument.");
        }

        final String checkstyleRepoPath = args[0];
        final List<Path> allExampleDirs = findAllExampleDirs(checkstyleRepoPath);
        final Map<String, List<Path>> moduleExamples = processExampleDirs(allExampleDirs);

        YamlParserAndProjectHandler.processProjectsForExamples(PROJECT_ROOT.toString());

        for (final Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
            generateAllInOneConfig(entry.getKey(), entry.getValue());
            generateReadmes(entry.getKey(), entry.getValue());
        }
    }

    private static List<Path> findAllExampleDirs(final String checkstyleRepoPath) throws IOException {
        final List<Path> allExampleDirs = new ArrayList<>();
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources")));
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources-noncompilable")));
        return allExampleDirs;
    }

    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static Map<String, List<Path>> processExampleDirs(final List<Path> allExampleDirs) {
        final Map<String, List<Path>> moduleExamples = new ConcurrentHashMap<>();
        for (final Path dir : allExampleDirs) {
            try {
                final String moduleName = processDirectory(dir.toString());
                if (moduleName != null) {
                    moduleExamples.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(dir);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "I/O Error processing directory: " + dir, e);
            } catch (ConfigurationException e) {
                LOGGER.log(Level.SEVERE, "Configuration error processing directory: " + dir, e);
            } catch (Exception e) { // Fallback for any other unexpected exceptions
                LOGGER.log(Level.SEVERE, "Unexpected error processing directory: " + dir, e);
            }
        }
        return moduleExamples;
    }

    private static List<Path> findNonFilterExampleDirs(final Path basePath) throws IOException {
        try (Stream<Path> pathStream = Files.walk(basePath)) {
            return pathStream
                    .filter(Files::isDirectory)
                    .filter(path -> !path.toString().contains("suppresswarningsholder"))
                    .filter(path -> !path.toString().contains("filters") && !path.toString().contains("filfilters"))
                    .filter(CheckstyleExampleExtractor::containsExampleFile)
                    .collect(Collectors.toList());
        }
    }

    private static boolean containsExampleFile(final Path path) {
        try (Stream<Path> files = Files.list(path)) {
            return files.anyMatch(file -> file.getFileName().toString().matches(EXAMPLE_FILE_PATTERN));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error listing files in directory: " + path, e);
            return false;
        }
    }

    /**
     * Process a directory containing example files.
     *
     * @param inputDir Input directory path
     * @return Module name if processing was successful, null otherwise
     * @throws Exception If an I/O error occurs
     */
    public static String processDirectory(final String inputDir) throws Exception {
        final Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            final List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                    .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATTERN))
                    .collect(Collectors.toList());

            if (exampleFiles.isEmpty()) {
                return null;
            }

            final Path firstExampleFile = exampleFiles.get(0);
            final String moduleName = ConfigSerializer.extractModuleName(firstExampleFile.toString());
            if (moduleName == null) {
                return null;
            }

            final Path outputPath = PROJECT_ROOT.resolve(moduleName);
            Files.createDirectories(outputPath);

            for (final Path exampleFile : exampleFiles) {
                processExampleFile(exampleFile, outputPath);
            }

            return moduleName;
        }
    }

    private static void processExampleFile(final Path exampleFile, final Path outputPath) throws Exception {
        final Path fileName = exampleFile.getFileName();
        if (fileName != null) {
            final String fileNameStr = fileName.toString().replaceFirst("\\.(java|txt)$", "");
            final Path subfolderPath = outputPath.resolve(fileNameStr);
            Files.createDirectories(subfolderPath);
            processFile(exampleFile.toString(), subfolderPath);
        }
    }

    private static void processFile(final String exampleFile, final Path outputPath) throws Exception {
        if (exampleFile == null || outputPath == null || exampleFile.endsWith(EXCLUDED_FILE_PATTERN)) {
            return;
        }

        try {
            final String templateFilePath = getTemplateFilePath(exampleFile);
            if (templateFilePath == null) {
                LOGGER.log(Level.WARNING, "Unable to get template file path for: " + exampleFile);
                return;
            }

            final String generatedContent = ConfigSerializer.serializeConfigToString(exampleFile, templateFilePath);
            writeConfigFile(outputPath, generatedContent);
            copyPropertiesFile(outputPath);
            generateReadme(outputPath);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading or processing the file: " + exampleFile, e);
        }
    }

    private static void writeConfigFile(final Path outputPath, final String content) throws IOException {
        final Path outputFilePath = outputPath.resolve("config.xml");
        Files.writeString(outputFilePath, content, StandardCharsets.UTF_8);
    }

    private static void copyPropertiesFile(final Path outputPath) throws IOException {
        final Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
        final Path targetPropertiesPath = outputPath.resolve(PROJECT_PROPERTIES_FILENAME);
        Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void generateReadme(final Path outputPath) {
        final Path parentPath = outputPath.getParent();
        if (parentPath != null) {
            final Path moduleNamePath = parentPath.getFileName();
            if (moduleNamePath != null) {
                final String moduleName = moduleNamePath.toString();
                try {
                    ReadmeGenerator.generateIndividualReadme(outputPath, moduleName);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error generating README for: " + outputPath, e);
                }
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
    public static void generateAllInOneConfig(final String moduleName, final List<Path> exampleDirs) throws Exception {
        final List<String> allExampleFiles = getAllExampleFiles(exampleDirs);
        if (allExampleFiles.isEmpty()) {
            return;
        }

        Collections.sort(allExampleFiles, Comparator.comparingInt(CheckstyleExampleExtractor::extractExampleNumber));

        final Path outputPath = PROJECT_ROOT.resolve(moduleName);
        final Path allInOneSubfolderPath = outputPath.resolve(ALL_IN_ONE_SUBFOLDER);
        Files.createDirectories(allInOneSubfolderPath);

        generateAllInOneContent(allExampleFiles, allInOneSubfolderPath);
        handleAllExamplesInOne(moduleName, allInOneSubfolderPath);
        generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    private static List<String> getAllExampleFiles(final List<Path> exampleDirs) throws IOException {
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

    private static void generateAllInOneContent(final List<String> allExampleFiles, final Path allInOneSubfolderPath) throws Exception {
        final String templateFilePath = getTemplateFilePath(allExampleFiles.get(0));
        final Path outputFilePath = allInOneSubfolderPath.resolve("config.xml");

        final String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                allExampleFiles.toArray(new String[0]), templateFilePath);
        Files.writeString(outputFilePath, generatedContent);
    }

    private static void handleAllExamplesInOne(final String moduleName, final Path allInOneSubfolderPath) {
        try {
            final Map<String, Object> yamlData = YamlParserAndProjectHandler.parseYamlFile();
            final Map<String, Object> moduleConfig = (Map<String, Object>) yamlData.get(moduleName);

            if (moduleConfig != null && moduleConfig.containsKey(ALL_IN_ONE_SUBFOLDER)) {
                final Map<String, Object> allInOneConfig = (Map<String, Object>) moduleConfig.get(ALL_IN_ONE_SUBFOLDER);
                final List<String> projectNames = (List<String>) allInOneConfig.get("projects");
                YamlParserAndProjectHandler.createProjectsFileForExample(allInOneSubfolderPath, projectNames,
                        Files.readAllLines(Paths.get(YamlParserAndProjectHandler.ALL_PROJECTS_PATH)),
                        moduleName);
            } else {
                copyDefaultPropertiesFile(allInOneSubfolderPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error processing YAML file for all-examples-in-one: " + e.getMessage(), e);
            copyDefaultPropertiesFile(allInOneSubfolderPath);
        }
    }

    private static void copyDefaultPropertiesFile(final Path allInOneSubfolderPath) {
        try {
            final Path sourcePropertiesPath = Paths.get(YamlParserAndProjectHandler.DEFAULT_PROJECTS_PATH).toAbsolutePath();
            final Path targetPropertiesPath = allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
            Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error copying default properties file", e);
        }
    }

    private static void generateAllInOneReadme(final Path allInOneSubfolderPath, final String moduleName) {
        try {
            ReadmeGenerator.generateAllInOneReadme(allInOneSubfolderPath, moduleName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error generating all-in-one README for: " + allInOneSubfolderPath, e);
        }
    }

    private static void generateReadmes(final String moduleName, final List<Path> exampleDirs) throws IOException {
        final Path outputPath = PROJECT_ROOT.resolve(moduleName);

        for (final Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(EXAMPLE_FILE_PATTERN))
                        .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATTERN))
                        .forEach(exampleFile -> generateIndividualReadme(exampleFile, outputPath, moduleName));
            }
        }

        final Path allInOneSubfolderPath = outputPath.resolve(ALL_IN_ONE_SUBFOLDER);
        generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    private static void generateIndividualReadme(final Path exampleFile, final Path outputPath, final String moduleName) {
        Optional.ofNullable(exampleFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(name -> name.replaceFirst("\\.(java|txt)$", ""))
                .ifPresent(fileName -> {
                    final Path subfolderPath = outputPath.resolve(fileName);
                    try {
                        ReadmeGenerator.generateIndividualReadme(subfolderPath, moduleName);
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error generating individual README for: " + subfolderPath, e);
                    }
                });

        if (!Optional.ofNullable(exampleFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .isPresent()) {
            LOGGER.log(Level.WARNING, "Invalid or null file name for: " + exampleFile);
        }
    }

    private static String getTemplateFilePath(final String exampleFilePath) {
        try {
            final TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
            final Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
            final boolean isTreeWalker = ConfigSerializer.isTreeWalkerConfig(xmlConfig);
            final String resourceName = isTreeWalker ? "config-template-treewalker.xml" : "config-template-non-treewalker.xml";
            return ResourceLoader.getResourcePath(resourceName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting template file path for: " + exampleFilePath, e);
            return null;
        }
    }

    private static int extractExampleNumber(final String filename) {
        final Matcher matcher = Pattern.compile("Example(\\d+)\\.(java|txt)").matcher(filename);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : Integer.MAX_VALUE;
    }
}