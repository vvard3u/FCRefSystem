const titles = {
  invitations: ['UC-01', 'Создание реферального приглашения'],
  regulation: ['UC-02', 'Создание регламента отбора'],
  voting: ['UC-03', 'Голосование по кандидату'],
  blocking: ['UC-04', 'Блокировка кандидата'],
  stage: ['UC-05', 'Прохождение этапа отбора'],
  journal: ['FR-018', 'Журнал событий']
};

const roleLabels = {
  ADMIN: 'Администратор',
  MEMBER: 'Участник клуба',
  PRIVILEGED_MEMBER: 'Привилегированный участник',
  INTERVIEWER: 'Интервьюер',
  CANDIDATE: 'Кандидат',
  MODERATOR: 'Модератор'
};

const statusLabels = {
  REGISTERED: 'Зарегистрирован',
  IN_PROGRESS: 'В процессе',
  VOTING: 'Голосование',
  PASSED: 'Прошел',
  FAILED: 'Не прошел',
  BLOCKED: 'Заблокирован',
  ACTIVE: 'Активно',
  ACTIVATED: 'Активировано',
  CANCELLED: 'Отменено',
  EXPIRED: 'Истекло',
  AVAILABLE: 'Доступен',
  WAITING: 'Ожидает',
  SUBMITTED: 'Отправлен',
  RETRY: 'Повтор',
  OPEN: 'Открыто',
  CLOSED: 'Закрыто'
};

const eventLabels = {
  INVITATION_CREATED: 'Создание приглашения',
  INVITATION_ACTIVATED: 'Активация приглашения',
  CANDIDATE_REGISTERED: 'Регистрация кандидата',
  REGULATION_CHANGED: 'Изменение регламента',
  STAGE_ASSIGNED: 'Назначение этапа',
  STAGE_RESULT_SUBMITTED: 'Отправка результата',
  VERDICT_RECORDED: 'Фиксация вердикта',
  VOTE_OPENED: 'Открытие голосования',
  VOTE_CAST: 'Подача голоса',
  VOTE_CLOSED: 'Закрытие голосования',
  COMPLAINT_CREATED: 'Жалоба',
  CANDIDATE_BLOCKED: 'Блокировка',
  CANDIDATE_UNBLOCKED: 'Снятие блокировки',
  ROLE_ASSIGNED: 'Назначение роли',
  ROLE_REVOKED: 'Отзыв роли'
};

const state = {
  snapshot: null,
  session: null,
  auth: localStorage.getItem('fcRefBasicAuth') || '',
  view: 'invitations',
  voteChoice: 'SUPPORT',
  issuedCredentials: null,
  selectedCandidates: {
    voting: 'candidate-vote',
    blocking: 'candidate-block',
    stage: 'candidate-stage'
  }
};

const app = document.querySelector('#app');
const authPanel = document.querySelector('#auth-panel');
const viewTitle = document.querySelector('#view-title');
const viewKicker = document.querySelector('#view-kicker');
const toast = document.querySelector('#toast');

document.querySelectorAll('[data-view]').forEach((button) => {
  button.addEventListener('click', () => {
    state.view = button.dataset.view;
    document.querySelectorAll('[data-view]').forEach((item) => item.classList.toggle('is-active', item === button));
    render();
  });
});

load();

async function load() {
  if (!state.auth) {
    render();
    return;
  }
  try {
    await loadAuthenticatedState();
  } catch (error) {
    clearAuth();
    render();
    showToast(error.message || 'Не удалось войти');
  }
}

async function loadAuthenticatedState() {
  state.session = await request('/api/session');
  state.snapshot = await request('/api/snapshot');
  normalizeCandidateSelection();
  render();
}

