package com.tibco.ep.community.dtschema;

import org.junit.Test;
import org.junit.After;
import org.junit.Before;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tibco.ep.testing.framework.TransactionalDeadlockDetectedException;
import com.tibco.ep.testing.framework.TransactionalMemoryLeakException;
import com.tibco.ep.testing.framework.UnitTest;

/**
 * Example test case
 */
public class TestCase extends UnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestCase.class);

    /**
     * Setup test framework before running tests
     * 
     */
    @Before
    public void initializeTest()  {
        initialize();
    }

    /**
     * Complete test framework and check for any errors
     * 
     * @throws TransactionalMemoryLeakException Leak detected
     * @throws TransactionalDeadlockDetectedException Deadlock detected
     */
    @After
    public void completeTest() throws TransactionalMemoryLeakException, TransactionalDeadlockDetectedException {
        complete();
    }

    /**
     * test case
     */
    @Test
    public void test1() {
        LOGGER.info("Test Case 1");
    }
    
}
