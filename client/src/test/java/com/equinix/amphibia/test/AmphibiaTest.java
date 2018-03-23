package com.equinix.amphibia.test;

/**
 *
 * @author dgofman
 */
import com.equinix.amphibia.components.ProjectDialog;

import junit.framework.Test;
import junit.framework.TestCase;

import javax.swing.*;
import org.junit.Ignore;

public class AmphibiaTest extends BaseTest {

    public AmphibiaTest(String name) throws Exception {
        super(name);
    }

    @Ignore
    public void testCreateNewProject() throws Exception {
        JMenu mnuFile = $(instance, "mnuFile");
        JMenuItem mnuNewProject = $(instance, "mnuNewProject");
        ProjectDialog projectDialog = $(instance, "projectDialog");
        JTextField txtSwaggerUrl = $(projectDialog, "txtSwaggerUrl");
        JButton btnSwaggerUrl = $(projectDialog, "btnSwaggerUrl");
        JButton btnFinish = $(projectDialog, "btnFinish");
        JPanel pnlWaitOverlay = $(projectDialog, "pnlWaitOverlay");
        JPanel pnlAddHeader = $(mainPanel.wizard, "pnlAddHeader");
        JPanel pnlInterface = $(mainPanel.wizard, "pnlInterface");
        JButton btnAddRow = $(mainPanel.wizard, "btnAddRow");
        JTextField txtHeaderName = $(mainPanel.wizard, "txtHeaderName");
        JTextField txtHeaderValue = $(mainPanel.wizard, "txtHeaderValue");
        JCheckBox ckbAsGlobal = $(mainPanel.wizard, "ckbAsGlobal");

        doClick(mnuFile);
        invokeLater(() -> {
            doClick(mnuNewProject);
        });

        findDialog("Amphibia12", projectDialog);

        txtSwaggerUrl.setText("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v2.0/json/api-with-examples.json");
        doClick(btnSwaggerUrl);
        waitIsTrue(() -> {
            return !pnlWaitOverlay.isVisible();
        });
        doClick(btnFinish);

        findDialog("Amphibia3", pnlInterface);

        invokeLater(() -> {
            doClick(btnAddRow);
        });

        Dialog popupPnlAddHeader = findDialog("Amphibia17", pnlAddHeader);
        JButton apply = popupPnlAddHeader.getButton("apply");
        setText(txtHeaderName, "CONTENT-TYPE");
        setText(txtHeaderValue, "application/json");
        doClick(ckbAsGlobal);
        doClick(apply);
        JButton applyInterfaceButton = $(mainPanel.wizard, "applyInterfaceButton");
        doClick(applyInterfaceButton);
        TestCase.assertEquals("SimpleAPIoverview", getCollection().project.getLabel());
    }

    public static Test suite() {
        return BaseTest.suite(AmphibiaTest.class);
    }
}
