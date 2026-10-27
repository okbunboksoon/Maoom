package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Schematron 웹 API에서 사용하는 요청·응답 형식을 한곳에 모은다. */
public final class DitaValidationDtos {
    private DitaValidationDtos() { }

    public record PathRequest(String inputPath) { }
    public record MapRequest(String mapPath) { }
    public record MapItem(String name, String path) { }
    public record SchematronItem(String fileName, long size, String modifiedAt,
                                 boolean valid, String validationMessage) { }
    public record SchematronChoice(String type, String label, String fileName, String version,
                                   boolean available, String validationMessage) { }
    public record TreeNode(String id, String title, String href, List<TreeNode> children) { }
    public record MapTree(String mapPath, List<TreeNode> roots) { }
    public record RunRequest(String inputPath, String mapPath, String schematronType, String scope,
                             Set<String> selectedNodeIds, boolean validateMap, String progressToken) {
        // 독립 테스트와 기존 호출부가 진행 토큰 없이도 동일한 검사 API를 사용할 수 있게 유지한다.
        public RunRequest(String inputPath, String mapPath, String schematronType, String scope,
                          Set<String> selectedNodeIds, boolean validateMap) {
            this(inputPath, mapPath, schematronType, scope, selectedNodeIds, validateMap, null);
        }
    }
    public record RunResponse(Long historyId, String status, int validatedFileCount,
                              int issueCount, String reportUrl) { }
    public record JobAcceptedResponse(String jobId, String status) { }
    public record JobStatusResponse(String jobId, String status, Long historyId,
                                    int validatedFileCount, int issueCount,
                                    String reportUrl, String message) { }
    /** 관리자 운영 화면에 노출할 비동기 작업의 소유자와 실행 상태다. */
    public record JobAdminItem(String jobId, String status, String ownerUserId, String ownerName,
                               LocalDateTime createdAt, LocalDateTime updatedAt, Long historyId,
                               int validatedFileCount, int issueCount, String message) { }
    public record ProgressResponse(String status, int completed, int total, String currentFile,
                                   LocalDateTime updatedAt, String message) { }
    /** 결과 HTML의 항목별 확인 상태를 서버에 저장할 때 사용하는 요청이다. */
    public record ReportStateRequest(String issueId, boolean checked) { }
    public record ReportStateBatchRequest(List<String> issueIds, boolean checked) { }
    public record ReportMemoRequest(String issueId, String memo) { }
    /** 다른 사용자가 보고서를 열어도 동일하게 보이는 항목별 확인 상태다. */
    public record ReportStateItem(String issueId, boolean checked, LocalDateTime updatedAt,
                                  String confirmerName, String memo) { }
}
