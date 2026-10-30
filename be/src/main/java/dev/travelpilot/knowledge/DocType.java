package dev.travelpilot.knowledge;

/**
 * GUIDE = Wikivoyage section, PLACE = one curated place, TIPS = hand-written advice, TOOL = live result of a chat
 * tool call (weather, search, availability), never stored in the vector store.
 */
public enum DocType {
    GUIDE,
    PLACE,
    TIPS,
    TOOL
}
