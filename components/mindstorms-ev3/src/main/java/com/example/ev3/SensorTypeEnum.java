package com.example.ev3;

/**
 * Enumeration of EV3 sensor types.
 * 
 * Copyright © 2021. TIBCO Software Inc. This file is subject to the license terms contained in the license file that is
 * distributed with this file.
 */

public enum SensorTypeEnum {
    TOUCH("Touch"), COLOR("Color"), ULTRA("Ultrasonic"), GYRO("Gyroscope"), IR("Infrared"), MOTOR("Motor"), NONE("None");

    private final String rep;

    private SensorTypeEnum(String s) {
        rep = s;
    }

    public String toString() {
        return rep;
    }
}