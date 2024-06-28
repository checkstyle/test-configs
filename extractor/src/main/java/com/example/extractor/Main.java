package com.example.extractor;

import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.bdd.InlineConfigParser;
import com.puppycrawl.tools.checkstyle.bdd.TestInputConfiguration;

import java.nio.file.*;
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
        System.out.println("Using Checkstyle repo path: " + checkstyleRepoPath);

        // Process compilable and non-compilable paths
        List<Path> allExampleDirs = new ArrayList<>();
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources")));
        allExampleDirs.addAll(findNonFilterExampleDirs(Paths.get(checkstyleRepoPath, "src", "xdocs-examples", "resources-noncompilable")));

        System.out.println("Found " + allExampleDirs.size() + " non-filter directories with Example files.");

        Map<String, List<Path>> moduleExamples = new HashMap<>();

        // Process all directories
        for (Path dir : allExampleDirs) {
            System.out.println("Processing directory: " + dir);
            String moduleName = processDirectory(dir.toString(), PROJECT_ROOT.toString());
            if (moduleName != null) {
                moduleExamples.computeIfAbsent(moduleName, k -> new ArrayList<>()).add(dir);
            }
        }

        // Generate all-in-one configs for each module
        for (Map.Entry<String, List<Path>> entry : moduleExamples.entrySet()) {
            generateAllInOneConfig(entry.getKey(), entry.getValue());
        }
    }

    private static List<Path> findNonFilterExampleDirs(Path basePath) throws Exception {
        return Files.walk(basePath)
                .filter(Files::isDirectory)
                .filter(path -> !path.toString().contains("suppresswarningsholder")) // Exclude SuppressWarningsHolder modules
                .filter(path -> !path.toString().contains("filters") && !path.toString().contains("filfilters")) // Exclude filter and filfilters modules
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
        Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            List<Path> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("Example\\d+\\.(java|txt)"))
                    .filter(path -> !path.toString().endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE)) // Exclude specific file for regexp
                    .collect(Collectors.toList());

            if (exampleFiles.isEmpty()) {
                return null;
            }

            String moduleName = ConfigSerializer.extractModuleName(exampleFiles.get(0).toString());

            Path outputPath = PROJECT_ROOT.resolve(moduleName);
            Files.createDirectories(outputPath);

            for (Path exampleFile : exampleFiles) {
                String fileName = exampleFile.getFileName().toString().replaceFirst("\\.(java|txt)$", "");
                Path subfolderPath = outputPath.resolve(fileName);
                Files.createDirectories(subfolderPath);
                processFile(exampleFile.toString(), subfolderPath);
            }

            return moduleName;
        } catch (Exception e) {
            System.err.println("Error processing directory: " + inputDir);
            e.printStackTrace();
            return null;
        }
    }

    private static void processFile(String exampleFile, Path outputPath) {
        // Exclude specific files
        if (exampleFile.endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE)) {
            System.out.println("Skipping excluded file: " + exampleFile);
            return;
        }

        System.out.println("Processing file: " + exampleFile);
        String fileContent;
        try {
            fileContent = new String(Files.readAllBytes(Paths.get(exampleFile)));
            System.out.println("File content:\n" + fileContent);

            String generatedContent;
            try {
                System.out.println("Generating configuration for file: " + exampleFile);
                generatedContent = ConfigSerializer.serializeConfigToString(exampleFile, getTemplateFilePath(exampleFile));
                System.out.println("Generated configuration:\n" + generatedContent);
            } catch (Exception e) {
                System.err.println("Failed to process file: " + exampleFile);
                e.printStackTrace();
                return;
            }

            Path outputFilePath = outputPath.resolve("config.xml");
            System.out.println("Writing generated configuration to: " + outputFilePath);
            Files.writeString(outputFilePath, generatedContent);

            // Copy the project.properties file to the subfolder
            Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
            Path targetPropertiesPath = outputPath.resolve(PROJECT_PROPERTIES_FILENAME);
            System.out.println("Copying project properties from: " + sourcePropertiesPath + " to " + targetPropertiesPath);
            Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
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
                        .filter(file -> !file.endsWith(EXCLUDED_FILE_PATH_REGEXPMULTILINE)) // Exclude specific file for regexp
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
        String outputFilePath = allInOneSubfolderPath.resolve("config-all-in-one.xml").toString();

        System.out.println("Generating all-in-one configuration file for " + moduleName + " at: " + outputFilePath);
        String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(
                allExampleFiles.toArray(new String[0]), templateFilePath);
        Files.writeString(Path.of(outputFilePath), generatedContent);

        // Copy the project.properties file
        Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
        Path targetPropertiesPath = allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
        Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String getTemplateFilePath(String exampleFilePath) throws Exception {
        TestInputConfiguration testInputConfiguration = InlineConfigParser.parseWithXmlHeader(exampleFilePath);
        Configuration xmlConfig = testInputConfiguration.getXmlConfiguration();
        boolean isTreeWalker = ConfigSerializer.isTreeWalkerConfig(xmlConfig);
        return Main.class.getClassLoader().getResource(isTreeWalker ? "config-template-treewalker.xml" : "config-template-non-treewalker.xml").getPath();
    }

    private static int extractExampleNumber(String filename) {
        Matcher matcher = Pattern.compile("Example(\\d+)\\.(java|txt)").matcher(filename);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE; // A large number to place invalid filenames at the end
    }
}