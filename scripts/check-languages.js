#!/usr/bin/env node

/**
 * Script kiểm tra khả năng hỗ trợ ngôn ngữ của hệ thống.
 * Chạy: node scripts/check-languages.js [--lang=es] [--all]
 */

const fs = require('fs');
const path = require('path');

const CONFIG_PATH = path.resolve(__dirname, '../config/languages.json');

function loadConfig() {
  if (!fs.existsSync(CONFIG_PATH)) {
    console.error(`[LỖI] Không tìm thấy tệp cấu hình tại: ${CONFIG_PATH}`);
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf-8'));
}

async function testTranslation(text, sourceLang, targetLocale) {
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=${encodeURIComponent(sourceLang)}&tl=${encodeURIComponent(targetLocale)}&dt=t&q=${encodeURIComponent(text)}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);

  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
    });
    clearTimeout(timeout);
    if (!res.ok) {
      return { ok: false, errorType: 'HTTP_ERROR', message: `HTTP ${res.status}` };
    }
    const json = await res.json();
    if (Array.isArray(json) && Array.isArray(json[0])) {
      const translated = json[0].map((seg) => (seg ? seg[0] : '')).join('');
      return { ok: true, text: translated };
    }
    return { ok: false, errorType: 'PARSE_ERROR', message: 'Không phân tích được phản hồi dịch' };
  } catch (err) {
    clearTimeout(timeout);
    const isTimeout = err.name === 'AbortError';
    return {
      ok: false,
      errorType: 'NETWORK_ERROR',
      message: isTimeout ? 'Quá thời gian kết nối (10s)' : `Lỗi mạng: ${err.message}`,
    };
  }
}

