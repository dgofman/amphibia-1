/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.equinix.amphibia.agent.runner;

import java.awt.Color;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 *
 * @author dgofman
 */
public interface IHttpConnection {

    public IHttpConnection info(String text);

    public IHttpConnection info(String text, boolean isBold);

    public IHttpConnection info(String text, Color color);

    public IHttpConnection info(String text, SimpleAttributeSet attr);

    public DefaultMutableTreeNode addError(String error);

    public DefaultMutableTreeNode addError(Throwable t, String error);
}
