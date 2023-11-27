
> **Github：[SFTP Connect Pool](https://github.com/xyw10000/sftp-pool-spring-boot-starter.git)**


> **欢迎使用和Star支持，如使用过程中发现问题，可以提出Issue，我会尽力修复**

## 介绍

sftp-pool 是一个 SFTP 的 SpringBoot Starter，使用Apache commons-pool2管理连接
## Maven 依赖

依赖 Apache commons-pool2：
```xml
<dependency>
    <groupId>io.github.xyw10000</groupId>
    <artifactId>sftp-pool-spring-boot-starter</artifactId>
    <version>x.x.x</version>
</dependency>
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
    <version>x.x.x</version>
</dependency>
```



## 配置

详细的配置属性说明见开发工具的自动提示。

### 密码登录

```properties
sftp.pool.enabled=true
sftp.pool.connection.host = xxx
sftp.pool.connection.username = xxx
sftp.pool.connection.password = xxx
sftp.pool.connection.port = 22
```


### 连接池（可以不配置）

```properties
sftp.pool.config.minIdle=1
sftp.pool.config.maxWait=2000
```

## 用法

提供 SftpTemplate 类，注入SftpTemplate对象即可
