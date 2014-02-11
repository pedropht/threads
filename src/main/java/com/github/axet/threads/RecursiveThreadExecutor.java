package com.github.axet.threads;

import java.util.ArrayList;
import java.util.List;

public class RecursiveThreadExecutor {

    public static class Task implements Runnable {

        Runnable r;

        boolean start = false;
        boolean end = false;

        public Task(Runnable r) {
            this.r = r;
        }

        @Override
        public void run() {
            r.run();
        }

    }

    class Job extends Thread {
        public Job() {
            super("RecursiveThread - " + threads.size());
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Task t = waitForNewTask();
                    if (t != null)
                        executeTaskFlag(t);
                }
            } catch (InterruptedException e) {
                return;
            }
        }

    }

    int maxThreads;
    List<Job> threads = new ArrayList<Job>();
    List<Task> tasks = new ArrayList<Task>();

    int waitingThreads = 0;

    /**
     * maxThread - limit max thread by number.
     * 
     * @param maxThreads
     *            set max threads to 0 to run tasks on the current thread
     */
    public RecursiveThreadExecutor(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public RecursiveThreadExecutor() {
        maxThreads = Runtime.getRuntime().availableProcessors();
    }

    public void close() {
        for (Job j : threads) {
            j.interrupt();
            try {
                j.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        threads.clear();
    }

    public void execute(Task t) {
        synchronized (tasks) {
            tasks.add(t);

            if (waitingThreads == 0) {
                if (threads.size() < maxThreads) {
                    createThread();
                }
            } else {
                tasks.notify();
            }
        }
    }

    void createThread() {
        Job t = new Job();
        threads.add(t);
        t.start();
    }

    Task waitForNewTask() throws InterruptedException {
        return waitForNewTask(null);
    }

    Task waitForNewTask(Task taskEnd) throws InterruptedException {
        synchronized (tasks) {
            waitingThreads++;
            try {
                if (tasks.size() == 0) {
                    if (taskEnd != null) {
                        synchronized (taskEnd) {
                            if (taskEnd.end)
                                return null;
                        }
                    }
                    tasks.wait();
                    if (tasks.size() == 0) {
                        return null;
                    }
                }
                return tasks.remove(0);
            } finally {
                waitingThreads--;
            }
        }
    }

    void waitTaskEnd(Task t) throws InterruptedException {
        while (true) {
            Task tt = waitForNewTask(t);
            if (tt != null)
                executeTaskFlag(tt);
            else {
                synchronized (t) {
                    if (t.end)
                        return;
                }
            }
        }
    }

    public void waitTermination(Task t) throws InterruptedException {
        if (!executeTaskFlag(t))
            waitTaskEnd(t);
    }

    boolean executeTaskFlag(Task t) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        synchronized (t) {
            if (t.end)
                return true;
            if (t.start) {
                return false;
            }
            t.start = true;
        }

        t.run();

        synchronized (t) {
            t.end = true;
        }

        synchronized (tasks) {
            tasks.notifyAll();
        }

        return true;
    }
}