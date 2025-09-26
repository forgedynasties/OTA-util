# OTA Util

**OTA Util** is a system application designed to manage OTA (Over-The-Air) updates on Android devices.  
It handles the **download of OTA packages** from a specified URL and stores them in the device’s OTA directory for further processing.



## 📌 Features
- Downloads OTA package from a given **URL**.
- Saves OTA package to:
- /data/ota_package  


- Runs as a **system app** (requires privileged permissions).
- Intended to be integrated with Android’s OTA update workflow.

## ⚙️ Requirements
- Android system build with support for privileged/system apps.
- Network access to the OTA server.
- Sufficient storage in `/data/ota_package`.


## 🚀 Usage
1. Provide the OTA package URL (from server or local network).
2. OTA Util downloads the package and stores it at:

/data/ota_package/update.zip


3. The system (or a companion updater app) can then:
- Verify the package.
- Apply the update via `update_engine` or recovery.

## 🔒 Permissions
Since OTA Util is a **system app**, it may require privileged permissions, such as:
- `android.permission.REBOOT` and `android.permission.RECOVERY`
  

## 📂 File Location
- OTA package download path:

/data/ota_package/


Example: `/data/ota_package/update.zip`


## 🛠️ Development Notes
- This app must be signed with the platform key to be deployed as a **system/privileged app**.
- Works best when placed in:

/system/priv-app/OTAUtil/
