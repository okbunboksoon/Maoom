package maoomWeb.ire.user.dto;

/**
 * Google Drive 항목을 화면의 폴더/PDF 트리에 전달하는 읽기 전용 DTO.
 */
public class DriveFileDto {

    private String id;
    private String name;
    private String parentId;
    private String parentName;
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
