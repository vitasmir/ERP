package com.example.erp.frontend.base;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfiguration {
    @Bean
    public HikariDataSource sessionDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(requiredEnvironmentVariable("DB_URL"));
        config.setUsername(requiredEnvironmentVariable("DB_USERNAME"));
        config.setPassword(requiredEnvironmentVariable("DB_PASSWORD"));
        config.setPoolName("erp-frontend-sessions");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource sessionDataSource) {
        return new DataSourceTransactionManager(sessionDataSource);
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath("/");
        return serializer;
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable is missing: " + name);
        }
        return value;
    }
}
