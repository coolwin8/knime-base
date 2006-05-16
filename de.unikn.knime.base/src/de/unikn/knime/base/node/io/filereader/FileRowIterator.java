/* --------------------------------------------------------------------- *
 *   This source code, its documentation and all appendant files         *
 *   are protected by copyright law. All rights reserved.                *
 *                                                                       *
 *   Copyright, 2003 - 2006                                              *
 *   Universitaet Konstanz, Germany.                                     *
 *   Lehrstuhl fuer Angewandte Informatik                                *
 *   Prof. Dr. Michael R. Berthold                                       *
 *                                                                       *
 *   You may not modify, publish, transmit, transfer or sell, reproduce, *
 *   create derivative works from, distribute, perform, display, or in   *
 *   any way exploit any of the content, in whole or in part, except as  *
 *   otherwise expressly permitted in writing by the copyright owner.    *
 * --------------------------------------------------------------------- *
 */
package de.unikn.knime.base.node.io.filereader;

import java.io.IOException;
import java.util.HashSet;
import java.util.NoSuchElementException;

import de.unikn.knime.base.node.io.filetokenizer.FileTokenizer;
import de.unikn.knime.base.node.io.filetokenizer.FileTokenizerException;
import de.unikn.knime.core.data.DataCell;
import de.unikn.knime.core.data.DataColumnSpec;
import de.unikn.knime.core.data.DataRow;
import de.unikn.knime.core.data.DataTableSpec;
import de.unikn.knime.core.data.DataType;
import de.unikn.knime.core.data.RowIterator;
import de.unikn.knime.core.data.def.DoubleCell;
import de.unikn.knime.core.data.def.IntCell;
import de.unikn.knime.core.data.def.DefaultRow;
import de.unikn.knime.core.data.def.StringCell;

/**
 * 
 * @author Peter Ohl, University of Konstanz
 * 
 * @see de.unikn.knime.core.data.RowIterator
 */
final class FileRowIterator extends RowIterator {

    /* The tokenizer reads the next token from the input stream. */
    private final FileTokenizer m_tokenizer;

    // keep a reference for the filereader settings.
    private final FileReaderSettings m_frSettings;

    /* Keep a reference to the spec defining the table's strucutre. */
    private final DataTableSpec m_tableSpec;

    /* Counts the number of rows read. */
    private int m_rowNumber;

    // the resolved row header prefix used for each row, if set. The constructor
    // resolves all possible user settings and default values and sets this.
    private final String m_rowHeaderPrefix;

    // a hash set where we store row header read in - to ensure ID uniquity
    private final HashSet<String> m_rowIDhash;

    // if that is true we don't return any more rows.
    private boolean m_exceptionThrown;
    
    // if true we need to replace the char in the double tokens with a '.'
    private boolean m_customDecimalSeparator;
    
    private char m_decSeparator;

    /**
     * The RowIterator for the FileTable.
     * 
     * @param tableSpec The spec defining the structure of the rows to create.
     * @param frSettings object containing the wheres and hows to read the data
     * @throws IOException If it couldn't open the data file.
     */
    FileRowIterator(final FileReaderSettings frSettings,
            final DataTableSpec tableSpec) throws IOException {

        m_tableSpec = tableSpec;
        m_frSettings = frSettings;

        m_tokenizer = new FileTokenizer(m_frSettings.createNewInputReader());

        // set the tokenizer related settings in the tokenizer
        m_tokenizer.setSettings(frSettings);

        m_decSeparator = frSettings.getDecimalSeparator();
        m_customDecimalSeparator = m_decSeparator != '.';
        
        m_rowNumber = 1;
        m_exceptionThrown = false;

        // set the row prefix here (so we don't have to go through this for each
        // row separately). If this prefix is set it will be used - otherwise
        // it's safe to assume the file contains row headers!
        if (frSettings.getFileHasRowHeaders()) {
            // settings tell us to use the first column as row headers. We will.
            m_rowHeaderPrefix = null;
        } else {
            // Won't get them from the file. Get the user settings or the
            // default from the settings structure
            if (frSettings.getRowHeaderPrefix() != null) {
                m_rowHeaderPrefix = frSettings.getRowHeaderPrefix();
            } else {
                m_rowHeaderPrefix = FileReaderSettings.DEF_ROWPREFIX;
            }
        }

        m_rowIDhash = new HashSet<String>();

        // if the column headers are stored in the data file, we must read
        // them (the first line) and discard them (if they are actually used
        // from the file they should have been stored in the table spec).
        if (frSettings.getFileHasColumnHeaders()) {
            if (hasNext()) { // call this first to eat up empty lines
                String token = m_tokenizer.nextToken();
                while (!frSettings.isRowDelimiter(token)) {
                    token = m_tokenizer.nextToken();
                }
            }
        }

    } // FileRowIterator(FileTableSpec)

