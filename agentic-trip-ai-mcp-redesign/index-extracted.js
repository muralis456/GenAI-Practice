
    const layout = document.querySelector('.layout');
    const form = document.getElementById('travelForm');
    const chatThread = document.getElementById('chatThread');
    const messagesPane = document.getElementById('messagesPane');
    const chatHistoryList = document.getElementById('chatHistoryList');
    const userIdField = document.getElementById('userId');
    const promptInput = document.getElementById('tripPrompt');
    const submitButton = document.getElementById('submitButton');
    const loadingState = document.getElementById('loadingState');
    const newChatBtn = document.getElementById('newChatBtn');
    const composerNewChat = document.getElementById('composerNewChat');
    const tripsView = document.getElementById('tripsView');
    const tripsGrid = document.getElementById('tripsGrid');
    const tripsRefresh = document.getElementById('tripsRefresh');
    let dbTrips = [];

    const showToast = (message) => {
        let toast = document.getElementById('uiToast');
        if (!toast) {
            toast = document.createElement('div');
            toast.id = 'uiToast';
            toast.className = 'ui-toast';
            document.body.appendChild(toast);
        }
        toast.textContent = message;
        toast.classList.add('visible');
        clearTimeout(window.__agenticTripToast);
        window.__agenticTripToast = setTimeout(() => toast.classList.remove('visible'), 2600);
    };

    const setActiveNav = (name) => {
        document.querySelectorAll('.sidebar-nav-item').forEach(item =>
            item.classList.toggle('active', item.dataset.nav === name));
    };

    const showHomeView = () => {
        tripsView.classList.remove('visible');
        chatThread.style.display = '';
        const welcome = document.querySelector('.welcome-block');
        if (welcome) welcome.style.display = '';
        setActiveNav('home');
    };

    const showTripsView = async () => {
        tripsView.classList.add('visible');
        chatThread.style.display = 'none';
        const welcome = document.querySelector('.welcome-block');
        if (welcome) welcome.style.display = 'none';
        setActiveNav('trips');
        await loadDbTrips(true);
        messagesPane.scrollTo({ top: 0, behavior: 'smooth' });
    };

    document.querySelectorAll('.sidebar-nav-item').forEach(item => {
        item.addEventListener('click', async () => {
            const nav = item.dataset.nav;
            if (nav === 'trips') {
                await showTripsView();
            } else if (nav === 'home') {
                showHomeView();
                messagesPane.scrollTo({ top: 0, behavior: 'smooth' });
            } else if (nav === 'tools') {
                showHomeView();
                promptInput.focus();
                showToast('Travel Tools: describe flights, hotels, weather, itinerary, budget, or a complete trip.');
            } else if (nav === 'saved') {
                showHomeView();
                showToast('Saved Places will be connected to your saved destinations next.');
            } else if (nav === 'settings') {
                showHomeView();
                userIdField.focus();
                userIdField.select();
                showToast('Settings: update the session user ID here.');
            } else if (nav === 'help') {
                showHomeView();
                showToast('Use New Trip to start fresh. Review the plan, then approve, modify, or reject it.');
            }
        });
    });

    const resizePrompt = () => {
        // Let the browser recalculate the textarea's natural height first.
        // Using height=0 can be unreliable when the textarea also has a
        // min-height/flex parent, which is why long prompts were not growing.
        const minHeight = 28;
        const maxHeight = 220;

        promptInput.style.height = 'auto';
        promptInput.style.overflowY = 'hidden';

        const contentHeight = Math.max(promptInput.scrollHeight, minHeight);
        const nextHeight = Math.min(contentHeight, maxHeight);
        promptInput.style.height = nextHeight + 'px';

        const overflowing = contentHeight > maxHeight + 1;
        promptInput.classList.toggle('has-overflow', overflowing);
        promptInput.style.overflowY = overflowing ? 'auto' : 'hidden';
        if (overflowing) {
            promptInput.scrollTop = promptInput.scrollHeight;
        }
    };

    const updateComposerState = () => {
        // The submit handler already ignores blank requests. Keep the send/Enter
        // control enabled while the composer is idle, including after New Chat.
        if (!loadingState.classList.contains('visible')) {
            submitButton.disabled = false;
        }
    };

    const storageKey = () => 'agentic-trip-ai-chats:' + (userIdField.value.trim() || 'aaro_hi_user');
    let currentChatId = null;
    let startedFreshChat = false;

    const syncUserIdField = () => {
        form.querySelector('input[name="userId"]').value = userIdField.value.trim() || 'aaro_hi_user';
    };

    const syncUserId = () => {
        syncUserIdField();
        renderHistoryList();
        loadServerHistoryIntoStore();
    };

    userIdField.addEventListener('input', syncUserId);

    document.querySelectorAll('.city-card[data-prompt]').forEach(card => {
        card.addEventListener('click', () => {
            promptInput.value = card.getAttribute('data-prompt');
            resizePrompt();
            promptInput.focus();
        });
    });

    const loadStore = () => {
        try {
            return JSON.parse(localStorage.getItem(storageKey()) || '{"chats":[]}');
        } catch (e) {
            return { chats: [] };
        }
    };

    const trimPlanData = (planData) => {
        if (!planData) {
            return null;
        }
        return {
            status: planData.status,
            awaitingApproval: planData.awaitingApproval,
            threadId: planData.threadId,
            plan: planData.plan,
            execution: planData.execution ? {
                timeline: (planData.execution.timeline || []).slice(-16),
                sources: (planData.execution.sources || []).slice(0, 12),
                executionHistory: planData.execution.executionHistory || ''
            } : null
        };
    };

    const saveStore = (store) => {
        const chats = (store.chats || [])
            .slice()
            .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
        const kept = [];
        for (const chat of chats) {
            if (kept.length >= 20 && chat.id !== currentChatId) {
                continue;
            }
            kept.push({
                id: chat.id,
                threadId: chat.threadId || '',
                title: chat.title,
                updatedAt: chat.updatedAt,
                messages: (chat.messages || []).slice(-40).map(m => ({
                    role: m.role,
                    text: (m.text || '').slice(0, 8000),
                    planData: trimPlanData(m.planData)
                }))
            });
        }
        store.chats = kept;
        try {
            localStorage.setItem(storageKey(), JSON.stringify(store));
        } catch (e) {
            console.warn('Could not save chat history locally', e);
            try {
                store.chats = kept.map(chat => ({
                    ...chat,
                    messages: (chat.messages || []).map(m => ({ role: m.role, text: m.text, planData: null }))
                }));
                localStorage.setItem(storageKey(), JSON.stringify(store));
            } catch (ignored) {
                // ignore
            }
        }
    };

    const ensureCurrentChat = (titleHint) => {
        const store = loadStore();
        let chat = store.chats.find(c => c.id === currentChatId);
        if (!chat) {
            currentChatId = 'chat-' + Date.now();
            chat = {
                id: currentChatId,
                title: (titleHint || 'New trip').slice(0, 60),
                updatedAt: Date.now(),
                messages: []
            };
            store.chats.unshift(chat);
            saveStore(store);
        }
        return chat;
    };

    const persistMessage = (role, text, planData) => {
        try {
            const store = loadStore();
            let chat = store.chats.find(c => c.id === currentChatId);
            if (!chat) {
                ensureCurrentChat(text);
                return persistMessage(role, text, planData);
            }
            chat.messages.push({ role, text, planData: planData || null });
            if (planData?.threadId) {
                chat.threadId = String(planData.threadId);
            }
            chat.updatedAt = Date.now();
            if (role === 'user' && chat.messages.filter(m => m.role === 'user').length === 1) {
                chat.title = text.slice(0, 60);
            }
            saveStore(store);
            renderHistoryList();
        } catch (e) {
            console.warn('persistMessage failed', e);
        }
    };

    const loadServerHistoryIntoStore = async () => {
        const userId = userIdField.value.trim() || 'aaro_hi_user';
        try {
            const res = await fetch('/api/chat/history?userId=' + encodeURIComponent(userId) + '&limit=100');
            if (!res.ok) return;
            const items = await res.json();
            if (!Array.isArray(items) || !items.length) return;

            const store = loadStore();
            const serverPrefix = 'server-' + userId + '-';
            const existingLocal = store.chats.filter(c => !String(c.id || '').startsWith(serverPrefix));

            const grouped = new Map();
            items.forEach(item => {
                const sessionId = String(item.sessionId || '').trim();
                if (!sessionId) return;
                if (!grouped.has(sessionId)) {
                    grouped.set(sessionId, {
                        id: serverPrefix + sessionId,
                        threadId: sessionId,
                        title: 'Previous conversation',
                        updatedAt: 0,
                        messages: []
                    });
                }
                const chat = grouped.get(sessionId);
                const createdAt = item.createdAt ? new Date(item.createdAt).getTime() : Date.now();
                chat.updatedAt = Math.max(chat.updatedAt, Number.isFinite(createdAt) ? createdAt : Date.now());
                if (item.role === 'user') {
                    const text = String(item.content || '').trim();
                    if (!chat.messages.some(m => m.role === 'user') && text) {
                        chat.title = text.slice(0, 60) || 'Previous conversation';
                    }
                    chat.messages.push({ role: 'user', text, planData: null });
                } else {
                    let planData = null;
                    if (item.structuredData) {
                        try { planData = JSON.parse(item.structuredData); } catch (ignored) {}
                    }
                    chat.messages.push({
                        role: 'assistant',
                        text: item.content || '',
                        planData
                    });
                }
            });

            // Merge server data into an existing local chat when both refer to
            // the same thread. Server structuredData is the durable source of
            // truth and fixes older local entries that only contain plain text.
            const serverChats = Array.from(grouped.values());
            const mergedLocal = existingLocal.map(chat => {
                const threadId = chatThreadIdForDelete(chat);
                if (!threadId) return chat;
                const server = grouped.get(threadId);
                if (!server) return chat;
                const serverHasStructured = server.messages.some(m => m.role === 'assistant' && m.planData);
                return {
                    ...chat,
                    threadId,
                    title: server.title || chat.title,
                    updatedAt: Math.max(chat.updatedAt || 0, server.updatedAt || 0),
                    messages: serverHasStructured ? server.messages : (chat.messages || [])
                };
            });

            const localThreadIds = new Set(mergedLocal.map(chat => chatThreadIdForDelete(chat)).filter(Boolean));
            const authoritativeThreads = new Set(dbTrips.map(t => String(t.threadId || '')).filter(Boolean));
            mergedLocal.forEach(chat => {
                const threadId = chatThreadIdForDelete(chat);
                if (threadId) authoritativeThreads.add(threadId);
            });

            const unmergedServerChats = serverChats.filter(chat =>
                !localThreadIds.has(String(chat.threadId || ''))
                && !authoritativeThreads.has(String(chat.threadId || ''))
            );

            store.chats = mergedLocal.concat(unmergedServerChats);
            saveStore(store);
            renderHistoryList();
        } catch (e) {
            console.warn('Could not load server chat history', e);
        }
    };

    const tripStatusLabel = (trip) => {
        if (trip?.legacy) return 'Previous trip';
        const status = String(trip?.status || '').toUpperCase();
        if (trip?.awaitingApproval || status === 'PENDING_APPROVAL') return 'Pending approval';
        if (status === 'REJECTED') return 'Rejected';
        if (status === 'COMPLETE') return 'Confirmed';
        return status ? status.replaceAll('_', ' ') : 'Saved';
    };

    const renderDbTripsView = () => {
        if (!dbTrips.length) {
            tripsGrid.innerHTML = '<div class="trips-empty" style="grid-column:1/-1"><strong>No trips saved yet</strong>Create a new trip and your completed/pending plan will appear here automatically.</div>';
            return;
        }
        tripsGrid.innerHTML = dbTrips.map(t => {
            const route = [t.origin, t.destination].filter(Boolean).join(' → ') || t.title || 'Trip';
            const dates = formatDateRange(t.departureDate, t.returnDate);
            const status = tripStatusLabel(t);
            const statusClass = status === 'Rejected' ? ' rejected' : status === 'Pending approval' ? ' pending' : '';
            const meta = [dates, t.travelers ? `${t.travelers} traveler${Number(t.travelers) === 1 ? '' : 's'}` : '', t.budgetLabel || '', t.qualityScore ? `Quality ${t.qualityScore}/100` : ''].filter(Boolean);
            const openLabel = t.legacy ? 'Open previous conversation →' : 'Open saved plan →';
            return '<article class="trip-history-card">'
                + '<div class="trip-history-top"><div><div class="trip-history-route">' + escapeHtml(route) + '</div><div class="trip-history-date">' + (t.legacy ? 'Recovered from conversation history · ' : 'Updated ') + escapeHtml(t.updatedAt ? new Date(t.updatedAt).toLocaleString() : '') + '</div></div>'
                + '<span class="trip-history-status' + statusClass + '">' + escapeHtml(status) + '</span></div>'
                + '<div class="trip-history-meta">' + meta.map(m => '<span class="trip-history-chip">' + escapeHtml(m) + '</span>').join('') + '</div>'
                + '<button type="button" class="trip-history-open" data-trip-id="' + escapeHtml(String(t.id)) + '">' + openLabel + '</button>'
                + '</article>';
        }).join('');
    };

    const loadDbTrips = async (renderView = false) => {
        const userId = userIdField.value.trim() || 'aaro_hi_user';
        try {
            const res = await fetch('/api/trips?userId=' + encodeURIComponent(userId) + '&limit=50');
            if (!res.ok) throw new Error('Unable to load trips');
            const items = await res.json();
            dbTrips = Array.isArray(items) ? items : [];
            if (renderView) renderDbTripsView();
            renderHistoryList();
        } catch (e) {
            if (renderView) tripsGrid.innerHTML = '<div class="trips-empty" style="grid-column:1/-1"><strong>Could not load My Trips</strong>Refresh and try again.</div>';
            console.warn('Could not load database trips', e);
        }
    };

    const openSavedTrip = async (id) => {
        try {
            const trip = dbTrips.find(t => String(t.id) === String(id));
            const userId = userIdField.value.trim() || 'aaro_hi_user';
            showHomeView();
            clearThreadDom();

            if (trip?.legacy) {
                const sourceMessageId = Math.abs(Number(id));
                const messageRes = await fetch('/api/chat/message/' + encodeURIComponent(sourceMessageId)
                    + '?userId=' + encodeURIComponent(userId));
                const sourceMessage = await messageRes.json().catch(() => ({}));
                if (!messageRes.ok || !sourceMessage.sessionId) throw new Error(safeUiErrorMessage());

                const res = await fetch('/api/chat/session/' + encodeURIComponent(sourceMessage.sessionId)
                    + '?userId=' + encodeURIComponent(userId) + '&limit=100');
                const messages = await res.json().catch(() => []);
                if (!res.ok || !Array.isArray(messages)) throw new Error(safeUiErrorMessage());
                let lastUser = '';
                messages.forEach(msg => {
                    if (msg.role === 'user') {
                        lastUser = msg.content || '';
                        renderUserMessage(lastUser, false);
                    } else {
                        renderAssistantMessage({ text: msg.content || '' }, false, lastUser);
                    }
                });
                scrollChat();
                return;
            }

            const res = await fetch('/api/trips/' + encodeURIComponent(id) + '?userId=' + encodeURIComponent(userId));
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.error || safeUiErrorMessage());
            appendAssistantMessage(data, false);
            scrollChat();
        } catch (e) {
            showToast(e?.message || safeUiErrorMessage());
        }
    };

    tripsGrid.addEventListener('click', event => {
        const btn = event.target.closest('[data-trip-id]');
        if (btn) openSavedTrip(btn.getAttribute('data-trip-id'));
    });
    tripsRefresh.addEventListener('click', () => loadDbTrips(true));

    const renderHistoryList = () => {
        chatHistoryList.innerHTML = '';
        const store = loadStore();

        const chatThreadId = (chat) => {
            if (chat?.threadId) return String(chat.threadId);
            for (const message of (chat?.messages || [])) {
                const id = message?.planData?.threadId;
                if (id) return String(id);
            }
            return '';
        };

        const dbThreadIds = new Set(dbTrips.map(t => String(t.threadId || '')).filter(Boolean));
        const localChats = store.chats
            .filter(chat => !dbThreadIds.has(chatThreadId(chat)))
            .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
            .slice(0, 12);

        // One unified newest-first history. Every row also carries its real
        // session/thread so Delete can remove the corresponding DB memory.
        const entries = [];
        dbTrips.forEach(trip => entries.push({
            kind: 'db',
            id: trip.id,
            threadId: String(trip.threadId || ''),
            legacy: Boolean(trip.legacy),
            title: [trip.origin, trip.destination].filter(Boolean).join(' → ') || trip.title || 'Saved trip',
            meta: tripStatusLabel(trip) + ' · ' + (trip.updatedAt ? new Date(trip.updatedAt).toLocaleDateString() : ''),
            updatedAt: trip.updatedAt ? new Date(trip.updatedAt).getTime() : 0
        }));
        localChats.forEach(chat => entries.push({
            kind: 'chat',
            id: chat.id,
            threadId: chatThreadId(chat),
            title: chat.title || 'Recent conversation',
            meta: 'Recent · ' + (chat.updatedAt ? new Date(chat.updatedAt).toLocaleDateString() : ''),
            updatedAt: chat.updatedAt || 0
        }));

        entries.sort((a, b) => b.updatedAt - a.updatedAt);

        if (!entries.length) {
            const empty = document.createElement('div');
            empty.className = 'chat-history-empty';
            empty.textContent = 'No trips or conversations yet.';
            chatHistoryList.appendChild(empty);
            return;
        }

        entries.slice(0, 12).forEach(entry => {
            const wrap = document.createElement('div');
            wrap.className = 'chat-history-item-wrap';

            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'chat-history-item' + (entry.kind === 'chat' && entry.id === currentChatId ? ' active' : '');
            const title = document.createElement('span');
            title.textContent = entry.title;
            if (entry.kind === 'db') {
                const status = document.createElement('span');
                status.className = 'db-status';
                status.textContent = 'DB';
                title.appendChild(status);
            }
            const meta = document.createElement('span');
            meta.className = 'meta';
            meta.textContent = entry.meta;
            btn.appendChild(title);
            btn.appendChild(meta);
            btn.onclick = () => entry.kind === 'db' ? openSavedTrip(entry.id) : openChat(entry.id);

            const deleteButton = document.createElement('button');
            deleteButton.type = 'button';
            deleteButton.className = 'chat-history-delete-btn';
            deleteButton.setAttribute('aria-label', 'Delete history');
            deleteButton.setAttribute('title', 'Delete');
            deleteButton.textContent = '🗑';
            deleteButton.addEventListener('click', async event => {
                event.preventDefault();
                event.stopPropagation();
                await deleteHistoryEntry(entry, wrap);
            });

            wrap.appendChild(btn);
            wrap.appendChild(deleteButton);
            chatHistoryList.appendChild(wrap);
        });
    };

    const deleteHistoryEntry = async (entry, rowElement) => {
        const userId = userIdField.value.trim() || 'aaro_hi_user';
        const title = entry.title || 'this history item';
        const confirmed = window.confirm(
            'Delete "' + title + '"?\n\nThis removes it from Recent History and deletes its saved conversation memory from the database.'
            + (entry.kind === 'db' && !entry.legacy ? '\n\nThe saved trip record will also be deleted.' : '')
        );
        if (!confirmed) return;

        try {
            const params = new URLSearchParams({ userId });
            if (entry.kind === 'db' && !entry.legacy) {
                params.set('tripId', String(entry.id));
            } else if (entry.threadId) {
                params.set('sessionId', String(entry.threadId));
            }

            // A local-only chat has no server session. It is removed from
            // browser history without making a pointless DB request.
            const hasServerTarget = params.has('tripId') || params.has('sessionId');
            if (hasServerTarget) {
                const res = await fetch('/api/history?' + params.toString(), { method: 'DELETE' });
                const data = await res.json().catch(() => ({}));
                if (!res.ok) throw new Error(data.error || 'Could not delete history');
            }

            const store = loadStore();
            const threadId = entry.threadId || '';
            store.chats = (store.chats || []).filter(chat => {
                if (chat.id === entry.id) return false;
                if (threadId && chatThreadIdForDelete(chat) === threadId) return false;
                return true;
            });
            saveStore(store);

            if (entry.kind === 'db') {
                dbTrips = dbTrips.filter(trip => String(trip.id) !== String(entry.id));
            }

            if (entry.kind === 'chat' && entry.id === currentChatId) {
                currentChatId = null;
                startedFreshChat = true;
                clearThreadDom();
                promptInput.value = '';
                resizePrompt();
            }

            renderHistoryList();
            renderDbTripsView();
            showToast('Deleted from Recent History and memory database.');
        } catch (error) {
            console.warn('Could not delete history', error);
            showToast(error?.message || 'Could not delete history.');
        }
    };

    const chatThreadIdForDelete = (chat) => {
        if (chat?.threadId) return String(chat.threadId);
        for (const message of (chat?.messages || [])) {
            const id = message?.planData?.threadId;
            if (id) return String(id);
        }
        return '';
    };

    const clearThreadDom = () => {
        chatThread.querySelectorAll('.chat-msg').forEach(node => node.remove());
        layout.classList.remove('has-chat');
        if (!chatThread.querySelector('.chat-empty')) {
            const empty = document.createElement('div');
            empty.className = 'chat-empty';
            empty.textContent = 'Your trip plans will appear here and stay as you chat.';
            chatThread.appendChild(empty);
        }
    };

    const openChat = async (chatId) => {
        showHomeView();
        const store = loadStore();
        const chat = store.chats.find(c => c.id === chatId);
        if (!chat) return;

        currentChatId = chatId;
        startedFreshChat = String(chatId).startsWith('chat-');
        clearThreadDom();

        let lastUser = '';
        let fallbackRestore = null;
        const isServerChat = String(chatId).startsWith('server-');
        const sessionId = chatThreadIdForDelete(chat);

        // Always refresh a server conversation before rendering it. This makes
        // Recent History independent of stale localStorage and restores the
        // exact structured result saved by the backend.
        let messages = chat.messages || [];
        if (isServerChat && sessionId) {
            try {
                const userId = userIdField.value.trim() || 'aaro_hi_user';
                const res = await fetch('/api/chat/session/' + encodeURIComponent(sessionId)
                    + '?userId=' + encodeURIComponent(userId) + '&limit=100');
                if (res.ok) {
                    const serverMessages = await res.json();
                    if (Array.isArray(serverMessages) && serverMessages.length) {
                        messages = serverMessages.map(msg => {
                            let planData = null;
                            if (msg.structuredData) {
                                try { planData = JSON.parse(msg.structuredData); } catch (ignored) {}
                            }
                            return {
                                role: msg.role,
                                text: msg.content || '',
                                planData
                            };
                        });
                    }
                }
            } catch (e) {
                console.warn('Could not refresh server conversation', e);
            }
        }

        // Old V13/V14 server rows may have no structuredData. Recover the
        // latest response directly from the LangGraph checkpoint instead of
        // falling back to the generic "Travel information" text card.
        const needsRestore = isServerChat && sessionId
            && messages.some(m => m.role === 'assistant' && !m.planData);
        if (needsRestore) {
            try {
                const userId = userIdField.value.trim() || 'aaro_hi_user';
                const restoreRes = await fetch('/api/plan/' + encodeURIComponent(sessionId)
                    + '/restore?userId=' + encodeURIComponent(userId));
                if (restoreRes.ok) {
                    fallbackRestore = await restoreRes.json();
                }
            } catch (e) {
                console.warn('Could not restore structured history result', e);
            }
        }

        let restoredApplied = false;
        messages.forEach(msg => {
            if (msg.role === 'user') {
                lastUser = msg.text || '';
                renderUserMessage(lastUser, false);
            } else {
                let planData = msg.planData;
                if (!planData && fallbackRestore && !restoredApplied) {
                    planData = fallbackRestore;
                    restoredApplied = true;
                }
                renderAssistantMessage(planData || {
                    status: '',
                    finalPlan: msg.text,
                    awaitingApproval: false,
                    pipeline: []
                }, false, lastUser);
            }
        });
        renderHistoryList();
        scrollChat();
    };

    newChatBtn.addEventListener('click', () => {
        startedFreshChat = true;
        currentChatId = null;
        ensureCurrentChat('New trip');
        clearThreadDom();
        renderHistoryList();
        promptInput.value = '';
        resizePrompt();
        loadingState.classList.remove('visible');
        submitButton.disabled = false;
        promptInput.focus();
    });

    if (composerNewChat) {
        composerNewChat.addEventListener('click', () => {
            newChatBtn.click();
        });
    }

    promptInput.addEventListener('input', () => {
        resizePrompt();
        updateComposerState();
        requestAnimationFrame(resizePrompt);
    });

    // Recalculate after fonts/layout settle and when the viewport changes.
    window.addEventListener('resize', resizePrompt);

    const markChatActive = () => {
        layout.classList.add('has-chat');
        const empty = chatThread.querySelector('.chat-empty');
        if (empty) {
            empty.remove();
        }
    };

    const scrollChat = () => {
        const pane = messagesPane || chatThread;
        pane.scrollTop = pane.scrollHeight;
    };

    const escapeHtml = (value) => String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');

    const formatInr = (amount) => {
        if (amount == null || amount === '') {
            return '—';
        }
        const num = Number(amount);
        if (Number.isNaN(num)) {
            return String(amount);
        }
        return '₹' + num.toLocaleString('en-IN', { maximumFractionDigits: 0 });
    };

    const formatDateRange = (start, end) => {
        const fmt = (iso) => {
            if (!iso) {
                return '';
            }
            const d = new Date(iso + 'T12:00:00');
            return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
        };
        if (start && end) {
            return fmt(start) + ' – ' + fmt(end);
        }
        return fmt(start || end) || 'Dates TBD';
    };

    const tripEmoji = (destination) => {
        const d = (destination || '').toLowerCase();
        if (d.includes('japan') || d.includes('tokyo')) return '🇯🇵';
        if (d.includes('paris') || d.includes('france')) return '🇫🇷';
        if (d.includes('bangkok') || d.includes('thailand')) return '🇹🇭';
        if (d.includes('dubai')) return '🇦🇪';
        return '✈️';
    };

    const tripTitle = (data) => {
        const trip = data.plan && data.plan.trip;
        if (trip && trip.title) {
            return trip.title;
        }
        const dest = (data.destination || (trip && trip.destination) || 'Trip').trim();
        if (dest.toLowerCase().includes('japan')) {
            return 'Japan Trip';
        }
        return dest + ' Trip';
    };

    const audienceLabel = (data, userRequest) => {
        const trip = data.plan && data.plan.trip;
        if (trip && trip.audienceLabel) {
            return trip.audienceLabel;
        }
        const text = ((userRequest || '') + ' ' + (data.travelStyle || '')).toLowerCase();
        if (text.includes('family')) {
            return '👨‍👩‍👧‍👦 Family';
        }
        if (text.includes('couple')) {
            return '💑 Couple';
        }
        if (text.includes('solo')) {
            return '🧳 Solo';
        }
        return data.travelers > 1 ? '👤 ' + data.travelers + ' travelers' : '👤 1 traveler';
    };

    const sectionBlock = (title, bodyHtml, emptyMessage) => {
        if (!bodyHtml && !emptyMessage) return '';
        const content = bodyHtml || '<p class="result-empty">' + escapeHtml(emptyMessage || 'Not available.') + '</p>';
        return '<section class="result-panel"><div class="result-panel-title">' + title + '</div><div>' + content + '</div></section>';
    };

    const formatKnowledgeText = (text) => {
        if (!text) return '';
        const safe = escapeHtml(String(text).trim());
        const paragraphs = safe.split(/\n\s*\n/).map(x => x.trim()).filter(Boolean);
        if (!paragraphs.length) return '';
        return paragraphs.map(p => {
            if (/^(?:[-*•]\s+)/m.test(p)) {
                const items = p.split(/\n/).map(x => x.replace(/^(?:[-*•]\s+)/,'').trim()).filter(Boolean);
                return '<ul>' + items.map(i => '<li>' + i + '</li>').join('') + '</ul>';
            }
            return '<p>' + p.replace(/\n/g, '<br>') + '</p>';
        }).join('');
    };

    const buildBudgetTable = (budget) => {
        if (!budget || !budget.lineItems || !budget.lineItems.length) return '';
        const rows = budget.lineItems.map(item =>
            '<div class="budget-card"><div class="label">' + escapeHtml(item.category || 'Item') + '</div><div class="amount">' + formatInr(item.amountInr) + '</div></div>').join('');
        let html = '<div class="budget-grid">' + rows + '</div>';
        if (budget.estimatedCost != null) {
            html += '<div class="budget-total-card"><span>Total estimated</span><strong>' + formatInr(budget.estimatedCost) + '</strong></div>';
        }
        return html;
    };

    const buildPlanReview = (data, userRequest) => {
        const validation = data.plan && data.plan.validation;
        const warnings = (data.semanticNotes || []).concat(data.validationErrors || []);
        if (validation && validation.reviewItems && validation.reviewItems.length) {
            const items = validation.reviewItems.map(item => (item.level === 'warn' ? '⚠ ' : '✓ ') + (item.text || '')).join(' · ');
            return '<div class="validation-banner">' + escapeHtml(items) + '</div>';
        }
        if (warnings.length) return '<div class="validation-banner">⚠ ' + escapeHtml(warnings.join(' · ')) + '</div>';
        if (data.ragJudge && data.ragJudge !== 'PASS') return '<div class="validation-banner">⚠ ' + escapeHtml(String(data.ragJudge)) + '</div>';
        return '<div class="result-chip success">✓ Validation passed</div>';
    };

    const activityTags = (activity) => {
        const tags = [];
        if (activity.indoorOutdoor && activity.indoorOutdoor !== 'mixed') tags.push(activity.indoorOutdoor);
        if (activity.familyFriendly) tags.push('family-friendly');
        if (activity.foodExperience) tags.push('food');
        if (activity.localExperience) tags.push('local');
        return tags;
    };

    const buildFlightsSection = (flights) => {
        if (!flights || !flights.length) return '';
        return flights.map(f => {
            const route = (f.origin && f.destination) ? f.origin + ' → ' + f.destination : '';
            const meta = [route, f.departureTime, f.arrivalTime, f.price].filter(Boolean).join(' · ');
            return '<div class="flight-card"><div class="item-title">' + escapeHtml([f.airline, f.flightNumber].filter(Boolean).join(' · ') || 'Flight option') + '</div>'
                + (meta ? '<div class="item-meta">' + escapeHtml(meta) + '</div>' : '')
                + (f.notes ? '<div class="item-note">' + escapeHtml(f.notes) + '</div>' : '') + '</div>';
        }).join('');
    };

    const cleanHotelText = (value) => String(value || '')
        .replace(/^#{1,6}\s*/gm, '')
        .replace(/^[-*]\s*/gm, '')
        .replace(/\s+/g, ' ')
        .trim();

    const buildHotelsSection = (hotels) => {
        if (!hotels || !hotels.length) return '';
        const visibleCount = 4;
        const cards = hotels.map((h, index) => {
            const meta = [h.area, h.priceRange, h.rating ? '★ ' + h.rating : ''].filter(Boolean).join(' · ');
            const note = cleanHotelText(h.notes);
            const extraClass = index >= visibleCount ? ' is-extra' : '';
            return '<div class="hotel-card' + extraClass + '"><div class="item-title">' + escapeHtml(cleanHotelText(h.name) || 'Hotel') + '</div>'
                + (meta ? '<div class="item-meta">' + escapeHtml(meta) + '</div>' : '')
                + (note ? '<div class="item-note">' + escapeHtml(note) + '</div>' : '') + '</div>';
        }).join('');
        const more = hotels.length > visibleCount
            ? '<button type="button" class="hotel-more-btn" data-hotels-more>＋ ' + (hotels.length - visibleCount) + ' more hotel options</button>'
            : '';
        return '<div class="hotel-list">' + cards + '</div>' + more;
    };

    const buildItinerarySection = (itinerary) => {
        const days = itinerary && itinerary.days ? itinerary.days : [];
        if (!days.length) return '';
        let html = '<div class="itin-list itin-compact">';
        days.forEach(day => {
            const acts = (day.activities || []).filter(a => a && a.name).map(a => {
                const tags = activityTags(a);
                return '<li>' + escapeHtml(a.name) + (a.type ? ' · ' + escapeHtml(a.type) : '') + (tags.length ? ' <span class="item-meta">(' + escapeHtml(tags.join(' · ')) + ')</span>' : '') + '</li>';
            }).join('');
            html += '<div class="itin-item"><div class="itin-day-badge">DAY ' + escapeHtml(String(day.day || '')) + '</div><div>'
                + '<div class="itin-day-title">' + escapeHtml(day.title || 'Explore') + '</div>'
                + (acts ? '<ul class="itin-activities">' + acts + '</ul>' : '') + '</div></div>';
        });
        return html + '</div>';
    };

    const buildWeatherSection = (weather) => {
        if (!weather || (!weather.summary && !weather.location)) return '';
        const summary = weather.summary || 'Weather information available';
        const detail = [weather.location, weather.rainLikely ? 'Rain may be likely' : ''].filter(Boolean).join(' · ');
        return '<div class="weather-card"><div class="weather-icon">🌤️</div><div><div class="weather-main">' + escapeHtml(summary) + '</div>'
            + (detail ? '<div class="weather-detail">' + escapeHtml(detail) + '</div>' : '') + '</div></div>';
    };

    const buildAgentDetails = (data) => {
        const exec = data.execution || {};
        const rawSteps = exec.timeline || data.executionTimeline || data.pipeline || [];
        const stepsArr = dedupeSteps(rawSteps).slice(-16);
        const sourcesArr = [...new Set(exec.sources || data.sources || [])];
        const history = exec.executionHistory || data.executionHistory || '';
        const ragUsed = !!(exec.ragUsed || data.ragUsed);
        const ragAnswer = exec.ragAnswer || data.ragAnswer || '';
        const ragSources = exec.ragSources || data.ragSources || [];
        const ragQuery = exec.ragQuery || data.ragQuery || '';
        const ragDecision = exec.ragDecision || data.ragDecision || '';
        const ragMethod = exec.ragRetrievalMethod || data.ragRetrievalMethod || '';
        const ragScore = exec.ragEvidenceScore ?? data.ragEvidenceScore;
        const ragCandidates = exec.ragCandidateCount ?? data.ragCandidateCount;
        const ragReranked = exec.ragRerankedCount ?? data.ragRerankedCount;
        const ragIterations = exec.ragIterations ?? data.ragIterations;
        if (!stepsArr.length && !sourcesArr.length && !history && !ragUsed) return '';
        let body = '<div class="agent-details-body">';
        if (ragUsed) {
            body += '<div class="agent-subsection rag-observability"><div class="agent-subtitle">🧠 RAG result</div>';
            if (ragAnswer) {
                body += '<div class="rag-answer-preview">' + formatKnowledgeText(ragAnswer) + '</div>';
            } else {
                body += '<div class="rag-empty">RAG retrieval completed, but no answer was generated.</div>';
            }
            const meta = [];
            if (ragDecision) meta.push(ragDecision);
            if (ragMethod) meta.push(ragMethod);
            if (Number.isFinite(Number(ragScore))) meta.push('evidence ' + Number(ragScore).toFixed(2));
            if (Number.isFinite(Number(ragCandidates))) meta.push('candidates ' + ragCandidates);
            if (Number.isFinite(Number(ragReranked))) meta.push('reranked ' + ragReranked);
            if (Number.isFinite(Number(ragIterations))) meta.push('iteration ' + ragIterations);
            if (meta.length) body += '<div class="item-meta">' + escapeHtml(meta.join(' · ')) + '</div>';
            if (ragQuery) body += '<div class="rag-query">Query: ' + escapeHtml(ragQuery) + '</div>';
            if (ragSources.length) body += '<div class="agent-subtitle rag-sources-title">Sources</div><pre class="agent-pre">' + escapeHtml(ragSources.join('\n')) + '</pre>';
            body += '</div>';
        }
        if (stepsArr.length) {
            body += '<div class="agent-subsection"><div class="agent-subtitle">Execution</div><div class="execution-mini">'
                + stepsArr.map(step => '<span class="execution-pill">' + escapeHtml(formatStepLine(step)) + '</span>').join('') + '</div></div>';
        }
        if (sourcesArr.length) body += '<div class="agent-subsection"><div class="agent-subtitle">Sources</div><pre class="agent-pre">' + escapeHtml(sourcesArr.join('\n')) + '</pre></div>';
        if (history) body += '<div class="agent-subsection"><div class="agent-subtitle">History</div><pre class="agent-pre">' + escapeHtml(history) + '</pre></div>';
        body += '</div>';
        return '<details class="agent-details"><summary>🔎 Agent execution & sources</summary>' + body + '</details>';
    };

    const safeUiErrorMessage = () =>
        "We couldn\'t complete your travel plan. Please try again.";

    const humanizeKnowledgeSource = (source) => {
        const raw = String(source || '').trim();
        if (!raw) return '';
        return raw
            .replace(/^.*[\\/]/, '')
            .replace(/\\.(md|txt|pdf)$/i, '')
            .replace(/[-_]+/g, ' ')
            .replace(/\\b\\w/g, ch => ch.toUpperCase());
    };

    const buildKnowledgeGuidanceSection = (data, plan) => {
        const exec = data.execution || {};
        const knowledge = plan.knowledge || {};
        const answer = String(knowledge.answer || exec.ragAnswer || data.ragAnswer || '').trim();
        const available = knowledge.available === true || !!answer;
        if (!available || !answer) return '';

        const topics = Array.isArray(knowledge.topics) && knowledge.topics.length
            ? knowledge.topics
            : [];
        const sources = Array.isArray(knowledge.sources) && knowledge.sources.length
            ? knowledge.sources
            : (Array.isArray(exec.ragSources) ? exec.ragSources : []);
        const destination = String(knowledge.destination || '').trim();
        const title = String(knowledge.title || (destination
            ? 'Travel Knowledge & Guidance for ' + destination
            : 'Travel Knowledge & Guidance')).trim();
        const query = String(knowledge.query || exec.ragQuery || '').trim();

        let html = '<section id="section-knowledge" class="workspace-card full knowledge-guidance-card">'
            + '<div class="workspace-card-head"><div>'
            + '<div class="workspace-card-title">🧠 ' + escapeHtml(title) + '</div>'
            + '<div class="workspace-card-sub">Durable travel guidance from the knowledge base</div>'
            + '</div><span class="knowledge-guidance-badge">✓ Grounded guidance</span></div>';

        if (topics.length) {
            html += '<div class="knowledge-topics">' + topics.slice(0, 8).map(topic =>
                '<span class="knowledge-topic">' + escapeHtml(String(topic).replace(/[_-]+/g, ' ')) + '</span>'
            ).join('') + '</div>';
        }
        html += '<div class="workspace-content knowledge-answer">' + formatKnowledgeText(answer) + '</div>';
        if (sources.length) {
            html += '<div class="knowledge-sources">' + sources.slice(0, 6).map(source =>
                '<span class="knowledge-source">' + escapeHtml(humanizeKnowledgeSource(source)) + '</span>'
            ).join('') + '</div>';
        }
        if (query) html += '<div class="knowledge-query">Knowledge query: ' + escapeHtml(query) + '</div>';
        html += '</section>';
        return html;
    };

    const buildSpecialistKnowledgeCard = (data, plan) => {
        const exec = data.execution || {};
        const knowledge = plan.knowledge || {};
        const answer = String(knowledge.answer || exec.ragAnswer || data.ragAnswer || '').trim();
        if (!answer) return '';
        const sources = Array.isArray(knowledge.sources) && knowledge.sources.length
            ? knowledge.sources : (Array.isArray(exec.ragSources) ? exec.ragSources : []);
        const topics = Array.isArray(knowledge.topics) ? knowledge.topics : [];
        let html = '<section class="specialist-card specialist-answer-card knowledge-guidance-card">'
            + '<div class="knowledge-guidance-head"><div class="specialist-card-title">💡 Travel Tips & Guidance</div>'
            + '<span class="knowledge-guidance-badge">✓ Grounded</span></div>';
        if (topics.length) {
            html += '<div class="knowledge-topics">' + topics.slice(0, 8).map(topic =>
                '<span class="knowledge-topic">' + escapeHtml(String(topic).replace(/[_-]+/g, ' ')) + '</span>'
            ).join('') + '</div>';
        }
        html += '<div class="specialist-answer">' + formatKnowledgeText(answer) + '</div>';
        if (sources.length) {
            html += '<div class="knowledge-sources">' + sources.slice(0, 6).map(source =>
                '<span class="knowledge-source">' + escapeHtml(humanizeKnowledgeSource(source)) + '</span>'
            ).join('') + '</div>';
        }
        html += '</section>';
        return html;
    };

    const buildSpecialistResponseHtml = (data, userRequest) => {
        const plan = data.plan || {};
        const trip = plan.trip || {};
        const type = String(data.requestType || trip.requestType || 'GENERAL').toUpperCase();
        const location = String(
            (plan.weather && plan.weather.location) || trip.destination || data.destination || ''
        ).trim();
        const weather = plan.weather;
        const flights = Array.isArray(plan.flights) ? plan.flights : [];
        const hotels = Array.isArray(plan.hotels) ? plan.hotels : [];
        const budget = plan.budget;
        const exec = data.execution || {};
        const answer = String(exec.ragAnswer || data.ragAnswer || plan.tips || data.finalPlan || '').trim();

        let title = 'Travel information';
        let subtitle = 'Answer based on your latest request';
        let body = '';
        let icon = '✦';

        if (type === 'WEATHER') {
            title = location ? 'Weather in ' + location : 'Weather details';
            subtitle = 'Current or forecast conditions';
            icon = '☀️';
            body = buildWeatherSection(weather);
        } else if (type === 'FLIGHT_SEARCH') {
            title = location ? 'Flight options for ' + location : 'Flight options';
            subtitle = 'Available flight results';
            icon = '✈️';
            body = buildFlightsSection(flights);
        } else if (type === 'HOTEL_SEARCH') {
            title = location ? 'Hotels in ' + location : 'Hotel options';
            subtitle = 'Accommodation recommendations';
            icon = '🏨';
            body = buildHotelsSection(hotels);
        } else if (type === 'BUDGET') {
            title = 'Travel budget';
            subtitle = 'Cost estimate for your request';
            icon = '💰';
            body = buildBudgetTable(budget);
        } else if (type === 'RESEARCH') {
            title = location ? 'Things to do in ' + location : 'Travel research';
            subtitle = 'Destination recommendations and current research';
            icon = '🔎';
            body = answer ? '<div class="specialist-answer">' + formatKnowledgeText(answer) + '</div>' : '';
        } else if (type === 'MULTI_CAPABILITY' || type === 'MULTI_INTENT') {
            title = location ? 'Travel information for ' + location : 'Travel information';
            subtitle = 'Results for the capabilities requested in this message';
            icon = '✦';
            const parts = [];
            if (weather && (weather.summary || weather.location)) parts.push('<div class="specialist-subsection"><h3>☀️ Weather</h3>' + buildWeatherSection(weather) + '</div>');
            if (flights.length) parts.push('<div class="specialist-subsection"><h3>✈️ Flights</h3>' + buildFlightsSection(flights) + '</div>');
            if (hotels.length) parts.push('<div class="specialist-subsection"><h3>🏨 Hotels</h3>' + buildHotelsSection(hotels) + '</div>');
            if (budget) parts.push('<div class="specialist-subsection"><h3>💰 Budget</h3>' + buildBudgetTable(budget) + '</div>');
            // Knowledge is rendered as its own Travel Tips & Guidance card below.
            // Keeping it separate prevents the RAG answer from being duplicated
            // inside the live specialist result.
            body = parts.join('') ;
        } else {
            title = location ? 'Travel information for ' + location : 'Travel information';
            subtitle = 'Knowledge-backed answer to your question';
            body = answer ? '<div class="specialist-answer">' + formatKnowledgeText(answer) + '</div>' : '';
        }

        if (!body) {
            body = '<div class="specialist-empty">No additional structured details were returned for this request.</div>';
        }

        return '<div class="specialist-dashboard">'
            + '<section class="specialist-card specialist-hero">'
            + '<div class="specialist-icon">' + icon + '</div>'
            + '<div><div class="specialist-eyebrow">AgenticTripAI · ' + escapeHtml(type.replace(/_/g, ' ')) + '</div>'
            + '<h2>' + escapeHtml(title) + '</h2><p>' + escapeHtml(subtitle) + '</p></div>'
            + '</section>'
            + '<section class="specialist-card specialist-content">'
            + '<div class="specialist-card-head"><div><strong>' + escapeHtml(title) + '</strong><span>Requested information</span></div>'
            + '<span class="complete-badge">✓ Complete</span></div>'
            + body
            + '</section>'
            + ((type === 'RESEARCH' || type === 'GENERAL' || type === 'TRAVEL_INFORMATION') ? '' : buildSpecialistKnowledgeCard(data, plan))
            + '</div>';
    };

    const buildPlanCardHtml = (data, userRequest) => {
        const plan = data.plan || {};
        const trip = plan.trip || {};
        const tips = plan.tips || data.finalPlan || '';
        if (data.status === 'ERROR' || (tips && String(tips).startsWith('Error:'))) {
            return '<div class="result-card"><div class="result-hero"><div class="result-eyebrow">Agent response</div><h2 class="result-title">Something went wrong</h2></div><div class="result-body"><div class="validation-banner">⚠ ' + escapeHtml(safeUiErrorMessage()) + '</div></div></div>';
        }

        const hasItineraryData = !!(plan.itinerary && Array.isArray(plan.itinerary.days) && plan.itinerary.days.length);
        const isTripPlan = data.tripPlanning === true || hasItineraryData;
        const requestType = String(data.requestType || trip.requestType || '').toUpperCase();
        if (!isTripPlan && requestType !== 'TRIP_PLANNING') {
            return buildSpecialistResponseHtml(data, userRequest);
        }

        const hasFlights = Array.isArray(plan.flights) && plan.flights.length;
        const hasHotels = Array.isArray(plan.hotels) && plan.hotels.length;
        const hasItinerary = hasItineraryData;
        const hasBudget = !!(plan.budget && Array.isArray(plan.budget.lineItems) && plan.budget.lineItems.length);
        const hasWeather = !!(plan.weather && (plan.weather.summary || plan.weather.location));
        const knowledge = plan.knowledge || {};
        const exec = data.execution || {};
        const hasKnowledge = !!(knowledge.available === true || knowledge.answer || exec.ragAnswer || data.ragAnswer);
        const quality = trip.qualityScore != null ? trip.qualityScore : (plan.validation && plan.validation.quality && plan.validation.quality.overall > 0 ? Math.round(plan.validation.quality.overall * 100) : null);
        const responseStatus = String(data.status || '').toUpperCase();
        const tripStatus = String(trip.status || '').toUpperCase();
        const awaitingApproval = Boolean(
            data.awaitingApproval === true
            || trip.awaitingApproval === true
            || responseStatus === 'PENDING_APPROVAL'
            || tripStatus === 'PENDING_APPROVAL'
        );
        const complete = !awaitingApproval && (responseStatus === 'COMPLETE' || tripStatus === 'COMPLETE');
        const origin = trip.origin || data.origin || '';
        const destination = trip.destination || data.destination || '';
        const route = origin && destination ? origin + ' → ' + destination : (destination || 'Travel plan');
        const nights = trip.nights || '';
        const travelers = data.travelers || trip.travelers || 1;
        const budgetLabel = trip.budgetLabel || data.budgetLabel || '';

        let html = '<div class="travel-dashboard"><div class="travel-main">';
        html += '<div class="trip-banner"><div class="trip-eyebrow">AI Travel Plan</div><div class="trip-route">' + escapeHtml(route) + '</div>';
        html += '<div class="trip-facts">';
        if (trip.departureDate || trip.returnDate) html += '<span class="trip-fact">📅 ' + escapeHtml(formatDateRange(trip.departureDate, trip.returnDate)) + '</span>';
        if (nights) html += '<span class="trip-fact">🌙 ' + escapeHtml(String(nights)) + ' nights</span>';
        html += '<span class="trip-fact">👤 ' + escapeHtml(String(travelers)) + ' traveler' + (Number(travelers) === 1 ? '' : 's') + '</span>';
        if (budgetLabel) html += '<span class="trip-fact">💰 ' + escapeHtml(budgetLabel) + '</span>';
        if (quality != null) html += '<span class="trip-fact">✓ Quality ' + escapeHtml(String(quality)) + '/100</span>';
        html += '</div></div>';
        html += '<div class="workspace-tabs"><button type="button" class="workspace-tab active" data-section-target="overview">Overview</button>' + (hasFlights ? '<button type="button" class="workspace-tab" data-section-target="flights">✈ Flights</button>' : '') + (hasHotels ? '<button type="button" class="workspace-tab" data-section-target="hotels">🏨 Hotels</button>' : '') + (hasItinerary ? '<button type="button" class="workspace-tab" data-section-target="itinerary">🗓 Itinerary</button>' : '') + (hasWeather ? '<button type="button" class="workspace-tab" data-section-target="weather">☀ Weather</button>' : '') + (hasBudget ? '<button type="button" class="workspace-tab" data-section-target="budget">💰 Budget</button>' : '') + (hasKnowledge ? '<button type="button" class="workspace-tab" data-section-target="knowledge">🧠 Knowledge</button>' : '') + '</div>';
        html += '<div class="workspace-grid">';
        if (hasFlights) html += '<section id="section-flights" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">✈️ Flights</div><div class="workspace-card-sub">Best available flight options</div></div><span class="ready-badge">✓ ' + (complete ? 'Complete' : 'Ready') + '</span></div><div class="workspace-content">' + buildFlightsSection(plan.flights) + '</div></section>';
        if (hasHotels) html += '<section id="section-hotels" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">🏨 Hotels</div><div class="workspace-card-sub">Accommodation recommendations</div></div><span class="ready-badge">✓ ' + (complete ? 'Complete' : 'Ready') + '</span></div><div class="workspace-content">' + buildHotelsSection(plan.hotels) + '</div></section>';
        if (hasWeather) html += '<section id="section-weather" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">☀️ Weather</div><div class="workspace-card-sub">Travel-date forecast</div></div><span class="ready-badge">✓ ' + (complete ? 'Complete' : 'Ready') + '</span></div><div class="workspace-content">' + buildWeatherSection(plan.weather) + '</div></section>';
        if (hasBudget) html += '<section id="section-budget" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">💰 Budget</div><div class="workspace-card-sub">Estimated trip cost</div></div><span class="ready-badge">✓ ' + (complete ? 'Complete' : 'Ready') + '</span></div><div class="workspace-content">' + buildBudgetTable(plan.budget) + '</div></section>';
        if (hasItinerary) html += '<section id="section-itinerary" class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">🗓️ Day-wise Itinerary</div><div class="workspace-card-sub">Your planned activities by day</div></div><span class="ready-badge">✓ ' + (complete ? 'Complete' : 'Ready') + '</span></div><div class="workspace-content itin-compact">' + buildItinerarySection(plan.itinerary) + '</div></section>';
        html += buildKnowledgeGuidanceSection(data, plan);
        if (tips) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">💡 Travel Tips</div><div class="workspace-card-sub">Practical trip-specific suggestions</div></div></div><div class="workspace-content knowledge-answer">' + formatKnowledgeText(tips) + '</div></section>';
        html += '</div>';
        if (data.validationErrors?.length || data.semanticNotes?.length || data.ragJudge) html += '<section class="workspace-card full" style="margin-top:14px"><div class="workspace-card-title">✅ Validation</div><div class="workspace-content" style="margin-top:10px">' + buildPlanReview(data, userRequest) + '</div></section>';
        html += '</div><aside class="travel-side">';
        html += '<section class="trip-side-card"><div class="trip-side-title">📋 Trip Summary</div>';
        html += '<div class="summary-row"><span>Route</span><strong>' + escapeHtml(route) + '</strong></div>';
        if (trip.departureDate || trip.returnDate) html += '<div class="summary-row"><span>Dates</span><strong>' + escapeHtml(formatDateRange(trip.departureDate, trip.returnDate)) + '</strong></div>';
        html += '<div class="summary-row"><span>Travelers</span><strong>' + escapeHtml(String(travelers)) + '</strong></div>';
        if (budgetLabel) html += '<div class="summary-row"><span>Budget</span><strong>' + escapeHtml(budgetLabel) + '</strong></div>';
        if (quality != null) html += '<div class="summary-row"><span>Quality</span><strong>' + escapeHtml(String(quality)) + '/100</strong></div>';
        html += '</section>';
        const componentStatus = responseStatus === 'REJECTED' ? 'Rejected' : complete ? 'Complete' : 'Ready';
        const componentClass = responseStatus === 'REJECTED' ? ' rejected' : complete ? ' complete' : '';
        html += '<section class="trip-side-card"><div class="trip-side-title">⚙ Plan Status</div><div class="status-list">';
        if (hasFlights) html += '<div class="status-row"><span class="status-name"><i class="status-dot' + componentClass + '"></i>Flights</span><span class="status-value' + componentClass + '">' + componentStatus + '</span></div>';
        if (hasHotels) html += '<div class="status-row"><span class="status-name"><i class="status-dot' + componentClass + '"></i>Hotels</span><span class="status-value' + componentClass + '">' + componentStatus + '</span></div>';
        if (hasWeather) html += '<div class="status-row"><span class="status-name"><i class="status-dot' + componentClass + '"></i>Weather</span><span class="status-value' + componentClass + '">' + componentStatus + '</span></div>';
        if (hasBudget) html += '<div class="status-row"><span class="status-name"><i class="status-dot' + componentClass + '"></i>Budget</span><span class="status-value' + componentClass + '">' + componentStatus + '</span></div>';
        if (hasItinerary) html += '<div class="status-row"><span class="status-name"><i class="status-dot' + componentClass + '"></i>Itinerary</span><span class="status-value' + componentClass + '">' + componentStatus + '</span></div>';
        html += '</div></section>';
        if (awaitingApproval && data.threadId && data.tripPlanning !== false) {
            html += '<section class="final-decision"><div class="decision-status pending"><i class="decision-dot"></i> Waiting for your decision</div><h3>Ready to finalize?</h3><p>Review the complete plan. Approve it, request a change, or reject it.</p><div class="final-actions"><button type="button" class="final-approve" data-plan-action="approve">✓ Approve</button><button type="button" class="final-modify" data-plan-action="modify">✎ Modify</button><button type="button" class="final-reject" data-plan-action="reject">✕ Reject</button></div></section>';
        } else if (complete) {
            html += '<section class="final-decision"><div class="decision-status"><i class="decision-dot"></i> Plan confirmed</div><h3>✓ Trip plan confirmed</h3><p>This plan has already been finalized.</p></section>';
        }
        html += '</aside></div>';
        return html;
    };

    const findUserRequestForPlan = (beforeNode) => {
        const users = chatThread.querySelectorAll('.chat-msg.user .chat-body');
        if (!users.length) {
            return '';
        }
        if (!beforeNode) {
            return users[users.length - 1].textContent || '';
        }
        let last = '';
        for (const row of chatThread.querySelectorAll('.chat-msg')) {
            if (row === beforeNode) {
                break;
            }
            if (row.classList.contains('user')) {
                last = row.querySelector('.chat-body')?.textContent || '';
            }
        }
        return last;
    };

    const renderUserMessage = (text, persist) => {
        markChatActive();
        const row = document.createElement('div');
        row.className = 'chat-msg user';
        row.innerHTML =
            '<div class="chat-avatar">You</div>' +
            '<div class="chat-bubble"><div class="chat-role">You</div><div class="chat-body"></div></div>';
        row.querySelector('.chat-body').textContent = text;
        chatThread.appendChild(row);
        scrollChat();
        if (persist) {
            ensureCurrentChat(text);
            persistMessage('user', text, null);
        }
    };

    const renderAssistantMessage = (data, persist, userRequestHint) => {
        markChatActive();
        const row = document.createElement('div');
        row.className = 'chat-msg assistant';
        row.innerHTML =
            '<div class="chat-avatar">AI</div>' +
            '<div class="chat-bubble">' +
            '<div class="chat-role">AgenticTripAI</div>' +
            '<div class="chat-body"></div>' +
            '<div class="chat-actions"></div>' +
            '</div>';
        const userRequest = userRequestHint || findUserRequestForPlan(row);
        const body = row.querySelector('.chat-body');
        const hasStructuredPlan = !!(data && data.threadId && data.plan);
        if (hasStructuredPlan) {
            body.innerHTML = buildPlanCardHtml(data, userRequest);
        } else {
            const text = data && (data.finalPlan || data.text) ? (data.finalPlan || data.text) : 'Plan response available.';
            body.innerHTML = '<div class="server-memory-card">' + formatKnowledgeText(text) + '</div>';
        }
        body.querySelectorAll('[data-section-target]').forEach(tab => {
            tab.addEventListener('click', () => {
                body.querySelectorAll('.workspace-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                const target = tab.dataset.sectionTarget;
                if (target !== 'overview') body.querySelector('#section-' + target)?.scrollIntoView({behavior:'smooth', block:'center'});
                else body.querySelector('.trip-banner')?.scrollIntoView({behavior:'smooth', block:'start'});
            });
        });
        chatThread.appendChild(row);
        const finalActions = row.querySelectorAll('[data-plan-action]');
        finalActions.forEach(btn => {
            btn.addEventListener('click', () => {
                const action = btn.getAttribute('data-plan-action');
                if (action === 'approve') decide('/api/plan/approve', data.threadId, null, row);
                else if (action === 'reject') decide('/api/plan/reject', data.threadId, null, row);
                else if (action === 'modify') {
                    const notes = window.prompt('What should we change?', '');
                    if (notes && notes.trim()) decide('/api/plan/modify', data.threadId, notes.trim(), row);
                }
            });
        });
        scrollChat();
        if (persist) {
            ensureCurrentChat(data.destination || data.routeSummary || 'Trip plan');
            persistMessage('assistant', formatPlanSummary(data), data);
        }
        return row;
    };

    const appendUserMessage = (text) => renderUserMessage(text, true);
    const appendAssistantMessage = (data) => renderAssistantMessage(data, true);

    const normalizeSteps = (steps) => (steps || []).filter(step => step && step.node);

    const dedupeSteps = (steps) => {
        const seen = new Set();
        return normalizeSteps(steps).filter(step => {
            const key = (step.attempt || 1) + '|' + step.node + '|' + (step.detail || '');
            if (seen.has(key)) {
                return false;
            }
            seen.add(key);
            return true;
        });
    };

    const formatStepLine = (step) => {
        const status = (step.status || '').toUpperCase();
        const icon = status === 'PARTIAL' || status === 'WARN' ? '⚠ '
            : status === 'SKIPPED' || status === 'SKIP' ? '○ '
            : status === 'RETRYING' ? '↻ '
            : status === 'FAILED' ? '✕ '
            : status === 'WAITING_HUMAN' ? '⏸ '
            : '✓ ';
        return icon + step.node
            + (step.durationMs ? ' (' + (step.durationMs / 1000).toFixed(1) + 's)' : '')
            + (step.model ? ' [' + step.model + ']' : '')
            + (step.toolCalls ? ' tools=' + step.toolCalls : '')
            + (step.inputTokens ? ' tok=' + step.inputTokens + '/' + (step.outputTokens || 0) : '')
            + (step.detail ? ' — ' + step.detail : '');
    };

    const formatPlanSummary = (data) => {
        const title = tripTitle(data || {});
        const route = data && data.origin && data.destination
            ? data.origin + ' → ' + data.destination
            : (data && data.routeSummary) || title;
        const quality = data && data.planQuality && data.planQuality.overall > 0
            ? ' · Quality ' + Math.round(data.planQuality.overall * 100) + '/100' : '';
        return title + ' — ' + route + quality;
    };

    const formatPlan = (data) => formatPlanSummary(data);


    const createLiveResponse = () => {
        const row = document.createElement('div');
        row.className = 'live-response';
        row.innerHTML = '<div class="chat-avatar">AI</div><div class="live-response-card">'
            + '<div class="live-head"><div class="live-title">AgenticTripAI <span class="live-dots"><i></i><i></i><i></i></span></div><div class="live-state">Understanding request…</div></div>'
            + '<div class="live-steps"></div></div>';
        chatThread.appendChild(row);
        scrollChat();
        return {
            row,
            state: row.querySelector('.live-state'),
            steps: row.querySelector('.live-steps'),
            seen: new Set()
        };
    };

    const updateLiveResponse = (live, node) => {
        if (!live || !node) return;
        const normalized = String(node).replace(/^__+|__+$/g, '').replace(/[-_]+/g, ' ');
        live.state.textContent = normalized ? 'Running ' + normalized + '…' : 'Working…';
        const key = normalized.toLowerCase();
        if (!live.seen.has(key)) {
            live.seen.add(key);
            const pill = document.createElement('span');
            pill.className = 'live-step active';
            pill.textContent = '✓ ' + normalized;
            live.steps.appendChild(pill);
        }
        live.steps.querySelectorAll('.live-step').forEach(p => p.classList.remove('active'));
        const last = live.steps.lastElementChild;
        if (last) last.classList.add('active');
        scrollChat();
    };

    const removeLiveResponse = (live) => {
        if (live && live.row && live.row.isConnected) live.row.remove();
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        syncUserIdField();

        const text = promptInput.value.trim();
        if (!text) {
            return;
        }

        const formData = new FormData(form);
        const payload = Object.fromEntries(formData.entries());
        payload.preferences = text;
        payload.prompt = text;

        appendUserMessage(text);
        promptInput.value = '';
        submitButton.disabled = true;
        loadingState.classList.add('visible');
        const liveResponse = createLiveResponse();

        try {
            const startRes = await fetch('/api/plan/start', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const startedBody = await startRes.text();
            if (!startRes.ok) {
                throw new Error(startedBody || 'Request failed');
            }
            const started = JSON.parse(startedBody);
            const threadId = started.threadId;
            const statusSpan = loadingState.querySelectorAll('span')[1];
            await new Promise((resolve, reject) => {
                let finished = false;
                const es = new EventSource('/api/plan/' + encodeURIComponent(threadId) + '/events');
                es.addEventListener('node', (evt) => {
                    try {
                        const data = JSON.parse(evt.data);
                        if (statusSpan && data.node) {
                            statusSpan.textContent = 'Running ' + data.node + '…';
                            updateLiveResponse(liveResponse, data.node);
                        }
                    } catch (ignored) {}
                });
                es.addEventListener('complete', async (evt) => {
                    finished = true;
                    es.close();
                    try {
                        const wrapper = JSON.parse(evt.data);
                        const data = wrapper.plan || wrapper;
                        try {
                            const hist = await fetch('/api/plan/' + encodeURIComponent(threadId) + '/history').then(r => r.json());
                            if (!data.execution) {
                                data.execution = {};
                            }
                            data.execution.executionHistory = (hist.snapshots || []).map(s =>
                                (s.node || '?') + ' → ' + (s.next || 'end')
                                + (s.planQuality != null ? ' q=' + (s.planQuality * 100).toFixed(0) + '%' : '')).join('\n');
                            if (hist.planQuality) {
                                data.planQuality = hist.planQuality;
                            }
                            if (hist.nodeFailure) {
                                data.nodeFailure = hist.nodeFailure;
                            }
                            if (hist.semanticNotes) {
                                data.semanticNotes = hist.semanticNotes;
                            }
                            if (hist.executionTimeline && hist.executionTimeline.length) {
                                data.execution.timeline = hist.executionTimeline;
                            }
                        } catch (ignored) {}
                        removeLiveResponse(liveResponse);
                        appendAssistantMessage(data);
                        loadDbTrips(false);
                        resolve();
                    } catch (error) {
                        reject(error);
                    }
                });
                es.addEventListener('failed', (evt) => {
                    finished = true;
                    es.close();
                    removeLiveResponse(liveResponse);
                    try {
                        const data = JSON.parse(evt.data);
                        reject(new Error(safeUiErrorMessage()));
                    } catch (error) {
                        reject(error);
                    }
                });
                es.onerror = () => {
                    if (finished) {
                        return;
                    }
                    es.close();
                    reject(new Error(safeUiErrorMessage()));
                };
            });
        } catch (error) {
            removeLiveResponse(liveResponse);
            appendAssistantMessage({
                status: 'ERROR',
                finalPlan: safeUiErrorMessage(),
                awaitingApproval: false,
                pipeline: []
            });
        } finally {
            submitButton.disabled = false;
            loadingState.classList.remove('visible');
            updateComposerState();
            promptInput.focus();
        }
    });

    chatThread.addEventListener('click', (event) => {
        const moreButton = event.target.closest('[data-hotels-more]');
        if (!moreButton) return;
        const list = moreButton.previousElementSibling;
        if (!list || !list.classList.contains('hotel-list')) return;
        const expanded = list.classList.toggle('expanded');
        moreButton.textContent = expanded
            ? '− Show fewer hotel options'
            : '＋ Show more hotel options';
    });

    const decide = async (url, threadId, notes, sourceRow) => {
        if (!threadId) {
            appendAssistantMessage({ text: 'This plan is no longer available for approval. Please create a new trip plan.' }, false);
            return;
        }
        loadingState.classList.add('visible');
        submitButton.disabled = true;
        if (sourceRow) {
            sourceRow.querySelectorAll('[data-plan-action]').forEach(btn => {
                btn.disabled = true;
            });
        }
        try {
            const res = await fetch(url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: userIdField.value.trim() || 'aaro_hi_user',
                    threadId,
                    notes
                })
            });
            const payload = await res.json().catch(() => ({}));
            if (!res.ok) {
                throw new Error(payload.error || safeUiErrorMessage());
            }
            if (notes) {
                appendUserMessage(notes);
            } else if (url.includes('reject')) {
                appendUserMessage('Rejected this plan.');
            } else if (url.includes('approve')) {
                appendUserMessage('Approved this plan.');
            }
            appendAssistantMessage(payload);
            loadDbTrips(false);
        } catch (error) {
            if (sourceRow) {
                sourceRow.querySelectorAll('[data-plan-action]').forEach(btn => {
                    btn.disabled = false;
                    btn.classList.remove('action-unavailable');
                });
            }
            showToast(error && error.message ? error.message : safeUiErrorMessage());
        } finally {
            submitButton.disabled = false;
            loadingState.classList.remove('visible');
        }
    };

    promptInput.addEventListener('keydown', (event) => {
        if (event.key !== 'Enter' || event.shiftKey || event.isComposing) {
            return;
        }

        // Enter submits the same way as clicking the visible send button.
        // Use click() rather than requestSubmit() so this remains reliable
        // across browsers and does not depend on form submitter resolution.
        event.preventDefault();
        event.stopPropagation();

        if (!submitButton.disabled && promptInput.value.trim()) {
            submitButton.click();
        }
    });

    resizePrompt();
    requestAnimationFrame(resizePrompt);
    // Browser startup always lands on Home. History is loaded into the sidebar,
    // but the most recent conversation is never opened automatically.
    showHomeView();
    clearThreadDom();
    renderHistoryList();
    (async () => {
        await loadDbTrips(false);
        await loadServerHistoryIntoStore();
        renderHistoryList();
    })();
