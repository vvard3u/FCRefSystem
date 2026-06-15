const viewDefinitions = [
  {
    id: 'invitations',
    label: 'Приглашения',
    title: 'Создание реферального приглашения',
    roles: ['MEMBER', 'PRIVILEGED_MEMBER']
  },
  {
    id: 'regulation',
    label: 'Регламент',
    title: 'Регламент отбора',
    roles: ['ADMIN']
  },
  {
    id: 'voting',
    label: 'Голосование',
    title: 'Голосование по кандидату',
    roles: ['PRIVILEGED_MEMBER', 'ADMIN']
  },
  {
    id: 'blocking',
    label: 'Блокировка',
    title: 'Блокировка кандидата',
    roles: ['INTERVIEWER', 'ADMIN']
  },
  {
    id: 'stage',
    label: 'Этап отбора',
    title: 'Прохождение этапа отбора',
    roles: ['CANDIDATE', 'INTERVIEWER', 'ADMIN']
  },
  {
    id: 'journal',
    label: 'Журнал',
    title: 'Журнал событий',
    roles: ['ADMIN']
  }
];

const viewRenderers = {
  invitations: renderInvitations,
  regulation: renderRegulation,
  voting: renderVoting,
  blocking: renderBlocking,
  stage: renderStage,
  journal: renderJournal
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

const stageTypeLabels = {
  FORM: 'Анкета',
  TASK: 'Задание',
  INTERVIEW: 'Интервью',
  VOTE: 'Голосование'
};

const verdictLabels = {
  PASSED: 'Пройден',
  FAILED: 'Не пройден',
  RETRY: 'Повтор'
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
  lastInvitation: null,
  selectedCandidates: {
    voting: 'candidate-vote',
    blocking: 'candidate-block',
    stage: 'candidate-stage'
  }
};

const app = document.querySelector('#app');
const authPanel = document.querySelector('#auth-panel');
const nav = document.querySelector('#nav');
const viewTitle = document.querySelector('#view-title');
const toast = document.querySelector('#toast');

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
  const { headers = {}, ...fetchOptions } = options;
  const requestHeaders = { 'Content-Type': 'application/json', ...headers };
  if (state.auth) {
    requestHeaders.Authorization = state.auth;
  }
  const response = await fetch(path, {
    headers: requestHeaders,
    ...fetchOptions
  });
  if (!response.ok) {
    if (response.status === 401) {
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
    const result = await request(path, {
      method: 'POST',
      body: JSON.stringify(body)
    });
    await loadAuthenticatedState();
    showToast(label);
    return result;
  } catch (error) {
    showToast(error.message || 'Операция не выполнена');
    return null;
  }
}

function render() {
  renderAuthPanel();
  renderNav();
  if (!state.session || !state.snapshot) {
    viewTitle.textContent = 'Вход в систему';
    app.innerHTML = renderLoginContent();
    return;
  }

  ensureViewAccess();
  const definition = viewDefinitions.find((item) => item.id === state.view);
  viewTitle.textContent = definition?.title || 'FCRefSystem';
  app.innerHTML = viewRenderers[state.view]?.() || renderNoAvailableViews();
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

function renderNav() {
  if (!state.session) {
    nav.innerHTML = '';
    return;
  }
  ensureViewAccess();
  nav.innerHTML = allowedViews().map((definition) => `
    <button class="nav__item ${state.view === definition.id ? 'is-active' : ''}" type="button" data-view="${definition.id}">
      ${escapeHtml(definition.label)}
    </button>
  `).join('');
  nav.querySelectorAll('[data-view]').forEach((button) => {
    button.addEventListener('click', () => {
      state.view = button.dataset.view;
      render();
    });
  });
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
      state.lastInvitation = null;
      render();
    });
  }
}

