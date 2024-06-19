package com.example.extractor;

import org.junit.jupiter.api.Test;

public class MainsLauncherTest {

    @Test
    public void testMain() throws Exception {
        // Define the path in repo you want to test
        String pathInRepo = "src/xdocs-examples/resources/com/puppycrawl/tools/checkstyle/checks/naming/abbreviationaswordinname";

        // Pass the path as an argument
        Main.main(new String[]{pathInRepo});
    }
}