package com.example.extractor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainsLauncherTest {

    @Test
    public void testMain() throws Exception {
        // Define the path in repo you want to test
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/annotation/packageannotation";
        // Define the output directory
        String outputDirectory = "PackageAnnotation";

        // Pass the path and output directory as arguments
        Main.main(new String[]{pathInRepo, outputDirectory});
    }
}