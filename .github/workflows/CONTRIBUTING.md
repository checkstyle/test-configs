# Contributing to Checkstyle Test Configurations Generator

We welcome contributions to improve the test configurations for Checkstyle. This document outlines the process for contributing to this project.

## Getting Started

1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```
   git clone https://github.com/your-username/test-configs.git
   cd test-configs
   ```
3. Set up the project:
   ```
   ./setup.sh
   ```

## Making Changes

1. Create a new branch for your changes:
   ```
   git checkout -b your-feature-branch
   ```
2. Make your changes in the `extractor` directory.
3. Build and test your changes:
   ```
   cd extractor
   ./gradlew clean build
   ```

## Custom Configuration Bundles

This repository can store any configuration bundle. Any custom config and list of projects can be placed in a special folder and stored in this repository. The names of files should be the same as in all other folders (typically `config.xml` for the configuration file and `list-of-projects.properties` for the list of projects). This allows for flexibility in storing and managing various configuration setups while maintaining consistency across the repository.

## Submitting Changes

1. Push your changes to your fork on GitHub.
2. Create a pull request from your fork to the main repository.
3. Describe your changes and their purpose in the pull request description.

## Reporting Issues

If you find a bug or have a suggestion for improvement, please open an issue on the GitHub repository.

Thank you for contributing to the Checkstyle Test Configurations Generator!