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
  MEMBER: 'Участник',
  PRIVILEGED_MEMBER: 'Участник с привилегиями',
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
  view: 'invitations',
  actorUserId: 'member-1',
  voteChoice: 'SUPPORT',
  selectedCandidates: {
    voting: 'candidate-vote',
    blocking: 'candidate-block',
    stage: 'candidate-stage'
  }
};

const app = document.querySelector('#app');
const actorSelect = document.querySelector('#actor-select');
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

actorSelect.addEventListener('change', () => {
  state.actorUserId = actorSelect.value;
});

load();

async function load() {
  state.snapshot = await request('/api/snapshot');
  renderActorSelect();
  render();
}

async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  });
  if (!response.ok) {
    let error = { message: response.statusText };
    try {
      error = await response.json();
    } catch (ignored) {
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
    state.snapshot = await request('/api/snapshot');
    renderActorSelect();
    render();
    showToast(label);
  } catch (error) {
    showToast(error.message || 'Операция не выполнена');
  }
}

function renderActorSelect() {
  const previous = state.actorUserId;
  actorSelect.innerHTML = state.snapshot.users.map((user) => {
    const roles = user.roles.map((role) => roleLabels[role] || role).join(', ');
    return `<option value="${escapeHtml(user.id)}">${escapeHtml(user.displayName)} - ${escapeHtml(roles)}</option>`;
  }).join('');
  state.actorUserId = state.snapshot.users.some((user) => user.id === previous) ? previous : state.snapshot.users[0].id;
  actorSelect.value = state.actorUserId;
}

