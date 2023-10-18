package jdk.internal.access;

public interface JFRContextAccess {
    int getAllContext(long[] data, int size);
    void setAllContext(long[] data);
}