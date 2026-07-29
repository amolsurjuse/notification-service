package com.electrahub.notification.service;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplateMigrationTest {
    @Test
    void liquibaseSeedsTheElectraHubReceiptTemplateFromCodeOwnedResources() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:notification-template;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection()) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(),
                    database
            );
                liquibase.update(new Contexts(), new LabelExpression());

            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         select p.display_name, t.subject_template, t.body_template
                         from notification.notification_projects p
                         join notification.notification_templates t on t.project_key = p.project_key
                         where p.project_key = 'electrahub'
                           and t.template_key = 'charging-receipt-ready'
                           and t.channel = 'EMAIL'
                         """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("display_name")).isEqualTo("ElectraHub");
                assertThat(result.getString("subject_template")).contains("projectDisplayName");
                assertThat(result.getString("body_template"))
                        .contains("cid:project-logo")
                        .contains("Total paid");
            }
        }
    }
}
