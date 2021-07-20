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
public class EV3CommandAdapterBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view.
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = { new SBPropertyDescriptor("ConnectionManagerName", EV3CommandAdapter.class)
				.displayName("Linked Connection Manager name").description(
						"Name of the Connection Manager adapter to share a connection with."),
				new SBPropertyDescriptor("error", EV3CommandAdapter.class)
				.displayName("Acceptable Motor Position Error (Degrees)")
				.description("Margin of error for blocking motor commands to return success 'true'")};
		return p;
	}

}
