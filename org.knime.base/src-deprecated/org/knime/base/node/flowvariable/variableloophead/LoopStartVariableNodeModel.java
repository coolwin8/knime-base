/*
 * ------------------------------------------------------------------------
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   Sept 17 2008 (mb): created (from wiswedel's TableToVariableNode)
 */
package org.knime.base.node.flowvariable.variableloophead;

import java.io.IOException;

import org.knime.base.node.flowvariable.tablerowtovariable.TableToVariableNodeModel;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTable;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.RowIterator;
import org.knime.core.data.container.CloseableRowIterator;
import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.BufferedDataTable.KnowsRowCountTable;
import org.knime.core.node.ExecutionContext;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.port.PortObject;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.port.flowvariable.FlowVariablePortObject;
import org.knime.core.node.port.flowvariable.FlowVariablePortObjectSpec;
import org.knime.core.node.workflow.LoopStartNodeTerminator;

/** Start of loop: pushes variables in input datatable columns
 * onto stack, taking the values from one row per iteration.
 *
 * @author M. Berthold, University of Konstanz
 */
@Deprecated
public class LoopStartVariableNodeModel extends TableToVariableNodeModel implements LoopStartNodeTerminator {

    // remember which iteration we are in:
    private int m_currentIteration = -1;
    private int m_maxNrIterations = -1;
    // last seen table in #execute -- used for assertions
    private DataTable m_lastTable;
    // to fetch next row from
    private RowIterator m_iterator;

    /** One input, one output.
     */
    protected LoopStartVariableNodeModel() {
    }

    /** {@inheritDoc} */
    @Override
    protected PortObjectSpec[] configure(final PortObjectSpec[] inSpecs) throws InvalidSettingsException {
        pushVariables((DataTableSpec) inSpecs[0]);
        pushFlowVariableInt("maxIterations", 0);
        pushFlowVariableInt("currentIteration", 0);
        return new PortObjectSpec[]{FlowVariablePortObjectSpec.INSTANCE};
    }

    /** {@inheritDoc} */
    @Override
    protected PortObject[] execute(final PortObject[] inPOs,
            final ExecutionContext exec) throws Exception {
        BufferedDataTable inData = (BufferedDataTable)inPOs[0];
        DataRow row;
        if (m_currentIteration == -1) {
            assert m_iterator == null : "Iterator expected to be null here";
            // first time we see this, initialize counters:
            m_currentIteration = 0;
            m_maxNrIterations = KnowsRowCountTable.checkRowCount(inData.size());
            m_lastTable = inData;
            m_iterator = m_lastTable.iterator();
            if (m_maxNrIterations == 0) {
                assert !m_iterator.hasNext() : "Iterator returns rows but size is 0";
                row = null; // see AP-11399 -- perform single iteration in case of empty table
            } else {
                row = m_iterator.next();
            }
        } else {
            if (m_currentIteration > m_maxNrIterations) {
                throw new IOException("Loop did not terminate correctly.");
            }
            row = m_iterator.next();
        }
        assert m_lastTable == inData : "not the same table instance";
        // put values for variables on stack, based on current row
        pushVariables(inData.getDataTableSpec(), row);
        // and add information about loop progress
        pushFlowVariableInt("maxIterations", m_maxNrIterations);
        pushFlowVariableInt("currentIteration", m_currentIteration);
        m_currentIteration++;
        if (m_currentIteration == m_maxNrIterations) {
            assert !m_iterator.hasNext() : "Iterator supposed to be at the end but has more rows";
            closeIterator();
        }
        return new PortObject[]{FlowVariablePortObject.INSTANCE};
    }

    private void closeIterator() {
        if (m_iterator instanceof CloseableRowIterator) {
            ((CloseableRowIterator)m_iterator).close();
        }
        m_iterator = null;
        m_lastTable = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean terminateLoop() {
        return m_currentIteration >= m_maxNrIterations;
    }

    /** {@inheritDoc} */
    @Override
    protected void reset() {
        m_currentIteration = -1;
        m_maxNrIterations = -1;
        closeIterator();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDispose() {
        closeIterator();
    }

}
