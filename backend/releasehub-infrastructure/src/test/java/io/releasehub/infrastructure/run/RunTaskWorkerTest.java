package io.releasehub.infrastructure.run;

import io.releasehub.application.run.RunAppService;
import io.releasehub.application.run.RunPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 调度边界用真实有界线程池验证；存储资格和 Git 事实由 PostgreSQL/HTTP IT 验证。 */
class RunTaskWorkerTest {
    private final RunPort runs = mock(RunPort.class);
    private final RunAppService service = mock(RunAppService.class);
    private RunTaskWorker worker;

    @AfterEach
    void stopWorker() {
        if (worker != null) worker.close();
    }

    @Test
    void anUnclaimableFirstPageDoesNotStarveLaterRunsAndCursorWraps() throws Exception {
        worker = new RunTaskWorker(runs, service, 1, 2, 1);
        when(runs.findPendingTaskRunIds(null, 1)).thenReturn(List.of("blocked"));
        when(runs.findPendingTaskRunIds("blocked", 1)).thenReturn(List.of("ready"));
        when(runs.findPendingTaskRunIds("ready", 1)).thenReturn(List.of());
        var first = new CountDownLatch(1);
        var ready = new CountDownLatch(1);
        when(service.executeNextTask("blocked")).thenAnswer(call -> { first.countDown(); return false; });
        when(service.executeNextTask("ready")).thenAnswer(call -> { ready.countDown(); return true; });

        assertEquals(1, worker.scanOnce());
        assertTrue(first.await(3, TimeUnit.SECONDS));
        assertEquals(1, worker.scanOnce());
        assertTrue(ready.await(3, TimeUnit.SECONDS));
        assertEquals(1, worker.scanOnce());

        verify(runs, times(2)).findPendingTaskRunIds(null, 1);
        verify(runs).findPendingTaskRunIds("blocked", 1);
        verify(runs).findPendingTaskRunIds("ready", 1);
        verify(runs, never()).claimNextTask(anyString());
    }

    @Test
    void theSameRunCanReachTwoWorkersWithoutRunLevelSingleFlight() throws Exception {
        worker = new RunTaskWorker(runs, service, 2, 0, 1);
        when(runs.findPendingTaskRunIds(null, 1)).thenReturn(List.of("shared-run"));
        when(runs.findPendingTaskRunIds("shared-run", 1)).thenReturn(List.of());
        var bothStarted = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        when(service.executeNextTask("shared-run")).thenAnswer(call -> {
            bothStarted.countDown();
            release.await(3, TimeUnit.SECONDS);
            return true;
        });
        try {
            assertEquals(1, worker.scanOnce());
            assertEquals(1, worker.scanOnce());
            assertTrue(bothStarted.await(3, TimeUnit.SECONDS));
            verify(service, times(2)).executeNextTask("shared-run");
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectedAndCancelledCommandsNeverReachTheClaimingExecutor() throws Exception {
        worker = new RunTaskWorker(runs, service, 1, 1, 1);
        when(runs.findPendingTaskRunIds(null, 1)).thenReturn(List.of("active"));
        when(runs.findPendingTaskRunIds("active", 1)).thenReturn(List.of("queued"));
        when(runs.findPendingTaskRunIds("queued", 1)).thenReturn(List.of("rejected"));
        var entered = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(service.executeNextTask("active")).thenAnswer(call -> {
            entered.countDown();
            try { release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { finish.countDown(); }
            return true;
        });

        assertEquals(1, worker.scanOnce());
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertEquals(1, worker.scanOnce());
        assertEquals(0, worker.scanOnce());
        verify(service, never()).executeNextTask("queued");
        verify(service, never()).executeNextTask("rejected");
        worker.close();
        release.countDown();
        assertTrue(finish.await(3, TimeUnit.SECONDS));
        assertEquals(0, worker.scanOnce());
        verify(service, never()).executeNextTask("queued");
        verify(service, never()).executeNextTask("rejected");
        verify(runs, never()).claimNextTask(anyString());
    }

    @Test
    void aTaskFailureDoesNotStopLaterScansOrRetryFromMemory() throws Exception {
        worker = new RunTaskWorker(runs, service, 1, 1, 1);
        when(runs.findPendingTaskRunIds(null, 1)).thenReturn(List.of("failed-commit"));
        when(runs.findPendingTaskRunIds("failed-commit", 1)).thenReturn(List.of("other-run"));
        var failed = new CountDownLatch(1);
        var next = new CountDownLatch(1);
        when(service.executeNextTask("failed-commit")).thenAnswer(call -> {
            failed.countDown();
            throw new IllegalStateException("business commit failed");
        });
        when(service.executeNextTask("other-run")).thenAnswer(call -> { next.countDown(); return true; });

        assertEquals(1, worker.scanOnce());
        assertTrue(failed.await(3, TimeUnit.SECONDS));
        assertEquals(1, worker.scanOnce());
        assertTrue(next.await(3, TimeUnit.SECONDS));
        verify(service).executeNextTask("failed-commit");
        verify(runs, never()).claimNextTask(anyString());
    }

    @Test
    void aClosedWorkerDoesNotDiscoverOrDispatch() {
        worker = new RunTaskWorker(runs, service, 1, 1, 1);
        worker.close();
        assertEquals(0, worker.scanOnce());
        verifyNoInteractions(runs, service);
    }

    @Test
    void closingDoesNotWaitForCandidateQueryOrDispatchAfterItReturns() throws Exception {
        worker = new RunTaskWorker(runs, service, 1, 1, 1);
        var queryEntered = new CountDownLatch(1);
        var releaseQuery = new CountDownLatch(1);
        when(runs.findPendingTaskRunIds(null, 1)).thenAnswer(call -> {
            queryEntered.countDown();
            releaseQuery.await();
            return List.of("ready-after-close");
        });
        try (var callers = Executors.newFixedThreadPool(2)) {
            var scan = callers.submit(worker::scanOnce);
            try {
                assertTrue(queryEntered.await(3, TimeUnit.SECONDS));
                callers.submit(worker::close).get(2, TimeUnit.SECONDS);
            } finally {
                releaseQuery.countDown();
            }
            assertEquals(0, scan.get(3, TimeUnit.SECONDS));
        }
        verifyNoInteractions(service);
        verify(runs).findPendingTaskRunIds(null, 1);
        verify(runs, never()).claimNextTask(anyString());
    }

    @Test
    void invalidResourceBoundsAreRejectedBeforeStartingThreads() {
        assertThrows(IllegalArgumentException.class, () -> new RunTaskWorker(runs, service, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RunTaskWorker(runs, service, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RunTaskWorker(runs, service, 1, 1, 1001));
    }
}
