package com.tibco.streambase.ircdemo;

import java.beans.*;

import com.streambase.sb.operator.parameter.*;

public class WikimediaEditsAdapterBeanInfo extends SBSimpleBeanInfo {

	public SBPropertyDescriptor[] getPropertyDescriptorsChecked()
			throws IntrospectionException {
		SBPropertyDescriptor[] p = {
				new SBPropertyDescriptor("server", WikimediaEditsAdapter.class).displayName("IRC Server")
				.description("IRC server to connect to"),
				new SBPropertyDescriptor("port", WikimediaEditsAdapter.class).displayName("IRC Server Port #")
				.description("IRC server port to connect to"),
				new SBPropertyDescriptor("channel", WikimediaEditsAdapter.class).displayName("Channel")
				.description("IRC channel to join on connect"),
				new SBPropertyDescriptor("nick", WikimediaEditsAdapter.class).displayName("IRC Nickname")
				.description("IRC nickname to use when connected; the adapter will generate some alternatives based on this if the nick is taken"),
				new SBPropertyDescriptor("realName",WikimediaEditsAdapter.class).displayName("IRC Real Name")
				.description("IRC 'real name' to report to the server on connecting"),
				new SBPropertyDescriptor("connectTimeoutSecs",WikimediaEditsAdapter.class).displayName("Connection time-out (seconds)")
				.description("Seconds to allow the connection to the IRC server to be established during adapter initializations"),
						};
		return p;
	}

}
