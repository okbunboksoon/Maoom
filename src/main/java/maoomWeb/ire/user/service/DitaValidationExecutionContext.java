package maoomWeb.ire.user.service;

import java.nio.file.Path;

/** 한 번의 DITA 검사에서 생성되고 정리되는 경로와 식별자를 보관한다. */
final class DitaValidationExecutionContext {
    private final Path sourceRoot;
    private final Path sourceMap;
    private final boolean gomsExecution;
    private final String progressToken;
    private Long historyId;
    private Path stagingFolder;
    private Path snapshot;

    DitaValidationExecutionContext(Path sourceRoot, Path sourceMap,
            boolean gomsExecution, String progressToken) {
        this.sourceRoot = sourceRoot;
        this.sourceMap = sourceMap;
        this.gomsExecution = gomsExecution;
        this.progressToken = progressToken;
    }

    Path sourceRoot() { return sourceRoot; }
    Path sourceMap() { return sourceMap; }
    boolean gomsExecution() { return gomsExecution; }
    String progressToken() { return progressToken; }
    Long historyId() { return historyId; }
    void historyId(Long value) { historyId = value; }
    Path stagingFolder() { return stagingFolder; }
    void stagingFolder(Path value) { stagingFolder = value; }
    Path snapshot() { return snapshot; }
    void snapshot(Path value) { snapshot = value; }
}
