package de.geolykt.starloader.launcher;

import java.io.File;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import de.geolykt.starloader.UnlikelyEventException;
import de.geolykt.starloader.util.Version;
import de.geolykt.starloader.util.Version.Stabillity;

public class DigestCalculationRunnable implements RunnableFuture<Version> {

    public DigestCalculationRunnable(File target) {
        fileSrc = target;
    }

    final File fileSrc;
    private Version hash;
    private AtomicBoolean computing = new AtomicBoolean(false);
    private AtomicBoolean cancelled = new AtomicBoolean(false);
    private AtomicBoolean computed = new AtomicBoolean(false);
    private Semaphore deadlockPreventer = new Semaphore(1);
    private Thread executor;
    private Runnable afterRunner = null;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        }
        cancelled.set(true);
        if (mayInterruptIfRunning && executor != null) {
            executor.interrupt();
        }
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public boolean isDone() {
        return isCancelled() || computed.get();
    }

    @Override
    public Version get() throws InterruptedException {
        if (isDone()) {
            return hash;
        }
        if (computing.get()) {
            // wait for execution
            deadlockPreventer.acquire();
            deadlockPreventer.release(); // Do not actually deadlock
            return hash;
        } else {
            run();
            return hash;
        }
    }

    @Override
    public Version get(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
        if (isDone()) {
            return hash;
        }
        if (computing.get()) {
            // wait for execution
            if (!deadlockPreventer.tryAcquire(timeout, unit)) {
                throw new TimeoutException();
            }
            deadlockPreventer.release(); // Do not actually deadlock
            return hash;
        } else {
            run();
            return hash;
        }
    }

    @Override
    public void run() {
        if (isDone()) {
            return;
        }
        if (!deadlockPreventer.tryAcquire()) {
            throw new UnlikelyEventException();
        }
        computing.set(true);
        executor = Thread.currentThread();
        if (fileSrc.exists()) {
            hash = Utils.VERSIONS.getOrDefault(Utils.getChecksum(fileSrc), new Version(0, 0, 0, "error", "Unknown", Stabillity.SNAPSHOT));
        } else {
            hash = new Version(0, 0, 0, "error", "File not found", Stabillity.SNAPSHOT);
        }
        computed.set(true);
        executor = null;
        deadlockPreventer.release();
        if (afterRunner != null) {
            afterRunner.run();
        }
    }

    /**
     * Sets the afterrunner of this runnable.
     * The afterrunner will run after this runnable has completed via the {@link #run()} method;
     * due to how the class is implemented this might also be performed when {@link #get()} or {@link #get(long, TimeUnit)}
     * is called. The afterrunner should however be only called once, no matter how many times
     * run or similar is invoked
     *
     * @param r The afterrunner
     */
    public void setRunAfter(Runnable r) {
        afterRunner = r;
    }

    /**
     * The method performs similar to {@link #get()}, however it will not wait for the task to complete if the task is currently
     * running and similarly it will not run the task if none are computing it. The return code of this method should
     * not change if {@link #isDone()} yields true, unless a task is currently still computing (this may happen if the future
     * is cancelled).
     *
     * @return The return value, or null if it hasn't been computed yet (null can also represent a variable resolved to null!)
     */
    public Version getDirectly() {
        return hash;
    }
}
