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

import java.io.File;
import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Test class for CommandsUtil.
 */
class CommandsUtilTest {

    /**
     * Tests getOsSpecificCmd for non-Windows OS.
     */
    @Test
    void testGetOsSpecificCmdNonWindows() {
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (!osName.contains("windows")) {
            final String cmd = CommandsUtil.getOsSpecificCmd("ls -la");
            assertEquals("ls -la", cmd, "Unexpected command for non-Windows OS");
        }
    }

    /**
     * Tests getOsSpecificCmd for Windows OS.
     */
    @Test
    void testGetOsSpecificCmdWindows() {
        final String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osName.contains("windows")) {
            final String cmd = CommandsUtil.getOsSpecificCmd("dir");
            assertEquals("cmd /c dir", cmd, "Unexpected command for Windows OS");
        }
    }

    /**
     * Tests getOsSpecificPath.
     */
    @Test
    void testGetOsSpecificPath() {
        final String path = CommandsUtil.getOsSpecificPath("folder", "subfolder", "file.txt");
        final String expected =
                "folder" + File.separator + "subfolder" + File.separator + "file.txt";
        assertEquals(expected, path, "Unexpected OS-specific path");
    }
}
