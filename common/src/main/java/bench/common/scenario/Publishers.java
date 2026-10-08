package bench.common.scenario;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Minimal blocking consumption of a Reactive Streams {@link Publisher}, independent of any reactive
 * library, so every reactive implementation is consumed identically.
 */
public final class Publishers {

    private Publishers() {
    }

    /**
     * Subscribes, requests everything, and blocks until the publisher terminates.
     *
     * @return the number of elements received
     */
    public static long drain(Publisher<?> publisher) {
        BlockingSubscriber<Object> subscriber = new BlockingSubscriber<>(null);
        publisher.subscribe(subscriber);
        subscriber.await();
        subscriber.rethrow();
        return subscriber.count;
    }

    /**
     * @return the last element, or {@code null} if the publisher completed empty
     */
    public static <T> T last(Publisher<T> publisher) {
        BlockingSubscriber<T> subscriber = new BlockingSubscriber<>(null);
        publisher.subscribe(subscriber);
        subscriber.await();
        subscriber.rethrow();
        return subscriber.last;
    }

    /**
     * @return all elements, in order
     */
    public static <T> List<T> list(Publisher<T> publisher) {
        List<T> result = new ArrayList<>();
        BlockingSubscriber<T> subscriber = new BlockingSubscriber<>(result::add);
        publisher.subscribe(subscriber);
        subscriber.await();
        subscriber.rethrow();
        return result;
    }

    /**
     * @return the error the publisher terminated with, or {@code null} if it completed normally
     */
    public static Throwable error(Publisher<?> publisher) {
        BlockingSubscriber<Object> subscriber = new BlockingSubscriber<>(null);
        publisher.subscribe(subscriber);
        subscriber.await();
        return subscriber.error;
    }

    private static final class BlockingSubscriber<T> implements Subscriber<T> {

        private final CountDownLatch done = new CountDownLatch(1);
        private final Consumer<T> consumer;
        private volatile T last;
        private volatile long count;
        private volatile Throwable error;

        BlockingSubscriber(Consumer<T> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T t) {
            // onNext is serialized by the Reactive Streams contract
            last = t;
            count++;
            if (consumer != null) {
                consumer.accept(t);
            }
        }

        @Override
        public void onError(Throwable t) {
            error = t;
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        void await() {
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for the publisher", e);
            }
        }

        void rethrow() {
            Throwable t = error;
            if (t instanceof RuntimeException re) {
                throw re;
            } else if (t instanceof Error e) {
                throw e;
            } else if (t != null) {
                throw new IllegalStateException(t);
            }
        }
    }
}
