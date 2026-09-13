package com.example.travel.graph.node;

import com.example.travel.agent.WeatherAgentService;
import com.example.travel.graph.GraphExecutionLogger;
import com.example.travel.graph.NodeFailureSupport;
import com.example.travel.support.ToolFailureClassifier;
import com.example.travel.graph.TravelGraphNodes;
import com.example.travel.graph.TravelState;
import com.example.travel.model.ProvenanceEvent;
import com.example.travel.model.WeatherForecast;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WeatherNode implements NodeAction<TravelState> {

    private final WeatherAgentService weatherAgentService;

    public WeatherNode(WeatherAgentService weatherAgentService) {
        this.weatherAgentService = weatherAgentService;
    }

    @Override
    public Map<String, Object> apply(TravelState state) {
        if (!state.shouldExecuteTask("weather")) {
            Map<String, Object> skip = new LinkedHashMap<>();
            skip.putAll(TravelState.trace(TravelGraphNodes.WEATHER, "skip", "not requested"));
            return skip;
        }
        try {
            WeatherForecast weather = weatherAgentService.forecast(state);
            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put(TravelState.WEATHER, weather);
            updates.putAll(TravelState.trace(TravelGraphNodes.WEATHER,
                    weather != null && weather.isRainLikely() ? "warn" : "ok",
                    weather == null ? "no forecast" : weather.toDisplay()));
            boolean hasWeatherData = weather != null && (weather.hasCurrentDetails() || !weather.getDays().isEmpty());
            String status = hasWeatherData ? (weather.isRainLikely() ? "warn" : "ok") : "error";
            GraphExecutionLogger.specialistResult(TravelGraphNodes.WEATHER, state,
                    status,
                    weather == null ? "no forecast" : weather.toDisplay());
            updates.putAll(TravelState.provenance(new ProvenanceEvent(
                    "weather", "OpenWeather Free Weather APIs", "https://openweathermap.org/price", 0,
                    state.destination())));
            return updates;
        } catch (Exception ex) {
            return NodeFailureSupport.record(TravelGraphNodes.WEATHER, state, ex,
                    ToolFailureClassifier.fromException(ex).isRetryable(),
                    state.nodeFailure().getNodeRetryCount());
        }
    }
}
