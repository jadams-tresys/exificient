package com.siemens.ct.exi.datatype.strings;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import com.siemens.ct.exi.values.Value;

public class BoundedStringDecoderImpl extends StringDecoderImpl {

	/* maximum string length of value content items */
	protected final int valueMaxLength;

	/* maximum number of value content items in the string table */
	protected final int valuePartitionCapacity;

	/* global ID */
	protected int globalID;
	
	/* localID mapping, globalID index --> localID & values (in given context) */
	protected LocalIDMap[] localIdMapping;
	
	class LocalIDMap {
		final int localID;
		final QName context; /* debug info*/
		final List<Value> values;
		
		public LocalIDMap(int localID, QName context, List<Value> values) {
			this.localID = localID;
			this.context = context;
			this.values = values;
		}
	}
	
	public BoundedStringDecoderImpl(int valueMaxLength,
			int valuePartitionCapacity) {
		super();
		this.valueMaxLength = valueMaxLength;
		this.valuePartitionCapacity = valuePartitionCapacity;
		
		this.globalID = -1;
		if (valuePartitionCapacity >= 0) {
			localIdMapping = new LocalIDMap[valuePartitionCapacity];	
		}
	}

	@Override
	public void addValue(QName context, Value value) {
		// first: check "valueMaxLength"
		if (valueMaxLength < 0 || value.getCharactersLength() <= valueMaxLength) {
			// next: check "valuePartitionCapacity"
			if (valuePartitionCapacity < 0) {
				// no "valuePartitionCapacity" restriction
				super.addValue(context, value);
			} else
			// If valuePartitionCapacity is not zero the string S is added
			if (valuePartitionCapacity == 0) {
				// no values per partition
			} else {
				/*
				 * When S is added to the global value partition and there was
				 * already a string V in the global value partition associated
				 * with the compact identifier globalID, the string S replaces
				 * the string V in the global table, and the string V is removed
				 * from its associated local value partition by rendering its
				 * compact identifier permanently unassigned.
				 */
				assert (!globalValues.contains(value));
				
				// updateLocalValues(context, value);
				// TODO BLAAAAAAAAAAAAAAAAAAA
				List<Value> lvs = localValues.get(context);
				if (lvs == null) {
					lvs = new ArrayList<Value>();
					localValues.put(context, lvs);
				}
				assert (!lvs.contains(value));
				lvs.add(value);

				/*
				 * When the string value is added to the global value partition,
				 * the value of globalID is incremented by one (1). If the
				 * resulting value of globalID is equal to
				 * valuePartitionCapacity, its value is reset to zero (0)
				 */
				if ( (++globalID) == valuePartitionCapacity) {
					globalID = 0;
				}
				
				if (globalValues.size()>globalID) {
					Value prev = globalValues.set(globalID, value);
					if (prev != null) {
						// free memory
						LocalIDMap lvsFree = localIdMapping[globalID];
						assert(lvsFree != null);
						// System.out.println("Remove " + lvsFree.values.get(lvsFree.localID) + " in " + lvsFree.context);
						lvsFree.values.set(lvsFree.localID, null);
					}					
				} else {
					globalValues.add(value);
				}
				
				
				// update local ID mapping
				localIdMapping[globalID] = new LocalIDMap(lvs.size()-1, context, lvs);
				// System.out.println("Global " + globalID +  ": " + context );

			}
		}
	}
	
	@Override
	public void clear() {
		super.clear();
		globalID = -1;
	}


}