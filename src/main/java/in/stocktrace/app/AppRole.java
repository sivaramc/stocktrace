package in.stocktrace.app;

/** Application-level role for a stocktrace login. */
public enum AppRole {
    /** Regular end user. Can manage their own broker credentials and place self-trades. */
    USER,
    /** Administrator. Can list/activate/deactivate other users. */
    ADMIN
}
