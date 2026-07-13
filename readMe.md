# 🔐 Server Room — Bluetooth Door Lock System

An Android application for controlling a Bluetooth-based door lock (HC-05) for a server room. The app uses Firebase Authentication for user login, Firebase Realtime Database for lock registry and access logs, and Bluetooth (SPP) to send lock/unlock commands directly to the hardware module.

---
<table>
<tr>
<td align="center">
<img src="https://github.com/user-attachments/assets/78c5c4f6-0388-4c92-87ab-bc2595545f0f" width="250"/>
</td>
<td align="center">
<img src="https://github.com/user-attachments/assets/86eb89e7-da72-47f7-b789-ce7e137a7b5a" width="250"/>
</td>
<td align="center">
<img width="720" height="1600" alt="WhatsApp Image 2026-07-13 at 11 21 25 PM (1)" src="https://github.com/user-attachments/assets/a9473335-140f-415a-841b-91d81d07d439" />

</td>
</tr>
</table>


## 📱 Features

- **Firebase Login / Register** — Email & password authentication
- **Lock Registration** — Register a lock using a lock number pre-provisioned in Firebase
- **Bluetooth Door Control** — Send `L` (Lock) and `U` (Unlock) commands to an HC-05 Bluetooth module
- **Access Logs** — Every unlock is logged to Firebase with user name and timestamp
- **User Management** — View and manage users who have access to a lock
- **Guest Access** — Admin can grant guest access to other users via Firebase
- **Local SQLite Storage** — Lock MAC address and details cached locally after first registration
- **Android 12+ Support** — Runtime Bluetooth permission handling (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`)

---

## 🏗️ Project Structure

```
app/src/main/java/
├── com/project/server/room/
│   ├── Splash.java                  # Splash / entry screen
│   ├── Login.java                   # Firebase email login
│   ├── CreateAccountUsingEmail.java # New user registration
│   ├── Home.java                    # Home screen — list of registered locks
│   ├── RegisterLock.java            # Register a new lock using lock number
│   ├── LockOperate.java             # Bluetooth lock/unlock control screen ⬅️ main screen
│   ├── LockLog.java                 # Unlock history / access logs
│   ├── LockUsers.java               # Users with access to a lock
│   └── AddUser.java                 # Grant access to a new user
│
├── localDatabase/
│   ├── SetupDB.java                 # SQLite database setup
│   ├── Locks.java                   # Lock CRUD operations (local DB)
│   └── EventLockDisplayData.java   # Callback interface for lock display
│
└── logicBox/
    ├── LockCheck.java               # Firebase lock validation logic
    ├── SharedSpace.java             # SharedPreferences helper
    ├── EventLock.java               # Callback interface for lock events
    ├── AesEncryptionAlgorithm.java  # AES encryption utility
    ├── Utility.java                 # General utilities
    └── Validator.java               # Input validation helpers
```

---

## 🔧 Tech Stack

| Component | Technology |
|---|---|
| Language | Java |
| Min SDK | 21 (Android 5.0) |
| Target SDK | 31 (Android 12) |
| Authentication | Firebase Auth |
| Database (cloud) | Firebase Realtime Database |
| Database (local) | SQLite |
| Bluetooth | Android Bluetooth SPP (HC-05) |
| UI | Material Design, BottomNavigationView |

---

## ☁️ Firebase Database Structure

Before a user can register a lock in the app, the lock must be manually provisioned in Firebase Realtime Database by an admin:

```json
{
  "lockentry": {
    "LOCK001": {
      "lockname": "Server Room Door",
      "address": "Server Room - Floor 2",
      "mac": "XX:XX:XX:XX:XX:XX"
    }
  },
  "lockuser": {
    "LOCK001": {
      "users": {
        "<firebase_user_uid>": "John Doe"
      }
    }
  },
  "accesslevel": {
    "LOCK001": {
      "<firebase_user_uid>": "owner"
    }
  },
  "authorizeaccess": {
    "<firebase_user_uid>": {
      "LOCK001": true
    }
  },
  "logs": {
    "LOCK001": {
      "<push_id>": {
        "name": "John Doe",
        "timestamp": "08 Jul 26 14:30"
      }
    }
  }
}
```

---

## 🚀 Setup & Installation

### 1. Clone / Extract the project
Open Android Studio → File → Open → select the `Server Room` folder (the root folder containing `build.gradle` and `settings.gradle`).

### 2. Add `google-services.json`
- Go to [Firebase Console](https://console.firebase.google.com) → your project → Project Settings
- Download `google-services.json`
- Place it inside `app/` folder

### 3. Sync Gradle
Click **Sync Now** when Android Studio prompts. If you see an SDK path warning, click **OK** — Android Studio will fix it automatically.

### 4. Fix Gradle JDK (if prompted)
Go to **File → Settings → Build, Execution, Deployment → Build Tools → Gradle** → set **Gradle JDK** to `JAVA_HOME` or any listed JDK.

### 5. Build APK
Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**

APK output location:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔵 Hardware Setup (HC-05 Bluetooth Module)

The app connects to an **HC-05 Bluetooth module** using the standard SPP UUID.

**Commands sent:**
| Button | Command Sent |
|---|---|
| LOCK | `L` |
| UNLOCK | `U` |

**HC-05 default settings:**
- Baud Rate: `9600`
- Password: `1234` or `0000`

**Finding the MAC address:**
1. On your Android phone go to **Settings → Bluetooth**
2. Pair with HC-05
3. Tap the info/gear icon next to HC-05 — the MAC address is shown (e.g. `20:15:06:26:10:43`)

---

## 📋 First-Time Usage Flow

```
1. Admin adds lock to Firebase (lockentry node) with MAC address
        ↓
2. User creates account / logs in
        ↓
3. User taps Register Lock → enters 5-digit lock number
        ↓
4. App validates with Firebase → saves MAC locally
        ↓
5. User taps the lock on Home screen
        ↓
6. LockOperate screen opens → app connects to HC-05 via Bluetooth
        ↓
7. Tap LOCK → sends "L" | Tap UNLOCK → sends "U" + logs to Firebase
```

---

## 🔐 Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />  <!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />     <!-- Android 12+ -->
<uses-permission android:name="android.permission.INTERNET" />
```

---

## 🐛 Common Issues

| Error | Cause | Fix |
|---|---|---|
| "No lock with number exists in lockentry" | Lock not provisioned in Firebase | Add lock under `lockentry/{lockNumber}` in Firebase Console |
| "Connection failed. Check device is paired" | HC-05 not paired or out of range | Pair HC-05 in Android Bluetooth settings first |
| "Bluetooth permission denied" | User denied permission on Android 12+ | Go to phone Settings → Apps → Server Room → Permissions → Allow Bluetooth |
| "57 errors in AndroidManifest" | Project opened from wrong folder | Open the root `Server Room` folder, not `app/src/main` |
| Gradle JDK error | JDK not configured | File → Settings → Gradle → set Gradle JDK |

---

## 👨‍💻 Developer Notes

- The Bluetooth connection runs on a **background thread** (`ConnectedThread`) to avoid blocking the UI
- Lock MAC address is stored in **local SQLite** after first registration so the app works offline for connecting
- Unlock events are pushed to **Firebase logs** with user name and timestamp for audit trail
- The lock number entered in `RegisterLock` must be **exactly 5 characters** (one per input box)
