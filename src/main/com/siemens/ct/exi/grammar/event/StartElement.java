/*
 * Copyright (C) 2007-2009 Siemens AG
 *
 * This program and its interfaces are free software;
 * you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.siemens.ct.exi.grammar.event;

import javax.xml.XMLConstants;

/**
 * TODO Description
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Joerg.Heuer@siemens.com
 * 
 * @version 0.3.20081103
 */

public class StartElement extends AbstractEvent {
	
	private String namespaceURI;
	private String localName;

	public StartElement(String uri, String localName) {
		super("SE");
		eventType = EventType.START_ELEMENT;

		this.namespaceURI = uri == null ? XMLConstants.NULL_NS_URI : uri;
		this.localName = localName;
	}

	public String getNamespaceURI() {
		return namespaceURI;
	}

	public void setNamespaceURI(String namespaceURI) {
		this.namespaceURI = namespaceURI;
	}

	public String getLocalName() {
		return localName;
	}

	public void setLocalName(String localName) {
		this.localName = localName;
	}

	public String toString() {
		return "SE(" + namespaceURI + ":" + localName + ")";
	}

	@Override
	public int hashCode() {
		return (eventType.ordinal() ^ namespaceURI.hashCode() ^ localName
				.hashCode());
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StartElement) {
			StartElement otherSE = (StartElement) obj;
			return (localName.equals(otherSE.localName) && namespaceURI
					.equals(otherSE.namespaceURI));
		} else {
			return false;
		}
	}
	
	@Override
	public int compareTo(Event o) {
		if (o instanceof StartElement) {
			// both SE events
			// CASE 1: first is smallest etc. (schema order)
			 return +1;
			// // CASE 2: lexical order
		} else {
			return super.compareTo(o);
		}
	}

}
