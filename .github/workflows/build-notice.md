## 构建事项:
`material-icons-extended-android-1.7.0.aar` 里是 `classes.jar` 不是 `.jar` 名字，上面命令直接存成 `.jar` 也能被 Gradle 当 jar 读（AGP 会按后缀判断，若报错请改成`zcat` 解出 `classes.jar` 后保存）。
