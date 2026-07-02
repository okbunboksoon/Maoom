package maoomWeb.ire.user.service;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;

/**
 * 댓글 첨부파일의 실제 파일과 DB 메타데이터를 함께 관리한다.
 *
 * <p>실제 파일은 {@code app.comment.upload-dir} 아래에 UUID 이름으로 저장하고,
 * 사용자가 올린 원래 이름·크기·댓글/답글 연결 정보는 CommentMapper를 통해
 * {@code tb_comment_attachment}에 저장한다.</p>
 */
@Service
public class CommentAttachmentService {

    private static final Logger log =
            LoggerFactory.getLogger(CommentAttachmentService.class);

    private final CommentMapper commentMapper;
    private final Path uploadRoot;

    /** 설정된 업로드 경로를 정규화하고 저장 폴더를 준비한다. */
    public CommentAttachmentService(
            CommentMapper commentMapper,
            @Value("${app.comment.upload-dir}") String uploadDir)
            throws IOException {

        this.commentMapper = commentMapper;
        this.uploadRoot = Path.of(uploadDir)
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(uploadRoot);
    }

    /** 댓글 소유권을 확인한 뒤 파일과 첨부 메타데이터를 함께 저장한다. */
    public List<CommentAttachmentDto> saveAttachments(
            Long commentId,
            List<MultipartFile> files,
            String userId) throws IOException {

        CommentDto comment = commentMapper.getComment(commentId);

        if(comment == null){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "댓글을 찾을 수 없습니다.");
        }

        if(!userId.equals(comment.getUserId())){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "댓글 작성자만 파일을 첨부할 수 있습니다.");
        }

        saveFiles(
                commentId,
                null,
                files,
                userId);

        return commentMapper.getAttachmentList(commentId);
    }

    /** 답글 작성자 권한을 확인한 뒤 해당 답글에 파일을 저장한다. */
    public List<CommentAttachmentDto> saveReplyAttachments(
            Long commentId,
            Long replyId,
            List<MultipartFile> files,
            String userId) throws IOException {

        CommentReplyDto reply =
                commentMapper.getReply(replyId);

        if(reply == null
                || !commentId.equals(reply.getCommentId())){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "답글을 찾을 수 없습니다.");
        }

        if(!userId.equals(reply.getUserId())){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "답글 작성자만 파일을 첨부할 수 있습니다.");
        }

        saveFiles(
                commentId,
                replyId,
                files,
                userId);

        return commentMapper.getReplyAttachmentList(replyId);
    }

    private void saveFiles(
            Long commentId,
            Long replyId,
            List<MultipartFile> files,
            String userId) throws IOException {

        // 여러 파일 중 비어 있는 항목은 건너뛰고, 각 파일을 고유 저장명으로 보관한다.
        for(MultipartFile file : files){

            if(file == null || file.isEmpty()){
                continue;
            }

            String storedName = UUID.randomUUID().toString();
            Path target = resolveStoredFile(storedName);

            Files.copy(
                    file.getInputStream(),
                    target,
                    StandardCopyOption.REPLACE_EXISTING);

            CommentAttachmentDto attachment =
                    new CommentAttachmentDto();

            attachment.setCommentId(commentId);
            attachment.setReplyId(replyId);
            attachment.setOriginalName(
                    sanitizeFileName(
                            file.getOriginalFilename()));
            attachment.setStoredName(storedName);
            attachment.setContentType(file.getContentType());
            attachment.setFileSize(file.getSize());
            attachment.setUploaderId(userId);

            try{
                commentMapper.addAttachment(attachment);
            }catch(RuntimeException error){
                Files.deleteIfExists(target);
                throw error;
            }
        }
    }

    /** 지정 댓글에 등록된 첨부파일 목록을 반환한다. */
    public List<CommentAttachmentDto> getAttachmentList(
            Long commentId) {
        return commentMapper.getAttachmentList(commentId);
    }

    public List<CommentAttachmentDto> getReplyAttachmentList(
            Long replyId) {
        return commentMapper.getReplyAttachmentList(replyId);
    }

    /** 첨부파일 메타데이터를 조회하고 없으면 404 오류를 반환한다. */
    public CommentAttachmentDto getAttachment(
            Long attachmentId) {

        CommentAttachmentDto attachment =
                commentMapper.getAttachment(attachmentId);

        if(attachment == null){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "첨부파일을 찾을 수 없습니다.");
        }

        return attachment;
    }

    /** 저장 경로의 파일을 다운로드 가능한 Spring Resource로 변환한다. */
    public Resource getResource(
            CommentAttachmentDto attachment) throws IOException {

        Resource resource =
                new UrlResource(
                        resolveStoredFile(
                                attachment.getStoredName())
                        .toUri());

        if(!resource.exists() || !resource.isReadable()){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "첨부파일을 읽을 수 없습니다.");
        }

        return resource;
    }

    /** 댓글 삭제 시 연결된 DB 행과 실제 저장 파일을 함께 정리한다. */
    public void deleteForComment(Long commentId) {

        List<CommentAttachmentDto> attachments =
                commentMapper.getAllAttachmentList(commentId);

        commentMapper.deleteAttachmentsByCommentId(commentId);
        deleteStoredFiles(attachments);
    }

    public void deleteForReply(Long replyId) {

        List<CommentAttachmentDto> attachments =
                commentMapper.getReplyAttachmentList(replyId);

        commentMapper.deleteAttachmentsByReplyId(replyId);
        deleteStoredFiles(attachments);
    }

    private void deleteStoredFiles(
            List<CommentAttachmentDto> attachments) {

        attachments.forEach(attachment -> {
            try{
                Files.deleteIfExists(
                        resolveStoredFile(
                                attachment.getStoredName()));
            }catch(IOException e){
                log.warn(
                        "Attachment file cleanup failed",
                        e);
            }
        });
    }

    /** 저장 파일 경로가 업로드 루트 밖으로 벗어나지 않도록 검증한다. */
    private Path resolveStoredFile(String storedName) {

        Path target =
                uploadRoot.resolve(storedName).normalize();

        if(!target.startsWith(uploadRoot)){
            throw new IllegalArgumentException(
                    "잘못된 첨부파일 경로입니다.");
        }

        return target;
    }

    /** 브라우저가 보낸 경로 정보를 제거하고 순수 파일명만 남긴다. */
    private String sanitizeFileName(String fileName) {

        if(fileName == null || fileName.isBlank()){
            return "attachment";
        }

        return Path.of(fileName)
                .getFileName()
                .toString();
    }
}
