package com.example.ev3;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
* A BeanInfo class controls what properties are exposed, add 
* metadata about properties (such as which properties are optional), and access 
* special types of properties that can't be automatically derived via reflection. 
* If a BeanInfo class is present, only the properties explicitly declared in
* this class will be exposed by StreamBase.
*/
public class EV3StatusAdapterBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new JavaEnumPropertyDescriptor<EV3StatusAdapter.OutputTypeEnum>("OutputType", EV3StatusAdapter.class,
						EV3StatusAdapter.OutputTypeEnum.class).displayName("Output type").description(""),
				new SBPropertyDescriptor("StreamPortA", EV3StatusAdapter.class)
						.displayName("Stream port A data on startup").description(""),
				new SBPropertyDescriptor("StreamPortB", EV3StatusAdapter.class)
						.displayName("Stream port B data on startup").description(""),
				new SBPropertyDescriptor("StreamPortC", EV3StatusAdapter.class)
						.displayName("Stream port C data on startup").description(""),
				new SBPropertyDescriptor("StreamPortD", EV3StatusAdapter.class)
						.displayName("Stream port D data on startup").description(""),
				new SBPropertyDescriptor("StreamPort1", EV3StatusAdapter.class)
						.displayName("Stream port 1 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort2", EV3StatusAdapter.class)
						.displayName("Stream port 2 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort3", EV3StatusAdapter.class)
						.displayName("Stream port 3 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort4", EV3StatusAdapter.class)
						.displayName("Stream port 4 data on startup").description("")
	    		};
		return p;
	}

}
