import { apiClient } from './apiClient';
import {
  BatchTranslateRequest,
  BatchTranslateResponseData,
  CreateJobRequest,
  JobDetail,
  ListJobsResponseData,
  VoiceCatalogResponseData,
} from './types';

const RECENT_JOBS_KEY = 'cnpm_recent_jobs';

export const jobsApi = {
  getVoices(): Promise<VoiceCatalogResponseData> {
    return apiClient<VoiceCatalogResponseData>('/api/v1/voices');
  },

  createJob(body: CreateJobRequest, idempotencyKey: string): Promise<JobDetail> {
    return apiClient<JobDetail>('/api/v1/jobs', {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body,
    });
  },

  getJob(jobId: string): Promise<JobDetail> {
    return apiClient<JobDetail>(`/api/v1/jobs/${encodeURIComponent(jobId)}`);
  },

  listJobs(params?: {
    page?: number;
    pageSize?: number;
    poiId?: string;
    status?: string;
    dateRange?: string;
    lang?: string;
    query?: string;
  }): Promise<ListJobsResponseData> {
    const q = new URLSearchParams();
    if (params?.page) q.set('page', String(params.page));
    if (params?.pageSize) q.set('pageSize', String(params.pageSize));
    if (params?.poiId) q.set('poiId', params.poiId);
    if (params?.status) q.set('status', params.status);
    if (params?.dateRange) q.set('dateRange', params.dateRange);
    if (params?.lang) q.set('lang', params.lang);
    if (params?.query) q.set('query', params.query);
    const qs = q.toString();
    return apiClient<ListJobsResponseData>(`/api/v1/jobs${qs ? `?${qs}` : ''}`);
  },

  cancelJob(jobId: string): Promise<JobDetail> {
    return apiClient<JobDetail>(`/api/v1/jobs/${encodeURIComponent(jobId)}/cancel`, {
      method: 'POST',
    });
  },

  startBatchTranslate(body: BatchTranslateRequest): Promise<BatchTranslateResponseData> {
    return apiClient<BatchTranslateResponseData>('/api/v1/jobs/batch-translate', {
      method: 'POST',
      body,
    });
  },

  getBatchTranslate(batchId: string): Promise<BatchTranslateResponseData> {
    return apiClient<BatchTranslateResponseData>(`/api/v1/jobs/batch-translate/${encodeURIComponent(batchId)}`);
  },

  cancelBatchTranslate(batchId: string): Promise<BatchTranslateResponseData> {
    return apiClient<BatchTranslateResponseData>(`/api/v1/jobs/batch-translate/${encodeURIComponent(batchId)}/cancel`, {
      method: 'POST',
    });
  },

  // Session-based job history helpers (fallback cache)
  getRecentJobIds(): string[] {
    try {
      const raw = sessionStorage.getItem(RECENT_JOBS_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  },

  addRecentJobId(jobId: string): void {
    try {
      const current = jobsApi.getRecentJobIds();
      const updated = [jobId, ...current.filter((id) => id !== jobId)].slice(0, 50);
      sessionStorage.setItem(RECENT_JOBS_KEY, JSON.stringify(updated));
    } catch {
      // Ignore storage errors
    }
  },
};
