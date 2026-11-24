(function () {
  "use strict";

  var CONFIG_URL = "config.json";
  var CARDS_URL = "cards/";   // 이 아래 폴더 하나 = 칩 하나
  var THEME_KEY = "ce-theme";

  var chipsEl = document.getElementById("chips");
  var captionEl = document.getElementById("chip-caption");
  var siteNameEl = document.getElementById("site-name");
  var questionEl = document.getElementById("question");
  var noticeEl = document.getElementById("notice");

  var items = [];   // 선택 가능한 카드(활성/비활성 칩) 엘리먼트
  var chips = [];   // 칩 카드 + 준비 중 카드 데이터
  var focusIndex = 0;
  var loadSeq = 0;  // 새로고침을 잇달아 눌렀을 때 늦게 온 옛 응답을 버린다

  // ── 테마 ──────────────────────────
  function applyTheme(theme) {
    if (theme) document.documentElement.setAttribute("data-theme", theme);
    else document.documentElement.removeAttribute("data-theme");
  }
  function savedTheme() {
    try { return localStorage.getItem(THEME_KEY); } catch (e) { return null; }
  }
  applyTheme(savedTheme());

  document.getElementById("btn-theme").addEventListener("click", function () {
    var current = document.documentElement.getAttribute("data-theme");
    if (!current) {
      current = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    }
    var next = current === "dark" ? "light" : "dark";
    applyTheme(next);
    try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* 무시 */ }
  });

  document.getElementById("btn-reload").addEventListener("click", load);

  // ── 시계 ──────────────────────────
  var clockEl = document.getElementById("clock");
  function tick() {
    var d = new Date();
    clockEl.textContent =
      String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
  }
  tick();
  setInterval(tick, 10000);

  // ── 카드 아트 ─────────────────────
  function artFor(chip) {
    var art = document.createElement("div");
    art.className = "chip-art";
    if (chip.image) {
      art.style.background = "center / cover no-repeat url(" + JSON.stringify(chip.image) + ")";
      return art;
    }
    if (chip.art === "soon") {
      art.classList.add("art-soon");
      art.innerHTML = "<span>?</span>";
    } else {
      art.classList.add("art-initial");
      art.style.background = chip.color || "#607d8b";
      var s = document.createElement("span");
      s.textContent = (chip.name || "?").charAt(0).toUpperCase();
      art.appendChild(s);
    }
    return art;
  }

  // ── 렌더 ──────────────────────────
  function count(value, fallback) {
    var n = value == null ? fallback : Math.floor(Number(value));
    return n >= 0 && isFinite(n) ? n : fallback;
  }

  function soonCard() {
    return { name: "준비 중", description: "새 서비스가 곧 추가됩니다", art: "soon", enabled: false };
  }

  function render(config, cards) {
    var siteName = config.siteName || "CommentEngine";
    siteNameEl.textContent = siteName;
    document.title = siteName;
    if (config.question) questionEl.textContent = config.question;

    chips = cards.slice();
    for (var s = count(config.comingSoon, 1); s > 0; s--) chips.push(soonCard());
    noticeEl.textContent = cards.length ? "" : "꽂힌 서비스가 없습니다";
    noticeEl.hidden = cards.length > 0;

    captionEl.innerHTML = "&nbsp;";
    chipsEl.innerHTML = "";
    items = [];

    chips.forEach(function (chip, i) {
      var enabled = chip.enabled !== false && !!chip.url;
      var li = document.createElement("li");
      li.className = "chip" + (enabled ? "" : " is-disabled");
      li.setAttribute("role", "option");
      li.setAttribute("tabindex", "-1");
      li.setAttribute("aria-disabled", String(!enabled));
      li.setAttribute("aria-label", (chip.name || "") + (enabled ? "" : " (준비 중)"));

      li.appendChild(artFor(chip));

      if (chip.art !== "soon") {
        var title = document.createElement("span");
        title.className = "chip-title";
        title.textContent = chip.name || "";
        li.appendChild(title);
      }
      if (!enabled) {
        var badge = document.createElement("span");
        badge.className = "chip-badge";
        badge.textContent = "준비 중";
        li.appendChild(badge);
      }

      li.addEventListener("mouseenter", function () { setFocus(i, false); });
      li.addEventListener("click", function () { setFocus(i, true); open(i); });

      chipsEl.appendChild(li);
      items.push(li);
    });

    var empties = count(config.emptySlots, 2);
    for (var k = 0; k < empties; k++) {
      var e = document.createElement("li");
      e.className = "chip is-empty";
      e.setAttribute("aria-hidden", "true");
      chipsEl.appendChild(e);
    }

    var first = chips.findIndex(function (c) { return c.enabled !== false && c.url; });
    setFocus(first >= 0 ? first : 0, true);
  }

  function setFocus(i, moveDomFocus) {
    if (!items.length) return;
    focusIndex = (i + items.length) % items.length;
    items.forEach(function (el, idx) {
      var on = idx === focusIndex;
      el.classList.toggle("is-focused", on);
      el.setAttribute("aria-selected", String(on));
    });
    var el = items[focusIndex];
    if (moveDomFocus) el.focus({ preventScroll: true });
    el.scrollIntoView({ block: "nearest", inline: "nearest", behavior: "smooth" });

    var chip = chips[focusIndex];
    captionEl.innerHTML = "";
    var name = document.createTextNode(chip.name || "");
    captionEl.appendChild(name);
    if (chip.description) {
      var d = document.createElement("span");
      d.className = "desc";
      d.textContent = chip.description;
      captionEl.appendChild(d);
    }
  }

  function open(i) {
    var chip = chips[i];
    if (!chip || chip.enabled === false || !chip.url) return;
    // {host} 는 지금 접속한 주소로 바꾼다 (예: http://{host}:8088 → http://192.168.0.112:8088)
    window.location.href = String(chip.url).replace(/\{host\}/g, location.hostname);
  }

  function showError(msg) {
    items = [];
    chips = [];
    noticeEl.hidden = true;
    chipsEl.innerHTML = "";
    captionEl.innerHTML = "&nbsp;";
    var p = document.createElement("li");
    p.className = "error";
    p.textContent = msg;
    chipsEl.appendChild(p);
  }

  // ── 키보드 ────────────────────────
  document.addEventListener("keydown", function (ev) {
    if (!items.length) return;
    if (ev.target && ev.target.closest && ev.target.closest(".dock")) return;
    if (ev.key === "ArrowRight") { setFocus(focusIndex + 1, true); ev.preventDefault(); }
    else if (ev.key === "ArrowLeft") { setFocus(focusIndex - 1, true); ev.preventDefault(); }
    else if (ev.key === "Enter" || ev.key === " ") { open(focusIndex); ev.preventDefault(); }
  });

  // ── 설정·칩 카드 로드 ─────────────
  function getJson(url) {
    return fetch(url, { cache: "no-store" }).then(function (res) {
      if (!res.ok) {
        var err = new Error(url + " HTTP " + res.status);
        err.status = res.status;
        throw err;
      }
      return res.json();
    });
  }

  // 칩 폴더 이름 목록. 칩을 하나도 안 꽂으면 cards/ 폴더가 없어 404 → 빈 목록
  function loadFolders() {
    return getJson(CARDS_URL).then(function (entries) {
      return entries
        .filter(function (e) { return e.type === "directory"; })
        .map(function (e) { return e.name; });
    }, function (err) {
      if (err.status === 404) return [];
      throw err;
    });
  }

  // 카드 하나. 깨졌으면 그 카드만 건너뛴다(null)
  function loadCard(folder) {
    var base = CARDS_URL + encodeURIComponent(folder) + "/";
    return getJson(base + "card.json").then(function (card) {
      if (!card || typeof card.name !== "string" || typeof card.url !== "string") {
        throw new Error(base + "card.json에 name, url이 없음");
      }
      return {
        folder: folder,
        name: card.name,
        description: card.description,
        url: card.url,
        image: card.image ? base + encodeURIComponent(card.image) : "",
        color: card.color,
        order: typeof card.order === "number" ? card.order : Infinity   // 없으면 맨 뒤
      };
    }).catch(function (err) {
      console.warn("[CommentEngine] 칩 카드를 건너뜀 (" + folder + "):", err);
      return null;
    });
  }

  function byOrder(a, b) {
    if (a.order !== b.order) return a.order < b.order ? -1 : 1;
    return a.folder < b.folder ? -1 : 1;
  }

  function loadCards() {
    return loadFolders()
      .then(function (folders) { return Promise.all(folders.map(loadCard)); })
      .then(function (cards) { return cards.filter(Boolean).sort(byOrder); });
  }

  function load() {
    var seq = ++loadSeq;
    Promise.all([getJson(CONFIG_URL), loadCards()])
      .then(function (r) { if (seq === loadSeq) render(r[0], r[1]); })
      .catch(function (err) {
        if (seq !== loadSeq) return;
        console.error("[CommentEngine] 서비스 목록 로드 실패:", err);
        showError("서비스 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
      });
  }

  load();
})();
