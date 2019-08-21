/*
 * ------------------------------------------------------------------------
 *
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
 *   Aug 16, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.base.node.preproc.topk;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;
import org.knime.core.node.defaultnodesettings.SettingsModelString;
import org.knime.core.node.defaultnodesettings.SettingsModelStringArray;

/**
 * Manages the settings for the Top K Selector node.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
final class TopKSelectorSettings {

    private static SettingsModelIntegerBounded createKModel() {
        return new SettingsModelIntegerBounded("k", 5, 1, Integer.MAX_VALUE);
    }

    private static SettingsModelStringArray createColumnsModel() {
        return new SettingsModelStringArray("columns", null);
    }

    private static SettingsModelBoolean createMissingsToEndModel() {
        return new SettingsModelBoolean("missingsToEnd", true);
    }

    private static SettingsModelString createOutputOrderModel() {
        return new SettingsModelString("outputOrder", OutputOrder.NO_ORDER.name());
    }

    static final String CFG_ORDER = "order";

    private final SettingsModelIntegerBounded m_k = createKModel();

    private final SettingsModelStringArray m_columns = createColumnsModel();

    private final SettingsModelBoolean m_missingToEnd = createMissingsToEndModel();

    private final SettingsModelString m_outputOrder = createOutputOrderModel();

    /**
     * Array containing information about the sort order for each column. true: ascending; false: descending
     */
    private boolean[] m_orders = null;

    void saveSettingsTo(final NodeSettingsWO settings) {
        m_k.saveSettingsTo(settings);
        m_columns.saveSettingsTo(settings);
        m_missingToEnd.saveSettingsTo(settings);
        m_outputOrder.saveSettingsTo(settings);
        settings.addBooleanArray(CFG_ORDER, m_orders);
    }

    void validateSettings(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_k.validateSettings(settings);
        m_columns.validateSettings(settings);
        m_missingToEnd.validateSettings(settings);
        m_outputOrder.validateSettings(settings);
        settings.getBooleanArray(CFG_ORDER);
        checkForDuplicateRows(settings);
    }

    static void checkForDuplicateRows(final NodeSettingsRO settings) throws InvalidSettingsException {
        final SettingsModelStringArray temp = createColumnsModel();
        temp.loadSettingsFrom(settings);
        final String[] columns = temp.getStringArrayValue();
        for (int i = 0; i < columns.length; i++) {
            String entry = columns[i];
            for (int j = i + 1; j < columns.length; j++) {
                if (entry.equals(columns[j])) {
                    throw new InvalidSettingsException(
                        String.format("Dublicate column '%s' at positions %s and %s.", entry, i, j));
                }
            }
        }
    }

    void loadValidatedSettingsFrom(final NodeSettingsRO settings) throws InvalidSettingsException {
        m_k.loadSettingsFrom(settings);
        m_columns.loadSettingsFrom(settings);
        m_missingToEnd.loadSettingsFrom(settings);
        m_outputOrder.loadSettingsFrom(settings);
        m_orders = settings.getBooleanArray(CFG_ORDER);
    }

    int getK() {
        return m_k.getIntValue();
    }

    SettingsModelIntegerBounded getKModel() {
        return m_k;
    }

    SettingsModelString getOutputOrderModel() {
        return m_outputOrder;
    }

    SettingsModelBoolean getMissingToEndModel() {
        return m_missingToEnd;
    }

    String[] getColumns() {
        return m_columns.getStringArrayValue();
    }

    boolean[] getOrders() {
        return m_orders;
    }

    boolean isMissingToEnd() {
        return m_missingToEnd.getBooleanValue();
    }

    OutputOrder getOutputOrder() {
        return OutputOrder.valueOf(m_outputOrder.getStringValue());
    }

    void setColumns(final String[] columns) {
        m_columns.setStringArrayValue(columns);
    }

    void setOrders(final boolean[] orders) {
        m_orders = orders;
    }

    void setMissingToEnd(final boolean missingToEnd) {
        m_missingToEnd.setBooleanValue(missingToEnd);
    }

}
