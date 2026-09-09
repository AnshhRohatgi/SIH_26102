export type RiskLevel = "HIGH" | "MEDIUM" | "LOW";
export type ProjectStatus = "In progress" | "Completed" | "Under review" | "Sanctioned";

export type MpladRecord = {
  id: string;
  projectName: string;
  mpName: string;
  constituency: string;
  state: string;
  sanctionedAmount: number;
  releasedAmount: number;
  expenditure: number;
  date: string;
  status: ProjectStatus;
  riskLevel: RiskLevel;
  riskScore: number;
  flags: string[];
  explanation: string;
  category: string;
};

export type DashboardSnapshot = {
  records: MpladRecord[];
  lastSyncedAt: string;
  source: "live" | "demo";
};

/** Shape returned by AnomalyDetectionController.listAnomaliesWithExplanations(). */
export type BackendAnomaly = {
  anomaly_id: number;
  flagged_table_name: string;
  flagged_record_id: string;
  mp_id?: string | null;
  rule_triggered_name: string;
  anomaly_score?: number | null;
  detection_method: "rule_based" | "zscore" | "iqr" | string;
  review_status: "pending_review" | "reviewed_ok" | "confirmed_issue" | string;
  plain_language_explanation?: string | null;
  explanation_status?: string | null;
};

/**
 * Point this base URL at your Spring Boot deployment.
 * Example: VITE_API_BASE_URL=https://api.example.gov.in/api/v1
 */
export const API_CONFIG = {
  baseUrl: (import.meta.env.VITE_API_BASE_URL || "").replace(/\/$/, ""),
  useMockData: import.meta.env.VITE_USE_MOCK_DATA !== "false",
  endpoints: {
    records: import.meta.env.VITE_RECORDS_ENDPOINT || "/api/mplad/records",
    anomalies: import.meta.env.VITE_ANOMALIES_ENDPOINT || "/api/anomalies",
    explanation: import.meta.env.VITE_EXPLANATION_ENDPOINT || "/api/anomalies/:id/explain",
    analytics: import.meta.env.VITE_ANALYTICS_ENDPOINT || "/api/mplad/analytics/summary",
  },
};

