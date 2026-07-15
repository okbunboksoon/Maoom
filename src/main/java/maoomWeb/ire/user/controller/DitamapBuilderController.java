package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.DitamapTreeRequest;
import maoomWeb.ire.user.dto.DitamapTreeResponse;
import maoomWeb.ire.user.dto.DitamapAttributeUpdateRequest;
import maoomWeb.ire.user.dto.DitamapAttributeUpdateResponse;
import maoomWeb.ire.user.dto.DitamapLegalSaveRequest;
import maoomWeb.ire.user.dto.DitamapLegalSaveResponse;
import maoomWeb.ire.user.dto.DitamapLegalTarget;
import maoomWeb.ire.user.dto.DitamapTopicTitleRequest;
import maoomWeb.ire.user.dto.DitamapTopicTitleResponse;
import maoomWeb.ire.user.service.DitamapBuilderService;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * DITAMAP Builder 화면에서 사용하는 API를 담당한다.
 *
 * 현재 법규 DITAMAP 생성 흐름은 서버 배치를 돌려 자동 생성하지 않는다.
 * 화면에서 기준 DITAMAP을 읽고, 사용자가 오른쪽 법규 편집 팝업에서 구조를 만든 뒤
 * /api/ditamap-builder/legal 저장 API가 최종 LM_*.ditamap 파일을 작성한다.
 */
@RestController
public class DitamapBuilderController {

    private final DitamapBuilderService ditamapBuilderService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public DitamapBuilderController(
            DitamapBuilderService ditamapBuilderService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.ditamapBuilderService = ditamapBuilderService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    @PostMapping("/api/ditamap-builder/tree")
    public DitamapTreeResponse readTree(
            @RequestBody DitamapTreeRequest request) {
        return ditamapBuilderService.readTree(request.path());
    }

    @PostMapping("/api/ditamap-builder/tree-summary")
    public DitamapTreeResponse readTreeSummary(
            @RequestBody DitamapTreeRequest request) {
        return ditamapBuilderService.readTreeSummary(request.path());
    }

    @PostMapping("/api/ditamap-builder/legal-template")
    public DitamapTreeResponse readLegalTemplate() {
        return ditamapBuilderService.readLegalTemplate();
    }

    @PostMapping("/api/ditamap-builder/legal-master")
    public DitamapTreeResponse readLegalMaster() {
        return ditamapBuilderService.readLegalMaster();
    }

    @PostMapping("/api/ditamap-builder/legal-target-files")
    public List<String> readLegalTargetFiles() {
        return ditamapBuilderService.readLegalTargetFiles();
    }

    @PostMapping("/api/ditamap-builder/dita-files")
    public List<String> readDitaFiles(
            @RequestBody DitamapTreeRequest request) {
        return ditamapBuilderService.readDitaFiles(request.path());
    }

    @PostMapping("/api/ditamap-builder/legal-targets")
    public List<DitamapLegalTarget> readLegalTargets() {
        return ditamapBuilderService.readLegalTargets();
    }

    /*
     * 파일명 편집 흐름:
     * 1. 화면에서 topicref 파일명을 더블클릭해 수정한다.
     * 2. 화면은 기존 href와 새 파일명을 이 API로 보낸다.
     * 3. 서비스가 실제 DITA 파일을 찾아 title을 읽어 응답한다.
     * 4. 화면은 응답받은 title/fileName/href만 ditamap row에 반영한다.
     *
     * 여기서는 DITA 파일 자체를 저장하거나 수정하지 않는다.
     */
    @PostMapping("/api/ditamap-builder/topic-title")
    public DitamapTopicTitleResponse readTopicTitle(
            @RequestBody DitamapTopicTitleRequest request) {
        return ditamapBuilderService.readTopicTitle(request);
    }

    @PostMapping("/api/ditamap-builder/attributes")
    public DitamapAttributeUpdateResponse updateAttributes(
            @RequestBody DitamapAttributeUpdateRequest request) {
        return ditamapBuilderService.updateAttributes(request);
    }

    @PostMapping("/api/ditamap-builder/legal")
    public DitamapLegalSaveResponse saveLegalDitamap(
            @RequestBody DitamapLegalSaveRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "DITAMAP_BUILDER",
                "법규 DITAMAP 저장",
                currentUserService.getUserId(authentication),
                request == null ? null : request.baseDitamapFile(),
                "화면에서 편집한 법규 rows를 LM DITAMAP으로 저장합니다.");

        try{
            DitamapLegalSaveResponse response =
                    ditamapBuilderService.saveLegalDitamap(request);
            projectExecutionLogService.success(
                    logId,
                    response.savedDitamapFile(),
                    response.updatedCount() + "건 저장");
            return response;
        }catch(RuntimeException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
    }

    /*
     * 오른쪽 법규 DITAMAP 영역의 폴더열기 버튼에서 DITA 경로 폴더를 열 때 사용한다.`
     * 브라우저에서 V:/, H:/ 같은 로컬/네트워크 드라이브를 직접 열 수 없으므로
     * 서버가 허용 루트 검사를 통과한 경로만 Windows 탐색기로 열어 준다.
     */
    @PostMapping("/api/ditamap-builder/open-folder")
    public ResponseEntity<String> openFolder(
            @RequestBody DitamapTreeRequest request) {
        ditamapBuilderService.openFolder(request.path());
        return ResponseEntity.ok()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body("opened");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(
            IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .contentType(new MediaType(
                        "text",
                        "plain",
                        StandardCharsets.UTF_8))
                .body(exception.getMessage());
    }
}
