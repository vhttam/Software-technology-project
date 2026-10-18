import { describe, it, expect, vi, beforeEach } from 'vitest';

import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { AudioPreviewModal } from '../components/AudioPreviewModal';
import { contentApi } from '../api/contentApi';

vi.mock('../api/contentApi', () => ({
  contentApi: {
    listPoiNarrations: vi.fn(),
    getNarrationDetail: vi.fn(),
  },
}));

describe('AudioPreviewModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty message when no narrations are published', async () => {
    vi.mocked(contentApi.listPoiNarrations).mockResolvedValueOnce({
      poiId: 'poi-1',
      poiName: 'Bảo tàng Lịch sử',
      contentTitle: 'Giới thiệu bảo tàng',
      narrations: [],
    });

    render(<AudioPreviewModal poiId="poi-1" onClose={vi.fn()} />);

    expect(screen.getByText('Đang kiểm tra các bản thuyết minh...')).toBeInTheDocument();

    await waitFor(() => {
      expect(
        screen.getByText(/Chưa có bản thuyết minh nào được xuất bản/i)
      ).toBeInTheDocument();
    });
  });

  it('renders language tabs, subtitle, and audio player when narrations exist', async () => {
    vi.mocked(contentApi.listPoiNarrations).mockResolvedValueOnce({
      poiId: 'poi-1',
      poiName: 'Bảo tàng Lịch sử',
      contentTitle: 'Giới thiệu bảo tàng',
      narrations: [
        { lang: 'vi', version: 1 },
        { lang: 'en', version: 1 },
      ],
    });

    vi.mocked(contentApi.getNarrationDetail).mockResolvedValueOnce({
      poiId: 'poi-1',
      poiName: 'Bảo tàng Lịch sử',
      contentTitle: 'Giới thiệu bảo tàng',
      lang: 'vi',
      version: 1,
      subtitle: 'Chào mừng quý khách đến với Bảo tàng Lịch sử.',
      audioUrl: 'http://localhost:8085/media/audio/123.mp3?expires=9999&signature=abc',
      audioUrlExpiresAt: '2026-10-16T18:00:00Z',
    });

    render(<AudioPreviewModal poiId="poi-1" onClose={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Tiếng Việt' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Tiếng Anh/i })).toBeInTheDocument();
      expect(
        screen.getByText('Chào mừng quý khách đến với Bảo tàng Lịch sử.')
      ).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /Làm mới/i })
      ).toBeInTheDocument();
      expect(screen.queryByText(/Phát tệp MP3/i)).not.toBeInTheDocument();
      expect(screen.queryByText(/Phát giọng đọc trực tiếp/i)).not.toBeInTheDocument();
    });
  });

  it('calls onClose when close button is clicked', async () => {
    const handleClose = vi.fn();
    vi.mocked(contentApi.listPoiNarrations).mockResolvedValueOnce({
      poiId: 'poi-1',
      poiName: 'Bảo tàng Lịch sử',
      contentTitle: 'Giới thiệu bảo tàng',
      narrations: [],
    });

    render(<AudioPreviewModal poiId="poi-1" onClose={handleClose} />);

    await waitFor(() => {
      expect(
        screen.getByText(/Chưa có bản thuyết minh nào được xuất bản/i)
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText('Đóng'));
    expect(handleClose).toHaveBeenCalledTimes(1);
  });
});
