#!/bin/bash

function git_check_changes() {
  if [ "$(git status --porcelain)" ]; then
    echo "Changes detected:"
    echo "Git status output:"
    git status
    echo "Top 300 lines of diff:"
    git diff | head -n 300
    echo "changes=true" >> $GITHUB_OUTPUT
  else
    echo "No changes detected."
    echo "changes=false" >> $GITHUB_OUTPUT
  fi
}

function list_tasks() {
  sed -n 's/^function \(.*\)() {$/\1/p' "$0" | grep -v '^list_tasks$' | sort
}

case $1 in
  git_check_changes)
    git_check_changes
    ;;
  *)
    echo "Unexpected argument: $1"
    echo "Supported tasks:"
    list_tasks
    exit 1
    ;;
esac
