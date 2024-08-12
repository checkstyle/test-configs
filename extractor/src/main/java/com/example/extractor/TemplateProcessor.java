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

/**
 * Utility class for processing templates and replacing placeholders.
 * This class provides methods to replace specific placeholders in templates
 * with actual module content.
 */
public final class TemplateProcessor {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private TemplateProcessor() {
        // This constructor is intentionally empty. Nothing special is needed here.
    }

    /**
     * Replaces placeholders in the given template with the provided module content.
     *
     * @param template      The original template string containing placeholders.
     * @param moduleContent The content to replace the placeholder with.
     * @param isTreeWalker  A flag indicating whether the module is a TreeWalker module.
     * @return The template with placeholders replaced by the module content.
     */
    public static String replacePlaceholders(final String template,
                                             final String moduleContent,
                                             final boolean isTreeWalker) {
        final String placeholder;

        if (isTreeWalker) {
            placeholder = "<!-- treewalker_module_PLACEHOLDER -->";
        }
        else {
            placeholder = "<!-- Non/TreeWalker module placeholder -->";
        }

        return template.replace(placeholder, moduleContent);
    }
}
