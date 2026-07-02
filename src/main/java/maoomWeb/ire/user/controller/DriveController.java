package maoomWeb.ire.user.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import maoomWeb.ire.user.dto.DriveFileDto;
import maoomWeb.ire.user.service.PdfAccessService;

/**
 * PDF 리뷰 화면이 사용하는 Google Drive 탐색과 PDF 원본 전송 API다.
 *
 * <p>왼쪽 문서 트리는 items/path API를 호출하고, 가운데 PDF.js 뷰어는
 * pdf API를 호출한다. Drive API를 매번 호출하면 느리므로 폴더 목록은 메모리에
 * 5분간, PDF 원본은 로컬 캐시 폴더에 저장해 재사용한다.</p>
 */
@RestController
public class DriveController {

	private static final String PDF_MIME_TYPE =
			"application/pdf";

	private static final String FOLDER_MIME_TYPE =
			"application/vnd.google-apps.folder";

	private static final String SHORTCUT_MIME_TYPE =
			"application/vnd.google-apps.shortcut";

	private static final Duration ITEM_CACHE_DURATION =
			Duration.ofMinutes(5);

	private static final String ROOT_FOLDER_ID =
			"0ALoTVIAzU74bUk9PVA";

	private final Drive drive;
	private final PdfAccessService pdfAccessService;
	private final Path pdfCacheDir;
	private final Map<String,CachedDriveItems> itemCache =
			new ConcurrentHashMap<>();
	private final Map<String,Object> itemCacheLocks =
			new ConcurrentHashMap<>();
	private final Map<String,Object> pdfCacheLocks =
			new ConcurrentHashMap<>();

    public DriveController(
            Drive drive,
            PdfAccessService pdfAccessService,
            @Value("${app.drive.pdf-cache-dir:${user.home}/maoomtool-uploads/drive-pdf-cache}")
            String pdfCacheDir) {
        this.drive = drive;
        this.pdfAccessService = pdfAccessService;
        this.pdfCacheDir = Path.of(pdfCacheDir)
                .toAbsolutePath()
                .normalize();
	}

    /** 폴더와 PDF를 한 번에 조회하고 사용자/폴더별로 5분간 캐시한다. */
    @GetMapping("/api/drive/items")
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
    

    /** 지정한 Google Drive 폴더 바로 아래의 PDF만 조회한다. */
    @GetMapping("/api/drive/files")
    @ResponseBody
    public List<DriveFileDto> getFiles(
            @RequestParam String folderId,
            Authentication authentication) throws Exception {

        return getCachedItems(folderId, authentication)
                .stream()
                .filter(item -> "pdf".equals(item.getType()))
                .toList();
    }
    
    /** 지정한 Google Drive 폴더 바로 아래의 하위 폴더만 조회한다. */
    @GetMapping("/api/drive/folders")
    public List<DriveFileDto> getFolders(
            @RequestParam String folderId,
            Authentication authentication) throws Exception {

        return getCachedItems(folderId, authentication)
                .stream()
                .filter(item -> "folder".equals(item.getType()))
                .toList();
    }
    
