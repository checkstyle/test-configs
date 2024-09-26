# All Examples in One Configs

Bellow are two options that will do the same but use different versions
of github actions in checkstyle repository.


### Option 1
Trigger report generation by comment in Pull Request:
```
Github, generate report for TypeName/all-examples-in-one
```

### Option 2

Paste below given to PR description to use such test configs:
```
Report label: TypeName/all-examples-in-one
Diff Regression config: https://raw.githubusercontent.com/checkstyle/test-configs/main/TypeName/all-examples-in-one/config.xml
Diff Regression projects: https://raw.githubusercontent.com/checkstyle/test-configs/main/TypeName/all-examples-in-one/list-of-projects.properties
```

Trigger report generation by comment in Pull Request:
```
Github, generate report
```
or as alternative by comment
```
Github, generate report for configs in PR description
```
