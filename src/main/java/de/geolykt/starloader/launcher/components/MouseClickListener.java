package de.geolykt.starloader.launcher.components;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public final class MouseClickListener implements MouseListener {

    private final Runnable action;

    public MouseClickListener(Runnable action) {
        this.action = action;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        this.action.run();
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
