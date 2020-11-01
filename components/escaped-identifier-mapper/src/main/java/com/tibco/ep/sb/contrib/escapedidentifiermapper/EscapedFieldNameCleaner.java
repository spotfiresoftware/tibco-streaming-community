/**
* Copyright © 2020. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.sb.contrib.escapedidentifiermapper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.streambase.sb.CompleteDataType;
import com.streambase.sb.DataType;
import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Tuple;
import com.streambase.sb.TupleCopier;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;
import com.streambase.sb.util.Util;

/**
 * Copies input to output but turning escaped identifiers ("#F") into (PrefixF)
 * in any field name encountered. Handles lists, tuples of course. Doesn't bother
 * dealing with functions atm.
 * @author eddie 
 */
public class EscapedFieldNameCleaner extends Operator implements Parameterizable {

	public static final long serialVersionUID = 1588688236771L;
	// Properties
	private String prefix;
	private String displayName = "Exotic Field Name Cleaner";
	// Local variables
	private int inputPorts = 1;
	private int outputPorts = 1;
	private TupleCopier tupleCopier;

	/**
	 * c'tor
	 */
	public EscapedFieldNameCleaner() {
		super();
		setPortHints(inputPorts, outputPorts);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
		setPrefix("Field");

	}

	/**
	 * Typechecker
	 */
	public void typecheck() throws TypecheckException {
		requireInputPortCount(inputPorts);

		if (Util.isEmpty(getPrefix())) {
			throw new PropertyTypecheckException("prefix", "Must provide a prefix");
		}
		if (!Util.isValidNonExoticIdentifier(getPrefix())) {
			throw new PropertyTypecheckException("prefix", "Prefix must be a valid non-escaped identifier");
		}


		setOutputSchema(0, schemaWithUnwrappedEscapedIdentifiers(getInputSchema(0)));
	}

	/**
	 * @param in a schema to process, deeply
	 * @return a new schema identical to in except with field names prefixed and un-escaped as described in the classdoc 
	 * @throws TypecheckException to communicate some issue (such as not supporting FUNCTION field types)
	 */
	private Schema schemaWithUnwrappedEscapedIdentifiers(Schema in) throws TypecheckException {
		List<Schema.Field> newFields = new ArrayList<>();

		for (Schema.Field f : in) {
			// prefix only escaped identifiers
			String unwrapped = Util.unwrapExoticIdentifier(f.getName());
			String newName = f.getName().equals(unwrapped) ? f.getName() : String.format("%s%s", getPrefix(), unwrapped);


			if (f.getDataType() == DataType.TUPLE) {
				// dive in
				newFields.add(Schema.createTupleField(newName, schemaWithUnwrappedEscapedIdentifiers(f.getSchema())));
			} else if (f.getDataType() == DataType.LIST) {
				
				CompleteDataType elementType = f.getElementType();
				if (elementType.getSchema() != null) { // list of tuples
					newFields.add(Schema.createListField(newName,CompleteDataType.forTuple(schemaWithUnwrappedEscapedIdentifiers(elementType.getSchema()))));
				} else { // XXX list of list of... ?
					newFields.add(Schema.createListField(newName, f.getElementType()));
				}
			} else if (f.getDataType() == DataType.FUNCTION) {
				throw new TypecheckException("Field of type FUNCTION not supported"); // XXX could deal with if desired
			} else {
				newFields.add(Schema.createField(f.getDataType(), newName));
			}
		}

		return new Schema(null, newFields);
	}

	/**
	 * Take the input, send the output per this operator's spec
	 * @param inputPort the port the input came in on, ignored
	 * @param tuple the tuple in
	 */
	public void processTuple(int inputPort, Tuple tuple) throws StreamBaseException {
		// create a new output tuple from the Schema at the port we are about to send to
		Tuple out = getOutputSchema(0).createTuple();

		tupleCopier.copyTuple(tuple, out);

		sendOutput(0, out);
	}

	/**
	 * sets up a tuple copier
	 */
	public void init() throws StreamBaseException {
		super.init();

		tupleCopier = new TupleCopier(getInputSchema(0), getOutputSchema(0), EnumSet.of(TupleCopier.Options.CopyByIndex));
	}

	/**
	 *  no-op
	 */
	public void shutdown() {
	}

	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

	/**
	 * @param prefix setter, it will always trim it
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix.trim();
	}

	/**
	 * @return value of prefix
	 */
	public String getPrefix() {
		return this.prefix;
	}

}