async function request(path, options = {}) {
  const { publicRequest = false, headers = {}, ...fetchOptions } = options;
  const requestHeaders = { 'Content-Type': 'application/json', ...headers };
  if (state.auth && !publicRequest) {
    requestHeaders.Authorization = state.auth;
  }
  const response = await fetch(path, {
    headers: requestHeaders,
    ...fetchOptions
  });
  if (!response.ok) {
    if (response.status === 401 && !publicRequest) {
      clearAuth();
      render();
    }
    let error = { message: response.statusText };
    try {
      error = await response.json();
    } catch (ignored) {
      error = { message: response.statusText };
    }
    throw error;
  }
  return response.json();
}

async function mutate(path, body, label) {
  try {
    await request(path, {
      method: 'POST',
      body: JSON.stringify(body)
    });
    await loadAuthenticatedState();
    showToast(label);
  } catch (error) {
    showToast(error.message || 'Операция не выполнена');
  }
}

function render() {
  renderAuthPanel();
  if (!state.session || !state.snapshot) {
    viewKicker.textContent = 'UC-01';
    viewTitle.textContent = 'Активация приглашения';
    app.innerHTML = renderPublicActivation();
    wireView();
    return;
  }

  const [kicker, title] = titles[state.view];
  viewKicker.textContent = kicker;
  viewTitle.textContent = title;
  app.innerHTML = {
    invitations: renderInvitations,
    regulation: renderRegulation,
    voting: renderVoting,
    blocking: renderBlocking,
    stage: renderStage,
    journal: renderJournal
  }[state.view]();
  wireView();
}

function renderAuthPanel() {
  if (state.session) {
    authPanel.innerHTML = `
      <div class="user-chip">
        <div>
          <strong>${escapeHtml(state.session.displayName)}</strong>
          <span>${escapeHtml(roleList(state.session.roles))}</span>
        </div>
        <button class="btn" type="button" id="logout-button">Выйти</button>
      </div>
    `;
  } else {
    authPanel.innerHTML = `
      <form id="login-form" class="login-form">
        <input name="username" value="member" autocomplete="username" aria-label="Логин">
        <input name="password" value="member" type="password" autocomplete="current-password" aria-label="Пароль">
        <button class="btn btn--primary" type="submit">Войти</button>
      </form>
    `;
  }
  wireAuthPanel();
}

function wireAuthPanel() {
  const loginForm = document.querySelector('#login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = new FormData(loginForm);
      setAuth(form.get('username'), form.get('password'));
      try {
        await loadAuthenticatedState();
        showToast('Вход выполнен');
      } catch (error) {
        clearAuth();
        render();
        showToast(error.message || 'Неверный логин или пароль');
      }
    });
  }

  const logoutButton = document.querySelector('#logout-button');
  if (logoutButton) {
    logoutButton.addEventListener('click', () => {
      clearAuth();
      state.snapshot = null;
      state.session = null;
      render();
    });
  }
}

function renderPublicActivation() {
  return `
    <section class="grid grid--two">
      ${renderActivationPanel()}
      <div class="panel">
        <h2>Доступ к рабочим сценариям</h2>
        <div class="placeholder">
          <strong>Требуется вход</strong>
          <p class="muted">После входа система откроет действия, доступные роли текущего пользователя.</p>
        </div>
      </div>
    </section>
  `;
}

