package maoomWeb.ire.user.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import maoomWeb.ire.user.dto.BerAsisTobePair;
import maoomWeb.ire.user.dto.BerAsisTobeSentenceImportResult;
import maoomWeb.ire.user.mapper.BerAsisTobePairMapper;

@Service
public class BerAsisTobeSentenceImportService {

    private static final String BATCH_FILE =
            "02_asis-tobe-make-Build.bat";
    private static final Duration BATCH_TIMEOUT = Duration.ofMinutes(30);
    private static final Charset BATCH_OUTPUT_CHARSET =
            Charset.forName("MS949");
    private static final DateTimeFormatter RUN_ID_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String[] RESOURCE_ROOTS = {
            "bat",
            "xsl",
            "lib"
    };
    private static final String[] REQUIRED_FILES = {
            BATCH_FILE,
            "lib/saxon-ee-10.0.jar",
            "lib/xml-resolver-1.2.jar",
            "xsl/0100-excel-to-xml-update.xsl",
            "xsl/0000-doctype-remove.xsl",
            "xsl/0001-namespace-remove.xsl",
            "xsl/0002-toc-create.xsl",
            "xsl/0003-bookmap-create.xsl",
            "xsl/0004-topic-merge.xsl",
            "xsl/0260-kus-1st-group.xsl",
            "xsl/0270-kus-2nd-group.xsl",
            "xsl/0280-kus-3rd-group.xsl",
            "xsl/0290-kus-text-normalize.xsl",
            "xsl/0300-kus-inline-normalize.xsl",
            "xsl/0310-kus-beautify.xsl",
            "xsl/0320-kus-pair-extract_ber.xsl",
            "xsl/0330-kus-db-make_ber.xsl",
            "xsl/0420-kus-db-clean_ber.xsl",
            "xsl/0430-kus-db-update_ber.xsl",
            "xsl/asis-tobe_eu.xml",
            "xsl/asis-tobe_us.xml",
            "xsl/asis-tobe_exclude.xml"
    };

    private final PathMatchingResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();
    private final BerAsisTobePairMapper pairMapper;
    private final BerAsisTobeXmlService xmlService;
    private final BerAsisTobeSeedService seedService;
    private final JdbcTemplate jdbcTemplate;
    private final ProjectExecutionLogService logService;

    public BerAsisTobeSentenceImportService(
            BerAsisTobePairMapper pairMapper,
            BerAsisTobeXmlService xmlService,
            BerAsisTobeSeedService seedService,
            JdbcTemplate jdbcTemplate,
            ProjectExecutionLogService logService) {
        this.pairMapper = pairMapper;
        this.xmlService = xmlService;
        this.seedService = seedService;
        this.jdbcTemplate = jdbcTemplate;
        this.logService = logService;
    }

