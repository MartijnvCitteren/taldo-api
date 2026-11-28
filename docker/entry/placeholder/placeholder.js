// Shows whether the browser has a session with the BFF (GET /api/v1/me through the BFF to taldo-api), and logs out the
// way the frontend does (docs/application/bff.md): POST /logout with the CSRF header, then go to Keycloak's logout URL.
const status = document.getElementById('status');
const login = document.getElementById('login');
const logout = document.getElementById('logout');

function csrfToken() {
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='));
  return cookie ? decodeURIComponent(cookie.substring('XSRF-TOKEN='.length)) : '';
}

async function showSession() {
  if (new URLSearchParams(window.location.search).get('login') === 'failed') {
    status.textContent = 'Logging in failed.';
    login.hidden = false;
    return;
  }
  const response = await fetch('/api/v1/me', { headers: { Accept: 'application/json' } });
  if (response.status === 401) {
    status.textContent = 'You are not logged in.';
    login.hidden = false;
    return;
  }
  if (!response.ok) {
    status.textContent = `The backend answered ${response.status}.`;
    return;
  }
  const profile = await response.json();
  const name = profile.firstName ? ` as ${profile.firstName}` : '';
  status.textContent = `You are logged in${name}. Account status: ${profile.status}.`;
  logout.hidden = false;
}

logout.addEventListener('click', async () => {
  const response = await fetch('/logout', { method: 'POST', headers: { 'X-XSRF-TOKEN': csrfToken() } });
  if (!response.ok) {
    status.textContent = `Logging out failed (${response.status}).`;
    return;
  }
  const { logoutUrl } = await response.json();
  window.location.assign(logoutUrl);
});

showSession().catch(() => {
  status.textContent = 'The backend could not be reached.';
});
