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
        } else {
            placeholder = "<!-- Non/TreeWalker module placeholder -->";
        }

        return template.replace(placeholder, moduleContent);
    }
}