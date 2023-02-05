package org.r7c.test.model.xml;

import java.math.BigInteger;

/**
 * Test model for Mojo project execution. The build class is added to mojo project execution via maven
 * project outputDirectory property:
 *  <build><outputDirectory>../../test-classes</outputDirectory> ...
 *
 */
public class ExampleModelOrm {
    BigInteger id;
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
}
