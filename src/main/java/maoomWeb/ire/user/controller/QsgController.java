package maoomWeb.ire.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import maoomWeb.ire.user.dto.QsgRunRequest;
import maoomWeb.ire.user.dto.QsgRunResult;
import maoomWeb.ire.user.service.CurrentUserService;
import maoomWeb.ire.user.service.ProjectExecutionLogService;
import maoomWeb.ire.user.service.QsgApplyService;

/**
 * QSG 팝업의 실행 API.
 *
 * <p>화면 위치는 {@code templates/user/multilingual/qsgPopup.html}이고,
 * 유저 메인 {@code userMain.html}의 {@code openQsgPopup()}이
 * {@code /qsg/list} 팝업을 열어 이 기능으로 진입한다.</p>
 *
 * <p>화면은 입력 경로와 언어 코드 목록만 보낸다. 실제 경로 검증, 작업 폴더
 * 생성, classpath 리소스 복사, 언어별 배치 실행, Result_Folder 복사는
 * {@link QsgApplyService}가 담당한다.</p>
 *
 * <p>QSG 변환은 {@code src/main/resources/xsl/QSG_DB.xml}의
 * hash + lang + term 데이터를 사용한다. 이 XML은 관리자 QSG DB 화면에서
 * 조회/엑셀 업로드/내보내기할 수 있다.</p>
 *
 * <p>수정 시 주의: QSG_DB.xml 구조나 언어 코드 규칙을 바꾸면
 * QsgDbAdminService, QSG 적용 XSL, qsgPopup.html의 언어 선택 UI를 함께 확인한다.</p>
 */
@RestController
@RequestMapping("/api/qsg")
public class QsgController {

    private final QsgApplyService qsgApplyService;
    private final CurrentUserService currentUserService;
    private final ProjectExecutionLogService projectExecutionLogService;

    public QsgController(
            QsgApplyService qsgApplyService,
            CurrentUserService currentUserService,
            ProjectExecutionLogService projectExecutionLogService) {
        this.qsgApplyService = qsgApplyService;
        this.currentUserService = currentUserService;
        this.projectExecutionLogService = projectExecutionLogService;
    }

    /**
     * QSG 실행 버튼 클릭 시 호출된다.
     *
     * <p>성공하면 입력 경로의 Result_Folder를 반환한다.
     * 실행 이력은 ProjectExecutionLogService에 QSG 작업으로 남겨 관리자 실행 로그에서 확인한다.</p>
     */
    @PostMapping("/run")
    public QsgRunResult run(
            @RequestBody QsgRunRequest request,
            Authentication authentication) {

        Long logId = projectExecutionLogService.start(
                "QSG",
                "QSG 실행",
                currentUserService.getUserId(authentication),
                request == null ? null : request.inputPath(),
                "선택한 언어 QSG 배치를 실행합니다.");
        QsgRunResult result = qsgApplyService.run(request);

        if(result.success()){
            projectExecutionLogService.success(
                    logId,
                    result.outputPath(),
                    "QSG 변환 완료, "
                    + result.languageCodes().size()
                    + "개 언어");
        }else{
            projectExecutionLogService.fail(
                    logId,
                    new IllegalStateException(
                            result.logs().isEmpty()
                            ? "QSG 실행 실패"
                            : result.logs().get(result.logs().size() - 1)));
        }

        return result;
    }
}
