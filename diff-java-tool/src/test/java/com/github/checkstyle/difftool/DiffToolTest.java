//package com.github.checkstyle.difftool;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import static org.junit.jupiter.api.Assertions.*;
//
//class DiffToolTest {
//
//    private TestDiffTool testDiffTool;
//
//    @BeforeEach
//    void setUp() {
//        testDiffTool = new TestDiffTool();
//    }
//
//    @Test
//    void testRunGradleExecution() throws IOException, InterruptedException {
//        String srcDir = "/path/to/src";
//        String excludes = "exclude1,exclude2";
//        String checkstyleConfig = "/path/to/checkstyle.xml";
//        String checkstyleVersion = "8.41";
//        String extraRegressionOptions = "-DskipTests=true";
//
//        DiffTool.runGradleExecution(srcDir, excludes, checkstyleConfig, checkstyleVersion, extraRegressionOptions);
//
//        List<String> executedCommands = testDiffTool.getExecutedCommands();
//        assertEquals(2, executedCommands.size());
//        assertEquals("gradle clean", executedCommands.get(0));
//
//        String gradleCheckCommand = executedCommands.get(1);
//        assertTrue(gradleCheckCommand.contains("gradle check"));
//        assertTrue(gradleCheckCommand.contains("-Dcheckstyle.config.location=" + checkstyleConfig));
//        assertTrue(gradleCheckCommand.contains("-Dcheckstyle.excludes=" + excludes));
//        assertTrue(gradleCheckCommand.contains("-Dcheckstyle.version=" + checkstyleVersion));
//        assertTrue(gradleCheckCommand.contains(extraRegressionOptions));
//    }
//
//    private static class TestDiffTool extends DiffTool {
//        private final List<String> executedCommands = new ArrayList<>();
//
//        @Override
//        protected void executeGradleCommand(String command) {
//            executedCommands.add(command);
//        }
//
//        public List<String> getExecutedCommands() {
//            return executedCommands;
//        }
//    }
//}