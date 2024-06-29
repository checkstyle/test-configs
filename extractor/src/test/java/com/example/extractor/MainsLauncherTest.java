package com.example.extractor;

import org.junit.jupiter.api.Test;

public class MainsLauncherTest {
    @Test
    public void testMain() throws Exception {
        // Define the base path to the Checkstyle repo
        String checkstyleRepoPath = "../.ci-temp/checkstyle";

        // Pass the base path as argument
        Main.main(new String[]{checkstyleRepoPath});
    }
}
