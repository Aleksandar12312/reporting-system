package com.example.monitor.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Creates the table unit_issue_ack on start if it is missing (db/schema_issues.sql). */
@Component
public class IssueTableInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IssueTableInitializer.class);

    private final IssueRepository repository;

    public IssueTableInitializer(IssueRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            repository.createTableIfMissing();
        } catch (DataAccessException e) {
            // The API then answers 503 for these calls; everything else keeps working
            log.error("Could not create table unit_issue_ack", e);
        }
    }
}
