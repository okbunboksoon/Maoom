package maoomWeb.ire.user.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.QsgRunRequest;
import maoomWeb.ire.user.dto.QsgRunResult;
import maoomWeb.ire.user.service.QsgApplyService;

/** QSG 화면에서 선택한 입력 경로와 언어 코드를 받는다. */
@RestController
@RequestMapping("/api/qsg")
public class QsgController {

    private final QsgApplyService qsgApplyService;

    public QsgController(QsgApplyService qsgApplyService) {
        this.qsgApplyService = qsgApplyService;
    }

    @PostMapping("/run")
    public QsgRunResult run(
            @RequestBody QsgRunRequest request) {

        return qsgApplyService.run(request);
    }
}
