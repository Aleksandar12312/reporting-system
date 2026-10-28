package com.example.monitor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.monitor.api.ComponentCatalog.ComponentDefinition;
import com.example.monitor.api.ComponentCatalog.EventSourceRow;
import com.example.monitor.api.IssueEngine.Counts;
import com.example.monitor.api.IssueEngine.LastOk;
import com.example.monitor.api.IssueEngine.UnitWindow;

/**
 * When something is wrong with a component (default SCANNER = SHARE, RECYCLER = PER_DAY), what "marked OK"
 * changes, and how the event names of ranking.components are resolved.
 */
class IssueEngineTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 27, 10, 0);

    private final IssueEngine engine = new IssueEngine(RankingProperties.defaults(), 14);

    /** Units 1 and 2 are normal (8% rejected, 0.1 recycler faults per day), unit 3 is the one we look at. */
    private static List<UnitWindow> units() {
        return List.of(window(1), window(2), window(3));
    }

    private static UnitWindow window(long id) {
        return new UnitWindow(id, "Pro", NOW.minusDays(90), NOW, 90.0);
    }

    /** Scanner: work = notes read, problems = rejected. Recycler: only problems (faults). */
    private static Map<String, List<Counts>> counts(Counts scanner3, Counts recycler3) {
        return Map.of(
                "SCANNER", List.of(new Counts(1, 600, 48, 600, 48), new Counts(2, 600, 48, 600, 48), scanner3),
                "RECYCLER", List.of(new Counts(1, 0, 9, 0, 9), new Counts(2, 0, 9, 0, 9), recycler3));
    }

    private static ComponentIssue issue(Map<Long, List<ComponentIssue>> result, long unitId, String component) {
        return result.get(unitId).stream().filter(i -> i.component().equals(component)).findFirst().orElseThrow();
    }

    @Test
    void aComponentIsWrongWhenItIsFarWorseThanItsModel() {
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                counts(new Counts(3, 600, 180, 600, 180),     // 30% rejected
                       new Counts(3, 0, 45, 0, 45)),          // 0.5 faults per day
                List.of());

        ComponentIssue scanner = issue(result, 3, "SCANNER");
        assertThat(scanner.status()).isEqualTo(IssueStatus.ACTIVE);
        assertThat(scanner.metric()).isEqualTo(IssueMetric.SHARE);
        assertThat(scanner.value()).isEqualTo(0.3);
        assertThat(scanner.modelMedian()).isEqualTo(0.08);
        assertThat(issue(result, 3, "RECYCLER").status()).isEqualTo(IssueStatus.ACTIVE);
        // the order of ranking.components
        assertThat(result.get(3L)).extracting(ComponentIssue::component).containsExactly("SCANNER", "RECYCLER");

        assertThat(issue(result, 1, "SCANNER").status()).isEqualTo(IssueStatus.OK);
        assertThat(issue(result, 1, "RECYCLER").status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void tooLittleWorkIsNotEnoughToJudge() {
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                counts(new Counts(3, 20, 10, 20, 10),         // 50% rejected, but only 20 notes read
                       new Counts(3, 0, 9, 0, 9)),
                List.of());

        ComponentIssue scanner = issue(result, 3, "SCANNER");
        assertThat(scanner.enoughData()).isFalse();
        assertThat(scanner.status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void dataFromBeforeMarkedOkDoesNotCount() {
        LocalDateTime okAt = NOW.minusDays(2);
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                // the whole window is bad, but since the day after "OK" only 14 notes were read
                counts(new Counts(3, 600, 180, 14, 4), new Counts(3, 0, 45, 0, 5)),
                List.of(new LastOk(3, "SCANNER", okAt, "Marko", "scanner cleaned"),
                        new LastOk(3, "RECYCLER", okAt, "Marko", null)));

        ComponentIssue scanner = issue(result, 3, "SCANNER");
        assertThat(scanner.status()).isEqualTo(IssueStatus.OK);
        assertThat(scanner.enoughData()).isFalse();
        assertThat(scanner.countingSince()).isEqualTo(okAt.toLocalDate().plusDays(1).atStartOfDay());
        assertThat(scanner.lastOkBy()).isEqualTo("Marko");
        // about 1.4 days since the day after "OK": less than min-days of RECYCLER
        assertThat(issue(result, 3, "RECYCLER").status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void aProblemBackSoonAfterMarkedOkIsRecurring() {
        Counts badAgain = new Counts(3, 600, 180, 70, 21);   // 30% rejected since "OK" too
        Counts recyclerOk = new Counts(3, 0, 9, 0, 9);

        Map<Long, List<ComponentIssue>> soon = engine.evaluate(units(), counts(badAgain, recyclerOk),
                List.of(new LastOk(3, "SCANNER", NOW.minusDays(10), "Marko", null)));
        assertThat(issue(soon, 3, "SCANNER").status()).isEqualTo(IssueStatus.RECURRING);

        Map<Long, List<ComponentIssue>> later = engine.evaluate(units(), counts(badAgain, recyclerOk),
                List.of(new LastOk(3, "SCANNER", NOW.minusDays(30), "Marko", null)));
        assertThat(issue(later, 3, "SCANNER").status()).isEqualTo(IssueStatus.ACTIVE);
    }

    @Test
    void aPerDayComponentWithoutProblemsListGetsTheFaultsOfItsPart() {
        List<EventSourceRow> eventSources = List.of(
                new EventSourceRow("READ_NOTE", "OPERATION", "SCANNER"),
                new EventSourceRow("NOTE_REJECTED", "OPERATION", "SCANNER"),
                new EventSourceRow("JAMMED", "FAULT", "RECYCLER"),
                new EventSourceRow("TIME_OUT", "FAULT", "RECYCLER"),
                new EventSourceRow("DISPENSED", "OPERATION", "RECYCLER"),   // not a fault: not a problem
                new EventSourceRow("COIN_MECH_ERROR", "FAULT", null));      // no part: in no component

        List<ComponentDefinition> resolved =
                ComponentCatalog.resolve(RankingProperties.defaultComponents(), eventSources);

        assertThat(resolved).extracting(ComponentDefinition::id).containsExactly("SCANNER", "RECYCLER");
        assertThat(resolved.get(0).problems()).containsExactly("NOTE_REJECTED");
        assertThat(resolved.get(1).problems()).containsExactly("JAMMED", "TIME_OUT");

        // a misspelled event name is an error, not a silent 0
        List<RankingProperties.ComponentRule> wrong = List.of(new RankingProperties.ComponentRule(
                "SCANNER", IssueMetric.SHARE, List.of("READ_NOTES"), List.of("NOTE_REJECTED"), 30, 3, 5, 2.0));
        assertThatThrownBy(() -> ComponentCatalog.resolve(wrong, eventSources))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("READ_NOTES");
    }
}