function renderInvitations() {
  const actorInvitations = state.snapshot.invitations.filter((invitation) => invitation.authorUserId === state.session.id);
  const activeCount = actorInvitations.filter((invitation) => invitation.status === 'ACTIVE').length;
  const quota = Math.max(0, 3 - activeCount);
  const createPanel = hasRole('MEMBER') ? `
    <div class="panel">
      <div class="status-strip">
        <div class="status-item"><span>Доступная квота</span><strong>${quota}</strong></div>
        <div class="status-item"><span>Активные</span><strong>${activeCount}</strong></div>
        <div class="status-item"><span>Всего</span><strong>${actorInvitations.length}</strong></div>
      </div>
      <form id="create-invitation-form">
        <label class="field">
          <span>Комментарий</span>
          <textarea name="comment">Кандидат рекомендован действующим участником клуба</textarea>
        </label>
        <div class="actions">
          <button class="btn btn--primary" type="submit">Создать приглашение</button>
          <a class="btn" href="/openapi.yaml" target="_blank" rel="noreferrer">OpenAPI</a>
        </div>
      </form>
    </div>
  ` : `
    <div class="panel">
      <h2>Создание приглашения</h2>
      ${placeholder('Недоступно для текущей роли', 'Приглашение может создать только действующий участник клуба.')}
    </div>
  `;

  return `
    <section class="grid grid--two">
      ${createPanel}
      ${renderActivationPanel()}
    </section>
    <section class="panel">
      <h2>Приглашения</h2>
      ${table(['Токен', 'Статус', 'Автор', 'Создано', 'Срок'], state.snapshot.invitations.map((invitation) => [
        escapeHtml(invitation.token),
        badge(invitation.status),
        escapeHtml(userName(invitation.authorUserId)),
        escapeHtml(formatDate(invitation.createdAt)),
        escapeHtml(formatDate(invitation.expiresAt))
      ]))}
    </section>
  `;
}

function renderActivationPanel() {
  return `
    <div class="panel">
      <h2>Активация приглашения</h2>
      <form id="activate-invitation-form">
        <label class="field">
          <span>Токен</span>
          <input name="token" value="${escapeHtml(nextActiveToken())}">
        </label>
        <label class="field">
          <span>ФИО кандидата</span>
          <input name="fullName" value="Новый Кандидат">
        </label>
        <button class="btn btn--good" type="submit">Активировать</button>
      </form>
      ${state.issuedCredentials ? `
        <div class="credential-card">
          <span>Учетная запись кандидата</span>
          <strong>${escapeHtml(state.issuedCredentials.username)}</strong>
          <code>${escapeHtml(state.issuedCredentials.password)}</code>
        </div>
      ` : ''}
    </div>
  `;
}

function renderRegulation() {
  const regulation = activeRegulation();
  if (!hasRole('ADMIN')) {
    return `
      <section class="grid">
        <div class="panel">
          <div class="status-strip">
            <div class="status-item"><span>Активный регламент</span><strong>${escapeHtml(regulation.name)}</strong></div>
            <div class="status-item"><span>Этапов</span><strong>${regulation.stages.length}</strong></div>
            <div class="status-item"><span>Изменил</span><strong>${escapeHtml(userName(regulation.createdByUserId))}</strong></div>
          </div>
          ${placeholder('Редактирование недоступно', 'Регламент отбора изменяет администратор.')}
          ${renderStageList(regulation.stages)}
        </div>
      </section>
    `;
  }

  return `
    <section class="grid">
      <div class="panel">
        <div class="status-strip">
          <div class="status-item"><span>Активный регламент</span><strong>${escapeHtml(regulation.name)}</strong></div>
          <div class="status-item"><span>Этапов</span><strong>${regulation.stages.length}</strong></div>
          <div class="status-item"><span>Изменил</span><strong>${escapeHtml(userName(regulation.createdByUserId))}</strong></div>
        </div>
        <form id="regulation-form">
          <label class="field">
            <span>Название</span>
            <input name="name" value="${escapeHtml(regulation.name)}">
          </label>
          <label class="field">
            <span>Описание</span>
            <textarea name="description">${escapeHtml(regulation.description)}</textarea>
          </label>
          <div class="stage-list">
            ${regulation.stages.map((stage) => stageEditor(stage)).join('')}
          </div>
          <div class="actions">
            <button class="btn btn--primary" type="submit">Сохранить регламент</button>
          </div>
        </form>
      </div>
    </section>
  `;
}

