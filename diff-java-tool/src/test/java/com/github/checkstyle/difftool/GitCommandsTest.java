///////////////////////////////////////////////////////////////////////////////////////////////
// checkstyle: Checks Java source code and other text files for adherence to a set of rules.
// Copyright (C) 2001-2026 the original author or authors.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test class for GitCommands.
 */
class GitCommandsTest {

    /**
     * Tests isGitSha with valid SHA.
     */
    @Test
    void testIsGitShaValidSha() {
        assertTrue(GitCommands.isGitSha("a1b2c"), "Expected true for short SHA");
        assertTrue(GitCommands.isGitSha("abcdef1234567890abcdef1234567890abcdef12"),
                "Expected true for full SHA");
    }

    /**
     * Tests isGitSha with invalid SHA.
     */
    @Test
    void testIsGitShaInvalidSha() {
        assertFalse(GitCommands.isGitSha("a1b2"),
                "Expected false for too short SHA");
        assertFalse(GitCommands.isGitSha("g1b2c"),
                "Expected false for invalid characters in SHA");
        assertFalse(GitCommands.isGitSha("1234567890abcdef1234567890abcdef1234567890abcdef"),
            "Expected false for too long SHA");
    }

    /**
     * Tests getCloneCmd for Git repo.
     */
    @Test
    void testGetCloneCmd() {
        final String cmd = GitCommands.getCloneCmd("https://github.com/user/repo.git",
            "/path/to/dir");
        assertEquals("git clone https://github.com/user/repo.git /path/to/dir",
                cmd, "Unexpected clone command");
    }

    /**
     * Tests getResetCmd for Git repo with SHA.
     */
    @Test
    void testGetResetCmdWithSha() {
        final String cmd =
                GitCommands.getResetCmd("abcdef1234567890abcdef1234567890abcdef12");
        assertEquals("git reset --hard abcdef1234567890abcdef1234567890abcdef12",
                cmd, "Unexpected reset command");
    }

    /**
     * Tests getResetCmd for Git repo with tag.
     */
    @Test
    void testGetResetCmdGitRepoWithTag() {
        final String cmd = GitCommands.getResetCmd("v1.0");
        assertEquals("git reset --hard refs/tags/v1.0", cmd, "Unexpected reset command");
    }
}
