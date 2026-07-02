package maoomWeb.ire.user.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;
import maoomWeb.ire.user.websocket.PdfCollaborationHandler;

/**
 * PDF 댓글·답글의 저장 규칙과 후속 작업을 담당하는 중심 서비스다.
 *
 * <p>CommentController가 요청값과 접근 권한을 확인한 뒤 이 서비스를 호출하고,
 * 이 서비스는 CommentMapper로 DB를 변경한다. 변경 트랜잭션이 성공한 뒤에만
 * Slack 멘션과 WebSocket 갱신 신호를 보내므로, 저장 실패 데이터가 다른 사용자
 * 화면에 먼저 보이는 일을 막는다.</p>
 */
@Service
public class CommentService {

    private static final Logger log =
            LoggerFactory.getLogger(CommentService.class);

    private final CommentMapper commentMapper;
    private final SlackMentionService slackMentionService;
    private final PdfCollaborationHandler collaborationHandler;
    private final CommentAttachmentService attachmentService;

    public CommentService(
            CommentMapper commentMapper,
            SlackMentionService slackMentionService,
            PdfCollaborationHandler collaborationHandler,
            CommentAttachmentService attachmentService) {
        this.commentMapper = commentMapper;
        this.slackMentionService = slackMentionService;
        this.collaborationHandler = collaborationHandler;
        this.attachmentService = attachmentService;
    }

    /**
     * 상태 조건에 맞는 댓글을 조회하고 답글·첨부파일을 묶어 화면용 구조로 만든다.
     * 화면은 이 한 번의 응답만으로 댓글 카드 전체를 그릴 수 있다.
     */
    public List<CommentDto> getCommentList(
            Long pdfId,
            String status){

        List<CommentDto> comments =
                commentMapper.getCommentList(pdfId, status);

        if(comments.isEmpty()){
            return comments;
        }

        List<Long> commentIds = comments.stream()
                .map(CommentDto::getCommentId)
                .toList();
        List<CommentReplyDto> replies =
                commentMapper.getReplyListByCommentIds(commentIds);
        attachReplyAttachments(replies);

        Map<Long,List<CommentReplyDto>> repliesByComment =
                replies
                .stream()
                .collect(Collectors.groupingBy(
                        CommentReplyDto::getCommentId));
        Map<Long,List<CommentAttachmentDto>> attachmentsByComment =
                commentMapper
                .getAttachmentListByCommentIds(commentIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CommentAttachmentDto::getCommentId));

        comments.forEach(comment -> {
            comment.setReplies(
                    repliesByComment.getOrDefault(
                            comment.getCommentId(),
                            List.of()));
            comment.setAttachments(
                    attachmentsByComment.getOrDefault(
                            comment.getCommentId(),
                            List.of()));
        });

