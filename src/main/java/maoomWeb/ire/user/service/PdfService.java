package maoomWeb.ire.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import maoomWeb.ire.user.dto.PdfDto;
import maoomWeb.ire.user.mapper.PdfMapper;

@Service
/**
 * Google Drive PDF와 MAOOM 데이터베이스의 PDF 레코드를 연결한다.
 */
public class PdfService {

    private static final String ROOT_FOLDER_ID =
            "0ANjwWGzgwzlhUk9PVA";

    private final PdfMapper pdfMapper;
    private final Drive drive;

    public PdfService(
            PdfMapper pdfMapper,
            Drive drive) {
        this.pdfMapper = pdfMapper;
        this.drive = drive;
    }

    /**
     * Drive 파일 ID에 해당하는 PDF를 반환한다.
     * 최초 접근한 파일이면 댓글 저장에 사용할 내부 PDF 레코드를 생성한다.
     */
    public PdfDto findByDriveFileId(String driveFileId){

        try {

            System.out.println("driveFileId = " + driveFileId);

            PdfDto pdfDto = pdfMapper.findByDriveFileId(driveFileId);
            File driveFile =
                    getDriveFile(driveFileId);
            String fileName = driveFile.getName();
            String filePath = buildFolderPath(driveFile);

            if(pdfDto == null){

                pdfDto = new PdfDto();
                pdfDto.setDriveFileId(driveFileId);
                pdfDto.setFileName(fileName);
                pdfDto.setFilePath(filePath);
                pdfDto.setRegUserId("admin");

                pdfMapper.insertPdf(pdfDto);
            }else if(!same(fileName, pdfDto.getFileName())
                    || !same(filePath, pdfDto.getFilePath())){

                pdfMapper.updateFileInfo(
                        pdfDto.getPdfId(),
                        fileName,
                        filePath);
                pdfDto.setFileName(fileName);
                pdfDto.setFilePath(filePath);
            }

            return pdfDto;

        } catch(Exception e){

            e.printStackTrace();

            throw new IllegalStateException(
                    "Google Drive PDF 정보를 불러오지 못했습니다.",
                    e);
        }
    }

    private File getDriveFile(String fileId) throws Exception {
        return drive.files()
                .get(fileId)
                .setSupportsAllDrives(true)
                .setFields("id,name,parents")
                .execute();
    }

    private String buildFolderPath(File driveFile)
            throws Exception {

        List<String> folderNames = new ArrayList<>();
        File current = driveFile;

        while(current.getParents() != null
                && !current.getParents().isEmpty()){

            File parent =
                    getDriveFile(current.getParents().get(0));

            if(ROOT_FOLDER_ID.equals(parent.getId())){
                break;
            }

            folderNames.add(parent.getName());
            current = parent;
        }

        Collections.reverse(folderNames);
        return String.join("/", folderNames);
    }

    private boolean same(String left, String right) {
        String normalizedLeft =
                left == null ? "" : left.trim();
        String normalizedRight =
                right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

}
