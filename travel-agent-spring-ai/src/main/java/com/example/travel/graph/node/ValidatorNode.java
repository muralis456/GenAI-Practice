package com.example.travel.graph.node;

import com.example.travel.agent.SemanticValidatorService;
import com.example.travel.agent.ValidatorAgentService;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.PlanQualityScore;
import com.example.travel.model.SemanticValidationResult;
import com.example.travel.service.PlanQualityScorer;
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
    private final PlanQualityScorer planQualityScorer;

    public ValidatorNode(ValidatorAgentService validatorAgentService,
                          SemanticValidatorService semanticValidatorService,
                          PlanQualityScorer planQualityScorer) {
        this.validatorAgentService = validatorAgentService;
        this.semanticValidatorService = semanticValidatorService;
        this.planQualityScorer = planQualityScorer;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        List<String> errors = new ArrayList<>(validatorAgentService.validate(state));
        SemanticValidationResult semantic = semanticValidatorService.review(state);
        List<String> semanticNotes = semanticValidatorService.issueNotes(semantic);
        PlanQualityScore quality = planQualityScorer.score(state, semantic);

        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put(TravelState.VALIDATION_ERRORS, errors);
        updates.put(TravelState.SEMANTIC_NOTES, semanticNotes);
        updates.put(TravelState.SEMANTIC_VALIDATION, semantic);
        updates.put(TravelState.PLAN_QUALITY, quality);

        String detail = "overall=" + String.format("%.2f", quality.getOverall());
        if (!errors.isEmpty()) {
            detail += " | " + String.join("; ", errors);
        }
        if (!semanticNotes.isEmpty()) {
            detail += " | semantic: " + String.join("; ", semanticNotes);
        }
        boolean pass = errors.isEmpty() && !semantic.failed() && quality.passes();
        updates.putAll(TravelState.trace(TravelGraphNodes.VALIDATOR, pass ? "ok" : "warn", detail));
        return updates;
    }
}
