-- ============================================================================
-- MPLAD FRAUD & ANOMALY DETECTION — DATABASE SCHEMA
-- SIH26102 | Member 5: Database + AI Integration
-- ============================================================================
-- Naming rule followed throughout: every table and column name should tell
-- you what it holds WITHOUT needing a comment. No abbreviations like
-- "mp_master" or "amt" — spelled out in full.
-- ============================================================================

CREATE DATABASE IF NOT EXISTS mplad_fraud_detection;
USE mplad_fraud_detection;

-- ----------------------------------------------------------------------------
-- TABLE 1: members_of_parliament
-- One row per MP. Everything else in the system links back to this table.
-- ----------------------------------------------------------------------------
CREATE TABLE members_of_parliament (
    mp_id                   VARCHAR(20)  PRIMARY KEY,        -- e.g. 'MP-LS-18-01'
    full_name               VARCHAR(100) NOT NULL,
    house_type              VARCHAR(20)  NOT NULL CHECK (house_type IN ('Lok Sabha','Rajya Sabha','Nominated')),
    state_or_constituency   VARCHAR(100) NOT NULL,
    tenure_start_date       DATE,
    tenure_end_date         DATE,
    is_currently_active     BOOLEAN      DEFAULT TRUE
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- TABLE 2: fund_allocations_per_year
-- How much money each MP was given, per financial year.
-- ----------------------------------------------------------------------------
CREATE TABLE fund_allocations_per_year (
    allocation_id                     INT AUTO_INCREMENT PRIMARY KEY,
    mp_id                             VARCHAR(20)   NOT NULL,
    financial_year                    VARCHAR(7)    NOT NULL,   -- e.g. '2025-26'
    entitlement_amount_inr            DECIMAL(12,2) DEFAULT 50000000.00,
    released_amount_inr               DECIMAL(12,2) DEFAULT 0.00,
    interest_earned_inr               DECIMAL(12,2) DEFAULT 0.00,
    unspent_balance_carried_forward_inr DECIMAL(12,2) DEFAULT 0.00,
    total_available_funds_inr         DECIMAL(12,2) GENERATED ALWAYS AS
        (released_amount_inr + interest_earned_inr + unspent_balance_carried_forward_inr) STORED,
    authorization_date                DATE,
    cna_reference_number              VARCHAR(50),
    allocation_status                 VARCHAR(30) DEFAULT 'Active', -- Active, Sanctioned, Capped
    FOREIGN KEY (mp_id) REFERENCES members_of_parliament(mp_id),
    UNIQUE KEY unique_mp_per_year (mp_id, financial_year)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- TABLE 3: recommended_and_sanctioned_works
-- One row per project an MP proposed, whether it got approved or not.
-- ----------------------------------------------------------------------------
CREATE TABLE recommended_and_sanctioned_works (
    work_id                 VARCHAR(25)  PRIMARY KEY,        -- e.g. 'WRK-2025-UP-0012'
    mp_id                   VARCHAR(20)  NOT NULL,
    sector_category         VARCHAR(50)  NOT NULL,           -- Water, Roads, Education...
    work_description        TEXT         NOT NULL,
    proposed_cost_inr       DECIMAL(12,2) NOT NULL,
    sanctioned_cost_inr     DECIMAL(12,2) DEFAULT 0.00,
    quota_category          VARCHAR(20)  CHECK (quota_category IN ('General','SC','ST')),
    recommendation_date     DATE         NOT NULL,
    sanction_date           DATE,
    sanction_order_number   VARCHAR(50),
    nodal_district          VARCHAR(100),
    executing_agency_name   VARCHAR(100),
    work_status             VARCHAR(30)  DEFAULT 'Recommended', -- Recommended, Sanctioned, Rejected, In-Review
    rejection_reason        TEXT,
    FOREIGN KEY (mp_id) REFERENCES members_of_parliament(mp_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- TABLE 4: vendor_bills_and_payments
-- Every invoice raised against a sanctioned work, and what got paid.
-- ----------------------------------------------------------------------------
CREATE TABLE vendor_bills_and_payments (
    bill_id                     VARCHAR(25)  PRIMARY KEY,    -- e.g. 'INV-0101'
    work_id                     VARCHAR(25)  NOT NULL,
    vendor_name                 VARCHAR(100) NOT NULL,
    vendor_gst_number           VARCHAR(15),
    bill_number                 VARCHAR(50),
    bill_date                   DATE,
    payment_stage                VARCHAR(30), -- Advance, First Running Bill, Final Bill
    claimed_amount_inr          DECIMAL(12,2) NOT NULL,
    passed_amount_inr           DECIMAL(12,2) NOT NULL,
    tds_deducted_inr            DECIMAL(12,2) DEFAULT 0.00,
    net_amount_paid_inr         DECIMAL(12,2) NOT NULL,
    pfms_transaction_reference  VARCHAR(40),
    geo_tag_latitude             DECIMAL(10,6),
    geo_tag_longitude            DECIMAL(10,6),
    payment_status               VARCHAR(20) DEFAULT 'Disbursed', -- Pending Inspection, Approved, Disbursed
    FOREIGN KEY (work_id) REFERENCES recommended_and_sanctioned_works(work_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- TABLE 5: utilization_certificates
-- The formal statement that says "this much money was actually spent".
-- ----------------------------------------------------------------------------
CREATE TABLE utilization_certificates (
    uc_id                            VARCHAR(25) PRIMARY KEY,   -- e.g. 'UC-2025-UP-0041'
    mp_id                            VARCHAR(20) NOT NULL,
    nodal_district                   VARCHAR(100) NOT NULL,
    financial_year                   VARCHAR(7) NOT NULL,
    uc_issue_date                    DATE,
    authorized_amount_inr            DECIMAL(12,2) NOT NULL,
    utilized_amount_inr              DECIMAL(12,2) NOT NULL,
    utilization_percentage           DECIMAL(5,2) GENERATED ALWAYS AS
        (utilized_amount_inr / authorized_amount_inr * 100) STORED,
    unspent_balance_inr              DECIMAL(12,2),
    interest_reported_inr            DECIMAL(12,2),
    physical_assets_completed_count  INT,
    auditor_certificate_reference    VARCHAR(50),
    verification_status              VARCHAR(30) DEFAULT 'Submitted', -- Submitted, Verified MoSPI, Query Raised
    FOREIGN KEY (mp_id) REFERENCES members_of_parliament(mp_id)
) ENGINE=InnoDB;

-- ============================================================================
-- YOUR TWO TABLES (Member 5 — Database + AI Integration)
-- Member 2's rule engine WRITES to detected_anomalies.
-- Your AI integration code WRITES to anomaly_plain_language_explanations.
-- Member 3's dashboard READS from both, joined together.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- TABLE 6: detected_anomalies
-- One row per suspicious record found by the rule engine or statistics.
-- ----------------------------------------------------------------------------
CREATE TABLE detected_anomalies (
    anomaly_id              INT AUTO_INCREMENT PRIMARY KEY,
    flagged_table_name      VARCHAR(50) NOT NULL,   -- which table the bad record lives in
    flagged_record_id       VARCHAR(25) NOT NULL,   -- that table's primary key value
    mp_id                   VARCHAR(20),
    rule_triggered_name     VARCHAR(100) NOT NULL,  -- e.g. 'large_utilization_gap'
    rule_triggered_description TEXT,                -- plain sentence of what the rule checks
    anomaly_score            DECIMAL(6,2),          -- higher = more suspicious
    detection_method         VARCHAR(30) NOT NULL,  -- rule_based, zscore, iqr
    detected_at_timestamp    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    review_status             VARCHAR(30) DEFAULT 'pending_review', -- pending_review, reviewed_ok, confirmed_issue
    FOREIGN KEY (mp_id) REFERENCES members_of_parliament(mp_id)
) ENGINE=InnoDB;

-- ----------------------------------------------------------------------------
-- TABLE 7: anomaly_plain_language_explanations
-- The LLM's write-up of WHY a record was flagged, in plain language.
-- The LLM never decides anomalies — it only explains ones already flagged.
-- ----------------------------------------------------------------------------
CREATE TABLE anomaly_plain_language_explanations (
    explanation_id           INT AUTO_INCREMENT PRIMARY KEY,
    anomaly_id                INT NOT NULL,
    plain_language_explanation TEXT NOT NULL,
    ai_model_used              VARCHAR(50),          -- e.g. 'claude-sonnet-4-6'
    generated_at_timestamp     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    explanation_status         VARCHAR(30) DEFAULT 'generated', -- generated, reviewed, edited_by_officer
    FOREIGN KEY (anomaly_id) REFERENCES detected_anomalies(anomaly_id)
) ENGINE=InnoDB;
