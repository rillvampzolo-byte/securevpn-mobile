interface Server {
    id: string;
    name: string;
    country: string;
    config: string;
}

const flags: Record<string, string> = {
    'DE': '🇩🇪', 'US': '🇺🇸', 'NL': '🇳🇱', 'GB': '🇬🇧',
    'FR': '🇫🇷', 'JP': '🇯🇵', 'SG': '🇸🇬', 'CA': '🇨🇦',
    'KZ': '🇰🇿', 'RU': '🇷🇺', 'UA': '🇺🇦', 'PL': '🇵🇱',
};

// Состояние
let currentStatus = 'Disconnected';
let isConnecting = false;
let selectedServer: Server | null = null;
let servers: Server[] = [];

// DOM — основные
const connectBtn = document.getElementById('connectBtn') as HTMLButtonElement;
const btnText = document.getElementById('btnText')!;
const statusIndicator = document.getElementById('statusIndicator')!;
const statusText = document.getElementById('statusText')!;
const statusDetail = document.getElementById('statusDetail')!;
const currentServerEl = document.getElementById('currentServer')!;
const quickServersList = document.getElementById('quickServersList')!;
const statusArc = document.getElementById('statusArc')!;

// DOM — настройки
const settingsBtn = document.getElementById('settings-btn') as HTMLButtonElement;
const settingsOverlay = document.getElementById('settings-overlay') as HTMLDivElement;
const settingsPanel = document.getElementById('settings-panel') as HTMLDivElement;
const settingsCloseBtn = document.getElementById('settings-close-btn') as HTMLButtonElement;
const toggleAutoconnect = document.getElementById('setting-autoconnect') as HTMLInputElement;
const toggleKillswitch = document.getElementById('setting-killswitch') as HTMLInputElement;
const selectDns = document.getElementById('setting-dns') as HTMLSelectElement;
const feedbackTextarea = document.getElementById('feedback-text') as HTMLTextAreaElement;
const feedbackSendBtn = document.getElementById('feedback-send-btn') as HTMLButtonElement;
const feedbackStatus = document.getElementById('feedback-status') as HTMLSpanElement;
const faqItems = document.querySelectorAll('.faq-item');

// Константы
const STORAGE_KEYS = {
    AUTO_CONNECT: 'securevpn_autoconnect',
    KILL_SWITCH: 'securevpn_killswitch',
    DNS: 'securevpn_dns',
};

const DNS_VALUES: Record<string, string> = {
    cloudflare: '1.1.1.1',
    google: '8.8.8.8',
    adguard: '94.140.14.14',
};

// ============================================
// ЗАГРУЗКА СЕРВЕРОВ
// ============================================

async function loadServers() {
    try {
        const response = await fetch('/servers.json');
        const data = await response.json();
        servers = data.servers || [];
    } catch (e) {
        servers = [];
    }

    if (servers.length > 0) {
        selectedServer = servers[0];
        currentServerEl.textContent = `📍 ${selectedServer.name}`;
    }
    renderQuickServers();
}

function renderQuickServers() {
    if (servers.length === 0) {
        quickServersList.innerHTML = '<p style="color: #9CA3AF; text-align: center; width: 100%;">No servers available</p>';
        return;
    }

    quickServersList.innerHTML = servers.map(s => `
        <button class="quick-server-btn ${selectedServer?.id === s.id ? 'selected' : ''}" data-server-id="${s.id}">
            <span class="quick-server-flag">${flags[s.country] || '🌍'}</span>
            <span class="quick-server-name">${escapeHtml(s.name)}</span>
        </button>
    `).join('');

    quickServersList.querySelectorAll('.quick-server-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const id = (btn as HTMLElement).dataset.serverId!;
            
            if (currentStatus === 'Connected' || currentStatus === 'Connecting') {
                disconnectSilent();
            }
            
            selectedServer = servers.find(s => s.id === id) || null;
            renderQuickServers();
            if (selectedServer) {
                currentServerEl.textContent = `📍 ${selectedServer.name}`;
            }
            showNotification(`Server changed to ${selectedServer?.name}`, 'info');
        });
    });
}

// ============================================
// ПОДКЛЮЧЕНИЕ / ОТКЛЮЧЕНИЕ
// ============================================

async function handleConnect() {
    if (!selectedServer) {
        showNotification('No server selected', 'error');
        return;
    }

    if (isConnecting) {
        isConnecting = false;
        currentStatus = 'Disconnected';
        updateUI('Disconnected');
        return;
    }

    if (currentStatus === 'Connected') {
        try {
            await (window as any).Capacitor.Plugins.VpnPlugin.disconnect();
        } catch(e) {}
        currentStatus = 'Disconnected';
        updateUI('Disconnected');
        return;
    }

    isConnecting = true;
    currentStatus = 'Connecting';
    updateUI('Connecting');

    try {
        await (window as any).Capacitor.Plugins.VpnPlugin.connect();
        currentStatus = 'Connected';
        isConnecting = false;
        updateUI('Connected');
        showNotification(`Connected to ${selectedServer!.name}`, 'info');
    } catch(e) {
        showNotification('VPN permission denied', 'error');
        isConnecting = false;
        updateUI('Error');
    }
}

function disconnectSilent() {
    currentStatus = 'Disconnected';
    isConnecting = false;
    updateUI('Disconnected');
}

// ============================================
// UI
// ============================================

