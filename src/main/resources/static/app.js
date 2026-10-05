const content = document.getElementById("content");
const navigation = document.getElementById("navigation");
const errorBox = document.getElementById("error");
let screenVersion = 0;

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}

function showError(message) {
  errorBox.textContent = message;
  errorBox.hidden = !message;
}

async function api(method, path, body) {
  const token = localStorage.getItem("token");
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(path, {
    method, headers, cache: "no-store", body: body === undefined ? undefined : JSON.stringify(body)
  });
  const data = await response.json();
  if (response.status === 401) {
    localStorage.removeItem("token");
    screenVersion++;
    loginScreen();
  }
  if (!response.ok) throw new Error(data.error || "Не удалось выполнить запрос");
  return data;
}

function time(value) {
  if (!value) return "—";
  const [date, hours] = value.split("T");
  return `${date.slice(8, 10)}.${date.slice(5, 7)}.${date.slice(0, 4)} ${hours.slice(0, 5)}`;
}

function today() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Moscow", year: "numeric", month: "2-digit", day: "2-digit"
  }).formatToParts(new Date());
  const part = type => parts.find(item => item.type === type).value;
  return `${part("year")}-${part("month")}-${part("day")}`;
}

function addDays(date, days) {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

function formParams(form) {
  const params = new URLSearchParams();
  for (const [key, value] of new FormData(form)) if (value !== "") params.set(key, value);
  return params;
}

function go(path, params) {
  const hash = `#${path}${params.size ? `?${params}` : ""}`;
  if (location.hash === hash) render();
  else location.hash = hash;
}

function pagination(params, total) {
  const page = Number(params.get("page") || 1);
  const size = Number(params.get("size") || 20);
  const pages = Math.max(1, Math.ceil(total / size));
  return `<div class="pagination"><button data-page="${page - 1}" ${page <= 1 ? "disabled" : ""}>Назад</button>
    <span>Страница ${escapeHtml(page)} из ${pages} · всего ${total}</span>
    <button data-page="${page + 1}" ${page >= pages ? "disabled" : ""}>Вперёд</button></div>`;
}

function bindPagination(path, params) {
  content.querySelectorAll("[data-page]").forEach(button => button.addEventListener("click", () => {
    const next = new URLSearchParams(params);
    next.set("page", button.dataset.page);
    go(path, next);
  }));
}

function loginScreen() {
  navigation.hidden = true;
  content.innerHTML = `<h2>Вход</h2><form id="login" class="login">
    <label>Логин<input name="login" autocomplete="username" required></label>
    <label>Пароль<input name="password" type="password" autocomplete="current-password" required></label>
    <button>Войти</button></form><p>Демо-учётная запись: admin / demo</p>`;
  document.getElementById("login").addEventListener("submit", async event => {
    event.preventDefault();
    showError("");
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    const values = Object.fromEntries(new FormData(event.currentTarget));
    try {
      const result = await api("POST", "/api/auth/login", values);
      localStorage.setItem("token", result.token);
      await render();
    } catch (error) {
      showError(error.message);
    } finally {
      button.disabled = false;
    }
  });
}

async function bookingsScreen(params, version) {
  const result = await api("GET", `/api/bookings?${params}`);
  if (version !== screenVersion) return;
  const status = params.get("status") || "";
  const size = params.get("size") || "20";
  content.innerHTML = `<h2>Записи</h2><form id="filters">
    <label>Статус<select name="status">${[["", "Все"], ["active", "Активные"], ["cancelled", "Отменённые"]]
      .map(([value, label]) => `<option value="${value}" ${status === value ? "selected" : ""}>${label}</option>`).join("")}</select></label>
    <label>ID специалиста<input name="specialistId" type="number" min="1" value="${escapeHtml(params.get("specialistId"))}"></label>
    <label>С<input name="from" type="date" value="${escapeHtml(params.get("from"))}"></label>
    <label>По<input name="to" type="date" value="${escapeHtml(params.get("to"))}"></label>
    <label>На странице<select name="size">${[20, 50, 100].map(value =>
      `<option ${String(value) === size ? "selected" : ""}>${value}</option>`).join("")}</select></label>
    <button>Показать</button></form>
    <div class="table-wrap"><table><thead><tr><th>№</th><th>Начало</th><th>Конец</th><th>Специалист</th>
    <th>Специальность</th><th>Услуга</th><th>Клиент</th><th>Статус</th></tr></thead><tbody>
    ${result.items.map(row => `<tr data-booking="${row.id}"><td><a href="#/bookings/${row.id}">${row.id}</a></td>
      <td>${time(row.startsAt)}</td><td>${time(row.endsAt)}</td><td>${escapeHtml(row.specialist.fullName)}</td>
      <td>${escapeHtml(row.specialist.specialty)}</td><td>${escapeHtml(row.service.name)}</td>
      <td>${escapeHtml(row.clientName)}</td><td>${row.status === "active" ? "Активна" : "Отменена"}</td></tr>`).join("")}
    </tbody></table></div>${result.total === 0 ? "<p>Записей нет.</p>" : ""}
    ${pagination(params, result.total)}`;
  document.getElementById("filters").addEventListener("submit", event => {
    event.preventDefault();
    const next = formParams(event.currentTarget);
    next.set("page", "1");
    go("/bookings", next);
  });
  content.querySelectorAll("[data-booking]").forEach(row => row.addEventListener("click", () => {
    location.hash = `#/bookings/${row.dataset.booking}`;
  }));
  bindPagination("/bookings", params);
}

async function cardScreen(id, version) {
  const card = await api("GET", `/api/bookings/${id}`);
  if (version !== screenVersion) return;
  content.innerHTML = `<h2>Запись № ${card.id}</h2><dl>
    <dt>Статус</dt><dd>${card.status === "active" ? "Активна" : "Отменена"}</dd>
    <dt>Создана</dt><dd>${time(card.createdAt)}</dd><dt>Отменена</dt><dd>${time(card.cancelledAt)}</dd>
    <dt>Слот № ${card.slot.id}</dt><dd>${time(card.slot.startsAt)} — ${time(card.slot.endsAt)}</dd>
    <dt>Специалист</dt><dd>${escapeHtml(card.specialist.fullName)} · ${escapeHtml(card.specialist.specialty)}</dd>
    <dt>Услуга</dt><dd>${escapeHtml(card.service.name)} · ${card.service.durationMin} мин.</dd>
    <dt>Клиент</dt><dd>${escapeHtml(card.client.fullName)} · ${escapeHtml(card.client.login)}</dd></dl>
    ${card.status === "active" ? '<p><button id="cancel">Отменить запись</button></p>' : ""}`;
  document.getElementById("cancel")?.addEventListener("click", async event => {
    if (!confirm("Отменить запись?")) return;
    event.currentTarget.disabled = true;
    try { await api("POST", `/api/bookings/${id}/cancel`); await render(); }
    catch (error) { showError(error.message); if (version === screenVersion) document.getElementById("cancel").disabled = false; }
  });
}

async function bookScreen(params, version) {
  const services = await api("GET", "/api/services");
  if (version !== screenVersion) return;
  const serviceId = params.get("serviceId") || String(services[0]?.id || "");
  const from = params.get("from") || today();
  const to = params.get("to") || addDays(from, 6);
  content.innerHTML = `<h2>Записаться</h2><form id="search">
    <label>Услуга<select name="serviceId" required>${services.map(service =>
      `<option value="${service.id}" ${String(service.id) === serviceId ? "selected" : ""}>
      ${escapeHtml(service.name)} · ${service.durationMin} мин.</option>`).join("")}</select></label>
    <label>С<input name="from" type="date" required value="${escapeHtml(from)}"></label>
    <label>По<input name="to" type="date" required value="${escapeHtml(to)}"></label>
    <button ${services.length ? "" : "disabled"}>Найти слоты</button></form><div id="slots"></div>`;
  document.getElementById("search").addEventListener("submit", event => {
    event.preventDefault();
    const next = formParams(event.currentTarget);
    next.set("page", "1");
    go("/book", next);
  });
  if (!params.get("serviceId")) return;
  const query = new URLSearchParams({ from, to, page: params.get("page") || "1", size: "20" });
  const result = await api("GET", `/api/services/${encodeURIComponent(serviceId)}/free-slots?${query}`);
  if (version !== screenVersion) return;
  document.getElementById("slots").innerHTML = `<div class="table-wrap"><table><thead><tr>
    <th>Начало</th><th>Конец</th><th>Специалист</th><th></th></tr></thead><tbody>
    ${result.items.map(slot => `<tr><td>${time(slot.startsAt)}</td><td>${time(slot.endsAt)}</td>
      <td>${escapeHtml(slot.specialist.fullName)} · ${escapeHtml(slot.specialist.specialty)}</td>
      <td><button data-slot="${slot.id}">Записаться</button></td></tr>`).join("")}</tbody></table></div>
    ${result.total === 0 ? "<p>Свободных слотов нет.</p>" : ""}${pagination(params, result.total)}`;
  content.querySelectorAll("[data-slot]").forEach(button => button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      const card = await api("POST", "/api/bookings", { slotId: Number(button.dataset.slot), serviceId: Number(serviceId) });
      location.hash = `#/bookings/${card.id}`;
    } catch (error) { showError(error.message); button.disabled = false; }
  }));
  bindPagination("/book", params);
}

