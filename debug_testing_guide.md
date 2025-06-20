# MDM Account Auto-Creation Testing Guide

This guide provides several methods to test the MDM account auto-creation functionality without setting up a full MDM system.

## Option 1: Debug Mode with Hardcoded Values (Recommended)

The `ManagedSettings.kt` file now includes a debug mode that automatically uses hardcoded test values in debug builds.

### How it works:
- Automatically enabled when `BuildConfig.DEBUG = true`
- Uses predefined test account configurations
- No additional setup required

### To customize test accounts:
Edit the `debugConfigs` map in `ManagedSettings.kt`:

```kotlin
private val debugConfigs = mapOf(
    1 to ManagedAccountConfig(
        baseUrl = "https://your-test-server.com/carddav",
        username = "testuser1",
        password = "testpassword1",
        accountName = "Test Account 1"
    ),
    2 to ManagedAccountConfig(
        baseUrl = "https://your-test-server.com/carddav",
        username = "testuser2", 
        password = "testpassword2",
        accountName = "Test Account 2"
    )
)
```

### To enable/disable:
The debug mode automatically follows `BuildConfig.DEBUG`. For manual control, change:
```kotlin
private val DEBUG_MODE = BuildConfig.DEBUG
// to
private const val DEBUG_MODE = true  // or false
```

## Option 2: ADB Commands (For Real MDM Testing)

Use ADB to set app restrictions directly on the device:

### Set single account:
```bash
adb shell am start-foreground-service \
  -n com.android.deviceowner/.DeviceOwnerService \
  --es command SET_APPLICATION_RESTRICTIONS \
  --es packageName com.atomicasoftware.contactzillasync \
  --es restrictions '{
    "login_base_url": "https://dav.contactzilla.app/addressbooks/testuser/company",
    "login_user_name": "testuser",
    "login_password": "testpassword", 
    "login_account_name": "Test Account",
    "organization": "Test Organization"
  }'
```

### Set multiple accounts:
```bash
adb shell am start-foreground-service \
  -n com.android.deviceowner/.DeviceOwnerService \
  --es command SET_APPLICATION_RESTRICTIONS \
  --es packageName com.atomicasoftware.contactzillasync \
  --es restrictions '{
    "login_base_url": "https://dav.contactzilla.app/addressbooks/testuser1/company",
    "login_user_name": "testuser1",
    "login_password": "testpassword1",
    "login_account_name": "Test Account 1",
    "login_base_url_2": "https://dav.contactzilla.app/addressbooks/testuser2/company",
    "login_user_name_2": "testuser2", 
    "login_password_2": "testpassword2",
    "login_account_name_2": "Test Account 2",
    "organization": "Test Organization"
  }'
```

### Clear restrictions:
```bash
adb shell am start-foreground-service \
  -n com.android.deviceowner/.DeviceOwnerService \
  --es command CLEAR_APPLICATION_RESTRICTIONS \
  --es packageName com.atomicasoftware.contactzillasync
```

## Option 3: Test Management Console (Advanced)

Create a simple debug activity to manage test configurations:

### 1. Add to AndroidManifest.xml (debug variant):
```xml
<activity
    android:name=".ui.debug.DebugMdmActivity"
    android:label="Debug MDM"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### 2. Access via ADB:
```bash
adb shell am start -n com.atomicasoftware.contactzillasync/.ui.debug.DebugMdmActivity
```

## Recommended Testing Flow

1. **Start with Debug Mode**: Use the hardcoded values for initial testing
2. **Customize accounts**: Edit `debugConfigs` with your server details
3. **Test account creation**: 
   - Clear app data: `adb shell pm clear com.atomicasoftware.contactzillasync`
   - Launch app and observe logs for account creation
4. **Verify accounts**: Check that accounts appear in the accounts list
5. **Test sync**: Verify that sync works with the created accounts

## Logging

Enable verbose logging to see account creation progress:

```bash
adb shell setprop log.tag.ManagedAccountSetup DEBUG
adb shell setprop log.tag.ManagedSettings DEBUG
adb logcat | grep -E "(ManagedAccountSetup|ManagedSettings)"
```

## Debugging Tips

### Check if debug mode is active:
Look for log message: "Using debug configuration for account X: Account Name"

### Verify account creation:
Look for log messages:
- "MDM managed accounts detected, creating accounts automatically"
- "Creating managed account: Account Name"
- "Successfully created managed account: Account Name"

### Common issues:
1. **No accounts created**: Check if `hasManagedAccounts()` returns true
2. **Service discovery fails**: Verify the base URL is correct and accessible
3. **Account already exists**: Accounts with same name won't be recreated

### Force account recreation:
```bash
# Clear app data to remove existing accounts
adb shell pm clear com.atomicasoftware.contactzillasync

# Or remove specific account via Android Settings
adb shell am start -a android.settings.SYNC_SETTINGS
```

## Production Deployment

When deploying to production:

1. Ensure `DEBUG_MODE = BuildConfig.DEBUG` (not hardcoded true)
2. Remove or comment out debug configurations
3. Test with real MDM system
4. Verify debug mode is disabled in release builds 