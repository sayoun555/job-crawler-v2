const SITES = [
  { id: "SARAMIN",  name: "사람인",   domain: ".saramin.co.kr"  },
  { id: "JOBKOREA", name: "잡코리아", domain: ".jobkorea.co.kr" },
  { id: "JOBPLANET",name: "잡플래닛", domain: ".jobplanet.co.kr"},
  { id: "LINKAREER",name: "링커리어", domain: ".linkareer.com"  },
];

const CONFIG_KEY = "jobcrawler_config";

document.addEventListener("DOMContentLoaded", async () => {
  const config = await loadConfig();
  document.getElementById("serverUrl").value = config.serverUrl || "";
  document.getElementById("jwtToken").value = config.token || "";

  document.getElementById("saveConfigBtn").addEventListener("click", saveConfig);

  renderSites();
});

async function loadConfig() {
  return new Promise((resolve) => {
    chrome.storage.local.get(CONFIG_KEY, (result) => {
      resolve(result[CONFIG_KEY] || {});
    });
  });
}

async function saveConfig() {
  const serverUrl = document.getElementById("serverUrl").value.trim().replace(/\/$/, "");
  const token = document.getElementById("jwtToken").value.trim();

  if (!serverUrl || !token) {
    showMessage("서버 주소와 토큰을 모두 입력하세요.", "error");
    return;
  }

  await new Promise((resolve) => {
    chrome.storage.local.set({ [CONFIG_KEY]: { serverUrl, token } }, resolve);
  });
  showMessage("설정 저장 완료", "success");
}

function renderSites() {
  const container = document.getElementById("siteList");
  container.innerHTML = "";

  SITES.forEach((site) => {
    const item = document.createElement("div");
    item.className = "site-item";
    item.innerHTML = `
      <div>
        <div class="site-name">${site.name}</div>
        <div class="site-status" id="status-${site.id}">확인 중...</div>
      </div>
      <button class="sync-btn connect" id="btn-${site.id}">연동</button>
    `;
    container.appendChild(item);

    document.getElementById(`btn-${site.id}`).addEventListener("click", () => syncSite(site));
    checkLoginStatus(site);
  });
}

function checkLoginStatus(site) {
  chrome.cookies.getAll({ domain: site.domain }, (cookies) => {
    const statusEl = document.getElementById(`status-${site.id}`);
    const btnEl = document.getElementById(`btn-${site.id}`);

    if (cookies && cookies.length > 0) {
      statusEl.textContent = `쿠키 ${cookies.length}개 감지`;
      statusEl.className = "site-status connected";
      btnEl.textContent = "연동";
      btnEl.className = "sync-btn connect";
      btnEl.disabled = false;
    } else {
      statusEl.textContent = "로그인 필요";
      statusEl.className = "site-status";
      btnEl.textContent = "로그인 필요";
      btnEl.className = "sync-btn connect";
      btnEl.disabled = true;
    }
  });
}

async function syncSite(site) {
  const config = await loadConfig();
  if (!config.serverUrl || !config.token) {
    showMessage("먼저 서버 주소와 토큰을 설정하세요.", "error");
    return;
  }

  const btnEl = document.getElementById(`btn-${site.id}`);
  btnEl.disabled = true;
  btnEl.textContent = "전송 중...";

  chrome.cookies.getAll({ domain: site.domain }, async (cookies) => {
    if (!cookies || cookies.length === 0) {
      showMessage(`${site.name}에 먼저 로그인하세요.`, "error");
      btnEl.disabled = false;
      btnEl.textContent = "연동";
      return;
    }

    const cookieData = cookies.map((c) => ({
      name: c.name,
      value: c.value,
      domain: c.domain,
      path: c.path,
      secure: c.secure,
      httpOnly: c.httpOnly,
      ...(c.expirationDate ? { expires: c.expirationDate } : {}),
    }));

    try {
      const response = await fetch(`${config.serverUrl}/api/v1/accounts/cookie-session`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${config.token}`,
        },
        body: JSON.stringify({
          site: site.id,
          cookies: JSON.stringify(cookieData),
        }),
      });

      if (response.ok) {
        showMessage(`${site.name} 연동 성공!`, "success");
        btnEl.textContent = "연동 완료";
        btnEl.className = "sync-btn connected";
      } else if (response.status === 401 || response.status === 403) {
        showMessage("토큰이 만료되었습니다. 다시 로그인 후 토큰을 갱신하세요.", "error");
        btnEl.disabled = false;
        btnEl.textContent = "연동";
      } else {
        const err = await response.json().catch(() => ({}));
        showMessage(`연동 실패: ${err.message || response.statusText}`, "error");
        btnEl.disabled = false;
        btnEl.textContent = "연동";
      }
    } catch (e) {
      showMessage(`서버 연결 실패: ${e.message}`, "error");
      btnEl.disabled = false;
      btnEl.textContent = "연동";
    }
  });
}

function showMessage(text, type) {
  const el = document.getElementById("message");
  el.textContent = text;
  el.className = `message ${type}`;
  setTimeout(() => { el.className = "message"; }, 4000);
}
