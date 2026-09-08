# MPLAD Fraud & Anomaly Detection

SIH26102 — Member 5 (Database + AI Integration)

## What this does

Flags suspicious MPLAD fund records using statistics, then explains each flag in plain language using Claude.

```
detected_anomalies  ← StatisticalAnomalyDetectionService (Z-score, IQR)
        ↓
anomaly_plain_language_explanations  ← AnomalyExplanationService (Claude API)
        ↓
GET /api/anomalies  → dashboard (Member 3)
```

## Files

| File | Purpose |
|---|---|
| `01_mplad_database_schema.sql` | 7 tables: MPs, allocations, works, bills, UCs, anomalies, explanations |
| `02_mplad_sample_data.sql` | Sample rows for local testing |
| `StatisticalAnomalyDetectionService.java` | Z-score + IQR checks → writes to `detected_anomalies` |
| `AnomalyExplanationService.java` | Calls Claude API → writes to `anomaly_plain_language_explanations` |
| `AnomalyDetectionController.java` | REST endpoints tying both together |

## Detection methods

**Z-score** — how many standard deviations a value sits from the mean. Flags `\|z\| > 2.5`. Best for roughly normal, non-skewed data.

**IQR** — flags values outside `Q1 − 1.5×IQR` to `Q3 + 1.5×IQR`. More robust to skew and existing extreme values than Z-score.

Both currently compare a value against the whole table. Grouping by `financial_year` or `sector_category` is the natural next step for accuracy.

## API

| Method | Endpoint | Does |
|---|---|---|
| `POST` | `/api/anomalies/detect` | Run Z-score + IQR checks |
| `POST` | `/api/anomalies/{id}/explain` | Generate explanation for one anomaly |
| `POST` | `/api/anomalies/detect-and-explain-all` | Both steps, for demo |
| `GET` | `/api/anomalies` | List anomalies + explanations, joined |

## Setup

```properties
# application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mplad_fraud_detection
spring.datasource.username=root
spring.datasource.password=
anthropic.api.key=YOUR_KEY_HERE
```

```bash
mysql -u root -p < 01_mplad_database_schema.sql
mysql -u root -p < 02_mplad_sample_data.sql
```

## Notes

- The LLM never decides what's anomalous — it only explains what the statistics already flagged.
- Detection is idempotent: re-running `/detect` won't duplicate existing flags.