    public BerAsisTobeSentenceImportResult importSentenceExcel(
            String region,
            MultipartFile file,
            String userId) {

        String normalizedRegion = normalizeRegion(region);
        validateFile(file);

        Path workDirectory = createWorkDirectory(normalizedRegion, userId);
        Long logId = logService.start(
                "BER_ASIS_TOBE_IMPORT",
                "As-is/To-be 엑셀 반영",
                userId,
                file.getOriginalFilename(),
                normalizedRegion + " 배치 실행 준비");

        try{
            prepareWorkDirectory(workDirectory);
            xmlService.writeRegionXmlFiles(workDirectory.resolve("xsl"));
            Files.createDirectories(workDirectory.resolve("temp"));
            Files.createDirectories(workDirectory.resolve("topics"));
            Path uploadFile = workDirectory.resolve("temp")
                    .resolve("excel.xml");
            try(InputStream input = file.getInputStream()){
                Files.copy(
                        input,
                        uploadFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }

            runBatch(workDirectory, normalizedRegion);

            Path resultXml = resultXml(workDirectory, normalizedRegion);
            if(!Files.isRegularFile(resultXml)){
                throw new IllegalArgumentException(
                        "배치 결과 XML을 찾지 못했습니다: " + resultXml);
            }

            List<BerAsisTobePair> pairs;
            try(InputStream input = Files.newInputStream(resultXml)){
                pairs = seedService.readPairs(normalizedRegion, input);
            }
            if(pairs.isEmpty()){
                throw new IllegalArgumentException(
                        "배치 결과 XML에 반영할 BER 데이터가 없습니다.");
            }

            ensureBackupTables();
            Long backupId = createBackup(
                    normalizedRegion,
                    file.getOriginalFilename(),
                    workDirectory,
                    userId);
            ImportCounts counts = updateDatabase(normalizedRegion, pairs);
            String message = "전체 " + counts.totalRows()
                    + "건, 신규 " + counts.insertedCount()
                    + "건, 수정 " + counts.updatedCount()
                    + "건, 변경 없음 " + counts.unchangedCount()
                    + "건, 제외 " + counts.skippedCount() + "건";
            logService.success(logId, resultXml.toString(), message);

            return new BerAsisTobeSentenceImportResult(
                    backupId,
                    workDirectory.toString(),
                    counts.totalRows(),
                    counts.insertedCount(),
                    counts.updatedCount(),
                    counts.unchangedCount(),
                    counts.skippedCount());
        }catch(Exception exception){
            logService.fail(logId, exception);
            if(exception instanceof RuntimeException runtimeException){
                throw runtimeException;
            }
            throw new IllegalArgumentException(
                    "As-is/To-be 엑셀 반영 중 오류가 발생했습니다: "
                    + exception.getMessage(),
                    exception);
        }
    }

    private String normalizeRegion(String region) {
        String normalized = region == null
                ? ""
                : region.trim().toUpperCase(Locale.ROOT);

        if(!normalized.equals("EU") && !normalized.equals("US")){
            throw new IllegalArgumentException(
                    "region은 EU 또는 US만 사용할 수 있습니다.");
        }

        return normalized;
    }

    private void validateFile(MultipartFile file) {
        if(file == null || file.isEmpty()){
            throw new IllegalArgumentException(
                    "As-is/To-be 엑셀 XML 파일을 선택해 주세요.");
        }

        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if(!name.endsWith(".xml")){
            throw new IllegalArgumentException(
                    "현재 As-is/To-be 반영은 Excel XML(.xml) 파일만 지원합니다.");
        }
    }

    private Path createWorkDirectory(String region, String userId) {
        String safeUser = userId == null || userId.isBlank()
                ? "unknown"
                : userId.replaceAll("[^a-zA-Z0-9._-]", "_");

        return Path.of(
                System.getProperty("user.home"),
                ".maoomtool",
                "ber-asis-tobe-import-"
                        + region.toLowerCase(Locale.ROOT)
                        + "-"
                        + safeUser
                        + "-"
                        + RUN_ID_FORMAT.format(LocalDateTime.now()))
                .toAbsolutePath()
                .normalize();
    }

    private void prepareWorkDirectory(Path workDirectory) throws IOException {
        Files.createDirectories(workDirectory);

        for(String root : RESOURCE_ROOTS){
            copyResourceDirectory(root, root.equals("bat")
                    ? workDirectory
                    : workDirectory.resolve(root));
        }

        for(String requiredFile : REQUIRED_FILES){
            if(!Files.isRegularFile(workDirectory.resolve(requiredFile))){
                throw new IllegalArgumentException(
                        "As-is/To-be 배치 필수 파일을 찾지 못했습니다: "
                        + requiredFile);
            }
        }
    }

    private void copyResourceDirectory(String resourceRoot, Path target)
            throws IOException {
        Resource[] resources = resourceResolver.getResources(
                "classpath*:" + resourceRoot + "/**/*");

        for(Resource resource : resources){
            if(!resource.exists() || !resource.isReadable()){
                continue;
            }

            String url = resource.getURL().toString().replace('\\', '/');
            int index = url.lastIndexOf(resourceRoot + "/");
            if(index < 0){
                continue;
            }

            String relativePath = url.substring(
                    index + (resourceRoot + "/").length());
            if(relativePath.isBlank() || relativePath.endsWith("/")){
                continue;
            }

            Path destination = target.resolve(relativePath).normalize();
            if(!destination.startsWith(target.normalize())){
                throw new IllegalArgumentException(
                        "배치 리소스 경로가 올바르지 않습니다: "
                        + relativePath);
            }

            Files.createDirectories(destination.getParent());
            try(InputStream input = resource.getInputStream()){
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void runBatch(Path workDirectory, String region)
            throws IOException, InterruptedException {

        Path batchFile = workDirectory.resolve(BATCH_FILE);
        ProcessBuilder builder = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "call \""
                        + batchFile
                        + "\" "
                        + region);
        builder.directory(workDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put(
                "ROOT",
                workDirectory.toString() + "\\");

        Process process = builder.start();
        StringBuilder output = new StringBuilder();

        try(BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        process.getInputStream(),
                        BATCH_OUTPUT_CHARSET))){
            String line;
            while((line = reader.readLine()) != null){
                if(!line.isBlank()){
                    output.append(line).append(System.lineSeparator());
                }
            }
        }

        boolean finished = process.waitFor(
                BATCH_TIMEOUT.toMillis(),
                TimeUnit.MILLISECONDS);

        if(!finished){
            process.destroyForcibly();
            throw new IllegalArgumentException(
                    "As-is/To-be 배치 시간이 초과되었습니다.");
        }

        if(process.exitValue() != 0){
            throw new IllegalArgumentException(
                    "As-is/To-be 배치가 실패했습니다. "
                    + tail(output.toString()));
        }
    }

    private Path resultXml(Path workDirectory, String region) {
        return workDirectory.resolve("xsl")
                .resolve("asis-tobe_"
                        + region.toLowerCase(Locale.ROOT)
                        + ".xml");
    }

    private Long createBackup(
            String region,
            String sourceFileName,
            Path workDirectory,
            String userId) {

        int totalCount = pairMapper.countByRegion(region);
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tb_ber_asis_tobe_backup (
                        region,
                        source_file_name,
                        job_dir,
                        created_by,
                        total_count
                    ) VALUES (
                        ?,
                        ?,
                        ?,
                        ?,
                        ?
                    )
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, region);
            statement.setString(2, sourceFileName);
            statement.setString(3, workDirectory.toString());
            statement.setString(4, userId);
            statement.setInt(5, totalCount);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long backupId = key == null ? null : key.longValue();

        if(backupId != null){
            jdbcTemplate.update("""
                    INSERT INTO tb_ber_asis_tobe_backup_item (
                        backup_id,
                        region,
                        hash,
                        old_text,
                        new_text,
                        original_created_at,
                        original_updated_at
                    )
                    SELECT
                        ?,
                        region,
                        hash,
                        old_text,
                        new_text,
                        created_at,
                        updated_at
                    FROM tb_ber_asis_tobe_pair
                    WHERE region = ?
                    """,
                    backupId,
                    region);
        }

        return backupId;
    }

    private void ensureBackupTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tb_ber_asis_tobe_backup (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    region VARCHAR(10) NOT NULL,
                    source_file_name VARCHAR(255),
                    job_dir VARCHAR(1000),
                    created_by VARCHAR(100),
                    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    total_count INT NOT NULL DEFAULT 0,

                    PRIMARY KEY (id),
                    INDEX idx_ber_backup_region_created (region, created_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS tb_ber_asis_tobe_backup_item (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    backup_id BIGINT NOT NULL,
                    region VARCHAR(10) NOT NULL,
                    hash VARCHAR(128) NOT NULL,
                    old_text LONGTEXT,
                    new_text LONGTEXT NOT NULL,
                    original_created_at DATETIME(6),
                    original_updated_at DATETIME(6),

                    PRIMARY KEY (id),
                    INDEX idx_ber_backup_item_backup (backup_id),
                    CONSTRAINT fk_ber_backup_item_backup
                        FOREIGN KEY (backup_id)
                        REFERENCES tb_ber_asis_tobe_backup (id)
                        ON DELETE CASCADE
                )
                """);
    }

    private ImportCounts updateDatabase(
            String region,
            List<BerAsisTobePair> pairs) {

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        int skipped = 0;

        for(BerAsisTobePair pair : pairs){
            pair.setRegion(region);

            if(pair.getHash() == null
                    || pair.getHash().isBlank()
                    || pair.getNewText() == null
                    || pair.getNewText().isBlank()){
                skipped++;
                continue;
            }

            BerAsisTobePair oldValue =
                    pairMapper.findByRegionAndHash(region, pair.getHash());
            if(oldValue == null){
                pairMapper.upsert(pair);
                inserted++;
                continue;
            }

            if(equalsValue(oldValue, pair)){
                unchanged++;
                continue;
            }

            pairMapper.upsert(pair);
            updated++;
        }

        return new ImportCounts(
                pairs.size(),
                inserted,
                updated,
                unchanged,
                skipped);
    }

    private boolean equalsValue(
            BerAsisTobePair oldValue,
            BerAsisTobePair newValue) {

        return String.valueOf(oldValue.getOldText())
                .equals(String.valueOf(newValue.getOldText()))
                && String.valueOf(oldValue.getNewText())
                .equals(String.valueOf(newValue.getNewText()));
    }

    private String tail(String value) {
        if(value == null || value.isBlank()){
            return "";
        }

        String[] lines = value.strip().split("\\R");
        int from = Math.max(0, lines.length - 8);
        return String.join(" / ", java.util.Arrays.copyOfRange(
                lines,
                from,
                lines.length));
    }

    @SuppressWarnings("unused")
    private void deleteDirectory(Path directory) throws IOException {
        if(directory == null || !Files.exists(directory)){
            return;
        }

        try(Stream<Path> stream = Files.walk(directory)){
            for(Path path : stream.sorted(
                    java.util.Comparator.reverseOrder()).toList()){
                Files.deleteIfExists(path);
            }
        }
    }

    private record ImportCounts(
            int totalRows,
            int insertedCount,
            int updatedCount,
            int unchangedCount,
            int skippedCount) {
    }
}
