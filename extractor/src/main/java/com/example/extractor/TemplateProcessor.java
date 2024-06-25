package com.example.extractor;

public class TemplateProcessor {

    public static String replacePlaceholders(String template, String moduleContent, boolean isTreeWalker) {
        if (isTreeWalker) {
            // Replace the treewalker_module_PLACEHOLDER with the actual module content including properties
            template = template.replace("<!-- treewalker_module_PLACEHOLDER -->", moduleContent);
        } else {
            // Replace the Non/TreeWalker module placeholder with the actual module content including properties
            template = template.replace("<!-- Non/TreeWalker module placeholder -->", moduleContent);
        }

        return template;
    }
}