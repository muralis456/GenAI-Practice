package com.example.travel.agent;

import com.example.travel.model.AgentPlan;
import com.example.travel.model.AgentTask;
import com.example.travel.model.IntentPlan;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlanTest {
    @Test
    void fullTripCreatesCompleteDashboardTaskContract() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());

        assertEquals("TRIP_PLANNING", plan.getGoal());
        assertTrue(plan.has("flights"));
        assertTrue(plan.has("hotels"));
        assertTrue(plan.has("research"));
        assertTrue(plan.has("weather"));
        assertTrue(plan.has("budget"));
        assertTrue(plan.has("itinerary"));
        assertTrue(plan.ready("flights"));
        assertFalse(plan.ready("itinerary"));
        assertEquals(4, plan.preSupervisorTasks().size());
    }

    @Test
    void specialistRequestDoesNotAcquireUnrequestedTasks() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.hotelsOnly());
        assertTrue(plan.has("hotels"));
        assertFalse(plan.has("flights"));
        assertFalse(plan.has("weather"));
        assertEquals(1, plan.preSupervisorTasks().size());
        assertTrue(plan.ready("hotels"));
    }

    @Test
    void failedRequiredTaskIsRetryableOnlyBeforeAttemptLimit() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.weatherOnly());
        plan.markStarted("weather");
        plan.markFailed("weather", "provider timeout");
        assertTrue(plan.hasRetryableFailure(2));
        plan.markStarted("weather");
        plan.markFailed("weather", "provider timeout");
        assertFalse(plan.hasRetryableFailure(2));
    }
}
