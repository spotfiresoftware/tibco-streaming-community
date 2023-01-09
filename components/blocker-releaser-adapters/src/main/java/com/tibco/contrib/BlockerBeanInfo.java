/*
 * Copyright (c) 2015-2021 Cloud Software Group, Inc.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of Cloud Software Group, Inc.
 *
 */
package com.tibco.contrib;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
* A BeanInfo class controls what properties are exposed, add 
* metadata about properties (such as which properties are optional), and access 
* special types of properties that can't be automatically derived via reflection. 
* If a BeanInfo class is present, only the properties explicitly declared in
* this class will be exposed by StreamBase.
*/
public class BlockerBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = { new SBPropertyDescriptor("idName", Blocker.class)
				.displayName("Matching Releaser identifier").description("") };
		return p;
	}

}
