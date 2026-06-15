const form = document.querySelector('#activation-form');
const result = document.querySelector('#activation-result');
const toast = document.querySelector('#toast');
const params = new URLSearchParams(window.location.search);
const token = params.get('token');

if (token) {
  form.elements.token.value = token;
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const data = new FormData(form);
  try {
    const credentials = await activateInvitation({
      token: data.get('token'),
      fullName: data.get('fullName')
    });
    result.innerHTML = `
      <div class="credential-card">
        <span>Профиль кандидата создан</span>
        <strong>Логин: ${escapeHtml(credentials.username)}</strong>
        <code>Пароль: ${escapeHtml(credentials.password)}</code>
        <a href="/">Перейти ко входу</a>
      </div>
    `;
    form.reset();
    showToast('Приглашение активировано');
  } catch (error) {
    showToast(error.message || 'Активация не выполнена');
  }
});

async function activateInvitation(payload) {
  const response = await fetch('/api/invitations/activate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  if (!response.ok) {
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
