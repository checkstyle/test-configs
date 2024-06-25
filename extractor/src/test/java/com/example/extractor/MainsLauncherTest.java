package com.example.extractor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainsLauncherTest {

    @Test
    public void testMainTreeWalker() throws Exception {
        // Define the path in repo you want to test
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/naming";

        // Pass the path in repo and configuration type as arguments
        Main.main(new String[]{pathInRepo, "treewalker"});
    }

    @Test
    public void testMainNonTreeWalker() throws Exception {
        // Define the path in repo you want to test for non-TreeWalker configuration
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/orderedproperties";

        // Pass the path in repo and configuration type as arguments
        Main.main(new String[]{pathInRepo, "non-treewalker"});
    }
}