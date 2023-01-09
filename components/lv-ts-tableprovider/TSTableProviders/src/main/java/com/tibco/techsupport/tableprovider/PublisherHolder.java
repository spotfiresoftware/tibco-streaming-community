/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.TablePublisher;
import com.streambase.sb.Schema;

public class PublisherHolder {

// helper class to hold details about publishing to a particular table
	protected final String lvURI;
	protected final String tableName;
	protected LiveViewConnection lvconn=null;
	protected Schema tableSchema=null;
	private TablePublisher tablePub=null;
	protected long lastPublishTime;
	
	PublisherHolder(String lvURI, String tableName) {
		this.lvURI=lvURI;
		this.tableName=tableName;
		lastPublishTime=System.currentTimeMillis();
	}
	
	protected Schema getSchema() {
		return tableSchema;
	}
	
	protected LiveViewConnection getConnection() {
		return lvconn;
	}
	
	/*
	 * getTablePublisher is also a proxy to update the last publish time
	 */
	protected TablePublisher getTablePublisher() {
		lastPublishTime=System.currentTimeMillis();
		return tablePub;
	}
	
	protected void setTablePublisher(TablePublisher tablePub) {
		this.tablePub=tablePub;
	}
}
