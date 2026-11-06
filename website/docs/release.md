# 发布到 Central

版本 `0.1.0`。`mvn -DskipTests package` 不签名、不上传。真正发布使用 `release` profile 和 Central Portal，不要把令牌写进仓库。

命名空间 `io.github.awenjackson` 已在 [Central Portal](https://central.sonatype.com/) 验证。父 POM 的 `url` / `scm` 指向 `https://github.com/awenjackson/queryengine`。如果实际仓库不是这个地址，发布前改掉。

本机 `~/.m2/settings.xml`：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>${env.SONATYPE_USERNAME}</username>
      <password>${env.SONATYPE_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

```bash
export SONATYPE_USERNAME=你的用户名
export SONATYPE_PASSWORD=你的token
export GPG_KEYNAME=你的密钥id
export GPG_PASSPHRASE=你的密钥口令
mvn -P release -DskipTests deploy
```

`release` 会附上 sources、javadoc，并用环境变量里的 GPG 密钥签名。`autoPublish` 为 `false`，上传后到 Portal 里确认再发布。`queryengine-boot` 和 `queryengine-demo` 不会上传。

本地预览本站：

```bash
npm run docs:dev
```

构建：

```bash
npm run docs:build
```