function renderLoginContent() {
  return `
    <section class="grid grid--two">
      <div class="panel">
        <h2>Демо-доступ</h2>
        <div class="demo-users">
          ${demoUser('member', 'member', 'Создает приглашения')}
          ${demoUser('privileged', 'privileged', 'Создает приглашения и голосует')}
          ${demoUser('interviewer', 'interviewer', 'Проверяет этапы и блокирует кандидатов')}
          ${demoUser('admin', 'admin', 'Управляет регламентом и голосованием')}
          ${demoUser('candidate', 'candidate', 'Проходит назначенный этап')}
        </div>
      </div>
      <div class="panel">
        <h2>Активация приглашения</h2>
        <div class="placeholder">
          <strong>Отдельная страница кандидата</strong>
          <p class="muted">Кандидат активирует приглашение до входа в систему. После активации система создает профиль и выдает логин с паролем.</p>
        </div>
        <div class="actions toolbar">
          <a class="btn btn--good" href="/activate.html">Перейти к активации</a>
        </div>
      </div>
    </section>
  `;
}

function demoUser(username, password, description) {
  return `
    <article class="demo-user">
      <strong>${escapeHtml(username)} / ${escapeHtml(password)}</strong>
      <span>${escapeHtml(description)}</span>
    </article>
  `;
}

function renderNoAvailableViews() {
  return `
    <section class="panel">
      ${placeholder('Нет доступных разделов', 'Для текущей учетной записи не настроены рабочие разделы демо-версии.')}
    </section>
  `;
}

function renderInvitations() {
  const ownInvitations = state.snapshot.invitations;
  const activeCount = ownInvitations.filter((invitation) => invitation.status === 'ACTIVE').length;
  const quota = Math.max(0, 3 - activeCount);
  const lastInvitation = state.lastInvitation;

  return `
    <section class="grid grid--two">
      <div class="panel">
        <div class="status-strip">
          <div class="status-item"><span>Доступная квота</span><strong>${quota}</strong></div>
          <div class="status-item"><span>Активные</span><strong>${activeCount}</strong></div>
          <div class="status-item"><span>Всего</span><strong>${ownInvitations.length}</strong></div>
        </div>
        <form id="create-invitation-form">
          <label class="field">
            <span>Комментарий к рекомендации</span>
            <textarea name="comment">Кандидат рекомендован действующим участником клуба</textarea>
          </label>
          <div class="actions">
            <button class="btn btn--primary" type="submit">Создать приглашение</button>
          </div>
        </form>
      </div>
      <div class="panel">
        <h2>Ссылка для кандидата</h2>
        ${lastInvitation ? invitationLinkCard(lastInvitation) : placeholder(
    'Создайте приглашение',
    'После создания здесь появится ссылка, которую можно передать кандидату.'
  )}
      </div>
    </section>
    <section class="panel">
      <h2>Мои приглашения</h2>
      ${ownInvitations.length > 0 ? table(
    ['Токен', 'Ссылка активации', 'Статус', 'Создано', 'Срок действия'],
    ownInvitations.map((invitation) => [
      escapeHtml(invitation.token),
      `<a href="${escapeHtml(invitationLink(invitation.token))}" target="_blank" rel="noreferrer">Открыть</a>`,
      badge(invitation.status),
      escapeHtml(formatDate(invitation.createdAt)),
      escapeHtml(formatDate(invitation.expiresAt))
    ])
  ) : placeholder('Приглашений нет', 'Созданные приглашения появятся в этом списке.')}
    </section>
  `;
}

function invitationLinkCard(invitation) {
  const link = invitationLink(invitation.token);
  return `
    <div class="credential-card">
      <span>Приглашение создано</span>
      <strong>${escapeHtml(invitation.token)}</strong>
      <a href="${escapeHtml(link)}" target="_blank" rel="noreferrer">${escapeHtml(link)}</a>
    </div>
  `;
}

function renderRegulation() {
  const regulation = activeRegulation();
  return `
    <section class="grid grid--two">
      <div class="panel">
        <h2>Создание регламента</h2>
        <form id="regulation-form">
          <label class="field">
            <span>Название регламента</span>
            <input name="name" value="${escapeHtml(regulation.name)}">
          </label>
          <label class="field">
            <span>Описание правил отбора</span>
            <textarea name="description">${escapeHtml(regulation.description)}</textarea>
          </label>
          <div class="stage-list">
            ${regulation.stages.map((stage, index) => stageEditor(stage, index)).join('')}
          </div>
          <div class="actions toolbar">
            <button class="btn btn--primary" type="submit">Сохранить и активировать</button>
          </div>
        </form>
      </div>
      <div class="panel">
        ${renderRegulationSummary(regulation)}
      </div>
    </section>
  `;
}

