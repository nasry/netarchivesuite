/*
 * #%L
 * Netarchivesuite - common - test
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.common.utils.cdx;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.NetarkivetException;
import dk.netarkivet.common.utils.batch.FileBatchJob;

/**
 * Abstract class defining a batch job to run on an archive with ARC or WARC files. Each implementation is required to
 * define initialize() , processRecord() and finish() methods. The bitarchive application then ensures that the batch
 * job run initialize(), runs processRecord() on each record in each file in the archive, and then runs finish().
 */
@SuppressWarnings({"serial"})
public abstract class ArchiveBatchJob extends FileBatchJob {

    /** The total number of records processed. */
    protected int noOfRecordsProcessed = 0;

    /**
     * Initialize the job before runnning. This is called before the processRecord() calls start coming.
     *
     * @param os The OutputStream to which output data is written
     */
    public abstract void initialize(OutputStream os);

    /**
     * Exceptions should be handled with the handleException() method.
     *
     * @param os The OutputStream to which output data is written
     * @param record the object to be processed.
     */
    public abstract void processRecord(ArchiveRecord record, OutputStream os);

    /**
     * Finish up the job. This is called after the last processRecord() call.
     *
     * @param os The OutputStream to which output data is written
     */
    public abstract void finish(OutputStream os);

    /**
     * returns a BatchFilter object which restricts the set of arcrecords in the archive on which this batch-job is
     * performed. The default value is a neutral filter which allows all records.
     *
     * @return A filter telling which records should be given to processRecord().
     */
    public ArchiveBatchFilter getFilter() {
        return ArchiveBatchFilter.NO_FILTER;
    }

    /**
     * Accepts only ARC and ARCGZ files. Runs through all records and calls processRecord() on every record that is
     * allowed by getFilter(). Does nothing on a non-arc file.
     *
     * @param arcFile The ARC or ARCGZ file to be processed.
     * @param os the OutputStream to which output is to be written
     * @return true, if file processed successful, otherwise false
     * @throws ArgumentNotValid if either argument is null
     */
    public final boolean processFile(File arcFile, OutputStream os) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(arcFile, "arcFile");
        ArgumentNotValid.checkNotNull(os, "os");
        Log log = LogFactory.getLog(getClass().getName());
        long arcFileIndex = 0;
        boolean success = true;
        log.info("Processing file: " + arcFile.getName());

        try { // This outer try-catch block catches all unexpected exceptions
            // Create an ARCReader and retrieve its Iterator:
            ArchiveReader arcReader = null;

            try {
                arcReader = ArchiveReaderFactory.get(arcFile);
            } catch (IOException e) { // Some IOException
                handleException(e, arcFile, arcFileIndex);

                return false; // Can't process file after exception
            }

            try {
                Iterator<? extends ArchiveRecord> it = arcReader.iterator();
                /* Process all records from this Iterator: */
                log.debug("Starting processing records in ARCfile '" + arcFile.getName() + "'.");
                if (!it.hasNext()) {
                    log.debug("No ARCRecords found in ARCfile '" + arcFile.getName() + "'.");
                }
                while (it.hasNext()) {
                    log.debug("At begin of processing-loop");
                    ArchiveRecord record = null;

                    // Get a record from the file
                    try {
                        record = it.next();
                    } catch (Exception unexpectedException) {
                        handleException(unexpectedException, arcFile, arcFileIndex);
                        return false;
                    }
                    // Process with the job
                    try {
                        if (!getFilter().accept(record)) {
                            continue;
                        }
                        log.debug("Processing ArchiveRecord #" + noOfRecordsProcessed + " in file '"
                                + arcFile.getName() + "'.");
                        processRecord(record, os);
                        ++noOfRecordsProcessed;
                    } catch (NetarkivetException e) { // Our exceptions don't stop us
                        success = false;

                        // With our exceptions, we assume that just the processing
                        // of this record got stopped, and we can easily find the next
                        handleOurException(e, arcFile, arcFileIndex);
                    } catch (Exception e) {
                        success = false; // Strange exceptions do stop us

                        handleException(e, arcFile, arcFileIndex);
                        // With strange exceptions, we don't know if we've skipped records
                        break;
                    }
                    // Close the record
                    try {
                        // FIXME: Don't know how to compute this for warc-files
                        // computation for arc-files: long arcRecordOffset =
                        // record.getBodyOffset() + record.getMetaData().getLength();
                        // computation for warc-files (experimental)
                        long arcRecordOffset = record.getHeader().getOffset();

                        record.close();
                        arcFileIndex = arcRecordOffset;
                    } catch (IOException ioe) { // Couldn't close an ARCRecord
                        success = false;

                        handleException(ioe, arcFile, arcFileIndex);
                        // If close fails, we don't know if we've skipped records
                        break;
                    }
                    log.debug("At end of processing-loop");
                }
            } finally {
                try {
                    arcReader.close();
                } catch (IOException e) { // Some IOException
                    // TODO: Discuss whether exceptions on close cause filesFailed addition
                    handleException(e, arcFile, arcFileIndex);
                }
            }
        } catch (Exception unexpectedException) {
            handleException(unexpectedException, arcFile, arcFileIndex);
            return false;
        }
        return success;
    }

    private void handleOurException(NetarkivetException e, File arcFile, long index) {
        handleException(e, arcFile, index);
    }

    /**
     * When the org.archive.io.arc classes throw IOExceptions while reading, this is where they go. Subclasses are
     * welcome to override the default functionality which simply logs and records them in a list. TODO: Actually use
     * the arcfile/index entries in the exception list
     *
     * @param e An Exception thrown by the org.archive.io.arc classes.
     * @param arcfile The arcFile that was processed while the Exception was thrown
     * @param index The index (in the ARC file) at which the Exception was thrown
     * @throws ArgumentNotValid if e is null
     */
    public void handleException(Exception e, File arcfile, long index) throws ArgumentNotValid {
        ArgumentNotValid.checkNotNull(e, "e");
        Log log = LogFactory.getLog(getClass().getName());
        log.debug("Caught exception while running batch job " + "on file " + arcfile + ", position " + index + ":\n"
                + e.getMessage(), e);
        addException(arcfile, index, ExceptionOccurrence.UNKNOWN_OFFSET, e);
    }

    /**
     * Returns a representation of the list of Exceptions recorded for this ARC batch job. If called by a subclass, a
     * method overriding handleException() should always call super.handleException().
     *
     * @return All Exceptions passed to handleException so far.
     */
    public Exception[] getExceptionArray() {
        List<ExceptionOccurrence> exceptions = getExceptions();
        Exception[] exceptionList = new Exception[exceptions.size()];
        int i = 0;
        for (ExceptionOccurrence e : exceptions) {
            exceptionList[i++] = e.getException();
        }
        return exceptionList;
    }

    public int noOfRecordsProcessed() {
        return noOfRecordsProcessed;
    }

}
