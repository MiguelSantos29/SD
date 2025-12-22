package src;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class Demultiplexer implements AutoCloseable {
    private final TaggedConnection conn;
    ReentrantLock lock = new ReentrantLock();
    private Map<Integer, FrameValue> map = new HashMap<>();
    private IOException exception = null;


    private class FrameValue {
        int waiters = 0;
        private Queue<byte[]> queue = new LinkedList<>();
        private Condition cond = lock.newCondition();
        public FrameValue(){}
    }


    public Demultiplexer(TaggedConnection conn) {
        this.conn = conn;
    }

    public void start() {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    TaggedConnection.Frame frame = conn.receive();
                    this.lock.lock();
                    try {
                        FrameValue fv = map.get(frame.tag);
                        if (fv == null) {
                            fv = new FrameValue();
                            map.put(frame.tag, fv);
                        }
                        fv.queue.add(frame.data);
                        fv.cond.signal();
                    } finally {
                        this.lock.unlock();
                    }
                }
            } catch (IOException e) {
                this.lock.lock();
                try {
                    this.exception = e;
                    for (FrameValue fv : map.values()) {
                        fv.cond.signalAll();
                    }
                } finally {
                    this.lock.unlock();
                }
            }
        });
        t.start();
    }

    public void send(TaggedConnection.Frame frame) throws IOException {
        conn.send(frame);
    }
    public void send(int tag, byte[] data) throws IOException { conn.send(tag, data); }

    public byte[] receive(int tag) throws IOException, InterruptedException {
        this.lock.lock();
        FrameValue fv;
        try{
            fv = map.get(tag);
            if (fv == null) {
                fv = new FrameValue();
                map.put(tag, fv);
            }
            fv.waiters++;
            while (true) {
                if(!(fv.queue.isEmpty())){
                    fv.waiters--;
                    byte[] result = fv.queue.poll();
                    if (fv.waiters == 0 && fv.queue.isEmpty()) {
                        map.remove(tag);
                    }
                    return result;
                }
                if (exception != null) {
                    throw exception;
                }
                fv.cond.await();
            }
        } finally {
            this.lock.unlock();
        }
    }
    public void close() throws IOException {
        conn.close();
    }
}
