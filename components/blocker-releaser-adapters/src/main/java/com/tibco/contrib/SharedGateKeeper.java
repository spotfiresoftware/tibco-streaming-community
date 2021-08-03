/*
 * Copyright (c) 2015-2021 TIBCO Software Inc.
 * All rights reserved.
 *
 * This software is confidential and proprietary information of TIBCO Software Inc.
 *
 */
package com.tibco.contrib;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.streambase.sb.StreamBaseException;
import com.streambase.sb.operator.Operator.SharedObject;
import com.streambase.sb.util.Util;

public class SharedGateKeeper implements SharedObject  {

	private int releaseLimit = Util.getIntSystemProperty("streambase.custom.waittime.s", 30);
	private CountDownLatch latch = new CountDownLatch(1);
	private Logger logger=LoggerFactory.getLogger(this.getClass());;
	
	/**
	 * waitForRelease - must be called before release.
	 */
	public void waitForRelease() {
		try {
			boolean worked=latch.await(releaseLimit, TimeUnit.SECONDS);
			if (!worked) {
				logger.warn(String.format("Waited for release %s S, failed", releaseLimit));
			}
		} catch (InterruptedException e) {
			logger.debug(e.getMessage());
		}
		latch = new CountDownLatch(1);
	}
	
	public void release() {
		latch.countDown();
	}
	
	@Override
	public void startObject() throws StreamBaseException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resumeObject() throws StreamBaseException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void suspendObject() throws StreamBaseException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void shutdownObject() throws StreamBaseException {
		// TODO Auto-generated method stub
		
	}

}
