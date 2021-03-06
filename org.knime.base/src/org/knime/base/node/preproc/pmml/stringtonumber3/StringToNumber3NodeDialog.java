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
 * --------------------------------------------------------------------
 *
 * History
 *   03.07.2007 (cebron): created
 */
package org.knime.base.node.preproc.pmml.stringtonumber3;

import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.Border;

import org.knime.base.node.preproc.pmml.stringtonumber.StringToNumberNodeModel;
import org.knime.core.data.DataType;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.defaultnodesettings.DialogComponentColumnFilter2;
import org.knime.core.node.port.PortObjectSpec;
import org.knime.core.node.util.DataTypeListCellRenderer;

/**
 * Dialog for the String to Number Node. Lets the user choose the columns to
 * use.
 *
 * @author cebron, University of Konstanz
 * @since 4.0
 */
public class StringToNumber3NodeDialog extends NodeDialogPane {
    @SuppressWarnings("unchecked")
    private final DialogComponentColumnFilter2 m_filtercomp =
            new DialogComponentColumnFilter2(StringToNumber3NodeModel.createInclModel(), 0);

    private final JTextField m_decimalSeparator = new JTextField(".", 3);

    private final JTextField m_thousandsSeparator = new JTextField(",", 3);

    private final JComboBox m_typeChooser =
            new JComboBox(StringToNumberNodeModel.POSSIBLETYPES);


    /**
     * Constructor.
     *
     */
    public StringToNumber3NodeDialog() {
        JPanel contentpanel = new JPanel();
        contentpanel.setLayout(new BoxLayout(contentpanel, BoxLayout.Y_AXIS));
        JPanel separatorPanel = new JPanel();
        Border border = BorderFactory.createTitledBorder("Parsing options");
        separatorPanel.setBorder(border);
        separatorPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));

        m_typeChooser.setRenderer(new DataTypeListCellRenderer());
        separatorPanel.add(new JLabel("Type: "));
        separatorPanel.add(m_typeChooser);

        separatorPanel.add(new JLabel("Decimal separator: "));
        separatorPanel.add(m_decimalSeparator);

        separatorPanel.add(new JLabel("Thousands separator: "));
        separatorPanel.add(m_thousandsSeparator);
        contentpanel.add(separatorPanel);

        contentpanel.add(m_filtercomp.getComponentPanel());
        super.addTab("Settings", contentpanel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void loadSettingsFrom(final NodeSettingsRO settings,
            final PortObjectSpec[] specs) throws NotConfigurableException {
        m_filtercomp.loadSettingsFrom(settings, specs);
        String decimalsep =
                settings.getString(AbstractStringToNumberNodeModel.CFG_DECIMALSEP,
                        AbstractStringToNumberNodeModel.DEFAULT_DECIMAL_SEPARATOR);
        m_decimalSeparator.setText(decimalsep);
        String thousandssep =
                settings.getString(AbstractStringToNumberNodeModel.CFG_THOUSANDSSEP,
                        AbstractStringToNumberNodeModel.DEFAULT_THOUSANDS_SEPARATOR);
        m_thousandsSeparator.setText(thousandssep);
        if (settings.containsKey(AbstractStringToNumberNodeModel.CFG_PARSETYPE)) {
            m_typeChooser.setSelectedItem(settings.getDataType(
                    AbstractStringToNumberNodeModel.CFG_PARSETYPE, DoubleCell.TYPE));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveSettingsTo(final NodeSettingsWO settings)
            throws InvalidSettingsException {
        m_filtercomp.saveSettingsTo(settings);
        settings.addString(AbstractStringToNumberNodeModel.CFG_DECIMALSEP,
                m_decimalSeparator.getText());
        settings.addString(AbstractStringToNumberNodeModel.CFG_THOUSANDSSEP,
                m_thousandsSeparator.getText());
        settings.addDataType(AbstractStringToNumberNodeModel.CFG_PARSETYPE,
                (DataType)m_typeChooser.getSelectedItem());
    }
}
