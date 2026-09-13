
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
            const res = await fetch('/api/chat/history?userId=' + encodeURIComponent(userId) + '&limit=50');
            if (!res.ok) return;
            const items = await res.json();
            if (!Array.isArray(items) || !items.length) return;

            const store = loadStore();
            const serverPrefix = 'server-' + userId + '-';
            // Remove the old single server-history bucket and rebuild it as
            // individual recent trips. This makes the sidebar useful instead
            // of showing one giant "previous chats" entry.
            store.chats = store.chats.filter(c =>
                !String(c.id || '').startsWith(serverPrefix) && c.id !== ('server-' + userId));

            let current = null;
            items.forEach(item => {
                const role = item.role === 'assistant' ? 'assistant' : 'user';
                if (role === 'user') {
                    current = {
                        id: serverPrefix + String(item.id || Date.now()),
                        title: String(item.content || 'Previous trip').trim().slice(0, 60) || 'Previous trip',
                        updatedAt: item.createdAt ? new Date(item.createdAt).getTime() : Date.now(),
                        messages: [{ role: 'user', text: item.content || '', planData: null }]
                    };
                    store.chats.push(current);
                } else if (current) {
                    // Server memory stores a text summary, not the full structured
                    // TripPlanResponse. Keep it as plain assistant text so the UI
                    // does not manufacture fake Flights/Hotels/Budget cards.
                    current.messages.push({ role: 'assistant', text: item.content || '', planData: null });
                    if (item.createdAt) current.updatedAt = new Date(item.createdAt).getTime();
                }
            });

            saveStore(store);
            renderHistoryList();
            const viewingFresh = startedFreshChat || (currentChatId && String(currentChatId).startsWith('chat-'));
            if (!viewingFresh && !chatThread.querySelector('.chat-msg')) {
                const newest = store.chats
                    .filter(c => String(c.id || '').startsWith(serverPrefix))
                    .sort((a, b) => b.updatedAt - a.updatedAt)[0];
                if (newest) openChat(newest.id);
            }
        } catch (e) {
            console.warn('Could not load server chat history', e);
        }
    };

    const tripStatusLabel = (trip) => {
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
            return '<article class="trip-history-card">'
                + '<div class="trip-history-top"><div><div class="trip-history-route">' + escapeHtml(route) + '</div><div class="trip-history-date">Updated ' + escapeHtml(t.updatedAt ? new Date(t.updatedAt).toLocaleString() : '') + '</div></div>'
                + '<span class="trip-history-status' + statusClass + '">' + escapeHtml(status) + '</span></div>'
                + '<div class="trip-history-meta">' + meta.map(m => '<span class="trip-history-chip">' + escapeHtml(m) + '</span>').join('') + '</div>'
                + '<button type="button" class="trip-history-open" data-trip-id="' + escapeHtml(String(t.id)) + '">Open saved plan →</button>'
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
            const userId = userIdField.value.trim() || 'aaro_hi_user';
            const res = await fetch('/api/trips/' + encodeURIComponent(id) + '?userId=' + encodeURIComponent(userId));
            const data = await res.json().catch(() => ({}));
            if (!res.ok) throw new Error(data.error || safeUiErrorMessage());
            showHomeView();
            clearThreadDom();
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
        if (dbTrips.length) {
            dbTrips.slice(0, 20).forEach(trip => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'chat-history-item';
                const title = document.createElement('span');
                title.textContent = [trip.origin, trip.destination].filter(Boolean).join(' → ') || trip.title || 'Saved trip';
                const status = document.createElement('span');
                status.className = 'db-status';
                status.textContent = 'DB';
                title.appendChild(status);
                const meta = document.createElement('span');
                meta.className = 'meta';
                meta.textContent = tripStatusLabel(trip) + ' · ' + (trip.updatedAt ? new Date(trip.updatedAt).toLocaleDateString() : '');
                btn.appendChild(title);
                btn.appendChild(meta);
                btn.onclick = () => openSavedTrip(trip.id);
                chatHistoryList.appendChild(btn);
            });
            return;
        }
        const store = loadStore();
        if (!store.chats.length) {
            const empty = document.createElement('div');
            empty.className = 'chat-history-empty';
            empty.textContent = 'No saved trips yet. Create a New Trip to get started.';
            chatHistoryList.appendChild(empty);
            return;
        }
        store.chats.slice().sort((a,b)=>b.updatedAt-a.updatedAt).slice(0,12).forEach(chat => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'chat-history-item' + (chat.id === currentChatId ? ' active' : '');
            const title = document.createElement('span'); title.textContent = chat.title || 'Untitled trip';
            const meta = document.createElement('span'); meta.className='meta'; meta.textContent = new Date(chat.updatedAt).toLocaleDateString();
            btn.appendChild(title); btn.appendChild(meta); btn.onclick=()=>openChat(chat.id); chatHistoryList.appendChild(btn);
        });
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

    const openChat = (chatId) => {
        showHomeView();
        const store = loadStore();
        const chat = store.chats.find(c => c.id === chatId);
        if (!chat) {
            return;
        }
        currentChatId = chatId;
        startedFreshChat = String(chatId).startsWith('chat-');
        clearThreadDom();
        let lastUser = '';
        (chat.messages || []).forEach(msg => {
            if (msg.role === 'user') {
                lastUser = msg.text || '';
                renderUserMessage(msg.text, false);
            } else {
                renderAssistantMessage(msg.planData || {
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

    const buildPlanCardHtml = (data, userRequest) => {
        const plan = data.plan || {};
        const trip = plan.trip || {};
        const tips = plan.tips || data.finalPlan || '';
        if (data.status === 'ERROR' || (tips && String(tips).startsWith('Error:'))) {
            return '<div class="result-card"><div class="result-hero"><div class="result-eyebrow">Agent response</div><h2 class="result-title">Something went wrong</h2></div><div class="result-body"><div class="validation-banner">⚠ ' + escapeHtml(safeUiErrorMessage()) + '</div></div></div>';
        }
        const hasFlights = Array.isArray(plan.flights) && plan.flights.length;
        const hasHotels = Array.isArray(plan.hotels) && plan.hotels.length;
        const hasItinerary = !!(plan.itinerary && Array.isArray(plan.itinerary.days) && plan.itinerary.days.length);
        const hasBudget = !!(plan.budget && Array.isArray(plan.budget.lineItems) && plan.budget.lineItems.length);
        const hasWeather = !!(plan.weather && (plan.weather.summary || plan.weather.location));
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
        const title = trip.title || tripTitle(data);
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
        html += '<div class="workspace-tabs"><button type="button" class="workspace-tab active" data-section-target="overview">Overview</button>' + (hasFlights ? '<button type="button" class="workspace-tab" data-section-target="flights">✈ Flights</button>' : '') + (hasHotels ? '<button type="button" class="workspace-tab" data-section-target="hotels">🏨 Hotels</button>' : '') + (hasItinerary ? '<button type="button" class="workspace-tab" data-section-target="itinerary">🗓 Itinerary</button>' : '') + (hasWeather ? '<button type="button" class="workspace-tab" data-section-target="weather">☀ Weather</button>' : '') + (hasBudget ? '<button type="button" class="workspace-tab" data-section-target="budget">💰 Budget</button>' : '') + '</div>';
        html += '<div class="workspace-grid">';
        if (hasFlights) html += '<section id="section-flights" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">✈️ Flights</div><div class="workspace-card-sub">Best available flight options</div></div><span class="ready-badge">✓ Ready</span></div><div class="workspace-content">' + buildFlightsSection(plan.flights) + '</div></section>';
        if (hasHotels) html += '<section id="section-hotels" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">🏨 Hotels</div><div class="workspace-card-sub">Accommodation recommendations</div></div><span class="ready-badge">✓ Ready</span></div><div class="workspace-content">' + buildHotelsSection(plan.hotels) + '</div></section>';
        if (hasWeather) html += '<section id="section-weather" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">☀️ Weather</div><div class="workspace-card-sub">Travel-date forecast</div></div><span class="ready-badge">✓ Ready</span></div><div class="workspace-content">' + buildWeatherSection(plan.weather) + '</div></section>';
        if (hasBudget) html += '<section id="section-budget" class="workspace-card"><div class="workspace-card-head"><div><div class="workspace-card-title">💰 Budget</div><div class="workspace-card-sub">Estimated trip cost</div></div><span class="ready-badge">✓ Ready</span></div><div class="workspace-content">' + buildBudgetTable(plan.budget) + '</div></section>';
        if (hasItinerary) html += '<section id="section-itinerary" class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">🗓️ Day-wise Itinerary</div><div class="workspace-card-sub">Your planned activities by day</div></div><span class="ready-badge">✓ Ready</span></div><div class="workspace-content itin-compact">' + buildItinerarySection(plan.itinerary) + '</div></section>';
        if (tips) html += '<section class="workspace-card full"><div class="workspace-card-head"><div><div class="workspace-card-title">💡 Travel Notes</div><div class="workspace-card-sub">Additional guidance from the agent</div></div></div><div class="workspace-content knowledge-answer">' + formatKnowledgeText(tips) + '</div></section>';
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
        if (awaitingApproval && data.threadId) {
            html += '<section class="final-decision"><div class="decision-status pending"><i class="decision-dot"></i> Waiting for your decision</div><h3>Ready to finalize?</h3><p>Review the complete plan. Approve it, request a change, or reject it.</p><div class="final-actions"><button type="button" class="final-approve" data-plan-action="approve">✓ Approve</button><button type="button" class="final-modify" data-plan-action="modify">✎ Modify</button><button type="button" class="final-reject" data-plan-action="reject">✕ Reject</button></div></section>';
        } else if (complete) {
            html += '<section class="final-decision"><div class="decision-status"><i class="decision-dot"></i> Plan confirmed</div><h3>✓ Trip plan confirmed</h3><p>This plan has already been finalized. Create a new trip or use Modify from a pending plan.</p></section>';
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
    renderHistoryList();
    loadDbTrips(false);
    const storeOnLoad = loadStore();
    if (storeOnLoad.chats.length) {
        openChat(storeOnLoad.chats.slice().sort((a, b) => b.updatedAt - a.updatedAt)[0].id);
    }
    loadServerHistoryIntoStore();
