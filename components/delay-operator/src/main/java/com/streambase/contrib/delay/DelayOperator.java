package com.streambase.contrib.delay;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import org.slf4j.Logger;

import com.streambase.sb.Schema;
import com.streambase.sb.StreamBaseException;
import com.streambase.sb.Timestamp;
import com.streambase.sb.Tuple;
import com.streambase.sb.operator.Operator;
import com.streambase.sb.operator.Parameterizable;
import com.streambase.sb.operator.TypecheckException;

/**
 * Delay input messages for a specific amount of time before outputting them again.
 *
 * @author Richard Tibbetts
 */
public class DelayOperator extends Operator implements Parameterizable {

	private class QueueRunner implements Runnable {

		public void run() {
			List<Tuple> toSend = new ArrayList<Tuple>();
			final int BATCH_LIMIT=100;
			QueueEntry top=null;
			
			while (shouldRun()) {
				try {
					Timestamp now = null;
					synchronized (queue) {
						now = Timestamp.now();
						top = queue.peek();
						int count=0;
						while (top != null && (top.emitTime.compareTo(now) <= 0) && (count++ < BATCH_LIMIT)) {
							// Got some stuff to emit.
							top = queue.remove();
							toSend.add(top.tuple);
							// Look for more.
							top = queue.peek();
						}
					}
					
					// Don't send tuples while holding the queue.
					// Even sendOutputAsync can block for periods if the deadlock detector kicks in
					if (!toSend.isEmpty()) {
						sendOutput(0, toSend);
						toSend.clear();
					}
					
					synchronized (queue) {
						// Ok, get the top again
						top = queue.peek();
						if (top == null) {
							queue.wait();
						} else {
							long timeout = top.emitTime.toMsecs() - now.toMsecs();
							if (timeout > 0)
								queue.wait(timeout);
						}
					}
				} catch (InterruptedException e) {
					// Do nothing, the top of the loop will check.
				} catch (StreamBaseException e) {
					logger.error("Got an error while outputing a tuple", e);
				}
			}
		}

	}

	private static class QueueEntry implements Comparable<QueueEntry> {
		// When to emit the tuple
		public final Timestamp emitTime;
		public final Tuple tuple;
		
		public QueueEntry(Timestamp emitTime, Tuple tuple) {
			this.emitTime = emitTime;
			this.tuple = tuple;
		}
	
		public int compareTo(QueueEntry other) {
			return emitTime.compareTo(other.emitTime);
		}
	}

	public static final long serialVersionUID = 1240269536271L;
	private Logger logger;
	// Properties
	private Timestamp delay;
	private String displayName = "Delay Operator";
	// Local variables
	private Schema schema;
	// The queue of elements. Use a regular priority queue, not a concurrent one,
	// because we want our own blocking/notification behavior.
	private PriorityQueue<QueueEntry> queue = new PriorityQueue<QueueEntry>();

	public DelayOperator() {
		super();
		logger = getLogger();
		setPortHints(1, 1);
		setDisplayName(displayName);
		setShortDisplayName(this.getClass().getSimpleName());
	}

	@Override
	public void typecheck() throws TypecheckException {
		requireInputPortCount(1);
		schema = getInputSchema(0);
		setOutputSchema(0, schema);
	}

	@Override
	public int size() {
		synchronized (queue) {
			return queue.size();
		}
	}
	
	/**
	 * Consume an input tuple and put it on the queue in the right place.
	 */
	@Override
	public void processTuple(int inputPort, Tuple tuple)
			throws StreamBaseException {
		assert inputPort == 0;
		Timestamp emitTime = Timestamp.msecs(Timestamp.TIMESTAMP, Timestamp.now().toMsecs() + delay.toMsecs());
		QueueEntry e = new QueueEntry(emitTime, tuple);
		synchronized (queue) {
			queue.add(e);
			// Micro optimization to only notify if the new entry is the top entry.
			if (queue.peek() == e ) {
				queue.notify();
			}
		}
	}

	@Override
	public void init() throws StreamBaseException {
		super.init();
		registerRunnable(new QueueRunner(), true);
	}

	/***************************************************************************************
	 * The getter and setter methods provided by the Parameterizable object.               *
	 * StreamBase Studio uses them to determine the name and type of each property         *
	 * and obviously, to set and get the property values.                                  *
	 ***************************************************************************************/

	public void setDelay(Timestamp delay) {
		this.delay = delay;
	}

	public Timestamp getDelay() {
		return this.delay;
	}
}