function renderRegulationSummary(regulation) {
  return `
    <h2>Сохраненный регламент</h2>
    <div class="status-strip">
      <div class="status-item"><span>Название</span><strong>${escapeHtml(regulation.name)}</strong></div>
      <div class="status-item"><span>Этапов</span><strong>${regulation.stages.length}</strong></div>
      <div class="status-item"><span>Создал</span><strong>${escapeHtml(userName(regulation.createdByUserId))}</strong></div>
    </div>
    <p class="muted">${escapeHtml(regulation.description || 'Описание не указано')}</p>
    ${renderStageList(regulation.stages)}
  `;
}

function renderVoting() {
  const candidate = selectedCandidate('voting');
  if (!candidate) {
    return emptyCandidatePanel();
  }
  const current = currentStage(candidate);
  const session = candidate.votingSessions.find((item) => item.status === 'OPEN') || candidate.votingSessions.at(-1);
  const canManageVoting = hasRole('ADMIN');
  const canVote = hasRole('PRIVILEGED_MEMBER');
  const votePanel = canVote ? `
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
          <span>Обязательное пояснение</span>
          <textarea name="reason">Решение принято по материалам кандидата</textarea>
        </label>
        <button class="btn btn--primary" type="submit">Отправить голос</button>
      </form>
    </div>
  ` : `
    <div class="panel">
      <h2>Управление голосованием</h2>
      ${placeholder('Голосуют участники', 'Администратор открывает и закрывает голосование, но не подает голос за участника клуба.')}
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
          <p class="muted">Статус голосования: ${session ? badge(session.status) : 'не открыто'}</p>
          <p class="muted">Голосов: ${session ? session.votes.length : 0}</p>
        </div>
        ${canManageVoting ? `
          <div class="actions toolbar">
            <button class="btn" type="button" id="open-vote">Открыть голосование</button>
            <button class="btn" type="button" id="close-vote" ${session?.status === 'OPEN' ? '' : 'disabled'}>Закрыть голосование</button>
          </div>
        ` : ''}
      </div>
      ${votePanel}
    </section>
    ${canVote ? `
      <section class="panel">
        <h2>Жалоба</h2>
        <form id="complaint-form" class="actions">
          <input name="reason" value="Требуется дополнительная проверка" aria-label="Причина жалобы">
          <button class="btn" type="submit">Подать жалобу</button>
        </form>
      </section>
    ` : ''}
  `;
}

function renderBlocking() {
  const candidate = selectedCandidate('blocking');
  if (!candidate) {
    return emptyCandidatePanel();
  }
  return `
    <section class="grid grid--two">
      <div class="panel">
        ${candidatePicker('blocking')}
        ${candidateStatus(candidate)}
        ${renderBlocks(candidate)}
      </div>
      <div class="panel">
        <h2>Блокировка</h2>
        <form id="block-form">
          <label class="field">
            <span>Категория нарушения</span>
            <input name="category" value="Нарушение правил сообщества">
          </label>
          <label class="field">
            <span>Обязательная причина</span>
            <textarea name="reason">Основание зафиксировано интервьюером</textarea>
          </label>
          <div class="actions">
            <button class="btn btn--danger" type="submit">Заблокировать</button>
            ${hasRole('ADMIN') ? '<button class="btn" id="unblock-candidate" type="button">Снять блокировку</button>' : ''}
          </div>
        </form>
      </div>
    </section>
  `;
}

function renderBlocks(candidate) {
  const activeBlock = candidate.blocks.find((block) => block.active);
  if (!activeBlock) {
    return placeholder('Активной блокировки нет', 'Если интервьюер или администратор зафиксирует нарушение, оно появится здесь.');
  }
  return `
    <div class="band">
      <h3>${escapeHtml(activeBlock.category)}</h3>
      <p class="muted">${escapeHtml(activeBlock.reason)}</p>
    </div>
  `;
}

