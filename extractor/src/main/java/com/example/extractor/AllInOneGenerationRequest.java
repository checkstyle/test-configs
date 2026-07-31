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

package com.example.extractor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable holder for the parameters needed to generate an "all-in-one" configuration
 * for a module. Introduced to replace a long String-heavy parameter list
 * (PMD UseObjectForClearerAPI). Kept as a top-level type so checkstyle's
 * {@code InnerTypeLast} rule has nothing to flag on the classes that use it.
 * The compact constructor and accessor defensively copy {@code exampleDirs} to avoid
 * exposing or storing a mutable external list (SpotBugs EI/EI2).
 *
 * @param moduleName Module name.
 * @param exampleDirs List of example directories.
 * @param checkstyleRepoPath The path to the Checkstyle repository.
 * @param filePattern The filename pattern used to select which files to include.
 * @param subfolderName The name of the output subfolder to write config.xml into.
 */
public record AllInOneGenerationRequest(
        String moduleName,
        List<Path> exampleDirs,
        String checkstyleRepoPath,
        String filePattern,
        String subfolderName) {

    /**
     * Compact constructor that defensively copies {@code exampleDirs} so the record
     * does not store a reference to an externally mutable list.
     *
     * @param moduleName Module name.
     * @param exampleDirs List of example directories.
     * @param checkstyleRepoPath The path to the Checkstyle repository.
     * @param filePattern The filename pattern used to select which files to include.
     * @param subfolderName The name of the output subfolder to write config.xml into.
     */
    public AllInOneGenerationRequest {
        exampleDirs = Collections.unmodifiableList(new ArrayList<>(exampleDirs));
    }

    /**
     * Returns an unmodifiable view of the example directories.
     *
     * @return an unmodifiable list of example directories.
     */
    @Override
    public List<Path> exampleDirs() {
        return exampleDirs;
    }
}
