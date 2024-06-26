package com.example.extractor;

import org.junit.jupiter.api.Test;

public class MainsLauncherTest {

    @Test
    public void testMainTreeWalker() throws Exception {
        // Define the path in repo you want to test
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/naming";

        // Pass the path in repo as the argument
        Main.main(new String[]{pathInRepo});
    }

    @Test
    public void testMainNonTreeWalker() throws Exception {
        // Define the path in repo you want to test for non-TreeWalker configuration
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/translation";

        // Pass the path in repo as the argument
        Main.main(new String[]{pathInRepo});
    }
}
