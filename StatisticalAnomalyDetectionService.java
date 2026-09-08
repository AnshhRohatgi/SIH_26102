package com.mplad.frauddetection.statisticaldetection;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/**
 * StatisticalAnomalyDetectionService
 * --------------------------------------------------------------------------
 * Runs the Z-score and IQR checks that feed detected_anomalies.
 * Pairs with AnomalyExplanationService: this class decides WHAT is anomalous,
 * that class explains WHY in plain language. Never mix the two jobs.
 *
 * LIMITATION (worth mentioning in your report/demo): both checks currently
 * compare a value against ALL rows in the table. In a real dataset you'd
 * usually group first — e.g. compare utilization_percentage within the same
 * financial_year, or sanctioned_cost_inr within the same sector_category —
 * since "normal" for Water projects may not be "normal" for Education
 * projects. For SIH's scale of sample data, table-wide stats are fine to
 * demonstrate the method; grouping is a natural v2 improvement to mention.
 */
@Service
public class StatisticalAnomalyDetectionService {

    private final JdbcTemplate jdbcTemplate;

    // Z-score threshold: how many standard deviations from the mean before
    // a value is "far enough" to flag. 2.5 is a common cutoff for financial
    // outlier checks (roughly the top/bottom ~1% under a normal distribution).
    private static final double Z_SCORE_THRESHOLD = 2.5;

    // IQR multiplier: the standard "Tukey's fence" constant.
    // 1.5x = ordinary outlier, 3.0x = extreme outlier. We use 1.5.
    private static final double IQR_MULTIPLIER = 1.5;

    public StatisticalAnomalyDetectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Which numeric column, in which table, gets checked — and what that
     * table's primary key / mp_id columns are called. Spelled out explicitly
     * because the schema doesn't name these consistently across tables.
     */
    private record ColumnCheck(
        String table,
        String primaryKeyColumn,
        String mpIdColumn,          // null if the table has no direct mp_id column
        String numericColumn,
        String ruleLabel
    ) {}

    private static final List<ColumnCheck> CHECKS = List.of(
        new ColumnCheck("utilization_certificates", "uc_id", "mp_id",
            "utilization_percentage", "utilization_percentage_outlier"),
        new ColumnCheck("vendor_bills_and_payments", "bill_id", null,
            "claimed_amount_inr", "claimed_amount_outlier"),
        new ColumnCheck("vendor_bills_and_payments", "bill_id", null,
            "net_amount_paid_inr", "net_amount_paid_outlier"),
        new ColumnCheck("recommended_and_sanctioned_works", "work_id", "mp_id",
            "sanctioned_cost_inr", "sanctioned_cost_outlier")
    );

    /** Entry point: run both checks across every configured column. Call this from a controller or a scheduled job. */
    public void runAllStatisticalChecks() {
        for (ColumnCheck check : CHECKS) {
            runZScoreCheck(check);
            runIqrCheck(check);
        }
    }

    // ------------------------------------------------------------------
    // Z-SCORE
    // z = (value - mean) / standard deviation
    // Flags values that sit far from the average in std-dev units.
    // Assumes roughly bell-shaped data — sensitive to extreme outliers
    // dragging the mean/stddev themselves, which is why IQR runs too.
    // ------------------------------------------------------------------
    private void runZScoreCheck(ColumnCheck check) {
        List<Map<String, Object>> rows = fetchRows(check);
        List<Double> values = extractValues(rows, check.numericColumn());
        if (values.size() < 3) return; // stddev is meaningless on tiny samples

        double mean = mean(values);
        double stddev = stddev(values, mean);
        if (stddev == 0) return; // every value identical — nothing can be an outlier

        for (Map<String, Object> row : rows) {
            double value = toDouble(row.get(check.numericColumn()));
            double z = (value - mean) / stddev;
            if (Math.abs(z) > Z_SCORE_THRESHOLD) {
                String description = String.format(
                    "%s of %.2f is %.2f standard deviations from the average of %.2f seen across %s (flag threshold: %.1f).",
                    check.numericColumn(), value, z, mean, check.table(), Z_SCORE_THRESHOLD
                );
                insertAnomaly(check, row, "zscore", description, Math.min(100, Math.abs(z) * 15));
            }
        }
    }

