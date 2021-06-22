//
// Copyright (c) 2004-2009 StreamBase Systems, Inc. All rights reserved.
//
package com.example.ev3;


/**
 * All adapters (Input and Output) looking to become "linked" must implement this interface.
 */
public interface ISharableAdapter
{
    /**
     * @return the name of this adapter as returned by a call to Operator.getName().
     */
    public String getName();
    
    /**
     * @return the name of this adapter as returned by a call to Operator.getFullyQualifiedName().
     */
    public String getFullyQualifiedName();
    
    /**
     * @return the name of this adapter's container as returned by a call to Operator.getContainerName().
     */
    public String getContainerName();
    
    /**
     * @return the name of the connection manager to share
     */
    public String getConnectionManagerName();
    
}
