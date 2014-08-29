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
package dk.netarkivet.archive.arcrepositoryadmin;

import dk.netarkivet.archive.arcrepository.distribute.StoreMessage;

/**
 * Class needed to test the constructor for FilePreservationStatus, which takes an ArcRepositoryEntry as one of its
 * arguments. The constructor of ArcRepositoryEntry is package private.
 */
public class MyArcRepositoryEntry extends ArcRepositoryEntry {

    public MyArcRepositoryEntry(String filename, String md5sum, StoreMessage replyInfo) {
        super(filename, md5sum, replyInfo);

    }

}
