# Example1 Configs

Bellow are two options that will do the same but use different versions
of github actions in checkstyle repository.


### Option 1
Trigger report generation by comment in Pull Request:
```
Github, generate report for RecordComponentName/Example1
```

### Option 2

Paste below given to PR description to use such test configs:
```
Report label: RecordComponentName/Example1
Diff Regression config: https://raw.githubusercontent.com/checkstyle/test-configs/main/RecordComponentName/Example1/config.xml
Diff Regression projects: https://raw.githubusercontent.com/checkstyle/test-configs/main/RecordComponentName/Example1/list-of-projects.properties
```

Trigger report generation by comment in Pull Request:
```
Github, generate report
```
or as alternative by comment
```
Github, generate report for configs in PR description
```
