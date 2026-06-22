

package com.virtuehire.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaFixer {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixSchema() {
        makeLegacyQuestionTextColumnNullable();
        makeLegacyQuestionOptionColumnsNullable();
        makeTestIdColumnNullable();

        /*
         * try {
         * System.out.println("Checking and fixing database schema index...");
         * 
         * // 1. Drop the incorrect index if it exists
         * // Hibernate's auto-generated index name from the screenshot
         * String wrongIndex = "UKdtpqf2umuoipb83tq9ofl8ahqc";
         * 
         * try {
         * jdbcTemplate.execute("ALTER TABLE assessment_results DROP INDEX " +
         * wrongIndex);
         * System.out.println("Dropped faulty index: " + wrongIndex);
         * } catch (Exception e) {
         * System.out.println("Faulty index not found or already dropped: " +
         * e.getMessage());
         * }
         * 
         * // 2. Add the correct unique index on (candidate_id, subject, level)
         * // Use a stable name so we don't duplicate it
         * try {
         * jdbcTemplate.execute(
         * "ALTER TABLE assessment_results ADD UNIQUE INDEX UK_candidate_subject_level (candidate_id, subject, level)"
         * );
         * System.out.println("Created correct 3-column unique index.");
         * } catch (Exception e) {
         * System.out.println("Correct index already exists or could not be created: " +
         * e.getMessage());
         * }
         * 
         * } catch (Exception e) {
         * System.err.println("Failed to fix database schema: " + e.getMessage());
         * e.printStackTrace();
         * }
         */
    }

    private void makeLegacyQuestionTextColumnNullable() {
        try {
            // The Question entity maps to the existing questions.text column.
            // If a newer question_text column also exists from a previous run, it is
            // not populated by inserts and must be nullable for legacy schemas.
            if (columnExists("questions", "question_text")) {
                jdbcTemplate.execute("ALTER TABLE questions MODIFY question_text VARCHAR(1000) NULL");
            }
        } catch (Exception e) {
            System.out.println("Question schema compatibility check skipped for question_text: " + e.getMessage());
        }
    }

    private void makeLegacyQuestionOptionColumnsNullable() {
        String[] legacyOptionColumns = {
                "optiona", "optionb", "optionc", "optiond",
                "option_a", "option_b", "option_c", "option_d"
        };

        for (String column : legacyOptionColumns) {
            try {
                if (columnExists("questions", column)) {
                    jdbcTemplate.execute("ALTER TABLE questions MODIFY " + column + " VARCHAR(1000) NULL");
                }
            } catch (Exception e) {
                System.out.println("Question schema compatibility check skipped for " + column + ": " + e.getMessage());
            }
        }
    }

    private void makeTestIdColumnNullable() {
        try {
            if (columnExists("questions", "test_id")) {
                jdbcTemplate.execute("ALTER TABLE questions MODIFY test_id BIGINT NULL");
                System.out.println("Made test_id column nullable in questions table");
            }
        } catch (Exception e) {
            System.out.println("Question schema compatibility check skipped for test_id: " + e.getMessage());
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);

        return count != null && count > 0;
    }
}
