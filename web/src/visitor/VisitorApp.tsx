import React, { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import {
  VisitorPoi,
  NarrationDetail,
  SUPPORTED_LANGUAGES,
} from './types';
import { getTranslation, SupportedLang } from './i18n';

declare const L: any;
declare const Html5Qrcode: any;

export const VisitorApp: React.FC = () => {
  // Global Language State (synced with localStorage)
  const [selectedLang, setSelectedLang] = useState<string>(() => {
    return localStorage.getItem('visitor_language') || 'vi';
  });

  // Active translation dictionary
  const t = getTranslation(selectedLang);

  // State
  const [pois, setPois] = useState<VisitorPoi[]>([]);
  const [filteredPois, setFilteredPois] = useState<VisitorPoi[]>([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [userLocation, setUserLocation] = useState<{ lat: number; lng: number } | null>(null);
  const [gpsStatusKey, setGpsStatusKey] = useState<'requesting' | 'located' | 'center_default' | 'denied'>('requesting');
  const [mapMode, setMapMode] = useState<'streets' | 'satellite'>('streets');

  // Selected POI for QR display modal
  const [qrModalPoi, setQrModalPoi] = useState<VisitorPoi | null>(null);
  const [qrCodeDataUrl, setQrCodeDataUrl] = useState<string>('');

  // QR Scanner modal
  const [showScanner, setShowScanner] = useState<boolean>(false);
  const [scannerError, setScannerError] = useState<string>('');
  const [scannerInput, setScannerInput] = useState<string>('');
  const [cameraActive, setCameraActive] = useState<boolean>(false);

  // Narration player modal
  const [activeNarration, setActiveNarration] = useState<NarrationDetail | null>(null);
  const [narrationNotice, setNarrationNotice] = useState<string>('');
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackSpeed, setPlaybackSpeed] = useState<number>(1.0);
  const [loadingNarration, setLoadingNarration] = useState<boolean>(false);

  // Map & Media refs
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapInstanceRef = useRef<any>(null);
  const tileLayerRef = useRef<any>(null);
  const userMarkerRef = useRef<any>(null);
  const poiMarkersRef = useRef<any[]>([]);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const html5QrCodeRef = useRef<any>(null);

  // 1. Silent Background Presence Heartbeat (No visible badge on visitor UI)
  useEffect(() => {
    // Strictly do not run visitor heartbeat if user is on admin routes
    if (
      typeof window !== 'undefined' &&
      (window.location.pathname.startsWith('/admin') ||
        window.location.pathname.includes('admin.html'))
    ) {
      return;
    }

    let visitorId = sessionStorage.getItem('visitor_id');
    if (!visitorId) {
      visitorId = 'visitor_' + Math.random().toString(36).substring(2, 10);
      sessionStorage.setItem('visitor_id', visitorId);
    }

    const sendHeartbeat = async (isLeaving = false) => {
      try {
        const payload = JSON.stringify({
          visitorId,
          page: 'index.html',
          lang: selectedLang,
          leave: isLeaving,
        });

        if (isLeaving && typeof navigator !== 'undefined' && navigator.sendBeacon) {
          navigator.sendBeacon('/api/v1/client/presence/heartbeat', payload);
        } else {
          await fetch('/api/v1/client/presence/heartbeat', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: payload,
            keepalive: isLeaving,
          });
        }
      } catch (err) {
        // Silent background heartbeat
      }
    };

    sendHeartbeat(false);
    const interval = setInterval(() => sendHeartbeat(false), 20000);

    const handleBeforeUnload = () => {
      sendHeartbeat(true);
    };
    window.addEventListener('beforeunload', handleBeforeUnload);

    return () => {
      clearInterval(interval);
      window.removeEventListener('beforeunload', handleBeforeUnload);
      sendHeartbeat(true);
    };
  }, [selectedLang]);

  // 2. Fetch POIs, GPS location & Check deep-link ?qr= on mount
  useEffect(() => {
    fetchPois();
    requestUserLocation();

    // Support deep-link ?qr= parameter (UC13, UC14)
    if (typeof window !== 'undefined') {
      const params = new URLSearchParams(window.location.search);
      const deepLinkQr = params.get('qr');
      if (deepLinkQr) {
        handleScanSubmit(deepLinkQr);
      }
    }
  }, []);

  const fetchPois = async () => {
    try {
      const res = await fetch('/api/v1/client/pois?pageSize=100');
      if (res.ok) {
        const json = await res.json();
        const items = json.data?.items || [];
        setPois(items);
        setFilteredPois(items);
      }
    } catch (err) {
      console.error('Failed to load POIs:', err);
    }
  };

  const requestUserLocation = () => {
    if ('geolocation' in navigator) {
      setGpsStatusKey('requesting');
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const loc = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          setUserLocation(loc);
          setGpsStatusKey('located');
          updateNearbyDistances(loc.lat, loc.lng);
        },
        (err) => {
          console.warn('Geolocation denied or unavailable:', err);
          const fallback = { lat: 10.7769, lng: 106.7009 }; // TP.HCM Center
          setUserLocation(fallback);
          setGpsStatusKey('center_default');
          updateNearbyDistances(fallback.lat, fallback.lng);
        },
        { enableHighAccuracy: true, timeout: 8000 }
      );
    } else {
      const fallback = { lat: 10.7769, lng: 106.7009 };
      setUserLocation(fallback);
      setGpsStatusKey('center_default');
      updateNearbyDistances(fallback.lat, fallback.lng);
    }
  };

  const updateNearbyDistances = async (lat: number, lng: number) => {
    try {
      const res = await fetch(`/api/v1/client/pois/nearby?lat=${lat}&lng=${lng}&radius=20000`);
      if (res.ok) {
        const json = await res.json();
        const items = json.data?.items || [];
        if (items.length > 0) {
          setPois(items);
          setFilteredPois(items);
        }
      }
    } catch (err) {
      console.error('Failed to get nearby POIs:', err);
    }
  };

  // 3. Search filter
  useEffect(() => {
    if (!searchQuery.trim()) {
      setFilteredPois(pois);
    } else {
      const q = searchQuery.toLowerCase();
      const matched = pois.filter(
        (p) =>
          (p.poiName && p.poiName.toLowerCase().includes(q)) ||
          (p.name && p.name.toLowerCase().includes(q)) ||
          (p.description && p.description.toLowerCase().includes(q))
      );
      setFilteredPois(matched);
    }
  }, [searchQuery, pois]);

  // 4. Initialize & Update Google Maps via Leaflet
  useEffect(() => {
    if (!mapContainerRef.current || typeof L === 'undefined') return;

    if (!mapInstanceRef.current) {
      const initialLat = userLocation?.lat || 10.7769;
      const initialLng = userLocation?.lng || 106.7009;

      const map = L.map(mapContainerRef.current, {
        center: [initialLat, initialLng],
        zoom: 14,
        zoomControl: true,
      });

      mapInstanceRef.current = map;

      // Google Maps Tile Layer
      const streetTileUrl = 'https://mt{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}';
      tileLayerRef.current = L.tileLayer(streetTileUrl, {
        subdomains: ['0', '1', '2', '3'],
        maxZoom: 20,
        attribution: '&copy; Google Maps',
      }).addTo(map);

      setTimeout(() => {
        map.invalidateSize();
      }, 250);
    }

    const map = mapInstanceRef.current;

    // Switch between Google Streets and Satellite
    if (tileLayerRef.current) {
      map.removeLayer(tileLayerRef.current);
      const lyrsCode = mapMode === 'satellite' ? 'y' : 'm';
      const tileUrl = `https://mt{s}.google.com/vt/lyrs=${lyrsCode}&x={x}&y={y}&z={z}`;
      const newLayer = L.tileLayer(tileUrl, {
        subdomains: ['0', '1', '2', '3'],
        maxZoom: 20,
        attribution: '&copy; Google Maps',
      }).addTo(map);
      tileLayerRef.current = newLayer;
    }

    // Update User Marker (Blue Dot with Pulse effect)
    if (userLocation) {
      if (userMarkerRef.current) {
        map.removeLayer(userMarkerRef.current);
      }
      const userIcon = L.divIcon({
        className: 'user-pulse-container',
        html: `
          <div class="user-pulse-wave"></div>
          <div class="user-blue-dot"></div>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });
      userMarkerRef.current = L.marker([userLocation.lat, userLocation.lng], { icon: userIcon })
        .addTo(map)
        .bindPopup(
          `<div class="map-popup-bubble">
            <div class="popup-badge">GPS</div>
            <strong style="color:#0f172a; font-size:13px;">${t.legendYourLocation}</strong>
            <p style="margin:2px 0 0 0; color:#64748b; font-size:11px;">${userLocation.lat.toFixed(5)}, ${userLocation.lng.toFixed(5)}</p>
          </div>`
        );
    }

    // Clear old POI markers
    poiMarkersRef.current.forEach((m) => map.removeLayer(m));
    poiMarkersRef.current = [];

    // Add Modern Pin Markers for POIs
    filteredPois.forEach((poi) => {
      if (poi.latitude && poi.longitude) {
        const poiIcon = L.divIcon({
          className: 'poi-custom-pin',
          html: `
            <div class="poi-pin-wrapper">
              <div class="poi-pin-circle">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#ffffff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path>
                  <circle cx="12" cy="10" r="3"></circle>
                </svg>
              </div>
              <div class="poi-pin-stem"></div>
            </div>
          `,
          iconSize: [32, 40],
          iconAnchor: [16, 38],
        });

        const distText = poi.distanceMeters != null ? t.distanceFormat(poi.distanceMeters) : poi.distanceText;

        const marker = L.marker([poi.latitude, poi.longitude], { icon: poiIcon })
          .addTo(map)
          .bindPopup(
            `<div class="map-popup-card">
              <div class="popup-title">${poi.poiName || poi.name}</div>
              <div class="popup-desc">${poi.description || t.noDescription}</div>
              ${distText ? `<div class="popup-distance">${distText}</div>` : ''}
              <button id="btn-poi-${poi.poiId}" class="popup-btn-action">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
                  <polygon points="5 3 19 12 5 21 5 3"></polygon>
                </svg>
                ${t.mapPopupViewAndListen}
              </button>
            </div>`,
            { className: 'modern-leaflet-popup' }
          );

        marker.on('popupopen', () => {
          const btn = document.getElementById(`btn-poi-${poi.poiId}`);
          if (btn) {
            btn.onclick = () => openPoiQrModal(poi);
          }
        });

        poiMarkersRef.current.push(marker);
      }
    });

    // Invalidate size
    setTimeout(() => {
      map.invalidateSize();
    }, 200);
  }, [userLocation, filteredPois, mapMode, selectedLang]);

  // Recenter map on user location
  const handleRecenter = () => {
    requestUserLocation();
    if (mapInstanceRef.current && userLocation) {
      mapInstanceRef.current.setView([userLocation.lat, userLocation.lng], 16, { animate: true });
    }
  };

  // Open POI QR Modal with full deep-link URL (UC13, UC14)
  const openPoiQrModal = async (poi: VisitorPoi) => {
    setQrModalPoi(poi);
    const code = poi.qrCode || poi.poiId;
    const origin = typeof window !== 'undefined' ? window.location.origin : '';
    const fullQrUrl = `${origin}/?qr=${encodeURIComponent(code)}`;
    try {
      const url = await QRCode.toDataURL(fullQrUrl, {
        width: 280,
        margin: 2,
        color: { dark: '#0f172a', light: '#ffffff' },
      });
      setQrCodeDataUrl(url);
    } catch (err) {
      console.error('Failed to generate QR code data URL:', err);
    }
  };

  // Live Camera Scanner management
  useEffect(() => {
    if (showScanner) {
      setScannerError('');
      setCameraActive(false);

      const timer = setTimeout(() => {
        try {
          if (typeof Html5Qrcode !== 'undefined') {
            const html5QrCode = new Html5Qrcode('qr-reader');
            html5QrCodeRef.current = html5QrCode;

            html5QrCode
              .start(
                { facingMode: 'environment' },
                {
                  fps: 10,
                  qrbox: { width: 240, height: 240 },
                },
                (decodedText: string) => {
                  handleScanSubmit(decodedText);
                  stopScannerQuietly();
                },
                () => {}
              )
              .then(() => {
                setCameraActive(true);
              })
              .catch((err: any) => {
                console.warn('Camera permission denied or camera not found:', err);
                setScannerError(t.cameraErrorDefault);
                setCameraActive(false);
              });
          } else {
            setScannerError(t.cameraErrorDefault);
          }
        } catch (e: any) {
          setScannerError(t.cameraErrorDefault);
        }
      }, 300);

      return () => {
        clearTimeout(timer);
        stopScannerQuietly();
      };
    } else {
      stopScannerQuietly();
    }
  }, [showScanner, selectedLang]);

  const stopScannerQuietly = () => {
    if (html5QrCodeRef.current) {
      try {
        if (html5QrCodeRef.current.isScanning) {
          html5QrCodeRef.current.stop().catch(() => {});
        }
      } catch (e) {}
      html5QrCodeRef.current = null;
    }
  };

  // Scan file input handler
  const handleFileScan = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (typeof Html5Qrcode !== 'undefined') {
      try {
        const scanner = html5QrCodeRef.current || new Html5Qrcode('qr-reader');
        const result = await scanner.scanFile(file, true);
        handleScanSubmit(result);
        stopScannerQuietly();
      } catch (err) {
        alert(t.invalidQrImage);
      }
    }
  };

  // Trigger Narration Player with Auto-Language Sync & Strict Language Integrity (UC13, UC14)
  const loadNarration = async (poiId: string, langCode: string) => {
    setLoadingNarration(true);
    setNarrationNotice('');
    const langTrans = getTranslation(langCode);
    const current = pois.find((p) => p.poiId === poiId);
    const poiName = current?.poiName || current?.name || qrModalPoi?.poiName || 'Điểm tham quan';

    try {
      // 1. Try to fetch published narration in the requested language
      const res = await fetch(`/api/v1/client/pois/${poiId}/narrations/${langCode}`);
      if (res.ok) {
        const json = await res.json();
        const data = json.data;
        // Verify genuine published narration for this specific language
        const isLegitForLang =
          data &&
          (data.lang === langCode || !data.lang) &&
          (data.subtitle || data.textContent) &&
          (langCode === 'vi' || data.subtitle !== current?.description);

        if (isLegitForLang) {
          setActiveNarration({
            poiId,
            poiName: data.poiName || poiName,
            contentTitle: langTrans.multilingualNarrationSub,
            lang: langCode,
            subtitle: data.subtitle || data.textContent,
            audioUrl: data.audioUrl || '',
          });
          setQrModalPoi(null);
          setShowScanner(false);
          return;
        }
      }

      // 2. If NOT PUBLISHED for this language:
      // STRICT REQUIREMENT: Absolutely NEVER copy raw Vietnamese text as fallback!
      // Must display translated "Chưa có thuyết minh" according to the selected language:
      // vi: "Chưa có thuyết minh"
      // en: "No narration available"
      // fr: "Aucune narration disponible"
      // ja: "まだ解説はありません"
      // ko: "아직 해설이 없습니다"
      // zh: "暂无讲解"
      setActiveNarration({
        poiId,
        poiName,
        contentTitle: langTrans.multilingualNarrationSub,
        lang: langCode,
        subtitle: langTrans.noNarrationAvailable,
        audioUrl: '',
      });
      setQrModalPoi(null);
      setShowScanner(false);
    } catch (err) {
      console.error('Failed to load narration:', err);
      setActiveNarration({
        poiId,
        poiName,
        contentTitle: langTrans.multilingualNarrationSub,
        lang: langCode,
        subtitle: langTrans.noNarrationAvailable,
        audioUrl: '',
      });
      setQrModalPoi(null);
      setShowScanner(false);
    } finally {
      setLoadingNarration(false);
    }
  };

  // Scan QR code submission (from Camera, Deep-link, or Manual input)
  const handleScanSubmit = async (code: string) => {
    if (!code || !code.trim()) return;
    let actualCode = code.trim();

    // Extract actual code if a full URL was scanned or opened via deep-link
    try {
      if (actualCode.includes('?qr=')) {
        const parsed = new URL(actualCode, window.location.origin);
        const qVal = parsed.searchParams.get('qr');
        if (qVal) actualCode = qVal;
      } else if (actualCode.includes('/listen/')) {
        actualCode = actualCode.split('/listen/')[1].split(/[?#]/)[0];
      }
    } catch (_) {}

    try {
      const res = await fetch(`/api/v1/client/qr/${encodeURIComponent(actualCode)}`);
      if (res.ok) {
        const json = await res.json();
        const poiId = json.data?.poiId;
        if (poiId) {
          loadNarration(poiId, selectedLang);
          return;
        }
      }
      alert(t.poiNotFoundQr);
    } catch (err) {
      alert(t.qrLookupError);
    }
  };

  // Global language switch handler
  const handleLanguageChange = (nextLang: string) => {
    setSelectedLang(nextLang);
    localStorage.setItem('visitor_language', nextLang);
    if (activeNarration) {
      loadNarration(activeNarration.poiId, nextLang);
    }
  };

  // Status label
  const locationStatusLabel =
    gpsStatusKey === 'located'
      ? t.locatedSuccess
      : gpsStatusKey === 'requesting'
      ? t.locatingRequest
      : gpsStatusKey === 'denied'
      ? t.locatingGps
      : t.locatedCenterFallback;

  return (
    <div className="visitor-app-container">
      {/* 1. Header Navigation - WITHOUT Admin Button */}
      <header className="visitor-header">
        <div className="header-brand">
          <div className="brand-logo-badge">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#ffffff" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 18v-6a9 9 0 0 1 18 0v6"></path>
              <path d="M21 19a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3zM3 19a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-3a2 2 0 0 0-2-2H3z"></path>
            </svg>
          </div>
          <div className="brand-titles">
            <h1 className="brand-title">{t.brandTitle}</h1>
            <p className="brand-subtitle">{t.brandSubtitle}</p>
          </div>
        </div>

        <div className="header-actions">
          {/* Language Selector (Active i18n switcher) */}
          <div className="language-selector-box">
            <select
              value={selectedLang}
              onChange={(e) => handleLanguageChange(e.target.value)}
              className="lang-select-input"
              aria-label="Chọn ngôn ngữ"
            >
              {SUPPORTED_LANGUAGES.map((l) => (
                <option key={l.code} value={l.code}>
                  {l.flag} {l.name}
                </option>
              ))}
            </select>
          </div>

          {/* QR Camera Scanner Trigger */}
          <button
            onClick={() => setShowScanner(true)}
            className="btn-header-scan"
            title={t.scanQrTitle}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"></path>
              <circle cx="12" cy="13" r="4"></circle>
            </svg>
            <span>{t.scanQrBtn}</span>
          </button>
        </div>
      </header>

      {/* 2. Uniform 44px (h-11) Toolbar: Search + Recenter GPS + Map Mode */}
      <div className="visitor-toolbar-container">
        <div className="toolbar-row">
          {/* Search Box */}
          <div className="toolbar-search-wrapper">
            <svg className="search-svg-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="8"></circle>
              <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
            </svg>
            <input
              type="text"
              placeholder={t.searchPlaceholder}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="toolbar-search-input"
            />
            {searchQuery && (
              <button onClick={() => setSearchQuery('')} className="btn-search-clear">
                ✕
              </button>
            )}
          </div>

          {/* GPS Recenter Action */}
          <button
            onClick={handleRecenter}
            className="btn-toolbar-action"
            title={t.recenterTitle}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="22" y1="12" x2="18" y2="12"></line>
              <line x1="6" y1="12" x2="2" y2="12"></line>
              <line x1="12" y1="6" x2="12" y2="2"></line>
              <line x1="12" y1="22" x2="12" y2="18"></line>
            </svg>
            <span className="location-label">{locationStatusLabel}</span>
          </button>

          {/* Google Maps Layer Switcher */}
          <button
            onClick={() => setMapMode(mapMode === 'streets' ? 'satellite' : 'streets')}
            className="btn-toolbar-action btn-map-toggle"
            title={t.mapModeToggleTitle}
          >
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <polygon points="12 2 2 7 12 12 22 7 12 2"></polygon>
              <polyline points="2 17 12 22 22 17"></polyline>
              <polyline points="2 12 12 17 22 12"></polyline>
            </svg>
            <span>{mapMode === 'streets' ? t.mapModeSatellite : t.mapModeStreet}</span>
          </button>
        </div>
      </div>

      {/* 3. Main Split View Grid: Google Maps + Nearby Suggestions */}
      <div className="visitor-content-split">
        {/* Left: Google Maps Interactive View */}
        <div className="map-card-wrapper">
          <div ref={mapContainerRef} className="google-leaflet-map" />
          <div className="map-floating-legend">
            <span className="legend-item"><span className="legend-dot blue"></span> {t.legendYourLocation}</span>
            <span className="legend-item"><span className="legend-dot red"></span> {t.legendAttractions} ({filteredPois.length})</span>
          </div>
        </div>

        {/* Right: Nearby POIs List */}
        <div className="nearby-card-panel">
          <div className="nearby-panel-header">
            <div className="nearby-title-wrap">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#0284c7" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="10"></circle>
                <polygon points="16.24 7.76 14.12 14.12 7.76 16.24 9.88 9.88 16.24 7.76"></polygon>
              </svg>
              <h2 className="nearby-heading">{t.nearbyHeading}</h2>
            </div>
            <span className="nearby-count-tag">{filteredPois.length} {t.locationsCount}</span>
          </div>

          <div className="poi-cards-scroll">
            {filteredPois.length === 0 ? (
              <div className="empty-search-state">
                <p>{t.noPoiFound}</p>
              </div>
            ) : (
              filteredPois.map((poi) => {
                const distText = poi.distanceMeters != null ? t.distanceFormat(poi.distanceMeters) : poi.distanceText;
                return (
                  <div
                    key={poi.poiId}
                    className="poi-item-card"
                    onClick={() => openPoiQrModal(poi)}
                  >
                    <div className="poi-item-header">
                      <h3 className="poi-item-name">{poi.poiName || poi.name}</h3>
                      {distText && (
                        <span className="poi-dist-badge">
                          {distText}
                        </span>
                      )}
                    </div>
                    <p className="poi-item-desc">
                      {poi.description || t.noDescription}
                    </p>
                    <div className="poi-item-footer">
                      <span className="poi-click-cta">
                        {t.viewQrAndNarration}
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
                          <line x1="5" y1="12" x2="19" y2="12"></line>
                          <polyline points="12 5 19 12 12 19"></polyline>
                        </svg>
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>
      </div>

      {/* 4. MODAL: POI QR CODE DISPLAY (UC13) */}
      {qrModalPoi && (
        <div className="modal-backdrop-blur" onClick={() => setQrModalPoi(null)}>
          <div className="modal-dialog-card qr-modal-box" onClick={(e) => e.stopPropagation()}>
            <button className="btn-modal-close" onClick={() => setQrModalPoi(null)}>
              ✕
            </button>
            <div className="modal-box-header">
              <span className="modal-badge-tag">{t.qrModalBadge}</span>
              <h2 className="modal-poi-title">{qrModalPoi.poiName || qrModalPoi.name}</h2>
              {(qrModalPoi.distanceMeters != null || qrModalPoi.distanceText) && (
                <p className="modal-dist-text">
                  {t.distanceFromYou}: <strong>{qrModalPoi.distanceMeters != null ? t.distanceFormat(qrModalPoi.distanceMeters) : qrModalPoi.distanceText}</strong>
                </p>
              )}
            </div>

            <div className="qr-preview-frame">
              {qrCodeDataUrl ? (
                <img src={qrCodeDataUrl} alt="QR Code" className="qr-image-element" />
              ) : (
                <div className="qr-loading-text">{t.qrGenerating}</div>
              )}
            </div>

            <p className="qr-guide-desc">
              {t.qrGuideDesc}
            </p>

            <div className="modal-button-group">
              <button
                className="btn-primary-action"
                onClick={() => loadNarration(qrModalPoi.poiId, selectedLang)}
                disabled={loadingNarration}
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                  <polygon points="5 3 19 12 5 21 5 3"></polygon>
                </svg>
                <span>{loadingNarration ? t.loadingNarration : t.listenNowBtn}</span>
              </button>
              <button
                className="btn-secondary-action"
                onClick={() => {
                  setQrModalPoi(null);
                  setShowScanner(true);
                }}
              >
                {t.openCameraOther}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 5. MODAL: LIVE CAMERA QR SCANNER (UC14) */}
      {showScanner && (
        <div className="modal-backdrop-blur" onClick={() => setShowScanner(false)}>
          <div className="modal-dialog-card scanner-modal-box" onClick={(e) => e.stopPropagation()}>
            <button className="btn-modal-close" onClick={() => setShowScanner(false)}>
              ✕
            </button>

            <div className="scanner-header-area">
              <h2 className="scanner-main-title">{t.scanModalTitle}</h2>
              <p className="scanner-sub-text">{t.scanModalSubtitle}</p>
            </div>

            {/* Live Camera Viewfinder Element */}
            <div className="camera-viewfinder-wrapper">
              <div id="qr-reader" className="html5-qr-reader-container"></div>
              {cameraActive && <div className="scanning-laser-line"></div>}
            </div>

            {scannerError && (
              <div className="scanner-error-callout">
                <p>{scannerError}</p>
                <label className="btn-upload-qr-file">
                  <span>{t.chooseImageFile}</span>
                  <input type="file" accept="image/*" onChange={handleFileScan} style={{ display: 'none' }} />
                </label>
              </div>
            )}

            {/* Quick Demo QR Testing buttons */}
            <div className="scanner-demo-strip">
              <span className="demo-label">{t.quickDemoTitle}</span>
              <div className="demo-buttons-flex">
                {pois.slice(0, 3).map((p) => (
                  <button
                    key={p.poiId}
                    className="btn-demo-poi"
                    onClick={() => {
                      if (p.qrCode) handleScanSubmit(p.qrCode);
                      else loadNarration(p.poiId, selectedLang);
                    }}
                  >
                    {p.poiName || p.name}
                  </button>
                ))}
              </div>
            </div>

            {/* Manual QR Code fallback */}
            <div className="manual-input-strip">
              <input
                type="text"
                placeholder={t.manualInputPlaceholder}
                value={scannerInput}
                onChange={(e) => setScannerInput(e.target.value)}
                className="input-manual-qr"
              />
              <button
                onClick={() => handleScanSubmit(scannerInput)}
                className="btn-manual-submit"
              >
                {t.confirmBtn}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 6. MODAL: MULTILINGUAL NARRATION & MP3 PLAYER (UC14) */}
      {activeNarration && (
        <div className="modal-backdrop-blur" onClick={() => setActiveNarration(null)}>
          <div className="modal-dialog-card narration-modal-box" onClick={(e) => e.stopPropagation()}>
            <button className="btn-modal-close" onClick={() => setActiveNarration(null)}>
              ✕
            </button>

            <div className="player-brand-header">
              <span className="modal-badge-tag">{t.playerBadge}</span>
              <h2 className="player-poi-title">{activeNarration.poiName}</h2>
              <p className="player-poi-sub">{activeNarration.contentTitle}</p>
            </div>

            {/* Supported Language Tabs with Immediate Narration & UI Sync */}
            <div className="player-languages-bar">
              {SUPPORTED_LANGUAGES.map((l) => (
                <button
                  key={l.code}
                  className={`btn-player-lang ${activeNarration.lang === l.code ? 'active' : ''}`}
                  onClick={() => {
                    handleLanguageChange(l.code);
                    loadNarration(activeNarration.poiId, l.code);
                  }}
                >
                  {l.flag} {l.name}
                </button>
              ))}
            </div>

            {/* Fallback Notice Banner if requested lang doesn't have a recording */}
            {narrationNotice && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  backgroundColor: '#fef3c7',
                  border: '1px solid #fde68a',
                  color: '#92400e',
                  borderRadius: '8px',
                  padding: '8px 12px',
                  margin: '10px 16px',
                  fontSize: '12px',
                  fontWeight: 500,
                }}
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#d97706" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="12" r="10"></circle>
                  <line x1="12" y1="8" x2="12" y2="12"></line>
                  <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>{narrationNotice}</span>
              </div>
            )}

            {/* Subtitle Box */}
            <div className="subtitle-display-panel">
              <div className="subtitle-panel-title">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                  <polyline points="10 9 9 9 8 9"></polyline>
                </svg>
                <span>{t.subtitleHeading} ({activeNarration.lang.toUpperCase()}):</span>
              </div>
              <div className="subtitle-scroll-content">
                {activeNarration.subtitle}
              </div>
            </div>

            {/* MP3 Player Section */}
            <div className="mp3-player-panel">
              <div className="mp3-panel-meta">
                <span className="mp3-status-indicator">
                  <span className={`status-audio-dot ${isPlaying ? 'playing' : ''}`}></span>
                  {isPlaying ? t.audioPlaying : t.audioPaused}
                </span>
                <div className="speed-adjust-group">
                  <span className="speed-title">{t.speedLabel}:</span>
                  {[0.75, 1.0, 1.25, 1.5].map((s) => (
                    <button
                      key={s}
                      className={`btn-speed-pill ${playbackSpeed === s ? 'active' : ''}`}
                      onClick={() => {
                        setPlaybackSpeed(s);
                        if (audioRef.current) audioRef.current.playbackRate = s;
                      }}
                    >
                      {s}x
                    </button>
                  ))}
                </div>
              </div>

              {activeNarration.audioUrl ? (
                <div className="native-audio-container">
                  <audio
                    ref={audioRef}
                    src={activeNarration.audioUrl}
                    controls
                    autoPlay
                    onPlay={() => setIsPlaying(true)}
                    onPause={() => setIsPlaying(false)}
                    className="styled-audio-element"
                  />
                </div>
              ) : (
                <div className="mp3-empty-fallback">
                  <p className="fallback-text">{t.audioPreparingFallback}</p>
                  <p className="fallback-sub">{t.readSubtitleNote}</p>
                </div>
              )}
            </div>

            <div className="player-modal-footer">
              <button
                className="btn-dismiss-player"
                onClick={() => setActiveNarration(null)}
              >
                {t.closePlayerBtn}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
