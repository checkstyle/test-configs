# Example6 Configs

Bellow are two options that will do the same but use different versions
of github actions in checkstyle repository.


### Option 1
Trigger report generation by comment in Pull Request:
```
Github, generate report for IllegalImport/Example6
```

### Option 2

Paste below given to PR description to use such test configs:
```
Report label: IllegalImport/Example6
Diff Regression config: https://raw.githubusercontent.com/checkstyle/test-configs/main/IllegalImport/Example6/config.xml
Diff Regression projects: https://raw.githubusercontent.com/checkstyle/test-configs/main/IllegalImport/Example6/list-of-projects.properties
```

Trigger report generation by comment in Pull Request:
```
Github, generate report
```
or as alternative by comment
```
Github, generate report for configs in PR description
```
