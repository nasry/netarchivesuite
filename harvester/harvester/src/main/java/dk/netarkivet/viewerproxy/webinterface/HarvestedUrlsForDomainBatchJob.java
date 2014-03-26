/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2012 The Royal Danish Library, the Danish State and
 * University Library, the National Library of France and the Austrian
 * National Library.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.viewerproxy.webinterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.DomainUtils;
import dk.netarkivet.common.utils.FixedUURI;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.archive.ArchiveBatchJob;
import dk.netarkivet.common.utils.archive.ArchiveRecordBase;
import dk.netarkivet.common.utils.batch.ArchiveBatchFilter;
import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.Constants;

/**
 * Batchjob that extracts lines referring to a specific domain from a crawl log.
 * The batch job should be restricted to run on metadata files for a specific
 * job only, using the {@link #processOnlyFilesMatching(String)} construct.
 */
public class HarvestedUrlsForDomainBatchJob extends ArchiveBatchJob {

    // logger
    private final Log log = LogFactory.getLog(getClass().getName());
   
    /** Metadata URL for crawl logs. */
    private static final String SETUP_URL_FORMAT
            = String.format("metadata://%s/crawl/logs/crawl.log", 
                    Settings.get(CommonSettings.ORGANIZATION));
    /** The domain to extract crawl.log lines for. */
    final String domain;

    /**
     * Initialise the batch job.
     *
     * @param domain The domain to get crawl.log lines for.
     */
    public HarvestedUrlsForDomainBatchJob(String domain) {
        ArgumentNotValid.checkNotNullOrEmpty(domain, "domain");
        this.domain = domain;

        /**
        * Two week in milliseconds.
        */
        batchJobTimeout = 7* Constants.ONE_DAY_IN_MILLIES;
    }

    /**
     * Does nothing, no initialisation is needed.
     * @param os Not used.
     */
    @Override
    public void initialize(OutputStream os) {
    }
    
    @Override
    public ArchiveBatchFilter getFilter() {
        return new ArchiveBatchFilter("OnlyCrawlLog") {
            @Override
            public boolean accept(ArchiveRecordBase record) {
                // All ARC records have a URL, but the WarcInfo records doesn't 
                if (record.bIsWarc){
                    // In the WARC file the warc-info hasn't a URL. the other
                    // records in the metadata file have that.
                    return (record.getHeader().getUrl() != null 
                            && record.getHeader().getUrl().startsWith(SETUP_URL_FORMAT));
                } else {
                    return record.getHeader().getUrl().startsWith(SETUP_URL_FORMAT);
                }
            }
        };
    }

    /**
     * Process a record on crawl log concerning the given domain to result.
     * @param record The record to process.
     * @param os The output stream for the result.
     *
     * @throws ArgumentNotValid on null parameters
     * @throws IOFailure on trouble processing the record.
     */
    @Override
    public void processRecord(ArchiveRecordBase record, OutputStream os) {
        ArgumentNotValid.checkNotNull(record, "ArchiveRecordBase record");
        ArgumentNotValid.checkNotNull(os, "OutputStream os");
        BufferedReader arcreader
                = new BufferedReader(new InputStreamReader(record.getInputStream()));
        try {
            for(String line = arcreader.readLine(); line != null;
                line = arcreader.readLine()) {

                // Parse a single crawl-log line into parts
                // The parts are here separated by white space.
                // part 4 of the crawl-line is the url component
                // part 6 of the crawl-line is the discovery url component
                // Cf. "http://crawler.archive.org/articles/user_manual
                // /analysis.html#logs"

                String[] parts = line.split("\\s+");
                final int URL_PART_INDEX = 3;
                final int DISCOVERY_URL_PART_INDEX = 5;
                // The current crawl.log line is written to the outstream 
                // in two cases:
                // A. If it has a URL component (4th component) and 
                //    this URL belongs to the domain in question
                // B. If it has a Discovery URL (6th component) and 
                //    this URL belongs to the domain in question
                if (parts.length > 3 && DomainUtils.domainNameFromHostname(
                        new FixedUURI(parts[URL_PART_INDEX], 
                                true).getReferencedHost()).equals(domain)) {
                    os.write(line.getBytes("UTF-8"));
                    os.write('\n');

                } else if (parts.length > 5 && !parts[5].equals("-")
                        && DomainUtils.domainNameFromHostname(
                                new FixedUURI(
                                        parts[DISCOVERY_URL_PART_INDEX],
                                        true).getReferencedHost()).equals(
                                                domain)) {
                    os.write(line.getBytes("UTF-8"));
                    os.write('\n');
                }

            }
        } catch (IOException e) {
            throw new IOFailure("Unable to process (w)arc record", e);
        } finally {
            try {
                arcreader.close(); 
            } catch (IOException e) {
                log.warn("unable to close arcreader probably", e);
            }
        }
    }

    /**
     * Does nothing, no finishing is needed.
     * @param os Not used.
     */
    @Override
    public void finish(OutputStream os) {
    }
    
    /**
     * Humanly readable representation of this instance.
     * @return The class content.
     */
    @Override
    public String toString() {
        return getClass().getName() + ", with arguments: Domain = " + domain
                + ", Filter = " + getFilter();
    }
}
