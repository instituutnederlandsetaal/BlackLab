package nl.inl.blacklab.tools.frequency.data;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import java.util.Map;

public class IdMap {
    private final Object2IntOpenCustomHashMap<int[]> map;
    private int _id = 1;

    public IdMap() {
        this.map = new Object2IntOpenCustomHashMap<>(IntArrays.HASH_STRATEGY);
        map.defaultReturnValue(-1);
    }

    public int putOrGet(final int[] key) {
        // calculate key
        final int idToPut = _id;
        final int id = map.putIfAbsent(key, idToPut);
        if (id == -1) {
            // new ID was created
            _id++; // increment for next time
            return idToPut;

        } else {
            // existing ID
            return id;
        }
    }

    public int putOrGet(final int[] values, final int[] idxSelection) {
        // calculate key based on selected indices
        final var key = getKey(values, idxSelection);
        return putOrGet(key);
    }

    private int[] getKey(final int[] values, final int[] idxSelection) {
        final var key = new int[idxSelection.length];
        for (int i = 0; i < idxSelection.length; i++) {
            key[i] = values[idxSelection[i]];
        }
        return key;
    }

    public Map<int[], Integer> getMap() {
        return map;
    }
}
