package com.tibco.contrib.lv_cluster_scaler;

import com.streambase.liveview.client.LiveViewConnection;

public class LDMServer {

	private final String URL;
	private LiveViewConnection lvcon=null;
	private String nodeType=null;
	
	public static final String DATA_LAYER="DATA_LAYER";
	public static final String SERVICE_LAYER="SERVICE_LAYER";
	
	public LDMServer (String URL) {
		this.URL=URL;
	}
	
	public String getURL() {
		return URL;
	}
	
	public void setLVCon(LiveViewConnection lvcon) {
		this.lvcon=lvcon;
	}
	public LiveViewConnection getLVCon() {
		return this.lvcon;
	}
	
	public void setNodeType(String nodeType) {
		this.nodeType=nodeType;
	}
	public String getNodeType() {
		return this.nodeType;
	}
}
