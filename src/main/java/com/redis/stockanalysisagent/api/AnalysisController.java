package com.redis.stockanalysisagent.api;

import com.redis.stockanalysisagent.agent.AgentOrchestrationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class AnalysisController {

    private final AgentOrchestrationService agentOrchestrationService;

    public AnalysisController(AgentOrchestrationService agentOrchestrationService) {
        this.agentOrchestrationService = agentOrchestrationService;
    }

    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.ok(agentOrchestrationService.processRequest(request));
    }
}
