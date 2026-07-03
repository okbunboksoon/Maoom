package maoomWeb.ire.user.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.mapper.PdfMapper;

/**
 * PDF 리뷰에서 사용하는 PDF, 댓글, 답글, 첨부파일의 접근 가능 여부를 한곳에서 검사한다.
 *
 * <p>컨트롤러와 WebSocket 핸들러는 DB를 직접 조회하지 않고 이 서비스를 호출한다.
 * PDF.js는 한 PDF를 읽을 때 여러 번의 Range 요청을 보낼 수 있으므로, 허용 결과는
 * 30초 동안 캐시한다. 거부 결과도 2초만 캐시해 반복 공격성 요청은 줄이되,
 * 권한 상태 변경은 비교적 빠르게 반영한다.</p>
 *
 * <p>현재 MyBatis 쿼리는 해당 리소스가 MAOOM DB에 등록되어 있는지를 확인한다.
 * 향후 사용자별 권한 테이블이 추가되면 {@code PdfMapper.xml}의 EXISTS 조건을
 * 확장하면 되고, 컨트롤러 코드는 그대로 유지할 수 있다.</p>
 */
@Service
public class PdfAccessService {

    private static final long ALLOW_TTL_NANOS =
            Duration.ofSeconds(30).toNanos();
    private static final long DENY_TTL_NANOS =
            Duration.ofSeconds(2).toNanos();
    private static final int MAX_CACHE_ENTRIES = 4096;

    private final PdfMapper pdfMapper;
    private final ConcurrentHashMap<AccessKey,CacheEntry> cache =
            new ConcurrentHashMap<>();

    public PdfAccessService(PdfMapper pdfMapper) {
        this.pdfMapper = pdfMapper;
    }

    /** 내부 pdfId가 유효한지 확인한다. 댓글 목록 조회와 Excel 내보내기에 사용된다. */
    public void requirePdf(Long pdfId, String userId) {
        require(
                new AccessKey("pdf", userId, String.valueOf(pdfId)),
                () -> pdfId != null
                        && validUser(userId)
                        && pdfMapper.hasPdfAccess(pdfId, userId));
    }

    /** Drive 파일 ID가 등록된 PDF인지 확인한다. PDF 원본 스트리밍 전에 사용된다. */
    public void requireDriveFile(
            String driveFileId,
            String userId) {
        require(
                new AccessKey("drive", userId, driveFileId),
                () -> validId(driveFileId)
                        && validUser(userId)
                        && pdfMapper.hasDriveFileAccess(
                                driveFileId,
                                userId));
    }

    /** pdfId와 Drive 파일 ID가 같은 파일을 가리키는지 확인한다. 주석 PDF 내보내기에 사용된다. */
    public void requirePdfFile(
            Long pdfId,
            String driveFileId,
            String userId) {
        require(
                new AccessKey(
                        "pdf-file",
                        userId,
                        pdfId + ":" + driveFileId),
                () -> pdfId != null
                        && validId(driveFileId)
                        && validUser(userId)
                        && pdfMapper.hasPdfFileAccess(
                                pdfId,
                                driveFileId,
                                userId));
    }

    /** 댓글이 접근 가능한 PDF에 속하는지 확인한다. 수정·삭제·첨부 요청 전에 사용된다. */
    public void requireComment(
            Long commentId,
            String userId) {
        require(
                new AccessKey(
                        "comment",
                        userId,
                        String.valueOf(commentId)),
                () -> commentId != null
                        && validUser(userId)
                        && pdfMapper.hasCommentAccess(
                                commentId,
                                userId));
    }

    /** WebSocket 편집 이벤트의 댓글이 현재 접속한 PDF에 실제로 속하는지 확인한다. */
    public boolean canAccessCommentInPdf(
            Long commentId,
            Long pdfId,
            String userId) {
        return isAllowed(
                new AccessKey(
                        "pdf-comment",
                        userId,
                        pdfId + ":" + commentId),
                () -> commentId != null
                        && pdfId != null
                        && validUser(userId)
                        && pdfMapper.hasCommentAccessForPdf(
                                commentId,
                                pdfId,
                                userId));
    }

    /** 답글이 접근 가능한 댓글 아래에 있는지 확인한다. */
    public void requireReply(
            Long replyId,
            String userId) {
        require(
                new AccessKey(
                        "reply",
                        userId,
                        String.valueOf(replyId)),
                () -> replyId != null
                        && validUser(userId)
                        && pdfMapper.hasReplyAccess(
                                replyId,
                                userId));
    }

