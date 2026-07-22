package maoomWeb.ire.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.QsgRunRequest;
import maoomWeb.ire.user.dto.QsgRunResult;
import maoomWeb.ire.user.service.QsgApplyService;

/**
 * QSG 팝업의 실행 API.
 *
 * <p>화면은 입력 경로와 언어 코드 목록만 보낸다. 실제 경로 검증, 작업 폴더
 * 생성, classpath 리소스 복사, 언어별 배치 실행, Result_Folder 복사는
 * {@link QsgApplyService}가 담당한다.</p>
 */
@RestController
@RequestMapping("/api/qsg")
public class QsgController {

    private final QsgApplyService qsgApplyService;

    public QsgController(QsgApplyService qsgApplyService) {
        this.qsgApplyService = qsgApplyService;
    }

    @PostMapping("/run")
    /** QSG 실행 버튼 클릭 시 호출된다. 성공하면 입력 경로의 Result_Folder를 반환한다. */
    public QsgRunResult run(
            @RequestBody QsgRunRequest request) {

        return qsgApplyService.run(request);
    }
}