        return comments;

    }
    
    /**
     * PDF별 순번(id0001 형식)을 만든 뒤 댓글을 저장한다.
     * 같은 PDF에서 동시에 등록해도 번호가 겹치지 않도록 먼저 PDF 행을 잠근다.
     */
    @Transactional
    public Long addComment(CommentDto commentDto){

        commentMapper.lockPdf(commentDto.getPdfId());
        int commentNumber =
                commentMapper.getNextCommentNumber(
                        commentDto.getPdfId());
        commentDto.setCommentCode(
                String.format("id%04d", commentNumber));

        commentMapper.addComment(commentDto);

        afterCommit(() ->
                slackMentionService.sendMentionNotification(
                        commentDto.getCommentText(),
                        commentDto.getPdfId(),
                        commentDto.getCommentCode()));
        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        commentDto.getPdfId()));

        return commentDto.getCommentId();

    }

    /** 작성자 본인의 댓글만 수정하고 관련 사용자에게 변경을 알린다. */
    @Transactional
    public int updateComment(
            CommentDto commentDto,
            String userId){

        CommentDto savedComment =
                commentMapper.getComment(
                        commentDto.getCommentId());

        int result = commentMapper.updateComment(
                commentDto.getCommentId(),
                commentDto.getCommentText(),
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 댓글을 수정할 수 있습니다.");
        }

        Long pdfId =
                savedComment == null
                ? null
                : savedComment.getPdfId();
        String commentCode =
                savedComment == null
                ? null
                : savedComment.getCommentCode();

        afterCommit(() ->
                slackMentionService.sendMentionNotification(
                        commentDto.getCommentText(),
                        pdfId,
                        commentCode));
        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;

    }

    /** 작성자 본인의 사각형 또는 말풍선 좌표를 변경하고 협업 사용자에게 알린다. */
    @Transactional
    public int updateCommentGeometry(
            CommentDto commentDto,
            String userId){

        int result = commentMapper.updateCommentGeometry(
                commentDto.getCommentId(),
                commentDto.getRectX(),
                commentDto.getRectY(),
                commentDto.getRectW(),
                commentDto.getRectH(),
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 주석의 위치와 크기를 변경할 수 있습니다.");
        }

        CommentDto savedComment =
                commentMapper.getComment(
                        commentDto.getCommentId());

        Long pdfId =
                savedComment == null
                ? null
                : savedComment.getPdfId();

        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;
    }

    /** 댓글과 소속 답글을 하나의 트랜잭션으로 삭제한다. */
    @Transactional
    public int deleteComment(
            Long commentId,
            String userId){

        CommentDto savedComment =
                commentMapper.getComment(commentId);

        commentMapper.deleteRepliesByCommentId(
                commentId,
                userId);

        int result = commentMapper.deleteComment(
                commentId,
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 댓글을 삭제할 수 있습니다.");
        }

        attachmentService.deleteForComment(commentId);

        Long pdfId =
                savedComment == null
                ? null
                : savedComment.getPdfId();

        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;

    }

    /** 허용된 상태값인지 확인한 후 작성자 본인의 댓글 상태를 변경한다. */
    @Transactional
    public int updateCommentStatus(
            CommentDto commentDto,
            String userId){

        String status = commentDto.getStatus();

        if(!"OPEN".equals(status)
                && !"RESOLVED".equals(status)){
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "지원하지 않는 댓글 상태입니다.");
        }

        int result = commentMapper.updateCommentStatus(
                commentDto.getCommentId(),
                status,
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 댓글 상태를 변경할 수 있습니다.");
        }

        CommentDto savedComment =
                commentMapper.getComment(
                        commentDto.getCommentId());

        Long pdfId =
                savedComment == null
                ? null
                : savedComment.getPdfId();

        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;

    }
    
    /** 답글 목록에 각 답글의 첨부파일을 결합해 반환한다. */
    public List<CommentReplyDto> getReplyList(Long commentId){
        List<CommentReplyDto> replies =
                commentMapper.getReplyList(commentId);
        attachReplyAttachments(replies);
        return replies;
    }

    /** 답글을 저장하고 원 댓글이 속한 PDF에 변경 이벤트를 전송한다. */
    @Transactional
    public Long addReply(CommentReplyDto dto){

        commentMapper.addReply(dto);

        CommentDto comment =
                commentMapper.getComment(
                        dto.getCommentId());

        Long pdfId =
                comment == null
                ? null
                : comment.getPdfId();
        String commentCode =
                comment == null
                ? null
                : comment.getCommentCode();

        afterCommit(() ->
                slackMentionService.sendMentionNotification(
                        dto.getReplyText(),
                        pdfId,
                        commentCode));
        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return dto.getReplyId();
    }

    /** 작성자 본인의 답글만 수정한다. */
    @Transactional
    public int updateReply(
            CommentReplyDto dto,
            String userId){

        int result = commentMapper.updateReply(
                dto.getReplyId(),
                dto.getReplyText(),
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 답글을 수정할 수 있습니다.");
        }

        CommentDto comment =
                commentMapper.getComment(
                        commentMapper.getCommentIdByReplyId(
                                dto.getReplyId()));

        Long pdfId =
                comment == null
                ? null
                : comment.getPdfId();
        String commentCode =
                comment == null
                ? null
                : comment.getCommentCode();

        afterCommit(() ->
                slackMentionService.sendMentionNotification(
                        dto.getReplyText(),
                        pdfId,
                        commentCode));
        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;
    }

    /** 작성자 본인의 답글만 삭제한다. */
    @Transactional
    public int deleteReply(
            Long replyId,
            String userId){

        Long pdfId =
                commentMapper.getPdfIdByReplyId(replyId);

        int result = commentMapper.deleteReply(
                replyId,
                userId);

        if(result == 0){
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "작성자만 답글을 삭제할 수 있습니다.");
        }

        attachmentService.deleteForReply(replyId);

        afterCommit(() ->
                collaborationHandler.broadcastCommentChanged(
                        pdfId));

        return result;
    }

    public CommentDto getComment(Long commentId){
        return commentMapper.getComment(commentId);
    }

    private void attachReplyAttachments(
            List<CommentReplyDto> replies) {

        if(replies.isEmpty()){
            return;
        }

        List<Long> replyIds = replies.stream()
                .map(CommentReplyDto::getReplyId)
                .toList();
        Map<Long,List<CommentAttachmentDto>> attachmentsByReply =
                commentMapper
                .getAttachmentListByReplyIds(replyIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CommentAttachmentDto::getReplyId));

        replies.forEach(reply ->
                reply.setAttachments(
                        attachmentsByReply.getOrDefault(
                                reply.getReplyId(),
                                List.of())));
    }

    /** 답글 변경 후 어느 PDF 방에 WebSocket 신호를 보낼지 찾을 때 사용한다. */
    public Long getPdfIdByReplyId(Long replyId){
        return commentMapper.getPdfIdByReplyId(replyId);
    }

    private void afterCommit(Runnable action) {

        // 트랜잭션 안에서 호출됐다면 DB 커밋 이후 실행한다.
        // 테스트나 트랜잭션 밖 호출이면 즉시 실행해 동일한 후속 동작을 유지한다.
        if(TransactionSynchronizationManager.isSynchronizationActive()){
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            runSafely(action);
                        }
                    });
            return;
        }

        runSafely(action);
    }

    private void runSafely(Runnable action) {

        // Slack/WebSocket 장애가 이미 완료된 댓글 저장 결과를 되돌리지는 않게 한다.
        try{
            action.run();
        }catch(RuntimeException error){
            log.warn(
                    "Post-commit comment notification failed",
                    error);
        }
    }

}
