package com.example.travel.graph.node;

import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.service.ValidatorAgentService;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ValidatorNode implements NodeAction<TravelState> {

    private final ValidatorAgentService validatorAgentService;

    public ValidatorNode(ValidatorAgentService validatorAgentService) {
        this.validatorAgentService = validatorAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        List<String> errors = validatorAgentService.validate(state);
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.VALIDATION_ERRORS, errors);
        updates.putAll(TravelState.trace(TravelGraphNodes.VALIDATOR, errors.isEmpty() ? "ok" : "warn",
                errors.isEmpty() ? "valid" : String.join("; ", errors)));
        return updates;
    }
}
