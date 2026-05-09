import axios from 'axios';
import type {
  AggregatedRiskResponse,
  CommentRequest,
  CreateSprintRequest,
  RequirementChangeRequest,
  RiskPredictionResponse,
  RiskSummaryResponse,
  RiskTrendPoint,
  SprintMetricRequest,
  SprintResponse,
  TaskRequest,
  UpdateSprintRequest,
  UserStoryRequest,
} from './types';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});

// ─── Sprints ─────────────────────────────────────────────────────────────────

export const sprintsApi = {
  list: () => api.get<SprintResponse[]>('/api/v1/sprints').then(r => r.data),
  get: (id: number) => api.get<SprintResponse>(`/api/v1/sprints/${id}`).then(r => r.data),
  create: (body: CreateSprintRequest) =>
    api.post<SprintResponse>('/api/v1/sprints', body).then(r => r.data),
  update: (id: number, body: UpdateSprintRequest) =>
    api.patch<SprintResponse>(`/api/v1/sprints/${id}`, body).then(r => r.data),
  delete: (id: number) => api.delete(`/api/v1/sprints/${id}`),
};

// ─── Ingestion ────────────────────────────────────────────────────────────────

export const ingestionApi = {
  addMetric: (sprintId: number, body: SprintMetricRequest) =>
    api.post<number>(`/api/v1/sprints/${sprintId}/metrics`, body).then(r => r.data),

  addStory: (sprintId: number, body: UserStoryRequest) =>
    api.post<number>(`/api/v1/sprints/${sprintId}/stories`, body).then(r => r.data),

  addTask: (storyId: number, body: TaskRequest) =>
    api.post<number>(`/api/v1/stories/${storyId}/tasks`, body).then(r => r.data),

  addComment: (storyId: number, body: CommentRequest) =>
    api.post<number>(`/api/v1/stories/${storyId}/comments`, body).then(r => r.data),

  addRequirementChange: (sprintId: number, body: RequirementChangeRequest) =>
    api.post<number>(`/api/v1/sprints/${sprintId}/requirement-changes`, body).then(r => r.data),
};

// ─── Risk ─────────────────────────────────────────────────────────────────────

export const riskApi = {
  evaluate: (sprintId: number) =>
    api.post<AggregatedRiskResponse>(`/api/v1/sprints/${sprintId}/evaluate-risk`).then(r => r.data),
};

// ─── Reporting ────────────────────────────────────────────────────────────────

export const reportingApi = {
  summary: (sprintId: number) =>
    api.get<RiskSummaryResponse>(`/api/v1/sprints/${sprintId}/risk-summary`).then(r => r.data),

  history: (sprintId: number) =>
    api.get<RiskPredictionResponse[]>(`/api/v1/sprints/${sprintId}/history`).then(r => r.data),

  trend: (sprintId: number) =>
    api.get<RiskTrendPoint[]>(`/api/v1/sprints/${sprintId}/trend`).then(r => r.data),

  compare: (ids: number[]) =>
    api.get<AggregatedRiskResponse[]>('/api/v1/sprints/compare', { params: { ids } }).then(r => r.data),
};
