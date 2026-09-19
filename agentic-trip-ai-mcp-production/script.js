
    const layout = document.querySelector('.layout');
    const form = document.getElementById('travelForm');
    const chatThread = document.getElementById('chatThread');
    const messagesPane = document.getElementById('messagesPane');
    const chatHistoryList = document.getElementById('chatHistoryList');
    const userIdField = document.getElementById('userId');
    const promptInput = document.getElementById('tripPrompt');
    const submitButton = document.getElementById('submitButton');
    const composerBar = document.getElementById('composerBar');
    const modifyComposerBar = document.getElementById('modifyComposerBar');
    const modifyComposerSub = document.getElementById('modifyComposerSub');
    const cancelModify = document.getElementById('cancelModify');
    const loadingState = document.getElementById('loadingState');
    const newChatBtn = document.getElementById('newChatBtn');
    const composerNewChat = document.getElementById('composerNewChat');
    const tripsView = document.getElementById('tripsView');
    const tripsGrid = document.getElementById('tripsGrid');
    const tripsRefresh = document.getElementById('tripsRefresh');
    const historyDeleteModal = document.getElementById('historyDeleteModal');
    const historyDeleteItem = document.getElementById('historyDeleteItem');
    const historyDeleteNote = document.getElementById('historyDeleteNote');
    const historyDeleteCancel = document.getElementById('historyDeleteCancel');
    const historyDeleteConfirm = document.getElementById('historyDeleteConfirm');
    let dbTrips = [];
    let pendingHistoryDelete = null;
    let modifyContext = null;

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

    const exitModifyMode = (clearInput = false) => {
        modifyContext = null;
        modifyComposerBar?.classList.remove('visible');
        composerBar?.classList.remove('modify-active');
        if (modifyComposerSub) {
            modifyComposerSub.textContent = 'Describe the change. Only the affected specialist(s) will run.';
        }
        promptInput.placeholder = 'Ask anything — plan a trip, get travel advice, check weather, find hotels, or ask about a destination…';
        if (clearInput) {
            promptInput.value = '';
            resizePrompt();
        }
    };

    const enterModifyMode = (threadId, sourceRow) => {
        if (!threadId) {
            showToast('This plan is no longer available for modification.');
            return;
        }
        modifyContext = { threadId: String(threadId), sourceRow: sourceRow || null };
        modifyComposerBar?.classList.add('visible');
        composerBar?.classList.add('modify-active');
        if (modifyComposerSub) {
            modifyComposerSub.textContent = 'Current plan retained · tell me exactly what to change; only affected agents will run.';
        }
        promptInput.placeholder = 'What should we change in this plan?';
        promptInput.focus();
        resizePrompt();
        showToast('Modify mode enabled — your next message will update this trip.');
    };

    if (cancelModify) {
        cancelModify.addEventListener('click', () => {
            exitModifyMode(false);
            promptInput.focus();
        });
    }

    const storageKey = () => 'agentic-trip-ai-chats:' + (userIdField.value.trim() || 'aaro_hi_user');
    let currentChatId = null;
    let currentHistoryKey = null;
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
        // Keep the complete UI response for browser fallback. The server is
        // authoritative when a threadId exists, but retaining specialist fields
        // here prevents a blank/generic card if the API is temporarily unavailable.
        return JSON.parse(JSON.stringify(planData));
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
            const res = await fetch('/api/chat/history?userId=' + encodeURIComponent(userId) + '&limit=100&_=' + Date.now(), { cache: 'no-store' });
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
        const refreshButton = document.getElementById('tripsRefresh');
        const previousLabel = refreshButton ? refreshButton.textContent : '';
        if (refreshButton && renderView) {
            refreshButton.disabled = true;
            refreshButton.textContent = '↻ Refreshing…';
            refreshButton.setAttribute('aria-busy', 'true');
        }
        try {
            // Never let browser/proxy caching make My Trips appear stale.
            const url = '/api/trips?userId=' + encodeURIComponent(userId)
                + '&limit=50&_=' + Date.now();
            const res = await fetch(url, { cache: 'no-store' });
            if (!res.ok) throw new Error('Unable to load trips');
            const items = await res.json();
            dbTrips = Array.isArray(items) ? items : [];
            if (renderView) renderDbTripsView();
            renderHistoryList();
            if (renderView) {
                const stamp = document.getElementById('tripsRefreshStamp');
                if (stamp) stamp.textContent = 'Updated just now';
                showToast('My Trips refreshed.');
            }
            return true;
        } catch (e) {
            if (renderView) tripsGrid.innerHTML = '<div class="trips-empty" style="grid-column:1/-1"><strong>Could not load My Trips</strong>Check the server connection and try Refresh again.</div>';
            console.warn('Could not load database trips', e);
            if (renderView) showToast('Could not refresh My Trips.');
            return false;
        } finally {
            if (refreshButton && renderView) {
                refreshButton.disabled = false;
                refreshButton.textContent = previousLabel || '↻ Refresh';
                refreshButton.removeAttribute('aria-busy');
            }
        }
    };

    const openSavedTrip = async (id) => {
        try {
            currentHistoryKey = 'db:' + String(id);
            renderHistoryList();
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
    tripsRefresh.addEventListener('click', async (event) => {
        event.preventDefault();
        // Refresh both the saved-trip view and the conversation-backed Recent
        // History. This keeps My Trips and the sidebar in sync after a new chat,
        // approval, deletion, or a second browser tab creates a new record.
        const ok = await loadDbTrips(true);
        if (ok) {
            await loadServerHistoryIntoStore();
            renderDbTripsView();
            renderHistoryList();
        }
    });

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
            const entryKey = entry.kind + ':' + String(entry.id);
            btn.className = 'chat-history-item' + (entryKey === currentHistoryKey ? ' active' : '');
            if (entryKey === currentHistoryKey) btn.setAttribute('aria-current', 'page');
            else btn.removeAttribute('aria-current');
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
            deleteButton.innerHTML = '<span aria-hidden="true">⌫</span>';
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

    const closeDeleteHistoryModal = (result = false) => {
        if (!historyDeleteModal) return;
        historyDeleteModal.classList.remove('visible');
        historyDeleteModal.setAttribute('aria-hidden', 'true');
        document.body.classList.remove('history-delete-open');
        const pending = pendingHistoryDelete;
        pendingHistoryDelete = null;
        if (pending) pending.resolve(result);
    };

    const askDeleteHistoryConfirmation = (entry) => new Promise(resolve => {
        pendingHistoryDelete = { resolve };
        historyDeleteItem.textContent = entry.title || 'this history item';
        historyDeleteNote.textContent = entry.kind === 'db' && !entry.legacy
            ? 'This also permanently deletes the saved trip record and its conversation memory.'
            : entry.threadId
                ? 'This permanently deletes the saved conversation memory for this session.'
                : 'This removes the item from your local Recent History.';
        historyDeleteModal.classList.add('visible');
        historyDeleteModal.setAttribute('aria-hidden', 'false');
        document.body.classList.add('history-delete-open');
        requestAnimationFrame(() => historyDeleteConfirm.focus());
    });

    historyDeleteCancel?.addEventListener('click', () => closeDeleteHistoryModal(false));
    historyDeleteConfirm?.addEventListener('click', () => closeDeleteHistoryModal(true));
    historyDeleteModal?.addEventListener('click', event => {
        if (event.target === historyDeleteModal) closeDeleteHistoryModal(false);
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && historyDeleteModal?.classList.contains('visible')) {
            event.preventDefault();
            closeDeleteHistoryModal(false);
        }
    });

    const deleteHistoryEntry = async (entry, rowElement) => {
        const userId = userIdField.value.trim() || 'aaro_hi_user';
        const title = entry.title || 'this history item';
        const confirmed = await askDeleteHistoryConfirmation(entry);
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

            if ((entry.kind === 'chat' && entry.id === currentChatId) || currentHistoryKey === entry.kind + ':' + String(entry.id)) {
                currentChatId = null;
                currentHistoryKey = null;
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
        exitModifyMode(false);
        showHomeView();
        const store = loadStore();
        const chat = store.chats.find(c => c.id === chatId);
        if (!chat) return;

        currentChatId = chatId;
        currentHistoryKey = 'chat:' + String(chatId);
        renderHistoryList();
        startedFreshChat = String(chatId).startsWith('chat-');
        clearThreadDom();

        let lastUser = '';
        let fallbackRestore = null;
        // A chat created in the current browser starts with chat-..., but once
        // the backend creates the LangGraph thread it is still the same chat.
        // Treat ANY chat with a real threadId as server-backed. Otherwise we
        // render stale/local planData and lose structured Weather/Hotel/RAG/etc.
        // when the user switches chats and comes back.
        const sessionId = chatThreadIdForDelete(chat);
        const isServerChat = String(chatId).startsWith('server-') || !!sessionId;

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
        exitModifyMode(true);
        startedFreshChat = true;
        currentChatId = null;
        currentHistoryKey = null;
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
        const usable = (flights || []).filter(f => f && String(f.status || '').toLowerCase() !== 'unavailable');
        if (!usable.length) return '<div class="specialist-empty">No confirmed flight options are available for the requested route/date.</div>';

        const escape = value => escapeHtml(value == null ? '' : String(value));
        const initials = value => {
            const words = String(value || 'Flight').trim().split(/\s+/).filter(Boolean);
            return words.slice(0, 2).map(w => w[0]).join('').toUpperCase() || '✈';
        };
        const firstMatch = (text, regex) => {
            const match = String(text || '').match(regex);
            return match && match[1] ? match[1].trim() : '';
        };
        const noteText = f => String(f.notes || '');
        const price = f => f.price != null ? String(f.price) : firstMatch(noteText(f), /(?:price|fare)\s*[=:]\s*([^,;|]+)/i);
        const currency = f => f.currency || firstMatch(noteText(f), /(?:currency)\s*[=:]\s*([A-Z]{3}|₹|\$|€|£)/i) || '';
        const provider = f => f.provider || firstMatch(noteText(f), /provider\s*[=:]\s*([^,;|]+)/i) || '';
        const segmentCount = f => {
            const raw = f.segmentCount != null ? f.segmentCount : firstMatch(noteText(f), /segments?\s*[=:]\s*(\d+)/i);
            const n = Number(raw);
            return Number.isFinite(n) && n > 0 ? n : 1;
        };
        const selfTransfer = f => f.selfTransfer === true || /selfTransfer\s*[=:]\s*true/i.test(noteText(f));
        const ignavId = f => f.ignavId || firstMatch(noteText(f), /ignavId\s*[=:]\s*([A-Za-z0-9_-]+)/i);
        const isIso = value => /^\d{4}-\d{2}-\d{2}T/.test(String(value || ''));
        const formatTime = value => {
            if (!value) return '';
            const text = String(value);
            if (!isIso(text)) return text.length > 24 ? text.slice(0, 24) : text;
            const d = new Date(text);
            if (Number.isNaN(d.getTime())) return text;
            return new Intl.DateTimeFormat(undefined, { hour:'2-digit', minute:'2-digit', hour12:false }).format(d);
        };
        const formatDate = value => {
            if (!value) return '';
            const text = String(value);
            if (!isIso(text)) return text;
            const d = new Date(text);
            if (Number.isNaN(d.getTime())) return text.slice(0, 10);
            return new Intl.DateTimeFormat(undefined, { day:'2-digit', month:'short' }).format(d);
        };
        const duration = (dep, arr) => {
            if (!dep || !arr) return '';
            const a = new Date(dep).getTime(), b = new Date(arr).getTime();
            if (!Number.isFinite(a) || !Number.isFinite(b) || b <= a) return '';
            const mins = Math.round((b-a)/60000), h = Math.floor(mins/60), m = mins%60;
            return h ? h + 'h' + (m ? ' ' + m + 'm' : '') : m + 'm';
        };
        const airport = value => escape(value || '—');
        const cleanNote = f => noteText(f)
            .replace(/provider\s*[=:]\s*[^,;|]+/ig, '')
            .replace(/(?:price|fare)\s*[=:]\s*[^,;|]+/ig, '')
            .replace(/segments?\s*[=:]\s*\d+/ig, '')
            .replace(/selfTransfer\s*[=:]\s*(?:true|false)/ig, '')
            .replace(/ignavId\s*[=:]\s*[A-Za-z0-9_-]+/ig, '')
            .replace(/^[\s·,;|-]+|[\s·,;|-]+$/g, '')
            .replace(/\s{2,}/g, ' ')
            .trim();

        const renderFlight = (f, index) => {
            const direction = String(f.direction || 'outbound').toLowerCase() === 'return' ? 'return' : 'outbound';
            const dep = f.departureTime || f.departureScheduled || '';
            const arr = f.arrivalTime || f.arrivalScheduled || '';
            const airline = f.airline || 'Flight option';
            const flightNo = f.flightNumber || '';
            const route = (f.origin && f.destination) ? f.origin + ' → ' + f.destination : '';
            const dur = f.duration || duration(dep, arr);
            const segments = segmentCount(f);
            const stops = f.stops != null ? Number(f.stops) : Math.max(0, segments - 1);
            const fare = price(f);
            const fareCurrency = currency(f);
            const providerName = provider(f);
            const self = selfTransfer(f);
            const extraClass = index >= 3 ? ' is-extra-flight' : '';
            const dateLabel = f.requestedDate || formatDate(dep);
            const note = cleanNote(f);
            const status = String(f.status || '').toLowerCase();
            const isLive = /live|scheduled|confirmed/.test(status) || /provider\s*[=:]\s*(aviationstack|ignav)/i.test(noteText(f));
            const stopsLabel = stops === 0 ? 'Non-stop' : (stops === 1 ? '1 stop' : stops + ' stops');
            const meta = [dur, stopsLabel, self ? 'Self-transfer' : ''].filter(Boolean);
            return '<article class="rich-flight-card' + extraClass + '" data-flight-index="' + index + '">' 
                + '<div class="flight-airline"><div class="flight-airline-mark">' + escape(initials(airline)) + '</div><div class="flight-airline-copy"><div class="flight-airline-name">' + escape(airline) + '</div><div class="flight-number">' + escape(flightNo || 'Flight details') + '</div></div></div>'
                + '<div class="flight-path">'
                + '<div class="flight-times">'
                + '<div class="flight-time-point"><div class="flight-time">' + escape(formatTime(dep) || '—') + '</div><div class="flight-airport">' + airport(f.origin) + '</div><div class="flight-date">' + escape(dateLabel) + '</div></div>'
                + '<div class="flight-route-line"><span>' + escape(dur || 'journey') + '</span><span class="flight-route-plane">✈</span></div>'
                + '<div class="flight-time-point"><div class="flight-time">' + escape(formatTime(arr) || '—') + '</div><div class="flight-airport">' + airport(f.destination) + '</div><div class="flight-date">' + escape(formatDate(arr)) + '</div></div>'
                + '</div>'
                + '<div class="flight-meta-row">'
                + meta.map((item, i) => '<span class="flight-pill' + (i === 1 && stops > 0 ? ' warn' : '') + '">' + escape(item) + '</span>').join('')
                + (isLive ? '<span class="flight-pill live">● Live schedule</span>' : '')
                + (direction === 'return' ? '<span class="flight-pill">↩ Return</span>' : '<span class="flight-pill">↗ Outbound</span>')
                + (providerName ? '<span class="flight-pill">via ' + escape(providerName) + '</span>' : '')
                + '</div>'
                + '</div>'
                + '<div class="flight-fare">'
                + (fare ? '<div class="flight-price">' + escape(fareCurrency ? fareCurrency + ' ' + fare : fare) + '</div><div class="flight-price-muted">fare returned</div>' : '<div class="flight-price-muted">Schedule only</div><div class="flight-price-muted">fare not returned</div>')
                + (providerName ? '<div class="flight-provider">' + escape(providerName) + '</div>' : '')
                + '</div>'
                + (note ? '<div class="flight-note">' + escape(note) + '</div>' : '')
                + '</article>';
        };

        // Keep this as an object list because Thymeleaf reserves nested bracket syntax.
        // Object literals keep this script valid JavaScript without colliding with Thymeleaf syntax.
        const groups = [
            { key: 'outbound', label: 'Outbound' },
            { key: 'return', label: 'Return' }
        ];
        let total = 0, html = '<div class="flight-rich-widget">';
        groups.forEach(group => {
            const key = group.key;
            const label = group.label;
            const items = usable.filter(f => String(f.direction || 'outbound').toLowerCase() === key);
            if (!items.length) return;
            total += items.length;
            const extraCount = Math.max(0, items.length - 3);
            const listClass = extraCount ? 'flight-list has-extra-flights' : 'flight-list';
            html += '<div class="flight-group">'
                + '<div class="flight-group-title"><span>' + label + '</span><span class="flight-group-count">' + items.length + ' option' + (items.length === 1 ? '' : 's') + '</span></div>'
                + '<div class="' + listClass + '">' + items.map(renderFlight).join('') + '</div>'
                + (extraCount ? '<button type="button" class="flight-more-btn" data-flights-more>＋ ' + extraCount + ' more available option' + (extraCount === 1 ? '' : 's') + '</button>' : '')
                + '</div>';
        });
        html = html.replace('<div class="flight-rich-widget">', '<div class="flight-rich-widget"><div class="flight-summary-bar"><div class="flight-summary-main"><div class="flight-summary-icon">✈</div><div><div class="flight-summary-title">Live flight options</div><div class="flight-summary-sub">Compare departure, arrival, duration, stops and returned fare data</div></div></div><span class="flight-summary-count">' + total + ' option' + (total === 1 ? '' : 's') + '</span></div>');
        return html + '</div>';
    };

    const cleanHotelText = (value) => String(value || '')
        .replace(/^#{1,6}\s*/gm, '')
        .replace(/^[-*]\s*/gm, '')
        .replace(/\s+/g, ' ')
        .trim();

    const isLikelyHotelName = (value) => {
        const name = cleanHotelText(value);
        if (name.length < 2 || name.length > 120) return false;
        const lower = name.toLowerCase();
        if (/https?:\/\//i.test(name) || /\b20\d{2}\b/.test(name)) return false;
        if (/best areas|areas? & hotels?|hotels? to stay|where to stay|top hotels?|hotel guide|accommodation guide|travel guide|things to do|complete guide/.test(lower)) return false;
        if (/^the\s+best\b|^top\s+\d+|\b\d+\s*(best|top|hotels?)\b/.test(lower)) return false;
        if (/\(\s*\d+\s*(best|top|hotels?|options?)?/i.test(name)) return false;
        if ((name.match(/[:;|]/g) || []).length > 1) return false;
        if (name.split(/\s+/).length > 11) return false;
        return true;
    };

    const buildHotelsSection = (hotels) => {
        // Backend HotelAgentService is the single authoritative validation boundary.
        // The browser MUST NOT re-validate hotel identity with its own lexical rules:
        // provider hotel names can legitimately contain punctuation, brands,
        // locations, languages, or long legal property names. A second UI filter
        // previously rejected valid backend results and produced a false empty state.
        const usable = (Array.isArray(hotels) ? hotels : [])
            .filter(h => h && String(h.name || '').trim());
        if (!usable.length) return '<div class="specialist-empty">No verified hotel properties were found for this request.</div>';
        const visibleCount = 4;
        const cards = usable.map((h, index) => {
            const meta = [h.area ? '📍 ' + h.area : '', h.priceRange ? '💰 ' + h.priceRange : '', h.rating ? '★ ' + h.rating : ''].filter(Boolean).join(' · ');
            const fit = cleanHotelText(h.suitableFor);
            const note = cleanHotelText(h.notes);
            const extraClass = index >= visibleCount ? ' is-extra' : '';
            return '<div class="hotel-card' + extraClass + '">'
                + '<div class="hotel-card-main"><div class="item-title">' + escapeHtml(cleanHotelText(h.name)) + '</div>'
                + (meta ? '<div class="item-meta">' + escapeHtml(meta) + '</div>' : '')
                + (fit ? '<span class="hotel-fit">' + escapeHtml(fit) + '</span>' : '')
                + (note ? '<div class="item-note">' + escapeHtml(note) + '</div>' : '')
                + '</div></div>';
        }).join('');
        const more = usable.length > visibleCount
            ? '<button type="button" class="hotel-more-btn" data-hotels-more>＋ ' + (usable.length - visibleCount) + ' more hotel options</button>'
            : '';
        return '<div class="hotel-list">' + cards + '</div>' + more;
    };

    const buildItinerarySection = (itinerary) => {
        const days = itinerary && Array.isArray(itinerary.days) ? itinerary.days : [];
        if (!days.length) return '';
        const text = value => value == null ? '' : String(value).trim();
        const safeUrl = value => { const url = text(value); return /^https?:\/\//i.test(url) ? url : ''; };
        const money = (value, currency) => { const v = text(value); if (!v) return ''; const c = text(currency); return c ? c + ' ' + v : v; };
        const activityIcon = type => {
            const t = text(type).toLowerCase();
            if (t.includes('food') || t.includes('restaurant') || t.includes('dining')) return '🍜';
            if (t.includes('culture') || t.includes('museum') || t.includes('history')) return '🏛️';
            if (t.includes('nature') || t.includes('park') || t.includes('scenic')) return '🌿';
            if (t.includes('transport') || t.includes('airport')) return '✈️';
            if (t.includes('lodging') || t.includes('hotel')) return '🏨';
            if (t.includes('shopping')) return '🛍️';
            if (t.includes('leisure') || t.includes('walk')) return '🚶';
            return '📍';
        };
        let html = '<div class="itin-rich-list">';
        if (text(itinerary.summary)) {
            html += '<div class="itin-overview"><div class="itin-overview-icon">🗺️</div><div style="flex:1"><div class="itin-overview-title">Day-by-day itinerary' + (itinerary.provider ? ' <span class="itin-provider-badge">' + escapeHtml(itinerary.provider) + '</span>' : '') + '</div><div class="itin-overview-text">' + escapeHtml(itinerary.summary) + '</div></div></div>';
        }
        days.forEach(day => {
            const activities = Array.isArray(day.activities) ? day.activities.filter(a => a && text(a.name)) : [];
            const dayCost = money(day.estimatedCost, day.currency);
            html += '<article class="itin-rich-day"><div class="itin-rich-day-head">'
                + '<div class="itin-rich-day-number">DAY ' + escapeHtml(String(day.day || '')) + '</div>'
                + '<div class="itin-rich-day-title-wrap"><h3>' + escapeHtml(text(day.title) || 'Explore') + '</h3>'
                + (text(day.summary) ? '<p>' + escapeHtml(day.summary) + '</p>' : '') + '</div>'
                + (dayCost ? '<div class="itin-day-cost">' + escapeHtml(dayCost) + '<small>estimated</small></div>' : '')
                + '</div>';
            if (activities.length) {
                html += '<div class="itin-rich-activities">';
                activities.forEach(a => {
                    const tags = activityTags(a);
                    const image = safeUrl(a.imageUrl || a.image || a.photoUrl);
                    const booking = safeUrl(a.bookingUrl || a.url || a.link);
                    const cost = money(a.estimatedCost || a.cost || a.price, a.currency);
                    const location = text(a.location || a.address || a.area);
                    const duration = text(a.duration || a.durationText);
                    const description = text(a.description || a.details || a.notes);
                    html += '<div class="itin-rich-activity">'
                        + '<div class="itin-rich-activity-media">'
                        + (image ? '<img src="' + escapeHtml(image) + '" alt="' + escapeHtml(a.name) + '" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\';">' : '')
                        + '<div class="itin-rich-activity-placeholder"' + (image ? ' style="display:none"' : '') + '>' + activityIcon(a.type) + '</div>'
                        + '</div><div class="itin-rich-activity-body">'
                        + '<div class="itin-rich-activity-top"><div><div class="itin-rich-activity-name">' + escapeHtml(a.name) + '</div>'
                        + (a.type ? '<span class="itin-rich-type">' + escapeHtml(a.type) + '</span>' : '') + '</div>'
                        + (cost ? '<div class="itin-activity-cost">' + escapeHtml(cost) + '</div>' : '') + '</div>'
                        + (description ? '<div class="itin-rich-description">' + escapeHtml(description) + '</div>' : '')
                        + '<div class="itin-rich-meta">'
                        + (location ? '<span>📍 ' + escapeHtml(location) + '</span>' : '')
                        + (duration ? '<span>⏱ ' + escapeHtml(duration) + '</span>' : '')
                        + (a.indoorOutdoor && String(a.indoorOutdoor).toLowerCase() !== 'mixed' ? '<span>◉ ' + escapeHtml(a.indoorOutdoor) + '</span>' : '')
                        + '</div>'
                        + (tags.length ? '<div class="itin-rich-tags">' + tags.map(tag => '<span>✓ ' + escapeHtml(tag) + '</span>').join('') + '</div>' : '')
                        + (booking ? '<a class="itin-book-btn" href="' + escapeHtml(booking) + '" target="_blank" rel="noopener noreferrer">View / book ↗</a>' : '')
                        + '</div></div>';
                });
                html += '</div>';
            } else {
                html += '<div class="itin-rich-empty">No activities were returned for this day.</div>';
            }
            html += '</article>';
        });
        return html + '</div>';
    };

    const buildWeatherSection = (weather, options = {}) => {
        if (!weather) return '';
        const current = weather.current || {};
        const days = Array.isArray(weather.days) ? weather.days.filter(Boolean).slice(0, 7) : [];
        const hasCurrent = current.temperature != null;
        const hasForecast = days.some(day => day && (day.high != null || day.low != null || day.condition));
        const hasWeatherData = hasCurrent || hasForecast;
        const focused = !!options.focused;
        if (!hasWeatherData) {
            const location = weather.location || 'the requested destination';
            return '<div class="weather-dashboard-card weather-empty-state">'
                + '<div class="weather-dashboard-head"><div><div class="weather-location-title">🌤️ Weather</div>'
                + '<div class="weather-location-sub">' + escapeHtml(location) + '</div></div>'
                + '<span class="weather-live-badge">OpenWeather</span></div>'
                + '<div class="weather-empty-message"><div class="weather-empty-icon">☁️</div>'
                + '<div><div class="weather-empty-title">No weather details found</div>'
                + '<div class="weather-empty-detail">We could not retrieve usable weather details for ' + escapeHtml(location) + '.</div></div></div>'
                + '</div>';
        }
        const fmtDate = (value) => {
            if (!value) return '';
            const d = new Date(value + (String(value).length === 10 ? 'T00:00:00' : ''));
            return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString(undefined, { weekday:'short', month:'short', day:'numeric' });
        };
        const shortDate = (value) => {
            if (!value) return '';
            const d = new Date(value + (String(value).length === 10 ? 'T00:00:00' : ''));
            return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString(undefined, { month:'short', day:'numeric' });
        };
        const dayKey = (value) => String(value || '').slice(0,10);
        const todayKey = new Date().toISOString().slice(0,10);
        const fmtTime = (unix) => {
            if (unix == null || unix === '') return '—';
            const d = new Date(Number(unix) * 1000);
            return Number.isNaN(d.getTime()) ? '—' : d.toLocaleTimeString(undefined, { hour:'numeric', minute:'2-digit' });
        };
        const num = (value, digits = 0) => value == null || value === '' || Number.isNaN(Number(value)) ? '—' : Number(value).toFixed(digits);
        const km = (meters) => meters == null ? '—' : (Number(meters) / 1000).toFixed(1) + ' km';
        const windDir = (deg) => {
            if (deg == null || Number.isNaN(Number(deg))) return '—';
            const dirs = ['N','NE','E','SE','S','SW','W','NW'];
            return dirs[Math.round(Number(deg) / 45) % 8] + ' · ' + Math.round(Number(deg)) + '°';
        };
        const weatherIcon = (icon, cls) => /^[0-9]{2}[dn]$/.test(String(icon || ''))
            ? '<img class="' + cls + '" src="https://openweathermap.org/img/wn/' + escapeHtml(String(icon)) + '@2x.png" alt="Weather icon">'
            : '<span class="' + cls + '" style="display:flex;align-items:center;justify-content:center;font-size:30px">🌤️</span>';
        const dateRange = days.length ? shortDate(days[0].date) + (days.length > 1 ? ' – ' + shortDate(days[days.length - 1].date) : '') : '';
        let html = '<div class="weather-dashboard-card' + (focused ? ' weather-focused' : '') + '">';
        html += '<div class="weather-dashboard-head"><div><div class="weather-location-title">🌤️ ' + escapeHtml(weather.location || 'Destination') + ' Weather</div>'
            + '<div class="weather-location-sub">' + escapeHtml(dateRange || 'Current conditions') + '</div></div>'
            + '<span class="weather-live-badge">OpenWeather · Live</span></div>';

        if (hasCurrent) {
            const desc = current.description || current.condition || 'Current conditions';
            const rainNow = current.rain1h != null ? Number(current.rain1h).toFixed(1) + ' mm/h' : 'None reported';
            const snowNow = current.snow1h != null ? Number(current.snow1h).toFixed(1) + ' mm/h' : 'None reported';
            html += '<div class="weather-current">'
                + '<div class="weather-current-main"><div class="weather-current-label">Current conditions</div>'
                + '<div class="weather-current-condition">' + weatherIcon(current.icon, 'weather-current-icon') + '<div><div class="weather-current-temp">' + num(current.temperature) + '°<small>C</small></div><div class="weather-current-desc">' + escapeHtml(desc) + '</div></div></div>'
                + '<div class="weather-current-feels">Feels like ' + num(current.feelsLike) + '°C · Updated ' + escapeHtml(fmtTime(current.observedAt)) + '</div></div>'
                + '<div><div class="weather-metrics">'
                + metric('💧','Humidity', current.humidity != null ? Math.round(Number(current.humidity)) + '%' : '—')
                + metric('🌡️','Pressure', current.pressure != null ? Math.round(Number(current.pressure)) + ' hPa' : '—')
                + metric('💨','Wind gust', current.windGust != null ? num(current.windGust,1) + ' m/s' : '—')
                + metric('☁️','Cloud cover', current.clouds != null ? Math.round(Number(current.clouds)) + '%' : '—')
                + metric('💨','Wind', current.windSpeed != null ? num(current.windSpeed,1) + ' m/s' : '—', windDir(current.windDeg))
                + metric('👁️','Visibility', km(current.visibilityMeters))
                + metric('🌧️','Rain · 1h', rainNow)
                + metric('❄️','Snow · 1h', snowNow)
                + '</div><div class="weather-sunline">'
                + '<div class="weather-sun">🌅 Sunrise<strong>' + escapeHtml(fmtTime(current.sunrise)) + '</strong></div>'
                + '<div class="weather-sun">🌇 Sunset<strong>' + escapeHtml(fmtTime(current.sunset)) + '</strong></div>'
                + '<div class="weather-sun">🌎 Timezone<strong>' + escapeHtml(current.timezone || 'Local') + '</strong></div>'
                + '</div></div></div>';
            html += '<div class="weather-source">Source: <strong>OpenWeather Free · Current Weather + 5-day / 3-hour Forecast</strong> · Provider values are displayed directly; recommendations may be generated separately.</div>';
        }

        if (days.length) {
            const wet = days.reduce((best, day) => Number(day.rainProbability || 0) > Number(best?.rainProbability || 0) ? day : best, null);
            const maxRain = Number(wet?.rainProbability || 0);
            html += '<div class="weather-outlook">'
                + '<div class="weather-outlook-head"><div><div class="weather-outlook-title">Travel-date outlook</div>'
                + '<div class="weather-outlook-sub">A quick view of conditions across your travel window</div></div>'
                + '<div class="weather-outlook-count">' + days.length + ' day' + (days.length === 1 ? '' : 's') + '</div></div>'
                + '<div class="weather-days-grid">';
            html += days.map((day, index) => {
                const high = day.high != null ? Math.round(Number(day.high)) + '°' : '—';
                const low = day.low != null ? Math.round(Number(day.low)) + '°' : '—';
                const rainValue = day.rainProbability != null ? Math.max(0, Math.min(100, Math.round(Number(day.rainProbability)))) : null;
                const rain = rainValue != null ? rainValue + '%' : '—';
                const isToday = dayKey(day.date) === todayKey || index === 0 && dayKey(day.date) === todayKey;
                const condition = day.condition || 'Forecast';
                let note = 'Good for plans';
                if (rainValue != null && rainValue >= 70) note = 'Plan rain cover';
                else if (rainValue != null && rainValue >= 40) note = 'Keep plans flexible';
                else if (rainValue != null && rainValue < 20) note = 'Great for outdoors';
                return '<div class="weather-day-card' + (isToday ? ' today' : '') + '">'
                    + '<div class="weather-day-top"><div class="weather-day-date">' + escapeHtml(fmtDate(day.date)) + '</div>'
                    + (isToday ? '<span class="weather-day-badge">Today</span>' : '') + '</div>'
                    + '<div class="weather-day-main">' + weatherIcon(day.icon, 'weather-day-icon') + '<div class="weather-day-condition">' + escapeHtml(condition) + '</div></div>'
                    + '<div class="weather-day-temp"><strong>' + escapeHtml(high) + '</strong><span>Low ' + escapeHtml(low) + '</span></div>'
                    + '<div class="weather-day-rain"><span>🌧 Rain chance</span><strong>' + escapeHtml(rain) + '</strong></div>'
                    + (rainValue != null ? '<div class="weather-rain-track"><div class="weather-rain-fill" style="width:' + rainValue + '%"></div></div>' : '')
                    + '<div class="weather-day-note">' + escapeHtml(note) + '</div></div>';
            }).join('');
            html += '</div>';
            let insight = '';
            if (maxRain >= 70) insight = 'Rain is most likely around ' + shortDate(wet.date) + '. Consider indoor attractions or keep a flexible backup plan.';
            else if (maxRain >= 40) insight = 'There is a moderate rain risk in the travel window. Outdoor plans should stay flexible.';
            else insight = 'The forecast looks broadly comfortable for sightseeing, with lower precipitation risk across the travel window.';
            html += '<div class="weather-insight"><span class="weather-insight-icon">💡</span><div><strong>Travel Weather Insight</strong><div>' + escapeHtml(insight) + '</div></div></div>';
            html += '</div>';
        }
        return html + '</div>';

        function metric(icon, label, value, sub) {
            return '<div class="weather-metric"><div class="weather-metric-label">' + icon + ' ' + escapeHtml(label) + '</div><div class="weather-metric-value">' + escapeHtml(String(value)) + '</div>' + (sub ? '<div class="weather-metric-sub">' + escapeHtml(String(sub)) + '</div>' : '') + '</div>';
        }
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

    const isInternalOrPlanKnowledge = (text) => {
        const value = String(text || '').trim();
        if (!value) return true;
        return /llm\s+budget\s+exhausted|budget\s+exhausted|quota(?:\s+exceeded)?|rate\s*limit|model unavailable|stack trace/i.test(value)
            || /(?:^|\n)\s*(?:#{1,6}\s*)?(?:flights?|hotels?|accommodation|itinerary|day[- ]by[- ]day|trip plan)\s*[:#-]?/i.test(value)
            || /(?:₹|\$|€)\s*[\d,]+/i.test(value)
            || /\b(?:flight|airline|hotel|accommodation|trip total|budget breakdown|fare)\b/i.test(value);
    };

    const safeKnowledgeAnswer = (text) => {
        const value = String(text || '').trim();
        if (!value || isInternalOrPlanKnowledge(value) || value.length > 1600) return '';
        return value;
    };

    const safeTripTips = (text) => {
        const value = String(text || '').trim();
        if (!value || /llm\s+budget\s+exhausted|budget\s+exhausted|quota(?:\s+exceeded)?|rate\s*limit|model unavailable|stack trace/i.test(value)) return '';
        return value.length > 1400 ? value.slice(0, 1397).trim() + '…' : value;
    };

    const buildKnowledgeGuidanceSection = (data, plan) => {
        const exec = data.execution || {};
        const knowledge = plan.knowledge || {};
        const answer = safeKnowledgeAnswer(knowledge.answer || exec.ragAnswer || data.ragAnswer || '');
        const available = knowledge.available === true || !!answer;
        if (!available || !answer) return '';
        const topics = Array.isArray(knowledge.topics) ? knowledge.topics : [];
        const sources = Array.isArray(knowledge.sources) ? knowledge.sources : (Array.isArray(exec.ragSources) ? exec.ragSources : []);
        const destination = String(knowledge.destination || '').trim();
        const title = String(knowledge.title || (destination ? 'Travel Knowledge & Guidance for ' + destination : 'Travel Knowledge & Guidance')).trim();
        const bullets = formatKnowledgeText(answer);
        return '<section id="section-knowledge" class="workspace-card full knowledge-guidance-card compact-knowledge-card">'
            + '<div class="workspace-card-head"><div><div class="workspace-card-title">🧠 ' + escapeHtml(title) + '</div>'
            + '<div class="workspace-card-sub">Durable destination guidance — separate from live trip results</div></div>'
            + '<span class="knowledge-guidance-badge">✓ Grounded</span></div>'
            + (topics.length ? '<div class="knowledge-topics">' + topics.slice(0, 5).map(topic => '<span class="knowledge-topic">' + escapeHtml(String(topic).replace(/[_-]+/g, ' ')) + '</span>').join('') + '</div>' : '')
            + '<div class="workspace-content knowledge-answer compact-knowledge-answer">' + bullets + '</div>'
            + (sources.length ? '<details class="knowledge-sources-details"><summary>View knowledge sources</summary><div class="knowledge-sources">' + sources.slice(0, 5).map(source => '<span class="knowledge-source">' + escapeHtml(humanizeKnowledgeSource(source)) + '</span>').join('') + '</div></details>' : '')
            + '</section>';
    };

    const buildSpecialistKnowledgeCard = (data, plan) => {
        const exec = data.execution || {};
        const knowledge = plan.knowledge || {};
        const answer = safeKnowledgeAnswer(knowledge.answer || exec.ragAnswer || data.ragAnswer || '');
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
            html += '<details class="knowledge-sources-details"><summary>View knowledge sources</summary><div class="knowledge-sources">' + sources.slice(0, 6).map(source =>
                '<span class="knowledge-source">' + escapeHtml(humanizeKnowledgeSource(source)) + '</span>'
            ).join('') + '</div></details>';
        }
        html += '</section>';
        return html;
    };

    const hasSpecialistKnowledge = (data, plan) => {
        const knowledge = plan?.knowledge || {};
        const exec = data?.execution || {};
        return knowledge.available === true
            || !!String(knowledge.answer || exec.ragAnswer || data?.ragAnswer || '').trim();
    };

    const buildHistoryResponseHtml = (data) => {
        const plan = data?.plan || {};
        const trip = plan.trip || {};
        const route = [trip.origin, trip.destination].filter(Boolean).join(' → ') || 'Saved trip';
        const dates = trip.datesFlexible === true
            ? 'Dates flexible'
            : formatDateRange(trip.departureDate, trip.returnDate);
        const flights = Array.isArray(plan.flights) ? plan.flights : [];
        const hotels = Array.isArray(plan.hotels) ? plan.hotels : [];
        const itinerary = plan.itinerary;
        const weather = plan.weather;
        const budget = plan.budget;
        const tips = safeTripTips(plan.tips || '');
        const quality = Number(trip.qualityScore || 0);
        const requirements = Array.isArray(trip.requirements) ? trip.requirements : [];

        let html = '<div class="history-recalled-dashboard">'
            + '<section class="workspace-card full history-recalled-hero">'
            + '<div class="workspace-card-head"><div><div class="workspace-card-title">🕘 Recalled saved trip</div>'
            + '<div class="workspace-card-sub">Read-only details retrieved from your persistent trip history</div></div>'
            + '<span class="knowledge-guidance-badge">✓ Memory</span></div>'
            + '<div class="history-recalled-route">' + escapeHtml(route) + '</div>'
            + '<div class="history-recalled-meta">'
            + '<span>' + escapeHtml(dates || 'Dates not recorded') + '</span>'
            + (trip.nights > 0 ? '<span>' + escapeHtml(String(trip.nights)) + ' nights</span>' : '')
            + '<span>' + escapeHtml(String(trip.travelers || 1)) + ' traveler' + ((trip.travelers || 1) === 1 ? '' : 's') + '</span>'
            + (trip.budgetLabel ? '<span>' + escapeHtml(trip.budgetLabel) + '</span>' : '')
            + (quality > 0 ? '<span>Confidence ' + escapeHtml(String(quality)) + '/100</span>' : '')
            + '</div>'
            + (requirements.length ? '<div class="knowledge-topics">' + requirements.slice(0, 8).map(r => '<span class="knowledge-topic">' + escapeHtml(String(r).replace(/[_-]+/g, ' ')) + '</span>').join('') + '</div>' : '')
            + '</section>';

        if (flights.length) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">✈️ Flights</div><div class="workspace-card-sub">Saved flight details from the trip</div></div></div><div class="workspace-content">' + buildFlightsSection(flights) + '</div></section>';
        if (hotels.length) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">🏨 Hotels</div><div class="workspace-card-sub">Saved accommodation details</div></div></div><div class="workspace-content">' + buildHotelsSection(hotels) + '</div></section>';
        if (weather && (weather.summary || weather.location || (Array.isArray(weather.days) && weather.days.length))) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">☀️ Weather</div><div class="workspace-card-sub">Saved weather information</div></div></div><div class="workspace-content">' + buildWeatherSection(weather) + '</div></section>';
        if (budget && Array.isArray(budget.lineItems) && budget.lineItems.length) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">💰 Budget</div><div class="workspace-card-sub">Saved trip cost estimate</div></div></div><div class="workspace-content">' + buildBudgetTable(budget) + '</div></section>';
        if (itinerary && Array.isArray(itinerary.days) && itinerary.days.length) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">🗓️ Day-wise Itinerary</div><div class="workspace-card-sub">Saved itinerary from this trip</div></div></div><div class="workspace-content itin-compact">' + buildItinerarySection(itinerary) + '</div></section>';
        if (plan.knowledge?.available === true || plan.knowledge?.answer) html += buildKnowledgeGuidanceSection(data, plan);
        if (tips) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">💡 Travel Tips</div><div class="workspace-card-sub">Saved trip-specific suggestions</div></div></div><div class="workspace-content knowledge-answer">' + formatKnowledgeText(tips) + '</div></section>';

        html += '<section class="workspace-card full history-readonly-note"><div class="workspace-card-title">🔒 Read-only history</div><div class="workspace-card-sub">This is a recalled snapshot. Approve, modify and reject actions are disabled until you create or modify a new plan.</div></section>';
        return html + '</div>';
    };

    const buildSpecialistResponseHtml = (data, userRequest) => {
        const plan = data.plan || data || {};
        const trip = plan.trip || {};
        const type = String(data.requestType || trip.requestType || 'GENERAL').toUpperCase();
        const location = String((plan.weather && plan.weather.location) || trip.destination || data.destination || '').trim();
        const weather = plan.weather || data.weather;
        const flights = Array.isArray(plan.flights) ? plan.flights : (Array.isArray(data.flights) ? data.flights : []);
        const hotels = Array.isArray(plan.hotels) ? plan.hotels : (Array.isArray(data.hotels) ? data.hotels : []);
        const budget = plan.budget;
        const exec = data.execution || {};
        const answer = String(exec.ragAnswer || data.ragAnswer || plan.tips || data.finalPlan || '').trim();
        const hasWeather = !!(weather && ((weather.current && weather.current.temperature != null) || (Array.isArray(weather.days) && weather.days.some(d => d && (d.high != null || d.low != null || d.condition)))));
        const hasBudget = !!(budget && Array.isArray(budget.lineItems) && budget.lineItems.length);
        const hasAnswer = !!answer;
        const clarification = String(data.clarificationRequired || data.plan?.clarificationRequired || '').trim();
        const needsUserInput = String(data.status || '').toUpperCase() === 'NEEDS_USER_INPUT';

        const meta = {
            WEATHER: ['☀️', location ? 'Weather in ' + location : 'Weather details', 'Current conditions and forecast'],
            FLIGHT_SEARCH: ['✈️', location ? 'Flight options for ' + location : 'Flight options', 'Available schedules and returned fare data'],
            HOTEL_SEARCH: ['🏨', location ? 'Hotels in ' + location : 'Hotel options', 'Accommodation recommendations'],
            BUDGET: ['💰', 'Travel budget', 'Estimated cost for your request'],
            RESEARCH: ['🔎', location ? 'Things to do in ' + location : 'Travel research', 'Destination recommendations and current research'],
            HISTORY: ['🕘', 'Recent saved trip', 'Retrieved from persistent trip history']
        }[type] || ['✦', location ? 'Travel information for ' + location : 'Travel information', 'Knowledge-backed answer to your question'];

        const isMulti = type === 'MULTI_CAPABILITY' || type === 'MULTI_INTENT';
        const widgets = [];
        if (hasWeather && (type === 'WEATHER' || isMulti)) { widgets.push({key:'weather', icon:'☀️', title:'Weather', subtitle:'Current conditions and forecast', body:buildWeatherSection(weather, {focused: !isMulti})}); }
        if (flights.length && (type === 'FLIGHT_SEARCH' || isMulti)) { widgets.push({key:'flights', icon:'✈️', title:'Flights', subtitle:'Available schedules and fare data', body:buildFlightsSection(flights)}); }
        if (hotels.length && (type === 'HOTEL_SEARCH' || isMulti)) { widgets.push({key:'hotels', icon:'🏨', title:'Hotels', subtitle:'Accommodation recommendations', body:buildHotelsSection(hotels)}); }
        if (hasBudget && (type === 'BUDGET' || isMulti)) { widgets.push({key:'budget', icon:'💰', title:'Budget', subtitle:'Estimated cost', body:buildBudgetTable(budget)}); }
        if ((type === 'RESEARCH' || type === 'HISTORY' || (!isMulti && !widgets.length)) && hasAnswer) widgets.push({key:'answer', icon:meta[0], title:meta[1], subtitle:meta[2], body:'<div class="specialist-answer">' + formatKnowledgeText(answer) + '</div>'});

        const nav = widgets.length > 1 ? '<nav class="plan-section-nav">' + widgets.map((w,i) => '<button type="button" class="plan-nav-btn' + (i === 0 ? ' active' : '') + '" data-section-target="' + w.key + '" title="Open ' + escapeHtml(w.title) + '" aria-label="Open ' + escapeHtml(w.title) + '">' + w.icon + ' ' + escapeHtml(w.title) + '</button>').join('') + '</nav>' : '';
        let steps = '';
        widgets.forEach((w, i) => {
            steps += '<section id="section-' + w.key + '" class="plan-step">'
                + '<div class="plan-step-rail"><span class="plan-step-number">' + (i + 1) + '</span><span class="plan-step-line"></span></div>'
                + '<div class="plan-step-card"><div class="plan-step-head"><div class="plan-step-heading"><div class="plan-step-icon">' + w.icon + '</div><div><h3>' + escapeHtml(w.title) + ' <span class="plan-check">✓</span></h3><p>' + escapeHtml(w.subtitle) + '</p></div></div><span class="ready-badge">✓ Complete</span></div>'
                + w.body + '</div></section>';
        });
        if (!steps) steps = '<div class="specialist-empty">No additional structured details were returned for this request.</div>';

        let html = '<div class="plan-workspace specialist-dynamic-workspace">'
            + '<header class="plan-header specialist-dynamic-header"><div class="plan-header-top"><div><div class="trip-eyebrow">AGENTICTRIPAI · ' + escapeHtml(type.replace(/_/g, ' ')) + '</div><h1>' + escapeHtml(meta[1]) + '</h1></div><div class="plan-header-status">' + (needsUserInput ? 'Needs your input' : '✓ Complete') + '</div></div>'
            + '<div class="plan-header-facts"><span>✦ Requested information</span>' + (location ? '<span>📍 ' + escapeHtml(location) + '</span>' : '') + (userRequest ? '<span class="specialist-request">' + escapeHtml(String(userRequest).slice(0, 100)) + '</span>' : '') + '</div></header>'
            + (needsUserInput && clarification ? '<div class="agent-clarification-banner"><strong>✦ Action needed</strong><span>' + escapeHtml(clarification) + '</span></div>' : '')
            + nav + '<div class="plan-steps">' + steps + '</div>'
            + '</div>';
        return html;
    };

    const formatPlanTimestamp = (value) => {
        if (!value) return '';
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return String(value);
        return date.toLocaleString('en-IN', {
            day: '2-digit', month: 'short', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    };

    const buildPlanCardHtml = (data, userRequest) => {
        const plan = data.plan || {};
        const trip = plan.trip || {};
        const tips = safeTripTips(plan.tips || data.finalPlan || '');
        if (data.status === 'ERROR' || (tips && String(tips).startsWith('Error:'))) {
            return '<div class="result-card"><div class="result-hero"><div class="result-eyebrow">Agent response</div><h2 class="result-title">Something went wrong</h2></div><div class="result-body"><div class="validation-banner">⚠ ' + escapeHtml(safeUiErrorMessage()) + '</div></div></div>';
        }

        const hasItineraryData = !!(plan.itinerary && Array.isArray(plan.itinerary.days) && plan.itinerary.days.length);
        const requestType = String(data.requestType || trip.requestType || '').toUpperCase();
        const isTripPlan = data.tripPlanning === true || requestType === 'TRIP_PLANNING';
        const flights = Array.isArray(plan.flights) ? plan.flights.filter(Boolean) : [];
        const hotels = Array.isArray(plan.hotels) ? plan.hotels.filter(Boolean) : [];
        const hasFlights = flights.length > 0;
        const hasReturnFlights = hasFlights && flights.some(f => String(f.status || '').toLowerCase() !== 'unavailable' && String(f.direction || 'outbound').toLowerCase() === 'return');
        const roundTripReturnMissing = hasFlights && trip.roundTrip !== false && !hasReturnFlights;
        const hasHotels = hotels.length > 0;
        const hasItinerary = hasItineraryData;
        const hasBudget = !!(plan.budget && Array.isArray(plan.budget.lineItems) && plan.budget.lineItems.length);
        const hasWeather = !!(plan.weather && ((plan.weather.current && plan.weather.current.temperature != null) || (Array.isArray(plan.weather.days) && plan.weather.days.some(d => d && (d.high != null || d.low != null || d.condition)))));
        const knowledge = plan.knowledge || {};
        const exec = data.execution || {};
        const hasKnowledge = !!(knowledge.available === true || knowledge.answer || exec.ragAnswer || data.ragAnswer);
        const quality = trip.qualityScore != null ? trip.qualityScore : (plan.validation && plan.validation.quality && plan.validation.quality.overall > 0 ? Math.round(plan.validation.quality.overall * 100) : null);
        const responseStatus = String(data.status || '').toUpperCase();
        const tripStatus = String(trip.status || '').toUpperCase();
        const awaitingApproval = Boolean(data.awaitingApproval === true || trip.awaitingApproval === true || responseStatus === 'PENDING_APPROVAL' || tripStatus === 'PENDING_APPROVAL');
        const complete = !awaitingApproval && (responseStatus === 'COMPLETE' || tripStatus === 'COMPLETE');
        const flightDateUnconfirmed = hasFlights && flights.some(f => /not independently confirmed/i.test(String(f.notes || '')));
        const origin = trip.origin || data.origin || '';
        const destination = trip.destination || data.destination || '';
        const route = origin && destination ? origin + ' → ' + destination : (destination || 'Travel plan');
        const nights = trip.nights || '';
        const travelers = data.travelers || trip.travelers || 1;
        const budgetLabel = trip.budgetLabel || data.budgetLabel || '';
        const requirements = Array.isArray(trip.requirements) ? trip.requirements : [];
        const imageUrl = trip.imageUrl || trip.heroImageUrl || data.destinationImageUrl || '';
        const statusText = complete ? 'Plan Ready' : awaitingApproval ? 'Ready for review' : 'Planning in progress';

        // Non-trip requests stay intentionally lightweight: only requested/returned widgets are shown.
        if (!isTripPlan) {
            return buildSpecialistResponseHtml(data, userRequest);
        }

        const section = (number, key, icon, title, subtitle, body, badge, extraClass) => {
            if (!body) return '';
            return '<section id="section-' + key + '" class="plan-step ' + (extraClass || '') + '">'
                + '<div class="plan-step-rail"><span class="plan-step-number">' + number + '</span><span class="plan-step-line"></span></div>'
                + '<div class="plan-step-card">'
                + '<div class="plan-step-head"><div class="plan-step-heading"><div class="plan-step-icon">' + icon + '</div><div><h3>' + title + ' <span class="plan-check">✓</span></h3><p>' + subtitle + '</p></div></div>'
                + (badge ? '<span class="ready-badge ' + (badge === 'Review' ? 'review-badge' : '') + '">' + badge + '</span>' : '')
                + '</div>' + body + '</div></section>';
        };

        let html = '<div class="plan-workspace">';
        html += '<header class="plan-header">'
            + '<div class="plan-header-top"><div><div class="trip-eyebrow">AI TRAVEL PLAN</div><h1>' + escapeHtml(route) + '</h1></div><div class="plan-header-status">' + escapeHtml(statusText) + '</div></div>'
            + '<div class="plan-header-facts">'
            + '<span>📅 ' + escapeHtml(trip.datesFlexible === true || (!trip.departureDate && !trip.returnDate) ? 'Dates flexible' : formatDateRange(trip.departureDate, trip.returnDate)) + (nights ? ' · ' + escapeHtml(String(nights)) + ' nights' : '') + '</span>'
            + '<span>👤 ' + escapeHtml(String(travelers)) + ' traveler' + (Number(travelers) === 1 ? '' : 's') + '</span>'
            + '<span>↔ ' + (trip.roundTrip === false ? 'One-way' : 'Round trip') + '</span>'
            + (budgetLabel ? '<span>💰 ' + escapeHtml(budgetLabel) + '</span>' : '')
            + (trip.generatedAt ? '<span>🕐 Generated ' + escapeHtml(formatPlanTimestamp(trip.generatedAt)) + '</span>' : '')
            + (trip.updatedAt && trip.updatedAt !== trip.generatedAt ? '<span>↻ Updated ' + escapeHtml(formatPlanTimestamp(trip.updatedAt)) + '</span>' : '')
            + '</div>'
            + (requirements.length ? '<div class="plan-requirements">' + requirements.map(r => '<span>✓ ' + escapeHtml(r) + '</span>').join('') + '</div>' : '')
            + '</header>'
            + (String(data.status || '').toUpperCase() === 'NEEDS_USER_INPUT' && String(data.clarificationRequired || '').trim() ? '<div class="agent-clarification-banner"><strong>✦ Action needed</strong><span>' + escapeHtml(String(data.clarificationRequired)) + '</span></div>' : '');

        const nav = [];
        if (hasFlights) nav.push(['flights','✈ Flights']);
        if (hasHotels) nav.push(['hotels','🏨 Hotels']);
        if (hasItinerary) nav.push(['itinerary','🗓 Itinerary']);
        if (hasWeather) nav.push(['weather','☀ Weather']);
        if (hasBudget) nav.push(['budget','💰 Budget']);
        if (hasKnowledge) nav.push(['knowledge','🧠 Tips']);
        if (nav.length > 1) html += '<nav class="plan-section-nav">' + nav.map((x,i) => '<button type="button" class="plan-nav-btn' + (i === 0 ? ' active' : '') + '" data-section-target="' + x[0] + '" title="Open ' + escapeHtml(x[1].replace(/^[^A-Za-z]+/, '')) + '" aria-label="Open ' + escapeHtml(x[1].replace(/^[^A-Za-z]+/, '')) + '">' + x[1] + '</button>').join('') + '</nav>';

        html += '<div class="plan-steps">';
        let step = 1;
        if (hasFlights) {
            const badge = (flightDateUnconfirmed || roundTripReturnMissing) ? 'Review' : '✓ Ready';
            html += section(step++, 'flights', '✈️', 'Flights', flightDateUnconfirmed ? 'Live schedules · requested date not independently confirmed' : 'Best available flight options for your trip', buildFlightsSection(flights), badge);
        }
        if (hasHotels) html += section(step++, 'hotels', '🏨', 'Hotels', 'Top accommodation recommendations', buildHotelsSection(hotels), '✓ Ready');
        if (hasItinerary) html += section(step++, 'itinerary', '🗓️', 'Day-wise Itinerary', 'A complete day-by-day plan', buildItinerarySection(plan.itinerary), '✓ Ready', 'plan-step-itinerary');
        if (hasWeather) html += section(step++, 'weather', '☀️', 'Weather & Best Time', 'Travel-date forecast and planning guidance', buildWeatherSection(plan.weather), '✓ Ready');
        if (hasBudget) html += section(step++, 'budget', '💰', 'Budget Breakdown', 'Estimated cost for your trip', buildBudgetTable(plan.budget), '✓ Ready');
        if (hasKnowledge) {
            const knowledgeBody = buildKnowledgeGuidanceSection(data, plan);
            if (knowledgeBody) html += section(step++, 'knowledge', '🧠', 'Travel Knowledge & Tips', 'Useful destination guidance for your trip', knowledgeBody.replace(/^<section[^>]*>|<\/section>$/g, ''), '✓ Grounded');
        }
        if (tips && !hasKnowledge) html += section(step++, 'tips', '💡', 'Travel Tips', 'Practical trip-specific suggestions', '<div class="knowledge-answer">' + formatKnowledgeText(tips) + '</div>', '✓ Ready');
        html += '</div>';

        if (data.validationErrors?.length || data.semanticNotes?.length || data.ragJudge) {
            html += '<div class="plan-validation">' + buildPlanReview(data, userRequest) + '</div>';
        }

        if (awaitingApproval && data.threadId && (data.tripPlanning !== false || responseStatus === 'NEEDS_USER_INPUT')) {
            const clarification = responseStatus === 'NEEDS_USER_INPUT' || Boolean(data.clarificationRequired);
            if (clarification) {
                html += '<section class="plan-final-action" data-decision-panel><div><span class="decision-status pending"><i class="decision-dot"></i> More information needed</span><h3>One detail is missing</h3><p>' + escapeHtml(String(data.clarificationQuestion || data.clarificationRequired || 'Please provide the missing travel detail.')) + '</p></div><div class="final-actions-inline"><button type="button" class="final-modify" data-plan-action="modify" data-thread-id="' + escapeHtml(data.threadId) + '">✎ Provide details</button><button type="button" class="final-reject" data-plan-action="reject" data-thread-id="' + escapeHtml(data.threadId) + '">✕ Cancel</button></div></section>';
            } else {
                html += '<section class="plan-final-action" data-decision-panel><div><span class="decision-status pending"><i class="decision-dot"></i> Waiting for your decision</span><h3>Ready to finalize?</h3><p>Review the plan, then approve it, request a change, or reject it.</p><div class="decision-progress" data-decision-progress><span class="decision-spinner"></span><span data-decision-message>Working…</span></div></div><div class="final-actions-inline"><button type="button" class="final-approve" data-plan-action="approve" data-thread-id="' + escapeHtml(data.threadId) + '">✓ Approve</button><button type="button" class="final-modify" data-plan-action="modify" data-thread-id="' + escapeHtml(data.threadId) + '">✎ Modify</button><button type="button" class="final-reject" data-plan-action="reject" data-thread-id="' + escapeHtml(data.threadId) + '">✕ Reject</button></div></section>';
            }
        } else if (complete) {
            html += '<section class="plan-final-action confirmed"><div><span class="decision-status"><i class="decision-dot"></i> Plan confirmed</span><h3>✓ Trip plan confirmed</h3><p>This plan has already been finalized.</p></div></section>';
        }

        html += '</div>';
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
        if (data?.threadId) row.dataset.threadId = String(data.threadId);
        row.innerHTML =
            '<div class="chat-avatar">AI</div>' +
            '<div class="chat-bubble">' +
            '<div class="chat-role">AgenticTripAI</div>' +
            '<div class="chat-body"></div>' +
            '<div class="chat-actions"></div>' +
            '</div>';
        const userRequest = userRequestHint || findUserRequestForPlan(row);
        const body = row.querySelector('.chat-body');
        // TravelPlanResponse is also used for specialist responses. Those
        // responses can legitimately have no plan object while still carrying
        // requestType, weather/hotels/flights/budget or RAG knowledge. Always
        // let the structured renderer decide which UI is appropriate.
        const responseType = String(data?.requestType || data?.plan?.trip?.requestType || '').toUpperCase();
        const specialistResponse = data && data.tripPlanning !== true
            && ['WEATHER','FLIGHT_SEARCH','HOTEL_SEARCH','BUDGET','RESEARCH','MULTI_CAPABILITY','MULTI_INTENT','TRAVEL_INFORMATION','HISTORY','GENERAL'].includes(responseType);
        const hasStructuredResponse = !!(data && (
            data.threadId || data.plan || data.requestType || data.weather ||
            data.flights || data.hotels || data.budget || data.execution ||
            data.ragAnswer || data.knowledge
        ));
        if (responseType === 'HISTORY') {
            body.innerHTML = buildHistoryResponseHtml(data);
        } else if (specialistResponse) {
            body.innerHTML = buildSpecialistResponseHtml(data, userRequest);
        } else if (hasStructuredResponse) {
            body.innerHTML = buildPlanCardHtml(data, userRequest);
        } else {
            const text = data && (data.finalPlan || data.text) ? (data.finalPlan || data.text) : 'Plan response available.';
            body.innerHTML = '<div class="server-memory-card">' + formatKnowledgeText(text) + '</div>';
        }
        body.querySelectorAll('[data-section-target]').forEach(tab => {
            tab.addEventListener('click', () => {
                body.querySelectorAll('.workspace-tab,.plan-nav-btn').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                const target = tab.dataset.sectionTarget;
                if (target === 'overview') {
                    body.querySelector('.trip-banner')?.scrollIntoView({behavior:'smooth', block:'start'});
                    return;
                }
                const section = body.querySelector('#section-' + CSS.escape(target));
                if (!section) return;
                // The top widget tabs are also a quick way to unfold a widget.
                // This makes the navigation useful even after every widget has been folded in.
                if (section.classList.contains('widget-collapsed')) {
                    section.classList.remove('widget-collapsed');
                    updateWidgetFoldState(section);
                }
                section.scrollIntoView({behavior:'smooth', block:'center'});
            });
        });
        chatThread.appendChild(row);
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
            ? ' · Confidence ' + Math.round(data.planQuality.overall * 100) + '/100' : '';
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

    const updateLiveResponse = (live, node, phase = 'start') => {
        if (!live || !node) return;
        const raw = String(node).replace(/^__+|__+$/g, '').replace(/[-_]+/g, ' ').trim();
        // START is a graph lifecycle marker, not an actual agent stage.
        // Never leave the UI showing "Running START…".
        if (!raw || raw.toUpperCase() === 'START') {
            if (phase === 'start') {
                live.state.textContent = 'Understanding request…';
            }
            return;
        }
        const normalized = raw;
        if (phase === 'start') {
            live.state.textContent = 'Running ' + normalized + '…';
        }
        const key = normalized.toLowerCase();
        let pill = Array.from(live.steps.querySelectorAll('.live-step'))
            .find(p => p.dataset.node === key);
        if (!pill) {
            pill = document.createElement('span');
            pill.className = 'live-step';
            pill.dataset.node = key;
            live.steps.appendChild(pill);
        }
        pill.textContent = '✓ ' + normalized;
        live.steps.querySelectorAll('.live-step').forEach(p => p.classList.remove('active'));
        if (phase === 'start') {
            pill.classList.add('active');
        }
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

        // Modify mode deliberately resumes the existing LangGraph checkpoint.
        // It must not create a new trip or rerun the original fan-out.
        if (modifyContext?.threadId) {
            const context = modifyContext;
            const success = await decide('/api/plan/modify', context.threadId, text, context.sourceRow);
            if (success) {
                exitModifyMode(true);
            }
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
                // The backend emits a `started` event immediately after the
                // async graph begins.  Listen to it as well as `node`; without
                // this handler the live card stays on its initial
                // "Understanding request…" text until the first graph node
                // finishes, which can take a while when the intent model is
                // running.
                es.addEventListener('started', (evt) => {
                    // Graph accepted. The activity row is the single source of
                    // truth for the current live action; don't duplicate it in
                    // the header.
                    try {
                        const data = JSON.parse(evt.data);
                        if (data.node && String(data.node).toUpperCase() !== 'START') {
                            updateLiveResponse(liveResponse, data.node, 'start', data.message);
                        }
                    } catch (ignored) {}
                });
                es.addEventListener('node_start', (evt) => {
                    try {
                        const data = JSON.parse(evt.data);
                        if (data.node) {
                            if (statusSpan) statusSpan.textContent = data.message || ('Running ' + data.node + '…');
                            updateLiveResponse(liveResponse, data.node, 'start', data.message);
                        }
                    } catch (ignored) {}
                });
                es.addEventListener('node_complete', (evt) => {
                    try {
                        const data = JSON.parse(evt.data);
                        if (data.node) {
                            updateLiveResponse(liveResponse, data.node, 'complete', data.message);
                        }
                    } catch (ignored) {}
                });
                // Specialist-level events are the most useful live signal.
                // Show them in the activity row while keeping the five phase
                // timeline clean and non-duplicated.
                es.addEventListener('task_start', (evt) => {
                    try {
                        const data = JSON.parse(evt.data);
                        if (data.task) {
                            setLiveTask(liveResponse, data.task, 'RUNNING', data.message);
                            if (statusSpan) statusSpan.textContent = data.message || humanTaskLabel(data.task) + '…';
                        }
                    } catch (ignored) {}
                });
                es.addEventListener('task_complete', (evt) => {
                    try {
                        const data = JSON.parse(evt.data);
                        if (data.task) {
                            const status = String(data.status || '').toUpperCase() === 'FAILED' ? 'FAILED' : 'SUCCEEDED';
                            setLiveTask(liveResponse, data.task, status, data.message);
                            if (statusSpan) statusSpan.textContent = data.message || (humanTaskLabel(data.task) + (status === 'FAILED' ? ' failed' : ' complete'));
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
                        liveResponse.row.classList.add('live-complete');
                        setTimeout(() => removeLiveResponse(liveResponse), 260);
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
                    liveResponse.row.classList.add('live-complete');
                    setTimeout(() => removeLiveResponse(liveResponse), 260);
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

    const setDecisionProgress = (sourceRow, message, state = 'working') => {
        const panel = sourceRow?.querySelector('[data-decision-panel]');
        if (!panel) return;
        const progress = panel.querySelector('[data-decision-progress]');
        const messageNode = panel.querySelector('[data-decision-message]');
        if (!progress) return;
        progress.classList.add('visible');
        progress.classList.toggle('success', state === 'success');
        progress.classList.toggle('error', state === 'error');
        if (messageNode) {
            messageNode.textContent = message;
            const spinner = progress.querySelector('.decision-spinner');
            if (spinner) spinner.style.display = state === 'working' ? '' : 'none';
        }
    };

    const setDecisionButtonBusy = (button, busy, label) => {
        if (!button) return;
        if (busy) {
            button.dataset.originalLabel = button.innerHTML;
            button.classList.add('is-busy');
            button.disabled = true;
            button.innerHTML = '<span class="decision-button-spinner"></span>' + label;
        } else {
            button.classList.remove('is-busy');
            button.disabled = false;
            if (button.dataset.originalLabel) button.innerHTML = button.dataset.originalLabel;
        }
    };

    // Decision actions are delegated from the chat thread so they keep working
    // after assistant cards are replaced/re-rendered. The button carries the
    // thread id explicitly, avoiding any dependency on a stale closure.
    chatThread.addEventListener('click', async (event) => {
        const button = event.target.closest('[data-plan-action]');
        if (!button || !chatThread.contains(button)) return;
        event.preventDefault();
        event.stopPropagation();
        if (button.disabled || button.dataset.busy === 'true') return;
        const row = button.closest('.chat-msg.assistant');
        const action = button.getAttribute('data-plan-action');
        const threadId = button.getAttribute('data-thread-id') || row?.dataset.threadId;
        if (!threadId) {
            showToast('This plan no longer has an active decision thread. Please create a new plan.');
            return;
        }
        if (action === 'modify') {
            setDecisionProgress(row, 'Modify mode is ready — describe the change below.', 'working');
            enterModifyMode(threadId, row);
            return;
        }
        const endpoint = action === 'approve' ? '/api/plan/approve' : action === 'reject' ? '/api/plan/reject' : null;
        if (!endpoint) return;
        await decide(endpoint, threadId, null, row);
    });

    chatThread.addEventListener('click', (event) => {
        const hotelMoreButton = event.target.closest('[data-hotels-more]');
        if (hotelMoreButton) {
            const list = hotelMoreButton.previousElementSibling;
            if (!list || !list.classList.contains('hotel-list')) return;
            const expanded = list.classList.toggle('expanded');
            hotelMoreButton.textContent = expanded
                ? '− Show fewer hotel options'
                : '＋ Show more hotel options';
            return;
        }

        const flightMoreButton = event.target.closest('[data-flights-more]');
        if (flightMoreButton) {
            const list = flightMoreButton.previousElementSibling;
            if (!list || !list.classList.contains('flight-list')) return;
            const expanded = list.classList.toggle('expanded');
            flightMoreButton.textContent = expanded
                ? '− Show fewer flight options'
                : '＋ ' + list.querySelectorAll('.is-extra-flight').length + ' more available option'
                    + (list.querySelectorAll('.is-extra-flight').length === 1 ? '' : 's');
        }
    });

    const decide = async (url, threadId, notes, sourceRow) => {
        if (!threadId) {
            appendAssistantMessage({ text: 'This plan is no longer available for approval. Please create a new trip plan.' }, false);
            return false;
        }
        loadingState.classList.add('visible');
        submitButton.disabled = true;
        const action = url.includes('approve') ? 'approve' : url.includes('reject') ? 'reject' : 'modify';
        const actionButton = sourceRow?.querySelector('[data-plan-action="' + action + '"]');
        const busyLabel = action === 'approve' ? 'Approving…' : action === 'reject' ? 'Rejecting…' : 'Updating…';
        setDecisionProgress(sourceRow, busyLabel + ' please wait…', 'working');
        setDecisionButtonBusy(actionButton, true, busyLabel);
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
            setDecisionProgress(sourceRow, action === 'approve' ? 'Plan approved successfully.' : action === 'reject' ? 'Plan rejected successfully.' : 'Plan updated successfully.', 'success');
            if (notes) {
                appendUserMessage(notes);
            } else if (url.includes('reject')) {
                appendUserMessage('Rejected this plan.');
            } else if (url.includes('approve')) {
                appendUserMessage('Approved this plan.');
            }
            appendAssistantMessage(payload);
            loadDbTrips(false);
            return true;
        } catch (error) {
            if (sourceRow) {
                sourceRow.querySelectorAll('[data-plan-action]').forEach(btn => {
                    btn.disabled = false;
                    btn.classList.remove('action-unavailable');
                });
            }
            const errorMessage = error && error.message ? error.message : safeUiErrorMessage();
            setDecisionProgress(sourceRow, 'Action failed: ' + errorMessage, 'error');
            showToast(errorMessage);
            setDecisionButtonBusy(actionButton, false);
            return false;
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
