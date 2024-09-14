///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2024 the original author or authors.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
///////////////////////////////////////////////////////////////////////////////////////////////

package com.github.checkstyle.difftool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.Test;

/**
 * Test class for DiffTool.
 */
class DiffToolTest {

    /**
     * Tests isValidCheckstyleConfigsCombination with all configs null.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationAllConfigsNull() throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, null, null, null, "diff");
        assertFalse(result, "Expected false when all configs are null");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination with config not null.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationConfigNotNull() throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, "config.xml", null, null, "diff");
        assertTrue(result, "Expected true when config is not null");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination with config and base config not null.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationConfigAndBaseConfigNotNull() throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, "config.xml",
                "baseConfig.xml", null, "diff");
        assertFalse(result, "Expected false when config and base config are not null");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination with base and patch config not null in diff mode.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationBaseAndPatchConfigNotNullDiffMode()
            throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null,
                null,
                "baseConfig.xml",
                "patchConfig.xml",
                "diff");
        assertTrue(result,
                "Expected true when base and patch config are not null in diff mode");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination with base config null
     * and patch config not null in diff mode.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationBaseConfigNullPatchConfigNotNullDiffMode()
            throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, null, null, "patchConfig.xml", "diff");
        assertFalse(result,
                "Expected false when base config is null "
                        + "and patch config is not null in diff mode");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination with base config not null
     * and patch config null in diff mode.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationBaseConfigNotNullPatchConfigNullDiffMode()
            throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, null,
                "baseConfig.xml", null, "diff");
        assertFalse(
                result,
                "Expected false when base config is not null and patch config is null "
                         + "in diff mode"
        );
    }

    /**
     * Tests isValidCheckstyleConfigsCombination in single mode with config.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationSingleModeWithConfig() throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (boolean) method.invoke(null, "config.xml", null, null, "single");
        assertFalse(result, "Expected false in single mode with config");
    }

    /**
     * Tests isValidCheckstyleConfigsCombination in single mode with patch config.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsValidCheckstyleConfigsCombinationSingleModeWithPatchConfig() throws Exception {
        final Method method = getDeclaredMethod("isValidCheckstyleConfigsCombination",
                String.class, String.class, String.class, String.class);
        final boolean result = (
                boolean) method.invoke(null, null, null, "patchConfig.xml", "single");
        assertTrue(result, "Expected true in single mode with patch config");
    }

    /**
     * Tests isGitSha with valid SHA.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsGitShaValidSha() throws Exception {
        final Method method = getDeclaredMethod("isGitSha", String.class);
        assertTrue((boolean) method.invoke(null, "a1b2c"), "Expected true for short SHA");
        assertTrue((boolean) method.invoke(null, "abcdef1234567890abcdef1234567890abcdef12"),
                "Expected true for full SHA");
    }

    /**
     * Tests isGitSha with invalid SHA.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testIsGitShaInvalidSha() throws Exception {
        final Method method = getDeclaredMethod("isGitSha", String.class);
        assertFalse((boolean) method.invoke(null, "a1b2"),
                "Expected false for too short SHA");
        assertFalse((boolean) method.invoke(null, "g1b2c"),
                "Expected false for invalid characters in SHA");
        assertFalse((boolean) method.invoke(null,
                "1234567890abcdef1234567890abcdef1234567890abcdef"),
                "Expected false for too long SHA");
    }

    /**
     * Tests getCloneCmd for Git repo.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetCloneCmdGitRepo() throws Exception {
        final Method method =
                getDeclaredMethod("getCloneCmd", String.class, String.class, String.class);
        final String cmd = (String) method.invoke(null, "git",
                "https://github.com/user/repo.git", "/path/to/dir");
        assertEquals("git clone https://github.com/user/repo.git /path/to/dir",
                cmd, "Unexpected clone command");
    }

    /**
     * Tests getCloneCmd for unknown repo type.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetCloneCmdUnknownRepoType() throws Exception {
        final Method method = getDeclaredMethod("getCloneCmd",
                String.class, String.class, String.class);
        final Exception exception = assertThrows(Exception.class, () -> {
            method.invoke(null, "svn", "https://svnrepo/repo", "/path/to/dir");
        }, "Expected exception for unknown repo type");
        assertTrue(exception.getCause() instanceof IllegalArgumentException,
                "Expected IllegalArgumentException");
    }

    /**
     * Tests getResetCmd for Git repo with SHA.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetResetCmdGitRepoWithSha() throws Exception {
        final Method method =
                getDeclaredMethod("getResetCmd", String.class, String.class);
        final String cmd =
                (String) method.invoke(null, "git", "abcdef1234567890abcdef1234567890abcdef12");
        assertEquals("git reset --hard abcdef1234567890abcdef1234567890abcdef12",
                cmd, "Unexpected reset command");
    }

    /**
     * Tests getResetCmd for Git repo with tag.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetResetCmdGitRepoWithTag() throws Exception {
        final Method method = getDeclaredMethod("getResetCmd", String.class, String.class);
        final String cmd = (String) method.invoke(null, "git", "v1.0");
        assertEquals("git reset --hard refs/tags/v1.0", cmd, "Unexpected reset command");
    }

    /**
     * Tests getOsSpecificCmd for non-Windows OS.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetOsSpecificCmdNonWindows() throws Exception {
        final Method method = getDeclaredMethod("getOsSpecificCmd", String.class);
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (!osName.contains("windows")) {
            final String cmd = (String) method.invoke(null, "ls -la");
            assertEquals("ls -la", cmd, "Unexpected command for non-Windows OS");
        }
    }

    /**
     * Tests getOsSpecificCmd for Windows OS.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetOsSpecificCmdWindows() throws Exception {
        final Method method = getDeclaredMethod("getOsSpecificCmd", String.class);
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osName.contains("windows")) {
            final String cmd = (String) method.invoke(null, "dir");
            assertEquals("cmd /c dir", cmd, "Unexpected command for Windows OS");
        }
    }

    /**
     * Tests getOsSpecificPath.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testGetOsSpecificPath() throws Exception {
        final Method method =
                getDeclaredMethod("getOsSpecificPath", String[].class);
        final String path = (String) method.invoke(null,
                        (Object) new String[]{"folder", "subfolder", "file.txt"});
        final String expected =
                "folder" + File.separator + "subfolder" + File.separator + "file.txt";
        assertEquals(expected, path, "Unexpected OS-specific path");
    }

    /**
     * Tests Config constructor with valid input.
     *
     * @throws Exception if an error occurs during the test
     */
    @Test
    void testConfigConstructorValidInput() throws Exception {
        final String[] args = {
            "-r", "/path/to/repo",
            "-p", "patchBranch",
            "-l", "projects.txt",
            "-m", "diff",
            "-bc", "baseConfig.xml",
            "-pc", "patchConfig.xml",
        };
        final CommandLine cmd = DiffTool.getCliOptions(args);

        final Class<?> configClass =
                Class.forName("com.github.checkstyle.difftool.DiffTool$Config");
        final java.lang.reflect.Constructor<?> constructor =
                configClass.getDeclaredConstructor(CommandLine.class);
        constructor.setAccessible(true);
        final Object configInstance = constructor.newInstance(cmd);

        final Method getModeMethod = configClass.getMethod("getMode");
        final String mode = (String) getModeMethod.invoke(configInstance);
        assertEquals("diff", mode, "Mode should be 'diff'");
    }

    private Method getDeclaredMethod(final String methodName,
                                     final Class<?>... parameterTypes)
            throws NoSuchMethodException {
        final Method method = DiffTool.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
