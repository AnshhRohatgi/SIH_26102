package com.mplad.frauddetection.aiintegration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

/**
 * AnomalyExplanationService
 * --------------------------------------------------------------------------
 * Member 5's AI integration piece.
 *
 * What it does, step by step:
 *   1. Reads one row from detected_anomalies (written by Member 2's rule engine)
 *   2. Pulls the actual flagged record's data from whichever table it lives in
 *   3. Builds a plain prompt describing the rule + the numbers
 *   4. Calls the Claude API
 *   5. Saves the plain-language reply into anomaly_plain_language_explanations
 *
 * This service NEVER decides whether something is an anomaly — it only
 * explains anomalies that already exist in detected_anomalies.
 */
@Service
public class AnomalyExplanationService {

    private final JdbcTemplate jdbcTemplate;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${anthropic.api.key}")
    private String anthropicApiKey;

    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL_NAME = "claude-sonnet-4-6";

    public AnomalyExplanationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Main entry point: pass the anomaly_id that Member 2's rule engine created.
     */
    public void generateAndSaveExplanation(int anomalyId) {

        // Step 1: read the anomaly row
        Map<String, Object> anomaly = jdbcTemplate.queryForMap(
            "SELECT * FROM detected_anomalies WHERE anomaly_id = ?", anomalyId
        );

        String flaggedTable = (String) anomaly.get("flagged_table_name");
        String flaggedRecordId = (String) anomaly.get("flagged_record_id");
        String ruleName = (String) anomaly.get("rule_triggered_name");
        String ruleDescription = (String) anomaly.get("rule_triggered_description");

        // Step 2: pull the actual flagged record so the LLM has real numbers to explain
        String primaryKeyColumn = getPrimaryKeyColumnFor(flaggedTable);
        Map<String, Object> flaggedRecord = jdbcTemplate.queryForMap(
            "SELECT * FROM " + flaggedTable + " WHERE " + primaryKeyColumn + " = ?", flaggedRecordId
        );

        // Step 3: build a plain, factual prompt — no jargon, just what triggered the flag
        String prompt = buildExplanationPrompt(ruleName, ruleDescription, flaggedRecord);

        // Step 4: call Claude
        String explanationText = callClaudeApi(prompt);

        // Step 5: save it back
        jdbcTemplate.update(
            "INSERT INTO anomaly_plain_language_explanations " +
            "(anomaly_id, plain_language_explanation, ai_model_used, explanation_status) " +
            "VALUES (?, ?, ?, 'generated')",
            anomalyId, explanationText, MODEL_NAME
        );
    }

    private String buildExplanationPrompt(String ruleName, String ruleDescription, Map<String, Object> record) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are explaining a flagged financial record from a government scheme to a non-technical reviewer.\n\n");
        sb.append("Rule triggered: ").append(ruleName).append("\n");
        sb.append("What the rule checks: ").append(ruleDescription).append("\n\n");
        sb.append("Record data:\n");
        record.forEach((column, value) -> sb.append("- ").append(column).append(": ").append(value).append("\n"));
        sb.append("\nWrite a 2-4 sentence plain-language explanation of why this record looks suspicious. ");
        sb.append("Be factual and specific with the numbers. Do not use technical statistics terms. ");
        sb.append("Do not accuse anyone of fraud directly — say it 'requires review'.");
        return sb.toString();
    }

    private String callClaudeApi(String promptText) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
            "model", MODEL_NAME,
            "max_tokens", 300,
            "messages", List.of(Map.of("role", "user", "content", promptText))
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        Map<?, ?> response = restTemplate.postForObject(CLAUDE_API_URL, requestEntity, Map.class);

        List<?> contentBlocks = (List<?>) response.get("content");
        Map<?, ?> firstBlock = (Map<?, ?>) contentBlocks.get(0);
        return (String) firstBlock.get("text");
    }

    /**
     * Every table's primary key column name, spelled out here so the mapping
     * is obvious to anyone reading this file — no guessing required.
     */
    private String getPrimaryKeyColumnFor(String tableName) {
        return switch (tableName) {
            case "fund_allocations_per_year" -> "allocation_id";
            case "recommended_and_sanctioned_works" -> "work_id";
            case "vendor_bills_and_payments" -> "bill_id";
            case "utilization_certificates" -> "uc_id";
            default -> throw new IllegalArgumentException("Unknown flagged table: " + tableName);
        };
    }
}
