package de.geolykt.starloader.launcher.components;

import javax.swing.JFrame;

public interface StarloaderTab {

    /**
     * Called when a tab is closed.
     *
     * @param parent The parent JFrame
     */
    public void onClose(JFrame parent);

    /**
     * Called when a tab is opened.
     *
     * @param parent The parent JFrame
     */
    public default void onOpen(JFrame parent) {
        
    }
}
