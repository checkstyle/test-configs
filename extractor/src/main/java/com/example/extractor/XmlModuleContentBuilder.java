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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;

/**
 * Utility class for rendering Checkstyle {@link Configuration} objects (and their
 * properties) back into XML text, and for producing id-suffixed copies of a
 * configuration for "all-in-one" output. Split out of {@link ConfigSerializer} to
 * reduce that class's cyclomatic complexity (PMD CyclomaticComplexity); no behavior
 * was changed during the split.
 */
public final class XmlModuleContentBuilder {

    /** XML tag for module elements. */
    private static final String MODULE_TAG = "<module name=\"";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private XmlModuleContentBuilder() {
        // Private constructor to prevent instantiation
    }

    /**
     * Builds the XML content for a single Checkstyle module, including its properties.
     *
     * @param config The Checkstyle configuration to convert into XML content.
     * @param indent The indentation to apply to the XML elements.
     * @return The XML content of the module as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    public static String buildSingleModuleContent(final Configuration config,
                                                  final String indent) throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        builder.append(MODULE_TAG).append(config.getName()).append("\">\n");
        final String properties = buildProperties(config, indent + "    ");
        if (properties.isEmpty()) {
            builder.setLength(builder.length() - 2);
            builder.append("/>");
        }
        else {
            builder.append(properties)
                    .append('\n')
                    .append(indent)
                    .append("</module>");
        }
        return builder.toString();
    }

    /**
     * Builds the XML content for a Checkstyle module and its child modules.
     *
     * @param config The configuration containing the module and its children.
     * @param indent The indentation to apply to the XML elements.
     * @return The XML content of the module and its children as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    public static String buildModuleContent(
            final Configuration config,
            final String indent)
            throws CheckstyleException {
        final StringBuilder builder = new StringBuilder();
        for (final Configuration child : config.getChildren()) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent)
                        .append(MODULE_TAG)
                        .append(child.getName())
                        .append("\"/>\n\n");
            }
            else {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\">\n")
                        .append(childProperties).append('\n')
                        .append(indent).append("</module>\n\n");
            }
        }
        return builder.toString().trim();
    }

    /**
     * Builds the combined XML content for multiple Checkstyle module children.
     *
     * @param children The list of child configurations to combine.
     * @param indent   The indentation to apply to the XML elements.
     * @return The combined XML content of the module children as a string.
     * @throws CheckstyleException If an error occurs while building the properties.
     */
    public static String buildCombinedModuleChildren(
            final List<Configuration> children,
            final String indent)
            throws CheckstyleException {
        final StringBuilder builder = new StringBuilder(children.size() * 300);

        for (final Configuration child : children) {
            final String childProperties = buildProperties(child, indent + "    ");
            if (childProperties.isEmpty()) {
                builder.append(indent)
                        .append(MODULE_TAG)
                        .append(child.getName())
                        .append("\"/>\n\n");
            }
            else {
                builder.append(indent).append(MODULE_TAG).append(child.getName()).append("\">\n")
                        .append(childProperties).append('\n')
                        .append(indent).append("</module>\n\n");
            }
        }

        return builder.toString().trim();
    }

    /**
     * Copies each child of {@code targetModule}, assigning it the given id,
     * and appends the copies to {@code combinedChildren}.
     *
     * @param targetModule The module whose children should be copied.
     * @param newId The id to assign to each copied child.
     * @param combinedChildren The list to which copied children are appended.
     */
    public static void appendCopiedChildren(
            final Configuration targetModule,
            final String newId,
            final List<Configuration> combinedChildren) {
        for (final Configuration child : targetModule.getChildren()) {
            final Configuration newChild = copyConfiguration(child, newId);
            combinedChildren.add(newChild);
        }
    }

