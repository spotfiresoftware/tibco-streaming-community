/*
* Copyright © 2019. TIBCO Software Inc.
* This file is subject to the license terms contained
* in the license file that is distributed with this file.
*/
package com.tibco.techsupport.tableprovider;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.liveview.client.LiveViewConnection;
import com.streambase.liveview.client.LiveViewException;
import com.streambase.liveview.client.LiveViewQueryLanguage;
import com.streambase.liveview.client.LiveViewQueryType;
import com.streambase.liveview.client.LiveViewTableCapability;
import com.streambase.liveview.client.Table.TableStatus;
import com.streambase.liveview.server.event.query.listener.QueryEventListener;
import com.streambase.liveview.server.query.QueryModel;
import com.streambase.liveview.server.table.CatalogedTable;
import com.streambase.liveview.server.table.Table;
import com.streambase.liveview.server.table.plugin.TableProviderControl;
import com.streambase.liveview.server.table.publisher.TablePublisher;
import com.streambase.sb.CompleteDataType;
import com.streambase.sb.Schema;
import com.streambase.sb.Timestamp;

/*
 * This is the ProxyPublishTable implementation. 
 * 
 * It can't do any querying - it's just a pass-through publisher
 * 
 * publish ProxyPublish
 * lvURI, serviceName,Table,Delete,JSONTuple
 * 
 * lv://your.lv.host.com,null,ItemsSales,null,"[99999,'2019-10-17 13:14:26.295-0400','thingy','automotive',173,9.0]"
 */
public class ProxyPublishTable implements Table  {

    private Logger logger = LoggerFactory.getLogger(ProxyPublishTable.class);
      
    public final static String NAME_LVURI="lvURI";
    private final static Schema.Field FIELD_LVURI = new Schema.Field(NAME_LVURI, CompleteDataType.forString());
    public final static String NAME_SERVICENAME="ServiceName";
    private final static Schema.Field FIELD_SERVICENAME = new Schema.Field(NAME_SERVICENAME, CompleteDataType.forString());
    public final static String NAME_TABLE="Table";
    private final static Schema.Field FIELD_TABLE = new Schema.Field(NAME_TABLE, CompleteDataType.forString());
    public final static String NAME_DELETE="Delete";
    private final static Schema.Field FIELD_DELETE = new Schema.Field(NAME_DELETE, CompleteDataType.forBoolean());
    public final static String NAME_JSONTUPLE="JSONTuple";
    private final static Schema.Field FIELD_JSONTUPLE = new Schema.Field(NAME_JSONTUPLE, CompleteDataType.forString());
    
    private List<String> pKeyFieldArray = Arrays.asList(NAME_LVURI, NAME_SERVICENAME, NAME_TABLE);
    public final static Schema tableSchema = new Schema(null, Arrays.asList(FIELD_LVURI, FIELD_SERVICENAME, FIELD_TABLE, FIELD_DELETE, FIELD_JSONTUPLE));

    // Some members for holding information about the ProxyPublishTable object 
	private CatalogedTable catalogTable=null;
	private final TableProviderControl helper;
	private final String tableName;

	// We only need one publisher - the publish data has the destination, table name, etc.
	private ProxyPublisher proxyPublisher = new ProxyPublisher();

	public ProxyPublishTable(TableProviderControl helper, String tableName) {
		this.helper=helper;
		this.tableName=tableName;
				
		catalogTable = new CatalogedTable(tableName);
		catalogTable.setStatus(TableStatus.ENABLED, "Ready to publish");
		catalogTable.setCapabilities(EnumSet.of(LiveViewTableCapability.PUBLISH));
		catalogTable.setQueryLanguages( EnumSet.of(LiveViewQueryLanguage.OTHER));
		catalogTable.setDescription("TableProvider that can publish data on remote nodes. V1.0");
		catalogTable.setGroup("TechnicalSupport");
        catalogTable.setCreateTime(Timestamp.now());
        catalogTable.setSchema(tableSchema);
       
        catalogTable.setKeyFields(pKeyFieldArray);
        catalogTable.setRuntimeTable(this);
        
        helper.insert(catalogTable);
    }
    
	@Override
	public void addListener(QueryEventListener listener, LiveViewQueryType arg1, QueryModel queryModel) throws LiveViewException {
		 throw new UnsupportedOperationException("Querying is not supported.");
	}

	@Override
	public TablePublisher createPublisher(String arg0) throws LiveViewException {
		// All the real work has to be deferred to when the first publish occurs.
		return proxyPublisher;
	}

	@Override
	public void removeListener(QueryEventListener listener) throws LiveViewException {
		return;
	}

	public Schema getSchema() {
		return tableSchema;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	// close all connections/publishers
	public void shutdown() {
		proxyPublisher.shtudown();
	}

	/*
	 * The parseQuery method is not supported
	 */
	@Override
	public QueryModel parseQuery(CatalogedTable catalogedTable, String queryString, LiveViewQueryType type, boolean includeInternal, String additionalPredicate) throws LiveViewException {
		 throw new UnsupportedOperationException("Querying is not supported.");
	}
}