function renderVoting() {
  const candidate = selectedCandidate('voting');
  const current = currentStage(candidate);
  const session = candidate.votingSessions.find((item) => item.status === 'OPEN') || candidate.votingSessions.at(-1);
  const votePanel = hasRole('PRIVILEGED_MEMBER') ? `
    <div class="panel">
      <h2>Голос</h2>
      <form id="vote-form">
        <div class="vote-options">
          <button type="button" class="choice ${state.voteChoice === 'SUPPORT' ? 'is-selected' : ''}" data-choice="SUPPORT">
            Поддержать
          </button>
          <button type="button" class="choice ${state.voteChoice === 'REJECT' ? 'is-selected' : ''}" data-choice="REJECT">
            Отклонить
          </button>
        </div>
        <label class="field">
          <span>Пояснение</span>
          <textarea name="reason">Решение принято по материалам кандидата</textarea>
        </label>
        <button class="btn btn--primary" type="submit">Отправить голос</button>
      </form>
    </div>
  ` : `
    <div class="panel">
      <h2>Голос</h2>
      ${placeholder('Голосование недоступно', 'Голос может подать только привилегированный участник.')}
    </div>
  `;

  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('voting')}
        ${candidateStatus(candidate)}
        <div class="band">
          <h3>${escapeHtml(candidate.fullName)}</h3>
          <p class="muted">Текущий этап: ${escapeHtml(current?.stageName || 'не назначен')}</p>
          <p class="muted">Голосов: ${session ? session.votes.length : 0}</p>
        </div>
        ${hasRole('ADMIN') ? `
          <div class="actions toolbar">
            <button class="btn" type="button" id="open-vote">Открыть голосование</button>
            <button class="btn" type="button" id="close-vote">Закрыть голосование</button>
          </div>
        ` : ''}
      </div>
      ${votePanel}
    </section>
    <section class="panel">
      <h2>Жалоба</h2>
      ${hasRole('PRIVILEGED_MEMBER') ? `
        <form id="complaint-form" class="actions">
          <input name="reason" value="Требуется дополнительная проверка" aria-label="Причина жалобы">
          <button class="btn" type="submit">Подать жалобу</button>
        </form>
      ` : placeholder('Недоступно для текущей роли', 'Жалобу может подать привилегированный участник.')}
    </section>
  `;
}

function renderBlocking() {
  const candidate = selectedCandidate('blocking');
  const canBlock = hasRole('INTERVIEWER') || hasRole('ADMIN');
  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('blocking')}
        ${candidateStatus(candidate)}
      </div>
      <div class="panel">
        <h2>Блокировка</h2>
        ${canBlock ? `
          <form id="block-form">
            <label class="field">
              <span>Категория</span>
              <input name="category" value="Нарушение правил сообщества">
            </label>
            <label class="field">
              <span>Причина</span>
              <textarea name="reason">Основание зафиксировано интервьюером</textarea>
            </label>
            <div class="actions">
              <button class="btn btn--danger" type="submit">Заблокировать</button>
              ${hasRole('ADMIN') ? '<button class="btn" id="unblock-candidate" type="button">Снять блокировку</button>' : ''}
            </div>
          </form>
        ` : placeholder('Блокировка недоступна', 'Кандидата блокирует интервьюер или администратор.')}
      </div>
    </section>
  `;
}