async function summaryScreen(params, version) {
  const date = today();
  const from = params.get("from") || `${date.slice(0, 7)}-01`;
  const nextMonth = new Date(`${date.slice(0, 7)}-01T00:00:00Z`);
  nextMonth.setUTCMonth(nextMonth.getUTCMonth() + 1);
  const to = params.get("to") || addDays(nextMonth.toISOString().slice(0, 10), -1);
  content.innerHTML = `<h2>Сводка</h2><form id="summary">
    <label>С<input name="from" type="date" required value="${escapeHtml(from)}"></label>
    <label>По<input name="to" type="date" required value="${escapeHtml(to)}"></label>
    <button>Посчитать</button></form><div id="totals"></div>`;
  document.getElementById("summary").addEventListener("submit", event => {
    event.preventDefault(); go("/summary", formParams(event.currentTarget));
  });
  const result = await api("GET", `/api/summary?${new URLSearchParams({ from, to })}`);
  if (version !== screenVersion) return;
  document.getElementById("totals").innerHTML = `<p>Записей: ${result.totalBookings}, доля отмен: ${(result.cancelledShare * 100).toFixed(1)} %</p>
    <div class="table-wrap"><table><thead><tr><th>Специалист</th><th>Специальность</th>
    <th>Минут в слотах</th><th>Минут занято</th><th>Загрузка</th></tr></thead><tbody>
    ${result.specialists.map(row => `<tr><td>${escapeHtml(row.fullName)}</td><td>${escapeHtml(row.specialty)}</td>
      <td>${row.slotMinutes}</td><td>${row.bookedMinutes}</td><td>${(row.load * 100).toFixed(1)} %</td></tr>`).join("")}
    </tbody></table></div>`;
}

async function render() {
  const version = ++screenVersion;
  showError("");
  if (!localStorage.getItem("token")) { loginScreen(); return; }
  navigation.hidden = false;
  const [path, query = ""] = (location.hash.slice(1) || "/bookings").split("?");
  const params = new URLSearchParams(query);
  content.innerHTML = "<p>Загрузка…</p>";
  try {
    if (path === "/bookings") await bookingsScreen(params, version);
    else if (/^\/bookings\/[0-9]+$/.test(path)) await cardScreen(path.split("/")[2], version);
    else if (path === "/book") await bookScreen(params, version);
    else if (path === "/summary") await summaryScreen(params, version);
    else location.hash = "#/bookings";
  } catch (error) {
    if (version === screenVersion || !localStorage.getItem("token")) showError(error.message);
  }
}

document.getElementById("logout").addEventListener("click", () => {
  localStorage.removeItem("token"); render();
});
window.addEventListener("hashchange", render);
render();
