/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.messageconcept.peoplesyncclient.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.app.usage.UsageStatsManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import android.os.PowerManager
import android.os.StatFs
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.dav4jvm.exception.DavException
import com.messageconcept.peoplesyncclient.BuildConfig
import com.messageconcept.peoplesyncclient.sync.account.InvalidAccountException
import com.messageconcept.peoplesyncclient.R
import com.messageconcept.peoplesyncclient.TextTable
import com.messageconcept.peoplesyncclient.db.AppDatabase
import com.messageconcept.peoplesyncclient.repository.AccountRepository
import com.messageconcept.peoplesyncclient.resource.LocalAddressBook
import com.messageconcept.peoplesyncclient.settings.AccountSettings
import com.messageconcept.peoplesyncclient.settings.AccountSettings.Companion.KEY_BASE_URL
import com.messageconcept.peoplesyncclient.settings.AccountSettings.Companion.KEY_USERNAME
import com.messageconcept.peoplesyncclient.settings.SettingsManager
import com.messageconcept.peoplesyncclient.sync.SyncDataType
import com.messageconcept.peoplesyncclient.sync.SyncFrameworkIntegration
import com.messageconcept.peoplesyncclient.sync.worker.BaseSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.PrintWriter
import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use
import at.bitfire.vcard4android.Utils.asSyncAdapter as asContactsSyncAdapter

