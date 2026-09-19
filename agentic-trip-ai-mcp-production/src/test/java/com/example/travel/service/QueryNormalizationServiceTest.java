package com.example.travel.service;

import com.example.travel.dto.TravelRequest;
import com.example.travel.entity.AirportLocation;
import com.example.travel.repository.AirportLocationRepository;
import com.example.travel.support.JsonSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QueryNormalizationServiceTest {

    @Test
    void usesLlmToResolveTypoWithoutHardcodedAlias() {
        AirportLocation hyderabad = airport("Hyderabad", "HYD", "India");
        AirportLocationRepository repository = mock(AirportLocationRepository.class);
        when(repository.findAll()).thenReturn(List.of(hyderabad));

        RoutedLlm llm = mock(RoutedLlm.class);
        JsonSupport json = mock(JsonSupport.class);
        when(llm.complete(any(), any(), any())).thenReturn(
                """
                {"normalizedPrompt":"any flights available from Hyderabad for today","corrections":[{"original":"hyderabd","corrected":"Hyderabad","type":"CITY","confidence":0.99}],"entities":[{"value":"Hyderabad","code":"HYD","country":"India","type":"CITY","confidence":0.99}]}
                """);
        doReturn(java.util.Optional.empty()).when(json).read(anyString(), any(Class.class));

        QueryNormalizationService service = new QueryNormalizationService(repository, llm, json);
        TravelRequest request = new TravelRequest();
        request.setPrompt("any flights available from hyderabd for today");

        QueryNormalizationService.NormalizationResult result = service.normalize(request);
        assertEquals(request.getPrompt(), result.normalizedPrompt());
        verify(llm).complete(any(), any(), any());
    }

    private static AirportLocation airport(String city, String iata, String country) {
        AirportLocation a = new AirportLocation();
        a.setCity(city);
        a.setIataCode(iata);
        a.setCountry(country);
        return a;
    }
}
