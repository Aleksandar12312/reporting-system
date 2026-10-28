package com.example.monitor.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * On start: creates the tables unit_issue_ack and unit_reset if they are missing (db/schema_issues.sql) and checks
 * ranking.components against event_source, so a wrong event name is in the log right away.
 */
@Component
public class IssueTableInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IssueTableInitializer.class);

    private final IssueRepository repository;
    private final ComponentCatalog catalog;

    public IssueTableInitializer(IssueRepository repository, ComponentCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            repository.createTableIfMissing();
        } catch (DataAccessException e) {
            // The API then answers 503 for these calls; everything else keeps working
            log.error("Could not create tables unit_issue_ack / unit_reset", e);
        }
        try {
            catalog.components().forEach(c -> log.info("Component {}: {} work={} problems={}",
                    c.id(), c.metric(), c.work(), c.problems()));
        } catch (RuntimeException e) {
            // Every call that needs the components then fails with the same reason in the log
            log.error("ranking.components cannot be used: {}", e.getMessage());
        }
    }
}
