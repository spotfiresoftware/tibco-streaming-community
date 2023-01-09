/*
* Copyright © 2019. Cloud Software Group, Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.server.table.plugin.TableNameMapper;
import com.streambase.liveview.server.table.plugin.TableProvider;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.plugin.TableProviderParameters;

/*
 * This is the Technical Support (TS) table provider
 */
public class TechSupportTableProvider implements TableProvider {

	private String providerId;
	private TableProviderControl helper;
	private TableNameMapper mapper;
	private List<String> params;
	private String TABLE_PARAM_NAME="Table";
	
	// Table names
	public static final String fsTableName="FileSystemRead";
	public static final String tsTableName="TSTable";
	public static final String osTableName="OSExec";
	public static final String proxyTableName="ProxyQuery";
	public static final String proxyPublishName="ProxyPublish";
	public static final String ServerLogName="LVServerLog";
	public static final String LVClientName="ProxyLVClient";
	public static final String PRoxySBClientName="ProxySBClient";
	
	private Logger logger = LoggerFactory.getLogger(TechSupportTableProvider.class);
	
	private FSReadTable fsReadTable=null;
	private OSExecTable osExecTable=null;
	private TSTable tsTable=null;
	private ProxyQueryTable proxyQueryTable=null;
	private ProxyPublishTable proxyPublishTable=null;
	private ServerLogTable serverLogTable=null;
	private LVClientTable lVClientTable=null;
	private ProxySBClientTable proxySBClientTable=null;

	@Override
	public void initialize(String id, TableProviderControl helper, TableProviderParameters parameters, TableNameMapper mapper)
			throws LiveViewException {
		this.providerId=id;
		this.helper=helper;
		this.mapper=mapper;

		// Get the names of the TS tables to include
		params = parameters.getMultivalue(TABLE_PARAM_NAME);
	}

	@Override
	public void start() throws LiveViewException, InterruptedException {
		// Create a new table for each group of activities
		if (params.contains(fsTableName)) {
			fsReadTable=new FSReadTable(helper, fsTableName);
		}
		if (params.contains(osTableName)) {
			osExecTable=new OSExecTable(helper, osTableName);
		}
		if (params.contains(tsTableName)) {
			tsTable=new TSTable(helper, tsTableName);
		}
		if (params.contains(proxyTableName)) {
			proxyQueryTable=new ProxyQueryTable(helper, proxyTableName);
		}
		if (params.contains(proxyPublishName)) {
			proxyPublishTable=new ProxyPublishTable(helper, proxyPublishName);
		}
		if (params.contains(ServerLogName)) {
			serverLogTable=new ServerLogTable(helper, ServerLogName);
		}
		if (params.contains(LVClientName)) {
			lVClientTable=new LVClientTable(helper, LVClientName);
		}
		if (params.contains(PRoxySBClientName)) {
			proxySBClientTable=new ProxySBClientTable(helper, PRoxySBClientName);
		}
	}

	@Override
	public void shutdown() {
		if (fsReadTable != null) {
			fsReadTable.shutdown();
		}
		if (osExecTable != null) {
			osExecTable.shutdown();
		}
		if (tsTable != null) {
			tsTable.shutdown();
		}
		if (proxyQueryTable != null) {
			proxyQueryTable.shutdown();
		}
		if (proxyPublishTable != null) {
			proxyPublishTable.shutdown();
		}
		if (serverLogTable != null) {
			serverLogTable.shutdown();
		}
		if (lVClientTable != null) {
			lVClientTable.shutdown();
		}
		if (proxySBClientTable != null) {
			proxySBClientTable.shutdown();
		}
	}
}