const mockRecords: MpladRecord[] = [
  {
    id: "MPLAD-2025-0917",
    projectName: "Upgradation of rural link roads — Cluster 4",
    mpName: "Shri Arjun Mehta",
    constituency: "Jaipur Rural",
    state: "Rajasthan",
    sanctionedAmount: 42_00_000,
    releasedAmount: 42_00_000,
    expenditure: 41_88_500,
    date: "2025-08-14",
    status: "Under review",
    riskLevel: "HIGH",
    riskScore: 94,
    flags: ["Duplicate vendor cluster", "98.6% utilisation in 12 days", "IQR outlier"],
    explanation: "The expenditure moved unusually fast after release and sits in the top 1% of comparable road projects. Three nearby records also reference the same contractor and invoice sequence, which merits document verification.",
    category: "Roads & bridges",
  },
  {
    id: "MPLAD-2025-0881",
    projectName: "Solar high-mast lighting at 18 public crossings",
    mpName: "Smt. Kavita Rao",
    constituency: "Mysuru",
    state: "Karnataka",
    sanctionedAmount: 18_50_000,
    releasedAmount: 12_00_000,
    expenditure: 3_25_000,
    date: "2025-07-29",
    status: "Sanctioned",
    riskLevel: "MEDIUM",
    riskScore: 71,
    flags: ["Low release-to-spend conversion", "Milestone overdue"],
    explanation: "Only 27% of the released amount has been booked after 61 days. The pattern is not proof of misuse, but the delay is materially outside the district baseline and should be reconciled with the implementing agency.",
    category: "Energy & lighting",
  },
  {
    id: "MPLAD-2025-0814",
    projectName: "Community health centre diagnostic wing",
    mpName: "Dr. Nadeem Qureshi",
    constituency: "Baramulla",
    state: "Jammu & Kashmir",
    sanctionedAmount: 65_00_000,
    releasedAmount: 32_50_000,
    expenditure: 18_40_000,
    date: "2025-06-18",
    status: "In progress",
    riskLevel: "LOW",
    riskScore: 38,
    flags: ["No material outlier"],
    explanation: "The project tracks within expected spend velocity and peer benchmarks. The record remains visible for audit completeness, but no immediate intervention is recommended.",
    category: "Health",
  },
  {
    id: "MPLAD-2025-0762",
    projectName: "Pre-fabricated classrooms at Government Sr. Sec. School",
    mpName: "Shri Vivek Patil",
    constituency: "Nashik",
    state: "Maharashtra",
    sanctionedAmount: 26_00_000,
    releasedAmount: 26_00_000,
    expenditure: 25_98_000,
    date: "2025-05-27",
    status: "Completed",
    riskLevel: "HIGH",
    riskScore: 88,
    flags: ["Exact-round expenditure", "High utilisation outlier"],
    explanation: "The project reports 99.9% utilisation with a near-perfect round figure, while comparable classroom projects in the same period average 78%. The record is queued for invoice and completion-certificate review.",
    category: "Education",
  },
  {
    id: "MPLAD-2025-0698",
    projectName: "Drinking water pipeline extension — Ward 11",
    mpName: "Smt. Rukmini Das",
    constituency: "Kolkata North",
    state: "West Bengal",
    sanctionedAmount: 14_00_000,
    releasedAmount: 7_00_000,
    expenditure: 6_75_000,
    date: "2025-04-10",
    status: "In progress",
    riskLevel: "LOW",
    riskScore: 29,
    flags: ["No material outlier"],
    explanation: "Spend is aligned with the first release milestone and similar public-utility works. Continue routine monitoring when the second release is booked.",
    category: "Water & sanitation",
  },
  {
    id: "MPLAD-2025-0611",
    projectName: "Women’s skill development and incubation centre",
    mpName: "Shri Manish Tandon",
    constituency: "Lucknow",
    state: "Uttar Pradesh",
    sanctionedAmount: 38_00_000,
    releasedAmount: 38_00_000,
    expenditure: 30_72_000,
    date: "2025-03-22",
    status: "Under review",
    riskLevel: "MEDIUM",
    riskScore: 64,
    flags: ["Concentration in one vendor", "Z-score above 2"],
    explanation: "The project has a higher-than-expected share of spending with one vendor and a positive spend z-score of 2.4 against comparable centres. A procurement split review is recommended.",
    category: "Livelihoods",
  },
];

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${API_CONFIG.baseUrl}${path}`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`API request failed (${response.status})`);
  return response.json() as Promise<T>;
}

export async function loadDashboardSnapshot(): Promise<DashboardSnapshot> {
  if (API_CONFIG.useMockData || !API_CONFIG.baseUrl) {
    return { records: mockRecords, lastSyncedAt: "09 Sep 2025 · 09:42 IST", source: "demo" };
  }

  try {
    const records = await getJson<MpladRecord[]>(API_CONFIG.endpoints.records);
    return { records, lastSyncedAt: new Date().toLocaleString("en-IN"), source: "live" };
  } catch {
    try {
      // The uploaded controller already exposes /api/anomalies even before a
      // joined project-record controller is added. Keep the dashboard useful
      // in that incremental integration stage.
      const anomalies = await loadAnomalies();
      return { records: anomalies, lastSyncedAt: new Date().toLocaleString("en-IN"), source: "live" };
    } catch {
      return { records: mockRecords, lastSyncedAt: "API unavailable · demo data shown", source: "demo" };
    }
  }
}

export async function loadAnomalies(): Promise<MpladRecord[]> {
  if (API_CONFIG.useMockData || !API_CONFIG.baseUrl) return mockRecords.filter((record) => record.riskLevel !== "LOW");
  const rows = await getJson<BackendAnomaly[]>(API_CONFIG.endpoints.anomalies);
  return rows.map(mapBackendAnomaly);
}

export async function loadExplanation(id: string): Promise<string> {
  if (API_CONFIG.useMockData || !API_CONFIG.baseUrl) {
    return mockRecords.find((record) => record.id === id)?.explanation || "No explanation available.";
  }
  const response = await fetch(`${API_CONFIG.baseUrl}${API_CONFIG.endpoints.explanation.replace(":id", id)}`, {
    method: "POST",
    headers: { Accept: "application/json" },
  });
  if (!response.ok) throw new Error(`Explanation request failed (${response.status})`);
  const data = (await response.json()) as { status?: string; explanation?: string };
  return data.explanation || data.status || "Explanation generated by the backend. Refresh the anomaly list to view it.";
}

export function mapBackendAnomaly(row: BackendAnomaly): MpladRecord {
  const score = Number(row.anomaly_score || 0);
  const riskLevel: RiskLevel = score >= 75 ? "HIGH" : score >= 50 ? "MEDIUM" : "LOW";
  const ruleLabel = row.rule_triggered_name.replaceAll("_", " ");
  return {
    id: `ANOM-${row.anomaly_id}`,
    projectName: `${row.flagged_table_name.replaceAll("_", " ")} · ${row.flagged_record_id}`,
    mpName: row.mp_id || "MP reference unavailable",
    constituency: "Backend record",
    state: "—",
    sanctionedAmount: 0,
    releasedAmount: 0,
    expenditure: 0,
    date: new Date().toISOString().slice(0, 10),
    status: row.review_status === "confirmed_issue" ? "Under review" : "In progress",
    riskLevel,
    riskScore: Math.round(score),
    flags: [ruleLabel, row.detection_method.toUpperCase(), row.review_status.replaceAll("_", " ")],
    explanation: row.plain_language_explanation || "The anomaly has been detected by the statistical rule engine. Generate or refresh the explanation from the backend for a plain-language review note.",
    category: row.flagged_table_name,
  };
}

export const mockAnalytics = [
  { month: "Apr", tracked: 118, flagged: 8 },
  { month: "May", tracked: 151, flagged: 11 },
  { month: "Jun", tracked: 143, flagged: 7 },
  { month: "Jul", tracked: 178, flagged: 15 },
  { month: "Aug", tracked: 210, flagged: 19 },
  { month: "Sep", tracked: 198, flagged: 13 },
];

export { mockRecords };
