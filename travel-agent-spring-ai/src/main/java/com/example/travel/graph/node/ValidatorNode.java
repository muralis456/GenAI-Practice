package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.agent.SemanticValidatorService;
import com.example.travel.agent.ValidatorAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ValidatorNode implements NodeAction<TravelState> {

    private final ValidatorAgentService validatorAgentService;
    private final SemanticValidatorService semanticValidatorService;

    public ValidatorNode(ValidatorAgentService validatorAgentService,
                          SemanticValidatorService semanticValidatorService) {
        this.validatorAgentService = validatorAgentService;
        this.semanticValidatorService = semanticValidatorService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        List<String> errors = new ArrayList<>(validatorAgentService.validate(state));
        List<String> semantic = semanticValidatorService.review(state);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.VALIDATION_ERRORS, errors);
        updates.put(TravelState.SEMANTIC_NOTES, semantic);
        String detail = errors.isEmpty() ? "valid" : String.join("; ", errors);
        if (!semantic.isEmpty()) {
            detail = detail + " | semantic: " + String.join("; ", semantic);
        }
        updates.putAll(TravelState.trace(TravelGraphNodes.VALIDATOR,
                errors.isEmpty() && semantic.isEmpty() ? "ok" : "warn", detail));
        return updates;
    }
}
