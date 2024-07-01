#!/bin/bash

# Create the .ci-temp directory if it doesn't exist
mkdir -p .ci-temp

# Check if the Checkstyle repository exists
if [ -d ".ci-temp/checkstyle" ]; then
    echo "Updating existing Checkstyle repository..."
    cd .ci-temp/checkstyle
    git fetch origin
    git reset --hard origin/master
    git clean -fd
    cd ../..
else
    echo "Cloning Checkstyle repository..."
    git clone --depth 1 --branch master https://github.com/checkstyle/checkstyle.git .ci-temp/checkstyle
fi

echo "Checkstyle repository is now up to date with the latest master branch."