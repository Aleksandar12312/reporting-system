package com.example.monitor.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.monitor.api.RankingEngine.PartCount;
import com.example.monitor.api.RankingEngine.ScoreRow;

/** The tier and scope rules, the part of the REST API that holds real logic. */
class RankingEngineTest {

    private final RankingEngine engine = new RankingEngine(RankingProperties.defaults());

    /** A unit of model "Pro" with the given faults and rate (median of the model = 1.0 unless given). */
    private static ScoreRow row(long id, int faults, double rate, Double median) {
        return new ScoreRow(id, "SN-" + id, "Pro", faults, 90.0, rate, median);
    }

    private UnitScore scoreOf(ScoreRow row, PartCount... parts) {
        return engine.score(List.of(row), List.of(parts)).get(0);
    }

    @Test
    void fewerThanMinFaultsIsOkWhateverTheRatio() {
        assertThat(scoreOf(row(1, 2, 50.0, 1.0)).tier()).isEqualTo(Tier.OK);
    }

    @Test
    void enoughFaultsButNoMedianIsNoBaseline() {
        UnitScore s = scoreOf(row(1, 5, 1.0, null));
        assertThat(s.tier()).isEqualTo(Tier.NO_BASELINE);
        assertThat(s.ratioToModel()).isNull();
    }

    @Test
    void ratioDecidesOkWatchAndCritical() {
        assertThat(scoreOf(row(1, 10, 1.9, 1.0)).tier()).isEqualTo(Tier.OK);
        assertThat(scoreOf(row(2, 10, 2.0, 1.0)).tier()).isEqualTo(Tier.WATCH);
        assertThat(scoreOf(row(3, 10, 4.0, 1.0)).tier()).isEqualTo(Tier.CRITICAL);
    }

    @Test
    void scopeIsPartWhenOneComponentCausesHalfOfTheFaults() {
        UnitScore part = scoreOf(row(1, 10, 4.0, 1.0), new PartCount(1, "RC", 8), new PartCount(1, "PU", 2));
        assertThat(part.scope()).isEqualTo(ProblemScope.PART);
        assertThat(part.topPart()).isEqualTo("RC");
        assertThat(part.topPartShare()).isEqualTo(0.8);

        UnitScore general = scoreOf(row(2, 10, 4.0, 1.0),
                new PartCount(2, "RC", 4), new PartCount(2, "PU", 3), new PartCount(2, "CM", 3));
        assertThat(general.scope()).isEqualTo(ProblemScope.GENERAL);
    }

    @Test
    void scopeIsNullForOkUnitsAndForTooFewFaults() {
        assertThat(scoreOf(row(1, 10, 1.0, 1.0), new PartCount(1, "RC", 10)).scope()).isNull();
        assertThat(scoreOf(row(2, 5, 4.0, 1.0), new PartCount(2, "RC", 5)).scope()).isNull();
    }

    @Test
    void highestRatioComesFirstAndUnitsWithoutRatioLast() {
        List<UnitScore> scores = engine.score(
                List.of(row(1, 3, 1.0, 1.0), row(2, 3, 3.0, 1.0), row(3, 3, 1.0, null)), List.of());
        assertThat(scores).extracting(UnitScore::unitId).containsExactly(2L, 1L, 3L);
    }

    private static ComponentIssue issue(String component, IssueStatus status) {
        return new ComponentIssue(component, status, IssueMetric.SHARE, 0.3, 0.08, 3.75, 100, true,
                null, null, null, null);
    }

    @Test
    void activeComponentMessageMakesOkAndNoBaselineUnitsWatchButNeverLowersTier() {
        List<ComponentIssue> scannerWrong = List.of(issue("SCANNER", IssueStatus.ACTIVE),
                issue("RECYCLER", IssueStatus.OK));
        assertThat(scoreOf(row(1, 0, 0.0, 1.0)).withIssues(scannerWrong).tier()).isEqualTo(Tier.WATCH);
        assertThat(scoreOf(row(2, 5, 1.0, null)).withIssues(scannerWrong).tier()).isEqualTo(Tier.WATCH);
        assertThat(scoreOf(row(3, 10, 4.0, 1.0)).withIssues(scannerWrong).tier()).isEqualTo(Tier.CRITICAL);

        List<ComponentIssue> recurring = List.of(issue("SCANNER", IssueStatus.OK),
                issue("RECYCLER", IssueStatus.RECURRING));
        UnitScore raised = scoreOf(row(4, 3, 1.0, 1.0)).withIssues(recurring);
        assertThat(raised.tier()).isEqualTo(Tier.WATCH);
        assertThat(raised.ratioToModel()).isEqualTo(1.0);
        assertThat(raised.isFlagged()).isTrue();
    }

    @Test
    void tierFollowsTheFaultRateWhenEveryComponentIsOk() {
        List<ComponentIssue> allOk = List.of(issue("SCANNER", IssueStatus.OK),
                issue("RECYCLER", IssueStatus.OK));
        assertThat(scoreOf(row(1, 2, 1.0, 1.0)).withIssues(allOk).tier()).isEqualTo(Tier.OK);
        assertThat(scoreOf(row(2, 5, 1.0, null)).withIssues(allOk).tier()).isEqualTo(Tier.NO_BASELINE);
        assertThat(scoreOf(row(3, 5, 1.0, 1.0)).withIssues(List.of()).tier()).isEqualTo(Tier.OK);
    }
}
