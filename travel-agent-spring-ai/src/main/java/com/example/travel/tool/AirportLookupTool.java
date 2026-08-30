package com.example.travel.tool;

import com.example.travel.entity.AirportLocation;
import com.example.travel.service.AirportLookupService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AirportLookupTool {

    private final AirportLookupService airportLookupService;

    public AirportLookupTool(AirportLookupService airportLookupService) {
        this.airportLookupService = airportLookupService;
    }

    public Optional<AirportLocation> resolve(String cityOrCode) {
        return airportLookupService.findAirport(cityOrCode);
    }

    @Tool(description = "Resolve a city, country, or airport name to a 3-letter IATA code from the airport database. "
            + "Examples: Bengaluru→BLR, Japan→NRT, Mumbai→BOM. Never invent codes.")
    public String resolveIata(
            @ToolParam(description = "City, country, or airport name/code to resolve") String cityOrCode) {
        return resolve(cityOrCode).map(AirportLocation::getIataCode).orElse("");
    }
}
