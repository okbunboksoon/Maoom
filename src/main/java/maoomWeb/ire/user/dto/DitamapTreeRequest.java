package maoomWeb.ire.user.dto;

/** DITAMAP Builder가 허용된 작업 경로 안의 DITA 경로를 전달할 때 사용하는 요청 DTO. */
public record DitamapTreeRequest(
        String path) {
}
