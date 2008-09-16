/*
 *
 * -------------------------------------------------------------------
 * This source code, its documentation and all appendant files
 * are protected by copyright law. All rights reserved.
 *
 * Copyright, 2003 - 2008
 * University of Konstanz, Germany
 * Chair for Bioinformatics and Information Mining (Prof. M. Berthold)
 * and KNIME GmbH, Konstanz, Germany
 *
 * You may not modify, publish, transmit, transfer or sell, reproduce,
 * create derivative works from, distribute, perform, display, or in
 * any way exploit any of the content, in whole or in part, except as
 * otherwise expressly permitted in writing by the copyright owner or
 * as specified in the license file distributed with this product.
 *
 * If you have any questions please contact the copyright holder:
 * website: www.knime.org
 * email: contact@knime.org
 * -------------------------------------------------------------------
 *
 * History
 *   04.11.2005 (mb): created
 */
package org.knime.base.node.mine.decisiontree2.predictor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.knime.base.node.mine.decisiontree2.model.DecisionTree;
import org.knime.base.node.mine.decisiontree2.model.DecisionTreeNode;
import org.knime.base.node.mine.decisiontree2.model.DecisionTreeNodeRenderer;
import org.knime.core.data.RowKey;
import org.knime.core.node.GenericNodeView;
import org.knime.core.node.property.hilite.HiLiteHandler;

/**
 *
 * @author Michael Berthold, University of Konstanz
 */
public class DecTreePredictorNodeView 
        extends GenericNodeView<DecTreePredictorNodeModel> {

    private JTree m_jTree;

    private HiLiteHandler m_hiLiteHdl;

    private JMenu m_hiLiteMenu;

    /**
     * Default constructor, taking the model as argument.
     *
     * @param model the underlying NodeModel
     */
    public DecTreePredictorNodeView(final DecTreePredictorNodeModel model) {
        super(model);

        m_jTree = new JTree();
        m_jTree.putClientProperty("JTree.lineStyle", "Angled");
        m_jTree.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        m_jTree.setRootVisible(true);
        // try to viz it...
        JScrollPane treeView = new JScrollPane(m_jTree);
        setComponent(treeView);
        // retrieve HiLiteHandler from Input port
        m_hiLiteHdl = model.getInHiLiteHandler(
                DecTreePredictorNodeModel.INDATAPORT);
        // and add menu entries for HiLite-ing
        m_hiLiteMenu = this.createHiLitetMenu();
        this.getJMenuBar().add(m_hiLiteMenu);
        m_hiLiteMenu.setEnabled(m_hiLiteHdl != null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modelChanged() {
        DecTreePredictorNodeModel model = this.getNodeModel();
        DecisionTree dt = model.getDecisionTree();
        if (dt != null) {
            // set new model
            m_jTree.setModel(new DefaultTreeModel(dt.getRootNode()));
            // change default renderer
            m_jTree.setCellRenderer(new DecisionTreeNodeRenderer());
            // make sure no default height is assumed (the renderer's
            // preferred size should be used instead)
            m_jTree.setRowHeight(0);
            // retrieve HiLiteHandler from Input port
            m_hiLiteHdl = model.getInHiLiteHandler(DecTreePredictorNodeModel.INDATAPORT);
            // and adjust menu entries for HiLite-ing
            m_hiLiteMenu.setEnabled(m_hiLiteHdl != null);
        } else {
            m_jTree.setModel(null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onClose() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onOpen() {

    }

    // /////////////////////////////
    // routines for HiLite Support
    // /////////////////////////////

    /*
     * hilite or unhilite all items that are covered by currently selected
     * branches in the tree
     *
     * @param state if true hilite, otherwise unhilite selection
     */
    private void changeSelectedHiLite(final boolean state) {
        TreePath[] selectedPaths = m_jTree.getSelectionPaths();
        if (selectedPaths == null) {
            return; // nothing selected
        }
        for (int i = 0; i < selectedPaths.length; i++) {
            assert (selectedPaths[i] != null);
            if (selectedPaths[i] == null) {
                return;
            }
            TreePath path = selectedPaths[i];
            Object lastNode = path.getLastPathComponent();
            assert (lastNode != null);
            assert (lastNode instanceof DecisionTreeNode);
            Set<RowKey> covPat = ((DecisionTreeNode)lastNode)
                    .coveredPattern();
            if (state) {
                m_hiLiteHdl.fireHiLiteEvent(covPat);
            } else {
                m_hiLiteHdl.fireUnHiLiteEvent(covPat);
            }
        }

    }

    /*
     * Create menu to control hiliting
     *
     * @return A new JMenu with hiliting buttons
     */
    private JMenu createHiLitetMenu() {
        final JMenu result = new JMenu("Hilite");
        result.setMnemonic('H');
        JMenuItem item = new JMenuItem("Hilite Selected Branch");
        item.setMnemonic('S');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                assert (m_hiLiteHdl != null);
                changeSelectedHiLite(true);
            }
        });
        result.add(item);
        item = new JMenuItem("Unhilite Selected Branch");
        item.setMnemonic('U');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                assert (m_hiLiteHdl != null);
                changeSelectedHiLite(false);
            }
        });
        result.add(item);
        item = new JMenuItem("Clear Hilite");
        item.setMnemonic('C');
        item.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                assert (m_hiLiteHdl != null);
                m_hiLiteHdl.fireClearHiLiteEvent();
            }
        });
        result.add(item);
        // TODO listener when the hilite handler changes
        // (disable/enable the menu)
        /*
         * PropertyChangeListener hiliterChangeListener = new
         * PropertyChangeListener() { public void propertyChange(final
         * PropertyChangeEvent evt) { result.setEnabled(tView.hasData() &&
         * tView.hasHiLiteHandler()); } };
         * tView.getContentModel().addPropertyChangeListener(
         * hiliterChangeListener); result.setEnabled(tView.hasData() &&
         * tView.hasHiLiteHandler());
         */
        return result;
    }
}
