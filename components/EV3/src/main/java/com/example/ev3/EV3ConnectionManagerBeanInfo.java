package com.example.ev3;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.*;

/**
* A BeanInfo class controls what properties are exposed, add 
* metadata about properties (such as which properties are optional), and access 
* special types of properties that can't be automatically derived via reflection. 
* If a BeanInfo class is present, only the properties explicitly declared in
* this class will be exposed by StreamBase.
*/
public class EV3ConnectionManagerBeanInfo extends SBSimpleBeanInfo {

	/*
	* The order of properties below determines the order they are displayed within
	* the StreamBase Studio property view. 
	*/
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = {
                //wireless MAC address
                new SBPropertyDescriptor("MACaddress", EV3ConnectionManager.class)
                .displayName("Bluetooth MAC address")
                .description("Set this to the 12-character MAC address of your EV3 brick.")
                .setUIHints(UIHints.create().setTab("Robot Configuration")),
               
                
                //physical port configuration
                new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port1Device", EV3ConnectionManager.class, SensorTypeEnum.class)
				.displayName("EV3 Port 1 Device").description("")
				.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port2Device", EV3ConnectionManager.class, SensorTypeEnum.class)
				.displayName("EV3 Port 2 Device").description("")
				.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port3Device", EV3ConnectionManager.class, SensorTypeEnum.class)
				.displayName("EV3 Port 3 Device").description("")
				.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port4Device", EV3ConnectionManager.class, SensorTypeEnum.class)
				.displayName("EV3 Port 4 Device").description("")
				.setUIHints(UIHints.create().setTab("Robot Configuration")),
				
				new SBPropertyDescriptor("PortAMotor", EV3ConnectionManager.class)
                .displayName("Motor connected to port A?").description("")
                .setUIHints(UIHints.create().setTab("Robot Configuration")),
                new SBPropertyDescriptor("PortBMotor", EV3ConnectionManager.class)
                .displayName("Motor connected to port B?").description("")
                .setUIHints(UIHints.create().setTab("Robot Configuration")),
                new SBPropertyDescriptor("PortCMotor", EV3ConnectionManager.class)
                .displayName("Motor connected to port C?").description("")
                .setUIHints(UIHints.create().setTab("Robot Configuration")),
                new SBPropertyDescriptor("PortDMotor", EV3ConnectionManager.class)
                .displayName("Motor connected to port D?").description("")
                .setUIHints(UIHints.create().setTab("Robot Configuration"))
		};
		return p;
	}

}
