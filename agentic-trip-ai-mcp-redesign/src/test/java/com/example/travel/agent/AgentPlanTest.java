package com.example.travel.agent;

import com.example.travel.model.AgentPlan;
import com.example.travel.model.AgentTask;
import com.example.travel.model.IntentPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentPlanTest {
    @Test
    void fullTripCreatesCompleteDashboardTaskContract() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());

        assertEquals("TRIP_PLANNING", plan.getGoal());
        assertFalse(plan.isSelectiveExecution());
        assertEquals(List.of("flight", "hotel", "research", "weather"), plan.executablePreSupervisorAgents());
        assertTrue(plan.has("flights"));
        assertTrue(plan.has("hotels"));
        assertTrue(plan.has("research"));
        assertTrue(plan.has("weather"));
        assertTrue(plan.has("budget"));
        assertTrue(plan.has("itinerary"));
        assertTrue(plan.ready("flights"));
        assertFalse(plan.ready("itinerary"));
    }

    @Test
    void specialistRequestRemainsSelectiveByContentButExecutableOnInitialPass() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.hotelsOnly());
        assertFalse(plan.isSelectiveExecution());
        assertEquals(List.of("hotel"), plan.executablePreSupervisorAgents());
        assertTrue(plan.has("hotels"));
        assertFalse(plan.has("flights"));
        assertFalse(plan.has("weather"));
    }

    @Test
    void selectiveRecoveryCannotFallBackToStaleTasks() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());
        plan.selectForExecution(List.of("hotels"));
        assertTrue(plan.isSelectiveExecution());
        assertEquals(List.of("hotel"), plan.executablePreSupervisorAgents());
        assertEquals(AgentTask.Status.SKIPPED, plan.task("flights").getStatus());
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

    @Test
    void selectiveRecoveryPreservesDownstreamTasks() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());
        plan.selectForExecution(List.of("hotels"));

        assertTrue(plan.shouldExecute("hotels"));
        assertFalse(plan.shouldExecute("flights"));
        assertTrue(plan.shouldExecute("budget"));
        assertTrue(plan.shouldExecute("itinerary"));
    }

    @Test
    void initialPlanExecutesPendingTasksFromCanonicalPlan() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());
        assertTrue(plan.shouldExecute("flights"));
        assertTrue(plan.shouldExecute("hotels"));
        assertTrue(plan.shouldExecute("weather"));
        assertTrue(plan.shouldExecute("budget"));
        assertTrue(plan.shouldExecute("itinerary"));
    }

    @Test
    void recoveryDependencyClosureIsTransitive() {
        AgentPlan plan = AgentPlan.fromIntent(IntentPlan.fullTrip());
        plan.selectForExecution(List.of("flights"));

        assertTrue(plan.shouldExecute("flights"));
        assertTrue(plan.shouldExecute("budget"));
        assertTrue(plan.shouldExecute("itinerary"));
        assertFalse(plan.shouldExecute("hotels"));
        assertFalse(plan.shouldExecute("weather"));
    }

}
