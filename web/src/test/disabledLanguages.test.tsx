import { describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { BrowserRouter } from 'react-router-dom';
import { setSession } from '../api/apiClient';
import { AuthProvider } from '../auth/AuthContext';
import { CreateJobPage } from '../pages/CreateJobPage';
import { BatchTranslateModal } from '../components/BatchTranslateModal';
import { AudioPreviewModal } from '../components/AudioPreviewModal';
import {
  getAllLanguages,
  getEnabledLanguages,
  getLanguageUnavailableMessage,
} from '../config/languageRegistry';
import { server } from './handlers';

describe('Quản lý hiển thị ngôn ngữ chưa kích hoạt và tạm dừng', () => {
  it('ngôn ngữ enabled=false không nằm trong danh sách enabled của language registry', () => {
    const enabledLangs = getEnabledLanguages();
    const allLangs = getAllLanguages();

    const enabledCodes = enabledLangs.map((l) => l.code);
    expect(enabledCodes).toContain('vi');
    expect(enabledCodes).toContain('en');
    expect(enabledCodes).toContain('fr');
    expect(enabledCodes).toContain('ja');
    expect(enabledCodes).toContain('zh');
    expect(enabledCodes).toContain('ko');

    // Các ngôn ngữ mẫu enabled=false tuyệt đối không xuất hiện
    expect(enabledCodes).not.toContain('es');
    expect(enabledCodes).not.toContain('th');
    expect(enabledCodes).not.toContain('id');
    expect(enabledCodes).not.toContain('ar');

    expect(allLangs.map((l) => l.code)).toEqual(enabledCodes);
  });

  it('ngôn ngữ enabled=false hoàn toàn không xuất hiện trên giao diện CreateJobPage', async () => {
    setSession({
      accessToken: 'valid-token',
      refreshToken: 'valid-refresh',
      user: { userId: 'u-1', username: 'admin', name: 'Admin', role: 'CONTENT_ADMIN' },
    });

    server.use(
      http.get('*/api/v1/contents', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            items: [
              {
                contentId: 'c-1',
                poiId: 'p-1',
                poiName: 'Chùa Một Cột',
                version: 1,
                title: 'Thuyết minh Chùa Một Cột',
                sourceLang: 'vi',
                isDeleted: false,
              },
            ],
            page: 1,
            pageSize: 20,
            totalItems: 1,
            totalPages: 1,
          },
        });
      }),
      http.get('*/api/v1/pois/:poiId/narrations', () => {
        return HttpResponse.json({
          status: 'success',
          data: { poiId: 'p-1', poiName: 'Chùa Một Cột', contentTitle: 'Thuyết minh Chùa Một Cột', narrations: [] },
        });
      }),
      http.get('*/api/v1/languages/config', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            languages: getEnabledLanguages(),
          },
        });
      })
    );

    render(
      <AuthProvider>
        <BrowserRouter>
          <CreateJobPage />
        </BrowserRouter>
      </AuthProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('Thuyết minh Chùa Một Cột')).toBeInTheDocument();
    });

    // 6 ngôn ngữ kích hoạt phải hiển thị
    expect(screen.getByText(/Tiếng Việt/)).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Anh/)).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Pháp/)).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Nhật/)).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Trung/)).toBeInTheDocument();
    expect(screen.getByText(/Tiếng Hàn/)).toBeInTheDocument();

    // Các ngôn ngữ tắt (es, th, id, ar) tuyệt đối KHÔNG có trong DOM
    expect(screen.queryByText(/Tiếng Tây Ban Nha/)).toBeNull();
    expect(screen.queryByText(/Tiếng Thái/)).toBeNull();
    expect(screen.queryByText(/Tiếng Indonesia/)).toBeNull();
    expect(screen.queryByText(/Tiếng Ả Rập/)).toBeNull();
    expect(document.querySelector('#target-es')).toBeNull();
    expect(document.querySelector('#target-th')).toBeNull();
    expect(document.querySelector('#target-id')).toBeNull();
    expect(document.querySelector('#target-ar')).toBeNull();
    expect(screen.queryByText(/Chưa kiểm tra/)).toBeNull();
  });

  it('ngôn ngữ enabled=false hoàn toàn không xuất hiện trong dropdown BatchTranslateModal', async () => {
    server.use(
      http.get('*/api/v1/languages/config', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            languages: getEnabledLanguages(),
          },
        });
      })
    );

    const { container } = render(
      <AuthProvider>
        <BatchTranslateModal onClose={() => {}} />
      </AuthProvider>
    );

    const select = container.querySelector('select');
    expect(select).toBeInTheDocument();

    const options = Array.from(select!.querySelectorAll('option')).map((opt) => opt.value);
    expect(options).toContain('en');
    expect(options).toContain('fr');
    expect(options).not.toContain('es');
    expect(options).not.toContain('th');
    expect(options).not.toContain('id');
    expect(options).not.toContain('ar');
    expect(screen.queryByText(/Chưa kiểm tra/)).toBeNull();
  });

  it('bản dịch cũ của ngôn ngữ đã tạm dừng vẫn nghe thử được và gắn nhãn Ngôn ngữ đã tạm dừng trong AudioPreviewModal', async () => {
    server.use(
      http.get('*/api/v1/client/pois/:poiId/narrations', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            poiId: 'p-1',
            poiName: 'Chùa Một Cột',
            contentTitle: 'Thuyết minh Chùa Một Cột',
            narrations: [
              { lang: 'vi', version: 1, isOutdated: false },
              { lang: 'es', version: 1, isOutdated: false }, // Bản dịch tiếng Tây Ban Nha cũ khi es đã enabled=false
            ],
          },
        });
      }),
      http.get('*/api/v1/client/pois/:poiId/narrations/:lang', () => {
        return HttpResponse.json({
          status: 'success',
          data: {
            lang: 'es',
            version: 1,
            textContent: 'Texto en español...',
            audioUrl: 'http://localhost/sample.mp3',
            audioUrlExpiresAt: new Date(Date.now() + 3600000).toISOString(),
            isOutdated: false,
          },
        });
      })
    );

    render(
      <AuthProvider>
        <AudioPreviewModal poiId="p-1" initialLang="es" onClose={() => {}} />
      </AuthProvider>
    );

    await waitFor(() => {
      // Tab tiếng Tây Ban Nha vẫn xuất hiện để nghe lại
      const tabs = screen.getAllByText(/Tiếng Tây Ban Nha/);
      expect(tabs.length).toBeGreaterThan(0);
    });

    // Xuất hiện nhãn "Ngôn ngữ đã tạm dừng"
    const pausedBadges = screen.getAllByText('Ngôn ngữ đã tạm dừng');
    expect(pausedBadges.length).toBeGreaterThan(0);
  });

  it('ngôn ngữ đang bật nhưng nhà cung cấp lỗi hiển thị lý do tiếng Việt thân thiện, không dùng thuật ngữ kỹ thuật', () => {
    // Ngôn ngữ giả định đang bật nhưng available=false
    const mockUnavailableLang = {
      code: 'ja',
      nameVi: 'Tiếng Nhật',
      nativeName: '日本語',
      ttsLocale: 'ja',
      sortOrder: 4,
      enabled: true,
      available: false,
      disabledReason: 'Network error with TTS provider',
      voices: [],
    };

    const msg = getLanguageUnavailableMessage(mockUnavailableLang);
    expect(msg).toBe('Tạm thời chưa dùng được. Hãy thử lại sau.');
    expect(msg).not.toMatch(/RTL|segmentation|provider|API|mẫu/i);
  });
});
