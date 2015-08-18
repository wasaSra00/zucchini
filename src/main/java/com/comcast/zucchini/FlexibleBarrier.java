package com.comcast.zucchini;

import java.util.Collections;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Phaser;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class FlexibleBarrier {
    private static Logger logger = LoggerFactory.getLogger(FlexibleBarrier.class);

    private AbstractZucchiniTest azt;
    private Phaser primary;
    private Phaser secondary;
    private Set<TestContext> arrivedThreads;
    private int primaryOrder;
    private boolean timedout;
    private int secondaryOrder;

    FlexibleBarrier(AbstractZucchiniTest azt) {
        this(azt, azt.contexts.size());
    }

    FlexibleBarrier(AbstractZucchiniTest azt, int size) {
        this.azt = azt;
        this.primary = new Phaser(size);
        this.secondary = new Phaser(size);
        //this.arrivedThreads = new HashSet<TestContext>();
        this.arrivedThreads = Collections.newSetFromMap(new ConcurrentHashMap<TestContext, Boolean>());
        this.timedout = false;
        this.primaryOrder = 0;
        this.secondaryOrder = 0;
    }

    void unlock() {
        synchronized(this) {
            //force all late tests to fail
            for(TestContext tc : this.azt.contexts) {
                //if the thread has not arrived or already been registered as failed, register it as failed, and stop it
                if(!(this.arrivedThreads.contains(tc) || this.azt.failedContexts.contains(tc))) {
                    azt.failedContexts.add(tc);
                    tc.getThread().stop();
                    logger.debug("Calling ThreadDeath from {} on {}", name(), tc.name());
                }
            }

            //release all of the threads that are currently waiting
            long missingCount = this.primary.getUnarrivedParties();
            for(long i = 0; i < missingCount; i++) {
                this.primary.arrive();
                this.secondary.arrive();
            }
        }
    }

    private synchronized int arrivePrimary() {
        return this.primaryOrder++;
    }

    private synchronized int arriveSecondary() {
        return this.secondaryOrder++;
    }

    int await() {
        return this.await(-1);
    }

    int await(int milliseconds) {
        if(milliseconds == 0) return -1; //we aren't waiting, return no positionnal data

        synchronized(this) {
            logger.debug("registered {}", name());
            this.arrivedThreads.add(TestContext.getCurrent());
        }

        //clear thread interrupt
        Thread.interrupted();

        logger.debug("lock {}", name());

        int phase = this.primary.arrive();

        long milli = getMonoMilliseconds() + milliseconds;

        if(milliseconds < 0)
            this.primary.awaitAdvance(phase);
        else {
            //if it's getting interrupted exceptions and it hasn't actually timedout, then ignore them
            while(true) {
                try {
                    this.primary.awaitAdvanceInterruptibly(
                            phase,
                            (milli - getMonoMilliseconds()),
                            TimeUnit.MILLISECONDS);
                    break;
                }
                catch(InterruptedException iex) {
                    if(timedout)
                        break;
                }
                catch(TimeoutException tex) {
                    if(!timedout) {
                        synchronized(this) {
                            if(!timedout) {
                                timedout = true;

                                this.unlock();
                            }
                        }
                    }
                    break;
                }
            }
        }

        int ret = this.arrivePrimary();

        //first one to release does the reset
        if(0 == ret) {
            this.secondaryOrder = 0;
            this.unlock();
        }

        //secondary barrier to prevent overrun
        this.secondary.arriveAndAwaitAdvance();

        if(0 == this.arriveSecondary())
            this.primaryOrder = 0;
            this.timedout = false;

        logger.debug("free {} as order {}", name(), ret);

        return ret;
    }

    /**
     * Decrements the number of parties that the current barrier is waiting for, and decreases the future number of parties to wait for.
     *
     * This is indicative of a party (Context) having crashed on the scenario and being irrecoverable.
     *
     * @author Andrew Benton
     */
    void dec() {
        this.primary.arriveAndDeregister();
        this.secondary.arriveAndDeregister();
    }

    /**
     * Reset the FlexibleBarrier for the next intercept in the scenario.
     *
     * @author Andrew Benton
     */
    synchronized void reset() {
        this.arrivedThreads.clear();
        this.timedout = false;
        this.primaryOrder = 0;
        this.secondaryOrder = 0;
    }

    /**
     * Reset the number of parties for the barrier back to full and removes arrived threads.
     *
     * @author Andrew Benton
     */
    synchronized void refresh() {
        this.reset();
        this.primary.bulkRegister(this.azt.contexts.size() - this.primary.getRegisteredParties());
        this.secondary.bulkRegister(this.azt.contexts.size() - this.secondary.getRegisteredParties());
    }

    private static String name() {
        TestContext ctx = TestContext.getCurrent();

        if(ctx == null)
            return "<NULL>";
        else
            return ctx.name();
    }

    private static long getMonoMilliseconds() {
        return System.nanoTime() / (1_000_000);
    }
}
