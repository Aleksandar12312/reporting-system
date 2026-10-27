package com.example.monitor.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.monitor.api.IssueEngine.LastOk;
import com.example.monitor.api.IssueEngine.RecyclerCounts;
import com.example.monitor.api.IssueEngine.ScannerCounts;
import com.example.monitor.api.IssueEngine.UnitWindow;

/** When something is wrong with the scanner or the recycler, and what "marked OK" changes. */
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

    private static List<ScannerCounts> scanner(ScannerCounts third) {
        return List.of(new ScannerCounts(1, 600, 48, 600, 48), new ScannerCounts(2, 600, 48, 600, 48), third);
    }

    private static List<RecyclerCounts> recycler(RecyclerCounts third) {
        return List.of(new RecyclerCounts(1, 9, 9), new RecyclerCounts(2, 9, 9), third);
    }

    private static ComponentIssue issue(Map<Long, List<ComponentIssue>> result, long unitId, IssueComponent c) {
        return result.get(unitId).stream().filter(i -> i.component() == c).findFirst().orElseThrow();
    }

    @Test
    void aComponentIsWrongWhenItIsFarWorseThanItsModel() {
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                scanner(new ScannerCounts(3, 600, 180, 600, 180)),   // 30% rejected
                recycler(new RecyclerCounts(3, 45, 45)),              // 0.5 faults per day
                List.of());

        ComponentIssue scanner = issue(result, 3, IssueComponent.SCANNER);
        assertThat(scanner.status()).isEqualTo(IssueStatus.ACTIVE);
        assertThat(scanner.value()).isEqualTo(0.3);
        assertThat(scanner.modelMedian()).isEqualTo(0.08);
        assertThat(issue(result, 3, IssueComponent.RECYCLER).status()).isEqualTo(IssueStatus.ACTIVE);

        assertThat(issue(result, 1, IssueComponent.SCANNER).status()).isEqualTo(IssueStatus.OK);
        assertThat(issue(result, 1, IssueComponent.RECYCLER).status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void tooFewNotesIsNotEnoughToJudge() {
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                scanner(new ScannerCounts(3, 20, 10, 20, 10)),        // 50% rejected, but only 20 notes read
                recycler(new RecyclerCounts(3, 9, 9)), List.of());

        ComponentIssue scanner = issue(result, 3, IssueComponent.SCANNER);
        assertThat(scanner.enoughData()).isFalse();
        assertThat(scanner.status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void dataFromBeforeMarkedOkDoesNotCount() {
        LocalDateTime okAt = NOW.minusDays(2);
        Map<Long, List<ComponentIssue>> result = engine.evaluate(units(),
                // the whole window is bad, but since the day after "OK" only 14 notes were read
                scanner(new ScannerCounts(3, 600, 180, 14, 4)),
                recycler(new RecyclerCounts(3, 45, 5)),
                List.of(new LastOk(3, IssueComponent.SCANNER, okAt, "Marko", "scanner cleaned"),
                        new LastOk(3, IssueComponent.RECYCLER, okAt, "Marko", null)));

        ComponentIssue scanner = issue(result, 3, IssueComponent.SCANNER);
        assertThat(scanner.status()).isEqualTo(IssueStatus.OK);
        assertThat(scanner.enoughData()).isFalse();
        assertThat(scanner.countingSince()).isEqualTo(okAt.toLocalDate().plusDays(1).atStartOfDay());
        assertThat(scanner.lastOkBy()).isEqualTo("Marko");
        // about 1.4 days since the day after "OK": less than ranking.recycler.min-days
        assertThat(issue(result, 3, IssueComponent.RECYCLER).status()).isEqualTo(IssueStatus.OK);
    }

    @Test
    void aProblemBackSoonAfterMarkedOkIsRecurring() {
        ScannerCounts badAgain = new ScannerCounts(3, 600, 180, 70, 21);   // 30% rejected since "OK" too

        Map<Long, List<ComponentIssue>> soon = engine.evaluate(units(), scanner(badAgain),
                recycler(new RecyclerCounts(3, 9, 9)),
                List.of(new LastOk(3, IssueComponent.SCANNER, NOW.minusDays(10), "Marko", null)));
        assertThat(issue(soon, 3, IssueComponent.SCANNER).status()).isEqualTo(IssueStatus.RECURRING);

        Map<Long, List<ComponentIssue>> later = engine.evaluate(units(), scanner(badAgain),
                recycler(new RecyclerCounts(3, 9, 9)),
                List.of(new LastOk(3, IssueComponent.SCANNER, NOW.minusDays(30), "Marko", null)));
        assertThat(issue(later, 3, IssueComponent.SCANNER).status()).isEqualTo(IssueStatus.ACTIVE);
    }
}
