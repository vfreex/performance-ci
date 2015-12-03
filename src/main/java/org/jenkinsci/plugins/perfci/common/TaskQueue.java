package org.jenkinsci.plugins.perfci.common;

import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Created by vfreex on 12/3/15.
 */
public class TaskQueue {
    private ConcurrentLinkedDeque<Runnable> tasks = new ConcurrentLinkedDeque<>();

    public TaskQueue() {
    }

    public void enqueue(Runnable task) {
        tasks.add(task);
    }

    /**
     * Start tasks in this queue.
     *
     * @param millis the time to wait in milliseconds, 0 means to wait forever.
     * @throws InterruptedException
     */
    public void runAll(long millis) throws InterruptedException {
        int threadCount = Math.min(tasks.size(), Runtime.getRuntime().availableProcessors());
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(new Runnable() {
                @Override
                public void run() {
                    Runnable task;
                    while ((task = tasks.poll()) != null) {
                        task.run();
                    }
                }
            });
        }
        for (int t = 0; t < threadCount; t++) {
            threads[t].start();
        }
        for (int t = 0; t < threadCount; t++) {
            threads[t].join(millis);
        }
    }
}
