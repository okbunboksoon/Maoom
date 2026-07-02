package maoomWeb.ire.user.dto;

/**
 * Google Drive 항목을 화면의 폴더/PDF 트리에 전달하는 읽기 전용 DTO.
 * DriveController가 Google API 응답을 이 단순 구조로 바꿔 브라우저에 반환한다.
 */
public class DriveFileDto {

    /** Drive 파일 또는 폴더의 고유 ID. */
    private String id;
    /** 왼쪽 탐색 트리에 표시되는 이름. */
    private String name;
    /** 탐색 경로를 구성할 때 사용하는 상위 폴더 ID. */
    private String parentId;
    /** 사용자에게 보여줄 상위 폴더 이름. */
    private String parentName;
    /** 화면 분기용 값으로 folder 또는 pdf를 사용한다. */
    private String type;

    public DriveFileDto(String id, String name, String parentId, String parentName, String type) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.parentName = parentName;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getParentId() {
        return parentId;
    }

    public String getParentName() {
        return parentName;
    }

    public String getType() {
        return type;
    }
}
