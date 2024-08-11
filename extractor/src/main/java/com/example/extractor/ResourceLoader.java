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