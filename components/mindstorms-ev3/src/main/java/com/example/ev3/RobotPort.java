package com.example.ev3;

import com.streambase.sb.Schema;

public class RobotPort {

	private String name;
	private boolean streaming;
	private byte address;
	private Schema schema;

	public RobotPort(String name, boolean streaming, byte address, Schema schema) {
		this.name = name;
		this.streaming = streaming;
		this.address = address;
		this.schema = schema;
	}

	public boolean isStreaming() {
		return streaming;
	}

	public void setStreaming(boolean streaming) {
		this.streaming = streaming;
	}

	public String getName() {
		return name;
	}

	public byte getAddress() {
		return address;
	}

	public Schema getSchema() {
		return schema;
	}

}
