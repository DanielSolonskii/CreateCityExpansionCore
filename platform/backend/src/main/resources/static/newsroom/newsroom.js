'use strict';

(() => {
  const $ = (id) => document.getElementById(id);
  const fields = {
    title: $('post-title'), body: $('post-body'), format: $('body-format'), kind: $('kind'),
    version: $('release-version'), telegram: $('channel-telegram'), launcher: $('channel-launcher'),
    audience: $('audience'), recipients: $('recipients'), changelog: $('changelog')
  };
  const names = { NEWS: 'Новость', IMPORTANT: 'Важное', UPDATE: 'Обновление', MAINTENANCE: 'Технические работы' };
  const statusNames = { DRAFT: 'Черновик', QUEUED: 'Опубликовано', COMPLETED: 'Опубликовано', CANCELLED: 'Удалённый черновик' };
  const errors = {
    STALE_DRAFT: 'Черновик изменён в другом окне или другим редактором. Загрузите актуальную версию перед продолжением.',
    POST_NOT_DRAFT: 'Этот материал уже опубликован или удалён. Обновите его состояние.',
    INVALID_POST: 'Проверьте заголовок, длину текста, версию и выбранные каналы.',
    INVALID_NEWS: 'Проверьте заголовок, длину текста, версию и выбранные каналы.',
    INVALID_PUBLICATION: 'Проверьте каналы и аудиторию. Личная и выборочная отправка доступны только в Telegram; выбранные аккаунты должны быть активны.',
    INVALID_IMAGE: 'Выберите настоящий PNG или JPEG размером до 8 МБ.',
    POST_NOT_FOUND: 'Материал не найден. Обновите список публикаций.',
    RATE_LIMITED: 'Слишком много действий. Подождите немного и повторите.',
    MARKDOWN_TOO_LONG: 'После форматирования сообщение превышает лимит Telegram. Сократите текст или подписи ссылок.',
    MARKDOWN_TOO_DEEP: 'Слишком много вложенных списков или цитат. Упростите разметку.',
    POST_NOT_PUBLISHED: 'Исправлять можно только опубликованную новость, доступную в лаунчере.'
  };
  const state = {
    token: null, expiresAt: null, expired: false, posts: [], post: null, filter: 'drafts',
    base: '', dirty: false, busy: false, uploading: false, photoFileId: null, imageUrl: null,
    imageObjectUrl: null, imageGeneration: 0, commandId: crypto.randomUUID(), confirmAction: null,
    recipients: [], preview: 'telegram', pendingNavigation: null, correction: false
  };
  const moscowDate = new Intl.DateTimeFormat('ru-RU', {
    timeZone: 'Europe/Moscow', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit'
  });
  const formatDate = (date) => {
    try { return moscowDate.format(new Date(date)) + ' МСК'; } catch { return 'Дата не получена'; }
  };
  const count = (value) => Number.isSafeInteger(value) && value >= 0 ? value : 0;

  class ApiError extends Error {
    constructor(status, code) { super(code || 'REQUEST_FAILED'); this.status = status; this.code = code; }
  }

  function expireSession() {
    state.expired = true;
    state.token = null;
    $('session-label').textContent = 'Сеанс завершён';
    $('session-reopen').hidden = false;
    notify('Сеанс редактора истёк. Получите новую ссылку в админском разделе бота. Несохранённый текст остаётся в этом окне — его можно скопировать.', 'error');
    $('conflict-actions').hidden = true;
    updateControls();
  }

  async function request(path, options = {}, anonymous = false) {
    const headers = new Headers(options.headers || {});
    headers.set('Accept', 'application/json');
    if (!anonymous) {
      if (!state.token || state.expired) throw new ApiError(401, 'SESSION_EXPIRED');
      headers.set('Authorization', 'Bearer ' + state.token);
    }
    const response = await fetch('/api/newsroom' + path, { ...options, headers, cache: 'no-store', credentials: 'omit' });
    if (!response.ok) {
      let code = null;
      try { code = (await response.json()).code; } catch { /* A proxy error need not be JSON. */ }
      if (!anonymous && (response.status === 401 || response.status === 403)) expireSession();
      throw new ApiError(response.status, code);
    }
    return response;
  }

  async function api(path, method = 'GET', body, anonymous = false) {
    const options = { method };
    if (body !== undefined) {
      options.headers = { 'Content-Type': 'application/json' };
      options.body = JSON.stringify(body);
    }
    const response = await request(path, options, anonymous);
    return response.status === 204 ? null : response.json();
  }

  function notify(text, tone = '') {
    $('notice').textContent = text;
    $('notice').className = 'notice' + (tone ? ' ' + tone : '');
    $('notice').hidden = !text;
    if (tone !== 'error') $('conflict-actions').hidden = true;
  }

  function failure(error, operation) {
    if (state.expired) return;
    if (error.status === 409) {
      notify(errors[error.code] || 'Материал уже изменился. Сначала проверьте актуальную версию.', 'error');
      $('conflict-actions').hidden = !state.post;
      return;
    }
    const detail = errors[error.code] || (error.status === 429
      ? 'Слишком много действий. Подождите немного и повторите.'
      : error.status === 413 ? 'Изображение слишком большое. Максимальный размер — 8 МБ.'
      : error.status === 400 ? 'Проверьте заполненные поля и допустимую длину текста.' : null);
    notify(detail || operation + ' Ответ не получен: проверьте соединение и состояние материала перед повторным действием.', 'error');
  }

  function snapshot() {
    return JSON.stringify({
      title: fields.title.value, body: fields.body.value, format: fields.format.value, kind: fields.kind.value,
      releaseVersion: fields.version.value, photoFileId: state.photoFileId,
      publishTelegram: fields.telegram.checked, publishLauncher: fields.launcher.checked,
      audience: fields.audience.value, recipients: [...fields.recipients.selectedOptions].map((option) => option.value).sort(),
      changelog: fields.changelog.checked
    });
  }

  function payload() {
    return {
      commandId: state.commandId, title: fields.title.value.trim(), body: bodyValue(), format: fields.format.value,
      kind: fields.kind.value, releaseVersion: fields.kind.value === 'UPDATE' ? fields.version.value.trim() : null,
      photoFileId: state.photoFileId, publishTelegram: fields.telegram.checked,
      publishLauncher: fields.launcher.checked, revision: state.post ? state.post.revision : 0,
      audience: fields.audience.value, recipients: [...fields.recipients.selectedOptions].map((option) => option.value),
      changelog: fields.kind.value === 'UPDATE' && fields.launcher.checked && fields.audience.value === 'ALL' && fields.changelog.checked
    };
  }

  function combinedLength() {
    return fields.title.value.trim().length + (bodyValue() ? bodyValue().length + 2 : 0);
  }

  function bodyValue() { return fields.format.value === 'MARKDOWN' ? fields.body.value : fields.body.value.trim(); }

  function validation() {
    const value = payload();
    if (!value.title || value.title.length > 120) return 'Добавьте заголовок длиной до 120 символов.';
    if (value.body.length > 3800) return 'Сократите текст до 3800 символов.';
    if (/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value.title + value.body)) return 'Уберите служебные символы из текста.';
    if (value.kind === 'UPDATE' && (value.releaseVersion.length > 40 || !/^v?\d{1,5}\.\d{1,5}(?:\.\d{1,5}){1,2}(?:-[A-Za-z0-9.-]{1,20})?$/.test(value.releaseVersion || '')))
      return 'Укажите версию обновления, например 1.0.18 или 1.0.18-beta.1.';
    if (!value.publishTelegram && !value.publishLauncher) return 'Выберите хотя бы один канал публикации.';
    if (value.audience !== 'ALL' && (!value.publishTelegram || value.publishLauncher)) return 'Для личной или выборочной отправки выберите только Telegram. Адресный материал не должен появиться в общей ленте лаунчера.';
    if (value.audience === 'SELECTED' && !value.recipients.length) return 'Выберите хотя бы одного получателя.';
    const limit = state.photoFileId && !state.correction ? 1024 : 4000;
    if (combinedLength() > limit) return 'Заголовок и текст вместе должны занимать не больше ' + limit + ' символов' + (state.photoFileId ? ' у материала с изображением.' : '.');
    return null;
  }

  function editable() { return !state.post || state.post.status === 'DRAFT' || state.correction; }

  function updateControls() {
    const disabled = state.busy || state.uploading || state.expired;
    $('editor-fields').disabled = state.busy || state.uploading;
    for (const field of [fields.title, fields.body]) field.readOnly = state.expired || !editable();
    fields.version.readOnly = state.expired || !editable() || state.correction;
    for (const field of [fields.kind, fields.audience, fields.recipients, fields.telegram]) field.disabled = disabled || !editable() || state.correction;
    fields.format.disabled = disabled || !editable();
    $('image-file').disabled = disabled || !editable() || state.correction;
    $('remove-image').disabled = disabled || !editable() || state.correction;
    fields.launcher.disabled = disabled || !editable() || state.correction || fields.audience.value !== 'ALL';
    fields.changelog.disabled = disabled || !editable() || state.correction || fields.kind.value !== 'UPDATE' || !fields.launcher.checked || fields.audience.value !== 'ALL';
    $('changelog-field').hidden = fields.kind.value !== 'UPDATE' || !fields.launcher.checked || fields.audience.value !== 'ALL';
    $('save-post').disabled = disabled || !editable() || (!state.dirty && !!state.post);
    $('save-post').textContent = state.correction ? 'Сохранить исправления' : 'Сохранить';
    $('publish-post').disabled = disabled || !editable() || !state.post || state.dirty;
    $('publish-post').hidden = state.correction;
    $('cancel-post').hidden = !state.post || !editable() || state.correction;
    $('cancel-post').disabled = disabled;
    $('new-post').disabled = disabled;
    $('reload-list').disabled = disabled;
    $('reload-post').disabled = disabled;
    $('refresh-delivery').disabled = disabled;
    $('edit-correction').hidden = !state.post || !['QUEUED', 'COMPLETED'].includes(state.post.status) || !state.post.publishLauncher || state.correction;
    $('edit-correction').disabled = disabled;
    $('cancel-correction').hidden = !state.correction;
    $('cancel-correction').disabled = disabled;
    $('correction-banner').hidden = !state.correction;
    $('markdown-file').disabled = disabled || !editable();
    $('markdown-import-label').classList.toggle('disabled', $('markdown-file').disabled);
    $('markdown-export').disabled = !fields.body.value;
    document.querySelectorAll('[data-markdown]').forEach((button) => { button.disabled = disabled || !editable(); });
    $('markdown-toolbar').hidden = fields.format.value !== 'MARKDOWN';
    $('markdown-help').hidden = fields.format.value !== 'MARKDOWN';
    fields.body.classList.toggle('markdown-source', fields.format.value === 'MARKDOWN');
    $('format-note').textContent = fields.format.value === 'MARKDOWN' ? 'Исходник .md · HTML не выполняется' : 'Обычный текст без форматирования';
    $('confirm-go').disabled = state.busy || state.uploading || (state.expired && state.confirmAction !== 'discard');
    $('confirm-back').disabled = state.busy;
    document.querySelectorAll('.post-item').forEach((button) => { button.disabled = disabled; });
    $('toggle-visibility').hidden = !state.post || !['QUEUED', 'COMPLETED'].includes(state.post.status) || !state.post.publishLauncher || state.correction;
    $('toggle-visibility').disabled = disabled;
    $('toggle-visibility').textContent = state.post?.hidden ? 'Вернуть материал в лаунчер' : 'Скрыть материал в лаунчере';
    $('save-indicator').classList.toggle('dirty', state.dirty);
    $('save-state').textContent = state.busy ? 'Выполняем действие…' : state.uploading ? 'Загружаем изображение…'
      : state.dirty ? 'Есть несохранённые изменения' : !state.post ? 'Новый черновик'
      : state.post.status === 'DRAFT' ? 'Сохранено · ' + formatDate(state.post.updatedAt || state.post.createdAt)
      : statusNames[state.post.status] + ' · ' + formatDate(state.post.publishedAt || state.post.updatedAt || state.post.createdAt);
    $('publish-hint').textContent = state.correction ? 'Сохранение обновит только ленту лаунчера после подтверждения. Telegram не будет отправлен повторно.' : !editable()
      ? 'Опубликованный материал доступен игрокам. Для новой новости создайте новый материал.'
      : !state.post ? 'Сначала сохраните черновик. Игроки его пока не видят.'
      : state.dirty ? 'Сохраните изменения, чтобы проверить и опубликовать актуальную версию.'
      : 'Черновик сохранён. Публикация — отдельное действие с подтверждением.';
    fields.version.required = fields.kind.value === 'UPDATE';
    $('version-field').hidden = fields.kind.value !== 'UPDATE';
    $('recipients-field').hidden = fields.audience.value !== 'SELECTED';
    $('recipients-count').textContent = fields.recipients.selectedOptions.length + ' выбрано';
    $('audience-help').textContent = fields.audience.value === 'SELF'
      ? 'Проверка перед общей публикацией. Выберите только Telegram: сообщение придёт вам, в лаунчере тест не появится.'
      : fields.audience.value === 'SELECTED' ? 'Только Telegram. Материал получат выбранные аккаунты; общая лента лаунчера не используется. Сохранённые получатели не сбрасываются при обновлении списка.'
      : 'Сообщение получат активные аккаунты с подходящей подпиской. Точный состав фиксируется при публикации.';
  }

  function updatePreview() {
    if (fields.kind.value !== 'UPDATE' || !fields.launcher.checked || fields.audience.value !== 'ALL') fields.changelog.checked = false;
    const title = fields.title.value.trim() || 'Заголовок вашей новости';
    const body = bodyValue() || (fields.title.value.trim() ? '' : 'Здесь появится текст публикации. Меняйте его слева — предпросмотр обновится сразу.');
    document.querySelectorAll('.preview-post-title').forEach((node) => { node.textContent = title; });
    document.querySelectorAll('.preview-post-body').forEach((node) => {
      const channel = node.closest('#preview-launcher') ? 'launcher' : node.closest('#preview-telegram') ? 'telegram' : 'markdown';
      window.NewsroomMarkdown.render(node, body, fields.format.value, channel);
    });
    $('preview-kind').textContent = (names[fields.kind.value] || 'Новость').toUpperCase() + (fields.kind.value === 'UPDATE' && fields.version.value.trim() ? ' · ' + fields.version.value.trim() : '');
    $('title-count').textContent = fields.title.value.length + ' / 120';
    $('body-count').textContent = fields.body.value.length + ' / 3800';
    const limit = state.photoFileId && !state.correction ? 1024 : 4000;
    $('telegram-count').textContent = combinedLength() + ' / ' + limit + (state.correction ? ' в исправлении' : ' в исходнике публикации');
    $('telegram-count').classList.toggle('over-limit', combinedLength() > limit);
    state.dirty = snapshot() !== state.base;
    updateControls();
    selectPreview(state.preview);
  }

  function selectPreview(channel) {
    const originalTelegram = state.correction || state.post?.launcherCorrected;
    $('preview-telegram-tab').disabled = !!originalTelegram;
    $('preview-markdown-tab').disabled = fields.format.value !== 'MARKDOWN';
    if ((channel === 'telegram' && originalTelegram) || (channel === 'markdown' && fields.format.value !== 'MARKDOWN')) channel = 'launcher';
    state.preview = channel;
    for (const name of ['telegram', 'launcher', 'markdown']) {
      const selected = channel === name;
      $('preview-' + name).hidden = !selected;
      const tab = $('preview-' + name + '-tab');
      tab.classList.toggle('active', selected); tab.setAttribute('aria-selected', String(selected));
    }
    $('preview-caption').textContent = channel === 'launcher'
      ? (originalTelegram ? 'Исправление только в лаунчере. Сообщение Telegram остаётся прежним. ' : '') + 'Установленный лаунчер показывает читаемый текст без маркеров Markdown. Обложка доступна в Telegram.'
      : channel === 'markdown' ? 'Предпросмотр разметки. В Telegram заголовки станут жирными строками; установленный лаунчер пока показывает текст.'
      : 'Пример Telegram: заголовки — жирным, списки и код сохраняются. Точный вид зависит от приложения игрока.';
  }

  function insertMarkdown(action) {
    if (!editable() || state.busy || state.uploading || state.expired || fields.format.value !== 'MARKDOWN') return;
    const textarea = fields.body, start = textarea.selectionStart, end = textarea.selectionEnd;
    const selected = textarea.value.slice(start, end);
    let from = start, to = end, replacement, selectStart, selectEnd;
    const wraps = { bold: ['**', '**', 'жирный текст'], italic: ['*', '*', 'курсив'], link: ['[', '](https://example.com)', 'текст ссылки'], 'inline-code': ['`', '`', 'код'], code: ['```\n', '\n```', 'код'] };
    if (wraps[action]) {
      const [before, after, placeholder] = wraps[action];
      const content = selected || placeholder;
      replacement = before + content + after;
      selectStart = start + before.length; selectEnd = selectStart + content.length;
    } else {
      from = textarea.value.lastIndexOf('\n', start - 1) + 1;
      const lineEnd = textarea.value.indexOf('\n', end);
      to = lineEnd < 0 ? textarea.value.length : lineEnd;
      const lines = textarea.value.slice(from, to).split('\n');
      const prefix = /^h[1-3]$/.test(action) ? '#'.repeat(Number(action[1])) + ' ' : action === 'bullet' ? '- ' : action === 'quote' ? '> ' : '';
      replacement = lines.map((line, index) => {
        const cleaned = /^h[1-3]$/.test(action) ? line.replace(/^#{1,6}\s+/, '') : line;
        return (action === 'number' ? index + 1 + '. ' : prefix) + cleaned;
      }).join('\n');
      selectStart = from; selectEnd = from + replacement.length;
    }
    if (textarea.value.length - (to - from) + replacement.length > 3800) {
      notify('Разметка не помещается в лимит 3800 символов. Сократите исходник.', 'error'); return;
    }
    textarea.focus(); textarea.setSelectionRange(from, to);
    // insertText keeps the native undo stack in supported desktop browsers.
    let inserted = false;
    try { inserted = document.execCommand('insertText', false, replacement); } catch { /* setRangeText is the standards-based fallback. */ }
    if (!inserted) textarea.setRangeText(replacement, from, to, 'end');
    textarea.setSelectionRange(selectStart, selectEnd);
    updatePreview();
  }

  async function importMarkdown(file) {
    if (!file || !editable() || state.busy || state.uploading || state.expired) return;
    if (!/\.(md|markdown)$/i.test(file.name) || !file.size || file.size > 64 * 1024) {
      notify('Выберите текстовый файл .md или .markdown размером до 64 КБ.', 'error'); return;
    }
    let source;
    const before = snapshot();
    try {
      source = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer()).replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n');
    } catch { notify('Не удалось прочитать файл. Сохраните его как обычный текст в UTF-8.', 'error'); return; }
    if (source.length > 3800 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(source)) {
      notify('В файле больше 3800 символов или есть нетекстовые данные. Файл не импортирован.', 'error'); return;
    }
    if (before !== snapshot() || !editable() || state.expired) { notify('Материал изменился во время чтения файла. Выберите файл ещё раз.', 'error'); return; }
    afterDiscard(() => {
      fields.body.value = source; fields.format.value = 'MARKDOWN'; updatePreview();
      notify('Текст .md импортирован. Заголовок новости задаётся отдельно; сохранение и публикация — отдельные действия.');
      fields.body.focus();
    });
  }

  function exportMarkdown() {
    if (!fields.body.value) return;
    const blob = new Blob([fields.body.value], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob), link = document.createElement('a');
    const name = (fields.title.value.trim() || 'novost').replace(/[^\p{L}\p{N}_-]+/gu, '-').slice(0, 80);
    link.href = url; link.download = name + '.md'; link.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }

  function startCorrection() {
    if (state.busy || state.uploading || state.expired || !state.post?.publishLauncher || !['QUEUED', 'COMPLETED'].includes(state.post.status)) return;
    state.correction = true; state.base = snapshot(); state.dirty = false;
    $('editor-title').textContent = 'Исправление в лаунчере';
    selectPreview('launcher'); updatePreview(); notify('Меняйте заголовок и текст. Каналы, изображение и отправленные сообщения Telegram сохраняются.');
    fields.title.focus();
  }

  function confirmCorrection() {
    state.confirmAction = 'correction';
    $('confirm-title').textContent = 'Сохранить исправления в лаунчере?';
    $('confirm-body').textContent = fields.title.value.trim();
    $('confirm-warning').textContent = 'Изменения появятся в лаунчере. Сообщения Telegram останутся прежними, повторной рассылки не будет.';
    $('confirm-go').textContent = 'Сохранить исправления';
    $('confirm-details').replaceChildren(); detail('Канал', 'Только лаунчер'); detail('Telegram', 'Без изменений и повторной отправки');
    $('confirm-dialog').showModal();
  }

  async function applyCorrection() {
    if (!state.correction || !state.post || state.busy || state.expired) return;
    const invalid = validation();
    if (invalid) { $('confirm-dialog').close(); state.confirmAction = null; notify(invalid, 'error'); return; }
    state.busy = true; updateControls();
    const value = { title: fields.title.value.trim(), body: bodyValue(), format: fields.format.value, revision: state.post.revision };
    try {
      const post = await api('/posts/' + encodeURIComponent(state.post.id) + '/correction', 'PUT', value);
      $('confirm-dialog').close(); state.confirmAction = null;
      loadForm(post); notify('Исправления сохранены в лаунчере. Сообщения Telegram не изменены, повторной отправки нет.', 'success');
      try { await refreshList(); } catch { /* The explicit correction response is authoritative. */ }
    } catch (error) {
      $('confirm-dialog').close(); state.confirmAction = null;
      // Never overwrite local corrections after a conflict or an uncertain response.
      failure(error, 'Не удалось подтвердить исправления. Проверьте актуальную версию перед повторным сохранением.');
    } finally { state.busy = false; updateControls(); }
  }

  function resetImage() {
    state.imageGeneration++;
    if (state.imageObjectUrl) URL.revokeObjectURL(state.imageObjectUrl);
    state.imageObjectUrl = null;
    $('editor-image').removeAttribute('src');
    document.querySelectorAll('.preview-image').forEach((node) => { node.hidden = true; node.removeAttribute('src'); });
    $('image-present').hidden = true;
    $('image-empty').hidden = !!state.photoFileId;
    $('image-status').hidden = true;
  }

  function showImage(url) {
    if (state.imageObjectUrl) URL.revokeObjectURL(state.imageObjectUrl);
    state.imageObjectUrl = url;
    $('editor-image').src = url;
    $('image-present').hidden = false;
    $('image-empty').hidden = true;
    $('image-status').hidden = true;
    document.querySelectorAll('.telegram-message .preview-image').forEach((node) => { node.src = url; node.hidden = false; });
  }

  async function loadImage(path) {
    if (!state.photoFileId) return;
    if (!path) {
      $('image-status').hidden = false;
      $('image-status').textContent = 'У материала есть изображение Telegram. Предпросмотр в браузере недоступен.';
      $('image-empty').hidden = false;
      return;
    }
    const generation = state.imageGeneration;
    try {
      const url = new URL(path, location.origin);
      if (url.origin !== location.origin || !url.pathname.startsWith('/api/newsroom/')) throw new Error('Invalid image path');
      const response = await request(url.pathname.slice('/api/newsroom'.length));
      const blob = await response.blob();
      if (generation === state.imageGeneration) showImage(URL.createObjectURL(blob));
    } catch {
      if (generation !== state.imageGeneration) return;
      $('image-status').hidden = false;
      $('image-status').textContent = 'Обложка сохранена, но её не удалось показать. Обновите материал, чтобы повторить загрузку.';
      $('image-empty').hidden = false;
    }
  }

  function loadForm(post) {
    state.post = post;
    state.correction = false;
    state.commandId = crypto.randomUUID();
    fields.title.value = post?.title || '';
    fields.body.value = post?.body || '';
    fields.format.value = post ? (post.format === 'MARKDOWN' ? 'MARKDOWN' : 'PLAIN') : 'MARKDOWN';
    fields.kind.value = post?.kind || 'NEWS';
    fields.version.value = post?.releaseVersion || '';
    fields.telegram.checked = post ? post.publishTelegram : true;
    fields.launcher.checked = post ? post.publishLauncher : true;
    fields.audience.value = post?.audience || 'ALL';
    fields.changelog.checked = !!post?.changelog && post.kind === 'UPDATE' && post.publishLauncher && fields.audience.value === 'ALL';
    populateRecipients(post?.recipients || []);
    state.photoFileId = post?.photoFileId || null;
    state.imageUrl = post?.imageUrl || null;
    resetImage();
    state.base = snapshot();
    state.dirty = false;
    $('editor-title').textContent = post ? (post.status === 'DRAFT' ? 'Работа над материалом' : 'Опубликованный материал') : 'Начните с главного';
    if (post?.status === 'CANCELLED') $('editor-title').textContent = 'Удалённый черновик';
    $('editor-kicker').textContent = post ? 'Материал · ' + formatDate(post.createdAt) : 'Новый материал';
    $('post-status').textContent = post?.hidden ? 'Скрыто в лаунчере' : statusNames[post?.status] || 'Черновик';
    $('post-status').className = 'badge' + (post && ['QUEUED', 'COMPLETED'].includes(post.status) ? ' published' : post?.status === 'CANCELLED' ? ' cancelled' : '');
    renderStats();
    updatePreview();
    renderList();
    if (state.photoFileId) loadImage(state.imageUrl);
  }

  function renderList() {
    const drafts = state.posts.filter((post) => post.status === 'DRAFT');
    const history = state.posts.filter((post) => post.status !== 'DRAFT');
    $('draft-count').textContent = drafts.length;
    $('history-count').textContent = history.length;
    document.querySelectorAll('[data-filter]').forEach((node) => {
      const selected = node.dataset.filter === state.filter;
      node.classList.toggle('active', selected); node.setAttribute('aria-pressed', String(selected));
    });
    const list = $('post-list'); list.replaceChildren();
    const rows = state.filter === 'drafts' ? drafts : history;
    if (!rows.length) {
      const empty = document.createElement('p'); empty.className = 'post-empty';
      empty.textContent = state.filter === 'drafts' ? 'Пока нет черновиков. Создайте первый материал — его увидят только редакторы.' : 'Здесь появятся опубликованные материалы и удалённые черновики.';
      list.append(empty);
    }
    for (const post of rows) {
      const button = document.createElement('button');
      button.className = 'post-item' + (state.post?.id === post.id ? ' selected' : '');
      button.type = 'button';
      button.setAttribute('aria-current', state.post?.id === post.id ? 'true' : 'false');
      const title = document.createElement('strong'); title.textContent = post.title || 'Без заголовка';
      const meta = document.createElement('small');
      const dot = document.createElement('span'); dot.className = 'item-dot' + (['QUEUED', 'COMPLETED'].includes(post.status) ? ' published' : '');
      meta.append(dot, document.createTextNode((statusNames[post.status] || post.status) + ' · ' + formatDate(post.updatedAt || post.createdAt)));
      button.append(title, meta);
      button.addEventListener('click', () => openPost(post.id));
      button.disabled = state.busy || state.uploading;
      list.append(button);
    }
  }

  async function refreshList() {
    const posts = await api('/posts');
    if (!Array.isArray(posts)) throw new Error('Invalid posts');
    state.posts = posts;
    renderList();
  }

  function populateRecipients(selected) {
    const chosen = new Set(selected);
    fields.recipients.replaceChildren();
    const known = new Set();
    for (const recipient of state.recipients) {
      const option = document.createElement('option'); option.value = recipient.id;
      option.textContent = recipient.username; option.selected = chosen.has(recipient.id);
      fields.recipients.append(option); known.add(recipient.id);
    }
    for (const id of chosen) if (!known.has(id)) {
      const option = document.createElement('option'); option.value = id;
      option.textContent = 'Ранее выбранный аккаунт (сейчас недоступен)'; option.selected = true;
      fields.recipients.append(option);
    }
  }

  function afterDiscard(action) {
    if (!state.dirty) { action(); return; }
    state.pendingNavigation = action;
    state.confirmAction = 'discard';
    $('confirm-title').textContent = 'Есть несохранённые изменения';
    $('confirm-body').textContent = 'Текущий текст ещё не сохранён. Можно вернуться к нему или продолжить без сохранения.';
    $('confirm-warning').textContent = 'При переходе изменения в этом окне будут потеряны. Если текст нужен, вернитесь и сохраните черновик.';
    $('confirm-go').textContent = 'Продолжить без сохранения';
    $('confirm-details').replaceChildren();
    updateControls();
    $('confirm-dialog').showModal();
  }

  async function openPost(id, force = false) {
    if (state.busy || state.uploading) return;
    if (!force && state.dirty) { afterDiscard(() => openPost(id, true)); return; }
    state.busy = true; updateControls();
    try {
      const post = await api('/posts/' + encodeURIComponent(id));
      loadForm(post); notify('');
    } catch (error) { failure(error, 'Не удалось открыть материал.'); }
    finally { state.busy = false; updateControls(); }
  }

  async function save() {
    if (state.busy || state.uploading || state.expired || !editable()) return;
    const invalid = validation();
    if (invalid) { notify(invalid, 'error'); return; }
    if (state.correction) { confirmCorrection(); return; }
    state.busy = true; updateControls();
    const existing = state.post;
    try {
      const post = await api(existing ? '/posts/' + encodeURIComponent(existing.id) : '/posts', existing ? 'PUT' : 'POST', payload());
      loadForm(post);
      notify('Черновик сохранён. Игроки его ещё не видят.', 'success');
      try { await refreshList(); } catch { notify('Черновик сохранён. Список публикаций временно не обновился — нажмите ↻ в списке.', 'success'); }
    } catch (error) { failure(error, 'Не удалось подтвердить сохранение.'); }
    finally { state.busy = false; updateControls(); }
  }

  function renderStats() {
    const post = state.post;
    const published = !!post && ['QUEUED', 'COMPLETED'].includes(post.status);
    $('delivery-section').hidden = !published;
    if (!published) return;
    $('delivery-stats').replaceChildren();
    for (const [name, label] of [['sent', 'Доставлено'], ['pending', 'В очереди'], ['failed', 'Не доставлено'], ['uncertain', 'Нужно проверить']]) {
      const cell = document.createElement('div'); cell.className = 'stat';
      const number = document.createElement('strong'); number.textContent = count(post[name]);
      const caption = document.createElement('span'); caption.textContent = label;
      cell.append(number, caption); $('delivery-stats').append(cell);
    }
    $('delivery-note').textContent = !post.publishTelegram ? 'Telegram не выбран. Материал опубликован только в лаунчере.'
      : count(post.uncertain) > 0 ? 'Для части сообщений результат не подтверждён. Автоматической повторной отправки нет, чтобы не создавать дубликаты.'
      : 'Получатели зафиксированы при публикации с учётом их настроек уведомлений. Данные доставки можно обновить.';
  }

  function confirmPublish() {
    if (!state.post || state.dirty || state.busy || !editable() || state.correction) return;
    const invalid = validation();
    if (invalid) { notify(invalid, 'error'); return; }
    state.confirmAction = 'publish';
    $('confirm-title').textContent = 'Опубликовать материал?';
    $('confirm-body').textContent = state.post.title;
    $('confirm-warning').textContent = 'После публикации материал станет доступен игрокам. Отправленное сообщение нельзя отозвать этим редактором.';
    $('confirm-go').textContent = 'Опубликовать';
    $('confirm-details').replaceChildren();
    detail('Каналы', [state.post.publishTelegram ? 'Telegram' : null, state.post.publishLauncher ? 'Лаунчер' : null].filter(Boolean).join(' + '));
    detail('Тип', names[state.post.kind] || 'Новость');
    if (state.post.publishTelegram) detail('Аудитория', state.post.audience === 'SELF' ? 'Только вам — тест'
      : state.post.audience === 'SELECTED' ? 'Выбрано аккаунтов: ' + (state.post.recipients || []).length
      : 'до ' + count(state.post.eligible) + ' аккаунтов сейчас');
    if (state.post.publishTelegram) detail('Доставка', 'С учётом подписок игроков');
    if (state.post.releaseVersion) detail('Версия', state.post.releaseVersion);
    $('confirm-dialog').showModal();
  }

  function detail(label, value) {
    const row = document.createElement('div'); row.className = 'confirm-detail';
    const key = document.createElement('span'); key.textContent = label;
    const text = document.createElement('span'); text.textContent = value;
    row.append(key, text); $('confirm-details').append(row);
  }

  function confirmCancel() {
    if (!state.post || state.busy || !editable() || state.correction) return;
    state.confirmAction = 'cancel';
    $('confirm-title').textContent = 'Удалить черновик?';
    $('confirm-body').textContent = state.post.title;
    $('confirm-warning').textContent = 'Черновик останется в истории. Игрокам ничего не отправится. Несохранённые изменения будут потеряны.';
    $('confirm-go').textContent = 'Удалить черновик';
    $('confirm-details').replaceChildren();
    $('confirm-dialog').showModal();
  }

  function confirmVisibility() {
    if (!state.post || state.busy || state.expired) return;
    state.confirmAction = 'visibility';
    const hidden = state.post.hidden;
    $('confirm-title').textContent = hidden ? 'Вернуть материал в лаунчер?' : 'Скрыть материал в лаунчере?';
    $('confirm-body').textContent = state.post.title;
    $('confirm-warning').textContent = 'Изменится только видимость в ленте лаунчера. Уже отправленные сообщения Telegram останутся без изменений.';
    $('confirm-go').textContent = hidden ? 'Вернуть в ленту' : 'Скрыть в лаунчере';
    $('confirm-details').replaceChildren();
    $('confirm-dialog').showModal();
  }

  async function confirmMutation() {
    if (state.confirmAction === 'correction') { await applyCorrection(); return; }
    if (state.confirmAction === 'discard') {
      const navigation = state.pendingNavigation;
      state.pendingNavigation = null; state.confirmAction = null;
      $('confirm-dialog').close();
      if (navigation) navigation();
      return;
    }
    if (!state.post || !state.confirmAction || state.busy || state.expired) return;
    const action = state.confirmAction;
    const id = state.post.id;
    state.busy = true; updateControls();
    const hidden = !state.post.hidden;
    try {
      const post = await api('/posts/' + encodeURIComponent(id) + '/' + action, 'POST', action === 'visibility'
        ? { revision: state.post.revision, hidden } : { revision: state.post.revision });
      $('confirm-dialog').close(); state.confirmAction = null;
      loadForm(post); state.filter = 'history';
      notify(action === 'publish' ? 'Материал опубликован. Статистика доставки обновляется ниже.'
        : action === 'visibility' ? (post.hidden ? 'Материал скрыт в лаунчере. Сообщения Telegram не изменены.' : 'Материал снова виден в лаунчере.')
        : 'Черновик удалён. Игрокам ничего не отправлено.', 'success');
      try { await refreshList(); } catch { /* The saved result remains authoritative. */ }
    } catch (error) {
      $('confirm-dialog').close(); state.confirmAction = null;
      failure(error, 'Не удалось подтвердить результат.');
      // Read only after uncertainty. Never blindly repeat the publication request.
      try {
        const current = await api('/posts/' + encodeURIComponent(id));
        if (current.status !== 'DRAFT' && (action !== 'visibility' || current.hidden === hidden)) {
          loadForm(current); state.filter = 'history'; renderList();
          notify(action === 'visibility' ? (current.hidden ? 'Проверили состояние: материал скрыт в лаунчере.' : 'Проверили состояние: материал виден в лаунчере.')
            : current.status === 'CANCELLED' ? 'Проверили состояние: черновик удалён.' : 'Проверили состояние: материал уже опубликован.', 'success');
        }
      } catch { /* Keep the failure and allow an explicit read-only refresh. */ }
    } finally { state.busy = false; updateControls(); }
  }

  async function upload(file) {
    if (!file || state.busy || state.uploading || !editable() || state.expired) return;
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 8 * 1024 * 1024 || file.size === 0) {
      notify('Выберите PNG или JPEG размером до 8 МБ.', 'error'); return;
    }
    state.uploading = true; updateControls();
    try {
      const response = await request('/media', { method: 'POST', headers: { 'Content-Type': file.type }, body: file });
      const data = await response.json();
      if (typeof data.photoFileId !== 'string' || !data.photoFileId.startsWith('upload_')) throw new Error('Invalid media response');
      state.photoFileId = data.photoFileId; state.imageUrl = data.imageUrl || null;
      state.imageGeneration++;
      showImage(URL.createObjectURL(file)); updatePreview();
      notify('Обложка загружена. Сохраните черновик, чтобы прикрепить её к материалу.', 'success');
    } catch (error) { failure(error, 'Не удалось загрузить изображение.'); }
    finally { state.uploading = false; $('image-file').value = ''; updateControls(); }
  }

  async function start() {
    const ticket = new URLSearchParams(location.hash.slice(1)).get('ticket');
    // One-time credentials must never remain in browser history, referrals or storage.
    history.replaceState(null, '', location.pathname + location.search);
    if (!ticket) {
      $('gate-status').textContent = 'Откройте редактор через админский раздел бота. Ссылка персональная и действует ограниченное время.';
      $('gate-link').hidden = false; return;
    }
    try {
      const session = await api('/session', 'POST', { ticket }, true);
      if (typeof session.token !== 'string' || !session.expiresAt) throw new Error('Invalid session');
      state.token = session.token; state.expiresAt = session.expiresAt;
      $('session-label').textContent = 'Сеанс до ' + formatDate(session.expiresAt);
      $('gate').hidden = true; $('workspace').hidden = false; $('exit-button').hidden = false;
      try {
        await refreshList();
        try { state.recipients = await api('/recipients'); } catch { state.recipients = []; }
        const draft = state.posts.find((post) => post.status === 'DRAFT');
        if (draft) await openPost(draft.id, true); else loadForm(null);
      } catch (error) { loadForm(null); failure(error, 'Не удалось загрузить список материалов.'); }
    } catch {
      $('gate-status').textContent = 'Ссылка уже использована, истекла или у аккаунта нет доступа. Получите новую ссылку в админском разделе бота.';
      $('gate-link').hidden = false;
    }
  }

  $('post-form').addEventListener('submit', (event) => { event.preventDefault(); save(); });
  Object.values(fields).forEach((field) => field.addEventListener('input', updatePreview));
  fields.kind.addEventListener('change', () => {
    if (!state.post && fields.kind.value === 'UPDATE' && fields.launcher.checked && fields.audience.value === 'ALL') fields.changelog.checked = true;
    updatePreview();
  });
  fields.audience.addEventListener('change', () => {
    if (fields.audience.value !== 'ALL') { fields.launcher.checked = false; fields.telegram.checked = true; }
    updatePreview();
  });
  $('new-post').addEventListener('click', () => {
    afterDiscard(() => {
      loadForm(null); state.filter = 'drafts'; renderList(); notify(''); fields.title.focus();
    });
  });
  $('reload-list').addEventListener('click', async () => {
    try { await refreshList(); notify('Список публикаций обновлён.'); }
    catch (error) { failure(error, 'Не удалось обновить список.'); }
  });
  $('reload-post').addEventListener('click', () => {
    if (state.post) afterDiscard(() => openPost(state.post.id, true));
  });
  $('save-post').addEventListener('click', save);
  $('edit-correction').addEventListener('click', startCorrection);
  $('cancel-correction').addEventListener('click', () => afterDiscard(() => { loadForm(state.post); notify('Исправления отменены. Опубликованный материал не изменён.'); }));
  document.querySelectorAll('[data-markdown]').forEach((button) => button.addEventListener('click', () => insertMarkdown(button.dataset.markdown)));
  $('markdown-file').addEventListener('change', () => {
    const file = $('markdown-file').files[0]; $('markdown-file').value = ''; importMarkdown(file);
  });
  $('markdown-export').addEventListener('click', exportMarkdown);
  $('publish-post').addEventListener('click', confirmPublish);
  $('cancel-post').addEventListener('click', confirmCancel);
  $('toggle-visibility').addEventListener('click', confirmVisibility);
  $('confirm-back').addEventListener('click', () => { $('confirm-dialog').close(); state.confirmAction = null; state.pendingNavigation = null; });
  $('confirm-go').addEventListener('click', confirmMutation);
  $('confirm-dialog').addEventListener('cancel', (event) => { if (state.busy) { event.preventDefault(); return; } state.confirmAction = null; state.pendingNavigation = null; });
  $('refresh-delivery').addEventListener('click', () => state.post && openPost(state.post.id));
  $('image-file').addEventListener('change', () => upload($('image-file').files[0]));
  $('remove-image').addEventListener('click', () => {
    if (state.busy || !editable() || state.correction) return;
    state.photoFileId = null; state.imageUrl = null; resetImage(); updatePreview();
  });
  $('exit-button').addEventListener('click', () => {
    afterDiscard(() => {
      state.token = null; state.expired = true; state.dirty = false; resetImage();
      $('workspace').hidden = true; $('gate').hidden = false; $('exit-button').hidden = true;
      $('session-label').textContent = 'Сеанс завершён';
      $('gate-status').textContent = 'Вы вышли из редактора. Для следующего входа откройте свежую ссылку из бота.';
      $('gate-link').hidden = false;
    });
  });
  document.querySelectorAll('[data-filter]').forEach((button) => button.addEventListener('click', () => {
    state.filter = button.dataset.filter; renderList();
  }));
  document.querySelectorAll('[data-preview]').forEach((button) => button.addEventListener('click', () => {
    selectPreview(button.dataset.preview);
  }));
  window.addEventListener('beforeunload', (event) => {
    if (state.dirty || state.busy || state.uploading) { event.preventDefault(); event.returnValue = ''; }
  });
  document.addEventListener('keydown', (event) => {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 's' && state.token) { event.preventDefault(); save(); }
    if ((event.metaKey || event.ctrlKey) && document.activeElement === fields.body && fields.format.value === 'MARKDOWN') {
      if (event.key.toLowerCase() === 'b') { event.preventDefault(); insertMarkdown('bold'); }
      if (event.key.toLowerCase() === 'i') { event.preventDefault(); insertMarkdown('italic'); }
    }
  });
  setInterval(async () => {
    if (!state.token || state.expired) return;
    if (new Date(state.expiresAt).getTime() <= Date.now()) { expireSession(); return; }
    if (state.post?.status === 'QUEUED' && !state.busy && !state.dirty && !document.hidden) {
      try {
        const post = await api('/posts/' + encodeURIComponent(state.post.id));
        state.post = post; renderStats();
      } catch { /* Manual refresh remains available; no writes are retried. */ }
    }
  }, 5000);
  start();
})();
