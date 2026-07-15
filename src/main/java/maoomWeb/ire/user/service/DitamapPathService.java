package maoomWeb.ire.user.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * DITAMAP Builder가 다루는 로컬/네트워크 경로의 보안 경계를 담당한다.
 *
 * <p>화면에서 넘어오는 경로는 사용자가 직접 입력하거나 브라우저 상태에서 온 값이라
 * 그대로 믿지 않는다. 이 서비스는 허용 루트 안의 .ditamap만 찾고, 상대 경로를 다시
 * 허용 루트 안의 실제 파일로 되돌리며, Windows mapped drive(V:, H: 등)를 UNC 경로와
 * 비교할 수 있게 정규화한다.</p>
 */
@Service
public class DitamapPathService {

    private final List<Path> allowedRoots;
    private final Map<String, String> mappedDriveCache =
            new ConcurrentHashMap<>();

    public DitamapPathService(
            @Value("${ditamap.builder.allowed-roots:}") String configuredRoots) {
        this.allowedRoots = createAllowedRoots(configuredRoots);
    }

    /**
     * 화면에서 받은 파일/폴더 경로를 기준 .ditamap 파일로 확정한다.
     * 폴더가 들어오면 이름순으로 첫 번째 .ditamap을 사용한다.
     */
    Path findDitamap(String rawPath) throws IOException {
        Path path = Path.of(rawPath.trim())
                .toAbsolutePath()
                .normalize();

        if(!Files.exists(path)){
            throw new IllegalArgumentException(
                    "경로가 존재하지 않습니다: " + path);
        }

        Path realPath = path.toRealPath();
        Path realAllowedRoot = findAllowedRoot(realPath);

        if(!isSameOrChildPath(realPath, realAllowedRoot)){
            throw new IllegalArgumentException(
                    "허용된 DITA 작업 경로는 "
                    + realAllowedRoot
                    + " 아래입니다.");
        }

        if(Files.isRegularFile(realPath)){
            if(isDitamap(realPath)){
                return realPath;
            }

            throw new IllegalArgumentException(
                    "DITAMAP 파일이 아닙니다: "
                    + realPath.getFileName());
        }

        try(var stream = Files.list(realPath)){
            return stream
                    .filter(Files::isRegularFile)
                    .filter(this::isDitamap)
                    .sorted(Comparator.comparing(
                            candidate -> candidate.getFileName()
                                    .toString()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "해당 폴더에서 .ditamap 파일을 찾지 못했습니다."));
        }
    }

    /** 실제 경로가 설정된 허용 루트 중 하나에 속하는지 확인하고 해당 루트를 반환한다. */
    Path findAllowedRoot(Path path) throws IOException {
        Path realPath = path.toRealPath();

        if(allowedRoots.isEmpty()){
            throw new IllegalArgumentException(describeAllowedRoots());
        }

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path realAllowedRoot = allowedRoot.toRealPath();

            if(isSameOrChildPath(realPath, realAllowedRoot)){
                return realAllowedRoot;
            }
        }

