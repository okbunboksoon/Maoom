package maoomWeb.ire.user.dto;

import org.springframework.http.MediaType;

/** 댓글 내보내기 서비스가 컨트롤러에 넘기는 다운로드 응답 데이터. */
public record CommentExportResponse(
        String fileName,
        byte[] content,
        MediaType mediaType) {
}
