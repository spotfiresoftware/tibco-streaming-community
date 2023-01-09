/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.net.URI;
import java.net.URISyntaxException;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewConnectionFactory;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewExceptionType;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.QueryConfig;
import com.streambase.liveview.client.SnapshotQueryListener;
import com.streambase.liveview.client.internal.LiveViewConstants;
import com.streambase.sb.Tuple;
import com.streambase.sb.util.Util;

public class TableUtils {
	
	
	/**
	 * getLVuriFromService
	 * @param lvconn - LV connection to the local system
	 * @param serviceName - the Service name, and optional ";username:password"
	 * @return a full LV URI
	 * @throws LiveViewException - when LVNodeInfo table query fails
	 */
	public static String getLVuriFromService(LiveViewConnection lvconn, String serviceName) throws LiveViewException {
		try {
			
			String creds=null;
			String noCreds;
			if (serviceName.indexOf(";") != -1) {
				creds=serviceName.substring(serviceName.indexOf(";")+1);
				noCreds=serviceName.substring(0, serviceName.indexOf(";"));
			} else {
				noCreds=serviceName;
			}
			
			final QueryConfig qc = new QueryConfig();
			qc.setQueryType(LiveViewQueryType.SNAPSHOT);
			qc.setQueryString(String.format("select Value from LVNodeInfo where Category=='Cluster' && Name=='Member' && Detail=='%s'", noCreds));
			
			final SnapshotQueryListener iter = new SnapshotQueryListener();
			lvconn.registerQuery(qc, iter);

			if (!iter.hasNext()) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("Can not find %s in LVNodeInfo", noCreds)); 
			}
			
			Tuple t=iter.next();
			if (t.isNull("Value")) {
				throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("LVNodeInfo Cluster Member had null Value: %s", noCreds)); 
			}
			return String.format("lv://%s%s", (creds==null) ? "" : creds + "@", t.getString("Value").substring(5));
				
		} catch (LiveViewException e) {
			throw e;
		} catch (Exception e) {
			throw LiveViewExceptionType.UNDERLYING_SB_EXCEPTION.error(String.format("failed to get LVNodeInfo table to lookup service: %s - %s", serviceName, e.getMessage())); 
		}
	}

    public static String getEncapsulatingURI() throws LiveViewException {
        String username = Util.getSystemProperty(LiveViewConstants.LIVEVIEW_INTERNAL_USERNAME);
        String password = Util.getSystemProperty(LiveViewConstants.LIVEVIEW_INTERNAL_PASSWORD);
        
        try {
            String auth = null;
            if (password != null || username != null) {
                if (username != null && username.contains(":"))
                    throw LiveViewExceptionType.INVALID_CONNECT_URI.error("username invalid: " + username);
                
                auth = String.format("%s%s%s", username == null ? "" : username, password == null ? "" : ":", password == null ? "" : password);
            }
            
            boolean doTLS=Util.getSystemProperty("liveview.ssl.only", "false").equals("true");
            String protocol = doTLS ? "lvs" : "lv";
            int port= doTLS ? Util.getIntSystemProperty("liveview.ssl.port", LiveViewConnectionFactory.DEFAULT_SERVER_SSL_PORT) : Util.getIntSystemProperty("liveview.port", LiveViewConnectionFactory.DEFAULT_SERVER_PORT);
            URI builtURI = new URI(protocol, auth, com.streambase.liveview.client.internal.Util.getHostName(),
            					   port,
                                   null, null, null);
  
            return builtURI.toString();
        } catch (URISyntaxException e) {
            throw LiveViewExceptionType.INVALID_CONNECT_URI.error(e.toString());
        }
    }
}
