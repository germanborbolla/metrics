package com.yammer.metrics.core;

import com.yammer.metrics.stats.EWMA;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A meter metric which measures mean throughput and one-, five-, and fifteen-minute
 * exponentially-weighted moving average throughputs.
 *
 * @see <a href="http://en.wikipedia.org/wiki/Moving_average#Exponential_moving_average">EMA</a>
 */
public class Meter implements Metered {
    private static final long TICK_INTERVAL = TimeUnit.SECONDS.toNanos(5);

    private final EWMA m1Rate = EWMA.oneMinuteEWMA();
    private final EWMA m5Rate = EWMA.fiveMinuteEWMA();
    private final EWMA m15Rate = EWMA.fifteenMinuteEWMA();

    private final AtomicLong count = new AtomicLong();
    private final long startTime;
    private final AtomicLong lastTick;
    private final TimeUnit rateUnit;
    private final String eventType;
    private final Clock clock;

    /**
     * Creates a new {@link Meter}.
     *
     * @param eventType  the plural name of the event the meter is measuring (e.g., {@code
     *                   "requests"})
     * @param rateUnit   the rate unit of the new meter
     * @param clock      the clock to use for the meter ticks
     */
    Meter(String eventType, TimeUnit rateUnit, Clock clock) {
        this.rateUnit = rateUnit;
        this.eventType = eventType;
        this.clock = clock;
        this.startTime = this.clock.tick();
        this.lastTick = new AtomicLong(startTime);
    }

    @Override
    public TimeUnit rateUnit() {
        return rateUnit;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    /**
     * Updates the moving averages.
     */
    void tick() {
        m1Rate.tick();
        m5Rate.tick();
        m15Rate.tick();
    }

    /**
     * Mark the occurrence of an event.
     */
    public void mark() {
        mark(1);
    }

    /**
     * Mark the occurrence of a given number of events.
     *
     * @param n the number of events
     */
    public void mark(long n) {
        tickIfNecessary();
        count.addAndGet(n);
        m1Rate.update(n);
        m5Rate.update(n);
        m15Rate.update(n);
    }

    private void tickIfNecessary() {
        final long oldTick = lastTick.get();
        final long newTick = clock.tick();
        final long age = newTick - oldTick;
        if (age > TICK_INTERVAL && lastTick.compareAndSet(oldTick, newTick)) {
            final long requiredTicks = age / TICK_INTERVAL;
            System.out.println("requiredTicks = " + requiredTicks);
            for (long i = 0; i < requiredTicks; i++) {
                tick();
            }
        }
    }

    @Override
    public long count() {
        return count.get();
    }

    @Override
    public double fifteenMinuteRate() {
        tickIfNecessary();
        return m15Rate.rate(rateUnit);
    }

    @Override
    public double fiveMinuteRate() {
        tickIfNecessary();
        return m5Rate.rate(rateUnit);
    }

    @Override
    public double meanRate() {
        if (count() == 0) {
            return 0.0;
        } else {
            final long elapsed = (clock.tick() - startTime);
            return convertNsRate(count() / (double) elapsed);
        }
    }

    @Override
    public double oneMinuteRate() {
        tickIfNecessary();
        return m1Rate.rate(rateUnit);
    }

    private double convertNsRate(double ratePerNs) {
        return ratePerNs * (double) rateUnit.toNanos(1);
    }

    @Override
    public <T> void processWith(MetricProcessor<T> processor, MetricName name, T context) throws Exception {
        processor.processMeter(name, this, context);
    }
}
