package maoomWeb.ire.user.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;
import maoomWeb.ire.user.mapper.CommentMapper;
import maoomWeb.ire.user.websocket.PdfCollaborationHandler;

@Service
/**
 * 댓글과 답글의 비즈니스 규칙을 처리한다.
 * DB 변경 후 Slack 알림과 WebSocket 갱신 이벤트를 연계한다.
 */
public class CommentService {

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

    /** 상태 조건에 맞는 PDF 댓글 목록을 조회한다. */
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
    
    /** 댓글과 업무용 번호를 저장한 뒤 멘션 알림과 실시간 갱신 이벤트를 전송한다. */
    @Transactional
    public Long addComment(CommentDto commentDto){

        commentMapper.lockPdf(commentDto.getPdfId());
        int commentNumber =
                commentMapper.getNextCommentNumber(
                        commentDto.getPdfId());
        commentDto.setCommentCode(
                String.format("id%04d", commentNumber));

        commentMapper.addComment(commentDto);

        slackMentionService.sendMentionNotification(
                commentDto.getCommentText(),
                commentDto.getPdfId(),
                commentDto.getCommentCode());

        collaborationHandler.broadcastCommentChanged(
                commentDto.getPdfId());

        return commentDto.getCommentId();

    }

    /** 작성자 본인의 댓글만 수정하고 관련 사용자에게 변경을 알린다. */
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

        slackMentionService.sendMentionNotification(
                commentDto.getCommentText(),
                savedComment == null
                        ? null
                        : savedComment.getPdfId(),
                savedComment == null
                        ? null
                        : savedComment.getCommentCode());

        collaborationHandler.broadcastCommentChanged(
                savedComment == null
                        ? null
                        : savedComment.getPdfId());

        return result;

    }

    /** 작성자 본인의 사각형 또는 말풍선 좌표를 변경하고 협업 사용자에게 알린다. */
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

        collaborationHandler.broadcastCommentChanged(
                savedComment == null
                        ? null
                        : savedComment.getPdfId());

        return result;
    }

    @Transactional
    /** 댓글과 소속 답글을 하나의 트랜잭션으로 삭제한다. */
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

        collaborationHandler.broadcastCommentChanged(
                savedComment == null
                        ? null
                        : savedComment.getPdfId());

        return result;

    }

    /** 허용된 상태값인지 확인한 후 작성자 본인의 댓글 상태를 변경한다. */
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

        collaborationHandler.broadcastCommentChanged(
                savedComment == null
                        ? null
                        : savedComment.getPdfId());

        return result;

    }
    
    public List<CommentReplyDto> getReplyList(Long commentId){
        List<CommentReplyDto> replies =
                commentMapper.getReplyList(commentId);
        attachReplyAttachments(replies);
        return replies;
    }

    /** 답글을 저장하고 원 댓글이 속한 PDF에 변경 이벤트를 전송한다. */
    public Long addReply(CommentReplyDto dto){

        commentMapper.addReply(dto);

        CommentDto comment =
                commentMapper.getComment(
                        dto.getCommentId());

        slackMentionService.sendMentionNotification(
                dto.getReplyText(),
                comment == null
                        ? null
                        : comment.getPdfId(),
                comment == null
                        ? null
                        : comment.getCommentCode());

        collaborationHandler.broadcastCommentChanged(
                comment == null
                        ? null
                        : comment.getPdfId());

        return dto.getReplyId();
    }

    /** 작성자 본인의 답글만 수정한다. */
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

        slackMentionService.sendMentionNotification(
                dto.getReplyText(),
                comment == null
                        ? null
                        : comment.getPdfId(),
                comment == null
                        ? null
                        : comment.getCommentCode());

        collaborationHandler.broadcastCommentChanged(
                commentMapper.getPdfIdByReplyId(
                        dto.getReplyId()));

        return result;
    }

    /** 작성자 본인의 답글만 삭제한다. */
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

        collaborationHandler.broadcastCommentChanged(
                pdfId);

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

    public Long getPdfIdByReplyId(Long replyId){
        return commentMapper.getPdfIdByReplyId(replyId);
    }

}