function renderStage() {
  const candidate = selectedCandidate('stage');
  const current = currentStage(candidate);
  const canSubmit = hasRole('ADMIN') || (hasRole('CANDIDATE') && candidate.candidateUserId === state.session.id);
  const canVerdict = hasRole('INTERVIEWER') || hasRole('ADMIN');
  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('stage')}
        ${candidateStatus(candidate)}
        ${canSubmit ? `
          <form id="stage-result-form">
            <label class="field">
              <span>Результат</span>
              <textarea name="result">Результат этапа подготовлен и отправлен через систему</textarea>
            </label>
            <button class="btn btn--primary" type="submit">Отправить результат</button>
          </form>
        ` : placeholder('Отправка результата недоступна', 'Результат этапа отправляет кандидат или администратор.')}
      </div>
      <div class="panel">
        <h2>Вердикт</h2>
        ${canVerdict ? `
          <form id="verdict-form">
            <label class="field">
              <span>Решение</span>
              <select name="verdict">
                <option value="PASSED">Passed</option>
                <option value="FAILED">Failed</option>
                <option value="RETRY">Retry</option>
              </select>
            </label>
            <label class="field">
              <span>Отчет</span>
              <textarea name="report">Результат проверен, решение зафиксировано</textarea>
            </label>
            <button class="btn" type="submit">Зафиксировать вердикт</button>
          </form>
        ` : placeholder('Вердикт недоступен', 'Вердикт фиксирует интервьюер или администратор.')}
        <div class="band">
          <strong>${escapeHtml(current?.stageName || 'Этап не назначен')}</strong>
          <p class="muted">Попытка: ${current?.attemptNumber || 0} из ${current?.attemptLimit || 0}</p>
        </div>
      </div>
    </section>
  `;
}

function renderJournal() {
  return `
    <section class="panel">
      <h2>События</h2>
      <div class="event-list">
        ${state.snapshot.events.map((event) => `
          <article class="event">
            <strong>${escapeHtml(formatDate(event.occurredAt))}</strong>
            <span>${escapeHtml(eventLabels[event.type] || event.type)}</span>
            <span class="muted">${escapeHtml(userName(event.actorUserId) || 'Система')}</span>
          </article>
        `).join('')}
      </div>
    </section>
  `;
}

function wireView() {
  const createInvitationForm = document.querySelector('#create-invitation-form');
  if (createInvitationForm) {
    createInvitationForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(createInvitationForm);
      mutate('/api/invitations', {
        comment: form.get('comment'),
        requestId: crypto.randomUUID()
      }, 'Приглашение создано');
    });
  }

  const activateForm = document.querySelector('#activate-invitation-form');
  if (activateForm) {
    activateForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = new FormData(activateForm);
      try {
        state.issuedCredentials = await request('/api/invitations/activate', {
          method: 'POST',
          body: JSON.stringify({
            token: form.get('token'),
            fullName: form.get('fullName')
          }),
          publicRequest: true
        });
        if (state.session) {
          state.snapshot = await request('/api/snapshot');
          normalizeCandidateSelection();
        }
        render();
        showToast('Кандидат зарегистрирован');
      } catch (error) {
        showToast(error.message || 'Активация не выполнена');
      }
    });
  }

  const regulationForm = document.querySelector('#regulation-form');
  if (regulationForm) {
    regulationForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(regulationForm);
      mutate('/api/regulations', {
        name: form.get('name'),
        description: form.get('description'),
        stages: readStages()
      }, 'Регламент сохранен');
    });
  }

  document.querySelectorAll('[data-candidate-select]').forEach((select) => {
    select.addEventListener('change', () => {
      state.selectedCandidates[select.dataset.candidateSelect] = select.value;
      render();
    });
  });

  document.querySelectorAll('[data-choice]').forEach((button) => {
    button.addEventListener('click', () => {
      state.voteChoice = button.dataset.choice;
      render();
    });
  });

  const voteForm = document.querySelector('#vote-form');
  if (voteForm) {
    voteForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(voteForm);
      mutate(`/api/candidates/${selectedCandidate('voting').id}/votes`, {
        choice: state.voteChoice,
        reason: form.get('reason')
      }, 'Голос принят');
    });
  }

  const openVote = document.querySelector('#open-vote');
  if (openVote) {
    openVote.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('voting').id}/voting`, {}, 'Голосование открыто');
    });
  }

  const closeVote = document.querySelector('#close-vote');
  if (closeVote) {
    closeVote.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('voting').id}/voting/close`, {}, 'Голосование закрыто');
    });
  }

  const complaintForm = document.querySelector('#complaint-form');
  if (complaintForm) {
    complaintForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(complaintForm);
      mutate(`/api/candidates/${selectedCandidate('voting').id}/complaints`, {
        reason: form.get('reason')
      }, 'Жалоба сохранена');
    });
  }

  const blockForm = document.querySelector('#block-form');
  if (blockForm) {
    blockForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(blockForm);
      mutate(`/api/candidates/${selectedCandidate('blocking').id}/blocks`, {
        category: form.get('category'),
        reason: form.get('reason')
      }, 'Кандидат заблокирован');
    });
  }

  const unblock = document.querySelector('#unblock-candidate');
  if (unblock) {
    unblock.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('blocking').id}/unblock`, {
        reason: 'Основание снятия блокировки зафиксировано администратором'
      }, 'Блокировка снята');
    });
  }

  const stageForm = document.querySelector('#stage-result-form');
  if (stageForm) {
    stageForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(stageForm);
      mutate(`/api/candidates/${selectedCandidate('stage').id}/stage-results`, {
        result: form.get('result'),
        requestId: crypto.randomUUID()
      }, 'Результат отправлен');
    });
  }

  const verdictForm = document.querySelector('#verdict-form');
  if (verdictForm) {
    verdictForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(verdictForm);
      mutate(`/api/candidates/${selectedCandidate('stage').id}/verdicts`, {
        verdict: form.get('verdict'),
        report: form.get('report')
      }, 'Вердикт сохранен');
    });
  }
}

