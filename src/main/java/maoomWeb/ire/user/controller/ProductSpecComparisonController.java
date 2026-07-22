package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.ProductSpecComparisonRequest;
import maoomWeb.ire.user.dto.ProductSpecComparisonResult;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProductSpecComparisonService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * 제품사양서 비교 팝업의 실행 API.
 *
 * <p>화면은 서버 PC 기준 입력 경로와 Before/After 파일명, 추출 Key 옵션만 보낸다.
 * 실제 경로 검증, 작업 폴더 생성, classpath 도구 복사, BAT 실행, Result_Folder
 * 복사는 {@link ProductSpecComparisonService}가 담당한다.</p>
 */
@RestController
@RequestMapping("/api/product-spec-comparison")
public class ProductSpecComparisonController {

    private final ProductSpecComparisonService productSpecComparisonService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public ProductSpecComparisonController(
            ProductSpecComparisonService productSpecComparisonService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.productSpecComparisonService = productSpecComparisonService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    @PostMapping("/run")
    /** 비교 실행 버튼 클릭 시 호출된다. 성공하면 입력 경로의 Result_Folder 결과 경로를 반환한다. */
    public ProductSpecComparisonResult run(
            @RequestBody ProductSpecComparisonRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "PRODUCT_SPEC_COMPARISON",
                "제품사양서 비교 실행",
                currentUserService.getUserId(authentication),
                request == null ? null : request.inputPath(),
                "제품사양서 비교 배치를 실행합니다.");
        ProductSpecComparisonResult result =
                productSpecComparisonService.run(request);

        if (result.success()) {
            projectExecutionLogService.success(
                    logId,
                    result.resultPath(),
                    "제품사양서 비교 완료");
        } else {
            projectExecutionLogService.fail(
                    logId,
                    new IllegalStateException(
                            result.logs().isEmpty()
                            ? "제품사양서 비교 실패"
                            : result.logs().get(result.logs().size() - 1)));
        }

        return result;
    }

    @GetMapping("/download")
    /** 최근 실행 결과 엑셀을 내려준다. 현재 화면은 결과 경로 표시만 쓰지만, API는 유지한다. */
    public ResponseEntity<Resource> download() {
        Path resultFile = productSpecComparisonService.getResultFile();
        if (resultFile == null || !Files.isRegularFile(resultFile)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        resultFile.getFileName().toString(),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new FileSystemResource(resultFile));
    }
}
