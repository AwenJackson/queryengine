package dev.queryengine;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RepositoryGuardTest {

    @Test
    void mainSourcesDoNotDeclareHardCapsOrForeignEngine() throws Exception {
        Path root = repositoryRoot();
        List<String> banned = List.of(
                "MAX_QUERY_DEPTH",
                "MAX_OBJECT_COUNT",
                "MAX_ARRAY_COUNT",
                "MAX_SQL_COUNT",
                "MAX_UPDATE_COUNT",
                "MAX_QUERY_COUNT"
        );
        try (var walk = Files.walk(root)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.equals("pom.xml")) {
                    String pom = Files.readString(file).replaceAll("(?s)<excludes>.*?</excludes>", "");
                    assertFalse(pom.toLowerCase().contains("apijson"), file.toString());
                }
                if (!file.toString().contains("/src/main/java/") || !name.endsWith(".java")) {
                    continue;
                }
                String source = Files.readString(file);
                for (String token : banned) {
                    assertFalse(source.contains(token), file + " 包含 " + token);
                }
            }
        }
    }

    private static Path repositoryRoot() throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        if (dir.getParent() != null && Files.exists(dir.getParent().resolve("pom.xml"))) {
            String parent = Files.readString(dir.getParent().resolve("pom.xml"));
            if (parent.contains("<modules>")) {
                return dir.getParent();
            }
        }
        return dir;
    }
}
