(function () {
    const sessionStorageKey = "stock-analysis-chat:session-id";
    const userStorageKey = "stock-analysis-chat:user-id";

    const state = {
        loading: false,
        messages: [],
        sessionId: null,
        userId: null,
        defaultUserId: null
    };

    const elements = {
        questionInput: document.getElementById("question-input"),
        userIdInput: document.getElementById("user-id-input"),
        messages: document.getElementById("messages"),
        composer: document.getElementById("composer"),
        sendButton: document.getElementById("send-button"),
        clearButton: document.getElementById("clear-chat-button"),
        statusPill: document.getElementById("status-pill"),
        sessionIdValue: document.getElementById("session-id-value"),
        emptyStateTemplate: document.getElementById("empty-state-template")
    };

    initialize();

    async function initialize() {
        state.sessionId = hydrateSessionId();
        state.userId = hydrateUserIdOverride();

        elements.composer.addEventListener("submit", onSubmit);
        elements.clearButton.addEventListener("click", clearChat);
        elements.questionInput.addEventListener("input", autoResizeTextarea);
        elements.questionInput.addEventListener("keydown", onComposerKeydown);
        elements.messages.addEventListener("click", onSuggestionClick);
        elements.userIdInput.addEventListener("input", onUserIdInput);
        elements.userIdInput.addEventListener("blur", commitUserId);

        setStatus("Session ready");
        await hydrateChatContext();
        renderIdentity();
        renderMessages();
        autoResizeTextarea();
    }

    function onComposerKeydown(event) {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            elements.composer.requestSubmit();
        }
    }

    function onSuggestionClick(event) {
        const suggestion = event.target.closest(".suggestion");
        if (!suggestion) {
            return;
        }

        elements.questionInput.value = suggestion.dataset.question || "";
        autoResizeTextarea();
        elements.questionInput.focus();
        setStatus("Prompt loaded");
    }

    function onUserIdInput() {
        state.userId = elements.userIdInput.value;
    }

    async function onSubmit(event) {
        event.preventDefault();
        if (state.loading) {
            return;
        }

        const question = elements.questionInput.value.trim();
        if (!question) {
            setStatus("Question required", "warning");
            elements.questionInput.focus();
            return;
        }

        commitUserId();

        appendMessage({
            role: "user",
            content: question,
            timestamp: new Date().toISOString()
        });

        elements.questionInput.value = "";
        autoResizeTextarea();
        setLoading(true);

        try {
            const response = await requestChat(question);
            state.userId = response.userId || activeUserId();
            state.sessionId = response.sessionId || state.sessionId;
            persistUserIdOverride();
            persistSessionId(state.sessionId);
            renderIdentity();

            appendMessage({
                role: "assistant",
                content: response.response || "No response returned.",
                timestamp: new Date().toISOString(),
                memories: response.retrievedMemories || [],
                fromSemanticCache: Boolean(response.fromSemanticCache),
                triggeredAgents: Array.isArray(response.triggeredAgents) ? response.triggeredAgents : [],
                responseTimeMs: Number.isFinite(response.responseTimeMs) ? response.responseTimeMs : null
            });
            setStatus("Response received");
        } catch (error) {
            appendMessage({
                role: "assistant",
                variant: "error",
                content: error.message,
                timestamp: new Date().toISOString()
            });
            setStatus("Request failed", "error");
        } finally {
            setLoading(false);
            elements.questionInput.focus();
        }
    }

    async function requestChat(message) {
        const response = await fetch(new URL("./api/chat", window.location.href), {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                userId: activeUserId(),
                sessionId: state.sessionId,
                message: message
            })
        });

        const contentType = response.headers.get("content-type") || "";
        const rawBody = await response.text();
        const body = contentType.includes("application/json") ? safeParseJson(rawBody) : null;

        if (!response.ok) {
            throw new Error(extractErrorMessage(body, rawBody, response.status));
        }

        return body || {};
    }

    async function hydrateChatContext() {
        try {
            const response = await fetch(new URL("./api/chat/context", window.location.href));
            if (!response.ok) {
                throw new Error("Failed to load chat context.");
            }

            const body = safeParseJson(await response.text());
            state.defaultUserId = body && typeof body.defaultUserId === "string" ? body.defaultUserId : null;
            if (!normalizeUserId(state.userId)) {
                state.userId = state.defaultUserId;
            }
        } catch (error) {
            if (!normalizeUserId(state.userId)) {
                state.userId = null;
            }
        }

        persistUserIdOverride();
    }

    async function clearChat() {
        commitUserId();

        const currentSessionId = state.sessionId;
        if (currentSessionId) {
            try {
                const clearUrl = new URL("./api/chat/session/" + encodeURIComponent(currentSessionId), window.location.href);
                const currentUserId = activeUserId();
                if (currentUserId) {
                    clearUrl.searchParams.set("userId", currentUserId);
                }
                await fetch(clearUrl, {
                    method: "DELETE"
                });
            } catch (error) {
                // Ignore session clear failures so the local UI can still reset.
            }
        }

        state.messages = [];
        state.sessionId = createSessionId();
        persistSessionId(state.sessionId);
        renderIdentity();
        renderMessages();
        setStatus("Chat cleared");
        elements.questionInput.focus();
    }

    function appendMessage(message) {
        state.messages.push(message);
        renderMessages();
    }

    function renderMessages() {
        elements.messages.replaceChildren();

        if (state.messages.length === 0) {
            elements.messages.appendChild(buildEmptyState());
            return;
        }

        for (const message of state.messages) {
            elements.messages.appendChild(buildMessage(message));
        }

        if (state.loading) {
            elements.messages.appendChild(buildTypingIndicator());
        }

        scrollMessagesToBottom();
    }

    function renderIdentity() {
        elements.userIdInput.value = state.userId || state.defaultUserId || "";
        elements.userIdInput.placeholder = state.defaultUserId || "Username";
        elements.sessionIdValue.textContent = state.sessionId || "Unavailable";
    }

    function commitUserId() {
        const normalized = normalizeUserId(elements.userIdInput.value);
        state.userId = normalized || state.defaultUserId || null;
        persistUserIdOverride();
        renderIdentity();
    }

    function persistUserIdOverride() {
        const normalizedUserId = normalizeUserId(state.userId);
        const normalizedDefault = normalizeUserId(state.defaultUserId);

        try {
            if (!normalizedUserId || normalizedUserId === normalizedDefault) {
                window.localStorage.removeItem(userStorageKey);
                return;
            }

            window.localStorage.setItem(userStorageKey, normalizedUserId);
        } catch (error) {
            // Ignore storage access failures.
        }
    }

    function buildEmptyState() {
        return elements.emptyStateTemplate.content.firstElementChild.cloneNode(true);
    }

    function buildMessage(message) {
        const article = document.createElement("article");
        article.className = ["message", "message--" + message.role, message.variant ? "message--" + message.variant : ""]
            .filter(Boolean)
            .join(" ");

        const header = document.createElement("div");
        header.className = "message__header";

        const role = document.createElement("span");
        role.className = "message__role";
        role.textContent = message.role === "user" ? "You" : "Agent";

        const meta = document.createElement("div");
        meta.className = "message__meta";

        if (message.fromSemanticCache || message.responseTimeMs != null) {
            const badges = document.createElement("div");
            badges.className = "message__badges";

            if (message.fromSemanticCache) {
                const cacheBadge = document.createElement("span");
                cacheBadge.className = "badge badge--cache";
                cacheBadge.textContent = "From cache";
                badges.appendChild(cacheBadge);
            }

            if (message.responseTimeMs != null) {
                const durationBadge = document.createElement("span");
                durationBadge.className = "badge badge--timing";
                durationBadge.textContent = formatDuration(message.responseTimeMs);
                badges.appendChild(durationBadge);
            }

            meta.appendChild(badges);
        }

        const timestamp = document.createElement("span");
        timestamp.className = "message__timestamp";
        timestamp.textContent = formatTimestamp(message.timestamp);

        meta.appendChild(timestamp);
        header.append(role, meta);
        article.appendChild(header);

        const content = document.createElement("div");
        content.className = "message__content";
        const paragraph = document.createElement("p");
        paragraph.textContent = message.content;
        content.appendChild(paragraph);
        article.appendChild(content);

        const supplements = buildSupplementPanels(message);
        if (supplements) {
            article.appendChild(supplements);
        }

        return article;
    }

    function buildSupplementPanels(message) {
        const panels = [];
        const memories = Array.isArray(message.memories) ? message.memories : [];
        const hasTriggeredAgentMetadata = Array.isArray(message.triggeredAgents);
        const triggeredAgents = hasTriggeredAgentMetadata ? message.triggeredAgents : [];

        if (memories.length > 0) {
            panels.push(buildDisclosurePanel("Retrieved memories", memories, function (memory) {
                const item = document.createElement("li");
                item.textContent = memory;
                return item;
            }));
        }

        if (message.role === "assistant" && hasTriggeredAgentMetadata) {
            panels.push(buildDisclosurePanel("Triggered agents", triggeredAgents, function (agentName) {
                const item = document.createElement("li");
                item.textContent = formatAgentLabel(agentName);
                return item;
            }, "No sub-agents triggered."));
        }

        if (panels.length === 0) {
            return null;
        }

        const container = document.createElement("div");
        container.className = "message__supplements";
        panels.forEach(function (panel) {
            container.appendChild(panel);
        });
        return container;
    }

    function buildDisclosurePanel(title, items, renderItem, emptyText) {
        const wrapper = document.createElement("details");
        wrapper.className = "message__disclosure";

        const summary = document.createElement("summary");
        summary.className = "message__disclosure-summary";

        const label = document.createElement("span");
        label.className = "message__disclosure-label";
        label.textContent = title;
        summary.appendChild(label);

        const count = document.createElement("span");
        count.className = "message__disclosure-count";
        count.textContent = String(items.length);
        summary.appendChild(count);

        wrapper.appendChild(summary);

        if (items.length === 0) {
            const empty = document.createElement("p");
            empty.className = "message__disclosure-empty";
            empty.textContent = emptyText || "No items.";
            wrapper.appendChild(empty);
            return wrapper;
        }

        const list = document.createElement("ul");
        list.className = "message__disclosure-list";
        for (const itemValue of items) {
            list.appendChild(renderItem(itemValue));
        }
        wrapper.appendChild(list);

        return wrapper;
    }

    function buildTypingIndicator() {
        const article = document.createElement("article");
        article.className = "message message--assistant";

        const header = document.createElement("div");
        header.className = "message__header";

        const role = document.createElement("span");
        role.className = "message__role";
        role.textContent = "Agent";

        const timestamp = document.createElement("span");
        timestamp.className = "message__timestamp";
        timestamp.textContent = "Working";

        header.append(role, timestamp);
        article.appendChild(header);

        const content = document.createElement("div");
        content.className = "message__content";

        const dots = document.createElement("div");
        dots.className = "typing-indicator";
        dots.innerHTML = "<span></span><span></span><span></span>";
        content.appendChild(dots);
        article.appendChild(content);

        return article;
    }

    function setLoading(isLoading) {
        state.loading = isLoading;
        elements.sendButton.disabled = isLoading;
        elements.clearButton.disabled = isLoading;
        elements.questionInput.disabled = isLoading;
        elements.userIdInput.disabled = isLoading;
        elements.sendButton.textContent = isLoading ? "Sending..." : "Send";

        if (isLoading) {
            setStatus("Analyzing");
        }

        renderMessages();
    }

    function setStatus(label, variant) {
        elements.statusPill.textContent = label;
        elements.statusPill.classList.toggle("is-warning", variant === "warning");
        elements.statusPill.classList.toggle("is-error", variant === "error");
    }

    function autoResizeTextarea() {
        elements.questionInput.style.height = "auto";
        elements.questionInput.style.height = Math.min(elements.questionInput.scrollHeight, 224) + "px";
    }

    function scrollMessagesToBottom() {
        window.requestAnimationFrame(() => {
            elements.messages.scrollTop = elements.messages.scrollHeight;
        });
    }

    function extractErrorMessage(body, rawBody, status) {
        if (body && typeof body === "object") {
            if (typeof body.detail === "string" && body.detail.trim()) {
                return body.detail.trim();
            }
            if (typeof body.message === "string" && body.message.trim()) {
                return body.message.trim();
            }
            if (typeof body.title === "string" && body.title.trim()) {
                return body.title.trim();
            }
        }

        if (rawBody && rawBody.trim()) {
            return rawBody.trim();
        }

        return "Request failed with HTTP " + status + ".";
    }

    function formatTimestamp(value) {
        const parsed = new Date(value);
        if (Number.isNaN(parsed.getTime())) {
            return "";
        }

        return new Intl.DateTimeFormat(undefined, {
            hour: "numeric",
            minute: "2-digit"
        }).format(parsed);
    }

    function safeParseJson(rawBody) {
        if (!rawBody) {
            return null;
        }

        try {
            return JSON.parse(rawBody);
        } catch (error) {
            return null;
        }
    }

    function formatDuration(durationMs) {
        if (durationMs < 1000) {
            return durationMs + " ms";
        }

        return (durationMs / 1000).toFixed(durationMs >= 10_000 ? 0 : 1) + " s";
    }

    function hydrateSessionId() {
        try {
            const savedSessionId = window.localStorage.getItem(sessionStorageKey);
            if (savedSessionId) {
                return savedSessionId;
            }
        } catch (error) {
            // Ignore storage access failures.
        }

        const sessionId = createSessionId();
        persistSessionId(sessionId);
        return sessionId;
    }

    function hydrateUserIdOverride() {
        try {
            const savedUserId = window.localStorage.getItem(userStorageKey);
            return normalizeUserId(savedUserId);
        } catch (error) {
            return null;
        }
    }

    function persistSessionId(sessionId) {
        try {
            window.localStorage.setItem(sessionStorageKey, sessionId);
        } catch (error) {
            // Ignore storage access failures.
        }
    }

    function createSessionId() {
        if (window.crypto && typeof window.crypto.randomUUID === "function") {
            return window.crypto.randomUUID();
        }

        return "session-" + Date.now();
    }

    function formatAgentLabel(agentName) {
        return String(agentName || "")
            .toLowerCase()
            .split("_")
            .map(function (segment) {
                return segment ? segment.charAt(0).toUpperCase() + segment.slice(1) : segment;
            })
            .join(" ");
    }

    function normalizeUserId(userId) {
        return userId && userId.trim() ? userId.trim() : null;
    }

    function activeUserId() {
        return normalizeUserId(state.userId) || normalizeUserId(state.defaultUserId) || null;
    }
}());
