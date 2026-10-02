package pl.sergeyst.gdanskmonitor;

/** Fail closed without including credentials or stored JSON in error messages. */
public final class StorageException extends IllegalStateException {
    public StorageException() { super("Encrypted storage unavailable; existing data preserved"); }
}
