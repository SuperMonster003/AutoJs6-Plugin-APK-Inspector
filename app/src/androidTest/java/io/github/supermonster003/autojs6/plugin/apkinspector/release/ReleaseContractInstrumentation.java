package io.github.supermonster003.autojs6.plugin.apkinspector.release;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import org.autojs.plugin.common.api.PluginInfo;
import org.autojs.plugin.explorer.api.IExplorerActionPlugin;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

/** No AndroidX/JUnit/Kotlin dependency and no keep rule in the production R8 artifact. */
public final class ReleaseContractInstrumentation extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle value) {
        super.onCreate(value);
        arguments = value;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int passed = 0;
        try {
            Context context = getTargetContext();
            PackageManager manager = context.getPackageManager();
            PackageInfo pkg = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            ApplicationInfo app = pkg.applicationInfo;
            check((app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0, "Target is debuggable");
            String expected = arguments.getString("expectedSha256", "");
            check(expected.matches("[0-9a-f]{64}"), "An explicit Release APK digest is required");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(app.sourceDir)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            check(expected.equals(hash.toString()), "Installed APK digest mismatch");
            try (ZipFile zip = new ZipFile(app.sourceDir)) {
                check(zip.stream().noneMatch(entry -> entry.getName().startsWith("lib/") && entry.getName().endsWith(".so")), "Unexpected native library");
            }
            String wake = app.metaData.getString("org.autojs.plugin.WAKE_ACTIVITY");
            check(wake != null, "Missing Wake metadata");
            ActivityInfo activity = manager.getActivityInfo(new ComponentName(context.getPackageName(),
                    wake.startsWith(".") ? context.getPackageName() + wake : wake), 0);
            check(activity.exported && activity.enabled && "org.autojs.permission.PLUGIN".equals(activity.permission), "Wake contract mismatch");
            passed++;

            Intent query = new Intent("org.autojs.plugin.EXPLORER_ACTION")
                    .addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.getPackageName());
            List<ResolveInfo> services = manager.queryIntentServices(query, 0);
            check(services.size() == 1, "Expected one Explorer Action service");
            android.content.pm.ServiceInfo service = services.get(0).serviceInfo;
            check(service.exported && "org.autojs.permission.PLUGIN".equals(service.permission), "Service protection mismatch");
            CountDownLatch connected = new CountDownLatch(1);
            IBinder[] binder = new IBinder[1];
            ServiceConnection connection = new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder value) { binder[0] = value; connected.countDown(); }
                @Override public void onServiceDisconnected(ComponentName name) { binder[0] = null; }
            };
            check(context.bindService(new Intent().setComponent(new ComponentName(context.getPackageName(), service.name)), connection, Context.BIND_AUTO_CREATE), "Bind failed");
            try {
                check(connected.await(10, TimeUnit.SECONDS), "Bind timeout");
                check(binder[0] != null && "org.autojs.plugin.explorer.api.IExplorerActionPlugin".equals(binder[0].getInterfaceDescriptor()), "Binder descriptor mismatch");
                PluginInfo info = IExplorerActionPlugin.Stub.asInterface(binder[0]).getInfo();
                long version = Build.VERSION.SDK_INT >= 28 ? pkg.getLongVersionCode() : pkg.versionCode;
                check(version == info.getVersionCode() && pkg.versionName.equals(info.getVersionName()), "Installed version metadata mismatch");
                check("apk-inspector".equals(info.getId()) && "explorer-action".equals(info.getEngine()) && "default".equals(info.getVariant()), "Plugin identity mismatch");
                check(info.getSupportedAbis() != null && info.getSupportedAbis().length == 0, "Unexpected ABI restriction");
                check(info.getInstruction() != null && !info.getInstruction().isEmpty(), "Missing instruction");
                check(info.getCapabilities() != null, "Missing capabilities");
                Parcel parcel = Parcel.obtain();
                try {
                    info.writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    PluginInfo copy = PluginInfo.CREATOR.createFromParcel(parcel);
                    check(copy.getVersionCode() == version && copy.getId().equals(info.getId()), "Parcelable round-trip mismatch");
                } finally { parcel.recycle(); }
            } finally { context.unbindService(connection); }
            passed++;

            // The AppCompat screens inflate their window through LayoutInflater.Factory2. A renamed
            // framework class copy (ARSCLib's android.util.AttributeSet, 1.1.0 - 1.2.4) broke that
            // override in R8 builds and crashed this process with AbstractMethodError here.
            for (String screen : new String[] {
                    "io.github.supermonster003.autojs6.plugin.apkinspector.ApkInspectorActivity",
                    "io.github.supermonster003.autojs6.plugin.apkinspector.ManifestViewerActivity",
            }) {
                Activity started = startActivitySync(new Intent(Intent.ACTION_MAIN)
                        .setClassName(context.getPackageName(), screen)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                check(started != null && screen.equals(started.getClass().getName()), "Screen did not start: " + screen);
                runOnMainSync(started::finish);
                waitForIdleSync();
            }
            passed++;
            result.putString("releaseFailures", "0");
            result.putString("stream", "Release APK/Wake, service metadata/Parcelable and screen inflation checks passed.\n");
        } catch (Throwable failure) {
            result.putString("releaseFailures", "1");
            result.putString("stream", failure.getClass().getName() + ": " + failure.getMessage() + "\n");
        }
        result.putString("releaseChecks", Integer.toString(passed));
        finish(passed == 3 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
