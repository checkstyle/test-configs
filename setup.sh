#!/bin/bash

# Create the .ci-temp directory if it doesn't exist
mkdir -p .ci-temp

# Clone the Checkstyle repository if it hasn't been cloned already
if [ ! -d ".ci-temp/checkstyle" ]; then
  git clone --depth 1 --branch master https://github.com/checkstyle/checkstyle.git .ci-temp/checkstyle
else
  echo "Checkstyle repository already cloned."
fi
