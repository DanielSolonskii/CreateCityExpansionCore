package com.example.examplemod.city;

import java.util.List;

/** Pages bound packet size, never the total number of discoverable cities. */
public final class CityDirectoryPager {
    public static final int PAGE_SIZE = 32;
    private CityDirectoryPager() {}
    public static <T> List<T> page(List<T> values, int offset) {
        if (offset < 0 || offset > values.size()) throw new IllegalArgumentException("Invalid directory offset");
        return List.copyOf(values.subList(offset, (int) Math.min(values.size(), (long) offset + PAGE_SIZE)));
    }
}
