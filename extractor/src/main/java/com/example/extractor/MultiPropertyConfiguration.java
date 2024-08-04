package com.example.extractor;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MultiPropertyConfiguration is a custom implementation of the Configuration interface
 * that supports multiple values for a single property.
 */
public class MultiPropertyConfiguration implements Configuration, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Delegate configuration to handle single property configurations.
     */
    private final DefaultConfiguration delegate;

    /**
     * Map to store properties with multiple values.
     */
    private final Map<String, List<String>> multiProperties = new ConcurrentHashMap<>();

    /**
     * Constructs a MultiPropertyConfiguration with the given name.
     *
     * @param name the name of the configuration
     */
    public MultiPropertyConfiguration(final String name) {
        this.delegate = new DefaultConfiguration(name);
    }

    /**
     * Adds a property with multiple values.
     *
     * @param name  the name of the property
     * @param value the value of the property
     */
    public void addMultiProperty(final String name, final String value) {
        multiProperties.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public String[] getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public String getAttribute(final String name) throws CheckstyleException {
        return delegate.getAttribute(name);
    }

    @Override
    public String[] getPropertyNames() {
        final Set<String> names = new HashSet<>(Arrays.asList(delegate.getPropertyNames()));
        names.addAll(multiProperties.keySet());
        return names.toArray(new String[0]);
    }

    @Override
    public String getProperty(final String name) throws CheckstyleException {
        final List<String> values = multiProperties.get(name);
        if (values != null && !values.isEmpty()) {
            return String.join(",", values);
        }
        return delegate.getProperty(name);
    }

    @Override
    public Configuration[] getChildren() {
        return delegate.getChildren();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Map<String, String> getMessages() {
        return delegate.getMessages();
    }

    /**
     * Adds a property to the delegate configuration.
     *
     * @param name  the name of the property
     * @param value the value of the property
     * @throws CheckstyleException if an error occurs while adding the property
     */
    public void addProperty(final String name, final String value) {
        delegate.addProperty(name, value);
    }

    /**
     * Adds a child configuration to the delegate configuration.
     *
     * @param child the child configuration to add
     */
    public void addChild(final Configuration child) {
        delegate.addChild(child);
    }
}