    /**
     * @see de.unikn.knime.core.data.RowIterator#hasNext()
     */
    public boolean hasNext() {

        if (m_exceptionThrown) {
            // after we've thrown an exception don't even try to read more.
            return false;
        }
        String token;

        // we must eat all empty lines to see if there is more meat in the file
        // DO NOT call this function if you are not at the beginning of the row
        while (true) {
            token = m_tokenizer.nextToken();

            if (token == null) {
                return false;
            }

            if (m_frSettings.getIgnoreEmtpyLines()
                    && m_frSettings.isRowDelimiter(token)) {
                // get the next token.
                continue;
            }

            m_tokenizer.pushBack();
            return true;
        }
    }

    /**
     * @see de.unikn.knime.core.data.RowIterator#next()
     */
    public DataRow next() {
        int noOfCols = m_tableSpec.getNumColumns();
        String token;
        boolean isMissingCell;
        DataCell rowHeader;
        DataCell[] row = new DataCell[noOfCols];

        // before anything else: check if there is more in the stream
        // this MUST be called, because it is the procedure that removes empty
        // lines (if we are supposed to).
        if (!hasNext()) {
            throw new NoSuchElementException(
                    "The row iterator proceeded beyond the last line of '"
                            + m_frSettings.getDataFileLocation().toString()
                            + "'.");
        }

        // counts the number of columns we've read and created
        int createdCols = 0;

        // first, create a row header. This will also read it from file, if any.
        rowHeader = createRowHeader(m_rowNumber);

        // we made sure before that there is at least one token in the stream
        assert rowHeader != null;

        // Now, read the columns until we have enough or see a row delimiter
        while (createdCols < noOfCols) {

            try {
                token = m_tokenizer.nextToken();
            } catch (FileTokenizerException fte) {
                m_exceptionThrown = true;
                throw createException(fte.getMessage() + " (Source: "
                        + m_frSettings.getDataFileLocation() + ", line "
                        + m_tokenizer.getLineNumber() + ")", m_tokenizer
                        .getLineNumber(), rowHeader, row);
            }
            // row delims are returned as token
            if ((token == null) || m_frSettings.isRowDelimiter(token)) {
                // line ended early.
                m_tokenizer.pushBack();
                // we need the row delim in the file, for after the loop
                break;
            }

            // check if we have a missing cell (i.e. nothing between two
            // column delimiters).
            if (token.equals("") && (!m_tokenizer.lastTokenWasQuoted())) {
                isMissingCell = true;
            } else if (token.equals(m_frSettings
                    .getMissingValueOfColumn(createdCols))) {
                // equals(null) if it was not specified - which is fine.
                isMissingCell = true;
            } else {
                isMissingCell = false;
            }

            DataColumnSpec cSpec = m_tableSpec.getColumnSpec(createdCols);
            // now get that new cell (it throws something at us if it couldn't)
            row[createdCols] = createNewDataCellOfType(cSpec.getType(), token,
                    isMissingCell, rowHeader, row);

            createdCols++;

        } // end of while(createdCols < noOfCols)

        // In case we've seen a row delimiter before the row was complete:
        // puke and die
        if (createdCols < noOfCols) {
            m_exceptionThrown = true;
            throw createException("Too few data elements in row "
                    + "(Source: '" + m_frSettings.getDataFileLocation()
                    + "' line: " + m_tokenizer.getLineNumber() + ")",
                    m_tokenizer.getLineNumber(), rowHeader, row);
        }
        // now read the row delimiter from the file, and in case there are more
        // data items in the file than we needed for one row: barf and die.
        token = m_tokenizer.nextToken();
        if (!m_frSettings.isRowDelimiter(token)) {
            m_exceptionThrown = true;
            throw createException("Too many data elements in row "
                    + "(Source: '" + m_frSettings.getDataFileLocation()
                    + "' line: " + m_tokenizer.getLineNumber() + ")",
                    m_tokenizer.getLineNumber(), rowHeader, row);
        }
        m_rowNumber++;

        return new DefaultRow(rowHeader, row);
    } // next()

