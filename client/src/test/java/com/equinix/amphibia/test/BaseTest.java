package com.equinix.amphibia.test;


/**
 *
 * @author dgofman
 */
import com.equinix.amphibia.Amphibia;
import com.equinix.amphibia.components.MainPanel;
import com.equinix.amphibia.components.TreeCollection;
import com.equinix.amphibia.components.TreeIconNode;

import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.lang.reflect.Field;
import java.util.prefs.Preferences;
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
    protected static int actionDelay;

    @SuppressWarnings("OverridableMethodCallInConstructor")
    public BaseTest(String name) throws Exception {
        super(name);
    }

    public static Test suite(Class clazz) {
        actionDelay = Integer.parseInt(System.getProperty("actionDelay", "1000"));
        System.setProperty("userPreferenceType", BaseTest.class.getName());
        return new TestSetup(new TestSuite(clazz)) {
            @Override
            protected void setUp() throws Exception {
                Preferences.userNodeForPackage(BaseTest.class).clear();
                instance = Amphibia.instance;
                instance.init();
                mainPanel = $(instance, "mainPanel");
                instance.setVisible(true);
            }

            @Override
            protected void tearDown() throws Exception {
                if (getNode() != null) {
                    FileUtils.forceDeleteOnExit(getCollection().getProjectDir());
                }
            }
        };
    }
    
    public static TreeIconNode getNode() {
        return MainPanel.selectedNode;
    }
    
    public static TreeCollection getCollection() {
        return MainPanel.selectedNode.getCollection();
    }

    public static void pause() {
        pause(actionDelay);
    }
    
    public static void pause(int mls) {
        try {
            Thread.sleep(mls);
        } catch (InterruptedException e) {}
    }
    
    protected void invokeLater(Runnable runnable) throws Exception {
        SwingUtilities.invokeLater(runnable);
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
    
    @SuppressWarnings({"NonPublicExported", "SleepWhileInLoop"})
    public static Dialog getDialogByName(String name) throws Exception {
        Dialog dialog = null;
        while (dialog == null) {
            Frame frame = JOptionPane.getRootFrame();
            for (Component c : frame.getOwnedWindows()) {
                if (name.equals(c.getName())) {
                    dialog = new Dialog((JDialog) c);
                }
            }
            Thread.sleep(500);
        }
        return dialog;
    }
    
    @SuppressWarnings("NonPublicExported")
    public static Dialog getDialogByMessage(Object message) throws Exception {
        Frame frame = JOptionPane.getRootFrame();
        for (Component c : frame.getOwnedWindows()) {
            Dialog dialog = new Dialog((JDialog) c);
            if (dialog.message == message) {
                System.out.println(dialog.getName());
                return dialog;
            }
        }
        return null;
    }
    
    @SuppressWarnings("NonPublicExported")
    public static Dialog findDialog(String name, Object message) throws Exception {
        final Dialog dialog = getDialogByName(name);
        TestCase.assertEquals(message, dialog.message);
        waitIsTrue(() -> {
            return dialog.isVisible();
        });
        return dialog;
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
    public static void waitIsTrue(Booleanable bool) throws Exception {
        while (!bool.isTrue()) {
            Thread.sleep(500);
        }
    }

    interface Booleanable {
        boolean isTrue() throws Exception;
    }
    
    static class Dialog {
        public final JDialog dialog;
        public final Object[] options;
        public final Object message;
        
        public Dialog(JDialog dialog) throws Exception {
            this.dialog = dialog;
            JPanel contentPane = BaseTest.$(dialog.getRootPane(), "contentPane");
            JOptionPane pane = BaseTest.findByType(contentPane, "javax.swing.JOptionPane");
            this.options = pane.getOptions();
            this.message = pane.getMessage();
        }
        
        public String getName() {
            return dialog.getName();
        }
        
        public boolean isVisible() {
            return dialog.isVisible();
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
