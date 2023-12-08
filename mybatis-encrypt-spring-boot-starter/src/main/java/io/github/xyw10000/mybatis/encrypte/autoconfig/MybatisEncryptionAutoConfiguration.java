package io.github.xyw10000.mybatis.encrypte.autoconfig;

import io.github.xyw10000.mybatis.encrypte.config.MybatisEncrypteConfig;
import io.github.xyw10000.mybatis.encrypte.interceptor.EncryptionInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Properties;

/**
 * @author one.xu
 */
@ConditionalOnProperty(name = "mybatis.encrypte.enabled", havingValue = "true")
@EnableConfigurationProperties({MybatisEncrypteConfig.class})
@Configuration
@Slf4j
public class MybatisEncryptionAutoConfiguration {
    @Bean
    public EncryptionInterceptor dataSecurityInterceptor(Map<String, SqlSessionFactory> sqlSessionFactoryMap,
                                                         MybatisEncrypteConfig mybatisEncrypteConfig) {
        EncryptionInterceptor encryptionInterceptor = new EncryptionInterceptor();
        Properties properties = new Properties();
        if (StringUtils.isNotBlank(mybatisEncrypteConfig.getEncryptKey())) {
            properties.setProperty("encryptKey", mybatisEncrypteConfig.getEncryptKey());
        }

        if (StringUtils.isNotBlank(mybatisEncrypteConfig.getEncryptionService())) {
            properties.setProperty("encryptionService", mybatisEncrypteConfig.getEncryptionService());
        }

        if (StringUtils.isNotBlank(mybatisEncrypteConfig.getExampleClassConfig())) {
            properties.setProperty("exampleClassConfigMap", mybatisEncrypteConfig.getExampleClassConfig());
        }
        encryptionInterceptor.setProperties(properties);
        boolean isAll = StringUtils.isBlank(mybatisEncrypteConfig.getSqlSessionFactoryIds());
        for (Map.Entry<String, SqlSessionFactory> m : sqlSessionFactoryMap.entrySet()) {
            if (!isAll && !(mybatisEncrypteConfig.getSqlSessionFactoryIds().contains(m.getKey()))) {
                continue;
            }
            log.info("SqlSessionFactory:{}注入敏感字段拦截器", m.getKey());
            m.getValue().getConfiguration().addInterceptor(encryptionInterceptor);
        }
        return encryptionInterceptor;
    }


}
