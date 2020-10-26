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
 *   Aug 5, 2020 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.filehandling.core.node.table.reader.preview.dialog;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.knime.core.node.InvalidSettingsException;
import org.knime.core.util.Pair;
import org.knime.core.util.SwingWorkerWithContext;
import org.knime.filehandling.core.defaultnodesettings.filechooser.reader.ReadPathAccessor;
import org.knime.filehandling.core.node.table.reader.MultiTableReadFactory;
import org.knime.filehandling.core.node.table.reader.config.ImmutableMultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.MultiTableReadConfig;
import org.knime.filehandling.core.node.table.reader.config.ReaderSpecificConfig;
import org.knime.filehandling.core.node.table.reader.config.TableSpecConfig;
import org.knime.filehandling.core.node.table.reader.preview.PreviewDataTable;
import org.knime.filehandling.core.node.table.reader.selector.MutableTransformationModel;
import org.knime.filehandling.core.node.table.reader.selector.TransformationModel;
import org.knime.filehandling.core.node.table.reader.util.MultiTableRead;
import org.knime.filehandling.core.node.table.reader.util.StagedMultiTableRead;
import org.knime.filehandling.core.util.CheckedExceptionSupplier;

/**
 * Controls the communication between the transformation model and the preview table.</br>
 * All I/O operations are executed in the background with {@link SwingWorkerWithContext swing workers}.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 * @param <C> the type of {@link ReaderSpecificConfig}
 * @param <T> the type used to identify external types
 */
final class TableReaderPreviewTransformationController<C extends ReaderSpecificConfig<C>, T> {

    private final MultiTableReadFactory<C, T> m_readFactory;

    private final AnalysisComponentModel m_analysisComponent;

    private final TableReaderPreviewModel m_previewModel;

    private final MutableTransformationModel<T> m_transformationModel;

    private final CheckedExceptionSupplier<MultiTableReadConfig<C>, InvalidSettingsException> m_configSupplier;

    private final Supplier<ReadPathAccessor> m_readPathAccessorSupplier;

    private PreviewRun m_currentRun;

    TableReaderPreviewTransformationController(final MultiTableReadFactory<C, T> readFactory,
        final MutableTransformationModel<T> transformationModel, final AnalysisComponentModel analysisComponentModel,
        final TableReaderPreviewModel tableReaderPreviewModel,
        final CheckedExceptionSupplier<MultiTableReadConfig<C>, InvalidSettingsException> configSupplier,
        final Supplier<ReadPathAccessor> readPathAccessorSupplier) {
        m_readFactory = readFactory;
        m_transformationModel = transformationModel;
        m_analysisComponent = analysisComponentModel;
        m_previewModel = tableReaderPreviewModel;
        m_configSupplier = configSupplier;
        m_readPathAccessorSupplier = readPathAccessorSupplier;
        m_transformationModel.addChangeListener(e -> handleTransformationModelChange());
    }

    void configChanged() {
        m_analysisComponent.reset();
        cancelCurrentRun();
        try {
            m_currentRun = new PreviewRun(m_configSupplier.get());
        } catch (InvalidSettingsException ex) {// NOSONAR, the exception is displayed in the dialog
            m_analysisComponent.setError(ex.getMessage());
            m_previewModel.setDataTable(null);
        }
    }

    private void cancelCurrentRun() {
        if (m_currentRun != null) {
            m_currentRun.close();
            m_currentRun = null;
        }
    }

    void load(final TransformationModel<T> transformationModel) {
        m_transformationModel.imitate(transformationModel);
    }

    TableSpecConfig getTableSpecConfig() {
        return m_currentRun != null ? m_currentRun.getTableSpecConfig() : null;
    }

    /**
     * To be called when the dialog is closed.
     */
    public void onClose() {
        cancelCurrentRun();
        m_previewModel.onClose();
    }

    private void handleTransformationModelChange() {
        // changes to the model while we are updating can only be caused by us in #consumeNeStagedMultiRead
        // and are therefore handled
        if (m_currentRun != null && !m_currentRun.isUpdatingPreview()) {
            m_currentRun.updatePreviewTable();
        }
    }

