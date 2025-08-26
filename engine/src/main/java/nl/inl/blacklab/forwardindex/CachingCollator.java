package nl.inl.blacklab.forwardindex;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import com.ibm.icu.text.CollationKey;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RawCollationKey;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.util.VersionInfo;

/**
 * A collator that caches collation keys to improve performance if
 * there's many duplicate strings.
 */
public class CachingCollator extends Collator {

    private final Collator baseCollator;

    private final Map<String, CollationKey> cache = new ConcurrentHashMap<>();

    public CachingCollator(Collator baseCollator) {
        this.baseCollator = baseCollator;
        if (!baseCollator.isFrozen())
            throw new IllegalArgumentException("Base collator must be frozen");
    }

    public void clearCache() {
        cache.clear();
    }

    @Override
    public int compare(String source, String target) {
        return baseCollator.compare(source, target);
    }

    @Override
    public CollationKey getCollationKey(String source) {
        return cache.computeIfAbsent(source, __ -> baseCollator.getCollationKey(source));
    }

    @Override
    public RawCollationKey getRawCollationKey(String s, RawCollationKey rawCollationKey) {
        return baseCollator.getRawCollationKey(s, rawCollationKey);
    }

    @Deprecated
    @Override
    public int setVariableTop(String s) {
        return baseCollator.setVariableTop(s);
    }

    @Override
    public int getVariableTop() {
        return baseCollator.getVariableTop();
    }

    @Deprecated
    @Override
    public void setVariableTop(int i) {
        baseCollator.setVariableTop(i);
    }

    @Override
    public VersionInfo getVersion() {
        return baseCollator.getVersion();
    }

    @Override
    public VersionInfo getUCAVersion() {
        return baseCollator.getUCAVersion();
    }

    @Override
    public void setStrength(int newStrength) {
        baseCollator.setStrength(newStrength);
    }

    @Deprecated
    @Override
    public Collator setStrength2(int newStrength) {
        return baseCollator.setStrength2(newStrength);
    }

    @Override
    public void setDecomposition(int decomposition) {
        baseCollator.setDecomposition(decomposition);
    }

    @Override
    public void setReorderCodes(int... order) {
        baseCollator.setReorderCodes(order);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return baseCollator.clone();
    }

    @Override
    public int getStrength() {
        return baseCollator.getStrength();
    }

    @Override
    public int getDecomposition() {
        return baseCollator.getDecomposition();
    }

    @Override
    public boolean equals(String source, String target) {
        return baseCollator.equals(source, target);
    }

    @Override
    public UnicodeSet getTailoredSet() {
        return baseCollator.getTailoredSet();
    }

    @Override
    public int compare(Object source, Object target) {
        return baseCollator.compare(source, target);
    }

    @Override
    public Collator setMaxVariable(int group) {
        return baseCollator.setMaxVariable(group);
    }

    @Override
    public int getMaxVariable() {
        return baseCollator.getMaxVariable();
    }

    @Override
    public int[] getReorderCodes() {
        return baseCollator.getReorderCodes();
    }

    @Override
    public boolean isFrozen() {
        return baseCollator.isFrozen();
    }

    @Override
    public Collator freeze() {
        return baseCollator.freeze();
    }

    @Override
    public Collator cloneAsThawed() {
        return baseCollator.cloneAsThawed();
    }

    @Override
    public ULocale getLocale(ULocale.Type type) {
        return baseCollator.getLocale(type);
    }

    @Override
    public Comparator<Object> reversed() {
        return baseCollator.reversed();
    }

    @Override
    public Comparator<Object> thenComparing(Comparator<? super Object> other) {
        return baseCollator.thenComparing(other);
    }

    @Override
    public <U> Comparator<Object> thenComparing(Function<? super Object, ? extends U> keyExtractor,
            Comparator<? super U> keyComparator) {
        return baseCollator.thenComparing(keyExtractor, keyComparator);
    }

    @Override
    public <U extends Comparable<? super U>> Comparator<Object> thenComparing(
            Function<? super Object, ? extends U> keyExtractor) {
        return baseCollator.thenComparing(keyExtractor);
    }

    @Override
    public Comparator<Object> thenComparingInt(ToIntFunction<? super Object> keyExtractor) {
        return baseCollator.thenComparingInt(keyExtractor);
    }

    @Override
    public Comparator<Object> thenComparingLong(ToLongFunction<? super Object> keyExtractor) {
        return baseCollator.thenComparingLong(keyExtractor);
    }

    @Override
    public Comparator<Object> thenComparingDouble(ToDoubleFunction<? super Object> keyExtractor) {
        return baseCollator.thenComparingDouble(keyExtractor);
    }

    @Override
    public int hashCode() {
        return baseCollator.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        CachingCollator other = (CachingCollator) obj;
        return baseCollator.equals(other.baseCollator);
    }

    public Collator stopCaching() {
        cache.clear();
        return baseCollator;
    }
}
