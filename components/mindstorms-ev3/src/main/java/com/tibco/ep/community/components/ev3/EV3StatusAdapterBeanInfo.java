package com.tibco.ep.community.components.ev3;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.JavaEnumPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBSimpleBeanInfo;
import com.streambase.sb.operator.parameter.UIHints;

/**
 * Copyright © 2021. TIBCO Software Inc. This file is subject to the license
 * terms contained in the license file that is distributed with this file.
 */
public class EV3StatusAdapterBeanInfo extends SBSimpleBeanInfo {

	/*
	 * The order of properties below determines the order they are displayed within
	 * the StreamBase Studio property view.
	 */
	public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		SBPropertyDescriptor[] p = { new SBPropertyDescriptor("ConnectionManagerName", EV3StatusAdapter.class)
				.displayName("Linked Connection Manager name").description(
						"Set this to the name of the Connection Manager adapter you wish to share a connection with."),
				new JavaEnumPropertyDescriptor<EV3StatusAdapter.OutputTypeEnum>("OutputType", EV3StatusAdapter.class,
						EV3StatusAdapter.OutputTypeEnum.class).displayName("Output type").description(""),
				new SBPropertyDescriptor("StreamButtonPort", EV3StatusAdapter.class)
						.displayName("Stream button data on startup").description(""),
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
						.displayName("Stream port 4 data on startup").description(""),

				// physical port configuration
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port1Device", EV3StatusAdapter.class,
						SensorTypeEnum.class).displayName("EV3 Port 1 Device").description("")
								.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port2Device", EV3StatusAdapter.class,
						SensorTypeEnum.class).displayName("EV3 Port 2 Device").description("")
								.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port3Device", EV3StatusAdapter.class,
						SensorTypeEnum.class).displayName("EV3 Port 3 Device").description("")
								.setUIHints(UIHints.create().setTab("Robot Configuration")),
				new JavaEnumPropertyDescriptor<SensorTypeEnum>("Port4Device", EV3StatusAdapter.class,
						SensorTypeEnum.class).displayName("EV3 Port 4 Device").description("")
								.setUIHints(UIHints.create().setTab("Robot Configuration")),

				new SBPropertyDescriptor("PortAMotor", EV3StatusAdapter.class).displayName("Motor connected to port A?")
						.description("").setUIHints(UIHints.create().setTab("Robot Configuration")),
				new SBPropertyDescriptor("PortBMotor", EV3StatusAdapter.class).displayName("Motor connected to port B?")
						.description("").setUIHints(UIHints.create().setTab("Robot Configuration")),
				new SBPropertyDescriptor("PortCMotor", EV3StatusAdapter.class).displayName("Motor connected to port C?")
						.description("").setUIHints(UIHints.create().setTab("Robot Configuration")),
				new SBPropertyDescriptor("PortDMotor", EV3StatusAdapter.class).displayName("Motor connected to port D?")
						.description("").setUIHints(UIHints.create().setTab("Robot Configuration")) };
		return p;
	}

}
