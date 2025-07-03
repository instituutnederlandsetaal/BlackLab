package org.ivdnt.blacklab.proxy.representation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.commons.collections4.iterators.ReverseListIterator;
import org.ivdnt.blacklab.proxy.helper.ErrorReadingResponse;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@XmlAccessorType(XmlAccessType.FIELD)
@JsonSerialize(using = ContextWords.Serializer.class)
@JsonDeserialize(using = ContextWords.Deserializer.class)
public class ContextWords {

    /** Use this to serialize this class to JSON */
    static class Serializer extends JsonSerializer<ContextWords> {
        @Override
        public void serialize(ContextWords value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            // See what annotations there are (because not every word will have a value for every annotation)
            Set<String> annotations = value.annotationNames;
            if (annotations == null) {
                annotations = new LinkedHashSet<>();
                annotations.add(Word.MAIN_ANNOTATION_NAME);
                for (Word w: value.words) {
                    annotations.addAll(w.otherAnnotations.keySet());
                }
            }

            jgen.writeStartObject();
            for (String annotation: annotations) {
                jgen.writeArrayFieldStart(annotation);
                for (Word w: value.words) {
                    String x;
                    if (annotation.equals(Word.MAIN_ANNOTATION_NAME))
                        x = w.mainAnnotation;
                    else
                        x = w.otherAnnotations.getOrDefault(annotation, "");
                    jgen.writeString(x);
                }
                jgen.writeEndArray();
            }
            jgen.writeEndObject();
        }
    }

    static class Deserializer extends JsonDeserializer<ContextWords> {

        @Override
        public ContextWords deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {

            JsonToken token = parser.currentToken();
            if (token != JsonToken.START_OBJECT)
                throw new ErrorReadingResponse("Expected START_OBJECT, found " + token);

            Map<String, List<String>> wordsPerAnnot = new LinkedHashMap<>();
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;
                if (token != JsonToken.FIELD_NAME)
                    throw new ErrorReadingResponse("Expected END_OBJECT or FIELD_NAME, found " + token);
                String annotationName = parser.getCurrentName();

                token = parser.nextToken();
                if (token != JsonToken.START_ARRAY)
                    throw new ErrorReadingResponse("Expected START_ARRAY, found " + token);
                List<String> words = new ArrayList<>();
                wordsPerAnnot.put(annotationName, words);
                while (true) {
                    token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY)
                        break;
                    if (token != JsonToken.VALUE_STRING)
                        throw new ErrorReadingResponse("Expected END_ARRAY or VALUE_STRING, found " + token);
                    String value = parser.getValueAsString();
                    words.add(value);
                }
            }

            // Convert to per-Word (JSON is structured per-annotation)
            List<Word> result = new ArrayList<>();
            int n = wordsPerAnnot.values().isEmpty() ? 0 : wordsPerAnnot.values().iterator().next().size();
            for (int i = 0; i < n; i++) {
                Word word = new Word();
                Map<String, String> m = word.otherAnnotations = new LinkedHashMap<>();
                for (String name: wordsPerAnnot.keySet()) {
                    String w = wordsPerAnnot.get(name).get(i);
                    if (name.equals(Word.MAIN_ANNOTATION_NAME))
                        word.mainAnnotation = w;
                    else
                        m.put(name, w);
                }
                result.add(word);
            }
            return new ContextWords(result, wordsPerAnnot.keySet());
        }
    }

    @XmlElement(name="w")
    public List<Word> words;

    /** Names of our annotations (or null).
     * Needed if there are no words, so we produce an empty array for each annotation.
     */
    @XmlTransient
    public Set<String> annotationNames;

    @SuppressWarnings("unused")
    private ContextWords() {}

    public ContextWords(List<Word> w, Set<String> annotationNames) {
        this.words = w;
        this.annotationNames = annotationNames;
    }

    public int compareTo(ContextWords other, String annotation, boolean sensitive, boolean reverse, boolean oneWordOnly) {
        Iterator<Word> ai, bi;
        if (!reverse) {
            ai = words.iterator();
            bi = other.words.iterator();
        } else {
            ai = new ReverseListIterator<>(words);
            bi = new ReverseListIterator<>(other.words);
        }
        while (ai.hasNext() && bi.hasNext()) {
            Word aw = ai.next();
            Word bw = bi.next();
            int result = aw.compareTo(bw, annotation, sensitive);
            if (result != 0 || oneWordOnly)
                return result;
        }
        if (!ai.hasNext() && !bi.hasNext()) {
            // Same length and identical elements
            return 0;
        }
        // Lists contain same elements but one is longer than the other
        if (ai.hasNext())
            return 1;
        else
            return -1;
    }

    @Override
    public String toString() {
        return "ContextWords{" +
                "words=" + words +
                '}';
    }
}
