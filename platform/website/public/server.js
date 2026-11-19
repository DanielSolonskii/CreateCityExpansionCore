const statusLabel = document.getElementById('server-state');
const playerCount = document.getElementById('server-players');
const newsList = document.getElementById('news-list');
const newsMessage = document.getElementById('news-message');
const moreButton = document.getElementById('news-more');
const retryButton = document.getElementById('news-retry');
const newsDate = new Intl.DateTimeFormat('ru-RU', { dateStyle: 'long', timeZone: 'Europe/Minsk' });
let checkingStatus = false;
let posts = [];
let shown = 0;

async function publicData(path) {
  const response = await fetch('/api/launcher/' + path, {
    cache: 'no-store', credentials: 'omit', signal: AbortSignal.timeout(8000)
  });
  if (!response.ok) throw new Error('Ошибка загрузки');
  return response.json();
}

async function refreshStatus() {
  if (document.hidden || checkingStatus) return;
  checkingStatus = true;
  let label = 'Нет данных о сервере';
  let count = '—';
  let state = 'unknown';
  try {
    const value = await publicData('server-status');
    if (value.state === 'ONLINE') {
      label = 'Сервер онлайн';
      state = 'online';
      if (Number.isSafeInteger(value.playersOnline) && value.playersOnline >= 0) {
        count = String(value.playersOnline);
        if (Number.isSafeInteger(value.playersMax) && value.playersMax > 0) count += ' / ' + value.playersMax;
      }
    } else if (value.state === 'OFFLINE') {
      label = 'Сервер офлайн';
      state = 'offline';
    }
  } catch {
    // An unavailable API cannot establish the state of the game server.
  } finally {
    if (statusLabel.dataset.state !== state) {
      statusLabel.dataset.state = state;
      statusLabel.textContent = label;
    }
    if (playerCount.textContent !== count) playerCount.textContent = count;
    checkingStatus = false;
  }
}

function appendNews() {
  const page = posts.slice(shown, shown + 3);
  for (const post of page) {
    const article = document.createElement('article');
    article.className = 'news-item';
    const date = document.createElement('time');
    date.dateTime = post.publishedAt;
    date.textContent = newsDate.format(new Date(post.publishedAt));
    const meta = document.createElement('span');
    meta.className = 'news-meta';
    meta.append(date);
    if (post.releaseVersion) meta.append(' · Обновление ' + post.releaseVersion);
    const title = document.createElement('h3');
    title.textContent = post.title;
    article.append(meta, title);
    if (post.body) {
      const body = document.createElement('p');
      body.className = 'news-body';
      const characters = Array.from(post.body);
      if (characters.length > 350) {
        const excerpt = characters.slice(0, 180).join('');
        const space = excerpt.lastIndexOf(' ');
        const end = space > 90 ? space : excerpt.length;
        body.textContent = post.body.slice(0, end) + '…';
        article.append(body);
        const details = document.createElement('details');
        const summary = document.createElement('summary');
        summary.textContent = 'Продолжение';
        const rest = document.createElement('p');
        rest.className = 'news-body';
        rest.textContent = post.body.slice(end);
        details.append(summary, rest);
        article.append(details);
      } else {
        body.textContent = post.body;
        article.append(body);
      }
    }
    newsList.append(article);
  }
  shown += page.length;
  moreButton.hidden = shown >= posts.length;
}

async function loadNews() {
  retryButton.hidden = true;
  newsMessage.hidden = false;
  newsMessage.textContent = 'Загружаем новости…';
  try {
    const feed = await publicData('news');
    if (!Array.isArray(feed)) throw new Error('Некорректная лента');
    posts = feed.filter(post => post && typeof post.title === 'string' && typeof post.body === 'string'
      && Number.isFinite(Date.parse(post.publishedAt)))
      .sort((a, b) => Date.parse(b.publishedAt) - Date.parse(a.publishedAt));
    shown = 0;
    newsList.replaceChildren();
    appendNews();
    newsMessage.hidden = posts.length > 0;
    newsMessage.textContent = posts.length ? '' : 'Публикаций пока нет.';
  } catch {
    newsMessage.textContent = 'Не удалось загрузить новости.';
    retryButton.hidden = false;
  }
}

moreButton.addEventListener('click', appendNews);
retryButton.addEventListener('click', loadNews);
document.addEventListener('visibilitychange', refreshStatus);
window.addEventListener('pageshow', refreshStatus);
setInterval(refreshStatus, 30000);
statusLabel.dataset.state = 'checking';
statusLabel.textContent = 'Проверяем статус сервера…';
refreshStatus();
loadNews();
