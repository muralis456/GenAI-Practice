package com.example.travel.service;

import com.example.travel.dto.TravelPlanResponse;
import com.example.travel.dto.TripHistoryItem;
import com.example.travel.dto.TripPlanResult;
import com.example.travel.dto.TripHeader;
import com.example.travel.entity.TripHistory;
import com.example.travel.repository.TripHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class TripHistoryService {

    private final TripHistoryRepository repository;
    private final ObjectMapper objectMapper;

    public TripHistoryService(TripHistoryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveOrUpdate(String userId, TravelPlanResponse response) {
        if (response == null || response.getThreadId() == null || response.getThreadId().isBlank()) {
            return;
        }
        // My Trips is for actual trip-plan workflows, not one-off specialist
        // questions such as "what is the weather in Bengaluru?".
        if (!response.isTripPlanning()) {
            return;
        }
        TripPlanResult plan = response.getPlan();
        TripHeader trip = plan == null ? null : plan.getTrip();
        if (trip == null) {
            return;
        }

        TripHistory entity = repository.findByUserIdAndThreadId(userId, response.getThreadId())
                .orElseGet(TripHistory::new);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUserId(userId);
        entity.setThreadId(response.getThreadId());
        entity.setTitle(firstNonBlank(trip.getTitle(), buildTitle(trip)));
        entity.setOrigin(trip.getOrigin());
        entity.setDestination(trip.getDestination());
        entity.setDepartureDate(trip.getDepartureDate());
        entity.setReturnDate(trip.getReturnDate());
        entity.setTravelers(trip.getTravelers());
        entity.setBudgetLabel(trip.getBudgetLabel());
        entity.setStatus(firstNonBlank(response.getStatus(), trip.getStatus(), "COMPLETE"));
        entity.setAwaitingApproval(response.isAwaitingApproval() || trip.isAwaitingApproval());
        entity.setQualityScore(trip.getQualityScore());
        entity.setUpdatedAt(now);
        try {
            entity.setPlanJson(objectMapper.writeValueAsString(response));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not save trip history", ex);
        }
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<TripHistoryItem> list(String userId, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        return repository.findByUserIdOrderByUpdatedAtDesc(userId, PageRequest.of(0, size))
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public TravelPlanResponse getPlan(String userId, Long id) {
        TripHistory entity = repository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        try {
            return objectMapper.readValue(entity.getPlanJson(), TravelPlanResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load saved trip", ex);
        }
    }

    private TripHistoryItem toItem(TripHistory t) {
        return new TripHistoryItem(t.getId(), t.getThreadId(), t.getTitle(), t.getOrigin(),
                t.getDestination(), t.getDepartureDate(), t.getReturnDate(), t.getTravelers(),
                t.getBudgetLabel(), t.getStatus(), t.isAwaitingApproval(), t.getQualityScore(),
                t.getCreatedAt(), t.getUpdatedAt());
    }

    private String buildTitle(TripHeader trip) {
        String destination = firstNonBlank(trip.getDestination(), "Trip");
        return destination + " Trip";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
