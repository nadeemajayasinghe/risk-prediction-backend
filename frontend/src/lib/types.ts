// ─── Enums ──────────────────────────────────────────────────────────────────

export type SprintStatus = 'PLANNED' | 'ACTIVE' | 'COMPLETED' | 'CANCELLED';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'UNKNOWN';
export type ModelType = 'OVER_BUDGET' | 'REQUIREMENT_CHANGE' | 'COMMUNICATION_COLLABORATION';
export type ChangeType =
  | 'SCOPE_ADDED'
  | 'SCOPE_REMOVED'
  | 'ACCEPTANCE_CRITERIA_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'DESCRIPTION_UPDATED'
  | 'OTHER';

// ─── Sprint ──────────────────────────────────────────────────────────────────

export interface SprintResponse {
  id: number;
  name: string;
  goal: string | null;
  startDate: string; // LocalDate → ISO string
  endDate: string;
  status: SprintStatus;
  teamId: string | null;
  capacityPoints: number | null;
  teamType: string | null;
  teamSize: number | null;
  complexity: number | null;
  baseVelocity: number | null;
  sprintCapacityHours: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSprintRequest {
  name: string;
  goal?: string;
  startDate: string;
  endDate: string;
  status: SprintStatus;
  teamId?: string;
  capacityPoints?: number;
  teamType?: string;
  teamSize?: number;
  complexity?: number;
  baseVelocity?: number;
  sprintCapacityHours?: number;
}

export interface UpdateSprintRequest extends Partial<CreateSprintRequest> {}

// ─── Ingestion ────────────────────────────────────────────────────────────────

export interface SprintMetricRequest {
  plannedPoints?: number;
  completedPoints?: number;
  effortDeviation?: number;
  bugsCount?: number;
  scopeChangesCount?: number;
  velocity?: number;
  reworkScore?: number;
  blockedTasks?: number;
  reopenedTasks?: number;
  fatigue?: number;
  avgResponseTimeHours?: number;
  inactiveDays?: number;
}

export interface UserStoryRequest {
  externalKey?: string;
  title: string;
  description?: string;
  storyPoints?: number;
  priority?: string;
  status?: string;
}

export interface TaskRequest {
  title: string;
  description?: string;
  estimatedHours?: number;
  actualHours?: number;
  status?: string;
  assignee?: string;
}

export interface CommentRequest {
  text: string;
}

export interface RequirementChangeRequest {
  storyId?: number;
  changeType: ChangeType;
  description?: string;
  requestedBy?: string;
}

// ─── Risk / Reporting ────────────────────────────────────────────────────────

export interface ShapByClass {
  low: number;
  medium: number;
  high: number;
}

export interface FeatureImpact {
  feature: string;
  value: unknown;
  riskContribution: number;
  shapByClass: ShapByClass;
  direction: string;
}

export interface RiskFinding {
  code: string;
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  reason: string;
  suggestion: string;
}

export interface RiskPredictionResponse {
  id: number;
  sprintId: number;
  modelType: ModelType;
  riskScore: number;
  riskLevel: RiskLevel;
  probability: number;
  explanation: string;
  createdAt: string;
}

export interface AggregatedRiskResponse {
  id: number;
  sprintId: number;
  evaluationId: string;
  overallScore: number;
  overallLevel: RiskLevel;
  overBudgetScore: number;
  requirementChangeScore: number;
  communicationCollaborationScore: number;
  combinedExplanation: string;
  degraded: boolean;
  findings: RiskFinding[];
  overBudgetFeatureImpacts: FeatureImpact[];
  overBudgetBaselineRiskScore: number;
  requirementChangeFeatureImpacts: FeatureImpact[];
  requirementChangeBaselineRiskScore: number;
  communicationCollaborationRecommendations: string[];
  communicationCollaborationLlmExplanation: string;
  createdAt: string;
}

export interface RiskSummaryResponse {
  sprint: SprintResponse;
  latestAggregated: AggregatedRiskResponse | null;
  latestPerModel: RiskPredictionResponse[];
}

export interface RiskTrendPoint {
  timestamp: string;
  overallScore: number;
  overallLevel: RiskLevel;
  overBudgetScore: number;
  requirementChangeScore: number;
}