        throw new IllegalArgumentException(
                "허용된 DITA 작업 경로는 "
                + describeAllowedRoots()
                + " 아래입니다.");
    }

    boolean isUnderAllowedRoot(Path path) throws IOException {
        try{
            findAllowedRoot(path);
            return true;
        }catch(IllegalArgumentException exception){
            return false;
        }
    }

    Path resolveAllowedRelativePath(String relativePath)
            throws IOException {
        if(relativePath == null || relativePath.isBlank()){
            throw new IllegalArgumentException(
                    "수정할 파일 정보가 없습니다.");
        }

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path target = allowedRoot.resolve(relativePath)
                    .normalize();

            if(Files.exists(target)
                    && isSameOrChildPath(
                            target.toRealPath(),
                            allowedRoot.toRealPath())){
                return target;
            }
        }

        throw new IllegalArgumentException(
                "허용된 작업 경로 안에서 수정할 파일을 찾지 못했습니다: "
                + relativePath);
    }

    String toAllowedRelativePath(Path target)
            throws IOException {
        Path realTarget = target.toRealPath();

        for(Path allowedRoot : allowedRoots){
            if(!Files.exists(allowedRoot)){
                continue;
            }

            Path realAllowedRoot = allowedRoot.toRealPath();

            if(!isSameOrChildPath(realTarget, realAllowedRoot)){
                continue;
            }

            if(realTarget.startsWith(realAllowedRoot)){
                return realAllowedRoot.relativize(realTarget)
                        .toString();
            }

            String targetText = toComparablePathText(realTarget);
            String rootText = toComparablePathText(realAllowedRoot);

            if(targetText.equals(rootText)){
                return "";
            }

            return targetText.substring(rootText.length() + 1)
                    .replace('/', '\\');
        }

        throw new IllegalArgumentException(
                "허용된 작업 경로 안에서 상대 경로를 만들 수 없습니다: "
                + target);
    }

    boolean isSameOrChildPath(
            Path path,
            Path root)
            throws IOException {
        return isSameOrChildPath(
                path.toRealPath().toString(),
                root.toRealPath().toString());
    }

    boolean isSameOrChildPath(
            String path,
            String root) {
        String pathText = normalizeComparablePath(
                convertMappedDrivePathToUnc(path));
        String rootText = normalizeComparablePath(
                convertMappedDrivePathToUnc(root));

        return pathText.equals(rootText)
                || pathText.startsWith(rootText + "\\");
    }

    /**
     * 오른쪽 법규 DITAMAP 영역의 폴더열기 버튼에서 사용자가 DITA 경로 폴더를
     * 바로 확인할 수 있도록 탐색기를 연다.
     */
    public void openFolder(String rawPath) {
        if(rawPath == null || rawPath.isBlank()){
            throw new IllegalArgumentException(
                    "열 폴더 경로가 없습니다.");
        }

        try{
            Path path = Path.of(rawPath.trim())
                    .toAbsolutePath()
                    .normalize();

            if(!Files.exists(path)){
                throw new IllegalArgumentException(
                        "경로가 존재하지 않습니다: " + path);
            }

            Path realPath = path.toRealPath();
            findAllowedRoot(realPath);
            Path folder = Files.isRegularFile(realPath)
                    ? realPath.getParent()
                    : realPath;

            if(folder == null || !Files.isDirectory(folder)){
                throw new IllegalArgumentException(
                        "열 수 있는 폴더가 아닙니다: " + realPath);
            }

            new ProcessBuilder(
                    "explorer.exe",
                    "/n,/e," + folder)
                    .start();
        }catch(IOException exception){
            throw new IllegalArgumentException(
                    "폴더를 열지 못했습니다. " + exception.getMessage(),
                    exception);
        }
    }

    private List<Path> createAllowedRoots(String configuredRoots) {
        List<Path> roots = new ArrayList<>();

        if(configuredRoots != null && !configuredRoots.isBlank()){
            for(String rawRoot : configuredRoots.split(";")){
                String root = rawRoot.trim();

                if(root.isBlank()){
                    continue;
                }

                roots.add(Path.of(expandPathTokens(root))
                        .toAbsolutePath()
                        .normalize());
            }
        }

        return roots.stream()
                .distinct()
                .toList();
    }

    private String expandPathTokens(String path) {
        return path.replace(
                "${user.home}",
                System.getProperty("user.home"));
    }

    private String describeAllowedRoots() {
        if(allowedRoots.isEmpty()){
            return "설정된 작업 루트가 없습니다. ditamap.builder.allowed-roots를 설정해 주세요.";
        }

        return allowedRoots.stream()
                .map(Path::toString)
                .reduce((left, right) -> left + ", " + right)
                .orElse("(없음)");
    }

    private String toComparablePathText(Path path)
            throws IOException {
        String text = path.toRealPath()
                .toString();
        return normalizeComparablePath(convertMappedDrivePathToUnc(text));
    }

    private String normalizeComparablePath(String path) {
        String normalized = path.replace('/', '\\');

        while(normalized.endsWith("\\") && normalized.length() > 3){
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized.toLowerCase(Locale.ROOT);
    }

    private String convertMappedDrivePathToUnc(String path) {
        if(path.length() < 3
                || path.charAt(1) != ':'
                || path.charAt(2) != '\\'){
            return path;
        }

        String drive = path.substring(0, 2)
                .toUpperCase(Locale.ROOT);
        String remote = mappedDriveCache.computeIfAbsent(
                drive,
                this::readMappedDriveRemote);

        if(remote.isBlank()){
            return path;
        }

        return remote + path.substring(2);
    }

    private String readMappedDriveRemote(String drive) {
        try{
            Process process = new ProcessBuilder(
                    "cmd",
                    "/c",
                    "net",
                    "use",
                    drive)
                    .redirectErrorStream(true)
                    .start();

            if(!process.waitFor(3, TimeUnit.SECONDS)){
                process.destroyForcibly();
                return "";
            }

            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            int uncStart = output.indexOf("\\\\");

            if(uncStart < 0){
                return "";
            }

            int uncEnd = uncStart;

            while(uncEnd < output.length()
                    && !Character.isWhitespace(output.charAt(uncEnd))){
                uncEnd++;
            }

            return output.substring(uncStart, uncEnd)
                    .replace('/', '\\');
        }catch(IOException | InterruptedException exception){
            if(exception instanceof InterruptedException){
                Thread.currentThread().interrupt();
            }

            return "";
        }
    }

    boolean isDitamap(Path path) {
        return path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT)
                .endsWith(".ditamap");
    }

    Map<String, String> mappedDriveCache() {
        return mappedDriveCache;
    }
}
