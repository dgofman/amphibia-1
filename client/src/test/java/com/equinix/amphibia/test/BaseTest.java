package com.equinix.amphibia.test;


/**
 *
 * @author dgofman
 */
import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.components.MainPanel;
import com.equinix.amphibia.components.TreeCollection;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Scanner;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.io.FileUtils;

public class BaseTest extends TestCase {

    protected static Amphibia instance;
    protected static MainPanel mainPanel;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public BaseTest(String name) throws Exception {
        super(name);
    }

    public static Test suite(Class clazz) {
        System.setProperty("userPreferenceType", BaseTest.class.getName());
        return new TestSetup(new TestSuite(clazz)) {
            @Override
            protected void setUp() throws Exception {
                instance = Amphibia.instance;
                instance.init();
                mainPanel = $(instance, "mainPanel");
                instance.setVisible(true);
            }

            @Override
            protected void tearDown() throws Exception {
                Amphibia.userPreferences.clear();
                if (MainPanel.selectedNode != null) {
                    TreeCollection collection = MainPanel.selectedNode.getCollection();
                    FileUtils.forceDeleteOnExit(collection.getProjectDir());
                }
            }
        };
    }

    public static void pause() {
        pause(1000);
    }
    
    public static void pause(int mls) {
        try {
            Thread.sleep(mls);
        } catch (InterruptedException e) {}
    }
    
    protected void invokeLater(Runnable runnable) throws Exception {
        SwingUtilities.invokeLater(runnable);
        pause();
        pause(100);
    }
    
    public static void doClick(AbstractButton c) {
        c.doClick();
        pause();
    }
            
    public static void setText(JTextComponent c, String text) {
        c.setText(text);
        pause();
    }

    public static <T extends Component> T $(Component parent, String name) throws Exception {
        Field f = parent.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object c = f.get(parent);
        TestCase.assertNotNull(c);
        return (T) c;
    }
    
    @SuppressWarnings("NonPublicExported")
    public static Dialog getDialogByName(String name) throws Exception {
        Frame frame = JOptionPane.getRootFrame();
        for (Component c : frame.getOwnedWindows()) {
            if (name.equals(c.getName())) {
                return new Dialog((JDialog) c);
            }
        }
        return null;
    }
    
    @SuppressWarnings("NonPublicExported")
    public static Dialog getDialogByMessage(Object message) throws Exception {
        Frame frame = JOptionPane.getRootFrame();
        for (Component c : frame.getOwnedWindows()) {
            Dialog dialog = new Dialog((JDialog) c);
            if (dialog.message == message) {
                return dialog;
            }
        }
        return null;
    }
    
    public static <T extends Component> T findByName(Component parent, String name) throws Exception {
        if (parent instanceof Window) {
            for (Component c : ((Window) parent).getOwnedWindows()) {
                if (name.equals(c.getName())) {
                    return (T) c;
                }
            }
        }
        if (parent instanceof Container) {
            Component[] children = (parent instanceof JMenu)
                    ? ((JMenu) parent).getMenuComponents()
                    : ((Container) parent).getComponents();
            for (Component c : children) {
                if (name.equals(c.getName())) {
                    return (T) c;
                }
            }
        }
        return null;
    }
    
    public static <T extends Component> T findByType(Component parent, String klass) throws Exception {
        return findByType(parent, klass, 0);
    }
    
    public static <T extends Component> T findByType(Component parent, String klass, int index) throws Exception {
        int count = 0;
        if (parent instanceof Window) {
            for (Component c : ((Window) parent).getOwnedWindows()) {
                if (klass.equals(c.getClass().getName())) {
                    if (count++ == index) {
                        return (T) c;
                    }
                }
            }
        }
        if (parent instanceof Container) {
            Component[] children = (parent instanceof JMenu)
                    ? ((JMenu) parent).getMenuComponents()
                    : ((Container) parent).getComponents();
            for (Component c : children) {
                if (klass.equals(c.getClass().getName())) {
                    if (count++ == index) {
                        return (T) c;
                    }
                }
            }
        }
        return null;
    }
    
    public static void lookup(Component c) {
        while (c != null) {
            System.out.println(String.format("Name: %s, Type: %s", c.getName(), c.getClass().getName()));
            c = c.getParent();
        }
    }
    
    @SuppressWarnings({"NonPublicExported", "SleepWhileInLoop"})
    protected void waitIsTrue(Booleanable bool) throws InterruptedException {
        while (bool.isTrue()) {
            Thread.sleep(500);
        }
    }

    interface Booleanable {
        boolean isTrue();
    }
    
    static class Dialog {
        public final Object[] options;
        public final Object message;
        
        public Dialog(JDialog dialog) throws Exception {
            JPanel contentPane = BaseTest.$(dialog.getRootPane(), "contentPane");
            JOptionPane pane = BaseTest.findByType(contentPane, "javax.swing.JOptionPane");
            this.options = pane.getOptions();
            this.message = pane.getMessage();
        }
        
        public <T extends AbstractButton> T getButton(String name) {
            for (Object button : options) {
                if (button instanceof AbstractButton && 
                    name.equals(((AbstractButton) button).getName())){
                    return (T) button;
                }
            }
            return null;
        }
    }
}
