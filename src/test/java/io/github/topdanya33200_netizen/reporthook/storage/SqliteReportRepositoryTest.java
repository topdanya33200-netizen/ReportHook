package io.github.topdanya33200_netizen.reporthook.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.topdanya33200_netizen.reporthook.TestReports;
import io.github.topdanya33200_netizen.reporthook.model.ChatLine;
import io.github.topdanya33200_netizen.reporthook.model.Report;
import io.github.topdanya33200_netizen.reporthook.model.ReportStatus;
import io.github.topdanya33200_netizen.reporthook.model.TransitionResult;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteReportRepositoryTest {

    @TempDir
    Path folder;

    private SqliteReportRepository repository;

    @BeforeEach
    void open() throws Exception {
        repository = new SqliteReportRepository(folder.resolve("data").resolve("reports.db"));
        repository.open();
    }

    @AfterEach
    void close() {
        repository.close();
    }

    @Test
    void insertsAndReadsBack() throws Exception {
        Report saved = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW));
        Report found = repository.find(saved.id()).orElseThrow();
        assertEquals(saved, found);
        assertEquals(ReportStatus.OPEN, found.status());
        assertEquals("Steve", found.targetName());
        assertEquals(120.7, found.location().x(), 1e-9);
        assertEquals(List.of(new ChatLine(TestReports.NOW.minusSeconds(5), "ez")), found.chatContext());
        assertTrue(repository.find(saved.id() + 1).isEmpty());
    }

    @Test
    void survivesReopening() throws Exception {
        long id = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW)).id();
        repository.close();
        repository.open();
        assertTrue(repository.find(id).isPresent());
    }

    @Test
    void listsNewestFirstWithPaging() throws Exception {
        for (int i = 0; i < 5; i++) {
            repository.insert(TestReports.draft(UUID.randomUUID(), TestReports.STEVE, TestReports.NOW.plusSeconds(i)));
        }
        List<Report> firstPage = repository.list(ReportStatus.active(), 2, 0);
        List<Report> lastPage = repository.list(ReportStatus.active(), 2, 4);
        assertEquals(2, firstPage.size());
        assertTrue(firstPage.get(0).createdAt().isAfter(firstPage.get(1).createdAt()));
        assertEquals(1, lastPage.size());
        assertEquals(5, repository.count(ReportStatus.active()));
        assertEquals(0, repository.count(ReportStatus.closed()));
    }

    @Test
    void detectsDuplicateActiveReports() throws Exception {
        Report report = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW));
        assertTrue(repository.hasActiveReport(TestReports.ALEX, TestReports.STEVE));
        assertFalse(repository.hasActiveReport(TestReports.STEVE, TestReports.ALEX));
        repository.transition(report.id(), ReportStatus.RESOLVED, TestReports.MOD, "Mod", null, TestReports.NOW);
        assertFalse(repository.hasActiveReport(TestReports.ALEX, TestReports.STEVE));
    }

    @Test
    void walksThroughTheLifecycle() throws Exception {
        long id = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW)).id();

        TransitionResult claimed = repository.transition(id, ReportStatus.CLAIMED, TestReports.MOD, "Mod", null, TestReports.NOW);
        Report afterClaim = assertInstanceOf(TransitionResult.Changed.class, claimed).report();
        assertEquals(ReportStatus.CLAIMED, afterClaim.status());
        assertEquals("Mod", afterClaim.handlerName());

        TransitionResult claimedAgain = repository.transition(id, ReportStatus.CLAIMED, UUID.randomUUID(), "Other", null, TestReports.NOW);
        assertEquals("Mod", assertInstanceOf(TransitionResult.Rejected.class, claimedAgain).current().handlerName());

        TransitionResult resolved = repository.transition(id, ReportStatus.RESOLVED, TestReports.MOD, "Mod", "Banned for 7 days", TestReports.NOW);
        TransitionResult.Changed change = assertInstanceOf(TransitionResult.Changed.class, resolved);
        assertEquals(ReportStatus.CLAIMED, change.previous());
        assertEquals("Banned for 7 days", change.report().note());

        TransitionResult reopened = repository.transition(id, ReportStatus.OPEN, TestReports.MOD, "Mod", null, TestReports.NOW);
        Report afterReopen = assertInstanceOf(TransitionResult.Changed.class, reopened).report();
        assertNull(afterReopen.handlerName());
        assertNull(afterReopen.note());

        assertInstanceOf(TransitionResult.NotFound.class,
                repository.transition(id + 100, ReportStatus.CLAIMED, TestReports.MOD, "Mod", null, TestReports.NOW));
    }

    @Test
    void countsByStatus() throws Exception {
        long first = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW)).id();
        repository.insert(TestReports.draft(TestReports.STEVE, TestReports.ALEX, TestReports.NOW));
        repository.transition(first, ReportStatus.DISMISSED, TestReports.MOD, "Mod", null, TestReports.NOW);
        Map<ReportStatus, Integer> counts = repository.countByStatus();
        assertEquals(1, counts.get(ReportStatus.OPEN));
        assertEquals(1, counts.get(ReportStatus.DISMISSED));
        assertNull(counts.get(ReportStatus.CLAIMED));
    }

    @Test
    void remembersTheDiscordMessage() throws Exception {
        long id = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW)).id();
        repository.setDiscordMessage(id, "1234567890", "9876543210");
        Report report = repository.find(id).orElseThrow();
        assertEquals("1234567890", report.discordMessageId());
        assertEquals("9876543210", report.discordChannelId());
    }

    @Test
    void historyIsPerTarget() throws Exception {
        repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW));
        repository.insert(TestReports.draft(TestReports.MOD, TestReports.STEVE, TestReports.NOW.plusSeconds(1)));
        repository.insert(TestReports.draft(TestReports.STEVE, TestReports.ALEX, TestReports.NOW));
        List<Report> history = repository.findByTarget(TestReports.STEVE, 10);
        assertEquals(2, history.size());
        assertEquals("Mod", history.get(0).reporterName());
    }

    @Test
    void purgesOnlyOldClosedReports() throws Exception {
        long oldClosed = repository.insert(TestReports.draft(TestReports.ALEX, TestReports.STEVE, TestReports.NOW)).id();
        long open = repository.insert(TestReports.draft(TestReports.MOD, TestReports.STEVE, TestReports.NOW)).id();
        Instant longAgo = TestReports.NOW.minus(Duration.ofDays(60));
        repository.transition(oldClosed, ReportStatus.RESOLVED, TestReports.MOD, "Mod", null, longAgo);

        int removed = repository.purgeClosedBefore(TestReports.NOW.minus(Duration.ofDays(30)));
        assertEquals(1, removed);
        assertTrue(repository.find(oldClosed).isEmpty());
        assertTrue(repository.find(open).isPresent());
    }

    @Test
    void damagedChatLogsDoNotBreakReports() {
        assertTrue(SqliteReportRepository.decodeChat("{not json").isEmpty());
        assertNull(SqliteReportRepository.encodeChat(List.of()));
    }
}
