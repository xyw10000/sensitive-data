package io.github.xyw10000.mybatis.encrypte.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author one.xu
 */
@ConfigurationProperties("mybatis.encrypte")
@Data
public class MybatisEncrypteConfig {
    private String encryptKey;
    private String encryptionService;
    /**
     * 针对多数据源情况配置
     * 需要注入的sqlSessionFactory benan id,多个逗号连接，为空则全部都需要
     */
    private String sqlSessionFactoryIds;
}
