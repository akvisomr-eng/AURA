const DEFAULTS = { enabled: true, warnHttp: true, notifications: true };
const lastAlert = new Map();

async function settings() {
  return { ...DEFAULTS, ...(await chrome.storage.local.get(DEFAULTS)) };
}

function assess(rawUrl, tabTitle = "") {
  let u;
  try { u = new URL(rawUrl); } catch { return { level: "unknown", score: 0, reasons: ["URL tidak dapat diperiksa"] }; }
  if (!["http:", "https:"].includes(u.protocol)) return { level: "ignored", score: 0, reasons: [] };
  const reasons = [];
  let score = 0;
  const host = u.hostname.toLowerCase();
  if (u.protocol === "http:") { score += 2; reasons.push("Koneksi HTTP tidak terenkripsi"); }
  if (host.includes("xn--")) { score += 2; reasons.push("Domain menggunakan karakter Punycode yang perlu diperiksa"); }
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host)) { score += 2; reasons.push("Situs menggunakan alamat IP langsung"); }
  if (host.split(".").length >= 6) { score += 1; reasons.push("Subdomain sangat panjang"); }
  if (host.length > 45) { score += 1; reasons.push("Nama domain sangat panjang"); }
  if (/(login|verify|secure|account|wallet|password|signin|update|support)[-0-9]/i.test(host)) {
    score += 1; reasons.push("Nama domain mengandung pola yang sering dipakai dalam umpan login (belum tentu berbahaya)");
  }
  if (/(free[-_]?gift|urgent|verify[-_]?account|password[-_]?reset)/i.test(u.pathname + u.search)) {
    score += 1; reasons.push("URL mengandung kata-kata yang kerap muncul pada umpan phishing");
  }
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local")) {
    return { level: "local", score: 0, reasons: ["Alamat lokal; tidak dinilai sebagai situs internet"] };
  }
  const level = score >= 4 ? "high" : score >= 2 ? "caution" : "no-obvious-signals";
  if (!reasons.length) reasons.push("Tidak ditemukan indikator sederhana dari URL. Ini BUKAN bukti situs aman; reputasi, konten, unduhan, dan phishing belum diverifikasi.");
  return { level, score, reasons, host, title: tabTitle };
}

async function inspect(tab) {
  if (!tab?.url) return;
  const s = await settings();
  if (!s.enabled) return;
  const result = assess(tab.url, tab.title || "");
  await chrome.storage.local.set({
    lastScan: { ...result, url: tab.url, scannedAt: new Date().toISOString(), tabId: tab.id }
  });
  if (!s.notifications || !["high", "caution"].includes(result.level)) return;
  const key = tab.id + ":" + tab.url;
  if (lastAlert.has(key)) return;
  lastAlert.set(key, Date.now());
  const title = result.level === "high" ? "AURA: Risiko tinggi terindikasi" : "AURA: Perlu kehati-hatian";
  chrome.notifications.create("aura-" + tab.id, {
    type: "basic",
    iconUrl: "icon.svg",
    title,
    message: result.reasons.slice(0, 2).join(". ").slice(0, 240),
    priority: result.level === "high" ? 2 : 1
  });
}

chrome.tabs.onActivated.addListener(async ({ tabId }) => {
  try { inspect(await chrome.tabs.get(tabId)); } catch {}
});
chrome.tabs.onUpdated.addListener((tabId, change, tab) => {
  if (change.status === "complete" || change.url) inspect(tab);
});
chrome.runtime.onInstalled.addListener(async () => {
  const current = await chrome.storage.local.get(DEFAULTS);
  await chrome.storage.local.set({ ...DEFAULTS, ...current });
  chrome.tabs.query({ active: true, lastFocusedWindow: true }, tabs => tabs.forEach(inspect));
});
chrome.runtime.onMessage.addListener((msg, _sender, sendResponse) => {
  if (msg?.type === "AURA_SCAN_ACTIVE_TAB") {
    chrome.tabs.query({ active: true, lastFocusedWindow: true }, async tabs => {
      const tab = tabs[0];
      if (!tab?.url) { sendResponse({ error: "Tab aktif tidak tersedia" }); return; }
      const result = assess(tab.url, tab.title || "");
      const data = { ...result, url: tab.url, scannedAt: new Date().toISOString(), tabId: tab.id };
      await chrome.storage.local.set({ lastScan: data });
      sendResponse(data);
    });
    return true;
  }
  if (msg?.type === "AURA_SET_ENABLED") {
    chrome.storage.local.set({ enabled: Boolean(msg.enabled) }).then(() => sendResponse({ enabled: Boolean(msg.enabled) }));
    return true;
  }
});