function render() {
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

function renderInvitations() {
  const actorInvitations = state.snapshot.invitations.filter((invitation) => invitation.authorUserId === state.actorUserId);
  const activeCount = actorInvitations.filter((invitation) => invitation.status === 'ACTIVE').length;
  const quota = Math.max(0, 3 - activeCount);
  return `
    <section class="grid grid--two">
      <div class="panel">
        <div class="status-strip">
          <div class="status-item"><span>Доступная квота</span><strong>${quota}</strong></div>
          <div class="status-item"><span>Активные</span><strong>${activeCount}</strong></div>
          <div class="status-item"><span>Всего</span><strong>${actorInvitations.length}</strong></div>
        </div>
        <form id="create-invitation-form">
          <label class="field">
            <span>Комментарий</span>
            <textarea name="comment">Кандидат рекомендован участником сообщества</textarea>
          </label>
          <div class="actions">
            <button class="btn btn--primary" type="submit">Создать приглашение</button>
            <a class="btn" href="/openapi.yaml" target="_blank" rel="noreferrer">OpenAPI</a>
          </div>
        </form>
      </div>
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
      </div>
    </section>
    <section class="panel">
      <h2>Приглашения</h2>
      ${table(['Токен', 'Статус', 'Автор', 'Создано', 'Срок'], state.snapshot.invitations.map((invitation) => [
        invitation.token,
        badge(invitation.status),
        userName(invitation.authorUserId),
        formatDate(invitation.createdAt),
        formatDate(invitation.expiresAt)
      ]))}
    </section>
  `;
}

function renderRegulation() {
  const regulation = activeRegulation();
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
      </div>
      <div class="panel">
        <h2>Голос</h2>
        <form id="vote-form">
          <div class="vote-options">
            <button type="button" class="choice ${state.voteChoice === 'SUPPORT' ? 'is-selected' : ''}" data-choice="SUPPORT">Поддержать</button>
            <button type="button" class="choice ${state.voteChoice === 'REJECT' ? 'is-selected' : ''}" data-choice="REJECT">Отклонить</button>
          </div>
          <label class="field">
            <span>Пояснение</span>
            <textarea name="reason">Решение принято по материалам кандидата</textarea>
          </label>
          <div class="actions">
            <button class="btn btn--primary" type="submit">Отправить голос</button>
            <button class="btn" type="button" id="open-vote">Открыть голосование</button>
            <button class="btn" type="button" id="close-vote">Закрыть голосование</button>
          </div>
        </form>
      </div>
    </section>
    <section class="panel">
      <h2>Жалоба</h2>
      <form id="complaint-form" class="actions">
        <input name="reason" value="Требуется дополнительная проверка" aria-label="Причина жалобы">
        <button class="btn" type="submit">Подать жалобу</button>
      </form>
    </section>
  `;
}

function renderBlocking() {
  const candidate = selectedCandidate('blocking');
  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('blocking')}
        ${candidateStatus(candidate)}
      </div>
      <div class="panel">
        <h2>Блокировка</h2>
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
            <button class="btn" id="unblock-candidate" type="button">Снять блокировку</button>
          </div>
        </form>
      </div>
    </section>
  `;
}

function renderStage() {
  const candidate = selectedCandidate('stage');
  const current = currentStage(candidate);
  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('stage')}
        ${candidateStatus(candidate)}
        <form id="stage-result-form">
          <label class="field">
            <span>Результат</span>
            <textarea name="result">Результат этапа подготовлен и отправлен через систему</textarea>
          </label>
          <button class="btn btn--primary" type="submit">Отправить результат</button>
        </form>
      </div>
      <div class="panel">
        <h2>Вердикт</h2>
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
            <strong>${formatDate(event.occurredAt)}</strong>
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
        actorUserId: state.actorUserId,
        comment: form.get('comment'),
        requestId: crypto.randomUUID()
      }, 'Приглашение создано');
    });
  }

  const activateForm = document.querySelector('#activate-invitation-form');
  if (activateForm) {
    activateForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(activateForm);
      mutate('/api/invitations/activate', {
        token: form.get('token'),
        fullName: form.get('fullName')
      }, 'Кандидат зарегистрирован');
    });
  }

  const regulationForm = document.querySelector('#regulation-form');
  if (regulationForm) {
    regulationForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(regulationForm);
      mutate('/api/regulations', {
        actorUserId: state.actorUserId,
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
        actorUserId: state.actorUserId,
        choice: state.voteChoice,
        reason: form.get('reason')
      }, 'Голос принят');
    });
  }

  const openVote = document.querySelector('#open-vote');
  if (openVote) {
    openVote.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('voting').id}/voting`, {
        actorUserId: state.actorUserId
      }, 'Голосование открыто');
    });
  }

  const closeVote = document.querySelector('#close-vote');
  if (closeVote) {
    closeVote.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('voting').id}/voting/close`, {
        actorUserId: state.actorUserId
      }, 'Голосование закрыто');
    });
  }

  const complaintForm = document.querySelector('#complaint-form');
  if (complaintForm) {
    complaintForm.addEventListener('submit', (event) => {
      event.preventDefault();
      const form = new FormData(complaintForm);
      mutate(`/api/candidates/${selectedCandidate('voting').id}/complaints`, {
        actorUserId: state.actorUserId,
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
        actorUserId: state.actorUserId,
        category: form.get('category'),
        reason: form.get('reason')
      }, 'Кандидат заблокирован');
    });
  }

  const unblock = document.querySelector('#unblock-candidate');
  if (unblock) {
    unblock.addEventListener('click', () => {
      mutate(`/api/candidates/${selectedCandidate('blocking').id}/unblock`, {
        actorUserId: state.actorUserId,
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
        actorUserId: state.actorUserId,
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
        actorUserId: state.actorUserId,
        verdict: form.get('verdict'),
        report: form.get('report')
      }, 'Вердикт сохранен');
    });
  }
}

function selectedCandidate(view) {
  const id = state.selectedCandidates[view];
  return state.snapshot.candidates.find((candidate) => candidate.id === id) || state.snapshot.candidates[0];
}

function candidatePicker(view) {
  return `
    <label class="field">
      <span>Кандидат</span>
      <select data-candidate-select="${view}">
        ${state.snapshot.candidates.map((candidate) => `
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

function nextActiveToken() {
  return state.snapshot.invitations.find((invitation) => invitation.status === 'ACTIVE')?.token || '';
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
