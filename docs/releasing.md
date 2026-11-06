# 发布到 Maven Central

当前版本 `0.1.0`，groupId `io.github.awenjackson`。日常 `mvn -DskipTests package` 不签名、不上传。发布走 `release` profile，使用 Central Portal（`central-publishing-maven-plugin`）。不要把令牌或私钥写进仓库。

会发布的构件：`queryengine`（父 POM）、`queryengine-bom`、`queryengine-core`、`queryengine-dialect-mysql`、`queryengine-dialect-dameng`、`queryengine-spring-boot-starter`、`queryengine-web`。`queryengine-boot` 和 `queryengine-demo` 设置了 `maven.deploy.skip`。

命名空间 `io.github.awenjackson` 已在 [Central Portal](https://central.sonatype.com/) 验证。父 POM 的 `url` 和 `scm` 指向 `https://github.com/awenjackson/queryengine`。如果实际仓库不是这个地址，发布前改掉。

`~/.m2/settings.xml` 只放在本机：

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

`SONATYPE_PASSWORD` 用 Portal 的 token。GPG 用环境变量，不要写进 POM：

```bash
export SONATYPE_USERNAME=你的用户名
export SONATYPE_PASSWORD=你的token
export GPG_KEYNAME=你的密钥id
export GPG_PASSPHRASE=你的密钥口令
mvn -P release -DskipTests deploy
```

`release` profile 会附上 sources jar、javadoc jar，并用 `maven-gpg-plugin` 签名。`autoPublish` 是 `false`：上传后到 Central Portal 里检查，再手动发布。没有密钥时不要加 `-P release`。
