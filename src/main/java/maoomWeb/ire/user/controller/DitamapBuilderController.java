package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import maoomWeb.ire.user.service.DitamapBuilderService;

/** DITAMAP Builder 화면에서 사용하는 API를 담당한다. */
@RestController
public class DitamapBuilderController {

    private final DitamapBuilderService ditamapBuilderService;

    public DitamapBuilderController(
            DitamapBuilderService ditamapBuilderService) {
        this.ditamapBuilderService = ditamapBuilderService;
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

    @PostMapping("/api/ditamap-builder/legal-targets")
    public List<DitamapLegalTarget> readLegalTargets() {
        return ditamapBuilderService.readLegalTargets();
    }

    @PostMapping("/api/ditamap-builder/attributes")
    public DitamapAttributeUpdateResponse updateAttributes(
            @RequestBody DitamapAttributeUpdateRequest request) {
        return ditamapBuilderService.updateAttributes(request);
    }

    @PostMapping("/api/ditamap-builder/legal-hash")
    public DitamapTreeResponse createLegalHash(
            @RequestBody DitamapAttributeUpdateRequest request) {
        return ditamapBuilderService.createLegalHash(request);
    }

    @PostMapping("/api/ditamap-builder/legal")
    public DitamapLegalSaveResponse saveLegalDitamap(
            @RequestBody DitamapLegalSaveRequest request) {
        return ditamapBuilderService.saveLegalDitamap(request);
    }

    /*
     * 오른쪽 법규 DITAMAP 영역의 폴더열기 버튼에서 DITA 경로 폴더를 열 때 사용한다.
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
