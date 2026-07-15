package maoomWeb.ire.user.service;

import java.util.List;
import java.util.Set;

import maoomWeb.ire.user.dto.RevisionOptionDto;

/**
 * DITA 정제 화면에 노출되는 옵션과 입력/출력 조합별 배치 실행 순서를 정의한다.
 *
 * <p>RevisionPipelineService는 파일 복사, 작업 폴더 준비, Saxon 실행만 담당하고,
 * "어떤 옵션이 유효한가"와 "어떤 배치 파일을 어떤 순서로 실행하는가"는 이 클래스가
 * 한곳에서 관리한다.</p>
 */
final class RevisionPipelineCatalog {

    static final String FILE_NAME_KEEP = "FILE_NAME_KEEP";
    static final String REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET =
            "REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET";
    static final String DELETE_DRAFT_COMMENT =
            "DELETE_DRAFT_COMMENT";

    private RevisionPipelineCatalog() {
    }

    static List<RevisionOptionDto> options() {
        return List.of(
                new RevisionOptionDto(
                        FILE_NAME_KEEP,
                        "파일명 유지",
                        "Chapter 변환 시 파일명 유지 배치를 실행합니다."),
                new RevisionOptionDto(
                        REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET,
                        "속성 및 세션 지우기",
                        "deliveryTarget 속성과 Simple operation 섹션을 제거합니다."),
                new RevisionOptionDto(
                        DELETE_DRAFT_COMMENT,
                        "Draft Comment 지우기",
                        "draft-comment 태그를 제거합니다."));
    }

    static Set<String> validateOptions(List<String> requestedOptions) {
        if(requestedOptions == null || requestedOptions.isEmpty()){
            return Set.of();
        }

        Set<String> available = Set.of(
                FILE_NAME_KEEP,
                REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET,
                DELETE_DRAFT_COMMENT);

        for(String option : requestedOptions){
            if(!available.contains(option)){
                throw new IllegalArgumentException(
                        "알 수 없는 정제 옵션입니다: " + option);
            }
        }

        return Set.copyOf(requestedOptions);
    }

    static BatchPlan createBatchPlan(
            RevisionFormat inputType,
            RevisionFormat outputType,
            Set<String> selectedOptions) {

        String chapterizeBatch = selectedOptions.contains(FILE_NAME_KEEP)
                ? "02_topics_Chapterize.bat"
                : "02_topics_Chapterize_NotFileNameChange.bat";

        if(inputType == RevisionFormat.XML
                && outputType == RevisionFormat.DITA){
            if(hasCleanupOption(selectedOptions)){
                return new BatchPlan(List.of(
                        "03_chapter_Topicalize.bat",
                        chapterizeBatch,
                        "03_chapter_Topicalize.bat"));
            }

            return new BatchPlan(List.of(
                    "03_chapter_Topicalize.bat"));
        }

        if(inputType == RevisionFormat.XML
                && outputType == RevisionFormat.XML){
            return new BatchPlan(List.of(
                    "03_chapter_Topicalize.bat",
                    chapterizeBatch));
        }

        if(inputType == RevisionFormat.DITA
                && outputType == RevisionFormat.XML){
            return new BatchPlan(List.of(chapterizeBatch));
        }

        return new BatchPlan(List.of(
                chapterizeBatch,
                "03_chapter_Topicalize.bat"));
    }

    private static boolean hasCleanupOption(Set<String> selectedOptions) {
        return selectedOptions.contains(REMOVE_SIMPLE_OPERATION_DELIVERY_TARGET)
                || selectedOptions.contains(DELETE_DRAFT_COMMENT);
    }

    record BatchPlan(List<String> batchFiles) {

        String lastBatchFile() {
            return batchFiles.isEmpty()
                    ? "없음"
                    : batchFiles.get(batchFiles.size() - 1);
        }
    }
}
