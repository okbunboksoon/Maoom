package maoomWeb.ire.user.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import maoomWeb.ire.user.dto.CommentDto;
import maoomWeb.ire.user.dto.CommentAttachmentDto;
import maoomWeb.ire.user.dto.CommentReplyDto;

@Mapper
/**
 * 댓글과 답글 데이터를 조회하고 변경하는 MyBatis 매퍼.
 * 실제 SQL은 resources/mapper/user/CommentMapper.xml에 정의한다.
 */
public interface CommentMapper {

    List<CommentDto> getCommentList(
            @Param("pdfId") Long pdfId,
            @Param("status") String status);

    CommentDto getComment(Long commentId);

    Long lockPdf(Long pdfId);

    int getNextCommentNumber(Long pdfId);

    int addComment(CommentDto commentDto);

    int addAttachment(CommentAttachmentDto attachment);

    List<CommentAttachmentDto> getAttachmentList(Long commentId);

    List<CommentAttachmentDto> getAllAttachmentList(Long commentId);

    List<CommentAttachmentDto> getReplyAttachmentList(Long replyId);

    List<CommentAttachmentDto> getAttachmentListByCommentIds(
            @Param("commentIds") List<Long> commentIds);

    List<CommentAttachmentDto> getAttachmentListByReplyIds(
            @Param("replyIds") List<Long> replyIds);

    CommentAttachmentDto getAttachment(Long attachmentId);

    int deleteAttachmentsByCommentId(Long commentId);

    int deleteAttachmentsByReplyId(Long replyId);

    int updateComment(
            @Param("commentId") Long commentId,
            @Param("commentText") String commentText,
            @Param("userId") String userId);

    int updateCommentGeometry(
            @Param("commentId") Long commentId,
            @Param("rectX") BigDecimal rectX,
            @Param("rectY") BigDecimal rectY,
            @Param("rectW") BigDecimal rectW,
            @Param("rectH") BigDecimal rectH,
            @Param("userId") String userId);

    int updateCommentStatus(
            @Param("commentId") Long commentId,
            @Param("status") String status,
            @Param("userId") String userId);

    int deleteComment(
            @Param("commentId") Long commentId,
            @Param("userId") String userId);

    int deleteRepliesByCommentId(
            @Param("commentId") Long commentId,
            @Param("userId") String userId);
    
    List<CommentReplyDto> getReplyList(Long commentId);

    CommentReplyDto getReply(Long replyId);

    List<CommentReplyDto> getReplyListByCommentIds(
            @Param("commentIds") List<Long> commentIds);

    int addReply(CommentReplyDto dto);

    int updateReply(
            @Param("replyId") Long replyId,
            @Param("replyText") String replyText,
            @Param("userId") String userId);

    int deleteReply(
            @Param("replyId") Long replyId,
            @Param("userId") String userId);

    Long getPdfIdByReplyId(Long replyId);

    Long getCommentIdByReplyId(Long replyId);

}
