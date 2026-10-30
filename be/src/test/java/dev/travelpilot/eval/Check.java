package dev.travelpilot.eval;

/**
 * One eval check. {@code hard} = a rule every plan must satisfy (schema, ids, budget, child age, pace, locked days);
 * the others are case expectations (what a good answer should contain).
 */
public record Check(String name, boolean hard, boolean passed, String detail) {

    static Check hard(String name, boolean passed, String detail) {
        return new Check(name, true, passed, passed ? "" : detail);
    }

    static Check expect(String name, boolean passed, String detail) {
        return new Check(name, false, passed, passed ? "" : detail);
    }
}
