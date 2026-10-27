package maoomWeb.ire.user.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.sts.dita.core.ValidationRequest;
import com.sts.dita.core.ValidationResult;
import com.sts.dita.core.ValidationScope;

/**
 * 운영 크기 DITA로 검사 시간과 메모리 사용량을 재현하는 수동 성능 시험이다.
 * 평소 전체 테스트에서는 건너뛰며 dita.performance.* 속성을 명시한 실행에서만 동작한다.
 */
class DitaValidationOperationalPerformanceTest {
    @Test
    void validatesAtLeastSevenHundredFilesAndOneThousandIssues() throws Exception {
        String mapValue = System.getProperty("dita.performance.map", "");
        String schemaValue = System.getProperty("dita.performance.schema", "");
        Assumptions.assumeTrue(!mapValue.isBlank() && !schemaValue.isBlank(),
                "운영 성능 시험 경로가 지정되지 않았습니다.");
        Path map = Path.of(mapValue).toAbsolutePath().normalize();
        Path schema = Path.of(schemaValue).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(map), "성능 시험 ditamap이 없습니다: " + map);
        assertTrue(Files.isRegularFile(schema), "성능 시험 Schematron이 없습니다: " + schema);

        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        long started = System.nanoTime();
        ValidationResult result = new DitaSchematronService().validate(new ValidationRequest(
                map, schema, ValidationScope.ALL, Set.of(), true, map.getParent()));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        System.out.printf("DITA_OPERATIONAL_PERFORMANCE files=%d issues=%d elapsedMs=%d memoryDeltaMb=%.1f%n",
                result.validatedFileCount(), result.issues().size(), elapsedMs,
                (memoryAfter - memoryBefore) / 1024d / 1024d);
        assertTrue(result.validatedFileCount() >= 700,
                "700개 이상 파일 프로젝트가 아닙니다: " + result.validatedFileCount());
        assertTrue(result.issues().size() >= 1000,
                "오류가 1,000건 이상인 시험 데이터가 아닙니다: " + result.issues().size());
    }
}
