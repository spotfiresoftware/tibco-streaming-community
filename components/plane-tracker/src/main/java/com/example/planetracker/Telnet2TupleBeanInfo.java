package com.example.planetracker;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

/**
* A BeanInfo class controls what properties are exposed, add 
* metadata about properties (such as which properties are optional), and access 
* special types of properties that can't be automatically derived via reflection. 
* If a BeanInfo class is present, only the properties explicitly declared in
* this class will be exposed by StreamBase.
*/
public class Telnet2TupleBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new SBPropertyDescriptor("TelnetUri", Telnet2Tuple.class).displayName("TelnetUri").description("Local telnet port publishing plane data by dump1090. Usually is localhost:30003."),
				new SBPropertyDescriptor("Region", Telnet2Tuple.class).displayName("Region").description("The place where this application is posting data such as NA-Wal (North America - Waltham"),
				new SBPropertyDescriptor("schema0", Telnet2Tuple.class), };
		return p;
	}

}
