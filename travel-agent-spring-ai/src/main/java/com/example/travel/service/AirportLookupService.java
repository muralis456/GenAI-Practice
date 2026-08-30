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
            return repository.findFirstByCountryIgnoreCase(parts[1].trim());
        }

        return repository.findFirstByCityIgnoreCase(normalizeCity(value));
    }

    private String normalizeCity(String city) {
        return switch (city.toLowerCase(Locale.ROOT)) {
            case "bangalore" -> "Bengaluru";
            case "bombay" -> "Mumbai";
            case "calcutta" -> "Kolkata";
            case "madras" -> "Chennai";
            case "new delhi" -> "Delhi";
            case "sao paulo", "são paulo" -> "Sao Paulo";
            default -> city;
        };
    }
}