    /** 첨부파일이 접근 가능한 댓글에 연결되어 있는지 확인한다. */
    public void requireAttachment(
            Long attachmentId,
            String userId) {
        require(
                new AccessKey(
                        "attachment",
                        userId,
                        String.valueOf(attachmentId)),
                () -> attachmentId != null
                        && validUser(userId)
                        && pdfMapper.hasAttachmentAccess(
                                attachmentId,
                                userId));
    }

    /** 예외 대신 true/false가 필요한 WebSocket 연결 단계에서 사용하는 PDF 검사 메서드다. */
    public boolean canAccessPdf(Long pdfId, String userId) {
        return isAllowed(
                new AccessKey(
                        "pdf",
                        userId,
                        String.valueOf(pdfId)),
                () -> pdfId != null
                        && validUser(userId)
                        && pdfMapper.hasPdfAccess(pdfId, userId));
    }

    /**
     * PDF 메타데이터 수정·삭제는 등록자 또는 관리자에게만 허용한다.
     * 단순 열람·댓글 권한과 관리 권한을 분리하기 위한 메서드다.
     */
    public void requirePdfManagement(
            Long pdfId,
            String userId,
            boolean administrator) {

        if(administrator){
            requirePdf(pdfId, userId);
            return;
        }

        boolean owner = isAllowed(
                new AccessKey(
                        "pdf-owner",
                        userId,
                        String.valueOf(pdfId)),
                () -> pdfId != null
                        && validUser(userId)
                        && pdfMapper.isPdfOwner(
                                pdfId,
                                userId));

        if(!owner){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "PDF 관리 권한이 없습니다.");
        }
    }

    /**
     * 방금 정상적으로 연 PDF의 관련 권한 세 가지를 캐시에 미리 저장한다.
     * 이후 PDF 스트리밍, 댓글 조회, WebSocket 연결이 연속으로 와도 DB 조회를 줄일 수 있다.
     */
    public void rememberPdfAccess(
            Long pdfId,
            String driveFileId,
            String userId) {
        long expiresAt = System.nanoTime() + ALLOW_TTL_NANOS;
        cache.put(
                new AccessKey("pdf", userId, String.valueOf(pdfId)),
                new CacheEntry(true, expiresAt));
        cache.put(
                new AccessKey("drive", userId, driveFileId),
                new CacheEntry(true, expiresAt));
        cache.put(
                new AccessKey(
                        "pdf-file",
                        userId,
                        pdfId + ":" + driveFileId),
                new CacheEntry(true, expiresAt));
        trimCache();
    }

    private void require(
            AccessKey key,
            BooleanSupplier loader) {
        if(!isAllowed(key, loader)){
            // 권한이 없는 사용자에게 리소스 존재 여부까지 알려주지 않도록 404로 통일한다.
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "PDF 리소스를 찾을 수 없습니다.");
        }
    }

    private boolean isAllowed(
            AccessKey key,
            BooleanSupplier loader) {
        // compute를 사용하면 같은 키가 동시에 요청되어도 한 번의 계산 결과를 공유할 수 있다.
        long now = System.nanoTime();
        CacheEntry resolved = cache.compute(
                key,
                (ignored, cached) -> {
                    if(cached != null
                            && cached.expiresAtNanos() > now){
                        return cached;
                    }

                    boolean allowed = loader.getAsBoolean();
                    long ttl =
                            allowed
                            ? ALLOW_TTL_NANOS
                            : DENY_TTL_NANOS;
                    return new CacheEntry(
                            allowed,
                            now + ttl);
                });
        trimCache();
        return resolved.allowed();
    }

    private void trimCache() {
        // 만료 항목을 먼저 제거하고, 그래도 상한을 넘으면 일부 키를 제거해 메모리 증가를 막는다.
        if(cache.size() <= MAX_CACHE_ENTRIES){
            return;
        }

        long now = System.nanoTime();
        cache.entrySet().removeIf(
                entry -> entry.getValue().expiresAtNanos() <= now);

        if(cache.size() > MAX_CACHE_ENTRIES){
            int removeCount = cache.size() - MAX_CACHE_ENTRIES;
            for(AccessKey key : cache.keySet()){
                if(removeCount-- <= 0){
                    break;
                }
                cache.remove(key);
            }
        }
    }

    private boolean validUser(String userId) {
        return userId != null && !userId.isBlank();
    }

    private boolean validId(String value) {
        return value != null && !value.isBlank();
    }

    private record AccessKey(
            String type,
            String userId,
            String objectId) {
    }

    private record CacheEntry(
            boolean allowed,
            long expiresAtNanos) {
    }
}
