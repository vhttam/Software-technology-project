export type SupportedLang = 'vi' | 'en' | 'fr' | 'ja' | 'ko' | 'zh';

export interface Translations {
  // Header
  brandTitle: string;
  brandSubtitle: string;
  scanQrBtn: string;
  scanQrTitle: string;

  // Toolbar
  searchPlaceholder: string;
  locatingGps: string;
  locatingRequest: string;
  locatedSuccess: string;
  locatedCenterFallback: string;
  recenterTitle: string;
  mapModeStreet: string;
  mapModeSatellite: string;
  mapModeToggleTitle: string;

  // Map Legend
  legendYourLocation: string;
  legendAttractions: string;
  mapPopupGps: string;
  mapPopupViewAndListen: string;

  // Nearby List Panel
  nearbyHeading: string;
  locationsCount: string;
  noPoiFound: string;
  noDescription: string;
  viewQrAndNarration: string;
  awayLabel: string; // e.g. "Cách" in VI or prefix/suffix

  // Distance formatting helper
  distanceFormat: (meters?: number) => string;

  // POI QR Modal
  qrModalBadge: string;
  distanceFromYou: string;
  qrGenerating: string;
  qrGuideDesc: string;
  listenNowBtn: string;
  loadingNarration: string;
  openCameraOther: string;

  // Camera QR Scanner Modal
  scanModalTitle: string;
  scanModalSubtitle: string;
  cameraErrorDefault: string;
  chooseImageFile: string;
  quickDemoTitle: string;
  manualInputPlaceholder: string;
  confirmBtn: string;
  invalidQrImage: string;
  poiNotFoundQr: string;
  qrLookupError: string;

  // Narration Player Modal
  playerBadge: string;
  subtitleHeading: string;
  audioPlaying: string;
  audioPaused: string;
  speedLabel: string;
  audioPreparingFallback: string;
  readSubtitleNote: string;
  closePlayerBtn: string;
  noNarrationAvailable: string;
  multilingualNarrationSub: string;
  fallbackLanguageNotice: (targetLang: string, actualLang: string) => string;
}

