import React from 'react';
import { JobStatus, TargetStatus } from '../api/types';

interface StatusBadgeProps {
  status: JobStatus | TargetStatus | string;
}

const STATUS_LABELS_VI: Record<string, string> = {
  PENDING: 'Đang chờ',
  PROCESSING: 'Đang xử lý',
  TRANSLATING: 'Đang dịch',
  SYNTHESIZING: 'Đang tạo giọng đọc',
  PUBLISHED: 'Sẵn sàng',
  COMPLETED: 'Hoàn tất',
  PARTIALLY_COMPLETED: 'Một phần thất bại',
  PARTIAL_FAILED: 'Một phần thất bại',
  FAILED: 'Thất bại',
  TIMEOUT: 'Quá thời gian',
  CANCELLED: 'Đã hủy',
};

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status }) => {
  const norm = String(status || 'PENDING').toUpperCase();

  let className = 'status';
  if (norm === 'COMPLETED' || norm === 'PUBLISHED') {
    className += ' status-completed';
  } else if (
    norm === 'PROCESSING' ||
    norm === 'TRANSLATING' ||
    norm === 'SYNTHESIZING'
  ) {
    className += ' status-processing';
  } else if (
    norm === 'FAILED' ||
    norm === 'CANCELLED' ||
    norm === 'PARTIALLY_COMPLETED' ||
    norm === 'PARTIAL_FAILED' ||
    norm === 'TIMEOUT'
  ) {
    className += ' status-failed';
  }

  const label = STATUS_LABELS_VI[norm] || norm.replace(/_/g, ' ');

  return (
    <span className={className} data-status={norm}>
      {label}
    </span>
  );
};
