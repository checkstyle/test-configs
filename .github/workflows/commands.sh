#!/bin/bash
set -e

function list_tasks() {
  cat "${0}" | sed -E -n 's/^([a-zA-Z0-9\-]*)\)$/\1/p' | sort
}

case $1 in

git-diff)
  if [ "$(git status | grep 'Changes not staged\|Untracked files')" ]; then
    printf "Please clean up or update .gitattributes file.\nGit status output:\n"
    printf "Top 300 lines of diff:\n"
    git status
    git diff | head -n 300
    false
  fi
  ;;

ci-temp-check)
    fail=0
    mkdir -p .ci-temp
    if [ -z "$(ls -A .ci-temp)" ]; then
        echo "Folder .ci-temp/ is empty."
    else
        echo "Folder .ci-temp/ is not empty. Verification failed."
        echo "Contents of .ci-temp/:"
        fail=1
    fi
    ls -A .ci-temp
    sleep 5s
    exit $fail
  ;;


*)
  echo "Unexpected argument: $1"
  echo "Supported tasks:"
  list_tasks "${0}"
  false
  ;;

esac
