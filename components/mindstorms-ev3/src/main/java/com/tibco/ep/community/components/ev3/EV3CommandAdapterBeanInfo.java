/*
* Copyright © 2021. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.ev3;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.SBPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBSimpleBeanInfo;

public class EV3CommandAdapterBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view.
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new SBPropertyDescriptor("ConnectionManagerName", EV3CommandAdapter.class)
						.displayName("Linked Connection Manager name")
						.description("Name of the Connection Manager adapter to share a connection with."),
				new SBPropertyDescriptor("error", EV3CommandAdapter.class)
						.displayName("Acceptable Motor Position Error (Degrees)")
						.description("Margin of error for blocking motor commands to return success 'true'") };
		return p;
	}

}