@WorkerThread
class DebugInfoGenerator @Inject constructor(
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val logger: Logger,
    private val settings: SettingsManager,
    private val syncFramework: SyncFrameworkIntegration
) {

    operator fun invoke(
        syncAccount: Account?,
        syncAuthority: String?,
        cause: Throwable?,
        localResource: String?,
        remoteResource: String?,
        timestamp: Long?,
        writer: PrintWriter
    ) {
        writer.println("--- BEGIN DEBUG INFO ---")
        writer.println()

        // begin with a timestamp to know when the error occurred
        if (timestamp != null) {
            val instant = Instant.ofEpochSecond(timestamp)
            writer.println("NOTIFICATION TIME")
            val iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            writer.println("Local time: ${instant.atZone(ZoneId.systemDefault()).format(iso)}")
            writer.println("UTC: ${instant.atZone(ZoneOffset.UTC).format(iso)}")
            writer.println()
        }

        // continue with most specific information
        if (syncAccount != null || syncAuthority != null) {
            writer.append("SYNCHRONIZATION INFO\n")
            if (syncAccount != null)
                writer.append("Account: $syncAccount\n")
            if (syncAuthority != null)
                writer.append("Authority: $syncAuthority\n")
            writer.append("\n")
        }

        if (cause != null) {
            writer.println("EXCEPTION")
            cause.printStackTrace(writer)
            writer.println()
        }

        // exception details
        if (cause is DavException) {
            cause.request?.let { request ->
                writer.append("HTTP REQUEST\n$request\n")
                cause.requestBody?.let { writer.append(it) }
                writer.append("\n\n")
            }
            cause.response?.let { response ->
                writer.append("HTTP RESPONSE\n$response\n")
                cause.responseBody?.let { writer.append(it) }
                writer.append("\n\n")
            }
        }

        if (localResource != null)
            writer.append("LOCAL RESOURCE\n$localResource\n\n")

        if (remoteResource != null)
            writer.append("REMOTE RESOURCE\n$remoteResource\n\n")

        // software info
        try {
            writer.append("SOFTWARE INFORMATION\n")
            val table = TextTable("Package", "Version", "Code", "Installer", "Notes")
            val pm = context.packageManager

            val packageNames = mutableSetOf(      // we always want info about these packages:
                BuildConfig.APPLICATION_ID,            // PeopleSync
            )
            // ... and info about contact and calendar provider
            for (authority in arrayOf(ContactsContract.AUTHORITY, CalendarContract.AUTHORITY))
                pm.resolveContentProvider(authority, 0)?.let { packageNames += it.packageName }
            // ... and info about contact, calendar, task-editing apps
            val dataUris = arrayOf(
                ContactsContract.Contacts.CONTENT_URI,
            )
            for (uri in dataUris) {
                val viewIntent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(uri, /* some random ID */ 1))
                for (info in pm.queryIntentActivities(viewIntent, 0))
                    packageNames += info.activityInfo.packageName
            }

            for (packageName in packageNames)
                try {
                    val info = pm.getPackageInfo(packageName, 0)
                    val appInfo = info.applicationInfo
                    val notes = mutableListOf<String>()
                    if (appInfo?.enabled == false)
                        notes += "disabled"
                    if (appInfo?.flags?.and(ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0)
                        notes += "<em>on external storage</em>"
                    table.addLine(
                        info.packageName, info.versionName, PackageInfoCompat.getLongVersionCode(info),
                        pm.getInstallerPackageName(info.packageName) ?: '—', notes.joinToString(", ")
                    )
                } catch (_: PackageManager.NameNotFoundException) {
                }
            writer.append(table.toString())
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Couldn't get software information", e)
        }

        // system info
        val locales: Any = LocaleList.getAdjustedDefault()
        writer.append(
            "\n\nSYSTEM INFORMATION\n\n" +
                    "Android version: ${Build.VERSION.RELEASE} (${Build.DISPLAY})\n" +
                    "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n\n" +
                    "Locale(s): $locales\n" +
                    "Time zone: ${TimeZone.getDefault().id}\n"
        )
        val filesPath = Environment.getDataDirectory()
        val statFs = StatFs(filesPath.path)
        writer.append("Internal memory ($filesPath): ")
            .append(Formatter.formatFileSize(context, statFs.availableBytes))
            .append(" free of ")
            .append(Formatter.formatFileSize(context, statFs.totalBytes))
            .append("\n\n")

        // power saving
        if (Build.VERSION.SDK_INT >= 28)
            context.getSystemService<UsageStatsManager>()?.let { statsManager ->
                val bucket = statsManager.appStandbyBucket
                writer
                    .append("App standby bucket: ")
                    .append(
                        when {
                            bucket <= 5 -> "exempted (very good)"
                            bucket <= UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active (good)"
                            bucket <= UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set (bad: job restrictions apply)"
                            bucket <= UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent (bad: job restrictions apply)"
                            bucket <= UsageStatsManager.STANDBY_BUCKET_RARE -> "rare (very bad: job and network restrictions apply)"
                            bucket <= UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted (very bad: job and network restrictions apply)"
                            else -> "$bucket"
                        }
                    )
                writer.append('\n')
            }
        context.getSystemService<PowerManager>()?.let { powerManager ->
            writer.append("App exempted from power saving: ")
                .append(if (powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) "yes (good)" else "no (bad)")
                .append('\n')
                .append("System in power-save mode: ")
                .append(if (powerManager.isPowerSaveMode) "yes (restrictions apply!)" else "no")
                .append('\n')
        }
        // system-wide sync
        writer.append("System-wide synchronization: ")
            .append(if (syncFramework.getMasterSyncAutomatically()) "automatically" else "manually")
            .append("\n\n")

        // connectivity
        context.getSystemService<ConnectivityManager>()?.let { connectivityManager ->
            writer.append("\n\nCONNECTIVITY\n\n")
            val activeNetwork = connectivityManager.activeNetwork
            connectivityManager.allNetworks.sortedByDescending { it == activeNetwork }.forEach { network ->
                val properties = connectivityManager.getLinkProperties(network)
                connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                    writer.append(if (network == activeNetwork) " ☒ " else " ☐ ")
                        .append(properties?.interfaceName ?: "?")
                        .append("\n   - ")
                        .append(capabilities.toString().replace('&', ' '))
                        .append('\n')
                }
                if (properties != null) {
                    writer.append("   - DNS: ")
                        .append(properties.dnsServers.joinToString(", ") { it.hostAddress })
                    if (Build.VERSION.SDK_INT >= 28 && properties.isPrivateDnsActive)
                        writer.append(" (private mode)")
                    writer.append('\n')
                }
            }
            writer.append('\n')

            connectivityManager.defaultProxy?.let { proxy ->
                writer.append("System default proxy: ${proxy.host}:${proxy.port}\n")
            }
            writer.append("Data saver: ").append(
                when (connectivityManager.restrictBackgroundStatus) {
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
                    else -> connectivityManager.restrictBackgroundStatus.toString()
                }
            ).append('\n')
            writer.append('\n')
        }

        writer.append("\n\nCONFIGURATION\n")
        // notifications
        val nm = NotificationManagerCompat.from(context)
        writer.append("\nNotifications")
        if (!nm.areNotificationsEnabled())
            writer.append(" (blocked!)")
        writer.append(":\n")
        if (Build.VERSION.SDK_INT >= 26) {
            val channelsWithoutGroup = nm.notificationChannels.toMutableSet()
            for (group in nm.notificationChannelGroups) {
                writer.append(" - ${group.id}")
                if (Build.VERSION.SDK_INT >= 28)
                    writer.append(" isBlocked=${group.isBlocked}")
                writer.append('\n')
                for (channel in group.channels) {
                    writer.append("  * ${channel.id}: importance=${channel.importance}\n")
                    channelsWithoutGroup -= channel
                }
            }
            for (channel in channelsWithoutGroup)
                writer.append(" - ${channel.id}: importance=${channel.importance}\n")
        }
        writer.append('\n')
        // permissions
        writer.append("Permissions:\n")
        val ownPkgInfo = context.packageManager.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_PERMISSIONS)
        for (permission in ownPkgInfo.requestedPermissions.orEmpty()) {
            val shortPermission = permission.removePrefix("android.permission.")
            writer.append(" - $shortPermission: ")
                .append(
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
                        "granted"
                    else
                        "denied"
                )
                .append('\n')
        }
        writer.append('\n')

        // accounts
        writer.append("\nACCOUNTS")
        val accountManager = AccountManager.get(context)
        val accounts = accountRepository.getAll()
        for (account in accounts)
            dumpAccount(account, accountManager, writer)

        val addressBookAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).toMutableList()
        if (addressBookAccounts.isNotEmpty()) {
            writer.append("ADDRESS BOOK ACCOUNTS\n\n")
            for (account in addressBookAccounts)
                dumpAddressBookAccount(account, accountManager, writer)
        }

        // non-sync workers
        writer.append("OTHER WORKERS\n")
        dumpOtherWorkers(accounts, writer)

        // database dump
        writer.append("\n\n\nDATABASE DUMP\n\n")
        db.dump(writer, arrayOf("webdav_document"))

        // app settings
        writer.append("\nAPP SETTINGS\n\n")
        settings.dump(writer)

        writer.append("--- END DEBUG INFO ---\n")
    }

    /**
     * Appends relevant android account information the given writer.
     */
    private fun dumpAccount(account: Account, accountManager: AccountManager, writer: Writer) {
        writer.append("\n\n - Account: ${account.name}\n")
        val accountSettings = accountSettingsFactory.create(account)

        writer.append(dumpAndroidAccount(account, AccountDumpInfo.caldavAccount(account)))
        try {
            val credentials = accountSettings.credentials()
            val authStr = mutableListOf<String>()
            if (credentials.username != null)
                authStr += "user name"
            if (credentials.password != null)
                authStr += "password"
            if (credentials.certificateAlias != null)
                authStr += "client certificate"
            credentials.authState?.let { authState ->
                authStr += "OAuth [${authState.authorizationServiceConfiguration?.authorizationEndpoint}]"
            }
            if (authStr.isNotEmpty())
                writer  .append("  Authentication: ")
                    .append(authStr.joinToString(", "))
                    .append("\n")

            writer.append("  WiFi only: ${accountSettings.getSyncWifiOnly()}")
            accountSettings.getSyncWifiOnlySSIDs()?.let { ssids ->
                writer.append(", SSIDs: ${ssids.joinToString(", ")}")
            }
            writer.append(
                "\n  Contact group method: ${accountSettings.getGroupMethod()}\n"
            )

            accountManager.getUserData(account, KEY_USERNAME)?.let { userName ->
                writer.append("  Username: ${userName}\n")
            }
            accountManager.getUserData(account, KEY_BASE_URL)?.let { baseUrl ->
                writer.append("  Base URL: ${baseUrl}\n")
            }

            writer.append("\nSync workers:\n")
            dumpSyncWorkers(account, writer)
            writer.append("\n")
        } catch (e: InvalidAccountException) {
            writer.append("$e\n")
        }
        writer.append('\n')
    }

    /**
     * Appends relevant address book type android account information to the given writer.
     */
    private fun dumpAddressBookAccount(account: Account, accountManager: AccountManager, writer: Writer) {
        writer.append("  * Address book: ${account.name}\n")
        val table = dumpAndroidAccount(account, AccountDumpInfo.addressBookAccount(account))
        writer.append(TextTable.indent(table, 4))
            .append("Collection ID: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_COLLECTION_ID)}\n")
            .append("    Read-only: ${accountManager.getUserData(account, LocalAddressBook.USER_DATA_READ_ONLY) ?: 0}\n\n")
    }

    /**
     * Retrieves specified information from an android account.
     */
    private fun dumpAndroidAccount(account: Account, infos: Iterable<AccountDumpInfo>): String {
        val table = TextTable("Authority", "isSyncable", "syncsOnContentChange", "Entries")
        for (info in infos) {
            var nrEntries = "—"
            if (info.countUri != null)
                try {
                    context.contentResolver.acquireContentProviderClient(info.authority)?.use { client ->
                        client.query(info.countUri, null, null, null, null)?.use { cursor ->
                            nrEntries = "${cursor.count} ${info.countStr}"
                        }
                    }
                } catch (e: Exception) {
                    nrEntries = e.toString()
                }
            table.addLine(
                info.authority,
                syncFramework.isSyncable(account, info.authority),
                syncFramework.syncsOnContentChange(account, info.authority),
                nrEntries
            )
        }
        return table.toString()
    }

    /**
     * Generates a table to display worker statuses.
     *
     * By default, the table provides the following columns:
     * Tags, State, Next run, Retries, Generation, Periodicity
     *
     * If more tables are desired, they can be added using [extraColumns].
     *
     * The key of the map is the position of the column relative to the default ones, so for example,
     * if a column is going to be added between "State" and "Next run", the index should be `2`.
     *
     * The value the map is a pair whose first element is the column name, and the second one is
     * the generator of the value.
     * The generator will be called on every worker info found after running the query, and should
     * return a String that will be placed in the cell.
     *
     * @param query The query to use for fetching the workers.
     * @param extraColumns Defaults to an empty map, pass extra columns to be added to the table.
     * @param filter Allows filtering the results given by the [query]. Will exclude all [WorkInfo]
     * whose filter result is `false`. Defaults to all `true` (do not filter).
     */
    private fun workersInfoTable(
        query: WorkQuery,
        extraColumns: Map<Int, Pair<String, (WorkInfo) -> String>> = emptyMap(),
        filter: (WorkInfo) -> Boolean = { true }
    ): String {
        val columnNames = mutableListOf("Tags", "State", "Next run", "Retries", "Generation", "Periodicity")
        for ((index, column) in extraColumns) {
            val (columnName) = column
            columnNames.add(index, columnName)
        }

        val table = TextTable(columnNames)
        val wm = WorkManager.getInstance(context)
        val workInfos = wm.getWorkInfos(query).get().filter(filter)
        for (workInfo in workInfos) {
            val line = mutableListOf(
                workInfo.tags.map { it.replace("\\bat\\.bitfire\\.davdroid\\.".toRegex(), ".") },
                "${workInfo.state} (${workInfo.stopReason})",
                workInfo.nextScheduleTimeMillis.let { nextRun ->
                    when (nextRun) {
                        Long.MAX_VALUE -> "—"
                        else -> DateUtils.getRelativeTimeSpanString(nextRun)
                    }
                },
                workInfo.runAttemptCount,
                workInfo.generation,
                workInfo.periodicityInfo?.let { periodicity ->
                    "every ${periodicity.repeatIntervalMillis/60000} min"
                } ?: "not periodic"
            )

            for ((index, column) in extraColumns) {
                val (_, transformer) = column
                val value = transformer(workInfo)
                line.add(index, value)
            }

            table.addLine(line)
        }
        return table.toString()
    }

    /**
     * Gets sync workers info.
     *
     * Note: WorkManager does not return worker names when queried, so we create them and ask
     * whether they exist one by one.
     */
    private fun dumpSyncWorkers(account: Account, writer: Writer) {
        writer.append(workersInfoTable(
            WorkQuery.Builder.fromTags(
                SyncDataType.entries.map { BaseSyncWorker.commonTag(account, it) }
            ).build(),
            mapOf(
                1 to ("Data Type" to { workInfo: WorkInfo ->
                    // See: BaseSyncWorker.commonTag
                    // "sync-$dataType ${account.type}/${account.name}"
                    workInfo.tags
                        // Search for the first tag that starts with sync-
                        .first { it.startsWith("sync-") }
                        // Get everything before the space (get rid of the account)
                        .substringBefore(' ')
                        // Remove the "sync-" prefix
                        .removePrefix("sync-")
                })
            )
        ))
    }

    /**
     * Gets account-independent workers info. This is done by querying all the workers, and
     * filtering the ones that depend on an account (the opposite of [dumpSyncWorkers]).
     *
     * Note: WorkManager does not return worker names when queried, so we create them and ask
     * whether they exist one by one.
     *
     * @param accounts The list of accounts in the system. This is used for filtering account-dependent
     * workers.
     */
    private fun dumpOtherWorkers(accounts: Array<Account>, writer: Writer) {
        val syncWorkersTags = accounts.flatMap { account ->
            SyncDataType.entries.map { BaseSyncWorker.commonTag(account, it) }
        }

        writer.append(workersInfoTable(
            // Fetch all workers
            WorkQuery.Builder.fromStates(WorkInfo.State.entries).build(),
            filter = { it.tags.all { tag -> !syncWorkersTags.contains(tag) } }
        ))
    }


    data class AccountDumpInfo(
        val account: Account,
        val authority: String,
        val countUri: Uri?,
        val countStr: String?,
    ) {

        companion object {

            internal fun caldavAccount(account: Account) = listOf(
                AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "wrongly assigned raw contact(s)")
            )

            internal fun addressBookAccount(account: Account) = listOf(
                AccountDumpInfo(account, ContactsContract.AUTHORITY, ContactsContract.RawContacts.CONTENT_URI.asContactsSyncAdapter(account), "raw contact(s)")
            )

        }

    }

}