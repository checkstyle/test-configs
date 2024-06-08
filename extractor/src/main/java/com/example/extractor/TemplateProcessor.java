package com.example.extractor;

public class TemplateProcessor {

    public static String replacePlaceholders(String template, String moduleContent) {
        // Replace the treewalker_module_PLACEHOLDER with the actual module content including properties
        template = template.replace("<!-- treewalker_module_PLACEHOLDER -->", moduleContent);

        return template;
    }
}
