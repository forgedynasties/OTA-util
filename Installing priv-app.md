
qssi/packages/apps/{APP_NAME}

Replace Android.mk with the following Android.bp file

qssi/packages/apps/{APP_NAME}/Android.bp
```
android_app {
    name: "{APP_NAME}",
    srcs: ["src/**/*.java"],
    certificate: "platform",
    privileged: true,
    platform_apis: true,
    dex_preopt: {
        enabled: false,
    },
}
```

This will result in

adb logcat -b all -v time AndroidRuntime:E *:S
```
09-24 06:18:13.627 E/AndroidRuntime( 7136): *** FATAL EXCEPTION IN SYSTEM PROCESS: main
09-24 06:18:13.627 E/AndroidRuntime( 7136): java.lang.IllegalStateException: Signature|privileged permissions not in privapp-permissions allowlist: {com.quectel.otatest (/system/priv-app/AB_OTATEST): android.permission.REBOOT, com.quectel.otatest (/system/priv-app/AB_OTATEST): android.permission.RECOVERY}
```

adb logcat -s PackageManager
```
09-24 06:06:59.568 13885 13885 W PackageManager: Privileged permission android.permission.RECOVERY for package com.quectel.otatest (/system/priv-app/AB_OTATEST) not in privapp-permissions allowlist
09-24 06:06:59.568 13885 13885 W PackageManager: Privileged permission android.permission.REBOOT for package com.quectel.otatest (/system/priv-app/AB_OTATEST) not in privapp-permissions allowlist
```

To prevent this, explicitly add the app in the privapp permiussions allowlist.
qssi/frameworks/base/data/etc/privapp-permissions-platform.xml
For example for com.quectel.otatest:
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
<privapp-permissions package="com.quectel.otatest">
    <permission name="android.permission.REBOOT"/>
    <permission name="android.permission.RECOVERY"/>
</privapp-permissions>
```xml
<permissions>
	<privapp-permissions package="com.quectel.otatest">
	    <permission name="android.permission.REBOOT"/>
	    <permission name="android.permission.RECOVERY"/>
	    <!--replace these with your required permissions-->
	</privapp-permissions>
	
	<!--rest of the allowlist-->
	
</permissions>
```

