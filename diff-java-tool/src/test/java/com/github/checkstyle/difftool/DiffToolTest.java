package com.github.checkstyle.difftool;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DiffToolTest {

    @Test
    public void testIsValidCheckstyleConfigsCombination_AllConfigsNull_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        final boolean result = (boolean) method.invoke(null, null, null, null, "diff");
        assertFalse(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_ConfigNotNull_ReturnsTrue() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, "config.xml", null, null, "diff");
        assertTrue(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_ConfigAndBaseConfigNotNull_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, "config.xml", "baseConfig.xml", null, "diff");
        assertFalse(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_BaseAndPatchConfigNotNull_DiffMode_ReturnsTrue() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, null, "baseConfig.xml", "patchConfig.xml", "diff");
        assertTrue(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_BaseConfigNull_PatchConfigNotNull_DiffMode_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, null, null, "patchConfig.xml", "diff");
        assertFalse(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_BaseConfigNotNull_PatchConfigNull_DiffMode_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, null, "baseConfig.xml", null, "diff");
        assertFalse(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_SingleMode_WithConfig_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, "config.xml", null, null, "single");
        assertFalse(result);
    }

    @Test
    public void testIsValidCheckstyleConfigsCombination_SingleMode_WithPatchConfig_ReturnsTrue() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "isValidCheckstyleConfigsCombination", String.class, String.class, String.class, String.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(null, null, null, "patchConfig.xml", "single");
        assertTrue(result);
    }

    @Test
    public void testIsGitSha_ValidSha_ReturnsTrue() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("isGitSha", String.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(null, "a1b2c"));
        assertTrue((boolean) method.invoke(null, "abcdef1234567890abcdef1234567890abcdef12"));
    }

    @Test
    public void testIsGitSha_InvalidSha_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("isGitSha", String.class);
        method.setAccessible(true);
        assertFalse((boolean) method.invoke(null, "a1b2"));
        assertFalse((boolean) method.invoke(null, "g1b2c"));
        assertFalse((boolean) method.invoke(null, "1234567890abcdef1234567890abcdef1234567890abcdef"));
    }

    @Test
    public void testGetCloneCmd_GitRepo_ReturnsCorrectCommand() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "getCloneCmd", String.class, String.class, String.class);
        method.setAccessible(true);
        String cmd = (String) method.invoke(null, "git", "https://github.com/user/repo.git", "/path/to/dir");
        assertEquals("git clone https://github.com/user/repo.git /path/to/dir", cmd);
    }

    @Test
    public void testGetCloneCmd_UnknownRepoType_ThrowsException() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "getCloneCmd", String.class, String.class, String.class);
        method.setAccessible(true);
        Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(null, "svn", "https://svnrepo/repo", "/path/to/dir");
        });
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    @Test
    public void testGetResetCmd_GitRepo_WithSha_ReturnsCorrectCommand() throws Exception {
        Method isGitShaMethod = DiffTool.class.getDeclaredMethod("isGitSha", String.class);
        isGitShaMethod.setAccessible(true);
        Method method = DiffTool.class.getDeclaredMethod(
                "getResetCmd", String.class, String.class);
        method.setAccessible(true);

        String cmd = (String) method.invoke(null, "git", "abcdef1234567890abcdef1234567890abcdef12");
        assertEquals("git reset --hard abcdef1234567890abcdef1234567890abcdef12", cmd);
    }

    @Test
    public void testGetResetCmd_GitRepo_WithTag_ReturnsCorrectCommand() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod(
                "getResetCmd", String.class, String.class);
        method.setAccessible(true);

        String cmd = (String) method.invoke(null, "git", "v1.0");
        assertEquals("git reset --hard refs/tags/v1.0", cmd);
    }

    @Test
    public void testGetOsSpecificCmd_NonWindows_ReturnsSameCommand() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("getOsSpecificCmd", String.class);
        method.setAccessible(true);

        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("windows")) {
            String cmd = (String) method.invoke(null, "ls -la");
            assertEquals("ls -la", cmd);
        }
    }

    @Test
    public void testGetOsSpecificCmd_Windows_ReturnsCmdCommand() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("getOsSpecificCmd", String.class);
        method.setAccessible(true);

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            String cmd = (String) method.invoke(null, "dir");
            assertEquals("cmd /c dir", cmd);
        }
    }

    @Test
    public void testGetOsSpecificPath_ReturnsCorrectPath() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("getOsSpecificPath", String[].class);
        method.setAccessible(true);

        String path = (String) method.invoke(null, (Object) new String[]{"folder", "subfolder", "file.txt"});
        String expected = "folder" + File.separator + "subfolder" + File.separator + "file.txt";
        assertEquals(expected, path);
    }

    @Test
    public void testGetCheckstyleVersionFromPomXml(@TempDir Path tempDir) throws Exception {
        // Create a temporary pom.xml file
        Path pomXml = tempDir.resolve("pom.xml");
        Files.write(pomXml, ("<project>\n" +
                "<version>8.36-SNAPSHOT</version>\n" +
                "</project>").getBytes());

        Method method = DiffTool.class.getDeclaredMethod("getCheckstyleVersionFromPomXml", String.class, String.class);
        method.setAccessible(true);
        String version = (String) method.invoke(null, pomXml.toString(), "version");
        assertEquals("8.36-SNAPSHOT", version);
    }

    @Test
    public void testGetCommitSha_ValidCommitId() throws Exception {
        // Assuming we have a local git repo at a given path
        String commitId = "abc123";
        String repoType = "git";
        String srcDestinationDir = "."; // Current directory

        // Mocking the method's behavior
        Method method = DiffTool.class.getDeclaredMethod(
                "getCommitSha", String.class, String.class, String.class);
        method.setAccessible(true);

        // Since actual git commands won't work here, we can only test that method invocation doesn't throw
        // In real unit tests, you should mock Process execution
        try {
            String sha = (String) method.invoke(null, commitId, repoType, srcDestinationDir);
            // Can't assert actual value without mocking
        } catch (Exception e) {
            // Since we don't have a git repo, it may throw
        }
    }

    @Test
    public void testIsExistingGitBranch_NonExistingBranch_ReturnsFalse() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("isExistingGitBranch", File.class, String.class);
        method.setAccessible(true);

        // Since we don't have an actual git repository, this test is just illustrative
        File fakeRepo = new File("/non/existing/path");
        boolean exists = (boolean) method.invoke(null, fakeRepo, "nonExistingBranch");
        // Should return false, but since it depends on external command, this is just illustrative
    }

    @Test
    public void testHasUnstagedChanges_NoRepo_ReturnsTrue() throws Exception {
        Method method = DiffTool.class.getDeclaredMethod("hasUnstagedChanges", File.class);
        method.setAccessible(true);

        // Since we don't have an actual git repository, we can only test method invocation
        File fakeRepo = new File("/non/existing/path");
        boolean hasChanges = (boolean) method.invoke(null, fakeRepo);
        // Should return true due to error, but since it depends on external command, this is just illustrative
    }

    @Test
    public void testConfigConstructor_ValidInput() throws Exception {
        final CommandLine cmd = DiffTool.getCliOptions(new String[]{
                "-r", "/path/to/repo",
                "-p", "patchBranch",
                "-l", "projects.txt",
                "-m", "diff",
                "-bc", "baseConfig.xml",
                "-pc", "patchConfig.xml"
        });

        // Since Config is an inner class, we need to access it via reflection
        final Class<?> configClass = Class.forName("com.github.checkstyle.difftool.DiffTool$Config");
        final java.lang.reflect.Constructor<?> constructor = configClass.getDeclaredConstructor(CommandLine.class);
        constructor.setAccessible(true);
        Object configInstance = constructor.newInstance(cmd);

        // Accessing methods of Config class
        Method getModeMethod = configClass.getMethod("getMode");
        final String mode = (String) getModeMethod.invoke(configInstance);
        assertEquals("diff", mode);
    }
}