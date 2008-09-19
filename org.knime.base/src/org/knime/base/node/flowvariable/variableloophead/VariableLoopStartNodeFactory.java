/*
 * ------------------------------------------------------------------
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
 * ---------------------------------------------------------------------
 * 
 * History
 *   Sept 17, 2008 (mb): created
 */
package org.knime.base.node.flowvariable.variableloophead;

import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;
import org.knime.core.node.NodeDialogPane;


/**
 * 
 * @author M. Berthold, University of Konstanz
 */
public class VariableLoopStartNodeFactory 
    extends NodeFactory<VariableLoopStartNodeModel> {
    
    /** Create factory, that instantiates nodes.
     */
    public VariableLoopStartNodeFactory() {
    }

    /** {@inheritDoc} */
    @Override
    protected NodeDialogPane createNodeDialogPane() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public VariableLoopStartNodeModel createNodeModel() {
        return new VariableLoopStartNodeModel();
    }

    /** {@inheritDoc} */
    @Override
    public NodeView<VariableLoopStartNodeModel> createNodeView(
            final int index, final VariableLoopStartNodeModel model) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected int getNrNodeViews() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override
    protected boolean hasDialog() {
        return false;
    }

}
