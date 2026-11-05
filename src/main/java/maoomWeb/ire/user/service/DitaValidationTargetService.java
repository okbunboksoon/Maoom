package maoomWeb.ire.user.service;

import static maoomWeb.ire.user.dto.DitaValidationDtos.*;

import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.sts.dita.core.MapModel;
import com.sts.dita.core.MapNode;
import maoomWeb.ire.user.config.DitaValidationProperties;

/** 검사 입력 경로, DITAMAP 목록과 트리 생성을 담당한다. */
@Service
public class DitaValidationTargetService {
    private final DitaSchematronService validationService;
    private final Path gomsOutputRoot;

    @Autowired
    public DitaValidationTargetService(DitaSchematronService validationService,
            DitaValidationProperties properties) {
        this(validationService, Path.of(properties.getGomsOutputRoot()));
    }

    DitaValidationTargetService(DitaSchematronService validationService, Path gomsOutputRoot) {
        this.validationService = validationService;
        this.gomsOutputRoot = gomsOutputRoot.toAbsolutePath().normalize();
    }

    public List<MapItem> findMaps(String inputPath) throws Exception {
        Path root = validDirectory(inputPath);
        int maxDepth = root.startsWith(gomsOutputRoot) && !root.equals(gomsOutputRoot) ? 2 : Integer.MAX_VALUE;
        return walkProjectEntries(root, maxDepth).stream()
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ditamap"))
                .sorted(Comparator.comparing(Path::toString))
                .map(path -> new MapItem(path.getFileName().toString(), path.toAbsolutePath().normalize().toString()))
                .toList();
    }

    public MapTree loadTree(String mapPath) throws Exception {
        Path map = validMap(mapPath, null);
        MapModel model = validationService.loadMap(map);
        return new MapTree(map.toString(), model.roots().stream().map(this::treeNode).toList());
    }

    Path validDirectory(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("입력 경로를 입력해 주세요.");
        Path path = Path.of(value.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) throw new IllegalArgumentException("접근할 수 있는 입력 폴더가 아닙니다.");
        return path;
    }

    Path validMap(String value, Path root) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("ditamap을 선택해 주세요.");
        Path map = Path.of(value).toAbsolutePath().normalize();
        if ((root != null && !map.startsWith(root)) || !Files.isRegularFile(map)
                || !map.getFileName().toString().toLowerCase().endsWith(".ditamap")) {
            throw new IllegalArgumentException("올바른 ditamap 파일이 아닙니다.");
        }
        return map;
    }

    static List<Path> walkProjectEntries(Path root) throws java.io.IOException {
        return walkProjectEntries(root, Integer.MAX_VALUE);
    }

    static List<Path> walkProjectEntries(Path root, int maxDepth) throws java.io.IOException {
        List<Path> entries = new ArrayList<>();
        Files.walkFileTree(root, Set.of(), maxDepth, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (!directory.equals(root)) {
                    String name = directory.getFileName().toString();
                    if ("old".equalsIgnoreCase(name) || "report".equalsIgnoreCase(name)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                }
                entries.add(directory);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                entries.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return entries;
    }

    private TreeNode treeNode(MapNode node) {
        return new TreeNode(node.id(), node.title(), node.href(),
                node.children().stream().map(this::treeNode).toList());
    }
}
