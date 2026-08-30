package com.example.travel.agent;

import com.example.travel.graph.TravelState;
import com.example.travel.model.WeatherForecast;
import com.example.travel.tool.WeatherTool;
import org.springframework.stereotype.Service;

@Service
public class WeatherAgentService {

    private final WeatherTool weatherTool;

    public WeatherAgentService(WeatherTool weatherTool) {
        this.weatherTool = weatherTool;
    }

    public WeatherForecast forecast(TravelState state) {
        if (!state.needsWeather() || TravelState.isBlank(state.destination())) {
            return new WeatherForecast("", "", false);
        }
        return weatherTool.forecast(state.destination(), state.departureDate(), state.returnDate());
    }
}