    // ------------------------------------------------------------------
    // IQR (Interquartile Range)
    // IQR = Q3 - Q1. Fence = [Q1 - 1.5*IQR, Q3 + 1.5*IQR].
    // Flags anything outside the fence. More robust than Z-score when data
    // is skewed or already has extreme values, since quartiles aren't
    // dragged around by outliers the way mean/stddev are.
    // ------------------------------------------------------------------
    private void runIqrCheck(ColumnCheck check) {
        List<Map<String, Object>> rows = fetchRows(check);
        List<Double> values = extractValues(rows, check.numericColumn());
        if (values.size() < 4) return; // need enough points for quartiles to mean anything

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double q1 = percentile(sorted, 25);
        double q3 = percentile(sorted, 75);
        double iqr = q3 - q1;
        double lowerFence = q1 - IQR_MULTIPLIER * iqr;
        double upperFence = q3 + IQR_MULTIPLIER * iqr;

        for (Map<String, Object> row : rows) {
            double value = toDouble(row.get(check.numericColumn()));
            if (value < lowerFence || value > upperFence) {
                double distancePastFence = value > upperFence ? value - upperFence : lowerFence - value;
                String description = String.format(
                    "%s of %.2f falls outside the normal range of %.2f to %.2f seen across %s.",
                    check.numericColumn(), value, lowerFence, upperFence, check.table()
                );
                double score = iqr == 0 ? 100 : Math.min(100, distancePastFence / iqr * 25);
                insertAnomaly(check, row, "iqr", description, score);
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private List<Map<String, Object>> fetchRows(ColumnCheck check) {
        return jdbcTemplate.queryForList("SELECT * FROM " + check.table());
    }

    private List<Double> extractValues(List<Map<String, Object>> rows, String column) {
        List<Double> values = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(column);
            if (raw != null) values.add(toDouble(raw));
        }
        return values;
    }

    private double toDouble(Object raw) {
        if (raw instanceof BigDecimal bd) return bd.doubleValue();
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(raw.toString());
    }

    private double mean(List<Double> values) {
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private double stddev(List<Double> values, double mean) {
        double sumSquaredDiff = 0;
        for (double v : values) sumSquaredDiff += Math.pow(v - mean, 2);
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    /** Linear-interpolation percentile — matches how most stats libraries compute Q1/Q3. */
    private double percentile(List<Double> sortedValues, double percentileWanted) {
        int n = sortedValues.size();
        double rank = percentileWanted / 100.0 * (n - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        if (lowerIndex == upperIndex) return sortedValues.get(lowerIndex);
        double fraction = rank - lowerIndex;
        return sortedValues.get(lowerIndex) + fraction * (sortedValues.get(upperIndex) - sortedValues.get(lowerIndex));
    }

    private void insertAnomaly(ColumnCheck check, Map<String, Object> row, String method, String description, double score) {
        String recordId = String.valueOf(row.get(check.primaryKeyColumn()));
        String mpId = check.mpIdColumn() != null ? (String) row.get(check.mpIdColumn()) : null;

        // Idempotency: don't re-flag the same record by the same method on every re-run.
        Integer existingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM detected_anomalies " +
            "WHERE flagged_table_name = ? AND flagged_record_id = ? AND detection_method = ?",
            Integer.class, check.table(), recordId, method
        );
        if (existingCount != null && existingCount > 0) return;

        jdbcTemplate.update(
            "INSERT INTO detected_anomalies " +
            "(flagged_table_name, flagged_record_id, mp_id, rule_triggered_name, rule_triggered_description, anomaly_score, detection_method) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            check.table(), recordId, mpId, check.ruleLabel(), description, score, method
        );
    }
}
