```bash
docker pull europe-west4-docker.pkg.dev/personal-rdlts/session-runners/1_spring_ai_fundamentals@sha256:28b7b496cd413a2b99640f72d08ce0f5b2ad662c215fb1580bccd44f27d9da51

docker pull europe-west4-docker.pkg.dev/personal-rdlts/session-runners/2_building_multi_agents@sha256:18673b0d39ea540e929394dff2bdb188a57da7393b538b2c24ea15c346284e41

docker pull europe-west4-docker.pkg.dev/personal-rdlts/session-runners/3_multi_agent_patterns@sha256:18ef9e1dafa1d036a691ab94fa18930ba8a045dd6c871bfeb914088ebde4a7e6

docker pull europe-west4-docker.pkg.dev/personal-rdlts/session-runners/4_advanced_multi_agent_patterns@sha256:59642a3a9ff010587fef843547bae78e766fb223ca7ea99bd7cbf0a57f6cfbc8

```


```bash
docker run --rm \
--name spring-ai-fundamentals \
--platform linux/amd64 \
-p 8084:8080 \
-e WORKSHOP_SESSION_ID=local-spring-ai-1 \
-e WORKSHOP_REDIS_KEY_PREFIX=session:local-spring-ai-1: \
-e OPENAI_API_KEY \
europe-west4-docker.pkg.dev/personal-rdlts/session-runners/1_spring_ai_fundamentals@sha256:28b7b496cd413a2b99640f72d08ce0f5b2ad662c215fb1580bccd44f27d9da51
```

```bash
docker run --rm \
  --name spring-ai-building-multi-agents \
  --platform linux/amd64 \
  -p 8085:8080 \
  -e WORKSHOP_SESSION_ID=local-spring-ai-2 \
  -e WORKSHOP_REDIS_KEY_PREFIX=session:local-spring-ai-2: \
  -e OPENAI_API_KEY \
  -e TWELVE_DATA_API_KEY \
  -e TAVILY_API_KEY \
  europe-west4-docker.pkg.dev/personal-rdlts/session-runners/2_building_multi_agents@sha256:18673b0d39ea540e929394dff2bdb188a57da7393b538b2c24ea15c346284e41

```

```bash
docker run --rm \
  --name spring-ai-multi-agent-patterns \
  --platform linux/amd64 \
  -p 8086:8080 \
  -e WORKSHOP_SESSION_ID=local-spring-ai-3 \
  -e WORKSHOP_REDIS_KEY_PREFIX=session:local-spring-ai-3: \
  -e OPENAI_API_KEY \
  -e TWELVE_DATA_API_KEY \
  -e TAVILY_API_KEY \
  europe-west4-docker.pkg.dev/personal-rdlts/session-runners/3_multi_agent_patterns@sha256:18ef9e1dafa1d036a691ab94fa18930ba8a045dd6c871bfeb914088ebde4a7e6

```

```bash
docker run --rm \
  --name spring-ai-advanced-multi-agent-patterns \
  --platform linux/amd64 \
  -p 8087:8080 \
  -e WORKSHOP_SESSION_ID=local-spring-ai-4 \
  -e WORKSHOP_REDIS_KEY_PREFIX=session:local-spring-ai-4: \
  -e OPENAI_API_KEY \
  -e TWELVE_DATA_API_KEY \
  -e TAVILY_API_KEY \
  europe-west4-docker.pkg.dev/personal-rdlts/session-runners/4_advanced_multi_agent_patterns@sha256:59642a3a9ff010587fef843547bae78e766fb223ca7ea99bd7cbf0a57f6cfbc8
```