function updateUI(status: string) {
    statusIndicator.className = `status-indicator ${status.toLowerCase()}`;
    
    switch (status) {
        case 'Connected':
            statusText.textContent = 'Connected';
            statusDetail.textContent = 'Your connection is secure';
            btnText.textContent = 'Disconnect';
            connectBtn.className = 'connect-btn glass-btn connected';
            break;
        case 'Connecting':
            statusText.textContent = 'Connecting...';
            statusDetail.textContent = 'Press again to cancel';
            btnText.textContent = 'Cancel';
            connectBtn.className = 'connect-btn glass-btn connecting';
            break;
        case 'Error':
            statusText.textContent = 'Error';
            statusDetail.textContent = 'Connection failed';
            btnText.textContent = 'Retry';
            connectBtn.className = 'connect-btn glass-btn error';
            break;
        default:
            statusText.textContent = 'Disconnected';
            statusDetail.textContent = 'Your connection is not protected';
            btnText.textContent = 'Connect';
            connectBtn.className = 'connect-btn glass-btn';
            break;
    }
}

// ============================================
// УВЕДОМЛЕНИЯ
// ============================================

function showNotification(message: string, type: string = 'info') {
    const old = document.querySelector('.notification');
    if (old) old.remove();
    const n = document.createElement('div');
    n.className = 'notification';
    n.textContent = message;
    n.style.cssText = `
        position: fixed; top: 16px; right: 16px; left: 16px; padding: 0.8rem 1rem;
        background: rgba(20,20,22,0.9); backdrop-filter: blur(10px);
        border: 1px solid ${type === 'error' ? '#EF4444' : '#00FF88'};
        border-radius: 0.7rem; color: white; z-index: 9999;
        font-size: 0.8rem; text-align: center;
    `;
    document.body.appendChild(n);
    setTimeout(() => {
        n.style.opacity = '0';
        n.style.transition = 'opacity 0.3s';
        setTimeout(() => n.remove(), 300);
    }, 2500);
}

// ============================================
// УТИЛИТЫ
// ============================================

function escapeHtml(text: string): string {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ============================================
// НАСТРОЙКИ
// ============================================

function loadSettings(): void {
    const autoconnect = localStorage.getItem(STORAGE_KEYS.AUTO_CONNECT) === 'true';
    const killswitch = localStorage.getItem(STORAGE_KEYS.KILL_SWITCH) === 'true';
    const dns = localStorage.getItem(STORAGE_KEYS.DNS) || 'cloudflare';

    toggleAutoconnect.checked = autoconnect;
    toggleKillswitch.checked = killswitch;
    selectDns.value = dns;
}

function saveSettings(): void {
    localStorage.setItem(STORAGE_KEYS.AUTO_CONNECT, String(toggleAutoconnect.checked));
    localStorage.setItem(STORAGE_KEYS.KILL_SWITCH, String(toggleKillswitch.checked));
    localStorage.setItem(STORAGE_KEYS.DNS, selectDns.value);
}

function getSettings() {
    return {
        autoconnect: toggleAutoconnect.checked,
        killswitch: toggleKillswitch.checked,
        dns: selectDns.value,
        dnsIp: DNS_VALUES[selectDns.value] || '1.1.1.1',
    };
}

function applySettings(): void {
    saveSettings();
    console.log('[Settings] Применены:', getSettings());
}

function openSettings(): void {
    settingsPanel.classList.add('active');
    settingsOverlay.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeSettings(): void {
    settingsPanel.classList.remove('active');
    settingsOverlay.classList.remove('active');
    document.body.style.overflow = '';
}

settingsBtn.addEventListener('click', openSettings);
settingsCloseBtn.addEventListener('click', closeSettings);
settingsOverlay.addEventListener('click', closeSettings);

toggleAutoconnect.addEventListener('change', applySettings);
toggleKillswitch.addEventListener('change', applySettings);
selectDns.addEventListener('change', applySettings);

// FAQ
faqItems.forEach((item) => {
    const questionBtn = item.querySelector('.faq-question') as HTMLButtonElement;
    questionBtn.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');
        faqItems.forEach((i) => i.classList.remove('open'));
        if (!isOpen) item.classList.add('open');
    });
});

// Обратная связь
feedbackSendBtn.addEventListener('click', async () => {
    const message = feedbackTextarea.value.trim();
    if (!message) {
        feedbackStatus.textContent = 'Введите сообщение';
        feedbackStatus.className = 'feedback-status error';
        return;
    }

    feedbackSendBtn.disabled = true;
    feedbackSendBtn.textContent = 'Отправка...';

    try {
        const feedbacks = JSON.parse(localStorage.getItem('securevpn_feedbacks') || '[]');
        feedbacks.push({ message, date: new Date().toISOString(), settings: getSettings() });
        localStorage.setItem('securevpn_feedbacks', JSON.stringify(feedbacks));

        await new Promise((resolve) => setTimeout(resolve, 500));
        feedbackTextarea.value = '';
        feedbackStatus.textContent = '✓ Отправлено!';
        feedbackStatus.className = 'feedback-status success';
        setTimeout(() => { feedbackStatus.textContent = ''; feedbackStatus.className = 'feedback-status'; }, 3000);
    } catch {
        feedbackStatus.textContent = 'Ошибка';
        feedbackStatus.className = 'feedback-status error';
    } finally {
        feedbackSendBtn.disabled = false;
        feedbackSendBtn.textContent = 'Отправить';
    }
});

// ============================================
// ИНИЦИАЛИЗАЦИЯ
// ============================================

async function init() {
    loadSettings();
    await loadServers();
    connectBtn.addEventListener('click', handleConnect);
}

document.addEventListener('DOMContentLoaded', init);