export const I18N_DICTIONARY: Record<SupportedLang, Translations> = {
  vi: {
    brandTitle: 'HỆ THỐNG THUYẾT MINH TỰ ĐỘNG',
    brandSubtitle: 'Khám phá di sản và điểm tham quan thông minh',
    scanQrBtn: 'Quét mã QR',
    scanQrTitle: 'Mở camera quét mã QR',

    searchPlaceholder: 'Tìm kiếm điểm tham quan, di tích, địa danh...',
    locatingGps: 'Đang định vị GPS...',
    locatingRequest: 'Đang xin quyền GPS...',
    locatedSuccess: 'Đã định vị thành công',
    locatedCenterFallback: 'Vị trí trung tâm (Mặc định)',
    recenterTitle: 'Định vị và canh giữa vị trí của bạn',
    mapModeStreet: 'Đường phố',
    mapModeSatellite: 'Vệ tinh',
    mapModeToggleTitle: 'Chuyển đổi kiểu hiển thị bản đồ',

    legendYourLocation: 'Vị trí của bạn',
    legendAttractions: 'Điểm tham quan',
    mapPopupGps: 'Vị trí GPS của bạn',
    mapPopupViewAndListen: 'Xem mã QR & Nghe thuyết minh',

    nearbyHeading: 'Gợi ý điểm tham quan lân cận',
    locationsCount: 'địa điểm',
    noPoiFound: 'Không tìm thấy điểm tham quan nào phù hợp với từ khóa tìm kiếm.',
    noDescription: 'Chưa có mô tả chi tiết cho địa điểm tham quan này.',
    viewQrAndNarration: 'Xem mã QR & Thuyết minh',
    awayLabel: 'Cách',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `Cách ${Math.round(meters)} m` : `Cách ${(meters / 1000).toFixed(1)} km`;
    },

    qrModalBadge: 'MÃ QR ĐIỂM THAM QUAN',
    distanceFromYou: 'Khoảng cách từ bạn',
    qrGenerating: 'Đang tạo mã QR sắc nét...',
    qrGuideDesc: 'Quét mã QR này tại biển di tích hoặc bấm nghe trực tiếp để bắt đầu thuyết minh tự động.',
    listenNowBtn: 'Nghe thuyết minh ngay trên máy này',
    loadingNarration: 'Đang tải thuyết minh...',
    openCameraOther: 'Mở camera quét mã điểm khác',

    scanModalTitle: 'Quét mã QR tại điểm tham quan',
    scanModalSubtitle: 'Hướng camera về phía mã QR dán tại biển di tích',
    cameraErrorDefault: 'Không thể khởi động camera trực tiếp. Vui lòng cấp quyền camera hoặc tải ảnh QR từ máy.',
    chooseImageFile: 'Chọn ảnh QR từ thiết bị',
    quickDemoTitle: 'Hoặc chọn nhanh điểm mẫu để nghe thử:',
    manualInputPlaceholder: 'Nhập chuỗi mã QR thủ công...',
    confirmBtn: 'Xác nhận',
    invalidQrImage: 'Không tìm thấy mã QR hợp lệ trong ảnh này. Vui lòng thử ảnh khác!',
    poiNotFoundQr: 'Không tìm thấy điểm tham quan tương ứng với mã QR này.',
    qrLookupError: 'Lỗi khi tra cứu mã QR.',

    playerBadge: 'THUYẾT MINH ÂM THANH & PHỤ ĐỀ',
    subtitleHeading: 'Phụ đề thuyết minh',
    audioPlaying: 'Đang phát âm thanh...',
    audioPaused: 'Đã tạm dừng',
    speedLabel: 'Tốc độ',
    audioPreparingFallback: 'Bản ghi âm giọng đọc chuẩn cho ngôn ngữ này đang được tạo.',
    readSubtitleNote: 'Quý khách có thể theo dõi phụ đề dịch thuật chi tiết ở khung trên.',
    closePlayerBtn: 'Đóng hộp thuyết minh',
    noNarrationAvailable: 'Chưa có thuyết minh',
    multilingualNarrationSub: 'Thuyết minh đa ngôn ngữ',
    fallbackLanguageNotice: (target, actual) =>
      `Chưa có bản ghi âm (${target.toUpperCase()}), đang phát bản (${actual.toUpperCase()}) khả dụng.`,
  },

  en: {
    brandTitle: 'MULTILINGUAL AUTO NARRATION',
    brandSubtitle: 'Smart heritage and cultural attraction discovery',
    scanQrBtn: 'Scan QR Code',
    scanQrTitle: 'Open camera to scan QR code',

    searchPlaceholder: 'Search attractions, monuments, landmarks...',
    locatingGps: 'Locating GPS...',
    locatingRequest: 'Requesting GPS permission...',
    locatedSuccess: 'Located successfully',
    locatedCenterFallback: 'City Center (Default)',
    recenterTitle: 'Recenter on your location',
    mapModeStreet: 'Street',
    mapModeSatellite: 'Satellite',
    mapModeToggleTitle: 'Toggle map display style',

    legendYourLocation: 'Your Location',
    legendAttractions: 'Attractions',
    mapPopupGps: 'Your GPS Location',
    mapPopupViewAndListen: 'View QR & Listen Narration',

    nearbyHeading: 'Nearby Attractions',
    locationsCount: 'locations',
    noPoiFound: 'No attractions match your search query.',
    noDescription: 'No detailed description available for this place.',
    viewQrAndNarration: 'View QR & Narration',
    awayLabel: 'Away',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `${Math.round(meters)} m away` : `${(meters / 1000).toFixed(1)} km away`;
    },

    qrModalBadge: 'ATTRACTION QR CODE',
    distanceFromYou: 'Distance from you',
    qrGenerating: 'Generating high-definition QR...',
    qrGuideDesc: 'Scan this QR code at the landmark plaque or tap Listen Now to begin auto narration.',
    listenNowBtn: 'Listen to narration on this device',
    loadingNarration: 'Loading narration...',
    openCameraOther: 'Open camera to scan other POIs',

    scanModalTitle: 'Scan Landmark QR Code',
    scanModalSubtitle: 'Point camera at the QR code affixed on the landmark plaque',
    cameraErrorDefault: 'Cannot open live camera. Please grant camera permission or upload a QR image.',
    chooseImageFile: 'Select QR image from device',
    quickDemoTitle: 'Or quickly select a sample landmark to preview:',
    manualInputPlaceholder: 'Enter QR code text manually...',
    confirmBtn: 'Confirm',
    invalidQrImage: 'No valid QR code detected in this image. Please try another image!',
    poiNotFoundQr: 'No attraction found matching this QR code.',
    qrLookupError: 'Error occurred while looking up QR code.',

    playerBadge: 'AUDIO NARRATION & SUBTITLES',
    subtitleHeading: 'Narration Subtitle',
    audioPlaying: 'Playing audio...',
    audioPaused: 'Paused',
    speedLabel: 'Speed',
    audioPreparingFallback: 'Standard audio recording for this language is being prepared.',
    readSubtitleNote: 'You can follow the detailed translated subtitle in the box above.',
    closePlayerBtn: 'Close Narration Box',
    noNarrationAvailable: 'No narration available',
    multilingualNarrationSub: 'Multilingual narration',
    fallbackLanguageNotice: (target, actual) =>
      `No audio in (${target.toUpperCase()}) yet. Playing available (${actual.toUpperCase()}) audio.`,
  },

  fr: {
    brandTitle: 'SYSTÈME DE NARRATION AUTOMATIQUE',
    brandSubtitle: 'Découvrez le patrimoine et les attractions intelligentes',
    scanQrBtn: 'Scanner le QR',
    scanQrTitle: 'Ouvrir la caméra pour scanner le QR',

    searchPlaceholder: 'Rechercher des monuments, attractions, lieux...',
    locatingGps: 'Localisation GPS en cours...',
    locatingRequest: 'Demande d’autorisation GPS...',
    locatedSuccess: 'Localisation réussie',
    locatedCenterFallback: 'Centre-ville (Par défaut)',
    recenterTitle: 'Recentrer sur votre position',
    mapModeStreet: 'Plan rue',
    mapModeSatellite: 'Satellite',
    mapModeToggleTitle: 'Basculer le style de carte',

    legendYourLocation: 'Votre position',
    legendAttractions: 'Attractions',
    mapPopupGps: 'Votre position GPS',
    mapPopupViewAndListen: 'Voir le QR et écouter',

    nearbyHeading: 'Attractions à proximité',
    locationsCount: 'lieux',
    noPoiFound: 'Aucune attraction ne correspond à votre recherche.',
    noDescription: 'Aucune description détaillée disponible pour ce lieu.',
    viewQrAndNarration: 'Voir QR & Narration',
    awayLabel: 'À',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `À ${Math.round(meters)} m` : `À ${(meters / 1000).toFixed(1)} km`;
    },

    qrModalBadge: 'CODE QR DU SITE',
    distanceFromYou: 'Distance depuis vous',
    qrGenerating: 'Génération du QR code HD...',
    qrGuideDesc: 'Scannez ce code QR sur la plaque du monument ou cliquez pour écouter la narration.',
    listenNowBtn: 'Écouter la narration sur cet appareil',
    loadingNarration: 'Chargement de la narration...',
    openCameraOther: 'Ouvrir la caméra pour un autre point',

    scanModalTitle: 'Scanner le QR Code du site',
    scanModalSubtitle: 'Dirigez la caméra vers le QR code fixé sur le panneau',
    cameraErrorDefault: 'Impossible d’activer la caméra. Veuillez autoriser la caméra ou charger une image QR.',
    chooseImageFile: 'Choisir une image QR depuis l’appareil',
    quickDemoTitle: 'Ou sélectionnez un point échantillon pour tester:',
    manualInputPlaceholder: 'Saisir manuellement le texte QR...',
    confirmBtn: 'Confirmer',
    invalidQrImage: 'Aucun QR code valide détecté dans cette image. Essayez-en une autre !',
    poiNotFoundQr: 'Aucune attraction trouvée correspondant à ce QR code.',
    qrLookupError: 'Erreur lors de la recherche du code QR.',

    playerBadge: 'NARRATION AUDIO ET SOUS-TITRES',
    subtitleHeading: 'Sous-titre de narration',
    audioPlaying: 'Lecture audio...',
    audioPaused: 'En pause',
    speedLabel: 'Vitesse',
    audioPreparingFallback: 'L’enregistrement audio standard pour cette langue est en cours de création.',
    readSubtitleNote: 'Vous pouvez lire les sous-titres traduits détaillés dans le cadre ci-dessus.',
    closePlayerBtn: 'Fermer la boîte de narration',
    noNarrationAvailable: 'Aucune narration disponible',
    multilingualNarrationSub: 'Narration multilingue',
    fallbackLanguageNotice: (target, actual) =>
      `Aucun audio en (${target.toUpperCase()}). Lecture de la version (${actual.toUpperCase()}) disponible.`,
  },

  ja: {
    brandTitle: '多言語自動音声ガイドシステム',
    brandSubtitle: 'スマートな遺産と歴史的観光スポットの探索',
    scanQrBtn: 'QRコードスキャン',
    scanQrTitle: 'カメラを開いてQRコードをスキャン',

    searchPlaceholder: '観光地、史跡、名所、ランドマークを検索...',
    locatingGps: 'GPS現在地を測定中...',
    locatingRequest: 'GPS権限を要求しています...',
    locatedSuccess: '現在地を特定しました',
    locatedCenterFallback: '市内中心部（デフォルト）',
    recenterTitle: '現在地を中心にして再表示',
    mapModeStreet: '標準マップ',
    mapModeSatellite: '航空写真',
    mapModeToggleTitle: '地図表示の切り替え',

    legendYourLocation: '現在地',
    legendAttractions: '観光スポット',
    mapPopupGps: 'あなたのGPS現在地',
    mapPopupViewAndListen: 'QR表示と音声ガイド',

    nearbyHeading: '近くのおすすめ観光スポット',
    locationsCount: 'スポット',
    noPoiFound: '検索条件に一致する観光スポットは見つかりませんでした。',
    noDescription: 'この観光スポットの詳細説明はまだ登録されていません。',
    viewQrAndNarration: 'QRコード＆音声ガイドを見る',
    awayLabel: '距離',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `約 ${Math.round(meters)} m` : `約 ${(meters / 1000).toFixed(1)} km`;
    },

    qrModalBadge: 'スポットQRコード',
    distanceFromYou: '現在地からの距離',
    qrGenerating: '高解像度QRコードを生成中...',
    qrGuideDesc: '現地プレートのQRコードをスキャンするか、「今すぐ聴く」を押して音声解説を開始します。',
    listenNowBtn: 'この端末で今すぐ解説を聴く',
    loadingNarration: '解説音声を読み込み中...',
    openCameraOther: '他のスポットをスキャン',

    scanModalTitle: '観光スポットのQRコードをスキャン',
    scanModalSubtitle: '看板やプレートに貼られたQRコードにカメラを向けてください',
    cameraErrorDefault: 'カメラを起動できません。カメラのアクセス許可を与えるか、QR画像をアップロードしてください。',
    chooseImageFile: '端末からQR画像を選択',
    quickDemoTitle: 'またはサンプル観光地を選んで試聴:',
    manualInputPlaceholder: 'QRコードのテキストを手動入力...',
    confirmBtn: '確認',
    invalidQrImage: 'この画像から有効なQRコードが見つかりませんでした。別の画像をお試しください。',
    poiNotFoundQr: 'このQRコードに対応する観光スポットが見つかりません。',
    qrLookupError: 'QRコードの検索中にエラーが発生しました。',

    playerBadge: '音声ガイド＆多言語字幕',
    subtitleHeading: '音声ガイド字幕',
    audioPlaying: '音声を再生中...',
    audioPaused: '一時停止中',
    speedLabel: '再生速度',
    audioPreparingFallback: 'この言語の標準音声データは現在準備中です。',
    readSubtitleNote: '上の枠にある詳細な翻訳字幕をご覧いただけます。',
    closePlayerBtn: '音声ガイドを閉じる',
    noNarrationAvailable: 'まだ解説はありません',
    multilingualNarrationSub: '多言語自動解説',
    fallbackLanguageNotice: (target, actual) =>
      `(${target.toUpperCase()}) の音声は未作成のため、利用可能な (${actual.toUpperCase()}) 版を再生しています。`,
  },

  ko: {
    brandTitle: '다국어 자동 오디오 가이드 시스템',
    brandSubtitle: '스마트한 문화유산 및 관광 명소 탐방',
    scanQrBtn: 'QR 코드 스캔',
    scanQrTitle: '카메라로 QR 코드 스캔',

    searchPlaceholder: '관광 명소, 유적지, 랜드마크 검색...',
    locatingGps: 'GPS 위치 확인 중...',
    locatingRequest: 'GPS 권한 요청 중...',
    locatedSuccess: '위치 확인 완료',
    locatedCenterFallback: '도심 중심 (기본값)',
    recenterTitle: '내 현재 위치로 지도 이동',
    mapModeStreet: '일반 지도',
    mapModeSatellite: '위성 지도',
    mapModeToggleTitle: '지도 표시 모드 변경',

    legendYourLocation: '현재 위치',
    legendAttractions: '관광 명소',
    mapPopupGps: '현재 GPS 위치',
    mapPopupViewAndListen: 'QR 보기 및 오디오 듣기',

    nearbyHeading: '주변 추천 관광 명소',
    locationsCount: '개 명소',
    noPoiFound: '검색어와 일치하는 관광 명소가 없습니다.',
    noDescription: '이 장소에 대한 상세 설명이 아직 등록되지 않았습니다.',
    viewQrAndNarration: 'QR 코드 & 오디오 가이드 보기',
    awayLabel: '거리',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `${Math.round(meters)} m 거리` : `${(meters / 1000).toFixed(1)} km 거리`;
    },

    qrModalBadge: '관광 명소 QR 코드',
    distanceFromYou: '현재 위치로부터의 거리',
    qrGenerating: '선명한 QR 코드 생성 중...',
    qrGuideDesc: '유적지 안내판의 QR 코드를 스캔하거나 바로 듣기를 눌러 오디오 가이드를 시작하세요.',
    listenNowBtn: '이 기기에서 오디오 가이드 바로 듣기',
    loadingNarration: '오디오 가이드 로딩 중...',
    openCameraOther: '카메라로 다른 장소 스캔하기',

    scanModalTitle: '관광 명소에서 QR 코드 스캔',
    scanModalSubtitle: '유적지 안내판의 QR 코드를 카메라로 비춰주세요',
    cameraErrorDefault: '카메라를 시작할 수 없습니다. 카메라 권한을 허용하거나 QR 이미지 파일을 업로드하세요.',
    chooseImageFile: '기기에서 QR 이미지 업로드',
    quickDemoTitle: '또는 샘플 장소를 선택하여 체험:',
    manualInputPlaceholder: 'QR 코드 문자열 수동 입력...',
    confirmBtn: '확인',
    invalidQrImage: '이 이미지에서 유효한 QR 코드를 찾을 수 없습니다. 다른 이미지를 시도하세요!',
    poiNotFoundQr: '이 QR 코드에 해당하는 관광 명소를 찾을 수 없습니다.',
    qrLookupError: 'QR 코드 조회 중 오류가 발생했습니다.',

    playerBadge: '오디오 가이드 & 자막',
    subtitleHeading: '가이드 자막',
    audioPlaying: '오디오 재생 중...',
    audioPaused: '일시 정지됨',
    speedLabel: '재생 속도',
    audioPreparingFallback: '이 언어의 표준 음성 녹음이 준비 중입니다.',
    readSubtitleNote: '위의 상자에서 번역된 상세 자막을 확인하실 수 있습니다.',
    closePlayerBtn: '오디오 가이드 닫기',
    noNarrationAvailable: '아직 해설이 없습니다',
    multilingualNarrationSub: '다국어 자동 해설',
    fallbackLanguageNotice: (target, actual) =>
      `(${target.toUpperCase()}) 오디오가 없어 사용 가능한 (${actual.toUpperCase()}) 버전으로 재생합니다.`,
  },

  zh: {
    brandTitle: '多语言自动语音导览系统',
    brandSubtitle: '探索智能历史文化遗产与旅游景点',
    scanQrBtn: '扫描二维码',
    scanQrTitle: '开启相机扫描二维码',

    searchPlaceholder: '搜索景点、名胜古迹、地标...',
    locatingGps: '正在进行GPS定位...',
    locatingRequest: '正在请求GPS权限...',
    locatedSuccess: '定位成功',
    locatedCenterFallback: '市中心（默认位置）',
    recenterTitle: '定位并居中到我的位置',
    mapModeStreet: '街道地图',
    mapModeSatellite: '卫星地图',
    mapModeToggleTitle: '切换地图显示模式',

    legendYourLocation: '您的当前位置',
    legendAttractions: '景点',
    mapPopupGps: '您的GPS位置',
    mapPopupViewAndListen: '查看二维码并收听讲解',

    nearbyHeading: '周边推荐景点',
    locationsCount: '个地点',
    noPoiFound: '未找到符合搜索条件的景点。',
    noDescription: '该景点暂无详细描述信息。',
    viewQrAndNarration: '查看二维码与语音导览',
    awayLabel: '距离',

    distanceFormat: (meters) => {
      if (meters == null) return '';
      return meters < 1000 ? `距离 ${Math.round(meters)} 米` : `距离 ${(meters / 1000).toFixed(1)} 公里`;
    },

    qrModalBadge: '景点二维码',
    distanceFromYou: '距离您',
    qrGenerating: '正在生成高清二维码...',
    qrGuideDesc: '在古迹现场标牌处扫描此二维码，或直接点击收听自动语音讲解。',
    listenNowBtn: '在此设备上立即收听讲解',
    loadingNarration: '正在加载讲解内容...',
    openCameraOther: '开启相机扫描其他景点',

    scanModalTitle: '扫描景点现场二维码',
    scanModalSubtitle: '将相机对准古迹介绍牌上的二维码',
    cameraErrorDefault: '无法启动相机。请授予相机权限或从设备上传二维码图片。',
    chooseImageFile: '从设备上传二维码图片',
    quickDemoTitle: '或快速选择示例景点进行试听：',
    manualInputPlaceholder: '手动输入二维码文本...',
    confirmBtn: '确认',
    invalidQrImage: '此图片中未找到有效的二维码，请尝试其他图片！',
    poiNotFoundQr: '未找到与此二维码对应的景点。',
    qrLookupError: '查询二维码时出错。',

    playerBadge: '语音讲解与多语字幕',
    subtitleHeading: '讲解字幕',
    audioPlaying: '正在播放音频...',
    audioPaused: '已暂停',
    speedLabel: '播放速度',
    audioPreparingFallback: '该语言的标准语音导览正在准备中。',
    readSubtitleNote: '您可以在上方查看详细翻译字幕。',
    closePlayerBtn: '关闭导览窗口',
    noNarrationAvailable: '暂无讲解',
    multilingualNarrationSub: '多语言自动讲解',
    fallbackLanguageNotice: (target, actual) =>
      `暂无 (${target.toUpperCase()}) 音频，已为您播放可用的 (${actual.toUpperCase()}) 版本。`,
  },
};

export function getTranslation(lang: string): Translations {
  const code = (lang || 'vi').toLowerCase() as SupportedLang;
  return I18N_DICTIONARY[code] || I18N_DICTIONARY.vi;
}
