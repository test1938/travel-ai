package dev.travelpilot.conversation;

/** What a chat message wants (plan/02-architecture.md, chat flow). */
public enum Intent {
    /** A question about the destination or the plan: answered with RAG + citations. */
    ASK_QUESTION,
    /** Change the plan (budget, a day, a place, indoor...): partial re-planning, new version. */
    MODIFY_PLAN,
    /** Greetings and thanks: short fixed reply, no model call. */
    SMALL_TALK,
    /** Anything else (code, other cities, prompt extraction...): polite fixed refusal. */
    OUT_OF_SCOPE
}
