package com.mplad.frauddetection.api;

import com.mplad.frauddetection.aiintegration.AnomalyExplanationService;
import com.mplad.frauddetection.statisticaldetection.StatisticalAnomalyDetectionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AnomalyDetectionController
 * --------------------------------------------------------------------------
 * The API surface Member 3's dashboard (or Postman, for your demo) talks to.
 * Three endpoints, matching the three steps of the pipeline:
 *   1. POST /api/anomalies/detect            -> runs Z-score + IQR, fills detected_anomalies
 *   2. POST /api/anomalies/{id}/explain       -> calls Claude, fills anomaly_plain_language_explanations
 *   3. GET  /api/anomalies                    -> lists anomalies with their explanation (if generated)
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalyDetectionController {

    private final StatisticalAnomalyDetectionService statisticalAnomalyDetectionService;
    private final AnomalyExplanationService anomalyExplanationService;
    private final JdbcTemplate jdbcTemplate;

    public AnomalyDetectionController(
        StatisticalAnomalyDetectionService statisticalAnomalyDetectionService,
        AnomalyExplanationService anomalyExplanationService,
        JdbcTemplate jdbcTemplate
    ) {
        this.statisticalAnomalyDetectionService = statisticalAnomalyDetectionService;
        this.anomalyExplanationService = anomalyExplanationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Step 1: run the statistical checks. Safe to call repeatedly — already-flagged records are skipped. */
    @PostMapping("/detect")
    public Map<String, String> runDetection() {
        statisticalAnomalyDetectionService.runAllStatisticalChecks();
        return Map.of("status", "Z-score and IQR checks complete. See detected_anomalies for results.");
    }

    /** Step 2: generate the plain-language explanation for one flagged anomaly. */
    @PostMapping("/{anomalyId}/explain")
    public Map<String, String> explainAnomaly(@PathVariable int anomalyId) {
        anomalyExplanationService.generateAndSaveExplanation(anomalyId);
        return Map.of("status", "Explanation generated for anomaly_id " + anomalyId);
    }

    /** Step 3: everything the dashboard needs in one call — anomaly + its explanation, if one exists. */
    @GetMapping
    public List<Map<String, Object>> listAnomaliesWithExplanations() {
        return jdbcTemplate.queryForList(
            "SELECT a.anomaly_id, a.flagged_table_name, a.flagged_record_id, a.mp_id, " +
            "       a.rule_triggered_name, a.anomaly_score, a.detection_method, a.review_status, " +
            "       e.plain_language_explanation, e.explanation_status " +
            "FROM detected_anomalies a " +
            "LEFT JOIN anomaly_plain_language_explanations e ON e.anomaly_id = a.anomaly_id " +
            "ORDER BY a.anomaly_score DESC"
        );
    }

    /** Convenience: run detection AND explain every newly flagged anomaly in one call, for demo purposes. */
    @PostMapping("/detect-and-explain-all")
    public Map<String, String> detectAndExplainAll() {
        statisticalAnomalyDetectionService.runAllStatisticalChecks();
        List<Map<String, Object>> unexplained = jdbcTemplate.queryForList(
            "SELECT a.anomaly_id FROM detected_anomalies a " +
            "LEFT JOIN anomaly_plain_language_explanations e ON e.anomaly_id = a.anomaly_id " +
            "WHERE e.explanation_id IS NULL"
        );
        for (Map<String, Object> row : unexplained) {
            anomalyExplanationService.generateAndSaveExplanation((Integer) row.get("anomaly_id"));
        }
        return Map.of("status", "Detected anomalies and generated " + unexplained.size() + " new explanation(s).");
    }
}
