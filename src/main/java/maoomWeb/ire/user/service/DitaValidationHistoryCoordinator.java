package maoomWeb.ire.user.service;

import java.nio.file.Path;

import org.springframework.stereotype.Service;

/** 검사 이력의 시작·성공·실패·취소 상태 전환을 한곳에서 관리한다. */
@Service
public class DitaValidationHistoryCoordinator {
    private final SchematronHistoryService historyService;

    public DitaValidationHistoryCoordinator(SchematronHistoryService historyService) {
        this.historyService = historyService;
    }

    Long start(String userId, Path root, Path map, Path schema, String schemaHash,
            String scope, String selectedNodeIds, String summary, String requestedInputPath,
            String executionMode) {
        return historyService.start(userId, root.toString(), map.toString(),
                schema.getFileName().toString(), schemaHash, scope, selectedNodeIds, summary,
                requestedInputPath, executionMode);
    }

    void success(Long historyId, int fileCount, int issueCount, Path report, Path snapshot) {
        historyService.success(historyId, fileCount, issueCount, report, snapshot);
    }

    void cancel(Long historyId, String message) {
        historyService.cancel(historyId, message);
    }

    void fail(Long historyId, Exception exception) {
        historyService.fail(historyId, exception);
    }
}
