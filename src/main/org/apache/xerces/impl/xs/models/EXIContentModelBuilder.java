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


package org.apache.xerces.impl.xs.models;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.xerces.impl.xs.SchemaGrammar;
import org.apache.xerces.impl.xs.SubstitutionGroupHandler;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSGrammarBucket;
import org.apache.xerces.impl.xs.XSParticleDecl;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSWildcard;

import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammar.event.EndElement;
import com.siemens.ct.exi.grammar.event.Event;
import com.siemens.ct.exi.grammar.event.StartElement;
import com.siemens.ct.exi.grammar.event.StartElementGeneric;
import com.siemens.ct.exi.grammar.event.StartElementNS;
import com.siemens.ct.exi.grammar.rule.Rule;
import com.siemens.ct.exi.grammar.rule.RuleElementSchemaInformed;
import com.siemens.ct.exi.grammar.rule.SchemaInformedRule;
import com.siemens.ct.exi.util.ExpandedName;

public abstract class EXIContentModelBuilder extends CMBuilder implements
		XMLErrorHandler {

	private static final boolean DEBUG = false;

	protected static final Event END_ELEMENT = new EndElement();

	protected static final boolean forUPA = false;

	protected SubstitutionGroupHandler subGroupHandler;

	protected XSModel xsModel;

	protected Map<XSElementDeclaration, Vector<XSObject>> elements;

	// errors while schema parsing
	protected List<String> schemaParsingErrors;

	// elements that appear while processing
	protected List<XSElementDeclaration> remainingElements;

	// scope for elements
	protected Map<XSElementDeclaration, List<XSComplexTypeDefinition>> enclosingTypes;

	public EXIContentModelBuilder() {
		super(new CMNodeFactory());
	}

	protected void initOnce() {
		elements = new HashMap<XSElementDeclaration, Vector<XSObject>>();
		remainingElements = new ArrayList<XSElementDeclaration>();
		enclosingTypes = new HashMap<XSElementDeclaration, List<XSComplexTypeDefinition>>();
		schemaParsingErrors = new ArrayList<String>();
	}

	protected void initEachRun() {
		elements.clear();
		remainingElements.clear();
		enclosingTypes.clear();
		schemaParsingErrors.clear();
	}

	public void loadGrammar(XMLInputSource xsdSource) throws EXIException {
		try {
			initEachRun();

			// load XSD schema & get XSModel
			XMLSchemaLoader sl = new XMLSchemaLoader();
			sl.setErrorHandler(this);

			SchemaGrammar g = (SchemaGrammar) sl.loadGrammar(xsdSource);

			// set XSModel
			xsModel = g.toXSModel();

			// create substitution group-handler
			// NOTE: it is needed but not really used later on
			// (substitution groups are handled separately)
			XSGrammarBucket grammarBucket = new XSGrammarBucket();
			grammarBucket.putGrammar(g, true);
			subGroupHandler = new SubstitutionGroupHandler(grammarBucket);

		} catch (Exception e) {
			throw new EXIException(e);
		}
	}

	public void loadGrammar(String xsdLocation) throws EXIException {
		// XSD source
		String publicId = null;
		String systemId = xsdLocation;
		String baseSystemId = null;
		XMLInputSource xsdSource = new XMLInputSource(publicId, systemId,
				baseSystemId);
		loadGrammar(xsdSource);
	}

	public void loadGrammar(InputStream xsdInputStream) throws EXIException {
		// XSD source
		String publicId = null;
		String systemId = null;
		String baseSystemId = null;
		String encoding = null;
		XMLInputSource xsdSource = new XMLInputSource(publicId, systemId,
				baseSystemId, xsdInputStream, encoding);
		loadGrammar(xsdSource);
	}

	public XSModel getXSModel() {
		return this.xsModel;
	}

	@Override
	XSCMValidator createAllCM(XSParticleDecl particle) {
		// Note: xsd:all is allowed to contain elements only
		// maxOccurs: value must be 1
		// minOccurs: value can be 0 or 1
		assert (particle.getMaxOccurs() == 1);
		assert (particle.getMinOccurs() == 0 || particle.getMinOccurs() == 1);

		// TODO simplified EXI all
		// XSCMValidator valAll = super.createAllCM(particle);
		// int[] state = valAll.startContentModel();
		// @SuppressWarnings("unchecked")
		// Vector<XSObject> possibleElements = valAll.whatCanGoHere(state);

		return super.createAllCM(particle);
	}

	protected static void addNewState(Map<CMState, SchemaInformedRule> states,
			CMState key) {
		SchemaInformedRule val = new RuleElementSchemaInformed();
		if (key.end) {
			val.addTerminalRule(END_ELEMENT);
		}
		states.put(key, val);
	}

	protected SchemaInformedRule handleParticle(XSComplexTypeDefinition ctd)
			throws EXIException {

		XSCMValidator xscmVal = getContentModel((XSComplexTypeDecl) ctd, forUPA);

		int[] state = xscmVal.startContentModel();
		@SuppressWarnings("unchecked")
		Vector<XSObject> possibleElements = xscmVal.whatCanGoHere(state);
		boolean isEnd = xscmVal.endContentModel(state);

		CMState startState = new CMState(possibleElements, isEnd, state);
		if (DEBUG) {
			System.out.println("Start = " + startState);
		}

		Map<CMState, SchemaInformedRule> knownStates = new HashMap<CMState, SchemaInformedRule>();
		addNewState(knownStates, startState);
		handleStateEntries(possibleElements, xscmVal, state, startState,
				knownStates, ctd);

		return knownStates.get(startState);
	}

	protected void handleStateEntries(Vector<XSObject> possibleElements,
			XSCMValidator xscmVal, int[] originalState, CMState startState,
			Map<CMState, SchemaInformedRule> knownStates,
			XSComplexTypeDefinition enclosingType) throws EXIException {
		assert (knownStates.containsKey(startState));

		for (XSObject xs : possibleElements) {
			// copy state since it gets modified
			int[] cstate = Arrays.copyOf(originalState, originalState.length);

			if (xs.getType() == XSConstants.ELEMENT_DECLARATION) {
				// make transition
				XSElementDeclaration nextEl = (XSElementDeclaration) xs;
				QName qname = new QName(null, nextEl.getName(), null, nextEl
						.getNamespace());
				Object nextRet = xscmVal.oneTransition(qname, cstate,
						subGroupHandler);
				// check whether right transition was taken
				assert (xs == nextRet);

				// next possible state
				@SuppressWarnings("unchecked")
				Vector<XSObject> nextPossibleElements = xscmVal
						.whatCanGoHere(cstate);
				boolean isEnd = xscmVal.endContentModel(cstate);
				CMState nextState = new CMState(nextPossibleElements, isEnd,
						cstate);

				printTransition(startState, xs, nextState);

				// add to list of "remaining" elements
				if (!remainingElements.contains(nextEl)) {
					remainingElements.add(nextEl);

				}

				// update list of "enclosing" types
				List<XSComplexTypeDefinition> encls;
				if (enclosingTypes.containsKey(nextEl)) {
					encls = enclosingTypes.get(nextEl);
				} else {
					encls = new ArrayList<XSComplexTypeDefinition>();
					enclosingTypes.put(nextEl, encls);
				}
				if (!encls.contains(enclosingType)) {
					encls.add(enclosingType);
				}

				//	retrieve list of possible elements (e.g. substitution group elements)
				List<ExpandedName> elements = getPossibleElementDeclarations(nextEl);
				assert(elements.size() > 0);
				boolean isNewState = false;
				
				for(int i=0; i<elements.size(); i++) {
					ExpandedName nextEN = elements.get(i);
					Event xsEvent = new StartElement(nextEN.getNamespaceURI(), nextEN.getLocalName());
					if( i == 0) {
						//	first element tells the right way to proceed
						isNewState = handleStateEntry(startState, knownStates, xsEvent, nextState);
					} else {
						handleStateEntry(startState, knownStates, xsEvent, nextState);
					}
				}

				if (isNewState) {
					handleStateEntries(nextPossibleElements, xscmVal, cstate,
							nextState, knownStates, enclosingType);
				}

			} else {
				assert (xs.getType() == XSConstants.WILDCARD);
				XSWildcard nextWC = ((XSWildcard) xs);
				short constraintType = nextWC.getConstraintType();
				if (constraintType == XSWildcard.NSCONSTRAINT_ANY
						|| constraintType == XSWildcard.NSCONSTRAINT_NOT) {
					// make transition
					QName qname = new QName(null, "##wc", null, "");
					Object nextRet = xscmVal.oneTransition(qname, cstate,
							subGroupHandler);
					// check whether right transition was taken
					assert (xs == nextRet);

					// next possible state
					@SuppressWarnings("unchecked")
					Vector<XSObject> nextPossibleElements = xscmVal
							.whatCanGoHere(cstate);
					boolean isEnd = xscmVal.endContentModel(cstate);
					CMState nextState = new CMState(nextPossibleElements,
							isEnd, cstate);

					printTransition(startState, xs, nextState);

					Event xsEvent = new StartElementGeneric();

					boolean isNewState = handleStateEntry(startState,
							knownStates, xsEvent, nextState);
					if (isNewState) {
						handleStateEntries(nextPossibleElements, xscmVal,
								cstate, nextState, knownStates, enclosingType);
					}

				} else {
					assert (constraintType == XSWildcard.NSCONSTRAINT_LIST);
					// make transition
					StringList sl = nextWC.getNsConstraintList();
					QName qname = new QName(null, "##wc", null, sl.item(0));
					Object nextRet = xscmVal.oneTransition(qname, cstate,
							subGroupHandler);
					assert (xs == nextRet); // check whether right transition
					// was taken

					// next possible state
					@SuppressWarnings("unchecked")
					Vector<XSObject> nextPossibleElements = xscmVal
							.whatCanGoHere(cstate);
					boolean isEnd = xscmVal.endContentModel(cstate);
					CMState nextState = new CMState(nextPossibleElements,
							isEnd, cstate);

					printTransition(startState, xs, nextState);

					for (int i = 0; i < sl.getLength(); i++) {
						Event xsEvent = new StartElementNS(sl.item(i));
						boolean isNewState = handleStateEntry(startState,
								knownStates, xsEvent, nextState);
						if (isNewState) {
							handleStateEntries(nextPossibleElements, xscmVal,
									cstate, nextState, knownStates,
									enclosingType);
						}
					}
				}
			}
		}
	}

	/**
	 * 
	 * Creates/Modifies appropriate rule and return whether the next state has
	 * been already resolved. If returnValue == TRUE it is a new state which
	 * requires further processing. If the returnValue == FALSE the according
	 * state(rule) is already full evaluated
	 * 
	 * @param startState
	 * @param knownStates
	 * @param xsEvent
	 * @param nextState
	 * @return requires further processing of nextState
	 */
	protected boolean handleStateEntry(CMState startState,
			Map<CMState, SchemaInformedRule> knownStates, Event xsEvent,
			CMState nextState) {
		Rule startRule = knownStates.get(startState);

		if (knownStates.containsKey(nextState)) {
			startRule.addRule(xsEvent, knownStates.get(nextState));
			return false;
		} else {
			addNewState(knownStates, nextState);
			startRule.addRule(xsEvent, knownStates.get(nextState));
			return true;
		}
	}
	
	/**
	 * Returns a list of possible elements. In general this list is the element itself. In case of
	 * SubstitutionGroups the list is extended by all possible "replacements". The returned list
	 * is sorted lexicographically first by {name} then by {target namespace}.
	 * 
	 * (see http://www.w3.org/TR/exi/#elementTerms)
	 * 
	 * @param el
	 * @return
	 */
	protected List<ExpandedName> getPossibleElementDeclarations(XSElementDeclaration el) {
		
		List<ExpandedName> listElements = new ArrayList<ExpandedName>();
		
		//	add element itself
		listElements.add(new ExpandedName(el.getNamespace(), el.getName()));
		
		//	add possible substitution group elements
		XSObjectList listSG = xsModel.getSubstitutionGroup(el);
		if (listSG != null && listSG.getLength() > 0) {
			for (int i = 0; i < listSG.getLength(); i++) {
				XSElementDeclaration ed = (XSElementDeclaration) listSG.item(i);
				listElements.add(new ExpandedName(ed.getNamespace(), ed.getName()));
			}
		}
		
		//	sort list
		Collections.sort(listElements);
		
		return listElements;
	}
	

	protected static void printTransition(CMState startState, XSObject xs,
			CMState nextState) {
		if (DEBUG) {
			System.out.println("\t" + startState + " --> " + xs + " --> "
					+ nextState);
		}
	}

	/*
	 * XMLErrorHandler
	 */
	public void error(String domain, String key, XMLParseException exception)
			throws XNIException {
		schemaParsingErrors.add("[xs-error] " + exception.getMessage());
	}

	public void fatalError(String domain, String key,
			XMLParseException exception) throws XNIException {
		schemaParsingErrors.add("[xs-fatalError] " + exception.getMessage());
	}

	public void warning(String domain, String key, XMLParseException exception)
			throws XNIException {
		schemaParsingErrors.add("[xs-warning] " + exception.getMessage());
	}

	/*
	 * Internal Helper Class: CMState
	 */
	class CMState {
		protected final Vector<XSObject> states;
		protected final boolean end;
		protected int[] state;

		public CMState(Vector<XSObject> states, boolean end, int[] state) {
			this.states = states;
			this.end = end;
			this.state = Arrays.copyOf(state, state.length); // copy, may get
			// modified
		}

		public boolean equals(Object o) {
			if (o instanceof CMState) {
				CMState other = (CMState) o;
				if (end == other.end && states.equals(other.states)
						) {
					// if (Arrays.equals(state, other.state)) {
					assert(state.length > 1 && other.state.length > 1);
					//	NOTE: 3rd item is counter only!
					if (state[0] == other.state[0] && state[1] == other.state[1] ) {
						return true;	
					}
				}
			}
			return false;
		}

		public String toString() {
			return (end ? "F" : "N") + stateToString() + states.toString();
		}

		protected String stateToString() {
			StringBuffer s = new StringBuffer();
			s.append('(');
			for (int i = 0; i < state.length; i++) {
				s.append(state[i]);
				if (i < (state.length - 1)) {
					s.append(',');
				}
			}
			s.append(')');

			return s.toString();
		}

		public int hashCode() {
			return end ? states.hashCode() : -states.hashCode();
		}
	}

}
