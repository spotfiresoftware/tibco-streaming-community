package com.tibco.sb.compexch.amsclient;

import java.beans.IntrospectionException;

import com.streambase.sb.operator.parameter.SBPropertyDescriptor;
import com.streambase.sb.operator.parameter.SBSimpleBeanInfo;
import com.streambase.sb.operator.parameter.UIHints;

public class AMSClientBeanInfo extends SBSimpleBeanInfo {

    public static final String LOGIN_AT_STARTUP_PARAM_NAME = "logInAtStartup";
    public static final String AMS_SERVER_HOST_NAME_PARAM_NAME = "amsServerHostName";
	public static final String AMS_SERVER_PORT_NUMBER_PARAM_NAME = "amsServerPortNumber";
	public static final String AMS_SERVER_SECURE_CHANNEL_PARAM_NAME = "amsServerSecureChannel";
	public static final String AMS_SERVER_USERNAME_PARAM_NAME = "amsServerUsername";
	public static final String AMS_SERVER_PASSWORD_PARAM_NAME = "amsServerPassword";
    public static final String AUTO_COMMIT_PARAM_NAME = "autoCommit";
    public static final String AUTO_COMMIT_MESSAGE_PARAM_NAME = "autoCommitMessage";

	@Override
    public SBPropertyDescriptor[] getPropertyDescriptorsChecked() throws IntrospectionException {
		return new SBPropertyDescriptor[] {

                new SBPropertyDescriptor(LOGIN_AT_STARTUP_PARAM_NAME, AMSClient.class)
                .displayName("Log In At Start-up")
                .description("Log in to the AMS at start-up")
                .optional(),

                new SBPropertyDescriptor(AMS_SERVER_HOST_NAME_PARAM_NAME, AMSClient.class)
                .displayName("Host Name")
                .description("When loading from the AMS, the AMS server host name or IP address")
                .optional(),

                new SBPropertyDescriptor(AMS_SERVER_PORT_NUMBER_PARAM_NAME, AMSClient.class)
                .displayName("Port Number")
                .description("When loading from the AMS, the AMS server port number")
                .optional(),

                new SBPropertyDescriptor(AMS_SERVER_SECURE_CHANNEL_PARAM_NAME, AMSClient.class)
                .displayName("Secure Channel")
                .description("When loading from the AMS, if checked a secure (SSL) channel is used")
                .optional(),

                new SBPropertyDescriptor(AMS_SERVER_USERNAME_PARAM_NAME, AMSClient.class)
                .displayName("Username")
                .description("When loading from the AMS, the AMS server username")
                .optional(),

                new SBPropertyDescriptor(AMS_SERVER_PASSWORD_PARAM_NAME, AMSClient.class)
                .displayName("Password")
                .description("When loading from the AMS, the AMS server password")
                .optional()
                .setUIHints(UIHints.create().setMaskStringDisplay(true)),

                new SBPropertyDescriptor(AUTO_COMMIT_PARAM_NAME, AMSClient.class)
                .displayName("Auto Commit")
                .description("Automatically commit each artifact add, update, and delete operation")
                .optional(),

                new SBPropertyDescriptor(AUTO_COMMIT_MESSAGE_PARAM_NAME, AMSClient.class)
                .displayName("    Auto Commit Message")
                .description("Commit message for auto-committed adds, updates, and deletes")
                .optional(),
		};
	}

}
