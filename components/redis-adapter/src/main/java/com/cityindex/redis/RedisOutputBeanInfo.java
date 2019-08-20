package com.cityindex.redis;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
 * A BeanInfo class controls what properties are exposed, add 
 * metadata about properties (such as which properties are optional), and access 
 * special types of properties that can't be automatically derived via reflection. 
 * If a BeanInfo class is present, only the properties explicitly declared in
 * this class will be exposed by StreamBase.
 */
public class RedisOutputBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view. 
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked()
			throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new SBPropertyDescriptor("redisPort", RedisOutput.class)
						.displayName("Redis Server port")
						.description("")
						.optional(),
				new SBPropertyDescriptor("redisHost", RedisOutput.class)
						.displayName("Redis Server IP address or name")
						.description("")
						.optional()
				};
		return p;
	}

}