    /**
     * The method creates a default <code>DataCell</code> of the type passed
     * in, and initializes its value from the <code>data</code> string
     * (converting it to the corresponding type). Throws a
     * java.lang.NumberFormatException if the <code>data</code> argument
     * couldn't be converted to the appropriate type or a
     * <code> IllegalStateException
     * </code> if the <code> type </code> passed in
     * is not supported.
     * 
     * @param type the type of DataCell to be created, supported are Double-,
     *            Int-, and StringTypes.
     * @param data the string representation of the value that will be set in
     *            the DataCell created.
     * @param createMissingCell If set true a missing cell of the passed type
     *            will be created. The <code> data </code> parameter is ignored
     *            then.
     * @param rowHeader the rowID - for nice error messages only.
     * @param row the cells of the row created so far. Used for messages only.
     * @return <code> DataCell </code> of the type specified in <code> type
     * </code> .
     */
    private DataCell createNewDataCellOfType(final DataType type,
            final String data, final boolean createMissingCell,
            final DataCell rowHeader, final DataCell[] row) {

        if (createMissingCell) {
            return DataType.getMissingCell();
        }
        if (type.equals(StringCell.TYPE)) {
            return new StringCell(data);
            // also timming strings now. As per TG.
        } else if (type.equals(IntCell.TYPE)) {
            try {
                int val = Integer.parseInt(data);
                return new IntCell(val);
            } catch (NumberFormatException nfe) {
                m_exceptionThrown = true;
                throw createException("Wrong data format. In line "
                        + m_tokenizer.getLineNumber() + " read '" + data
                        + "' for an integer", m_tokenizer.getLineNumber(),
                        rowHeader, row);
            }
        } else if (type.equals(DoubleCell.TYPE)) {
            String dblData = data;
            if (m_customDecimalSeparator) {
                // we must reject tokens with a '.'. 
                if (data.indexOf('.') >= 0) {
                    throw createException("Wrong data format. In line "
                            + m_tokenizer.getLineNumber() + " read '" + data
                            + "' for a floating point.", m_tokenizer
                            .getLineNumber(), rowHeader, row);
                }
                dblData = data.replace(m_decSeparator, '.');
            }
            try {
                double val = Double.parseDouble(dblData);
                return new DoubleCell(val);
            } catch (NumberFormatException nfe) {
                m_exceptionThrown = true;
                throw createException("Wrong data format. In line "
                        + m_tokenizer.getLineNumber() + " read '" + data
                        + "' for a floating point.", m_tokenizer
                        .getLineNumber(), rowHeader, row);
            }
        } else {
            m_exceptionThrown = true;
            throw createException("Cannot create DataCell of type "
                    + type.toString() + ". Looks like an internal error. "
                    + "Sorry.", m_tokenizer.getLineNumber(), rowHeader, row);
        }
    } // createNewDataCellOfType(Class,String,boolean)

    /*
     * Creates a StringCell containing the row header. If the filereader
     * settings tell us that there is one in the file - it will be read. The
     * header actually created depends on the member 'rowHeaderPrefix'. If it's
     * set (not null) it will be used to create the row header (plus the row
     * number) overriding any file row header just read. If it's null then the
     * one read from the file will be used (and there better be one). If the
     * file returns a missing token we create a row header like "
     * <missing>+RowNo". Returns null if EOF was reached before a row header (or
     * a delimiter) was read.
     */
    private StringCell createRowHeader(final int rowNumber) {

        // the constructor sets m_rowHeaderPrefix if the file doesn't have one
        assert (m_frSettings.getFileHasRowHeaders() 
                || (m_rowHeaderPrefix != null));

        // if there is a row header in the file we must read it - independend
        // of if we are going to use it or not.
        String fileHeader = null;
        if (m_frSettings.getFileHasRowHeaders()) {
            // read it away.
            fileHeader = m_tokenizer.nextToken();
            if (fileHeader == null) {
                return null; // seen EOF
            }

            if (m_frSettings.isRowDelimiter(fileHeader)) {
                // Oops, we've read an empty line. Push the delimiter back,
                // others need to see it too.
                m_tokenizer.pushBack();
                fileHeader = "";
            }
        }

        if (m_rowHeaderPrefix == null) {
            assert fileHeader != null;
            String newRowHeader;
            if (fileHeader.equals("") && !m_tokenizer.lastTokenWasQuoted()) {
                // seems we got a missing row delimiter. Let's build one.
                newRowHeader = DataType.getMissingCell().toString()
                        + rowNumber;
            } else {
                newRowHeader = fileHeader;
            }
            // see if it's unique - and if not make it unique.
            newRowHeader = uniquifyRowHeader(newRowHeader);

            return new StringCell(newRowHeader);

        } else {

            return new StringCell(m_rowHeaderPrefix + rowNumber);

        }
    }

    /*
     * checks if the newRowHeader is already in the hash set of all created row
     * headers and if so it adds some suffix to make it unique. It will return a
     * unique row header, which could be the same than the one passed in (and
     * adds any rowheader returned to the hash set).
     */
    private String uniquifyRowHeader(final String newRowHeader) {

        String unique;

        if (!m_rowIDhash.contains(newRowHeader)) {
            unique = newRowHeader;
        } else {
            // add the line number to the suffix - and if that didn't help, add
            // a running index until it is unique.
            unique = newRowHeader;
            String prefix = unique;
            int idx = 2;
            while (m_rowIDhash.contains(unique)) {
                unique = prefix + "_" + idx;
                idx++;
            }
        }

        assert !m_rowIDhash.contains(unique);
        m_rowIDhash.add(unique);

        return unique;

    }

    private FileReaderException createException(final String msg,
            final int lineNumber, final DataCell rowHeader,
            final DataCell[] cellsRead) {
        DataCell[] errCells = new DataCell[cellsRead.length];
        System.arraycopy(cellsRead, 0, errCells, 0, errCells.length);

        for (int c = 0; c < errCells.length; c++) {
            if (errCells[c] == null) {
                errCells[c] = DataType.getMissingCell();
            }
        }

        DataCell errRowHeader = new StringCell("ERROR_ROW ("
                + rowHeader.toString() + ")");

        DataRow errRow = new DefaultRow(errRowHeader, errCells);

        return new FileReaderException(msg, errRow, lineNumber);

    }

} // FileRowIterator
