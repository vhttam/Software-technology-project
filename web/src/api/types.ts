/**
 * API Types according to API & Event Contracts V1.7 (docs/source/api-contracts.md)
 */

// ============================================================================
// §1.2 Response Envelope
// ============================================================================

export interface ApiSuccessEnvelope<T> {
  status: 'success';
  data: T;
}

export interface ApiErrorEnvelope {
  status: 'error';
  errorCode: string;
  message: string;
  details?: Record<string, unknown> | null;
  correlationId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly errorCode: string;
  readonly details: Record<string, unknown> | null | undefined;
  readonly correlationId: string | undefined;

  constructor(
    status: number,
    errorCode: string,
    message: string,
    details?: Record<string, unknown> | null,
    correlationId?: string
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.errorCode = errorCode;
    this.details = details;
    this.correlationId = correlationId;
  }
}

// ============================================================================
// §2. Auth Service (§2.1 - §2.5)
// ============================================================================

export interface UserSummary {
  userId: string;
  username: string;
  name: string;
  role: 'CONTENT_ADMIN' | string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponseData {
  accessToken: string;
  refreshToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  refreshExpiresIn: number;
  user: UserSummary;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface RefreshResponseData {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface LogoutRequest {
  refreshToken: string;
}

export interface UserAccountItem {
  userId: string;
  username: string;
  name: string;
  role: 'SUPER_ADMIN' | 'CONTENT_ADMIN' | string;
  status?: 'ACTIVE' | 'LOCKED_TEMP' | 'DISABLED';
  failedLoginCount: number;
  lockedUntil?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ListUsersResponseData {
  items: UserAccountItem[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface CreateUserRequest {
  username: string;
  name: string;
  password: string;
  role: 'CONTENT_ADMIN';
}

export interface ChangeOwnPasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ResetPasswordRequest {
  newPassword: string;
}

export interface ChangePasswordRequest {
  newPassword: string;
  currentPassword?: string;
}

export interface UnlockUserResponseData {
  userId: string;
  locked: boolean;
}

// ============================================================================
// §3. Content Service (§3.1 - §3.3)
// ============================================================================

export interface SourceLanguagesResponseData {
  languages: string[];
}

export interface PoiItem {
  poiId: string;
  name: string;
  description?: string;
  qrCode?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ListPoisResponseData {
  items: PoiItem[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface CreatePoiRequest {
  name: string;
  description?: string;
}

export interface UpdatePoiRequest {
  name: string;
  description?: string;
}

export interface ContentItem {
  contentId: string;
  poiId: string;
  poiName?: string;
  version: number;
  title: string;
  sourceLang: string;
  textContent?: string;
  isDeleted?: boolean;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ListContentsResponseData {
  items: ContentItem[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface CreateContentRequest {
  title: string;
  sourceLang: string;
  textContent: string;
}

export interface UpdateContentRequest {
  baseVersion: number;
  title: string;
  sourceLang: string;
  textContent: string;
}

// ============================================================================
// §4. Narration Service (§4.1 - §4.4)
// ============================================================================

export interface VoiceCatalogItem {
  lang: string;
  voices: string[];
}

export interface VoiceCatalogResponseData {
  supportedVoices: VoiceCatalogItem[];
  supportsVoiceSelection?: boolean;
  isMock?: boolean;
}

export type JobStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'PARTIALLY_COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export type TargetStatus =
  | 'PENDING'
  | 'TRANSLATING'
  | 'SYNTHESIZING'
  | 'PUBLISHED'
  | 'FAILED'
  | 'CANCELLED';

export interface JobTargetRequest {
  lang: string;
  voiceId: string;
}

export interface CreateJobRequest {
  contentId: string;
  retryOfJobId?: string;
  expectedVersion?: number;
  targets: JobTargetRequest[];
}

export interface JobTarget {
  targetId: string;
  lang: string;
  voiceId: string;
  status: TargetStatus;
  errorCode?: string | null;
  updatedAt?: string;
}

export interface JobDetail {
  jobId: string;
  contentId: string;
  version: number;
  status: JobStatus;
  createdAt: string;
  updatedAt?: string;
  targets: JobTarget[];
}

export interface JobListItem {
  jobId: string;
  contentId: string;
  contentTitle?: string;
  poiId?: string;
  poiName?: string;
  version: number;
  status: JobStatus;
  createdAt: string;
  updatedAt?: string;
  targets: JobTarget[];
}

export interface ListJobsResponseData {
  items: JobListItem[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface BatchTranslateRequest {
  targetLang: string;
  voiceId?: string;
}

export interface BatchTranslateResponseData {
  batchId: string;
  targetLang: string;
  voiceId: string;
  totalContents: number;
  skippedContents: number;
  queuedContents: number;
  processedContents: number;
  failedContents: number;
  status: 'PROCESSING' | 'COMPLETED' | 'CANCELLED';
  createdAt: string;
  updatedAt: string;
  createdJobIds: string[];
}

// ============================================================================
// §4.5 Client Narration Playback & Preview
// ============================================================================

export interface PublishedNarrationSummary {
  lang: string;
  version: number;
  currentVersion?: number;
  isOutdated?: boolean;
}

export interface ListPoiNarrationsResponseData {
  poiId: string;
  poiName: string;
  contentTitle: string;
  narrations: PublishedNarrationSummary[];
}

export interface NarrationDetailResponseData {
  poiId: string;
  poiName: string;
  contentTitle: string;
  lang: string;
  version: number;
  currentVersion?: number;
  isOutdated?: boolean;
  subtitle: string;
  audioUrl: string;
  audioUrlExpiresAt: string;
}

// ============================================================================
// §Health (Gateway health check)
// ============================================================================

export interface HealthResponseData {
  status: 'UP' | string;
}


