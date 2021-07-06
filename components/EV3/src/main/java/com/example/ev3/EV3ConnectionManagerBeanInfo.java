package com.example.ev3;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.*;

/**
 * A BeanInfo class controls what properties are exposed, add metadata about
 * properties (such as which properties are optional), and access special types
 * of properties that can't be automatically derived via reflection. If a
 * BeanInfo class is present, only the properties explicitly declared in this
 * class will be exposed by StreamBase.
 */
public class EV3ConnectionManagerBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view.
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				// wireless MAC address
				new SBPropertyDescriptor("MACaddress", EV3ConnectionManager.class).displayName("Bluetooth MAC address")
						.description("Set this to the 12-character MAC address of your EV3 brick."),
				// reconnection tries (defaults to one)
				new SBPropertyDescriptor("ConnectionTries", EV3ConnectionManager.class)
						.displayName("# of Connection Attempts")
						.description("Number of times to try reconnecting if the first fails") };
		return p;
	}

}
