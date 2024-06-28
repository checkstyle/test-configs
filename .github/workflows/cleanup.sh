#!/bin/bash

keep_dirs=(".github" "extractor" "infra" ".idea" ".ci-temp" "gradle")
grep_pattern=$(printf "|%s" "${keep_dirs[@]}")
grep_pattern=${grep_pattern:1}

find . -maxdepth 1 -type d ! -name "." \
  | grep -iEv "^./($grep_pattern|.git)$" \
  | xargs -I {} rm -rf {}

echo "Removed generated config folders."
