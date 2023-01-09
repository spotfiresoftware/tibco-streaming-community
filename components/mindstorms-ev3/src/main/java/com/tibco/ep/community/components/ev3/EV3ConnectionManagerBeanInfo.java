/*
* Copyright © 2021. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.ep.community.components.ev3;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.SBPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBSimpleBeanInfo;

public class EV3ConnectionManagerBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view.
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				// wireless MAC address
				new SBPropertyDescriptor("MACaddress", EV3ConnectionManager.class).displayName("Bluetooth MAC address")
						.description("Set this to the 12-character MAC address of your EV3 brick.") };
		return p;
	}

}
