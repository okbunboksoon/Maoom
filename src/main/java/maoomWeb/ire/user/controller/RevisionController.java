package maoomWeb.ire.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import maoomWeb.ire.user.dto.RevisionOptionDto;
import maoomWeb.ire.user.dto.RevisionRunRequest;
import maoomWeb.ire.user.dto.RevisionRunResult;
import maoomWeb.ire.user.service.RevisionPipelineService;

@RestController
@RequestMapping("/api/revision")
public class RevisionController {

    private final RevisionPipelineService revisionPipelineService;

    public RevisionController(
            RevisionPipelineService revisionPipelineService) {
        this.revisionPipelineService = revisionPipelineService;
    }

    @GetMapping("/options")
    public List<RevisionOptionDto> getOptions() {
        return revisionPipelineService.getOptions();
    }

    @PostMapping("/run")
    public RevisionRunResult run(
            @RequestBody RevisionRunRequest request) {
        return revisionPipelineService.run(request);
    }
}
