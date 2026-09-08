-- ============================================================================
-- SAMPLE DATA — loaded from your 5 uploaded reports
-- Run 01_mplad_database_schema.sql first.
-- ============================================================================
USE mplad_fraud_detection;

-- ---- members_of_parliament (from Report 01) --------------------------------
INSERT INTO members_of_parliament (mp_id, full_name, house_type, state_or_constituency, is_currently_active) VALUES
('MP-LS-18-01', 'Rajesh Kumar Sharma', 'Lok Sabha', 'Varanasi (UP)', TRUE),
('MP-RS-22-14', 'Dr. Sunita Verma',    'Rajya Sabha', 'Maharashtra', TRUE),
('MP-LS-18-42', 'A. R. Muthusamy',     'Lok Sabha', 'Madurai (TN)', TRUE);

-- ---- fund_allocations_per_year (from Report 01) ----------------------------
INSERT INTO fund_allocations_per_year
(mp_id, financial_year, entitlement_amount_inr, released_amount_inr, interest_earned_inr, unspent_balance_carried_forward_inr, authorization_date) VALUES
('MP-LS-18-01', '2025-26', 50000000.00, 50000000.00, 145200.00, 2210000.00, '2025-04-05'),
('MP-RS-22-14', '2025-26', 50000000.00, 25000000.00, 88400.00,  550000.00,  '2025-04-08'),
('MP-LS-18-42', '2025-26', 50000000.00, 50000000.00, 210000.00, 4100000.00, '2025-04-05'),
('MP-LS-18-01', '2026-27', 50000000.00, 25000000.00, 45000.00,  1830000.00, '2026-04-10');

-- ---- recommended_and_sanctioned_works (from Report 02) ---------------------
INSERT INTO recommended_and_sanctioned_works
(work_id, mp_id, sector_category, work_description, proposed_cost_inr, sanctioned_cost_inr, quota_category, recommendation_date, sanction_date, work_status, nodal_district, executing_agency_name, rejection_reason) VALUES
('WRK-2025-UP-0012', 'MP-LS-18-01', 'Water',      'Piped drinking water supply extension', 1250000.00, 1200000.00, 'SC', '2025-04-10', '2025-05-02', 'Sanctioned', 'Varanasi', 'UP Jal Nigam', NULL),
('WRK-2025-UP-0015', 'MP-LS-18-01', 'Education',  'Government school building block',       2500000.00, 2500000.00, 'General', '2025-05-18', '2025-06-12', 'Sanctioned', 'Varanasi', 'PWD Building Div', NULL),
('WRK-2025-TN-0088', 'MP-LS-18-42', 'Sanitation', 'Community sanitation facility',          800000.00,  800000.00,  'ST', '2025-06-01', '2025-06-25', 'Sanctioned', 'Madurai', 'Rural Dev Dept', NULL),
('WRK-2025-MH-0104', 'MP-RS-22-14', 'Roads',      'Rural link road construction',           3000000.00, 0.00,       'General', '2025-07-10', '2025-08-01', 'Rejected', 'Pune', NULL, 'Land unclarity - private land');

-- ---- vendor_bills_and_payments (from Report 04) ----------------------------
INSERT INTO vendor_bills_and_payments
(bill_id, work_id, vendor_name, payment_stage, bill_date, claimed_amount_inr, passed_amount_inr, net_amount_paid_inr, pfms_transaction_reference, payment_status) VALUES
('INV-0101', 'WRK-2025-UP-0012', 'Apex Infra Corp',   'Running Bill', '2025-05-10', 600000.00, 600000.00, 588000.00, 'PFMS2025051000921', 'Disbursed'),
('INV-0102', 'WRK-2025-UP-0012', 'Apex Infra Corp',   'Final Bill',   '2025-08-14', 600000.00, 600000.00, 588000.00, 'PFMS2025081400432', 'Disbursed'),
('INV-0144', 'WRK-2025-UP-0015', 'Shanti Builders',   'Advance',      '2025-06-20', 500000.00, 500000.00, 490000.00, 'PFMS2025062000118', 'Disbursed'),
('INV-0209', 'WRK-2025-TN-0088', 'Green Earth Water', 'Final Bill',   '2025-07-02', 800000.00, 785000.00, 769300.00, 'PFMS2025070200884', 'Disbursed');

-- ---- utilization_certificates (from Report 03) -----------------------------
INSERT INTO utilization_certificates
(uc_id, mp_id, nodal_district, financial_year, uc_issue_date, authorized_amount_inr, utilized_amount_inr, unspent_balance_inr, verification_status) VALUES
('UC-2025-UP-0041', 'MP-LS-18-01', 'Varanasi', '2024-25', '2025-03-20', 50000000.00, 42500000.00, 7500000.00, 'Verified MoSPI'),
('UC-2025-TN-0012', 'MP-LS-18-42', 'Madurai',  '2024-25', '2025-03-22', 50000000.00, 46000000.00, 4000000.00, 'Verified MoSPI'),
('UC-2025-MH-0078', 'MP-RS-22-14', 'Pune',     '2024-25', '2025-03-25', 50000000.00, 38000000.00, 12000000.00, 'Query Raised'),
('UC-2026-UP-0005', 'MP-LS-18-01', 'Varanasi', '2025-26', '2026-03-18', 50000000.00, 41000000.00, 9000000.00, 'Submitted');

-- ---- example anomaly + AI explanation (shows how tables 6 & 7 connect) -----
INSERT INTO detected_anomalies
(flagged_table_name, flagged_record_id, mp_id, rule_triggered_name, rule_triggered_description, anomaly_score, detection_method) VALUES
('utilization_certificates', 'UC-2025-MH-0078', 'MP-RS-22-14', 'low_utilization_query_raised',
 'Utilization is below 80% AND MoSPI has raised a query on this certificate', 78.50, 'rule_based');

INSERT INTO anomaly_plain_language_explanations
(anomaly_id, plain_language_explanation, ai_model_used, explanation_status) VALUES
(1, 'This utilization certificate for Pune shows only 76% of authorized funds were spent, below the 80% threshold required for the next tranche, and MoSPI has already raised a query on it. This combination — low spending plus an open query — is a common early signal of fund misuse or delayed project execution, and should be reviewed before releasing further funds to this MP.',
 'claude-sonnet-4-6', 'generated');
