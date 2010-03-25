/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2007 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
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

package dk.netarkivet.archive.arcrepository.bitpreservation;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.arc.FileBatchJob;
import dk.netarkivet.testutils.Serial;
import dk.netarkivet.testutils.TestFileUtils;

/**
 * Unit tests for the abstract class FileBatchJob, and its subclass
 * FileListJob.
 */
public class FileListJobTester extends TestCase {

     public void setUp() {
        TestFileUtils.copyDirectoryNonCVS(TestInfo.ORIGINALS_DIR,
                TestInfo.WORKING_DIR);
    }

    public void tearDown() {
        FileUtils.removeRecursively(TestInfo.WORKING_DIR);
    }

    /**
     * Test that FileBatchJob outputs the right data.
     */
    public void testJobRuns() {
        File bitarchive = new File(TestInfo.WORKING_DIR, "bitarchive1");
        File arcfile = new File(bitarchive, "integrity1.ARC");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        FileBatchJob job = new FileListJob();
        job.processFile(arcfile, baos);
        assertEquals("Job should return filename + newline", arcfile.getName()
                +"\n", baos.toString());
    }

    /**
     * Tests serializability of this class, under the assumption that its
     * toString() method is dependent on its entire relevant state.
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void testSerializable() throws IOException, ClassNotFoundException {
        FileBatchJob job = new FileListJob();
        FileBatchJob job2 = (FileBatchJob) Serial.serial(job);
        assertEquals("Should have same toString()", job.toString(),
                job2.toString());
    }

}