function renderStage() {
  const candidate = selectedCandidate('stage');
  if (!candidate) {
    return emptyCandidatePanel();
  }
  const current = currentStage(candidate);
  const rule = current ? stageRule(current.stageId) : null;
  const canSubmit = hasRole('CANDIDATE') && candidate.candidateUserId === state.session.id;
  const canVerdict = hasRole('INTERVIEWER') || hasRole('ADMIN');
  const canPickCandidate = !hasRole('CANDIDATE') || hasRole('ADMIN') || hasRole('INTERVIEWER');
  const submitDisabled = !current || ['SUBMITTED', 'PASSED', 'FAILED'].includes(current.state) || candidate.status === 'BLOCKED';

  return `
    <section class="grid grid--two">
      <div class="panel">
        ${canPickCandidate ? candidatePicker('stage') : `<div class="band"><h3>${escapeHtml(candidate.fullName)}</h3></div>`}
        ${candidateStatus(candidate)}
        ${renderStageDetails(current, rule)}
        ${canSubmit ? `
          <form id="stage-result-form">
            <label class="field">
              <span>Результат выполнения этапа</span>
              <textarea name="result">Результат этапа подготовлен и отправлен через систему</textarea>
            </label>
            <button class="btn btn--primary" type="submit" ${submitDisabled ? 'disabled' : ''}>Отправить результат</button>
          </form>
        ` : ''}
      </div>
      <div class="panel">
        <h2>Вердикт</h2>
        ${canVerdict ? `
          <form id="verdict-form">
            <label class="field">
              <span>Решение по этапу</span>
              <select name="verdict">
                <option value="PASSED">Пройден</option>
                <option value="FAILED">Не пройден</option>
                <option value="RETRY">Повтор</option>
              </select>
            </label>
            <label class="field">
              <span>Отчет интервьюера</span>
              <textarea name="report">Результат проверен, решение зафиксировано</textarea>
            </label>
            <button class="btn" type="submit">Зафиксировать вердикт</button>
          </form>
        ` : renderCandidateResult(current)}
      </div>
    </section>
  `;
}

function renderStageDetails(current, rule) {
  if (!current) {
    return placeholder('Этап не назначен', 'Текущий этап появится после назначения.');
  }
  return `
    <div class="stage-details">
      <div class="status-item"><span>Этап</span><strong>${escapeHtml(current.stageName)}</strong></div>
      <div class="status-item"><span>Тип</span><strong>${escapeHtml(stageTypeLabels[current.stageType] || current.stageType)}</strong></div>
      <div class="status-item"><span>Попытка</span><strong>${current.attemptNumber} из ${current.attemptLimit}</strong></div>
      <div class="status-item"><span>Срок</span><strong>${rule ? `${rule.dueDays} дн.` : 'не указан'}</strong></div>
      <div class="status-item stage-details__wide">
        <span>Критерии прохождения</span>
        <strong>${escapeHtml(rule?.criteria || 'Критерии не указаны')}</strong>
      </div>
      ${current.submittedResult ? `
        <div class="status-item stage-details__wide">
          <span>Отправленный результат</span>
          <strong>${escapeHtml(current.submittedResult)}</strong>
        </div>
      ` : ''}
    </div>
  `;
}

