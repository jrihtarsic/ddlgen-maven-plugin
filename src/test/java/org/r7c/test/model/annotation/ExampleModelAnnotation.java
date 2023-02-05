package org.r7c.test.model.annotation;

import jakarta.persistence.*;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Test model for Mojo project execution. The build class is added to mojo project execution via maven
 * project outputDirectory property:
 *  <build><outputDirectory>../../test-classes</outputDirectory> ...
 *
 */
@Entity
@Table(name = "EXAMPLE_MODEL_ANNOTATION",
        indexes = {@Index(name = "idx_annot_string_value", columnList = "STRING_VALUE")})

public class ExampleModelAnnotation {
    @GeneratedValue(generator = "ExampleModelAnnotationSeq")
    @SequenceGenerator(name = "ExampleModelAnnotationSeq", sequenceName = "SEQ_EXAMPLE_MODE_ANNOTATION", allocationSize = 1)
    @Id
    @Column(name = "ID")
    BigInteger id;
    @Column(name = "STRING_VALUE")
    String stringValue;

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public String getStringValue() {
        return stringValue;
    }

    public void setStringValue(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExampleModelAnnotation that = (ExampleModelAnnotation) o;

        if (!Objects.equals(id, that.id)) return false;
        return Objects.equals(stringValue, that.stringValue);
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (stringValue != null ? stringValue.hashCode() : 0);
        return result;
    }
}
