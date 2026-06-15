package maoomWeb.ire.user.controller;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import maoomWeb.ire.user.dto.DriveFileDto;

@RestController
/**
 * 서비스 계정으로 공유 드라이브의 폴더와 PDF를 조회하는 REST 컨트롤러.
 * 목록 응답은 사용자와 폴더별로 잠시 캐시한다.
 */
public class DriveController {

	private static final String PDF_MIME_TYPE =
			"application/pdf";

	private static final String FOLDER_MIME_TYPE =
			"application/vnd.google-apps.folder";

	private static final Duration ITEM_CACHE_DURATION =
			Duration.ofMinutes(5);

	private final Drive drive;
	private final Map<String,CachedDriveItems> itemCache =
			new ConcurrentHashMap<>();
	private final Map<String,Object> itemCacheLocks =
			new ConcurrentHashMap<>();

    public DriveController(Drive drive) {
        this.drive = drive;
	}

    @GetMapping("/api/drive/items")
    /** 폴더와 PDF를 한 번에 조회하고 사용자/폴더별로 5분간 캐시한다. */
    public List<DriveFileDto> getItems(
            @RequestParam String folderId,
            @RequestParam(
                    defaultValue = "false") boolean refresh,
            Authentication authentication) throws Exception {

        return getCachedItems(
                folderId,
                authentication,
                refresh);
    }
    

    @GetMapping("/api/drive/files")
    @ResponseBody
    /** 지정한 Google Drive 폴더 바로 아래의 PDF만 조회한다. */
    public List<DriveFileDto> getFiles(
            @RequestParam String folderId,
            Authentication authentication) throws Exception {

        return getCachedItems(folderId, authentication)
                .stream()
                .filter(item -> "pdf".equals(item.getType()))
                .toList();
    }
    
    @GetMapping("/api/drive/folders")
    /** 지정한 Google Drive 폴더 바로 아래의 하위 폴더만 조회한다. */
    public List<DriveFileDto> getFolders(
            @RequestParam String folderId,
            Authentication authentication) throws Exception {

        return getCachedItems(folderId, authentication)
                .stream()
                .filter(item -> "folder".equals(item.getType()))
                .toList();
    }
    