function renderCandidateResult(current) {
  if (!current?.verdict) {
    return placeholder('Решение еще не принято', 'После проверки здесь появится доступный кандидату результат.');
  }
  return `
    <div class="band">
      <h3>${escapeHtml(verdictLabels[current.verdict] || current.verdict)}</h3>
      <p class="muted">${escapeHtml(current.report || '')}</p>
    </div>
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
    createInvitationForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const form = new FormData(createInvitationForm);
      state.lastInvitation = await mutate('/api/invitations', {
        comment: form.get('comment'),
        requestId: crypto.randomUUID()
      }, 'Приглашение создано');
      render();
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

function allowedViews() {
  if (!state.session) {
    return [];
  }
  return viewDefinitions.filter((definition) => definition.roles.some((role) => hasRole(role)));
}

function ensureViewAccess() {
  const allowed = allowedViews();
  if (!allowed.some((definition) => definition.id === state.view)) {
    state.view = allowed[0]?.id || '';
  }
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
  if (!state.snapshot) {
    return [];
  }
  if (view === 'stage' && hasRole('CANDIDATE') && !hasRole('ADMIN') && !hasRole('INTERVIEWER')) {
    return state.snapshot.candidates.filter((candidate) => candidate.candidateUserId === state.session.id);
  }
  return state.snapshot.candidates;
}

function selectedCandidate(view) {
  const options = candidatesForView(view);
  const id = state.selectedCandidates[view];
  return options.find((candidate) => candidate.id === id) || options[0] || null;
}

function candidatePicker(view) {
  const options = candidatesForView(view);
  if (options.length === 0) {
    return placeholder('Кандидатов нет', 'В текущей роли нет доступных карточек кандидатов.');
  }
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
      <div class="status-item"><span>Следующее действие</span><strong>${escapeHtml(nextAction)}</strong></div>
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
  return candidate?.stages?.find((stage) => stage.stageId === candidate.currentStageId);
}

function activeRegulation() {
  return state.snapshot.regulations.find((regulation) => regulation.active) || state.snapshot.regulations[0];
}

function stageRule(stageId) {
  return activeRegulation()?.stages?.find((stage) => stage.id === stageId);
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

function stageEditor(stage, index) {
  return `
    <fieldset class="stage-row" data-stage-row>
      <legend>Этап ${index + 1}</legend>
      <label class="stage-field">
        <span>Название этапа</span>
        <input name="stageName" value="${escapeHtml(stage.name)}">
      </label>
      <label class="stage-field">
        <span>Тип этапа</span>
        <select name="stageType">
          ${Object.entries(stageTypeLabels).map(([type, label]) => `
            <option value="${type}" ${stage.type === type ? 'selected' : ''}>${escapeHtml(label)}</option>
          `).join('')}
        </select>
      </label>
      <label class="stage-field">
        <span>Лимит попыток</span>
        <input name="attemptLimit" type="number" min="1" value="${stage.attemptLimit}">
      </label>
      <label class="stage-field">
        <span>Срок, дней</span>
        <input name="dueDays" type="number" min="1" value="${stage.dueDays}">
      </label>
      <label class="stage-field">
        <span>Порог, %</span>
        <input name="thresholdPercent" type="number" min="1" max="100" value="${stage.thresholdPercent ?? ''}">
      </label>
      <label class="stage-field stage-field--wide">
        <span>Критерии прохождения</span>
        <input name="criteria" value="${escapeHtml(stage.criteria || '')}">
      </label>
      <label class="stage-check">
        <input name="requiresSubmission" type="checkbox" ${stage.requiresSubmission ? 'checked' : ''}>
        <span>Кандидат отправляет результат</span>
      </label>
      <input name="stageId" type="hidden" value="${escapeHtml(stage.id)}">
    </fieldset>
  `;
}

function renderStageList(stages) {
  return `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Этап</th>
            <th>Тип</th>
            <th>Попытки</th>
            <th>Срок</th>
            <th>Порог</th>
            <th>Критерии</th>
          </tr>
        </thead>
        <tbody>
          ${stages.map((stage) => `
            <tr>
              <td>${escapeHtml(stage.name)}</td>
              <td>${escapeHtml(stageTypeLabels[stage.type] || stage.type)}</td>
              <td>${stage.attemptLimit}</td>
              <td>${stage.dueDays} дн.</td>
              <td>${stage.thresholdPercent ? `${stage.thresholdPercent}%` : ''}</td>
              <td>${escapeHtml(stage.criteria || '')}</td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

function emptyCandidatePanel() {
  return `
    <section class="panel">
      ${placeholder('Нет доступных кандидатов', 'Для текущей роли пока нет кандидатов в этом сценарии.')}
    </section>
  `;
}

function invitationLink(token) {
  return `${window.location.origin}/activate.html?token=${encodeURIComponent(token)}`;
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
