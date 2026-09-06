package com.example.travel.mcp.service;

import com.example.travel.mcp.dto.SearchFlightsRequest;
import com.example.travel.mcp.dto.SearchFlightsResponse;
import org.springframework.stereotype.Service;

@Service
public class FlightService {

    private final AviationStackClient aviationStackClient;

    public FlightService(AviationStackClient aviationStackClient) {
        this.aviationStackClient = aviationStackClient;
    }

    public SearchFlightsResponse search(SearchFlightsRequest request) {
        if (request == null) {
            return SearchFlightsResponse.failure("INVALID_REQUEST", "Flight search input is required.");
        }
        if (!iataCode(request.origin()) || !iataCode(request.destination())) {
            return SearchFlightsResponse.failure("INVALID_AIRPORT", "origin and destination must be three-letter IATA codes.");
        }
        if (request.normalizedPassengers() < 1 || request.normalizedPassengers() > 9) {
            return SearchFlightsResponse.failure("INVALID_PASSENGERS", "passengers must be between 1 and 9.");
        }
        return aviationStackClient.search(request);
    }

    private boolean iataCode(String value) {
        return value != null && value.trim().matches("[A-Za-z]{3}");
    }
}
