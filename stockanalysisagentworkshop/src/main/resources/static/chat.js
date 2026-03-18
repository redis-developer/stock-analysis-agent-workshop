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
                tokenUsage: normalizeTokenUsage(response.tokenUsage),
                executionSteps: Array.isArray(response.executionSteps) ? response.executionSteps : [],
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

        const tokenUsage = resolveTokenUsage(message);
        if (message.fromSemanticCache || message.responseTimeMs != null || tokenUsage) {
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

            if (tokenUsage) {
                const tokenBadge = document.createElement("span");
                tokenBadge.className = "badge badge--tokens";
                tokenBadge.textContent = formatTokenBadge(tokenUsage);
                badges.appendChild(tokenBadge);
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
        const hasExecutionMetadata = Array.isArray(message.executionSteps);
        const executionSteps = hasExecutionMetadata ? message.executionSteps : [];

        if (memories.length > 0) {
            panels.push(buildDisclosurePanel("Retrieved memories", memories, function (memory) {
                const item = document.createElement("li");
                item.textContent = memory;
                return item;
            }));
        }

        if (message.role === "assistant" && hasExecutionMetadata) {
            panels.push(buildDisclosurePanel("Execution breakdown", executionSteps, function (step) {
                const item = document.createElement("li");
                item.className = "message__disclosure-item";

                const row = document.createElement("div");
                row.className = "message__disclosure-item-row";

                const heading = document.createElement("div");
                heading.className = "message__disclosure-item-heading";

                const label = document.createElement("span");
                label.className = "message__disclosure-item-label";
                label.textContent = resolveStepLabel(step);
                heading.appendChild(label);

                const kind = resolveStepKind(step);
                if (kind) {
                    const kindBadge = document.createElement("span");
                    kindBadge.className = "message__step-kind message__step-kind--" + kind;
                    kindBadge.textContent = formatStepKind(kind);
                    heading.appendChild(kindBadge);
                }

                row.appendChild(heading);

                const stepTokenUsage = resolveTokenUsage(step);
                if (stepTokenUsage) {
                    const tokenBadge = document.createElement("span");
                    tokenBadge.className = "badge badge--tokens badge--timing-inline";
                    tokenBadge.textContent = formatTokenBadge(stepTokenUsage);
                    row.appendChild(tokenBadge);
                }

                const durationMs = resolveStepDuration(step);
                if (durationMs != null) {
                    const timingBadge = document.createElement("span");
                    timingBadge.className = "badge badge--timing badge--timing-inline";
                    timingBadge.textContent = formatDuration(durationMs);
                    row.appendChild(timingBadge);
                }

                item.appendChild(row);

                const summary = resolveStepSummary(step);
                if (summary || stepTokenUsage) {
                    const details = document.createElement("details");
                    details.className = "message__subdisclosure";

                    const summaryToggle = document.createElement("summary");
                    summaryToggle.className = "message__subdisclosure-summary";
                    summaryToggle.textContent = "Details";
                    details.appendChild(summaryToggle);

                    const body = document.createElement("div");
                    body.className = "message__subdisclosure-body";
                    if (stepTokenUsage) {
                        const tokenBreakdown = document.createElement("p");
                        tokenBreakdown.className = "message__token-breakdown";
                        tokenBreakdown.textContent = formatTokenBreakdown(stepTokenUsage);
                        body.appendChild(tokenBreakdown);
                    }
                    if (summary) {
                        renderMarkdownContent(body, summary);
                    }
                    details.appendChild(body);

                    item.appendChild(details);
                }

                return item;
            }, "No execution steps recorded.", "message__disclosure-list--steps"));
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

    function formatTokenBadge(tokenUsage) {
        const totalTokens = resolveTokenCount(tokenUsage && tokenUsage.totalTokens);
        if (totalTokens != null) {
            return totalTokens + " tok";
        }

        const promptTokens = resolveTokenCount(tokenUsage && tokenUsage.promptTokens);
        const completionTokens = resolveTokenCount(tokenUsage && tokenUsage.completionTokens);
        const fallbackTotal = [promptTokens, completionTokens].filter(function (value) {
            return value != null;
        }).reduce(function (sum, value) {
            return sum + value;
        }, 0);

        return fallbackTotal + " tok";
    }

    function formatTokenBreakdown(tokenUsage) {
        const parts = [];
        const promptTokens = resolveTokenCount(tokenUsage && tokenUsage.promptTokens);
        const completionTokens = resolveTokenCount(tokenUsage && tokenUsage.completionTokens);
        const totalTokens = resolveTokenCount(tokenUsage && tokenUsage.totalTokens);

        if (promptTokens != null) {
            parts.push("Prompt: " + promptTokens);
        }

        if (completionTokens != null) {
            parts.push("Completion: " + completionTokens);
        }

        if (totalTokens != null) {
            parts.push("Total: " + totalTokens);
        }

        return "Token usage: " + parts.join(" • ");
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

    function resolveStepLabel(step) {
        if (step && typeof step === "object" && typeof step.label === "string" && step.label.trim()) {
            return step.label.trim();
        }

        return formatAgentLabel(resolveStepId(step));
    }

    function resolveStepId(step) {
        if (step && typeof step === "object" && typeof step.id === "string") {
            return step.id;
        }

        return step;
    }

    function resolveStepKind(step) {
        if (step && typeof step === "object" && typeof step.kind === "string" && step.kind.trim()) {
            return step.kind.trim().toLowerCase();
        }

        return null;
    }

    function resolveStepDuration(step) {
        if (step && typeof step === "object" && Number.isFinite(step.durationMs)) {
            return step.durationMs;
        }

        return null;
    }

    function resolveStepSummary(step) {
        if (step && typeof step === "object" && typeof step.summary === "string" && step.summary.trim()) {
            return step.summary.trim();
        }

        return null;
    }

    function resolveTokenUsage(value) {
        if (!value || typeof value !== "object") {
            return null;
        }

        const tokenSource = value.tokenUsage && typeof value.tokenUsage === "object"
            ? value.tokenUsage
            : value;

        const promptTokens = resolveTokenCount(tokenSource.promptTokens);
        const completionTokens = resolveTokenCount(tokenSource.completionTokens);
        const totalTokens = resolveTokenCount(tokenSource.totalTokens);
        if (promptTokens == null && completionTokens == null && totalTokens == null) {
            return null;
        }

        return {
            promptTokens: promptTokens,
            completionTokens: completionTokens,
            totalTokens: totalTokens
        };
    }

    function normalizeTokenUsage(value) {
        return resolveTokenUsage(value);
    }

    function resolveTokenCount(value) {
        return Number.isFinite(value) ? value : null;
    }

    function formatStepKind(kind) {
        return kind === "agent" ? "Agent" : "System";
    }

    function normalizeUserId(userId) {
        return userId && userId.trim() ? userId.trim() : null;
    }

    function activeUserId() {
        return normalizeUserId(state.userId) || normalizeUserId(state.defaultUserId) || null;
    }
}());
