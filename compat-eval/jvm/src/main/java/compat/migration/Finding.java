package compat.migration;

/** One detected construct. */
public final class Finding {
    public final String rule;
    public final String severity;
    public final Integer position;
    public final String message;
    public final String recommendation;
    public final boolean fixedInFork;
    /** safe textual replacement [start, end) -> replacement, or null if only a message */
    public Integer fixStart, fixEnd;
    public String fixText;

    public Finding(String rule, String severity, Integer position, String message, String recommendation, boolean fixedInFork) {
        this.rule = rule;
        this.severity = severity;
        this.position = position;
        this.message = message;
        this.recommendation = recommendation;
        this.fixedInFork = fixedInFork;
    }

    public Finding fix(int start, int end, String text) {
        fixStart = start;
        fixEnd = end;
        fixText = text;
        return this;
    }
}
