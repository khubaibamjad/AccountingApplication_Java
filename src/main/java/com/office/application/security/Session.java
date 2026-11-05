package com.office.application.security;

/** Who is logged in right now (a desktop app has exactly one session). */
public final class Session {
    private static volatile User current;

    private Session() { }

    public static void start(User user) { current = user; }
    public static void end() { current = null; }
    public static boolean isLoggedIn() { return current != null; }

    /** Name written to the audit log; "system" for work done before login (e.g. tests, setup). */
    public static String username() {
        User u = current;
        return u == null ? "system" : u.username();
    }
}