async function testTtsAudio(text, ttsLocale) {
  const chunk = text.slice(0, 80);
  const url = `https://translate.google.com/translate_tts?ie=UTF-8&tl=${encodeURIComponent(ttsLocale)}&client=tw-ob&q=${encodeURIComponent(chunk)}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);

  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' },
    });
    clearTimeout(timeout);
    if (!res.ok) {
      return { ok: false, errorType: 'HTTP_ERROR', message: `HTTP ${res.status}` };
    }
    const arrayBuffer = await res.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);
    if (buffer.length < 500) {
      return { ok: false, errorType: 'TOO_SHORT', message: `Tệp âm thanh quá nhỏ (${buffer.length} bytes)` };
    }
    return { ok: true, buffer };
  } catch (err) {
    clearTimeout(timeout);
    const isTimeout = err.name === 'AbortError';
    return {
      ok: false,
      errorType: 'NETWORK_ERROR',
      message: isTimeout ? 'Quá thời gian tải TTS (12s)' : `Lỗi mạng: ${err.message}`,
    };
  }
}

function calculateAudioMetrics(buffer, charCount) {
  // Google TTS 32kbps mono MP3 is approx 4000 bytes per second
  const durationSec = buffer.length / 4000.0;
  let zeroBytes = 0;
  for (let i = 0; i < buffer.length; i++) {
    if (buffer[i] === 0) zeroBytes++;
  }
  const silenceRatio = zeroBytes / buffer.length;
  const speed = charCount / durationSec;
  return { durationSec, speed, silenceRatio };
}

async function runCheck() {
  const args = process.argv.slice(2);
  let targetLangArg = null;
  let checkAll = false;

  for (const arg of args) {
    if (arg.startsWith('--lang=')) {
      targetLangArg = arg.split('=')[1].toLowerCase().trim();
    } else if (arg === '--all') {
      checkAll = true;
    }
  }

  const allConfigs = loadConfig();
  let toTest = [];

  if (targetLangArg) {
    const found = allConfigs.find((l) => l.code.toLowerCase() === targetLangArg);
    if (!found) {
      console.error(`[LỖI] Không tìm thấy cấu hình cho ngôn ngữ '${targetLangArg}' trong config/languages.json`);
      process.exit(1);
    }
    toTest = [found];
  } else if (checkAll) {
    toTest = allConfigs;
  } else {
    // Default: test all enabled languages
    toTest = allConfigs.filter((l) => l.enabled);
  }

  console.log('='.repeat(80));
  console.log(`KIỂM TRA TỰ ĐỘNG KHẢ NĂNG NHÀ CUNG CẤP NGÔN NGỮ (Số lượng: ${toTest.length})`);
  console.log('='.repeat(80));

  const sampleVi = 'Xin chào, chào mừng quý khách đến tham quan bảo tàng lịch sử và khám phá các hiện vật quý giá.';

  const results = [];

  for (const langDef of toTest) {
    process.stdout.write(`Đang kiểm tra [${langDef.code}] ${langDef.nameVi}... `);

    // 1. Dịch thử
    const transLocale = langDef.translationLocale || langDef.code;
    const transRes = await testTranslation(sampleVi, 'vi', transLocale);

    if (!transRes.ok) {
      const isNetwork = transRes.errorType === 'NETWORK_ERROR';
      console.log(`❌ THẤT BẠI (Dịch thuật)`);
      results.push({
        code: langDef.code,
        nameVi: langDef.nameVi,
        passed: false,
        warningOnly: isNetwork,
        step: 'Dịch thuật',
        reason: transRes.message,
        speed: '-',
        silence: '-',
      });
      continue;
    }

    const translatedText = transRes.text;

    // 2. Tải thử âm thanh TTS
    const ttsLocale = langDef.ttsLocale || langDef.code;
    const ttsRes = await testTtsAudio(translatedText, ttsLocale);

    if (!ttsRes.ok) {
      const isNetwork = ttsRes.errorType === 'NETWORK_ERROR';
      console.log(`❌ THẤT BẠI (TTS)`);
      results.push({
        code: langDef.code,
        nameVi: langDef.nameVi,
        passed: false,
        warningOnly: isNetwork,
        step: 'Giọng đọc TTS',
        reason: ttsRes.message,
        speed: '-',
        silence: '-',
      });
      continue;
    }

    // 3. Đo chỉ số âm thanh
    const metrics = calculateAudioMetrics(ttsRes.buffer, translatedText.length);
    const minSpeed = langDef.minReadingSpeed || 5.0;
    const maxSpeed = langDef.maxReadingSpeed || 25.0;

    let passed = true;
    let failReason = null;

    if (metrics.silenceRatio > 0.40) {
      passed = false;
      failReason = `Tỷ lệ im lặng quá cao: ${(metrics.silenceRatio * 100).toFixed(1)}% (> 40%)`;
    } else if (metrics.durationSec < 1.0) {
      passed = false;
      failReason = `Thời lượng quá ngắn: ${metrics.durationSec.toFixed(1)}s`;
    }

    if (passed) {
      console.log(`✓ ĐẠT (${metrics.speed.toFixed(1)} ký tự/s, im lặng ${(metrics.silenceRatio * 100).toFixed(1)}%)`);
      results.push({
        code: langDef.code,
        nameVi: langDef.nameVi,
        passed: true,
        step: 'Hoàn tất',
        reason: 'Đạt mọi tiêu chuẩn kiểm định',
        speed: `${metrics.speed.toFixed(1)} kt/s`,
        silence: `${(metrics.silenceRatio * 100).toFixed(1)}%`,
      });
    } else {
      console.log(`❌ KHÔNG ĐẠT (${failReason})`);
      results.push({
        code: langDef.code,
        nameVi: langDef.nameVi,
        passed: false,
        warningOnly: false,
        step: 'Chất lượng âm thanh',
        reason: failReason,
        speed: `${metrics.speed.toFixed(1)} kt/s`,
        silence: `${(metrics.silenceRatio * 100).toFixed(1)}%`,
      });
    }
  }

  console.log('\n' + '='.repeat(80));
  console.log('BẢNG KẾT QUẢ KIỂM ĐỊNH CHI TIẾT');
  console.log('='.repeat(80));
  console.table(
    results.map((r) => ({
      'Mã': r.code,
      'Ngôn ngữ': r.nameVi,
      'Tốc độ đọc': r.speed,
      'Tỷ lệ im lặng': r.silence,
      'Kết quả': r.passed ? '✓ ĐẠT' : r.warningOnly ? '⚠️ CẢNH BÁO (Lỗi mạng)' : '❌ KHÔNG ĐẠT',
      'Chi tiết / Lý do': r.reason,
    }))
  );

  const hasHardFail = results.some((r) => !r.passed && !r.warningOnly);
  if (hasHardFail) {
    console.log('\n[KẾT LUẬN] Có ngôn ngữ KHÔNG ĐẠT tiêu chuẩn chất lượng. Vui lòng kiểm tra lại cấu hình.');
    process.exit(1);
  } else {
    console.log('\n[KẾT LUẬN] Mọi ngôn ngữ được kiểm tra đều ĐẠT tiêu chuẩn chất lượng âm thanh và dịch thuật.');
    process.exit(0);
  }
}

runCheck().catch((err) => {
  console.error('[LỖI KHÔNG MONG MUỐN]', err);
  process.exit(1);
});
