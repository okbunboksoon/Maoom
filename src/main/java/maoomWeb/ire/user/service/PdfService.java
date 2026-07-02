package maoomWeb.ire.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import maoomWeb.ire.user.dto.PdfDto;
import maoomWeb.ire.user.mapper.PdfMapper;

/**
 * Google Drive의 PDF 한 건을 MAOOM 내부의 {@code tb_pdf} 레코드와 연결한다.
 *
 * <p>Drive는 실제 파일과 폴더 구조를 보관하고, DB의 PDF 레코드는 댓글이 참조할
 * 내부 {@code pdfId}를 제공한다. 따라서 처음 여는 파일은 Drive 메타데이터를 읽어
 * DB에 한 번 등록하고, 다음부터는 등록된 레코드를 그대로 반환한다.</p>
 */
@Service
public class PdfService {

    private static final Logger log =
            LoggerFactory.getLogger(PdfService.class);

    private static final String ROOT_FOLDER_ID =
            "0ALoTVIAzU74bUk9PVA";

    private final PdfMapper pdfMapper;
    private final Drive drive;
    private final PdfAccessService pdfAccessService;

    public PdfService(
            PdfMapper pdfMapper,
            Drive drive,
            PdfAccessService pdfAccessService) {
        this.pdfMapper = pdfMapper;
        this.drive = drive;
        this.pdfAccessService = pdfAccessService;
    }

    /**
     * Drive 파일 ID에 해당하는 내부 PDF 정보를 반환한다.
     *
     * <p>1) DB에 이미 있으면 즉시 반환하고, 2) 없으면 Drive에서 파일명과
     * 폴더 경로를 읽어 tb_pdf에 저장한다. 성공한 접근은
     * {@link PdfAccessService}에 잠시 기억시켜 뒤이은 PDF.js 요청과 댓글 요청이
     * 같은 권한 조회를 반복하지 않게 한다.</p>
     */
    public PdfDto findByDriveFileId(
            String driveFileId,
            String userId){

        try {

            log.debug(
                    "Loading Drive PDF metadata: {}",
                    driveFileId);

            PdfDto pdfDto = pdfMapper.findByDriveFileId(driveFileId);

            if(pdfDto != null){
                pdfAccessService.rememberPdfAccess(
                        pdfDto.getPdfId(),
                        driveFileId,
                        userId);
                return pdfDto;
            }

            File driveFile =
                    getDriveFile(driveFileId);
            String fileName = driveFile.getName();
            String filePath = buildFolderPath(driveFile);

            pdfDto = new PdfDto();
            pdfDto.setDriveFileId(driveFileId);
            pdfDto.setFileName(fileName);
            pdfDto.setFilePath(filePath);
            pdfDto.setRegUserId(userId);

            pdfMapper.insertPdf(pdfDto);
            pdfAccessService.rememberPdfAccess(
                    pdfDto.getPdfId(),
                    driveFileId,
                    userId);

            return pdfDto;

        }catch(Exception e){

            log.warn(
                    "Failed to load Google Drive PDF metadata",
                    e);

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

        // 현재 파일에서 부모 폴더를 한 단계씩 올라가며 화면/DB에 표시할 경로를 만든다.
        // 지정된 공유 드라이브 루트에 도달하지 못하면 외부 파일로 판단해 등록하지 않는다.
        List<String> folderNames = new ArrayList<>();
        File current = driveFile;
        boolean sharedDriveRootReached = false;

        while(current.getParents() != null
                && !current.getParents().isEmpty()){

            File parent =
                    getDriveFile(current.getParents().get(0));

            if(ROOT_FOLDER_ID.equals(parent.getId())){
                sharedDriveRootReached = true;
                break;
            }

            folderNames.add(parent.getName());
            current = parent;
        }

        if(!sharedDriveRootReached){
            throw new IllegalArgumentException(
                    "허용된 공유 드라이브의 PDF가 아닙니다.");
        }

        Collections.reverse(folderNames);
        return String.join("/", folderNames);
    }

}