    @GetMapping("/api/drive/pdf")
    /** Drive의 PDF 원본을 내려받아 브라우저에 application/pdf로 전달한다. */
    public ResponseEntity<byte[]> getPdf(
            @RequestParam String fileId,
            Authentication authentication) throws Exception {

        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        drive.files()
             .get(fileId)
             .executeMediaAndDownloadTo(outputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .body(outputStream.toByteArray());
    }

    /** Drive PDF를 원본 파일명으로 다운로드한다. */
    @GetMapping("/api/drive/pdf/download")
    public ResponseEntity<byte[]> downloadPdf(
            @RequestParam String fileId,
            Authentication authentication) throws Exception {

        File file = drive.files()
                .get(fileId)
                .setSupportsAllDrives(true)
                .setFields("name")
                .execute();
        ByteArrayOutputStream outputStream =
                new ByteArrayOutputStream();

        drive.files()
                .get(fileId)
                .executeMediaAndDownloadTo(outputStream);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        PDF_MIME_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                        .filename(
                                file.getName(),
                                StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(outputStream.toByteArray());
    }
    
    
    @GetMapping("/api/drive/path")
    /** 선택한 파일에서 상위 폴더를 따라가며 탐색 경로를 생성한다. */
    public List<DriveFileDto> getPath(
            @RequestParam String fileId,
            Authentication authentication) throws Exception {

        List<DriveFileDto> pathList =
                new ArrayList<>();

        File current =
                drive.files()
                     .get(fileId)
                     .setSupportsAllDrives(true)
                     .setFields("id,name,parents")
                     .execute();

        pathList.add(
                new DriveFileDto(
                        current.getId(),
                        current.getName(),
                        "",
                        "",
                        "pdf"
                )
        );

        while(current.getParents() != null
                && !current.getParents().isEmpty()){

            String parentId =
                    current.getParents().get(0);

            current =
                    drive.files()
                         .get(parentId)
                         .setSupportsAllDrives(true)
                         .setFields("id,name,parents")
                         .execute();
            pathList.add(0,
                    new DriveFileDto(
                            current.getId(),
                            current.getName(),
                            "",
                            "",
                            "folder"
                    )
            );

            if("0ANjwWGzgwzlhUk9PVA".equals(current.getId())){
                break;
            }
        }

        return pathList;
    }

    /**
     * 사용자와 폴더별 캐시를 조회한다.
     * 같은 폴더에 요청이 동시에 들어오면 한 요청만 Drive API를 호출한다.
     */
    private List<DriveFileDto> getCachedItems(
            String folderId,
            Authentication authentication) throws Exception {

        return getCachedItems(
                folderId,
                authentication,
                false);
    }

    private List<DriveFileDto> getCachedItems(
            String folderId,
            Authentication authentication,
            boolean refresh) throws Exception {

        String cacheKey =
                authentication.getName() + ":" + folderId;

        if(refresh){
            itemCache.remove(cacheKey);
            itemCacheLocks.remove(cacheKey);
        }

        CachedDriveItems cachedItems =
                getValidCache(cacheKey);

        if(cachedItems != null){
            return cachedItems.items();
        }

        Object cacheLock =
                itemCacheLocks.computeIfAbsent(
                        cacheKey,
                        key -> new Object());

        synchronized(cacheLock){

            cachedItems = getValidCache(cacheKey);

            if(cachedItems != null){
                return cachedItems.items();
            }

            List<DriveFileDto> items =
                    loadItemsFromDrive(
                            folderId,
                            authentication);

            itemCache.put(
                    cacheKey,
                    new CachedDriveItems(
                            items,
                            Instant.now().plus(
                                    ITEM_CACHE_DURATION)));
            removeExpiredCacheEntries();

            return items;
        }
    }

    /** Google Drive에서 필요한 최소 필드만 조회하고 화면 표시 순서로 정렬한다. */
    private List<DriveFileDto> loadItemsFromDrive(
            String folderId,
            Authentication authentication) throws Exception {

        FileList result =
                drive.files()
                .list()
                .setQ(
                        "'" + folderId + "' in parents"
                        + " and trashed=false"
                        + " and (mimeType='" + PDF_MIME_TYPE + "'"
                        + " or mimeType='" + FOLDER_MIME_TYPE + "')")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setPageSize(1000)
                .setFields("files(id,name,mimeType)")
                .execute();

        return result.getFiles()
                .stream()
                .map(file ->
                        new DriveFileDto(
                                file.getId(),
                                file.getName(),
                                "",
                                "",
                                FOLDER_MIME_TYPE.equals(
                                        file.getMimeType())
                                ? "folder"
                                : "pdf"))
                .sorted(
                        Comparator
                        .comparing(DriveFileDto::getType)
                        .thenComparing(
                                DriveFileDto::getName,
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** 만료되지 않은 캐시만 반환하고 만료 항목은 즉시 제거한다. */
    private CachedDriveItems getValidCache(String cacheKey) {

        CachedDriveItems cachedItems =
                itemCache.get(cacheKey);

        if(cachedItems == null){
            return null;
        }

        if(cachedItems.expiresAt().isAfter(Instant.now())){
            return cachedItems;
        }

        itemCache.remove(cacheKey, cachedItems);
        itemCacheLocks.remove(cacheKey);
        return null;
    }

    /** 장시간 사용 시 만료된 폴더 캐시와 잠금 객체가 쌓이지 않도록 정리한다. */
    private void removeExpiredCacheEntries() {

        Instant now = Instant.now();

        itemCache.forEach((key, value) -> {
            if(!value.expiresAt().isAfter(now)){
                itemCache.remove(key, value);
                itemCacheLocks.remove(key);
            }
        });
    }

    /** 캐시된 항목과 캐시 만료 시각을 함께 보관한다. */
    private record CachedDriveItems(
    		List<DriveFileDto> items,
    		Instant expiresAt) {
    }
}
