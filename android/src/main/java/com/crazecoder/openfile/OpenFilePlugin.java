package com.crazecoder.openfile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class OpenFilePlugin implements MethodCallHandler, FlutterPlugin, ActivityAware {
    private @Nullable
    FlutterPluginBinding flutterPluginBinding;
    private Context context;
    private Activity activity;
    private MethodChannel channel;
    private Result result;
    private String filePath;
    private String typeString;
    private boolean isResultSubmitted = false;

    private static final int REQUEST_CODE = 33432;
    private static final int RESULT_CODE = 0x12;
    private static final String TYPE_STRING_APK = "application/vnd.android.package-archive";

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        this.flutterPluginBinding = binding;
        context = flutterPluginBinding.getApplicationContext();
        setup();
    }

    private void setup() {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "open_file");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        if (channel != null) {
            channel.setMethodCallHandler(null);
            channel = null;
        }
        this.flutterPluginBinding = null;
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addRequestPermissionsResultListener((requestCode, permissions, grantResults) -> {
            if (requestCode == REQUEST_CODE) {
                for (int result : grantResults) {
                    if (result != PermissionChecker.PERMISSION_GRANTED) {
                        this.result.error("PERMISSION_DENIED", "Permission denied", null);
                        return false;
                    }
                }
                startActivity();
                return true;
            }
            return false;
        });
        binding.addActivityResultListener((requestCode, resultCode, data) -> {
            if (requestCode == RESULT_CODE) {
                startActivity();
            }
            return false;
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity();
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    @SuppressLint("NewApi")
    @Override
    public void onMethodCall(MethodCall call, @NonNull Result result) {
        isResultSubmitted = false;
        if (call.method.equals("open_file")) {
            this.result = result;
            filePath = call.argument("file_path");
            typeString = call.hasArgument("type") ? call.argument("type") : getFileType(filePath);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                result.error("PERMISSION_DENIED", "Manage external storage permission required", null);
                return;
            }

            if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) || Build.VERSION.SDK_INT >= 33) {
                startActivity();
            } else {
                ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE);
            }
        } else {
            result.notImplemented();
        }
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(activity, permission) == PermissionChecker.PERMISSION_GRANTED;
    }

    private void startActivity() {
        if (filePath == null) {
            result.error("INVALID_PATH", "File path is null", null);
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            result.error("FILE_NOT_FOUND", "File does not exist", null);
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        Uri uri = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? FileProvider.getUriForFile(context, context.getPackageName() + ".fileProvider.com.crazecoder.openfile", file)
                : Uri.fromFile(file);

        intent.setDataAndType(uri, typeString);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            activity.startActivity(intent);
            result.success("File opened successfully");
        } catch (ActivityNotFoundException e) {
            result.error("APP_NOT_FOUND", "No app found to open this file", null);
        }
    }

    private String getFileType(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "pdf": return "application/pdf";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            default: return "*/*";
        }
    }
}
