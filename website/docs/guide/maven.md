# Maven 依赖

groupId `io.github.awenjackson`，版本 `0.1.0`。

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.github.awenjackson</groupId>
      <artifactId>queryengine-bom</artifactId>
      <version>0.1.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

| 构件 | 何时用 |
| --- | --- |
| `queryengine-core` | 只要引擎，不要 Spring |
| `queryengine-dialect-mysql` | MySQL 方言，依赖 core |
| `queryengine-dialect-dameng` | 达梦方言。驱动 `com.dameng:DmJdbcDriver18` 由宿主自己加 |
| `queryengine-spring-boot-starter` | 注册 `QueryEngine` Bean，不开放 HTTP |
| `queryengine-web` | NestQL、GraphQL、REST。已带 starter 和 `spring-boot-starter-web` |
| `queryengine-bom` | 上面这些库的版本对齐 |

只要 HTTP：

```xml
<dependency>
  <groupId>io.github.awenjackson</groupId>
  <artifactId>queryengine-web</artifactId>
</dependency>
<dependency>
  <groupId>io.github.awenjackson</groupId>
  <artifactId>queryengine-dialect-mysql</artifactId>
</dependency>
```
