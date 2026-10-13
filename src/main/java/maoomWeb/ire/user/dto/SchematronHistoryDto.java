package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/** DB 관리 화면에 Schematron 실행 이력 한 행을 전달한다. */
public record SchematronHistoryDto(
        Long id,
        String status,
        String triggerUserId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long elapsedMs,
        String inputSummary,
        String reportFileName,
        String errorMessage) { }
