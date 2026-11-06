package dev.queryengine.plan;

/**
 * 引用路径的第一跳，用于同级依赖排序。
 * <p>绝对路径 {@code /User/id}、相对路径 {@code User/id}、当前元素 {@code []/User/id}
 * 的第一跳都是 {@code User}。
 */
public final class RefPaths {

    private RefPaths() {
    }

    public static String firstHop(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("/", -1);
        int index = 0;
        if (parts.length > 0 && (parts[0].isEmpty() || "[]".equals(parts[0]))) {
            index = 1;
        }
        if (index >= parts.length) {
            return null;
        }
        String hop = parts[index];
        if (hop.isEmpty() || "[]".equals(hop)) {
            return null;
        }
        return hop;
    }
}
