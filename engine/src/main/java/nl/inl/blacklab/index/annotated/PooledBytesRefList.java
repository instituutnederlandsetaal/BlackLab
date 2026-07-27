package nl.inl.blacklab.index.annotated;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.ByteBlockPool;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBlockPool;

/**
 * Compact append-friendly storage for many small payloads.
 *
 * Stores payload bytes in Lucene's block pool and keeps one primitive handle
 * per entry, avoiding one retained BytesRef and one retained byte[] per token.
 */
class PooledBytesRefList extends AbstractList<BytesRef> {

    private static final int NULL_HANDLE = -1;
    private static final int EXTERNAL_HANDLE_BASE = -2;
    private static final int MAX_POOLED_LENGTH = ByteBlockPool.BYTE_BLOCK_SIZE - Short.BYTES;

    private BytesRefBlockPool pool = new BytesRefBlockPool();
    private List<byte[]> oversizedPayloads = new ArrayList<>();
    private int[] handles = new int[16];
    private int size = 0;

    @Override
    public BytesRef get(int index) {
        rangeCheck(index);
        return bytesRefAt(index, new BytesRef());
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(BytesRef payload) {
        ensureCapacity(size + 1);
        store(size, payload);
        size++;
        modCount++;
        return true;
    }

    @Override
    public void add(int index, BytesRef payload) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException(index);
        ensureCapacity(size + 1);
        if (index < size) {
            System.arraycopy(handles, index, handles, index + 1, size - index);
        }
        store(index, payload);
        size++;
        modCount++;
    }

    @Override
    public BytesRef set(int index, BytesRef payload) {
        BytesRef old = get(index);
        store(index, payload);
        return old;
    }

    @Override
    public void clear() {
        pool = new BytesRefBlockPool();
        oversizedPayloads = new ArrayList<>();
        handles = new int[16];
        size = 0;
        modCount++;
    }

    void ensureCapacity(int capacity) {
        if (capacity > handles.length)
            handles = ArrayUtil.grow(handles, capacity);
    }

    @Override
    public Iterator<BytesRef> iterator() {
        return new Iterator<>() {
            private final BytesRef reusable = new BytesRef();
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size;
            }

            @Override
            public BytesRef next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                return bytesRefAt(index++, reusable);
            }
        };
    }

    private void store(int index, BytesRef payload) {
        if (payload == null) {
            handles[index] = NULL_HANDLE;
        } else if (payload.length <= MAX_POOLED_LENGTH) {
            handles[index] = pool.addBytesRef(payload);
        } else {
            int externalIndex = oversizedPayloads.size();
            oversizedPayloads.add(Arrays.copyOfRange(
                    payload.bytes, payload.offset, payload.offset + payload.length));
            handles[index] = EXTERNAL_HANDLE_BASE - externalIndex;
        }
    }

    private BytesRef bytesRefAt(int index, BytesRef result) {
        int handle = handles[index];
        if (handle == NULL_HANDLE)
            return null;
        if (handle >= 0) {
            pool.fillBytesRef(result, handle);
        } else {
            byte[] bytes = oversizedPayloads.get(EXTERNAL_HANDLE_BASE - handle);
            result.bytes = bytes;
            result.offset = 0;
            result.length = bytes.length;
        }
        return result;
    }

    private void rangeCheck(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException(index);
    }
}
