<service
        android:name=".core.service.HermesForegroundService"
        android:enabled="true"
        android:exported="false"
        android:foregroundServiceType="connectedDevice" /> <!-- Or "dataSync" depending on usage -->

    <activity
        android:name=".ui.MainActivity"
        android:launchMode="singleTop">
        <!-- Deep Link Filter -->
        <intent-filter android:autoVerify="true">
            <action android:name="android.intent.action.VIEW" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.BROWSABLE" />
            <data android:scheme="hermes" android:host="chat" />
            <data android:scheme="hermes" android:host="new" />
            <data android:scheme="hermes" android:host="settings" />
        </intent-filter>
    </activity>