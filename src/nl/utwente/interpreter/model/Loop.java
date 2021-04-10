package nl.utwente.interpreter.model;

import java.util.Objects;

public class Loop {
    private int to;
    private int by;
    private int increment;
    private boolean hasVarying = false;
    private boolean exitLoop = false;

    public void initVarying(Integer from, Integer to, Integer by) {
        this.to = Objects.requireNonNullElse(to, 1);
        this.by = Objects.requireNonNullElse(by, 1);

        this.increment = Objects.requireNonNullElse(from, 1);
        this.hasVarying = true;
    }

    public boolean hasVarying() {
        return this.hasVarying;
    }

    public void increment() {
        this.increment += by;

        if (this.increment > to) {
            this.exitLoop = true;
        }
    }

    public void exit() {
        this.exitLoop = true;
    }

    public boolean exitLoop() {
        return this.exitLoop;
    }

    public int getVaryingValue() {
        return increment;
    }
}
