package com.example.travel.graph;

import com.example.travel.model.GoalEvaluation;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProductionEvaluateNode implements NodeAction<TravelState> {
    private final GoalEvaluationService evaluator;
    public ProductionEvaluateNode(GoalEvaluationService evaluator){this.evaluator=evaluator;}
    @Override public Map<String,Object> apply(TravelState state){
        GoalEvaluation e=evaluator.evaluate(state); Map<String,Object> u=new LinkedHashMap<>();
        u.put(TravelState.GOAL_EVALUATION,e);
        u.put(TravelState.SUPERVISOR_DECISION,e.getStatus().name());
        u.put(TravelState.REPLAN_NOTES,e.getReason()+" unmet="+e.getUnmetCriteria());
        u.putAll(TravelState.trace("evaluate",e.getStatus().name().toLowerCase(),e.getReason()));
        return u;
    }
}
