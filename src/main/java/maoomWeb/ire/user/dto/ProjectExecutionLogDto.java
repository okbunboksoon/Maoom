package maoomWeb.ire.user.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면에서 프로젝트 실행 이력을 보여줄 때 사용하는 읽기 전용 DTO.
 */
public record ProjectExecutionLogDto(
        Long id,
        String jobType,
        String jobName,
        String status,
        String triggerUserId,
        String inputPath,
        String outputPath,
        String message,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long elapsedMs) {
}
