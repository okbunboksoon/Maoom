package maoomWeb.ire.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import static maoomWeb.ire.user.dto.DitaValidationDtos.*;
import maoomWeb.ire.user.service.DitaValidationJobService;

import maoomWeb.ire.user.dto.SchematronHistoryDto;
import maoomWeb.ire.user.service.SchematronHistoryService;

/** DB 관리의 Schematron 전용 이력 표에 실행 목록을 제공한다. */
@RestController
public class AdminSchematronHistoryController {
    private final SchematronHistoryService historyService;
    private final DitaValidationJobService jobService;

    public AdminSchematronHistoryController(SchematronHistoryService historyService,
            DitaValidationJobService jobService) {
        this.historyService = historyService;
        this.jobService = jobService;
    }

    @GetMapping("/admin/schematron-history")
    public List<SchematronHistoryDto> history() {
        // 기존 프로젝트 실행 로그와 분리된 Schematron 전용 테이블을 조회한다.
        return historyService.findAll();
    }

    @GetMapping("/admin/schematron-jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<JobAdminItem> jobs(Authentication authentication) {
        return jobService.findAllForAdministrator(authentication);
    }

    @PostMapping("/admin/schematron-jobs/{jobId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public JobStatusResponse cancel(@PathVariable String jobId, Authentication authentication) {
        return jobService.cancelAsAdministrator(jobId, authentication);
    }
}
