export interface VisitorPoi {
  poiId: string;
  poiName: string;
  name?: string;
  description?: string;
  qrCode?: string;
  latitude: number;
  longitude: number;
  triggerRadius?: number;
  distanceMeters?: number;
  distanceText?: string;
}

export interface NarrationDetail {
  poiId: string;
  poiName?: string;
  contentTitle?: string;
  lang: string;
  version?: number;
  subtitle?: string;
  audioUrl?: string;
  audioUrlExpiresAt?: string;
  isOutdated?: boolean;
}

export interface PresenceInfo {
  activeCount: number;
  windowSeconds?: number;
  timestamp: string;
}

export interface LanguageOption {
  code: string;
  name: string;
  flag: string;
}

export const SUPPORTED_LANGUAGES: LanguageOption[] = [
  { code: 'vi', name: 'Tiếng Việt', flag: '🇻🇳' },
  { code: 'en', name: 'English', flag: '🇺🇸' },
  { code: 'fr', name: 'Français', flag: '🇫🇷' },
  { code: 'ja', name: '日本語', flag: '🇯🇵' },
  { code: 'zh', name: '中文', flag: '🇨🇳' },
  { code: 'ko', name: '한국어', flag: '🇰🇷' },
];
