/*
 * Copyright (C) 2007-2011 Siemens AG
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

package com.siemens.ct.exi.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import com.siemens.ct.exi.Constants;
import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.FidelityOptions;
import com.siemens.ct.exi.core.container.NamespaceDeclaration;
import com.siemens.ct.exi.datatype.BooleanDatatype;
import com.siemens.ct.exi.datatype.QNameDatatype;
import com.siemens.ct.exi.datatype.QNameDatatypeNoAdds;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.exceptions.ErrorHandler;
import com.siemens.ct.exi.grammar.Grammar;
import com.siemens.ct.exi.grammar.event.StartElement;
import com.siemens.ct.exi.grammar.rule.Rule;
import com.siemens.ct.exi.grammar.rule.SchemaLessStartTag;
import com.siemens.ct.exi.helpers.DefaultErrorHandler;

/**
 * Shared functionality between EXI Body Encoder and EXI Body Decoder.
 * 
 * @author Daniel.Peintner.EXT@siemens.com
 * @author Joerg.Heuer@siemens.com
 * 
 * @version 0.6
 */

public abstract class AbstractEXIBodyCoder {

	// xsi:type & nil
	static final QName XSI_NIL = new QName(
			XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, Constants.XSI_NIL);
	static final QName XSI_TYPE = new QName(
			XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, Constants.XSI_TYPE);

	// factory
	protected final EXIFactory exiFactory;

	protected Grammar grammar;
	protected FidelityOptions fidelityOptions;
	protected boolean preservePrefix;
	protected boolean preserveLexicalValues;

	// error handler
	protected ErrorHandler errorHandler;

	// QName and Boolean datatype (coder)
	protected QNameDatatype qnameDatatype;
	protected BooleanDatatype booleanDatatype;

	// element-context and rule (stack) while traversing the EXI document
	protected ElementContext elementContext;
	protected Rule currentRule;
	protected ElementContext[] elementContextStack;
	protected int elementContextStackIndex;
	public static final int INITIAL_STACK_SIZE = 16;

	// SE pool
	protected Map<QName, StartElement> runtimeElements;

	public AbstractEXIBodyCoder(EXIFactory exiFactory) throws EXIException {
		this.exiFactory = exiFactory;
		// QName datatype (coder)
		if (exiFactory.usesProfile(EXIFactory.ULTRA_CONSTRAINED_DEVICE_PROFILE)) {
			qnameDatatype = new QNameDatatypeNoAdds(this, null);
		} else {
			qnameDatatype = new QNameDatatype(this, null);
		}

		initFactoryInformation();

		// use default error handler per default
		this.errorHandler = new DefaultErrorHandler();

		// init once (runtime lists et cetera)
		runtimeElements = new HashMap<QName, StartElement>();
		elementContextStack = new ElementContext[INITIAL_STACK_SIZE];

		// Boolean datatype
		booleanDatatype = new BooleanDatatype(null);
	}

	protected void initFactoryInformation() throws EXIException {
		this.grammar = exiFactory.getGrammar();
		this.fidelityOptions = exiFactory.getFidelityOptions();

		// preserve prefixes
		preservePrefix = fidelityOptions
				.isFidelityEnabled(FidelityOptions.FEATURE_PREFIX);

		// preserve lecicalValues
		preserveLexicalValues = fidelityOptions
				.isFidelityEnabled(FidelityOptions.FEATURE_LEXICAL_VALUE);

		qnameDatatype.setPreservePrefix(preservePrefix);
		qnameDatatype.setGrammarURIEnties(grammar.getGrammarEntries());

	}

	public void setErrorHandler(ErrorHandler errorHandler) {
		this.errorHandler = errorHandler;
	}

	// re-init (rule stack etc)
	protected void initForEachRun() throws EXIException, IOException {
		// clear runtime rules
		runtimeElements.clear();

		// possible document/fragment grammar
		currentRule = exiFactory.isFragment() ? grammar.getFragmentGrammar()
				: grammar.getDocumentGrammar();

		// (core) context
		elementContextStackIndex = 0;
		elementContextStack[elementContextStackIndex] = elementContext = new ElementContext(
				null, currentRule);

		qnameDatatype.initForEachRun();
	}

