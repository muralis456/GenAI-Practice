package com.example.travel.service;

import com.example.travel.entity.AirportLocation;
import com.example.travel.repository.AirportLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AirportLookupService {

    private final AirportLocationRepository repository;

    public AirportLookupService(AirportLocationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<AirportLocation> findAirport(String destination) {
        if (destination == null || destination.isBlank()) {
            return Optional.empty();
        }

        String value = destination.trim();
        if (value.matches("[A-Za-z]{3}")) {
            return repository.findByIataCodeIgnoreCase(value);
        }

        String[] parts = value.split(",");
        String city = normalizeCity(parts[0].trim().replaceFirst("(?i)\\s+city$", ""));
        Optional<AirportLocation> match = repository.findFirstByCityIgnoreCase(city);
        if (match.isPresent()) {
            return match;
        }
        if (parts.length > 1) {
            Optional<AirportLocation> byCountryPart = repository.findFirstByCountryIgnoreCase(parts[1].trim());
            if (byCountryPart.isPresent()) {
                return byCountryPart;
            }
        }

        Optional<AirportLocation> byCity = repository.findFirstByCityIgnoreCase(normalizeCity(value));
        if (byCity.isPresent()) {
            return byCity;
        }

        // "Japan" / "Thailand" style prompts: resolve via country to a major hub.
        return repository.findFirstByCountryIgnoreCase(normalizeCountry(value));
    }

    private String normalizeCity(String city) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "goa", "panaji", "panjim", "vasco", "vasco da gama" -> "Goa";
            case "kyoto" -> "Osaka";
            case "bangalore" -> "Bengaluru";
            case "bombay" -> "Mumbai";
            case "calcutta" -> "Kolkata";
            case "madras" -> "Chennai";
            case "new delhi" -> "Delhi";
            case "japan", "nippon" -> "Tokyo";
            case "thailand" -> "Bangkok";
            case "uae", "united arab emirates" -> "Dubai";
            case "uk", "united kingdom", "england" -> "London";
            case "usa", "united states", "america" -> "New York";
            case "sao paulo", "são paulo" -> "Sao Paulo";
            default -> city;
        };
    }

    private String normalizeCountry(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "japan", "nippon" -> "Japan";
            case "thailand" -> "Thailand";
            case "uae", "united arab emirates" -> "United Arab Emirates";
            case "uk", "united kingdom", "england" -> "United Kingdom";
            case "usa", "united states", "america" -> "United States";
            case "india" -> "India";
            default -> value;
        };
    }
}
