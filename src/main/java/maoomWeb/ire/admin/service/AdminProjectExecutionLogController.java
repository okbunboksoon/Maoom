package maoomWeb.ire.admin.service;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import maoomWeb.ire.user.dto.ProjectExecutionLogDto;

/** 관리자 화면의 프로젝트 실행 로그 조회 API. */
@Controller
public class AdminProjectExecutionLogController {

    private final AdminProjectExecutionLogService logService;

    public AdminProjectExecutionLogController(
            AdminProjectExecutionLogService logService) {
        this.logService = logService;
    }

    @GetMapping("/admin/project-logs")
    @ResponseBody
    public List<ProjectExecutionLogDto> getProjectLogs(
            @RequestParam(defaultValue = "100") int limit) {
        return logService.findRecent(limit);
    }
}
