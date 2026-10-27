package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DitaValidationJobServiceTest {
    @Test
    void returnsImmediatelyAndPublishesTheBackgroundResult() throws Exception {
        DitaValidationWorkflowService workflow = mock(DitaValidationWorkflowService.class);
        DitaValidationProgressService progress = new DitaValidationProgressService();
        Authentication authentication = mock(Authentication.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.getUserId(authentication)).thenReturn("user-a");
        when(currentUser.getUserName(authentication)).thenReturn("A사용자");
        RunResponse completed = new RunResponse(17L, "SUCCESS", 4, 9,
                "/api/dita-validation/reports/17");
        when(workflow.run(any(RunRequest.class), eq(authentication))).thenReturn(completed);
        DitaValidationJobService jobs = new DitaValidationJobService(workflow, progress, currentUser, 1, 2, 60);
        RunRequest request = new RunRequest("project", "root.ditamap", "DOCS", "ALL",
                Set.of(), true, "async-test-job");

        JobAcceptedResponse accepted = jobs.submit(request, authentication);
        JobStatusResponse status = jobs.get(accepted.jobId(), authentication);
        for (int i = 0; i < 100 && !"SUCCESS".equals(status.status()); i++) {
            Thread.sleep(10);
            status = jobs.get(accepted.jobId(), authentication);
        }

        assertAll(
                () -> assertEquals("async-test-job", accepted.jobId()),
                () -> assertEquals("SUCCESS", jobs.get(accepted.jobId(), authentication).status()),
                () -> assertEquals(17L, jobs.get(accepted.jobId(), authentication).historyId()),
                () -> assertEquals(9, jobs.get(accepted.jobId(), authentication).issueCount()));
        jobs.shutdown();
    }

    @Test
    void rejectsAnotherUsersJobAndDuplicateActiveJob() throws Exception {
        DitaValidationWorkflowService workflow = mock(DitaValidationWorkflowService.class);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(workflow.run(any(), any())).thenAnswer(invocation -> {
            release.await();
            return new RunResponse(1L, "SUCCESS", 1, 0, "/report/1");
        });
        CurrentUserService users = mock(CurrentUserService.class);
        Authentication owner = mock(Authentication.class), other = mock(Authentication.class);
        when(users.getUserId(owner)).thenReturn("owner");
        when(users.getUserName(owner)).thenReturn("소유자");
        when(users.getUserId(other)).thenReturn("other");
        DitaValidationJobService jobs = new DitaValidationJobService(workflow,
                new DitaValidationProgressService(), users, 1, 2, 60);
        RunRequest request = new RunRequest("p", "m", "DOCS", "ALL", Set.of(), true, "owned-job");
        jobs.submit(request, owner);
        assertThrows(IllegalStateException.class, () -> jobs.submit(
                new RunRequest("p", "m", "DOCS", "ALL", Set.of(), true, "second-job"), owner));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> jobs.get("owned-job", other));
        release.countDown();
        jobs.shutdown();
    }
}