    /**
     * Represents the calculations corresponding to one set of {@link MultiTableReadConfig} and
     * {@link ReadPathAccessor}.
     *
     * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
     */
    private class PreviewRun implements AutoCloseable {

        private ImmutableMultiTableReadConfig<C> m_config;

        private SpecGuessingSwingWorker<C, T> m_specGuessingWorker = null;

        private PathAccessSwingWorker m_pathAccessWorker = null;

        private StagedMultiTableRead<T> m_currentRead = null;

        private ReadPathAccessor m_readPathAccessor = null;

        private TableSpecConfig m_currentTableSpecConfig = null;

        private final AtomicBoolean m_closed = new AtomicBoolean(false);

        private boolean m_updatingPreview = true;

        PreviewRun(final MultiTableReadConfig<C> config) {
            m_config = new ImmutableMultiTableReadConfig<>(config);
            m_readPathAccessor = m_readPathAccessorSupplier.get();
            m_pathAccessWorker =
                new PathAccessSwingWorker(m_readPathAccessor, this::startSpecGuessingWorker, this::displayPathError);
            m_pathAccessWorker.execute();
        }

        TableSpecConfig getTableSpecConfig() {
            return m_currentTableSpecConfig;
        }

        @Override
        public void close() {
            m_closed.set(true);
            if (m_pathAccessWorker != null) {
                m_pathAccessWorker.cancel(true);
            }
            if (m_specGuessingWorker != null) {
                m_specGuessingWorker.cancel(true);
            }
            // the preview must be closed before we close the readPathAccessor
            // otherwise the iterator might throw a ClosedFileSystemException
            m_previewModel.setDataTable(null);
            if (m_readPathAccessor != null) {
                new CloserSwingWorker(m_readPathAccessor).execute();
            }
        }

        private void displayPathError(final ExecutionException exception) {
            m_analysisComponent.setError(exception.getCause().getMessage());
            m_previewModel.setDataTable(null);
        }

        boolean isUpdatingPreview() {
            return m_updatingPreview;
        }

        /**
         * Executed by the m_pathAccessWorker once it resolved the list of paths.
         *
         * @param rootPathAndPaths the list of paths resolved by m_pathAccessWorker
         */
        private void startSpecGuessingWorker(final Pair<Path, List<Path>> rootPathAndPaths) {
            if (m_closed.get()) {
                // this method is called in the EDT so it might be the case that
                // the run got cancelled between the completion of the path access worker
                // and the invocation of its background worker
                return;
            }
            m_analysisComponent.setVisible(true);
            m_specGuessingWorker = new SpecGuessingSwingWorker<>(m_readFactory, rootPathAndPaths.getFirst().toString(),
                rootPathAndPaths.getSecond(), m_config, m_analysisComponent, this::consumeNewStagedMultiRead);
            m_specGuessingWorker.execute();
        }

        private void consumeNewStagedMultiRead(final StagedMultiTableRead<T> stagedMultiTableRead) {
            if (m_closed.get()) {
                // this method is called in the EDT so it might be the case that
                // the run got cancelled between the completion of the StagedMultiTableRead
                // and the invocation of its background worker
                return;
            }
            m_currentRead = stagedMultiTableRead;
            // we disable the transformation view during this update to avoid concurrent manipulation
            m_transformationModel.setEnabled(false);
            m_transformationModel.updateRawSpec(m_currentRead.getRawSpec());
            // the table spec might not change but the read accessor will be closed therefore we need to
            // update the preview table otherwise we risk IOExceptions because the paths are no longer valid
            // In addition to this issue, it might also be the case that a config change might not result in
            // a different table spec but in a different table content e.g. if some rows are skipped
            updatePreviewTable();
            m_transformationModel.setEnabled(true);
            m_updatingPreview = false;
        }

        private void updatePreviewTable() {
            if (m_closed.get()) {
                return;
            }
            final MultiTableRead mtr = m_currentRead.withTransformation(m_transformationModel);
            m_currentTableSpecConfig = mtr.getTableSpecConfig();
            @SuppressWarnings("resource") // the m_preview must make sure that the PreviewDataTable is closed
            final PreviewDataTable pdt =
                new PreviewDataTable(() -> mtr.createPreviewIterator(), mtr.getOutputSpec());
            m_previewModel.setDataTable(pdt);
        }

    }
}