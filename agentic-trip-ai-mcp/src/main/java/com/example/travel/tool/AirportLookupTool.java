package com.example.travel.tool;

import com.example.travel.entity.AirportLocation;
import com.example.travel.service.AirportLookupService;
import com.example.travel.service.McpAirportClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AirportLookupTool {

    private final AirportLookupService airportLookupService;
    private final ObjectProvider<McpAirportClient> mcpAirportClient;

    public AirportLookupTool(AirportLookupService airportLookupService,
                             ObjectProvider<McpAirportClient> mcpAirportClient) {
        this.airportLookupService = airportLookupService;
        this.mcpAirportClient = mcpAirportClient;
    }

    public Optional<AirportLocation> resolve(String cityOrCode) {
        return airportLookupService.findAirport(cityOrCode);
    }

    @Tool(description = "Resolve a city, country, or airport name to a 3-letter IATA code from the airport database. "
            + "Examples: Bengaluru→BLR, Japan→NRT, Mumbai→BOM. Never invent codes.")
    public String resolveIata(
            @ToolParam(description = "City, country, or airport name/code to resolve") String cityOrCode) {
        McpAirportClient client = mcpAirportClient.getIfAvailable();
        if (client != null) {
            String iata = client.resolve(cityOrCode);
            if (!iata.isBlank()) {
                return iata;
            }
        }
        String fromDb = resolve(cityOrCode).map(AirportLocation::getIataCode).orElse("");
        if (!fromDb.isBlank()) {
            return fromDb;
        }
        return fallbackIata(cityOrCode);
    }

    private static String fallbackIata(String cityOrCode) {
        if (cityOrCode == null || cityOrCode.isBlank()) {
            return "";
        }
        return switch (cityOrCode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "goa", "panaji", "panjim", "dabolim" -> "GOI";
            case "mopa" -> "GOX";
            case "pune" -> "PNQ";
            case "kochi", "cochin" -> "COK";
            case "jaipur" -> "JAI";
            case "ahmedabad" -> "AMD";
            default -> "";
        };
    }
}
