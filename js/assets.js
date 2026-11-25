/**
 * HUNTER OF THE SEA - ASSET MANAGER & TRANSPARENT OFFSCREEN CANVAS PROCESSOR
 * 
 * Tự động loại bỏ khung nền trắng cho mọi sprite cá, bom, cá độc, nhân vật:
 * 1. An toàn tuyệt đối với mọi môi trường: Hoạt động trơn tru cả khi mở qua http://localhost:3000 lẫn mở file trực tiếp (file:///).
 * 2. Ưu tiên Offscreen Canvas tách nền, bảo toàn 100% mắt trắng, vây, răng nanh và chi tiết thân cá.
 * 3. Luôn luôn giữ fallback an toàn: Nếu canvas bị tainted bởi CORS/file:///, vẫn giữ nguyên ảnh nhân vật, KHÔNG BAO GIỜ biến thành hình bầu dục màu trơn.
 * 4. Cache chuẩn hóa đa khóa (hỗ trợ cả .png và .jpg, không phân biệt hoa/thường).
 */

const GameAssets = (() => {
    // Cache lưu trữ các HTMLCanvasElement đã tách nền trong suốt
    const transparentCanvasCache = new Map();
    const pendingCallbacks = new Map();
    const loadingSet = new Set();

    /**
     * Chuẩn hóa key lưu cache
     */
    function normalizeKey(key) {
        if (!key) return '';
        try {
            let str = decodeURIComponent(String(key));
            str = str.replace(/[?#].*$/, '');
            str = str.replace(/^.*[\\\/]/, '');
            return str.trim().toLowerCase();
        } catch (e) {
            return String(key).replace(/^.*[\\\/]/, '').trim().toLowerCase();
        }
    }

    /**
     * Kiểm tra pixel có phải là màu trắng / xám sáng của phông nền hay không
     */
    function isNearWhiteBackground(r, g, b, a, threshold = 205) {
        if (a < 35) return true; // Pixel trong suốt sẵn
        const avg = (r + g + b) / 3;
        if (avg >= threshold) {
            const diff = Math.max(r, g, b) - Math.min(r, g, b);
            return diff <= 35; // Nền gần trắng / xám sáng trung tính
        }
        return false;
    }

    /**
     * Tách nền trắng bằng Flood-Fill (BFS) từ 4 cạnh viền ngoài vào trong
     * @param {HTMLImageElement|HTMLCanvasElement} img 
     * @param {number} threshold Ngưỡng sáng trắng (mặc định 205)
     * @returns {HTMLCanvasElement}
     */
    function processTransparentSprite(img, threshold = 205) {
        if (!img) return null;
        const w = img.naturalWidth || img.width;
        const h = img.naturalHeight || img.height;
        if (!w || !h) return null;

        const canvas = document.createElement('canvas');
        canvas.width = w;
        canvas.height = h;
        const ctx = canvas.getContext('2d', { willReadFrequently: true });
        ctx.drawImage(img, 0, 0);

        let imgData;
        try {
            imgData = ctx.getImageData(0, 0, w, h);
        } catch (e) {
            // Khi mở qua file:/// trực tiếp, trình duyệt có thể chặn getImageData do canvas tainted
            // Trả về chính canvas đã vẽ ảnh thay vì null để đảm bảo nhân vật vẫn hiển thị đầy đủ
            return canvas;
        }

        const data = imgData.data;
        const total = w * h;

        // 0: chưa duyệt / thân cá, 1: nền ngoài cần làm trong suốt
        const isBg = new Uint8Array(total);
        const queue = new Int32Array(total);
        let qHead = 0;
        let qTail = 0;

        // 1. Thêm tất cả pixel viền ngoài (Border) vào hàng đợi nếu gần trắng
        for (let x = 0; x < w; x++) {
            // y = 0
            const pTop = x * 4;
            if (!isBg[x] && isNearWhiteBackground(data[pTop], data[pTop + 1], data[pTop + 2], data[pTop + 3], threshold)) {
                isBg[x] = 1;
                queue[qTail++] = x;
            }
            // y = h - 1
            const idxBot = (h - 1) * w + x;
            const pBot = idxBot * 4;
            if (!isBg[idxBot] && isNearWhiteBackground(data[pBot], data[pBot + 1], data[pBot + 2], data[pBot + 3], threshold)) {
                isBg[idxBot] = 1;
                queue[qTail++] = idxBot;
            }
        }

        for (let y = 0; y < h; y++) {
            // x = 0
            const idxLeft = y * w;
            if (!isBg[idxLeft]) {
                const pLeft = idxLeft * 4;
                if (isNearWhiteBackground(data[pLeft], data[pLeft + 1], data[pLeft + 2], data[pLeft + 3], threshold)) {
                    isBg[idxLeft] = 1;
                    queue[qTail++] = idxLeft;
                }
            }
            // x = w - 1
            const idxRight = y * w + (w - 1);
            if (!isBg[idxRight]) {
                const pRight = idxRight * 4;
                if (isNearWhiteBackground(data[pRight], data[pRight + 1], data[pRight + 2], data[pRight + 3], threshold)) {
                    isBg[idxRight] = 1;
                    queue[qTail++] = idxRight;
                }
            }
        }

        // 2. BFS Lan tỏa nhanh từ 4 biên vào vùng nền trắng ngoài (0 allocation trong vòng lặp)
        while (qHead < qTail) {
            const cur = queue[qHead++];
            const cx = cur % w;
            const cy = (cur / w) | 0;

            if (cx > 0) {
                const nidx = cur - 1;
                if (!isBg[nidx]) {
                    const p = nidx * 4;
                    if (isNearWhiteBackground(data[p], data[p + 1], data[p + 2], data[p + 3], threshold)) {
                        isBg[nidx] = 1;
                        queue[qTail++] = nidx;
                    }
                }
            }
            if (cx < w - 1) {
                const nidx = cur + 1;
                if (!isBg[nidx]) {
                    const p = nidx * 4;
                    if (isNearWhiteBackground(data[p], data[p + 1], data[p + 2], data[p + 3], threshold)) {
                        isBg[nidx] = 1;
                        queue[qTail++] = nidx;
                    }
                }
            }
            if (cy > 0) {
                const nidx = cur - w;
                if (!isBg[nidx]) {
                    const p = nidx * 4;
                    if (isNearWhiteBackground(data[p], data[p + 1], data[p + 2], data[p + 3], threshold)) {
                        isBg[nidx] = 1;
                        queue[qTail++] = nidx;
                    }
                }
            }
            if (cy < h - 1) {
                const nidx = cur + w;
                if (!isBg[nidx]) {
                    const p = nidx * 4;
                    if (isNearWhiteBackground(data[p], data[p + 1], data[p + 2], data[p + 3], threshold)) {
                        isBg[nidx] = 1;
                        queue[qTail++] = nidx;
                    }
                }
            }
        }

        // 3. Xử lý Alpha: Xóa phông nền ngoài (alpha = 0) & Khử răng cưa viền (Edge Feathering)
        for (let y = 0; y < h; y++) {
            for (let x = 0; x < w; x++) {
                const idx = y * w + x;
                const p = idx * 4;

                if (isBg[idx]) {
                    data[p + 3] = 0; // Xóa nền ngoài thành trong suốt hoàn toàn
                } else {
                    // Kiểm tra giáp ranh nền ngoài để làm mịn viền
                    const isBorder = (x > 0 && isBg[idx - 1]) ||
                                     (x < w - 1 && isBg[idx + 1]) ||
                                     (y > 0 && isBg[idx - w]) ||
                                     (y < h - 1 && isBg[idx + w]);

                    if (isBorder) {
                        const r = data[p];
                        const g = data[p + 1];
                        const b = data[p + 2];
                        const avg = (r + g + b) / 3;
                        if (avg > 185) {
                            const factor = Math.max(0, Math.min(1, (255 - avg) / 70));
                            data[p + 3] = Math.round(data[p + 3] * factor);
                        }
                    }
                }
            }
        }

        ctx.putImageData(imgData, 0, 0);
        return canvas;
    }

    /**
     * Tải và tạo Offscreen Canvas đã tách nền trong suốt cho sprite
     * @param {string} src Đường dẫn file ảnh
     * @param {function} [callback] Hàm gọi lại khi canvas đã sẵn sàng (canvas) => {}
     * @returns {HTMLCanvasElement|null}
     */
    function loadTransparentCanvas(src, callback) {
        if (!src) {
            if (callback) callback(null);
            return null;
        }

        const cleanKey = normalizeKey(src);
        const baseKey = cleanKey.replace(/\.(png|jpe?g)$/i, '');

        // 1. Kiểm tra cache có sẵn
        if (transparentCanvasCache.has(cleanKey)) {
            const cvs = transparentCanvasCache.get(cleanKey);
            if (callback) callback(cvs);
            return cvs;
        }
        if (transparentCanvasCache.has(baseKey)) {
            const cvs = transparentCanvasCache.get(baseKey);
            if (callback) callback(cvs);
            return cvs;
        }

        // 2. Lưu callback chờ xử lý
        if (callback) {
            if (!pendingCallbacks.has(cleanKey)) {
                pendingCallbacks.set(cleanKey, []);
            }
            pendingCallbacks.get(cleanKey).push(callback);
        }

        if (loadingSet.has(cleanKey)) {
            return null;
        }
        loadingSet.add(cleanKey);

        // 3. Tải ảnh (KHÔNG SET crossOrigin để chạy an toàn trên cả file:/// và http://)
        const img = new Image();

        let targetSrc = src;
        if (targetSrc.toLowerCase().endsWith('.jpg')) {
            targetSrc = src.replace(/\.jpg$/i, '.png');
        }
        img.src = targetSrc;

        function processAndCache(imageElement, loadedSrc) {
            try {
                const offscreenCanvas = processTransparentSprite(imageElement);
                if (offscreenCanvas) {
                    transparentCanvasCache.set(cleanKey, offscreenCanvas);
                    transparentCanvasCache.set(baseKey, offscreenCanvas);
                    transparentCanvasCache.set(normalizeKey(loadedSrc), offscreenCanvas);
                    transparentCanvasCache.set(normalizeKey(src), offscreenCanvas);

                    const cbs = pendingCallbacks.get(cleanKey);
                    if (cbs) {
                        cbs.forEach(cb => {
                            try { cb(offscreenCanvas); } catch (e) { console.error(e); }
                        });
                        pendingCallbacks.delete(cleanKey);
                    }
                    return offscreenCanvas;
                }
            } catch (err) {
                console.error('[GameAssets] Lỗi tách nền cho sprite:', src, err);
            } finally {
                loadingSet.delete(cleanKey);
            }
            return null;
        }

        img.onload = () => {
            processAndCache(img, targetSrc);
        };

        img.onerror = () => {
            // Nếu thử PNG không được thì fallback về đúng src ban đầu (.jpg)
            if (targetSrc !== src) {
                const fallbackImg = new Image();
                fallbackImg.src = src;
                fallbackImg.onload = () => {
                    processAndCache(fallbackImg, src);
                };
                fallbackImg.onerror = () => {
                    console.error('[GameAssets] ❌ Không thể tải ảnh sprite:', src);
                    loadingSet.delete(cleanKey);
                };
            } else {
                console.error('[GameAssets] ❌ Không thể tải ảnh sprite:', src);
                loadingSet.delete(cleanKey);
            }
        };

        return null;
    }

    /**
     * Lấy ngay HTMLCanvasElement đã tách nền từ cache hoặc xử lý đồng bộ nếu ảnh đã load
     * @param {string|HTMLImageElement|HTMLCanvasElement} srcOrImg 
     * @param {string} [customKey] 
     * @returns {HTMLCanvasElement|null}
     */
    function getTransparent(srcOrImg, customKey) {
        if (!srcOrImg) return null;
        if (srcOrImg instanceof HTMLCanvasElement) return srcOrImg;

        const raw = customKey || (typeof srcOrImg === 'string' ? srcOrImg : (srcOrImg.src || ''));
        const cleanKey = normalizeKey(raw);
        const baseKey = cleanKey.replace(/\.(png|jpe?g)$/i, '');

        if (cleanKey && transparentCanvasCache.has(cleanKey)) {
            return transparentCanvasCache.get(cleanKey);
        }
        if (baseKey && transparentCanvasCache.has(baseKey)) {
            return transparentCanvasCache.get(baseKey);
        }

        // Nếu truyền vào Image đã load xong, xử lý tạo Canvas đồng bộ ngay lập tức!
        if (srcOrImg instanceof HTMLImageElement && srcOrImg.complete && srcOrImg.naturalWidth > 0) {
            try {
                const cvs = processTransparentSprite(srcOrImg);
                if (cvs) {
                    if (cleanKey) transparentCanvasCache.set(cleanKey, cvs);
                    if (baseKey) transparentCanvasCache.set(baseKey, cvs);
                    return cvs;
                }
            } catch (e) {
                console.warn('[GameAssets] Không thể xử lý pixel, fallback:', e);
            }
        }

        // Nếu chưa có trong cache, kích hoạt nạp nền ngầm
        if (typeof srcOrImg === 'string') {
            loadTransparentCanvas(srcOrImg);
        } else if (srcOrImg && srcOrImg.src) {
            loadTransparentCanvas(srcOrImg.src);
        }

        return null;
    }

    /**
     * Tiền xử lý đồng loạt toàn bộ tài nguyên khi khởi chạy trang
     */
    function preloadAllSprites() {
        const spriteList = [
            'cá 1.png', 'cá 2.png', 'cá 3.png', 'cá 4.png', 'cá 5.png', 'cá 6.png', 'cá 7.png',
            'cá kiếm.png', 'cá kiếm ăn.png',
            'tôm.png', 'tôm ăn.png',
            'cua.png', 'cua ăn.png',
            'cá ép.png', 'cá ép ăn.png',
            'bạch tuộc.png', 'bạch tuộc ăn.png',
            'cá đuối điện.png', 'cá đuối điện ăn.png',
            'rùa.png', 'rùa ăn.png',
            'cá độc.png',
            'bom.png', 'bom nổ.png',
            'bá khí.png', 'mực đen.png'
        ];

        spriteList.forEach(file => {
            loadTransparentCanvas(file);
        });
    }

    // Tự động kích hoạt tiền tải ngay khi script được nạp
    if (typeof window !== 'undefined') {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', preloadAllSprites);
        } else {
            preloadAllSprites();
        }
    }

    return {
        loadTransparentCanvas,
        getTransparent,
        processTransparentSprite,
        preloadAllSprites
    };
})();

window.GameAssets = GameAssets;
