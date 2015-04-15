package com.gome.clover.zeromq;

import zmq.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
/**
 * ━━━━━━神兽出没━━━━━━
 * 　　　┏┓　　　┏┓
 * 　　┏┛┻━━━┛┻┓
 * 　　┃　　　　　　　┃
 * 　　┃　　　━　　　┃
 * 　　┃　┳┛　┗┳　┃
 * 　　┃　　　　　　　┃
 * 　　┃　　　┻　　　┃
 * 　　┃　　　　　　　┃
 * 　　┗━┓　　　┏━┛
 * 　　　　┃　　　┃神兽保佑, 永无BUG!
 * 　　　　┃　　　┃Code is far away from bug with the animal protecting
 * 　　　　┃　　　┗━━━┓
 * 　　　　┃　　　　　　　┣┓
 * 　　　　┃　　　　　　　┏┛
 * 　　　　┗┓┓┏━┳┓┏┛
 * 　　　　　┃┫┫　┃┫┫
 * 　　　　　┗┻┛　┗┻┛
 * ━━━━━━感觉萌萌哒━━━━━━
 * Module Desc:clover
 * User: wangyue-ds6 || stark_summer@qq.com
 * Date: 2014/12/25
 * Time: 21:24
 */
public class Helper {
    public static AtomicInteger counter = new AtomicInteger(2);

    private Helper()
    {
    }

    public static class DummyCtx extends Ctx
    {
    }

    public static class DummySocketChannel implements WritableByteChannel
    {
        private int bufsize;
        private byte[] buf;

        public DummySocketChannel()
        {
            this(64);
        }

        public DummySocketChannel(int bufsize)
        {
            this.bufsize = bufsize;
            buf = new byte[bufsize];
        }

        public byte[] data()
        {
            return buf;
        }

        @Override
        public void close() throws IOException
        {
        }

        @Override
        public boolean isOpen()
        {
            return true;
        }

        @Override
        public int write(ByteBuffer src) throws IOException
        {
            int remaining = src.remaining();
            if (remaining > bufsize) {
                src.get(buf);
                return bufsize;
            }
            src.get(buf, 0, remaining);
            return remaining;
        }

    }

    public static DummyCtx ctx = new DummyCtx();

    public static class DummyIOThread extends IOThread
    {
        public DummyIOThread()
        {
            super(ctx, 2);
        }
    }

    public static class DummySocket extends SocketBase
    {
        public DummySocket()
        {
            super(ctx, counter.get(), counter.get());
            counter.incrementAndGet();
        }

        @Override
        protected void xattach_pipe(Pipe pipe_, boolean icanhasall_) {

        }

        @Override
        protected void xterminated(Pipe pipe_) {

        }
    }

    public static class DummySession extends SessionBase
    {
        public List<Msg> out = new ArrayList<Msg>();

        public DummySession()
        {
            this(new DummyIOThread(),  false, new DummySocket(), new Options(), new Address("tcp", "localhost:9090", false));
        }

        public DummySession(IOThread ioThread, boolean connect,
                            SocketBase socket, Options options, Address addr)
        {
            super(ioThread, connect, socket, options, addr);
        }

        public int pushMsg(Msg msg)
        {
            System.out.println("session.write " + msg);
            out.add(msg);
            return 0;
        }

        public Msg pullMsg()
        {
            System.out.println("session.read " + out);
            if (out.size() == 0) {
                return null;
            }
            Msg msg = out.remove(0);

            return msg;
        }

    }

    public static void bounce(SocketBase sb, SocketBase sc)
    {
        byte[] content = "12345678ABCDEFGH12345678abcdefgh".getBytes(ZMQ.CHARSET);

        //  Send the message.
        int rc = ZMQ.zmq_send(sc, content, 32, ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.zmq_send(sc, content, 32, 0);
        assertThat(rc, is(32));

        //  Bounce the message back.
        Msg msg;
        msg = ZMQ.zmq_recv(sb, 0);
        assert (msg.size() == 32);
        long rcvmore = ZMQ.zmq_getsockopt(sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 1);
        msg = ZMQ.zmq_recv(sb, 0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt(sb, ZMQ.ZMQ_RCVMORE);
        assert (rcvmore == 0);
        rc = ZMQ.zmq_send(sb, new Msg(msg), ZMQ.ZMQ_SNDMORE);
        assert (rc == 32);
        rc = ZMQ.zmq_send(sb, new Msg(msg), 0);
        assert (rc == 32);

        //  Receive the bounced message.
        msg = ZMQ.zmq_recv(sc, 0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt(sc, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore , is(1L));
        msg = ZMQ.zmq_recv(sc,  0);
        assert (rc == 32);
        rcvmore = ZMQ.zmq_getsockopt(sc, ZMQ.ZMQ_RCVMORE);
        assertThat(rcvmore , is(0L));
        //  Check whether the message is still the same.
        //assert (memcmp (buf2, content, 32) == 0);
    }

    public static void send(Socket sa, String data) throws IOException
    {
        byte[] content = data.getBytes(ZMQ.CHARSET);

        byte[] length = String.format("%04d", content.length).getBytes(ZMQ.CHARSET);

        byte[] buf = new byte[1024];
        int reslen;
        int rc;
        //  Bounce the message back.
        InputStream in = sa.getInputStream();
        OutputStream out = sa.getOutputStream();

        out.write(length);
        out.write(content);

        System.out.println("sent " + data.length() + " " + data);
        int toRead = 4; // 4 + greeting_size
        int read = 0;
        while (toRead > 0) {
            rc = in.read(buf, read, toRead);
            read += rc;
            toRead -= rc;
            System.out.println("read " + rc + " total_read " + read + " toRead " + toRead);
        }
        System.out.println(String.format("%02x %02x %02x %02x", buf[0], buf[1], buf[2], buf[3]));
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
        reslen = Integer.valueOf(new String(buf, 0, 4, ZMQ.CHARSET));

        in.read(buf, 0, reslen);
        System.out.println("recv " + reslen + " " + new String(buf, 0, reslen, ZMQ.CHARSET));
    }
}