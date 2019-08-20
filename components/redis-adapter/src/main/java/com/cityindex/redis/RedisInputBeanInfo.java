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
public class RedisInputBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view. 
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked()
			throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new SBPropertyDescriptor("channelPath", RedisInput.class)
				        .displayName("Redis subscription channel").description("").optional(),
				new SBPropertyDescriptor("redisPort", RedisInput.class)
						.displayName("Redis Server Port").description("").optional(),
				new SBPropertyDescriptor("redisHost", RedisInput.class)
						.displayName("IP address or URL for Redis Server")
						.description("")
						.optional(),
				};
		return p;
	}

}
