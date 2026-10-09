const $ = id => document.getElementById(id);
function render(data) {
  const labels = { high: "RISIKO TINGGI TERINDIKASI", caution: "PERLU KEHATI-HATIAN", "no-obvious-signals": "TIDAK ADA INDIKATOR SEDERHANA", ignored: "SKEMA URL TIDAK DIPERIKSA", local: "ALAMAT LOKAL", unknown: "TIDAK DAPAT DINILAI" };
  $("status").textContent = labels[data.level] || "Status tidak diketahui";
  $("status").className = "status " + (data.level || "");
  $("host").textContent = data.host || data.url || "";
  $("reasons").replaceChildren();
  (data.reasons || ["Tidak ada detail tersedia."]).forEach(reason => { const li = document.createElement("li"); li.textContent = reason; $("reasons").append(li); });
}
async function scan() {
  $("status").textContent = "Memeriksa tab aktif…";
  chrome.runtime.sendMessage({ type: "AURA_SCAN_ACTIVE_TAB" }, response => {
    if (chrome.runtime.lastError) { $("status").textContent = "Tidak dapat memeriksa tab ini."; return; }
    if (response) render(response);
  });
}
chrome.storage.local.get({ enabled: true }).then(s => { $("enabled").checked = s.enabled; });
$("enabled").addEventListener("change", () => chrome.runtime.sendMessage({ type: "AURA_SET_ENABLED", enabled: $("enabled").checked }));
$("scan").addEventListener("click", scan);
scan();