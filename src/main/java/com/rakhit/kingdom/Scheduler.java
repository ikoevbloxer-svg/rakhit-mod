package com.rakhit.kingdom;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Мини-планировщик отложенных задач (в тиках сервера). */
public final class Scheduler {
    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private Scheduler() {}

    public static void later(MinecraftServer s, int ticks, Runnable r) {
        TASKS.add(new Task(s.getTickCount() + ticks, r));
    }

    public static void tick(MinecraftServer s) {
        if (TASKS.isEmpty()) return;
        long now = s.getTickCount();
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.at <= now) { due.add(t); return true; }
            return false;
        });
        for (Task t : due) t.run.run();
    }

    public static void clear() { TASKS.clear(); }
}
