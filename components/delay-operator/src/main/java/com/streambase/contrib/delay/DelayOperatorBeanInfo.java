package com.streambase.contrib.delay;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
 * A BeanInfo class controls what properties are exposed, add 
 * metadata about properties (such as which properties are optional), and access 
 * special types of properties that can't be automatically derived via reflection. 
 * If a BeanInfo class is present, only the properties explicitly declared in
 * this class will be exposed by StreamBase.
 */
public class DelayOperatorBeanInfo extends SimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view. 
	 */
	public PropertyDescriptor[] getPropertyDescriptors() {
		try {
			PropertyDescriptor[] p = { new SBExpressionPropertyDescriptor(
					"delay", DelayOperator.class, 0).displayName("delay")
					};

			return p;
		} catch (IntrospectionException e) {
			System.err.println("Failed to create property descriptors");
			e.printStackTrace();
			return null;
		}
	}

}
