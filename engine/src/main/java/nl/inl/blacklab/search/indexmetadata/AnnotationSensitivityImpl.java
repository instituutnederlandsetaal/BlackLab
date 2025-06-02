package nl.inl.blacklab.search.indexmetadata;

import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotationSensitivityImpl implements AnnotationSensitivity {

    @XmlTransient
    final Annotation annotation;
    
    final MatchSensitivity sensitivity;

    @Override
    public Annotation annotation() {
        return annotation;
    }

    @Override
    public MatchSensitivity sensitivity() {
        return sensitivity;
    }

    AnnotationSensitivityImpl(Annotation annotation, MatchSensitivity sensitivity) {
        super();
        this.annotation = annotation;
        this.sensitivity = sensitivity;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AnnotationSensitivityImpl that))
            return false;
        return annotation.equals(that.annotation) && sensitivity == that.sensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotation, sensitivity);
    }

    @Override
    public String toString() {
        return luceneField();
    }

    
}
