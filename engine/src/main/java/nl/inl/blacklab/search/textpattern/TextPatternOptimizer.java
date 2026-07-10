package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.search.lucene.SpanFilter;

public class TextPatternOptimizer extends TextPatternRewriterBase {

    @Override
    public TextPattern visitPositionFilter(TextPatternPositionFilter original) {
        TextPattern tp = super.visitPositionFilter(original);
        if (tp instanceof TextPatternPositionFilter pf) {
            switch (pf.getOperation()) {
                case WITHIN -> {
                    if (pf.getProducer() instanceof TextPatternPositionFilter pf2 && !pf2.isInvert()) {
                        if (pf2.getOperation() == SpanFilter.CONTAINING && pf2.getProducer().equals(pf.getFilter())) {
                            // (L containing S) within L --> L containing S
                            return new TextPatternPositionFilter(pf2.getFilter(), pf.getFilter(),
                                    SpanFilter.WITHIN, false);
                        } else if (pf2.getOperation() == SpanFilter.WITHIN &&
                            pf2.getFilter().equals(pf.getFilter())) {
                            // (S within L) within L --> S within L
                            return pf2;
                        }
                    } else if (pf.getFilter() instanceof TextPatternPositionFilter pf2 && !pf2.isInvert()) {
                        if (pf2.getOperation() == SpanFilter.CONTAINING &&
                                pf2.getFilter().equals(pf.getProducer())) {
                            // S within (L containing S) --> S within L
                            return new TextPatternPositionFilter(pf.getProducer(), pf2.getProducer(),
                                    SpanFilter.WITHIN, false);
                        } else if (pf2.getOperation() == SpanFilter.WITHIN &&
                                pf2.getProducer().equals(pf.getProducer())) {
                            // S within (S within L) --> S within L
                            return pf2;
                        }
                    }
                }
                case CONTAINING ->  {
                    if (pf.getProducer() instanceof TextPatternPositionFilter pf2 && !pf2.isInvert()) {
                        if (pf2.getOperation() == SpanFilter.CONTAINING &&
                                pf2.getFilter().equals(pf.getFilter())) {
                            // (L containing S) containing S --> L containing S
                            return pf2;
                        } else if (pf2.getOperation() == SpanFilter.WITHIN && pf2.getProducer().equals(pf.getFilter())) {
                            // (S within L) containing S => S within L
                            return pf2;
                        }
                    } else if (pf.getFilter() instanceof TextPatternPositionFilter pf2 && !pf2.isInvert()) {
                        if (pf2.getOperation() == SpanFilter.CONTAINING &&
                                pf2.getProducer().equals(pf.getProducer())) {
                            // L containing (L containing S) --> L containing S
                            return pf2;
                        } else if (pf2.getOperation() == SpanFilter.WITHIN &&
                                pf2.getFilter().equals(pf.getProducer())) {
                            // L containing (S within L) --> L containing S
                            return new TextPatternPositionFilter(pf.getProducer(), pf2.getProducer(),
                                    SpanFilter.CONTAINING, false);
                        }
                    }
                }
            }
        }
        return tp;
    }
}
