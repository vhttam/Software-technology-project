import { apiClient } from '../api/apiClient';

export interface VoiceOption {
  voiceId: string;
  gender: 'FEMALE' | 'MALE' | 'NEUTRAL';
  type: string;
  label: string;
  isDefault: boolean;
}

export interface LanguageDef {
  code: string;
  nameVi: string;
  nativeName: string;
  translationLocale?: string;
  ttsLocale: string;
  direction?: 'ltr' | 'rtl';
  fontStack?: string;
  sentenceDelimiters?: string;
  clauseDelimiters?: string;
  minReadingSpeed?: number;
  maxReadingSpeed?: number;
  maxTtsChunkChars?: number;
  maxTtsChunkBytes?: number;
  sortOrder: number;
  enabled?: boolean;
  available?: boolean;
  unavailableReason?: string;
  note?: string;
  disabledReason?: string;
  voices: VoiceOption[];
}

export const SUPPORTED_LANGUAGES: LanguageDef[] = [
  {
    code: 'vi',
    nameVi: 'Tiếng Việt',
    nativeName: 'Tiếng Việt',
    translationLocale: 'vi',
    ttsLocale: 'vi-VN',
    direction: 'ltr',
    fontStack: "'Times New Roman', Times, serif",
    sentenceDelimiters: '[.!?;\n]',
    clauseDelimiters: '[,]',
    minReadingSpeed: 12.0,
    maxReadingSpeed: 25.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 1,
    enabled: true,
    note: 'Ngôn ngữ gốc chính của hệ thống',
    voices: [
      { voiceId: 'vi-VN-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'vi-VN-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'en',
    nameVi: 'Tiếng Anh',
    nativeName: 'English',
    translationLocale: 'en',
    ttsLocale: 'en-US',
    direction: 'ltr',
    fontStack: "'Times New Roman', Times, serif",
    sentenceDelimiters: '[.!?;\n]',
    clauseDelimiters: '[,]',
    minReadingSpeed: 12.0,
    maxReadingSpeed: 18.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 2,
    enabled: true,
    note: 'Ngôn ngữ quốc tế thông dụng',
    voices: [
      { voiceId: 'en-US-Journey-F', gender: 'FEMALE', type: 'Journey', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'en-US-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'fr',
    nameVi: 'Tiếng Pháp',
    nativeName: 'Français',
    translationLocale: 'fr',
    ttsLocale: 'fr-FR',
    direction: 'ltr',
    fontStack: "'Times New Roman', Times, serif",
    sentenceDelimiters: '[.!?;\n]',
    clauseDelimiters: '[,]',
    minReadingSpeed: 12.0,
    maxReadingSpeed: 18.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 3,
    enabled: true,
    note: 'Ngôn ngữ cho khách Châu Âu',
    voices: [
      { voiceId: 'fr-FR-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'fr-FR-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'ja',
    nameVi: 'Tiếng Nhật',
    nativeName: '日本語',
    translationLocale: 'ja',
    ttsLocale: 'ja',
    direction: 'ltr',
    fontStack: "'Yu Gothic', 'Hiragino Sans', 'Meiryo', sans-serif",
    sentenceDelimiters: '[.!?;\n。！？；]',
    clauseDelimiters: '[,，、]',
    minReadingSpeed: 5.0,
    maxReadingSpeed: 9.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 4,
    enabled: true,
    note: 'Ngôn ngữ CJK cho khách Nhật Bản',
    voices: [
      { voiceId: 'ja-JP-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'ja-JP-Standard-C', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'zh',
    nameVi: 'Tiếng Trung',
    nativeName: '中文',
    translationLocale: 'zh-CN',
    ttsLocale: 'zh-CN',
    direction: 'ltr',
    fontStack: "'PingFang SC', 'Microsoft YaHei', 'SimHei', sans-serif",
    sentenceDelimiters: '[.!?;\n。！？；]',
    clauseDelimiters: '[,，、]',
    minReadingSpeed: 3.0,
    maxReadingSpeed: 7.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 5,
    enabled: true,
    note: 'Tiếng Trung phổ thông (Giản thể)',
    voices: [
      { voiceId: 'cmn-CN-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'cmn-CN-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'ko',
    nameVi: 'Tiếng Hàn',
    nativeName: '한국어',
    translationLocale: 'ko',
    ttsLocale: 'ko',
    direction: 'ltr',
    fontStack: "'Malgun Gothic', 'Apple SD Gothic Neo', 'Nanum Gothic', sans-serif",
    sentenceDelimiters: '[.!?;\n。！？；]',
    clauseDelimiters: '[,，、]',
    minReadingSpeed: 5.0,
    maxReadingSpeed: 9.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 6,
    enabled: true,
    note: 'Ngôn ngữ CJK cho khách Hàn Quốc',
    voices: [
      { voiceId: 'ko-KR-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'ko-KR-Standard-C', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'es',
    nameVi: 'Tiếng Tây Ban Nha',
    nativeName: 'Español',
    translationLocale: 'es',
    ttsLocale: 'es-ES',
    direction: 'ltr',
    fontStack: "'Times New Roman', Times, serif",
    sentenceDelimiters: '[.!?;\n]',
    clauseDelimiters: '[,]',
    minReadingSpeed: 12.0,
    maxReadingSpeed: 22.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 7,
    enabled: false,
    note: 'Chưa kiểm tra (mẫu)',
    voices: [
      { voiceId: 'es-ES-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'es-ES-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'th',
    nameVi: 'Tiếng Thái',
    nativeName: 'ไทย',
    translationLocale: 'th',
    ttsLocale: 'th-TH',
    direction: 'ltr',
    fontStack: "'Sarabun', 'Leelawadee UI', 'Noto Sans Thai', sans-serif",
    sentenceDelimiters: '[\n]',
    clauseDelimiters: '[\s]',
    minReadingSpeed: 6.0,
    maxReadingSpeed: 14.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 8,
    enabled: false,
    note: 'Chưa kiểm tra (cần xử lý tách từ/word segmentation)',
    voices: [
      { voiceId: 'th-TH-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
    ],
  },
  {
    code: 'id',
    nameVi: 'Tiếng Indonesia',
    nativeName: 'Bahasa Indonesia',
    translationLocale: 'id',
    ttsLocale: 'id-ID',
    direction: 'ltr',
    fontStack: "'Times New Roman', Times, serif",
    sentenceDelimiters: '[.!?;\n]',
    clauseDelimiters: '[,]',
    minReadingSpeed: 10.0,
    maxReadingSpeed: 20.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 9,
    enabled: false,
    note: 'Chưa kiểm tra (mẫu)',
    voices: [
      { voiceId: 'id-ID-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'id-ID-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
  {
    code: 'ar',
    nameVi: 'Tiếng Ả Rập',
    nativeName: 'العربية',
    translationLocale: 'ar',
    ttsLocale: 'ar-XA',
    direction: 'rtl',
    fontStack: "'Amiri', 'Traditional Arabic', 'Segoe UI', Tahoma, sans-serif",
    sentenceDelimiters: '[.!?;\n؟؛]',
    clauseDelimiters: '[,،]',
    minReadingSpeed: 8.0,
    maxReadingSpeed: 16.0,
    maxTtsChunkChars: 80,
    maxTtsChunkBytes: 120,
    sortOrder: 10,
    enabled: false,
    note: 'Chưa kiểm tra (cần xử lý hướng chữ RTL và dấu câu Ả Rập)',
    voices: [
      { voiceId: 'ar-XA-Standard-A', gender: 'FEMALE', type: 'Standard', label: 'Giọng nữ (khuyên dùng)', isDefault: true },
      { voiceId: 'ar-XA-Standard-B', gender: 'MALE', type: 'Standard', label: 'Giọng nam', isDefault: false },
    ],
  },
];

let dynamicLanguages: LanguageDef[] = [...SUPPORTED_LANGUAGES];

export async function fetchLanguageConfig(): Promise<LanguageDef[]> {
  try {
    const res = await apiClient<{ languages: LanguageDef[] }>('/api/v1/languages/config');
    if (res && Array.isArray(res.languages) && res.languages.length > 0) {
      dynamicLanguages = res.languages;
    }
  } catch {
    // If backend not reachable or unauthenticated, keep current fallback
  }
  return dynamicLanguages;
}

export function getAllLanguages(): LanguageDef[] {
  return dynamicLanguages.filter((l) => l.enabled !== false);
}

export function getEnabledLanguages(): LanguageDef[] {
  return dynamicLanguages.filter((l) => l.enabled !== false);
}

export function getAllLanguagesIncludingDisabled(): LanguageDef[] {
  return dynamicLanguages;
}

export function isLanguageAvailable(code: string): boolean {
  const def = getLanguageDef(code);
  if (!def || def.enabled === false) return false;
  if (def.available === false || def.disabledReason) return false;
  return true;
}

export function getLanguageUnavailableMessage(target: string | LanguageDef): string | null {
  const def = typeof target === 'string' ? getLanguageDef(target) : target;
  if (!def) return null;
  if (def.available === false || def.disabledReason) {
    return 'Tạm thời chưa dùng được. Hãy thử lại sau.';
  }
  return null;
}

export function getLanguageDef(code: string): LanguageDef | undefined {
  if (!code) return undefined;
  const match = dynamicLanguages.find((l) => l.code.toLowerCase() === code.trim().toLowerCase());
  if (match) return match;
  return SUPPORTED_LANGUAGES.find((l) => l.code.toLowerCase() === code.trim().toLowerCase());
}

export const getLanguageByCode = getLanguageDef;

export function getLanguageDisplayName(code: string): string {
  const def = getLanguageDef(code);
  if (!def) return code.toUpperCase();
  if (def.nameVi === def.nativeName) return def.nameVi;
  return `${def.nameVi} (${def.nativeName})`;
}

export function getLanguageShortLabel(code: string): string {
  const def = getLanguageDef(code);
  return def ? def.nameVi : code.toUpperCase();
}

export function getVoiceByVoiceId(voiceId: string): VoiceOption | undefined {
  if (!voiceId) return undefined;
  for (const langDef of dynamicLanguages) {
    const v = langDef.voices.find((opt) => opt.voiceId === voiceId);
    if (v) return v;
  }
  for (const langDef of SUPPORTED_LANGUAGES) {
    const v = langDef.voices.find((opt) => opt.voiceId === voiceId);
    if (v) return v;
  }
  return undefined;
}

export function getVoiceOptionsForLanguage(lang: string): VoiceOption[] {
  const def = getLanguageDef(lang);
  return def ? def.voices : [];
}

export function getDefaultVoiceForLanguage(lang: string): VoiceOption | undefined {
  const def = getLanguageDef(lang);
  if (!def || !def.voices.length) return undefined;
  return def.voices.find((v) => v.isDefault) || def.voices[0];
}

export function getVoiceFriendlyLabel(voiceId: string, lang?: string): string {
  if (!voiceId) return 'Mặc định';
  for (const langDef of dynamicLanguages) {
    if (lang && langDef.code.toLowerCase() !== lang.toLowerCase()) continue;
    const v = langDef.voices.find((opt) => opt.voiceId === voiceId);
    if (v) return v.label;
  }
  for (const langDef of SUPPORTED_LANGUAGES) {
    if (lang && langDef.code.toLowerCase() !== lang.toLowerCase()) continue;
    const v = langDef.voices.find((opt) => opt.voiceId === voiceId);
    if (v) return v.label;
  }
  if (voiceId === 'en-US-Journey-F') return 'Giọng nữ (khuyên dùng)';
  if (voiceId === 'en-GB-Standard-A') return 'Giọng nữ – Tiêu chuẩn Anh';
  if (voiceId === 'vi-VN-Standard-A') return 'Giọng nữ (khuyên dùng)';
  if (voiceId === 'fr-FR-Standard-A') return 'Giọng nữ (khuyên dùng)';
  return voiceId;
}

export function removeAccents(str: string): string {
  if (!str) return '';
  return str
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .trim();
}
