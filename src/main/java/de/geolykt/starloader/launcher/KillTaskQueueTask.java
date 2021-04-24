package de.geolykt.starloader.launcher;

/**
 * If an instance of this runnable is processed by the main task queue, then the main tasks queue will be stopped and the main thread exits.
 * This however does not mean that the VM exits, as other threads will be still happily around.
 */
public class KillTaskQueueTask implements Runnable {

    @Override
    public void run() {
        // Do nothing; it isn't really intended to be used as such
    }

}
