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
        appendMessageContent(content, message);
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
            panels.push(buildDisclosurePanel("Triggered agents", triggeredAgents, function (agent) {
                const item = document.createElement("li");
                item.className = "message__disclosure-item";

                const label = document.createElement("span");
                label.className = "message__disclosure-item-label";
                label.textContent = formatAgentLabel(resolveAgentName(agent));
                item.appendChild(label);

                const durationMs = resolveAgentDuration(agent);
                if (durationMs != null) {
                    const timingBadge = document.createElement("span");
                    timingBadge.className = "badge badge--timing badge--timing-inline";
                    timingBadge.textContent = formatDuration(durationMs);
                    item.appendChild(timingBadge);
                }

                return item;
            }, "No sub-agents triggered.", "message__disclosure-list--agents"));
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

    function buildDisclosurePanel(title, items, renderItem, emptyText, listClassName) {
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
        if (listClassName) {
            list.classList.add(listClassName);
        }
        for (const itemValue of items) {
            list.appendChild(renderItem(itemValue));
        }
        wrapper.appendChild(list);

        return wrapper;
    }

    function appendMessageContent(container, message) {
        if (message.role === "assistant" && !message.variant) {
            renderMarkdownContent(container, message.content);
            return;
        }

        const paragraph = document.createElement("p");
        paragraph.textContent = message.content;
        container.appendChild(paragraph);
    }

    function renderMarkdownContent(container, markdown) {
        const lines = String(markdown || "").replace(/\r\n?/g, "\n").split("\n");
        let index = 0;
        let paragraphLines = [];

        function flushParagraph() {
            if (paragraphLines.length === 0) {
                return;
            }

            const paragraph = document.createElement("p");
            appendInlineMarkdown(paragraph, paragraphLines.join(" "));
            container.appendChild(paragraph);
            paragraphLines = [];
        }

        while (index < lines.length) {
            const line = lines[index];
            const trimmed = line.trim();

            if (!trimmed) {
                flushParagraph();
                index += 1;
                continue;
            }

            const headingMatch = trimmed.match(/^(#{1,6})\s+(.*)$/);
            if (headingMatch) {
                flushParagraph();
                const level = Math.min(headingMatch[1].length, 6);
                const heading = document.createElement("h" + level);
                appendInlineMarkdown(heading, headingMatch[2]);
                container.appendChild(heading);
                index += 1;
                continue;
            }

            const unorderedMatch = trimmed.match(/^[-*]\s+(.*)$/);
            const orderedMatch = trimmed.match(/^\d+\.\s+(.*)$/);
            if (unorderedMatch || orderedMatch) {
                flushParagraph();
                const list = document.createElement(orderedMatch ? "ol" : "ul");

                while (index < lines.length) {
                    const listLine = lines[index].trim();
                    const currentUnordered = listLine.match(/^[-*]\s+(.*)$/);
                    const currentOrdered = listLine.match(/^\d+\.\s+(.*)$/);
                    const currentMatch = orderedMatch ? currentOrdered : currentUnordered;

                    if (!currentMatch) {
                        break;
                    }

                    const item = document.createElement("li");
                    appendInlineMarkdown(item, currentMatch[1]);
                    list.appendChild(item);
                    index += 1;
                }

                container.appendChild(list);
                continue;
            }

            paragraphLines.push(trimmed);
            index += 1;
        }

        flushParagraph();

        if (!container.hasChildNodes()) {
            const paragraph = document.createElement("p");
            paragraph.textContent = markdown;
            container.appendChild(paragraph);
        }
    }

    function appendInlineMarkdown(parent, text) {
        const pattern = /(\*\*[^*]+\*\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
        let lastIndex = 0;
        let match;

        while ((match = pattern.exec(text)) !== null) {
            if (match.index > lastIndex) {
                parent.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
            }

            const token = match[0];
            if (token.startsWith("**") && token.endsWith("**")) {
                const strong = document.createElement("strong");
                strong.textContent = token.slice(2, -2);
                parent.appendChild(strong);
            } else if (token.startsWith("`") && token.endsWith("`")) {
                const code = document.createElement("code");
                code.textContent = token.slice(1, -1);
                parent.appendChild(code);
            } else if (token.startsWith("[")) {
                const linkMatch = token.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
                if (linkMatch) {
                    const anchor = document.createElement("a");
                    anchor.href = linkMatch[2];
                    anchor.textContent = linkMatch[1];
                    anchor.target = "_blank";
                    anchor.rel = "noreferrer noopener";
                    parent.appendChild(anchor);
                } else {
                    parent.appendChild(document.createTextNode(token));
                }
            } else {
                parent.appendChild(document.createTextNode(token));
            }

            lastIndex = pattern.lastIndex;
        }

        if (lastIndex < text.length) {
            parent.appendChild(document.createTextNode(text.slice(lastIndex)));
        }
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

    function resolveAgentName(agent) {
        if (agent && typeof agent === "object" && typeof agent.agentType === "string") {
            return agent.agentType;
        }

        return agent;
    }

    function resolveAgentDuration(agent) {
        if (agent && typeof agent === "object" && Number.isFinite(agent.durationMs)) {
            return agent.durationMs;
        }

        return null;
    }

    function normalizeUserId(userId) {
        return userId && userId.trim() ? userId.trim() : null;
    }

    function activeUserId() {
        return normalizeUserId(state.userId) || normalizeUserId(state.defaultUserId) || null;
    }
}());
