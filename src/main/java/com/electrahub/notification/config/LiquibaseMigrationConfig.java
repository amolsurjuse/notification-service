package com.electrahub.notification.config;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiquibaseMigrationConfig {
    @Bean
    SpringLiquibase notificationLiquibase(
            DataSource dataSource,
            @Value("${spring.liquibase.enabled:true}") boolean enabled,
            @Value("${spring.liquibase.change-log:classpath:db/changelog/db.changelog-master.yaml}") String changeLog,
            @Value("${spring.liquibase.default-schema:public}") String defaultSchema,
            @Value("${spring.liquibase.liquibase-schema:public}") String liquibaseSchema
    ) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setShouldRun(enabled);
        liquibase.setChangeLog(changeLog);
        liquibase.setDefaultSchema(defaultSchema);
        liquibase.setLiquibaseSchema(liquibaseSchema);
        return liquibase;
    }
}
