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
public class EV3InputAdapterBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new JavaEnumPropertyDescriptor<EV3InputAdapter.OutputTypeEnum>("OutputType", EV3InputAdapter.class,
						EV3InputAdapter.OutputTypeEnum.class).displayName("Output type").description(""),
				new SBPropertyDescriptor("StreamPortA", EV3InputAdapter.class)
						.displayName("Stream port A data on startup").description(""),
				new SBPropertyDescriptor("StreamPortB", EV3InputAdapter.class)
						.displayName("Stream port B data on startup").description(""),
				new SBPropertyDescriptor("StreamPortC", EV3InputAdapter.class)
						.displayName("Stream port C data on startup").description(""),
				new SBPropertyDescriptor("StreamPortD", EV3InputAdapter.class)
						.displayName("Stream port D data on startup").description(""),
				new SBPropertyDescriptor("StreamPort1", EV3InputAdapter.class)
						.displayName("Stream port 1 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort2", EV3InputAdapter.class)
						.displayName("Stream port 2 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort3", EV3InputAdapter.class)
						.displayName("Stream port 3 data on startup").description(""),
				new SBPropertyDescriptor("StreamPort4", EV3InputAdapter.class)
						.displayName("Stream port 4 data on startup").description(""),
						
						//to connect it to the output adapter
						new SBPropertyDescriptor("BoolLinkedToAdapter", EV3InputAdapter.class)
	                    .displayName("Link To Existing Adapter's Connection")
	                    .description("Check this option to share a connection with a second EV3 adapter instead.")
	                    .setUIHints(UIHints.create().setTab("Robot Configuration"))
	                    .optional(),
	                    new SBPropertyDescriptor("linkedAdapter", EV3InputAdapter.class)
	                    .displayName("        Link With The Output Adapter Named")
	                    .description("Set this to the name of a 'Linked Output' adapter with which you wish to share a connection.")
	                    .setUIHints(UIHints.create().setTab("Robot Configuration"))
	                    .optional()
	    		};
		return p;
	}

}
