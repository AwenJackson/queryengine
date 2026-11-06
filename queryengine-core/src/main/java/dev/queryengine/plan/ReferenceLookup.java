package dev.queryengine.plan;

@FunctionalInterface
public interface ReferenceLookup {

    RefHit lookup(String path);
}