    /**
     * Creates a deep copy of the given configuration.
     *
     * @param config the configuration to copy
     * @param newId the new ID to assign to the copied configuration
     * @return a new {@link Configuration} that is a deep copy of the provided configuration
     */
    private static Configuration copyConfiguration(final Configuration config,
                                                   final String newId) {
        final DefaultConfiguration newConfig = new DefaultConfiguration(config.getName());

        for (final String name : config.getPropertyNames()) {
            try {
                final String value = config.getProperty(name);
                newConfig.addProperty(name, value);
            }
            catch (CheckstyleException ex) {
                // Property not found, skipping
            }
        }

        newConfig.addProperty("id", newId);

        return newConfig;
    }

    /**
     * Builds the XML properties for a given Checkstyle configuration.
     *
     * @param config The Checkstyle configuration whose properties are to be built.
     * @param indent The indentation to apply to each property.
     * @return The XML content of the properties as a string.
     * @throws CheckstyleException If an error occurs while retrieving properties.
     */
    private static String buildProperties(
            final Configuration config,
            final String indent) throws CheckstyleException {
        final String[] propertyNames = config.getPropertyNames();
        final StringBuilder builder = new StringBuilder(propertyNames.length * 50);
        final List<String> sortedPropertyNames = new ArrayList<>(Arrays.asList(propertyNames));
        Collections.sort(sortedPropertyNames);

        for (final String propertyName : sortedPropertyNames) {
            final String propertyValue = config.getProperty(propertyName);
            if ("id".equals(propertyName)) {
                final List<String> idValues =
                        new ArrayList<>(Arrays.asList(propertyValue.split(",")));
                Collections.sort(idValues);
                for (final String value : idValues) {
                    appendProperty(builder, indent, propertyName, value.trim());
                }
            }
            else {
                appendProperty(builder, indent, propertyName, propertyValue);
            }
        }
        return builder.toString();
    }

    /**
     * Appends a property in XML format to the provided StringBuilder.
     *
     * @param builder the StringBuilder to append to
     * @param indent the indentation to apply before the property tag
     * @param name the name of the property
     * @param value the value of the property
     */
    private static void appendProperty(final StringBuilder builder, final String indent,
                                       final String name, final String value) {
        if (builder.length() > 0) {
            builder.append('\n');
        }

        final boolean containsDoubleQuote = value.contains("\"");
        final boolean containsSingleQuote = value.contains("'");

        final char quote;
        if (containsDoubleQuote && !containsSingleQuote) {
            quote = '\'';
        }
        else {
            quote = '"';
        }

        final String escapedValue = escapeXmlAttributeValue(value, quote);

        builder.append(indent)
                .append("<property name=\"")
                .append(escapeXml(name))
                .append("\" value=")
                .append(quote)
                .append(escapedValue)
                .append(quote)
                .append("/>");
    }

    /**
     * Escapes special XML characters in the input string.
     * Replaces &amp;, &lt;, &gt;, ", and ' with their corresponding XML entities.
     * Returns the original input if null or empty.
     *
     * @param input the string to escape
     * @return the escaped string or the original if null/empty
     */
    private static String escapeXml(final String input) {
        String result = input;
        if (input != null && !input.isEmpty()) {
            result = input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }
        return result;
    }

    /**
     * Escapes special characters in an XML attribute value.
     * Replaces &amp;, &lt;, &gt; with their corresponding XML entities.
     * Depending on the delimiter, either ' or " is also escaped.
     *
     * @param input     the string to escape
     * @param delimiter the delimiter used for the attribute (' or ")
     * @return the escaped string or the original if null/empty
     */
    private static String escapeXmlAttributeValue(final String input, final char delimiter) {
        String result = input;
        if (input != null && !input.isEmpty()) {
            result = result.replace("&", "&amp;")
                    .replace("<", "&lt;");
            if (delimiter == '\'') {
                result = result.replace("'", "&apos;");
            }
            else {
                result = result.replace("\"", "&quot;");
            }
        }
        return result;
    }
}
