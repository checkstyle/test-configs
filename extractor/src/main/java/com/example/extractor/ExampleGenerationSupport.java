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

/**
 * Support class for {@link CheckstyleExampleExtractor}. Handles directory scanning,
 * xdocs header-file copying, and generation of "all-examples-in-one" /
 * "all-usecases-in-one" configurations, keeping this logic out of the main
 * orchestration class to reduce its object coupling (PMD CouplingBetweenObjects).
 */
public final class ExampleGenerationSupport {

    /** The regular expression pattern for example files (Example and UseCase files). */
    public static final String EXAMPLE_FILE_PATTERN = "(Example|UseCase)\\d+\\.(java|txt)";

    /** The regular expression pattern matching ONLY Example files. */
    public static final String EXAMPLE_ONLY_FILE_PATTERN = "Example\\d+\\.(java|txt)";

    /** The regular expression pattern matching ONLY UseCase files. */
    public static final String USECASE_ONLY_FILE_PATTERN = "UseCase\\d+\\.(java|txt)";

    /** The subfolder name for all-in-one examples. */
    public static final String ALL_IN_ONE_SUBFOLDER = "all-examples-in-one";

    /** The subfolder name for all-in-one use cases. */
    public static final String ALL_USECASES_IN_ONE_SUBFOLDER = "all-usecases-in-one";

    /** Logger for this class. */
    private static final Logger LOGGER =
            Logger.getLogger(ExampleGenerationSupport.class.getName());

    /** The root directory of the project. */
    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();

    /** The filename for project properties. */
    private static final String PROJ_PROP_FILENAME = "list-of-projects.properties";

    /** The filename for project yml. */
    private static final String PROJ_YML_PROP_FILENAME = "list-of-projects.yml";

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
    private ExampleGenerationSupport() {
        // Utility class, no instances
    }

