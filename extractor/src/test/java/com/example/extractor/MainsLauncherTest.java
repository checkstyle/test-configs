package com.example.extractor;

import org.junit.jupiter.api.Test;

public class MainsLauncherTest {

    @Test
    public void testMain() throws Exception {
        // Define the path in repo you want to test
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/javadoc/summaryjavadoc";
        // Define the output directory
        String outputDirectory = "SummaryJavadoc";

        // Pass the path and output directory as arguments
        Main.main(new String[]{pathInRepo, outputDirectory});
    }
}