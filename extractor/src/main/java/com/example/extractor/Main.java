package com.example.extractor;

import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Main {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();
    private static final String PROJECT_PROPERTIES_FILENAME = "list-of-projects.properties";
    private static final String PROJECT_PROPERTIES_FILE_PATH = "src/main/resources/" + PROJECT_PROPERTIES_FILENAME;

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Path in repo must be provided as argument.");
        }

        // Get the path in repo from the command-line arguments
        String pathInRepo = args[0];
        System.out.println("Using path in repo: " + pathInRepo);

        // Path to Checkstyle repo
        String checkstyleRepoPath = "../.ci-temp/checkstyle";

        // Input directory
        String inputDirectory = checkstyleRepoPath + "/" + pathInRepo;

        System.out.println("PROJECT_ROOT: " + PROJECT_ROOT);

        // Process directories
        processDirectory(inputDirectory, PROJECT_ROOT.toString());
    }

    public static void processDirectory(String inputDir, String outputDir) throws Exception {
        Path inputPath = Paths.get(inputDir);
        try (Stream<Path> paths = Files.list(inputPath)) {
            List<Path> contents = paths.collect(Collectors.toList());

            boolean hasSubdirectories = contents.stream().anyMatch(Files::isDirectory);

            if (hasSubdirectories) {
                // If there are subdirectories, process them
                for (Path dir : contents) {
                    if (Files.isDirectory(dir)) {
                        String relativePath = inputPath.relativize(dir).toString();
                        Path outputPath = Paths.get(outputDir).resolve(relativePath);
                        processFiles(dir.toString(), outputPath.toString());
                        generateAllInOneConfig(dir.toString(), outputPath.toString());
                    }
                }
            } else {
                // If there are no subdirectories, process the current directory
                processFiles(inputDir, outputDir);
                generateAllInOneConfig(inputDir, outputDir);
            }
        } catch (Exception e) {
            System.err.println("Error processing directory: " + inputDir);
            e.printStackTrace();
        }
    }

    public static void processFiles(String inputDir, String outputDir) throws Exception {
        // Pattern to match files named Example#.java or Example#.txt
        Pattern pattern = Pattern.compile("Example\\d+\\.(java|txt)");

        // Collect all Example#.java and Example#.txt files in the input directory
        System.out.println("Walking through the input directory to collect Example#.java and Example#.txt files...");
        try (Stream<Path> paths = Files.walk(Paths.get(inputDir))) {
            List<String> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Matcher matcher = pattern.matcher(path.getFileName().toString());
                        return matcher.matches();
                    })
                    .map(Path::toString)
                    .collect(Collectors.toList());

            System.out.println("Found " + exampleFiles.size() + " Example#.java or Example#.txt files.");

            if (exampleFiles.isEmpty()) {
                return; // Skip processing if no example files found
            }

            // Get the module name from the first example file
            String moduleName = ConfigSerializer.extractModuleName(exampleFiles.get(0));

            // Ensure output directory exists
            Path outputPath = PROJECT_ROOT.resolve(moduleName);
            if (!Files.exists(outputPath)) {
                System.out.println("Output directory does not exist. Creating: " + outputPath);
                Files.createDirectories(outputPath);
            } else {
                System.out.println("Output directory already exists: " + outputPath);
            }

            // Create subfolders for each file in the output directory
            for (String exampleFile : exampleFiles) {
                String fileName = Paths.get(exampleFile).getFileName().toString().replaceFirst("\\.(java|txt)$", "");
                Path subfolderPath = outputPath.resolve(fileName);
                Files.createDirectories(subfolderPath);
                processFile(exampleFile, subfolderPath);
            }
        } catch (Exception e) {
            System.err.println("Error walking through the input directory or processing files.");
            e.printStackTrace();
        }
    }

    private static void processFile(String exampleFile, Path outputPath) {
        // Determine the template file path using the class loader
        String templateFilePath = Main.class.getClassLoader().getResource("config-template-treewalker.xml").getPath();
        System.out.println("Template file path: " + templateFilePath);

        System.out.println("Processing file: " + exampleFile);
        String fileContent;
        try {
            fileContent = new String(Files.readAllBytes(Paths.get(exampleFile)));
            System.out.println("File content:\n" + fileContent);

            String generatedContent;
            try {
                System.out.println("Generating configuration for file: " + exampleFile);
                generatedContent = ConfigSerializer.serializeConfigToString(exampleFile, templateFilePath);
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

    public static void generateAllInOneConfig(String inputDir, String outputDir) throws Exception {
        // Pattern to match files named Example#.java or Example#.txt
        Pattern pattern = Pattern.compile("Example\\d+\\.(java|txt)");

        // Collect all Example#.java and Example#.txt files in the input directory
        System.out.println("Walking through the input directory to collect Example#.java and Example#.txt files...");
        List<String> exampleFiles;
        try (Stream<Path> paths = Files.walk(Paths.get(inputDir))) {
            exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Matcher matcher = pattern.matcher(path.getFileName().toString());
                        return matcher.matches();
                    })
                    .map(Path::toString)
                    .sorted(Comparator.comparingInt(Main::extractExampleNumber)) // Sort based on example number
                    .collect(Collectors.toList());
        }

        System.out.println("Found " + exampleFiles.size() + " Example#.java or Example#.txt files for all-in-one config.");

        if (exampleFiles.isEmpty()) {
            return; // Skip processing if no example files found
        }

        // Get the module name from the first example file
        String moduleName = ConfigSerializer.extractModuleName(exampleFiles.get(0));

        // Ensure output directory exists
        Path outputPath = PROJECT_ROOT.resolve(moduleName);
        if (!Files.exists(outputPath)) {
            System.out.println("Output directory does not exist. Creating: " + outputPath);
            Files.createDirectories(outputPath);
        } else {
            System.out.println("Output directory already exists: " + outputPath);
        }

        // Create a subfolder named "all-examples-in-one"
        Path allInOneSubfolderPath = outputPath.resolve("all-examples-in-one");
        if (!Files.exists(allInOneSubfolderPath)) {
            System.out.println("Subfolder 'all-examples-in-one' does not exist. Creating: " + allInOneSubfolderPath);
            Files.createDirectories(allInOneSubfolderPath);
        } else {
            System.out.println("Subfolder 'all-examples-in-one' already exists: " + allInOneSubfolderPath);
        }

        // Determine the template file path using the class loader
        String templateFilePath = Main.class.getClassLoader().getResource("config-template-treewalker.xml").getPath();
        System.out.println("Template file path: " + templateFilePath);

        String[] exampleFilePaths = exampleFiles.toArray(new String[0]);
        String outputFilePath = allInOneSubfolderPath.resolve("config-all-in-one.xml").toString();


        // Generate the all-in-one configuration file
        System.out.println("Generating all-in-one configuration file at: " + outputFilePath);
        ConfigSerializer.serializeAllInOneConfigToFile(exampleFilePaths, templateFilePath, outputFilePath);

        System.out.println("Writing generated configuration to: " + outputFilePath);
        String generatedContent = ConfigSerializer.serializeAllInOneConfigToString(exampleFilePaths, templateFilePath);
        Files.writeString(Path.of(outputFilePath), generatedContent);

        // Copy the project.properties file to the all-in-one subfolder
        Path sourcePropertiesPath = Paths.get(PROJECT_PROPERTIES_FILE_PATH).toAbsolutePath();
        Path targetPropertiesPath = allInOneSubfolderPath.resolve(PROJECT_PROPERTIES_FILENAME);
        System.out.println("Copying project properties from: " + sourcePropertiesPath + " to " + targetPropertiesPath);
        Files.copy(sourcePropertiesPath, targetPropertiesPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static int extractExampleNumber(String filename) {
        Matcher matcher = Pattern.compile("Example(\\d+)\\.(java|txt)").matcher(filename);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return Integer.MAX_VALUE; // A large number to place invalid filenames at the end
    }
}