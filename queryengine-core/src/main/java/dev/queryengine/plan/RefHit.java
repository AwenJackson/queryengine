package dev.queryengine.plan;

/** 引用查找结果。{@code found == false} 表示路径不存在；值为 null 表示目标存在且是 JSON null。 */
public record RefHit(boolean found, Object value) {

    public static RefHit found(Object value) {
        return new RefHit(true, value);
    }

    public static RefHit missing() {
        return new RefHit(false, null);
    }
}
