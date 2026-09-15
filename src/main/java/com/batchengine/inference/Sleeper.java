package com.batchengine.inference;

@FunctionalInterface
public interface Sleeper {
    void sleep(long milliseconds) throws InterruptedException;
}
