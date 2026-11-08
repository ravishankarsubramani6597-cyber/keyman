package com.keyman.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RAM-ONLY handoff repository for KEYSENSE Android v1.
 * Mirrors the intent of the iOS KeySenseDebugRepository.sessions array.
 * PROSIT replaces the add() consumer with its own local persistence/upload.
 * This class intentionally performs NO network, disk, or analytics operations.
 */
public final class KeySenseDebugRepositoryV1 {
    private final List<KeySenseTypingSessionV1> sessions = new ArrayList<>();

    public synchronized void add(KeySenseTypingSessionV1 session) {
        if (session == null) throw new IllegalArgumentException("Session required");
        session.validate();
        sessions.add(session);
    }

    /** A copy: callers cannot alter the in-memory repository. */
    public synchronized List<KeySenseTypingSessionV1> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(sessions));
    }

    public synchronized int size() { return sessions.size(); }

    /** Explicit debug reset only. Never call automatically on keyboard dismissal. */
    public synchronized void clear() { sessions.clear(); }
}
