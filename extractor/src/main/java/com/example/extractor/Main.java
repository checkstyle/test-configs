package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Main {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();
    private static final String PROJECT_PROPERTIES_FILENAME = "list-of-projects.properties";
    private static final String PROJECT_PROPERTIES_FILE_PATH = "src/main/resources/" + PROJECT_PROPERTIES_FILENAME;
    private static final String EXCLUDED_FILE_PATH_REGEXPMULTILINE = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/regexp/regexpmultiline/Example7.txt";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Base path for Checkstyle repo must be provided as argument.");
        }

        String checkstyleRepoPath = args[0];

        // Process compilable and non-compilable paths
        List<Path> allExampleDirs = new ArrayList<>();
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources")));
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources-noncompilable")));

        Map<String, List<Path>> moduleExamples = new HashMap<>();

        // Process all directories
        for (Path dir : allExampleDirs) {
            String moduleName = processDirectory(dir.toString(), PROJECT_ROOT.toString());
            if (moduleName != null) {
                moduleExamples.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(dir);
            }
        }

        // Process projects for examples based on YAML file
        YamlParserAndProjectHandler.processProjectsForExamples(PROJECT_ROOT.toString());

        // Generate all-in-one configs and READMEs for each module
        for (Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
            generateAllInOneConfig(entry.getKey(), entry.getValue());
            generateReadmes(entry.getKey(), entry.getValue());
        }
    }

    private static List<Path> findNonFilterExampleDirs(Path basePath) throws Exception {
        return Files.walk(basePath)
                .filter(Files::isDirectory)
                .filter(path -> !path.toString().contains("suppresswarningsholder"))
                .filter(path -> !path.toString().contains("filters") && !path.toString().contains("filfilters"))
                .filter(path -> {
                    try {
                        return Files.list(path)
                                .anyMatch(file -> file.getFileName().toString().matches("Example\\d+\\.(java|txt)"));
                    } catch (Exception e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());
    }

    public static String processDirectory(String inputDir, String outputDir) throws Exception {
        if (inputDir == null || outputDir == null) {
            throw new IllegalArgumentException("inputDir and outputDir must not be null");
        }

        Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Path fileName = path.getFileName();
                        return fileName != null && fileName.toString().matches("Example\\d+\\.(java|txt)");
                    })
                    .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE))
                    .collect(Collectors.toList());

            if (exampleFiles.isEmpty()) {
                return null;
            }

            Path firstExampleFile = exampleFiles.get(0);
            if (firstExampleFile == null) {
                return null;
            }

            String moduleName = ConfigSerializer.extractModuleName(firstExampleFile.toString());
            if (moduleName == null) {
                return null;
            }

            Path outputPath = PROJECT_ROOT.resolve(moduleName);
            Files.createDirectories(outputPath);

            for (Path exampleFile : exampleFiles) {
                if (exampleFile != null) {
                    Path fileName = exampleFile.getFileName();
                    if (fileName != null) {
                        String fileNameStr = fileName.toString().replaceFirst("\\.(java|txt)$", "");
                        Path subfolderPath = outputPath.resolve(fileNameStr);
                        Files.createDirectories(subfolderPath);
                        processFile(exampleFile.toString(), subfolderPath);
                    }
                }
            }

            return moduleName;
        } catch (Exception e) {
            System.err.println("Error processing directory: " + inputDir);
            e.printStackTrace();
            return null;
        }
    }

    private static void processFile(String exampleFile, Path outputPath) {
        if (exampleFile == null || outputPath == null || exampleFile.endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE)) {
            return;
        }

        try {
            String templateFilePath = getTemplateFilePath(exampleFile);
            if (templateFilePath == null) {
                throw new IllegalStateException("Unable to get template file path for: " + exampleFile);
            }

            String generatedContent = ConfigSerializer.serializeConfigToString(exampleFile, templateFilePath);

            Path outputFilePath = outputPath.resolve("config.xml");
            Files.writeString(outputFilePath, generatedContent, StandardCharsets.UTF_8);

            // Copy the project.properties file to the subfolder
            Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
            Path targetPropertiesPath = outputPath.resolve(PROJECT_PROPERTIES_FILENAME);
            Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);

            // Generate individual README for this example
            Path parentPath = outputPath.getParent();
            if (parentPath != null) {
                Path moduleNamePath = parentPath.getFileName();
                if (moduleNamePath != null) {
                    String moduleName = moduleNamePath.toString();
                    ReadmeGenerator.generateIndividualReadme(outputPath, moduleName);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading or processing the file: " + exampleFile);
            e.printStackTrace();
        }
    }


    public static void generateAllInOneConfig(String moduleName, List<Path> exampleDirs) throws Exception {
        List<String> allExampleFiles = new ArrayList<>();
        for (Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches("Example\\d+\\.(java|txt)"))
                        .map(Path::toString)
                        .filter(file -> !file.endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE))
                        .forEach(allExampleFiles::add);
            }
        }

        if (allExampleFiles.isEmpty()) {
            return;
        }

        Collections.sort(allExampleFiles, Comparator.comparingInt(Main::extractExampleNumber));

        Path outputPath = PROJECT_ROOT.resolve(moduleName);
        Path allInOneSubfolderPath = outputPath.resolve("all-examples-in-one");
        Files.createDirectories(allInOneSubfolderPath);

        String templateFilePath = getTemplateFilePath(allExampleFiles.get(0));
        String outputFilePath = allInOneSubfolderPath.resolve("config.xml").toString();

        String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                allExampleFiles.toArray(new String[0]), templateFilePath);
        Files.writeString(Path.of(outputFilePath), generatedContent);

        // Handle the all-examples-in-one case explicitly
        try {
            Map<String, Object> yamlData = YamlParserAndProjectHandler.parseYamlFile();
            Map<String, Object> moduleConfig = (Map<String, Object>) yamlData.get(moduleName);

            if (moduleConfig != null && moduleConfig.containsKey("all-examples-in-one")) {
                Map<String, Object> allInOneConfig = (Map<String, Object>) moduleConfig.get("all-examples-in-one");
                List<String> projectNames = (List<String>) allInOneConfig.get("projects");
                YamlParserAndProjectHandler.createProjectsFileForExample(allInOneSubfolderPath, projectNames,
                        Files.readAllLines(Paths.get(YamlParserAndProjectHandler.ALL_PROJECTS_FILE_PATH)),
                        moduleName);
            } else {
                // If no specific configuration for all-examples-in-one, use the default
                Path sourcePropertiesPath = Paths.get(YamlParserAndProjectHandler.DEFAULT_PROJECTS_FILE_PATH).toAbsolutePath();
                Path targetPropertiesPath = allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
                Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            System.err.println("Error processing YAML file for all-examples-in-one: " + e.getMessage());
            e.printStackTrace();

            // Fallback to copying the default properties file
            Path sourcePropertiesPath = Paths.get(YamlParserAndProjectHandler.DEFAULT_PROJECTS_FILE_PATH).toAbsolutePath();
            Path targetPropertiesPath = allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
            Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
        }

        ReadmeGenerator.generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    private static void generateReadmes(String moduleName, List<Path> exampleDirs) throws Exception {
        Path outputPath = PROJECT_ROOT.resolve(moduleName);

        for (Path dir : exampleDirs) {
            try (Stream<Path> paths = Files.list(dir)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().matches("Example\\d+\\.(java|txt)"))
                        .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE))
                        .forEach(exampleFile -> {
                            String fileName = exampleFile.getFileName().toString().replaceFirst("\\.(java|txt)$", "");
                            Path subfolderPath = outputPath.resolve(fileName);
                            try {
                                ReadmeGenerator.generateIndividualReadme(subfolderPath, moduleName);
                            } catch (Exception e) {
                                System.err.println("Error generating individual README for: " + subfolderPath);
                                e.printStackTrace();
                            }
                        });
            }
        }

        // Generate README for all-examples-in-one
        Path allInOneSubfolderPath = outputPath.resolve("all-examples-in-one");
        ReadmeGenerator.generateAllInOneReadme(allInOneSubfolderPath, moduleName);
    }

    private static String getTemplateFilePath(String exampleFilePath) throws Exception {
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        boolean isTreeWalker = ConfigSerializer.isTreeWalkerConfig(xmlConfig);
        String resourcePath = isTreeWalker ? "config-template-treewalker.xml" : "config-template-non-treewalker.xml";
        return Main.class.getClassLoader().getResource(resourcePath).getPath();
    }

    private static int extractExampleNumber(String filename) {
        Matcher matcher = Pattern.compile("Example(\\d+)\\.(java|txt)").matcher(filename);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE;
    }
}