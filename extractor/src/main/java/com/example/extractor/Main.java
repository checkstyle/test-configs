
package com.example.extractor;

import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Main {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath().getParent();

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Path in repo must be provided as the first argument.");
        }

        // Get the path in repo from the command-line arguments
        String pathInRepo = args[0];
        System.out.println("Using path in repo: " + pathInRepo);

        // Path to Checkstyle repo
        String checkstyleRepoPath = ".ci-temp/checkstyle";

        // Input and output directories
        String inputDirectory = checkstyleRepoPath + "/" + pathInRepo;

        System.out.println("PROJECT_ROOT: " + PROJECT_ROOT);
        Path outputDirectory = PROJECT_ROOT.resolve("AbstractClassName");

        // Process files in the input directory and save results to the output directory
        processFiles(inputDirectory, outputDirectory.toString());
    }

    public static void processFiles(String inputDir, String outputDir) throws Exception {
        // Pattern to match files named Example#.java
        Pattern pattern = Pattern.compile("Example\\d+\\.java");

        // Collect all Example#.java files in the input directory
        System.out.println("Walking through the input directory to collect Example#.java files...");
        try (Stream<Path> paths = Files.walk(Paths.get(inputDir))) {
            List<String> exampleFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        Matcher matcher = pattern.matcher(path.getFileName().toString());
                        return matcher.matches();
                    })
                    .map(Path::toString)
                    .collect(Collectors.toList());

            System.out.println("Found " + exampleFiles.size() + " Example#.java files.");

            // Ensure output directory exists
            Path outputPath = Paths.get(outputDir).toAbsolutePath();
            if (!Files.exists(outputPath)) {
                System.out.println("Output directory does not exist. Creating: " + outputPath);
                Files.createDirectories(outputPath);
            } else {
                System.out.println("Output directory already exists: " + outputPath);
            }

            // Create subfolders for each file in the output directory
            for (String exampleFile : exampleFiles) {
                String fileName = Paths.get(exampleFile).getFileName().toString().replace(".java", "");
                Path subfolderPath = Paths.get(outputDir, fileName);
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
        } catch (Exception e) {
            System.err.println("Error reading or processing the file: " + exampleFile);
            e.printStackTrace();
        }
    }
}