    /**
     * PDF.js에 PDF 내용을 스트리밍한다.
     * Range 요청을 지원하므로 큰 PDF도 브라우저가 필요한 구간부터 읽을 수 있다.
     */
    @GetMapping("/api/drive/pdf")
    public ResponseEntity<StreamingResponseBody> getPdf(
            @RequestParam String fileId,
            @RequestHeader(
                    value = HttpHeaders.RANGE,
                    required = false) String rangeHeader,
            Authentication authentication) throws Exception {

        pdfAccessService.requireDriveFile(
                fileId,
                authentication.getName());
        Path cachePath =
                ensurePdfCached(fileId);
        long fileSize =
                Files.size(cachePath);
        ByteRange range =
                parseRange(rangeHeader, fileSize);
        StreamingResponseBody stream =
                outputStream -> streamCachedPdf(
                        cachePath,
                        outputStream,
                        range);
        ResponseEntity.BodyBuilder response =
                range == null
                ? ResponseEntity.ok()
                : ResponseEntity.status(206);

        response
                .header(HttpHeaders.CONTENT_TYPE, PDF_MIME_TYPE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");

        if(range == null){
            response.header(
                    HttpHeaders.CONTENT_LENGTH,
                    String.valueOf(fileSize));
        }else{
            response.header(
                    HttpHeaders.CONTENT_LENGTH,
                    String.valueOf(range.length()));
            response.header(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes "
                    + range.start()
                    + "-"
                    + range.end()
                    + "/"
                    + fileSize);
        }

        return response.body(stream);
    }

    /** 로컬에 캐시된 Drive PDF 전체를 원본 파일명으로 다운로드한다. */
    @GetMapping("/api/drive/pdf/download")
    public ResponseEntity<StreamingResponseBody> downloadPdf(
            @RequestParam String fileId,
            Authentication authentication) throws Exception {

        pdfAccessService.requireDriveFile(
                fileId,
                authentication.getName());
        File file = drive.files()
                .get(fileId)
                .setSupportsAllDrives(true)
                .setFields("name")
                .execute();
        Path cachePath =
                ensurePdfCached(fileId);
        long contentLength =
                Files.size(cachePath);
        StreamingResponseBody stream =
                outputStream -> streamCachedPdf(
                        cachePath,
                        outputStream,
                        null);

        ResponseEntity.BodyBuilder response =
                ResponseEntity.ok()
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
                        .toString());

        response.header(
                HttpHeaders.CONTENT_LENGTH,
                String.valueOf(contentLength));

        return response.body(stream);
    }
    
    
    /** 선택한 파일에서 상위 폴더를 따라가며 탐색 경로를 생성한다. */
    @GetMapping("/api/drive/path")
    public List<DriveFileDto> getPath(
            @RequestParam String fileId,
            Authentication authentication) throws Exception {

        pdfAccessService.requireDriveFile(
                fileId,
                authentication.getName());
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

            if(ROOT_FOLDER_ID.equals(current.getId())){
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
                .setCorpora("drive")
                .setDriveId(ROOT_FOLDER_ID)
                .setQ(
                        "'" + folderId + "' in parents"
                        + " and trashed=false"
                        + " and (mimeType='" + PDF_MIME_TYPE + "'"
                        + " or mimeType='" + FOLDER_MIME_TYPE + "'"
                        + " or mimeType='" + SHORTCUT_MIME_TYPE + "')")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setPageSize(1000)
                .setFields(
                        "files(id,name,mimeType,"
                        + "shortcutDetails(targetId,targetMimeType))")
                .execute();

        return result.getFiles()
                .stream()
                .filter(this::isSupportedDriveItem)
                .map(file ->
                        new DriveFileDto(
                                getVisibleFileId(file),
                                file.getName(),
                                "",
                                "",
                                FOLDER_MIME_TYPE.equals(
                                        getVisibleMimeType(file))
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

    /**
     * Drive PDF를 로컬 캐시에 완전히 저장한 뒤 해당 경로를 반환한다.
     * 첫 요청의 비동기 응답 제한시간과 같은 파일의 직렬 전송을 피하기 위해
     * 실제 응답 스트리밍은 캐시 생성 잠금 밖에서 수행한다.
     */
    private Path ensurePdfCached(
            String fileId) throws Exception {

        Files.createDirectories(pdfCacheDir);

        Path cachePath =
                getPdfCachePath(fileId);

        if(Files.exists(cachePath)){
            return cachePath;
        }

        Object lock =
                pdfCacheLocks.computeIfAbsent(
                        fileId,
                        key -> new Object());

        synchronized(lock){
            if(Files.exists(cachePath)){
                return cachePath;
            }

            Path tempPath =
                    cachePath.resolveSibling(
                            cachePath.getFileName()
                            + "."
                            + UUID.randomUUID()
                            + ".tmp");

            try(OutputStream cacheStream =
                    Files.newOutputStream(tempPath)){

                drive.files()
                        .get(fileId)
                        .setSupportsAllDrives(true)
                        .executeMediaAndDownloadTo(cacheStream);
                cacheStream.flush();
            }catch(Exception error){
                Files.deleteIfExists(tempPath);
                throw error;
            }

            moveCacheFile(tempPath, cachePath);
        }

        return cachePath;
    }

    private Path getPdfCachePath(String fileId) {
        return pdfCacheDir.resolve(
                fileId.replaceAll("[^A-Za-z0-9_-]", "_")
                + ".pdf");
    }

    private void streamCachedPdf(
            Path cachePath,
            OutputStream responseStream,
            ByteRange range) throws IOException {

        try(InputStream inputStream =
                Files.newInputStream(cachePath)){

            long remaining;

            if(range == null){
                remaining = Files.size(cachePath);
            }else{
                inputStream.skipNBytes(range.start());
                remaining = range.length();
            }

            byte[] buffer = new byte[64 * 1024];

            while(remaining > 0){
                int read =
                        inputStream.read(
                                buffer,
                                0,
                                (int)Math.min(
                                        buffer.length,
                                        remaining));

                if(read < 0){
                    break;
                }

                responseStream.write(buffer, 0, read);
                remaining -= read;
            }

            responseStream.flush();
        }
    }

    private ByteRange parseRange(
            String rangeHeader,
            long fileSize) {

        if(rangeHeader == null
                || !rangeHeader.startsWith("bytes=")
                || fileSize <= 0){
            return null;
        }

        String value =
                rangeHeader.substring("bytes=".length()).trim();
        int commaIndex =
                value.indexOf(',');

        if(commaIndex >= 0){
            value = value.substring(0, commaIndex).trim();
        }

        String[] parts =
                value.split("-", 2);

        try{
            long start;
            long end;

            if(parts[0].isBlank()){
                long suffixLength =
                        Long.parseLong(parts[1]);
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            }else{
                start = Long.parseLong(parts[0]);
                end =
                        parts.length > 1 && !parts[1].isBlank()
                        ? Long.parseLong(parts[1])
                        : fileSize - 1;
            }

            if(start < 0 || start >= fileSize || end < start){
                return null;
            }

            return new ByteRange(
                    start,
                    Math.min(end, fileSize - 1));
        }catch(NumberFormatException error){
            return null;
        }
    }

    private record ByteRange(long start, long end) {

        long length() {
            return end - start + 1;
        }
    }

    private void moveCacheFile(
            Path tempPath,
            Path cachePath) throws IOException {

        try{
            Files.move(
                    tempPath,
                    cachePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException error){
            Files.move(
                    tempPath,
                    cachePath,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 폴더/PDF와 그 둘을 가리키는 Drive 바로가기를 목록에 포함한다. */
    private boolean isSupportedDriveItem(File file) {

        String mimeType =
                getVisibleMimeType(file);

        return FOLDER_MIME_TYPE.equals(mimeType)
                || PDF_MIME_TYPE.equals(mimeType);
    }

    private String getVisibleFileId(File file) {

        if(SHORTCUT_MIME_TYPE.equals(file.getMimeType())
                && file.getShortcutDetails() != null
                && file.getShortcutDetails().getTargetId() != null){
            return file.getShortcutDetails().getTargetId();
        }

        return file.getId();
    }

    private String getVisibleMimeType(File file) {

        if(SHORTCUT_MIME_TYPE.equals(file.getMimeType())
                && file.getShortcutDetails() != null){
            return file.getShortcutDetails().getTargetMimeType();
        }

        return file.getMimeType();
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
