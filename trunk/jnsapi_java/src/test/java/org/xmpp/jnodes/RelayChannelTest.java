package org.xmpp.jnodes;

import junit.framework.TestCase;
import org.xmpp.jnodes.nio.*;

import java.io.IOException;
import java.net.BindException;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RelayChannelTest extends TestCase {

    final static String encode = "UTF-8";
    final static String localIP = "127.0.0.1";
    private final static ExecutorService executorService = Executors.newCachedThreadPool();

    public void testDatagramChannels() {
        final List<Future> futures = new ArrayList<Future>();

        for (int i = 0; i < 8; i++) {
            final int ii = i;
            futures.add(executorService.submit(new Runnable() {
                public void run() {
                    socketTest(new TestSocket.ChannelProvider() {
                        public ListenerDatagramChannel open(DatagramListener datagramListener, SocketAddress address) throws IOException {
                            return SelDatagramChannel.open(datagramListener, address);
                        }

                        public String getName() {
                            return "SelDatagramChannel";
                        }
                    }, 500 * ii + 10000, 500 * ii + 10250);
                }
            }));
        }
        boolean finished = false;

        while (!finished) {
            try {
                Thread.sleep(5000);
                Thread.yield();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            finished = true;
            for (final Future f : futures) {
                finished &= f.isDone();
            }
        }
    }


    public void testDatagramChannelsExternal(final int portA, final int portB) {

        final SocketAddress sa = new InetSocketAddress(localIP, portA);
        final SocketAddress sb = new InetSocketAddress(localIP, portB);

        for (int i = 0; i < 5; i++) {
            socketTest(new TestSocket.ChannelProvider() {
                public ListenerDatagramChannel open(DatagramListener datagramListener, SocketAddress address) throws IOException {
                    return SelDatagramChannel.open(datagramListener, address);
                }

                public String getName() {
                    return "SelDatagramChannel";
                }
            }, sa, sb);
        }
    }

    public void socketTest(final TestSocket.ChannelProvider provider, final int socketRange, final int relayRange) {
        try {

            final int num = 10;
            final int packets = 25;
            final int tests = 100;
            final List<TestSocket> cs = new ArrayList<TestSocket>();
            final List<RelayChannel> rc = new ArrayList<RelayChannel>();

            assertEquals(num % 2, 0);

            for (int i = 0, j = 0, l = 0; i < num; i++, j++, l++) {
                for (int t = 0; t < 50; t++) {
                    try {
                        final TestSocket s = new TestSocket(localIP, socketRange + j, provider);
                        cs.add(s);
                        break;
                    } catch (BindException e) {
                        j++;
                    }
                }
                if (i % 2 == 0) {
                    for (int t = 0; t < 50; t++) {
                        try {
                            final RelayChannel c = new RelayChannel(localIP, relayRange + l, localIP, relayRange + l + 1);
                            rc.add(c);
                            break;
                        } catch (BindException e) {
                            l++;
                        }
                    }
                }
            }

            long tTime = 0;
            long min = 1000;
            long max = 0;
            final List<Future> futures = new ArrayList<Future>();

            for (int h = 0; h < tests; h++) {

                final long start = System.currentTimeMillis();

                for (int ii = 0; ii < packets; ii++) {
                    futures.add(executorService.submit(new Runnable() {
                        public void run() {
                            for (int i = 0; i < num; i++) {
                                final TestSocket a = cs.get(i);
                                final TestSocket b = i % 2 == 0 ? cs.get(i + 1) : cs.get(i - 1);

                                final RelayChannel c = rc.get(i / 2);
                                final SocketAddress d = i % 2 == 0 ? c.getAddressA() : c.getAddressB();

                                try {
                                    a.getChannel().send(b.getExpectedBuffer().duplicate(), d);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }));
                }

                boolean finished = false;

                while (!finished) {
                    Thread.sleep(1);
                    finished = true;
                    for (final Future f : futures) {
                        finished &= f.isDone();
                    }
                }

                finished = false;
                final int target = packets - 1;
                while (!finished) {
                    Thread.sleep(1);
                    finished = true;
                    for (int i = 0; i < num; i++) {
                        finished &= cs.get(i).getI().get() >= target;
                    }
                }

                final long d = (System.currentTimeMillis() - start);
                if (d > max) max = d;
                if (d < min) min = d;
                tTime += d;

                for (final TestSocket ts : cs)
                    ts.getI().set(0);
            }

            System.out.println(provider.getName() + " -> Max: " + max + "ms, Min: " + min + "ms, Avg: " + Math.ceil(tTime / tests) + "ms");

            for (final TestSocket ts : cs) {
                ts.getChannel().close();
            }
            for (final RelayChannel r : rc) {
                r.close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void socketTest(final TestSocket.ChannelProvider provider, final SocketAddress sa, final SocketAddress sb) {
        try {

            final int num = 2;
            int packets = 30;
            int tests = 100;
            final List<TestSocket> cs = new ArrayList<TestSocket>();

            assertEquals(num % 2, 0);

            for (int i = 0, j = 0, l = 0; i < num; i++, j++, l++) {
                for (int t = 0; t < 50; t++) {
                    try {
                        final TestSocket s = new TestSocket(localIP, 50000 + j, provider);
                        cs.add(s);
                        break;
                    } catch (BindException e) {
                        j++;
                    }
                }
            }

            long tTime = 0;
            long min = 1000;
            long max = 0;

            for (int h = 0; h < tests; h++) {

                final long start = System.currentTimeMillis();

                for (int ii = 0; ii < packets; ii++)
                    for (int i = 0; i < num; i++) {
                        final TestSocket a = cs.get(i);
                        final TestSocket b = i % 2 == 0 ? cs.get(i + 1) : cs.get(i - 1);

                        final SocketAddress d = i % 2 == 0 ? sa : sb;

                        a.getChannel().send(b.getExpectedBuffer().duplicate(), d);
                    }

                boolean finished = false;
                final int target = packets - 1;
                while (!finished) {
                    Thread.sleep(1);
                    finished = true;
                    for (int i = 0; i < num; i++) {
                        finished &= cs.get(i).getI().get() >= target;
                    }
                }

                final long d = (System.currentTimeMillis() - start);
                if (d > max) max = d;
                if (d < min) min = d;
                tTime += d;

                for (final TestSocket ts : cs)
                    ts.getI().set(0);
            }

            System.out.println(provider.getName() + " -> Max: " + max + "ms, Min: " + min + "ms, Avg: " + Math.ceil(tTime / tests) + "ms");

            for (final TestSocket ts : cs) {
                ts.getChannel().close();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}