    /**
     * Retrieves all example directories within the specified Checkstyle repository path.
     *
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return A list of paths to the example directories.
     * @throws IOException If an I/O error occurs.
     */
    public static List<Path> findAllExampleDirs(final String checkstyleRepoPath)
            throws IOException {
        final List<Path> allExampleDirs = new ArrayList<>();
        allExampleDirs.addAll(
                findNonFilterExampleDirs(
                        Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources")));
        allExampleDirs.addAll(
                findNonFilterExampleDirs(
                        Paths.get(checkstyleRepoPath, "src", "xdocs-examples",
                                "resources-noncompilable")));
        return allExampleDirs;
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
                    .filter(path -> {
                        return !path.toString().contains("suppresswarningsholder");
                    })
                    .filter(path -> {
                        return !path.toString().contains("filters")
                                && !path.toString().contains("filfilters");
                    })
                    .filter(ExampleGenerationSupport::containsExampleFile)
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
     * Processes example directories to map module names to their corresponding directories.
     *
     * @param allExampleDirs A list of paths to example directories.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @return A map associating module names with their example directories.
     * @throws Exception If an unexpected error occurs.
     */
    public static Map<String, List<Path>> processExampleDirs(
            final List<Path> allExampleDirs, final String checkstyleRepoPath) throws Exception {
        final Map<String, List<Path>> moduleExamples = new ConcurrentHashMap<>();
        for (final Path dir : allExampleDirs) {
            final String moduleName =
                    CheckstyleExampleExtractor.processDirectory(
                            dir.toString(), checkstyleRepoPath);
            if (moduleName != null) {
                moduleExamples.computeIfAbsent(moduleName, key -> {
                    return new ArrayList<>();
                }).add(dir);
            }
        }
        return moduleExamples;
    }

    /**
     * Gets module name from hardcoded path mapping or from example file config.
     *
     * @param inputPath The path to the example directory.
     * @param firstExampleFile The first example file in the example directory.
     * @return The module name for the example directory.
     * @throws Exception if an unexpected error occurs.
     */
    public static String getModuleName(final Path inputPath, final Path firstExampleFile)
            throws Exception {
        final String inputPathString = inputPath.toString().replace('\\', '/');
        final Optional<String> moduleName = MODULE_BY_SOURCE_PATH.entrySet().stream()
                .filter(entry -> {
                    return inputPathString.contains(entry.getKey());
                })
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
    private static String getHeaderRef(final String resourceDir, final String headerFileName) {
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
    public static void handleHeaderFileIfNeeded(final Path outputPath,
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
     * Generate all-in-one configuration for a module, restricted to files matching the
     * given file pattern (e.g. only Example files, or only UseCase files), and written
     * into the given subfolder (e.g. "all-examples-in-one" or "all-usecases-in-one").
     *
     * @param request The parameters describing what to generate and where.
     * @throws Exception If an I/O error occurs during generation.
     */
    public static void generateAllInOneConfig(final AllInOneGenerationRequest request)
            throws Exception {
        final List<String> allExampleFiles =
                getAllExampleFiles(request.exampleDirs(), request.filePattern());
        final boolean shouldProceed = !allExampleFiles.isEmpty();

        if (shouldProceed) {
            Collections.sort(
                    allExampleFiles,
                    Comparator.comparingInt(ExampleGenerationSupport::extractExampleNumber)
            );

            final Path outputPath = PROJECT_ROOT.resolve(request.moduleName());
            final Path allInOneSubfolderPath = outputPath.resolve(request.subfolderName());
            Files.createDirectories(allInOneSubfolderPath);

            final String idPrefix = getIdPrefixForSubfolder(request.subfolderName());
            generateAllInOneContent(allExampleFiles, allInOneSubfolderPath, idPrefix);
            handleAllExamplesInOne(request.moduleName(), allInOneSubfolderPath,
                    request.checkstyleRepoPath(),
                    YamlParserAndProjectHandler.isJavadocModule(request.exampleDirs()),
                    request.subfolderName());
            generateAllInOneReadme(allInOneSubfolderPath, request.moduleName(),
                    request.subfolderName());
        }
    }

    /**
     * Determines the id prefix to use for module ids in the generated all-in-one config,
     * based on which all-in-one subfolder is being generated.
     *
     * @param subfolderName The name of the all-in-one subfolder
     *                       (e.g. "all-examples-in-one" or "all-usecases-in-one").
     * @return "example" for all-examples-in-one, "usecase" for all-usecases-in-one
     *         (and any other subfolder name).
     */
    private static String getIdPrefixForSubfolder(final String subfolderName) {
        final String idPrefix;
        if (ALL_IN_ONE_SUBFOLDER.equals(subfolderName)) {
            idPrefix = "example";
        }
        else {
            idPrefix = "usecase";
        }
        return idPrefix;
    }

    /**
     * Retrieves all example files from the provided directories that match the given
     * filename pattern.
     *
     * @param exampleDirs The directories to search for example files.
     * @param filePattern The filename pattern used to select which files to include.
     * @return A list of paths to the example files.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static List<String> getAllExampleFiles(final List<Path> exampleDirs,
                                                   final String filePattern)
            throws IOException {
        final List<String> allExampleFiles = new ArrayList<>();
        for (final Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches(filePattern))
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
     * @param idPrefix The prefix to use when assigning ids to each combined module
     *                 (e.g. "example" for all-examples-in-one, "usecase" for
     *                 all-usecases-in-one).
     * @throws Exception If an unexpected error occurs during generation.
     */
    private static void generateAllInOneContent(
            final List<String> allExampleFiles,
            final Path allInOneSubfolderPath,
            final String idPrefix)
            throws Exception {
        final String templateFilePath =
                CheckstyleExampleExtractor.getTemplateFilePathForExamples(allExampleFiles.get(0));
        final Path outputFilePath = allInOneSubfolderPath.resolve("config.xml");

        final String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                allExampleFiles.toArray(new String[0]), templateFilePath, idPrefix);
        Files.writeString(outputFilePath, generatedContent);
    }

    /**
     * Handles the creation and copying of project files for an "all-in-one" case
     * (either "all-examples-in-one" or "all-usecases-in-one").
     *
     * @param moduleName The name of the module.
     * @param allInOneSubfolderPath  The path to the "all-in-one" subfolder.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @param javadocModule whether to use javadoc-specific project lists.
     * @param subfolderName The name of the "all-in-one" subfolder
     *                       (e.g. "all-examples-in-one" or "all-usecases-in-one"),
     *                       used as the lookup key into the projects YAML.
     */
    @SuppressWarnings("unchecked")
    private static void handleAllExamplesInOne(
            final String moduleName,
            final Path allInOneSubfolderPath,
            final String checkstyleRepoPath,
            final boolean javadocModule,
            final String subfolderName) {
        try {
            final Map<String, Object> yamlData = YamlParserAndProjectHandler.parseYamlFile();
            final Map<String, Object> moduleConfig =
                    (Map<String, Object>) yamlData.get(moduleName);

            if (moduleConfig != null && moduleConfig.containsKey(subfolderName)) {
                final Map<String, Object> allInOneConfig =
                        (Map<String, Object>) moduleConfig.get(subfolderName);
                final List<String> projectNames =
                        (List<String>) allInOneConfig.get("projects");

                final List<Map<String, Object>> projectData =
                        YamlParserAndProjectHandler.loadProjectDataForModule(javadocModule);
                final String yamlSourceName =
                        YamlParserAndProjectHandler.getProjectDataSourceName(javadocModule);

                YamlParserAndProjectHandler.createProjectsYmlFileForExample(
                        allInOneSubfolderPath,
                        projectNames,
                        projectData,
                        moduleName,
                        yamlSourceName
                );

                final List<String> projectLines =
                        YamlParserAndProjectHandler.loadProjectPropertiesForModule(javadocModule);

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
                copyDefaultPropertiesFile(allInOneSubfolderPath, javadocModule);
                copyDefaultYamlFile(allInOneSubfolderPath, javadocModule);
                handleAllInOneHeaderFilesIfNeeded(moduleName,
                        allInOneSubfolderPath, checkstyleRepoPath);
            }
        }
        catch (IOException ex) {
            LOGGER.log(Level.SEVERE,
                    "Error processing YAML file for " + subfolderName + ": "
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
     * Generates a README file for the "all-in-one" case.
     *
     * @param allInOneSubfolderPath The path where the README file will be generated.
     * @param moduleName            The name of the module.
     * @param subfolderName         The name of the all-in-one subfolder
     *                              (e.g. "all-examples-in-one" or "all-usecases-in-one").
     * @throws IOException If an I/O error occurs during README generation.
     */
    public static void generateAllInOneReadme(
            final Path allInOneSubfolderPath,
            final String moduleName,
            final String subfolderName)
            throws IOException {
        ReadmeGenerator.generateAllInOneReadme(allInOneSubfolderPath, moduleName,
                subfolderName, ReadmeGenerator.getAllInOneTitle(subfolderName));
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
