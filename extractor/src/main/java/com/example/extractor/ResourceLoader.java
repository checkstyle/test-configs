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

import java.io.IOException;

/**
 * Utility class for loading resources.
 */
final class ResourceLoader {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ResourceLoader() {
        // Utility class, no instances
    }

    /**
     * Get the path of a resource.
     *
     * @param resourceName The name of the resource
     * @return The path of the resource
     * @throws IOException If the resource is not found
     */
    public static String getResourcePath(final String resourceName) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            throw new IOException("Class loader not found");
        }
        final java.net.URL resourceUrl = classLoader.getResource(resourceName);
        if (resourceUrl == null) {
            throw new IOException("Resource not found: " + resourceName);
        }
        return resourceUrl.getPath();
    }
}