	protected final void declarePrefix(String pfx, String uri) {
		if (elementContext.nsDeclarations == null) {
			elementContext.nsDeclarations = new ArrayList<NamespaceDeclaration>();
		}
		assert (elementContext.nsDeclarations
				.contains(new NamespaceDeclaration(uri, pfx)) == false);
		elementContext.nsDeclarations.add(new NamespaceDeclaration(uri, pfx));
	}

	// // just works for decoder
	// public final String getPrefix(String uri) {
	// return uriToPrefix.get(uri);
	// }

	public final String getURI(String prefix) {
		// check all stack items expect last one (in reverse order)
		for (int i = elementContextStackIndex; i > 0; i--) {
			ElementContext ec = elementContextStack[i];
			if (ec.nsDeclarations != null) {
				for (NamespaceDeclaration ns : ec.nsDeclarations) {
					if (ns.prefix.equals(prefix)) {
						return ns.namespaceURI;
					}
				}
			}
		}
		return null;
	}

	protected final void pushElement(StartElement se, Rule contextRule) {
		// update "rule" item of current peak (for popElement() later on)
		// elementContext.rule = currentRule;
		elementContext.rule = contextRule;
		// set "new" current-rule
		currentRule = se.getRule();
		// create new stack item & push it
		pushElementContext(new ElementContext(se.getQName(), currentRule));
	}

	protected final void pushElementContext(ElementContext elementContext) {
		this.elementContext = elementContext;
		++elementContextStackIndex;
		// array needs to be extended?
		if (elementContextStack.length == elementContextStackIndex) {
			ElementContext[] elementContextStackNew = new ElementContext[elementContextStack.length << 2];
			System.arraycopy(elementContextStack, 0, elementContextStackNew, 0,
					elementContextStack.length);
			elementContextStack = elementContextStackNew;
		}
		elementContextStack[elementContextStackIndex] = elementContext;
	}

	protected final void popElement() {
		assert (this.elementContextStackIndex > 0);
		// pop element from stack
		elementContextStack[elementContextStackIndex--] = null; // let gc do the
																// rest
		elementContext = elementContextStack[elementContextStackIndex];
		// update current rule to new (old) element stack
		currentRule = elementContext.rule;
	}

	protected StartElement getGenericStartElement(QName qname) {
		// is there a global element that should be used
		StartElement nextSE = grammar.getGlobalElement(qname);
		if (nextSE == null) {
			// ultra-constrained device profile
			if (exiFactory
					.usesProfile(EXIFactory.ULTRA_CONSTRAINED_DEVICE_PROFILE)) {
				nextSE = new StartElement(qname);
				nextSE.setRule(grammar.getUrTypeGrammar());
			} else {
				// no global element --> runtime start element
				nextSE = runtimeElements.get(qname);
				if (nextSE == null) {
					// create new start element and new runtime rule
					nextSE = new StartElement(qname);
					nextSE.setRule(new SchemaLessStartTag());
					// add element to runtime map
					runtimeElements.put(qname, nextSE);
				}
			}
		}

		return nextSE;
	}

	protected QName getElementContextQName() {
		return elementContextStack[elementContextStackIndex].qname;
	}

	protected void setQNameAsString(String sqname) {
		elementContextStack[elementContextStackIndex].sqname = sqname;
	}

	protected String getQNameAsString() {
		return elementContextStack[elementContextStackIndex].sqname;
	}

	/*
	 * 
	 */
	protected void throwWarning(String message) {
		errorHandler.warning(new EXIException(message + ", options="
				+ exiFactory.getFidelityOptions()));
		// System.err.println(message);
	}

	static final class ElementContext {
		final QName qname;
		public String sqname;
		Rule rule; // may be modified while coding
		// prefix declarations
		List<NamespaceDeclaration> nsDeclarations;

		public ElementContext(QName qname, Rule rule) {
			this.qname = qname;
			this.rule = rule;
		}
	}
}