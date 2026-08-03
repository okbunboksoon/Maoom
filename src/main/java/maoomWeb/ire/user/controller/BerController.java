package maoomWeb.ire.user.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.BerApplyRequest;
import maoomWeb.ire.user.dto.BerApplyResult;
import maoomWeb.ire.user.service.BerApplyService;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;

/**
 * BER 화면의 AJAX 요청을 서비스 계층으로 넘기는 REST 컨트롤러.
 *
 * <p>화면 위치는 {@code templates/user/ber/ber.html}이고,
 * 유저 메인 {@code userMain.html}의 {@code openBerPopup()}이
 * {@code /pdf/ber} 팝업을 열어 이 기능으로 진입한다.</p>
 *
 * <p>화면은 {@code ditaPath} 문자열만 보낸다. 실제 경로 검증,
 * revision-tool 복사, BAT 실행, 결과 폴더 이동은 {@link BerApplyService}에서 처리한다.
 * 관리자 BER DB 화면에서 수정한 as-is/to-be 데이터는 실행 직전에 XML로 생성되어
 * 배치 리소스와 함께 사용된다.</p>
 *
 * <p>수정 시 주의: BAT/CMD 파일은 PowerShell로 직접 편집하지 않는다.
 * BER DB 구조를 바꾸면 관리자 BER DB 화면, BerAsisTobeXmlService,
 * 배치 XSL을 함께 확인해야 한다.</p>
 */
@RestController
public class BerController {

    private final BerApplyService berApplyService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public BerController(
            BerApplyService berApplyService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.berApplyService = berApplyService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    /**
     * BER 반영 버튼 클릭 시 호출되는 API.
     *
     * <p>성공하면 결과 temp/topics 경로를 JSON으로 반환한다.
     * 실행 이력은 ProjectExecutionLogService에 BER 작업으로 남겨 관리자 실행 로그에서 확인한다.</p>
     */
    @PostMapping("/api/ber/apply")
    public BerApplyResult apply(
            @RequestBody BerApplyRequest request,
            Authentication authentication) {

        String inputPath = request == null ? null : request.ditaPath();
        Long logId = projectExecutionLogService.start(
                "BER",
                "BER 반영 실행",
                currentUserService.getUserId(authentication),
                inputPath,
                "BER 반영 배치를 실행합니다.");

        try{
            BerApplyResult result = berApplyService.apply(inputPath);
            projectExecutionLogService.success(
                    logId,
                    result.topicsPath(),
                    "BER 반영 완료");
            return result;
        }catch(IllegalArgumentException exception){
            projectExecutionLogService.fail(logId, exception);
            throw exception;
        }
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
