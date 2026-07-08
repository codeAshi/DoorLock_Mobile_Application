package logicBox;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Map;

import localDatabase.Locks;

import static android.content.ContentValues.TAG;


public class LockCheck {
    EventLock eventLock;
    private Map<String, Object> dataCollector;
    private localDatabase.Locks locaDBLocks;

    public LockCheck(EventLock eventLock, Context mContext) {
        this.eventLock = eventLock;
        locaDBLocks = new Locks(mContext, null);

    }

    public LockCheck() {
    }

    public void checkLockFree(final String lockNumberRaw, final String userid) {
        final String lockNumber = lockNumberRaw.trim();
        Log.d(TAG, "checkLockFree: querying lockentry/[" + lockNumber + "] for user " + userid);

        DatabaseReference myRef = FirebaseDatabase.getInstance().getReference("lockentry/" + lockNumber);
        myRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists()) {
                    Log.d(TAG, "onDataChange: Lock found in lockentry. Checking lockuser...");
                    DatabaseReference lockUserRef = FirebaseDatabase.getInstance().getReference("lockuser/" + lockNumber);
                    lockUserRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            // Check if lock has any users assigned
                            boolean hasUsers = dataSnapshot.exists() && dataSnapshot.hasChild("users") && dataSnapshot.child("users").getChildrenCount() > 0;

                            if (hasUsers) {
                                Log.d(TAG, "onDataChange: Lock has existing owners.");
                                
                                // 1. Check if the CURRENT user is already an owner/user
                                if (dataSnapshot.child("users").hasChild(userid)) {
                                    Log.d(TAG, "onDataChange: User " + userid + " is already authorized.");
                                    eventLock.eventLockFree(true, "owner", null);
                                    return;
                                }

                                // 2. Not the owner. Check for guest access.
                                Log.d(TAG, "onDataChange: Checking guest access for " + userid);
                                DatabaseReference reference = FirebaseDatabase.getInstance()
                                        .getReference("authorizeaccess/" + userid);
                                reference.addListenerForSingleValueEvent(new ValueEventListener() {
                                    @Override
                                    public void onDataChange(DataSnapshot dataSnapshot) {
                                        if (dataSnapshot.exists() && dataSnapshot.hasChild(lockNumber)) {
                                            eventLock.eventLockFree(true, "guest", null);
                                        } else {
                                            String error = "Lock " + lockNumber + " is registered to another account.\n\n" +
                                                           "Your UID: " + userid + "\n\n" +
                                                           "If you are the owner, delete 'lockuser/" + lockNumber + "' in Firebase Console.";
                                            eventLock.eventLockFree(false, null, error);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(DatabaseError databaseError) {
                                        eventLock.eventLockFree(false, null, "Could not check guest access: " + databaseError.getMessage());
                                    }
                                });

                            } else {
                                eventLock.eventLockFree(true, "owner", null);
                            }
                        }

                        @Override
                        public void onCancelled(DatabaseError databaseError) {
                            eventLock.eventLockFree(false, null, "Could not check lock ownership: " + databaseError.getMessage());
                        }
                    });
                } else {
                    //Lock not available in lockentry.
                    eventLock.eventLockFree(false, null, "No lock with number \"" + lockNumber + "\" exists in lockentry. Ask whoever provisioned the device to add it first.");
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                eventLock.eventLockFree(false, null, "Could not reach the database: " + databaseError.getMessage());
            }
        });
    }

    public void getLockDataFromFirebase(final String lockNumber) {
        DatabaseReference myRef = FirebaseDatabase.getInstance().getReference("lockentry/" + lockNumber);
        myRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                dataCollector = (Map<String, Object>) dataSnapshot.getValue();
                if (dataCollector == null) return;

                locaDBLocks.id = lockNumber;   //lockid
                locaDBLocks.name = dataCollector.get("lockname") != null ? dataCollector.get("lockname").toString() : "Lock"; //lock name
                locaDBLocks.location = dataCollector.get("address") != null ? dataCollector.get("address").toString() : "";   //location
                locaDBLocks.mac = dataCollector.get("mac") != null ? dataCollector.get("mac").toString() : "";   //mac

                //Setting data to local database
                locaDBLocks.setData();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });
    }
}
