/*
 * #%L
 * Netarchivesuite - archive - test
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
package dk.netarkivet.archive.arcrepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import org.archive.io.arc.ARCConstants;
import org.archive.format.arc.ARCConstants;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.arc.ARCRecord;
import org.archive.io.arc.ARCRecordMetaData;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import dk.netarkivet.archive.ArchiveSettings;
import dk.netarkivet.archive.arcrepository.distribute.JMSArcRepositoryClient;
import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.distribute.ChannelsTesterHelper;
import dk.netarkivet.common.distribute.JMSConnectionMockupMQ;
import dk.netarkivet.common.distribute.arcrepository.ARCLookup;
import dk.netarkivet.common.distribute.arcrepository.ArcRepositoryClient;
import dk.netarkivet.common.distribute.arcrepository.BitarchiveRecord;
import dk.netarkivet.common.distribute.arcrepository.ViewerArcRepositoryClient;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.StreamUtils;
import dk.netarkivet.common.utils.arc.ARCKey;
import dk.netarkivet.testutils.ReflectUtils;
import dk.netarkivet.testutils.StringAsserts;
import dk.netarkivet.testutils.TestFileUtils;
import dk.netarkivet.testutils.preconfigured.ReloadSettings;

@SuppressWarnings({"unchecked"})
public class ARCLookupTester {
    private ViewerArcRepositoryClient realArcRepos;
    private static ARCLookup lookup;
    protected ARCReader arcReader;
    ReloadSettings rs = new ReloadSettings();

    @Before
    public void setUp() throws Exception {
        rs.setUp();
        dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL = new URI(
                "http://netarkivet.dk/netarchive_alm/billeder/spacer.gif");

        JMSConnectionMockupMQ.useJMSConnectionMockupMQ();
        ChannelsTesterHelper.resetChannels();

        // Although we also need some real data
        File WORKING_DIR = dk.netarkivet.archive.distribute.arcrepository.TestInfo.WORKING_DIR;

        FileUtils.removeRecursively(WORKING_DIR);
        TestFileUtils.copyDirectoryNonCVS(dk.netarkivet.archive.distribute.arcrepository.TestInfo.ORIGINALS_DIR,
                WORKING_DIR);
        FileUtils.createDir(new File(WORKING_DIR, "viewerproxy"));

        Settings.set(ArchiveSettings.DIRS_ARCREPOSITORY_ADMIN,
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.LOG_PATH.getAbsolutePath());
        Settings.set(CommonSettings.USE_REPLICA_ID, "ONE");
        Settings.set(ArchiveSettings.DIRS_ARCREPOSITORY_ADMIN, new File(WORKING_DIR, "admin-data").getAbsolutePath());
        Settings.set(ArchiveSettings.DIRS_ARCREPOSITORY_ADMIN,
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.LOG_PATH.getAbsolutePath());

        // This is "real" in the sense that it returns records from a
        // bitarchive-
        // like file system instaed of making them up.
        realArcRepos = new LocalArcRepositoryClient(new File(
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.ARCHIVE_DIR, "filedir"));

        lookup = new ARCLookup(realArcRepos);
        lookup.setIndex(dk.netarkivet.archive.distribute.arcrepository.TestInfo.INDEX_DIR_2_3);
    }

    @After
    public void tearDown() throws Exception {
        if (realArcRepos != null) {
            realArcRepos.close();
        }
        if (arcReader != null) {
            arcReader.close();
        }
        FileUtils.removeRecursively(dk.netarkivet.archive.distribute.arcrepository.TestInfo.WORKING_DIR);
        JMSConnectionMockupMQ.clearTestQueues();
        rs.tearDown();
    }

    @Test(expected = ArgumentNotValid.class)
    public void testSetNullIndex() throws Exception {
        ArcRepositoryClient arcrep = new TestArcRepositoryClient();
        ARCLookup lookup = new ARCLookup(arcrep);
        lookup.setIndex(null);
    }

    @Test(expected = ArgumentNotValid.class)
    public void testSetInvalidIndex() throws Exception {
        ArcRepositoryClient arcrep = new TestArcRepositoryClient();
        ARCLookup lookup = new ARCLookup(arcrep);
        lookup.setIndex(TestInfo.TMP_FILE);
    }

    @Test
    public void testSetIndex() throws Exception {
        ArcRepositoryClient arcrep = new TestArcRepositoryClient();
        ARCLookup lookup = new ARCLookup(arcrep);
        lookup.setIndex(dk.netarkivet.archive.distribute.arcrepository.TestInfo.INDEX_DIR_2_3);
        lookup.setIndex(dk.netarkivet.archive.distribute.arcrepository.TestInfo.INDEX_DIR_2_3);
    }

    /**
     * Test that lookup returns real arcrecord data, and that it is correct TODO: This test is bad: It may not clean up
     * properly on fail, and it is really an integrity test. Move and clean up!
     */
    @Test
    public void testLookup() throws Exception {
        Settings.set(ArchiveSettings.BITARCHIVE_SERVER_FILEDIR,
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.ARCHIVE_DIR.getAbsolutePath());
        Settings.set(CommonSettings.DIR_COMMONTEMPDIR, new File(
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.WORKING_DIR, "serverdir").getAbsolutePath());

        // Get the data from the bitarchive
        InputStream is = lookup.lookup(dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL)
                .getInputStream();
        assertNotNull("Should be able to find image", is);
        byte[] got = readFully(is);
        is.close();
        // Read the expected result from the "local" copy
        String filename = dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL_KEY.getFile().getName();
        File in = new File(dk.netarkivet.archive.distribute.arcrepository.TestInfo.WORKING_DIR, filename);
        arcReader = ARCReaderFactory.get(in,
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL_KEY.getOffset());
        ARCRecord arc = (ARCRecord) arcReader.get();
        BitarchiveRecord result = new BitarchiveRecord(arc, filename);
        arc.close();
        byte[] wanted = StreamUtils.inputStreamToBytes(result.getData(), (int) result.getLength());
        assertEquals("Did not get expected data: ", new String(got), new String(wanted));
    }

    /**
     * Test that when a uri with escaped characters is looked up, the uri is urldecoded first.
     */
    @Test
    public void testLookupWithCurlyBrackets() throws Exception {
        Settings.set(ArchiveSettings.BITARCHIVE_SERVER_FILEDIR,
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.ARCHIVE_DIR.getAbsolutePath());
        Settings.set(CommonSettings.DIR_COMMONTEMPDIR, new File(
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.WORKING_DIR, "serverdir").getAbsolutePath());
        Field searcher_field = ARCLookup.class.getDeclaredField("luceneSearcher");
        searcher_field.setAccessible(true);
        // Set the searcher to null. lookup will then throw a message with the
        // actual URI
        searcher_field.set(lookup, null);
        try {
            lookup.lookup(new URI("http://www.adomain.dk/?key=%7B12345%7D"));
            fail("Should get IOFailure when lucene lookup is null");
        } catch (IOFailure e) {
            assertTrue("Expect error message to contain decoded uri but was '" + e.getMessage() + "'", e.getMessage()
                    .contains("http://www.adomain.dk/?key={12345}"));
        }
    }

    @Test
    public void testLuceneLookup() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method luceneLookup = ReflectUtils.getPrivateMethod(ARCLookup.class, "luceneLookup", String.class);
        ARCKey key = (ARCKey) luceneLookup.invoke(lookup, "http://foo.bar");
        assertNull("Should get null key on not found", key);

        key = (ARCKey) luceneLookup.invoke(lookup, "http://www.raeder.dk/robots.txt");
        assertEquals("Should have found right file",
                "2-2-20060731110420-00000-sb-test-har-001.statsbiblioteket.dk.arc", key.getFile().getName());
        assertEquals("Should have found right offset", 1941, key.getOffset());
    }

    /**
     * Test that asking for a given URL makes ARCArchiveAccess ask the right stuff in the arc repository client and
     * returns it correctly.
     *
     * @throws Exception
     */
    @Test
    public void testLookupInputStream() throws Exception {
        realArcRepos.close();
        lookup = new ARCLookup(new TestArcRepositoryClient());
        lookup.setIndex(dk.netarkivet.archive.distribute.arcrepository.TestInfo.INDEX_DIR_2_3);
        InputStream is = lookup.lookup(dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL)
                .getInputStream();
        // Read a header line
        String line1 = readLine(is);
        // Read blank line
        String line2 = readLine(is);
        // Read blank line
        String line3 = readLine(is);
        // Read rest of content
        String s = new String(readFully(is));
        ARCKey retKey = new ARCKey(s.substring(0, s.indexOf(" ")), Long.parseLong(s.substring(s.indexOf(" ") + 1)));
        is.close();
        assertEquals("Result should start with status code", "HTTP/1.1 200 OK", line1);
        assertEquals("Location should be set to the ARC file", "Location: "
                + dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL_KEY.getFile(), line2);
        assertEquals("There should be an empty line after the headers", "", line3);
        assertEquals("Should get right file for gif",
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL_KEY.getFile(), retKey.getFile());
        assertEquals("Should get right offset for gif",
                dk.netarkivet.archive.distribute.arcrepository.TestInfo.GIF_URL_KEY.getOffset(), retKey.getOffset());
    }

    private class LocalArcRepositoryClient extends JMSArcRepositoryClient {
        File fileDir;

        public LocalArcRepositoryClient(File fileDir) {
            this.fileDir = fileDir;
        }

        public BitarchiveRecord get(String arcFile, long index) {
            try {
                ARCReader reader = ARCReaderFactory.get(new File(fileDir, arcFile), index);
                ARCRecord record = (ARCRecord) reader.get();
                return new BitarchiveRecord(record, arcFile);
            } catch (IOException e) {
                fail("Can't find file " + arcFile + ": " + e);
                return null;
            }
        }
    }

    /**
     * Fake arc repository client which on get returns a fake record which is ok.
     */
    private class TestArcRepositoryClient extends JMSArcRepositoryClient {
        public TestArcRepositoryClient() {
            super();
        }

        /**
         * Returns an OK BitarchiveRecord. Content is simply arcfile name and index encoded in a stream.
         */
        public BitarchiveRecord get(String arcFile, long index) {
            final Map<String, Object> metadata = new HashMap<String, Object>();
            for (String header_field : (List<String>) ARCConstants.REQUIRED_VERSION_1_HEADER_FIELDS) {
                metadata.put(header_field, "");
            }
            metadata.put(ARCConstants.ABSOLUTE_OFFSET_KEY, Long.valueOf(0L)); // Dummy
            // offset
            byte[] data = ("HTTP/1.1 200 OK\nLocation: " + arcFile + "\n\n" + arcFile + " " + index).getBytes();
            // TODO replace this by something else, or remove ?
            // (ARCConstants.LENGTH_HEADER_FIELD_KEY)
            // does not exist in Heritrix 1.10+
            // metadata.put(ARCConstants.LENGTH_HEADER_FIELD_KEY,
            // Integer.toString(data.length));
            // Note: ARCRecordMetadata.getLength() now reads the contents of the
            // LENGTH_FIELD_KEY
            // instead of LENGTH_HEADER_FIELD_KEY
            metadata.put(ARCConstants.LENGTH_FIELD_KEY, Integer.toString(data.length));
            try {
                ARCRecordMetaData meta = new ARCRecordMetaData(arcFile, metadata);
                return new BitarchiveRecord(new ARCRecord(new ByteArrayInputStream(data), meta), arcFile);
            } catch (IOException e) {
                fail("Cant't create metadata record");
                return null;
            }
        }

    }

    private byte[] readFully(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i = is.read();
        while (i != -1) {
            baos.write(i);
            i = is.read();
        }
        return baos.toByteArray();
    }

    /**
     * Read a line of bytes from an InputStream. Useful when an InputStream may contain both text and binary data.
     *
     * @param inputStream A source of data
     * @return A line of text read from inputStream, with terminating \r\n or \n removed, or null if no data is
     * available.
     * @throws IOException on trouble reading from input stream
     */
    private String readLine(InputStream inputStream) throws IOException {
        byte[] rawdata = readRawLine(inputStream);
        if (rawdata == null) {
            return null;
        }
        int len = rawdata.length;
        if (len > 0) {
            if (rawdata[len - 1] == '\n') {
                len--;
                if (len > 0) {
                    if (rawdata[len - 1] == '\r') {
                        len--;
                    }
                }
            }
        }
        return new String(rawdata, 0, len);
    }

    /**
     * Reads a raw line from an InputStream, up till \n. Since HTTP allows \r\n and \n as terminators, this gets the
     * whole line. This code is adapted from org.apache.commons.httpclient.HttpParser
     *
     * @param inputStream A stream to read from.
     * @return Array of bytes read or null if none are available.
     * @throws IOException if the underlying reads fail
     */
    private static byte[] readRawLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ch;
        while ((ch = inputStream.read()) >= 0) {
            buf.write(ch);
            if (ch == '\n') {
                break;
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        return buf.toByteArray();
    }
}