function setAuth(username, password) {
  state.auth = `Basic ${btoa(`${username}:${password}`)}`;
  localStorage.setItem('fcRefBasicAuth', state.auth);
}

function clearAuth() {
  state.auth = '';
  localStorage.removeItem('fcRefBasicAuth');
}

function hasRole(role) {
  return state.session?.roles?.includes(role) || false;
}

function roleList(roles) {
  return roles.map((role) => roleLabels[role] || role).join(', ');
}

function normalizeCandidateSelection() {
  Object.keys(state.selectedCandidates).forEach((view) => {
    const options = candidatesForView(view);
    if (!options.some((candidate) => candidate.id === state.selectedCandidates[view]) && options.length > 0) {
      state.selectedCandidates[view] = options[0].id;
    }
  });
}

function candidatesForView(view) {
  if (view === 'stage' && hasRole('CANDIDATE') && !hasRole('ADMIN')) {
    const ownedCandidates = state.snapshot.candidates.filter((candidate) => candidate.candidateUserId === state.session.id);
    return ownedCandidates.length > 0 ? ownedCandidates : [];
  }
  return state.snapshot.candidates;
}

function selectedCandidate(view) {
  const options = candidatesForView(view);
  const id = state.selectedCandidates[view];
  return options.find((candidate) => candidate.id === id) || options[0] || state.snapshot.candidates[0];
}

function candidatePicker(view) {
  const options = candidatesForView(view);
  return `
    <label class="field">
      <span>Кандидат</span>
      <select data-candidate-select="${view}">
        ${options.map((candidate) => `
          <option value="${escapeHtml(candidate.id)}" ${candidate.id === selectedCandidate(view).id ? 'selected' : ''}>
            ${escapeHtml(candidate.fullName)}
          </option>
        `).join('')}
      </select>
    </label>
  `;
}

function candidateStatus(candidate) {
  const current = currentStage(candidate);
  const nextAction = nextActionFor(candidate, current);
  return `
    <div class="status-strip">
      <div class="status-item"><span>Статус</span><strong>${badge(candidate.status)}</strong></div>
      <div class="status-item"><span>Этап</span><strong>${escapeHtml(current?.stageName || 'нет')}</strong></div>
      <div class="status-item"><span>Действие</span><strong>${escapeHtml(nextAction)}</strong></div>
    </div>
  `;
}

function nextActionFor(candidate, current) {
  if (candidate.status === 'BLOCKED') return 'Остановлен';
  if (candidate.status === 'PASSED') return 'Завершен';
  if (candidate.status === 'FAILED') return 'Завершен';
  if (!current) return 'Назначить этап';
  if (candidate.status === 'VOTING') return 'Голосование';
  if (current.state === 'SUBMITTED') return 'Вердикт';
  return 'Отправка результата';
}

function currentStage(candidate) {
  return candidate.stages.find((stage) => stage.stageId === candidate.currentStageId);
}

function activeRegulation() {
  return state.snapshot.regulations.find((regulation) => regulation.active) || state.snapshot.regulations[0];
}

