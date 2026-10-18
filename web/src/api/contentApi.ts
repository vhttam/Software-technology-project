import { apiClient } from './apiClient';
import {
  ContentItem,
  CreateContentRequest,
  CreatePoiRequest,
  ListContentsResponseData,
  ListPoiNarrationsResponseData,
  ListPoisResponseData,
  NarrationDetailResponseData,
  PoiItem,
  SourceLanguagesResponseData,
  UpdateContentRequest,
  UpdatePoiRequest,
} from './types';


export const contentApi = {
  getSourceLanguages(): Promise<SourceLanguagesResponseData> {
    return apiClient<SourceLanguagesResponseData>('/api/v1/languages/source');
  },

  listPois(page = 1, pageSize = 20, query?: string): Promise<ListPoisResponseData> {
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (query?.trim()) params.set('query', query.trim());

    return apiClient<ListPoisResponseData>(`/api/v1/pois?${params.toString()}`);
  },

  getPoi(poiId: string): Promise<PoiItem> {
    return apiClient<PoiItem>(`/api/v1/pois/${encodeURIComponent(poiId)}`);
  },

  createPoi(body: CreatePoiRequest): Promise<PoiItem> {
    return apiClient<PoiItem>('/api/v1/pois', {
      method: 'POST',
      body,
    });
  },

  updatePoi(poiId: string, body: UpdatePoiRequest): Promise<PoiItem> {
    return apiClient<PoiItem>(`/api/v1/pois/${encodeURIComponent(poiId)}`, {
      method: 'PUT',
      body,
    });
  },

  deletePoi(poiId: string): Promise<void> {
    return apiClient<void>(`/api/v1/pois/${encodeURIComponent(poiId)}`, {
      method: 'DELETE',
    });
  },

  listContents(
    page = 1,
    pageSize = 20,
    query?: string,
    poiId?: string
  ): Promise<ListContentsResponseData> {
    const params = new URLSearchParams({
      page: String(page),
      pageSize: String(pageSize),
    });
    if (query?.trim()) params.set('query', query.trim());
    if (poiId?.trim()) params.set('poiId', poiId.trim());

    return apiClient<ListContentsResponseData>(`/api/v1/contents?${params.toString()}`);
  },

  getContent(contentId: string): Promise<ContentItem> {
    return apiClient<ContentItem>(`/api/v1/contents/${encodeURIComponent(contentId)}`);
  },

  getActiveContentByPoi(poiId: string): Promise<ContentItem> {
    return apiClient<ContentItem>(`/api/v1/pois/${encodeURIComponent(poiId)}/contents/active`);
  },

  createContent(poiId: string, body: CreateContentRequest): Promise<ContentItem> {
    return apiClient<ContentItem>(`/api/v1/pois/${encodeURIComponent(poiId)}/contents`, {
      method: 'POST',
      body,
    });
  },

  updateContent(contentId: string, body: UpdateContentRequest): Promise<ContentItem> {
    return apiClient<ContentItem>(`/api/v1/contents/${encodeURIComponent(contentId)}`, {
      method: 'PUT',
      body,
    });
  },

  deleteContent(contentId: string): Promise<void> {
    return apiClient<void>(`/api/v1/contents/${encodeURIComponent(contentId)}`, {
      method: 'DELETE',
    });
  },

  listPoiNarrations(poiId: string): Promise<ListPoiNarrationsResponseData> {
    return apiClient<ListPoiNarrationsResponseData>(
      `/api/v1/client/pois/${encodeURIComponent(poiId)}/narrations`
    );
  },

  getNarrationDetail(poiId: string, lang: string): Promise<NarrationDetailResponseData> {
    return apiClient<NarrationDetailResponseData>(
      `/api/v1/client/pois/${encodeURIComponent(poiId)}/narrations/${encodeURIComponent(lang)}`
    );
  },
};


