package logicBox;

public interface EventLock {
    void eventLockFree(boolean lockStatus, String accessLevel, String reason); //true new lock, false lock already assigned. 'reason' is null on success, populated on failure.
}
