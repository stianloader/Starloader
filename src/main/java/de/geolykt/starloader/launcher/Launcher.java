package de.geolykt.starloader.launcher;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingDeque;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import de.geolykt.starloader.launcher.components.ConfigurationTab;
import de.geolykt.starloader.launcher.components.ExtensionsTab;
import de.geolykt.starloader.launcher.components.MouseClickListener;
import de.geolykt.starloader.launcher.components.PatchesTab;
import de.geolykt.starloader.launcher.components.StarloaderTab;

public class Launcher {

    @Deprecated(forRemoval = true)
    public static final Launcher INSTANCE = new Launcher();

    public static final String GUI_TITLE = "Starloader Launcher";

    /**
     * A queue of runnables that will be processed by the main thread.
     */
    public static final LinkedBlockingDeque<Runnable> MAIN_TASK_QUEUE = new LinkedBlockingDeque<>();

    public final LauncherConfiguration configuration;

    private String[] args = new String[] {};
    private JFrame gui = null;
    private JMenuBar menuBar = null;
    private JButton play = null;
    private JButton extensions = null;
    private JButton patches = null;
    private JButton settings = null;
    private JPanel contentsStart = null;

    private ConfigurationTab contentsConfig = null;
    private ExtensionsTab contentsExtension = null;
    private PatchesTab contentsPatches = null;

    private StarloaderTab currentTab;

    public Launcher() {
        try {
            configuration = new LauncherConfiguration(new File(".slconfig.json"));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
            throw new RuntimeException("The JVM should've exited by now.");
        }
    }

    public void show() {
        if (gui != null) {
            throw new IllegalStateException("GUI already constructed.");
        }
        gui = new JFrame(GUI_TITLE);
        menuBar = new JMenuBar();

        play = new JButton("Play");
        play.addMouseListener(new MouseClickListener(this::play));
        menuBar.add(play);
        extensions = new JButton("Extensions");
        extensions.addMouseListener(new MouseClickListener(this::extensions));
        menuBar.add(extensions);
        patches = new JButton("Patches");
        patches.addMouseListener(new MouseClickListener(this::patches));
        menuBar.add(patches);
        settings = new JButton("Settings");
        settings.addMouseListener(new MouseClickListener(this::settings));
        menuBar.add(settings);

        contentsStart = new JPanel();
        contentsConfig = new ConfigurationTab(configuration, gui);
        contentsExtension = new ExtensionsTab(configuration);
        contentsPatches = new PatchesTab(configuration);

        contentsPatches.add(new JLabel("Coming soon (TM)!"));

        currentTab = new StarloaderTab() {
            @Override
            public void onClose(JFrame parent) {
                parent.remove(contentsStart);
            }
        };

        gui.setJMenuBar(menuBar);
        gui.add(contentsStart);
        gui.pack();
        gui.setVisible(true);
        gui.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {
        // Main entrypoint for debugging purposes
        INSTANCE.show();
        while (true) {
            try {
                Runnable r = MAIN_TASK_QUEUE.poll();
                if (r == null) {
                    Thread.sleep(14);
                } else if (r instanceof KillTaskQueueTask) {
                    if (MAIN_TASK_QUEUE.size() != 0) {
                        System.err.println("Error: Main thread recieved the KillTasksQueueTask despite there still being elements in the queue!");
                    }
                    break;
                } else {
                    r.run();
                }
            } catch (InterruptedException e) {
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void play() {
        currentTab.onClose(gui);
        gui.setVisible(false);
        // Memory deallocation - they are no longer used, so there is little reason on continuing to use it
        gui = null;
        menuBar = null;
        play = null;
        extensions = null;
        settings = null;
        contentsConfig = null;
        contentsExtension = null;
        contentsPatches = null;
        contentsStart = null;
        currentTab = null;
        MAIN_TASK_QUEUE.add(() -> Utils.startGalimulator(args, configuration));
        MAIN_TASK_QUEUE.add(new KillTaskQueueTask());
    }

    private void settings() {
        currentTab.onClose(gui);
        gui.add(contentsConfig);
        currentTab = contentsConfig;
        currentTab.onOpen(gui);
        gui.setSize(Utils.combineLargest(gui.getSize(), gui.getPreferredSize()));
        gui.paintComponents(gui.getGraphics());
    }

    private void extensions() {
        currentTab.onClose(gui);
        gui.add(contentsExtension);
        currentTab = contentsExtension;
        currentTab.onOpen(gui);
        gui.setSize(Utils.combineLargest(gui.getSize(), gui.getPreferredSize()));
        gui.paintComponents(gui.getGraphics());
    }

    private void patches() {
        currentTab.onClose(gui);
        gui.add(contentsPatches);
        currentTab = contentsPatches;
        currentTab.onOpen(gui);
        gui.setSize(Utils.combineLargest(gui.getSize(), gui.getPreferredSize()));
        gui.paintComponents(gui.getGraphics());
    }

    public String[] getArgs() {
        return args;
    }

    public void setArgs(String[] args) {
        this.args = args;
    }
}