function readStages() {
  return Array.from(document.querySelectorAll('[data-stage-row]')).map((row) => ({
    id: row.querySelector('[name="stageId"]').value,
    name: row.querySelector('[name="stageName"]').value,
    type: row.querySelector('[name="stageType"]').value,
    attemptLimit: Number(row.querySelector('[name="attemptLimit"]').value),
    dueDays: Number(row.querySelector('[name="dueDays"]').value),
    thresholdPercent: row.querySelector('[name="thresholdPercent"]').value
      ? Number(row.querySelector('[name="thresholdPercent"]').value)
      : null,
    criteria: row.querySelector('[name="criteria"]').value,
    requiresSubmission: row.querySelector('[name="requiresSubmission"]').checked
  }));
}

function stageEditor(stage) {
  return `
    <div class="stage-row" data-stage-row>
      <input name="stageName" value="${escapeHtml(stage.name)}" aria-label="Название этапа">
      <select name="stageType" aria-label="Тип этапа">
        ${['FORM', 'TASK', 'INTERVIEW', 'VOTE'].map((type) => `<option value="${type}" ${stage.type === type ? 'selected' : ''}>${type}</option>`).join('')}
      </select>
      <input name="attemptLimit" type="number" min="1" value="${stage.attemptLimit}" aria-label="Лимит попыток">
      <input name="dueDays" type="number" min="1" value="${stage.dueDays}" aria-label="Срок">
      <input name="thresholdPercent" type="number" min="1" max="100" value="${stage.thresholdPercent ?? ''}" aria-label="Порог">
      <input name="criteria" value="${escapeHtml(stage.criteria || '')}" aria-label="Критерии">
      <label><input name="requiresSubmission" type="checkbox" ${stage.requiresSubmission ? 'checked' : ''}> Результат</label>
      <input name="stageId" type="hidden" value="${escapeHtml(stage.id)}">
    </div>
  `;
}

function renderStageList(stages) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr><th>Этап</th><th>Тип</th><th>Попытки</th><th>Срок</th><th>Порог</th></tr></thead>
        <tbody>
          ${stages.map((stage) => `
            <tr>
              <td>${escapeHtml(stage.name)}</td>
              <td>${escapeHtml(stage.type)}</td>
              <td>${stage.attemptLimit}</td>
              <td>${stage.dueDays}</td>
              <td>${stage.thresholdPercent ?? ''}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function nextActiveToken() {
  if (!state.snapshot) {
    return 'bk-seed-active';
  }
  return state.snapshot.invitations.find((invitation) => invitation.status === 'ACTIVE')?.token || '';
}

function placeholder(title, text) {
  return `
    <div class="placeholder">
      <strong>${escapeHtml(title)}</strong>
      <p class="muted">${escapeHtml(text)}</p>
    </div>
  `;
}

function table(headers, rows) {
  return `
    <div class="table-wrap">
      <table>
        <thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead>
        <tbody>
          ${rows.map((row) => `<tr>${row.map((cell) => `<td>${cell}</td>`).join('')}</tr>`).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function badge(value) {
  const label = statusLabels[value] || value;
  let modifier = '';
  if (['ACTIVE', 'PASSED', 'OPEN', 'AVAILABLE'].includes(value)) modifier = ' badge--green';
  if (['BLOCKED', 'FAILED', 'EXPIRED'].includes(value)) modifier = ' badge--red';
  if (['VOTING', 'SUBMITTED', 'RETRY'].includes(value)) modifier = ' badge--amber';
  return `<span class="badge${modifier}">${escapeHtml(label)}</span>`;
}

function userName(userId) {
  if (!userId) return '';
  return state.snapshot.users.find((user) => user.id === userId)?.displayName || userId;
}

function formatDate(value) {
  if (!value) return '';
  return new Intl.DateTimeFormat('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add('is-visible');
  window.clearTimeout(showToast.timeout);
  showToast.timeout = window.setTimeout(() => toast.classList.remove('is-visible'), 2600);
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}
