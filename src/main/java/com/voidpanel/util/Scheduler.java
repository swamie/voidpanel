package com.voidpanel.util;

import com.voidpanel.VoidPanel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs tasks on the server thread after a delay. Used to defer GUI opens out of click handlers. */
public final class Scheduler {
	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> PENDING = new ArrayList<>();

	private Scheduler() {}

	private static final class Task {
		int ticks;
		final Runnable run;

		Task(int ticks, Runnable run) {
			this.ticks = ticks;
			this.run = run;
		}
	}

	public static void later(int ticks, Runnable run) {
		synchronized (PENDING) {
			PENDING.add(new Task(Math.max(1, ticks), run));
		}
	}

	public static void next(Runnable run) {
		later(1, run);
	}

	public static void tick() {
		synchronized (PENDING) {
			TASKS.addAll(PENDING);
			PENDING.clear();
		}
		Iterator<Task> it = TASKS.iterator();
		List<Runnable> due = new ArrayList<>();
		while (it.hasNext()) {
			Task t = it.next();
			if (--t.ticks <= 0) {
				due.add(t.run);
				it.remove();
			}
		}
		for (Runnable r : due) {
			try {
				r.run();
			} catch (Exception e) {
				VoidPanel.LOGGER.error("Scheduled task failed", e);
			}
		}
	}

	public static void clear() {
		TASKS.clear();
		synchronized (PENDING) {
			PENDING.clear();
		}
	}
}
