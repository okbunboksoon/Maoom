package maoomWeb.ire.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.SchematronHistoryDto;
import maoomWeb.ire.user.service.SchematronHistoryService;

/** DB 관리의 Schematron 전용 이력 표에 실행 목록을 제공한다. */
@RestController
public class AdminSchematronHistoryController {
    private final SchematronHistoryService historyService;

    public AdminSchematronHistoryController(SchematronHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping("/admin/schematron-history")
    public List<SchematronHistoryDto> history() {
        // 기존 프로젝트 실행 로그와 분리된 Schematron 전용 테이블을 조회한다.
        return historyService.findAll();
    }
}
