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
package dk.netarkivet.common.utils;

import static org.junit.Assert.assertEquals;

import org.dom4j.Document;
import org.dom4j.Node;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import dk.netarkivet.testutils.preconfigured.MoveTestFiles;

/**
 * Unit tests for the class XmlUtils.
 */
public class XmlUtilsTester {
    private final MoveTestFiles mtf = new MoveTestFiles(TestInfo.DATADIR, TestInfo.TEMPDIR);

    @Before
    public void setUp() throws Exception {
        mtf.setUp();
    }

    @After
    public void tearDown() throws Exception {
        mtf.tearDown();
    }

    @Test
    public void testSetNode() throws Exception {
        Document doc = XmlUtils.getXmlDoc(TestInfo.XML_FILE_1);
        Node node = doc.selectSingleNode(TestInfo.XML_FILE_1_XPATH_1);
        assertEquals("Should have original value at start", "Should go away", node.getText());
        XmlUtils.setNode(doc, TestInfo.XML_FILE_1_XPATH_1, "newValue");
        assertEquals("Should have new value after setting it", "newValue", node.getText());
    }
}
