
package com.dbaq.cordova.filepickerio;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.ContactsContract.Contacts.Data;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.filestack.Client;
import com.filestack.Config;
import com.filestack.FileLink;
import com.filestack.Policy;
import com.filestack.Sources;
import com.filestack.StorageOptions;
import com.filestack.android.FilestackPicker;
import com.filestack.android.FsConstants;
import com.filestack.android.Selection;
import com.filestack.android.Theme;

public class FilePickerIO extends CordovaPlugin {

    private CallbackContext callbackContext;
    
    private JSONArray executeArgs;
    
    private String action;
    
    public static final String ACTION_SET_KEY = "setKey";
    
    public static final String ACTION_SET_NAME = "setName";

    public static final String ACTION_PICK = "pick";

    public static final String ACTION_PICK_AND_STORE = "pickAndStore";

    public static final String ACTION_HAS_PERMISSION = "hasPermission";
    
    private static final String LOG_TAG = "FilePickerIO";
    private int maxSize;
    private int maxNumber;
    private Config config;
    private int numSelected;

    public FilePickerIO() {}

    private String key;

    private String name;

    @Override
    protected void pluginInitialize() {
        IntentFilter intentFilter = new IntentFilter(FsConstants.BROADCAST_UPLOAD);
        UploadStatusReceiver receiver = new UploadStatusReceiver();
        LocalBroadcastManager.getInstance(cordova.getContext()).registerReceiver(receiver, intentFilter);
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action            The action to execute.
     * @param args              JSONArray of arguments for the plugin.
     * @param callbackContext   The callback context used when calling back into JavaScript.
     * @return                  True if the action was valid, false otherwise.
     */
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        this.callbackContext = callbackContext;
        this.executeArgs = args;
        this.action = action; 
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_HAS_PERMISSION)) {
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, hasPermission()));
            return true;
        }
        else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || action.equals(ACTION_SET_KEY) || action.equals(ACTION_SET_NAME)) {
                execute();
                return true;
            }
            else {
                if (hasPermission()) {
                    execute();
                } else {
                    requestPermission();
                }
                return true;
            }
        }
    }

    private boolean hasPermission() {
        return cordova.hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private void requestPermission() {
        cordova.requestPermission(this, 0, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] grantResults) throws JSONException {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, "User has denied permission"));
                return;
            }
        }
        execute();
    }

    public void execute() {
        final FilePickerIO cdvPlugin = this;
        this.cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    if (ACTION_SET_KEY.equals(cdvPlugin.getAction())) {
                        // Store key for later. All this information could be passed all at once, but
                        // this is how the plugin is set up right now, so I'll go with it
                        key = cdvPlugin.getArgs().getString(0);
                        return;
                    }
                    if (ACTION_SET_NAME.equals(cdvPlugin.getAction())) {
                        name = cdvPlugin.getArgs().getString(0);
                        return;
                    }

                    FilestackPicker.Builder pickerBuilder = new FilestackPicker.Builder();
                    if (ACTION_PICK.equals(cdvPlugin.getAction()) || ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                        parseGlobalArgs(pickerBuilder, cdvPlugin.getArgs());
                        if (ACTION_PICK_AND_STORE.equals(cdvPlugin.getAction())) {
                            parseStoreArgs(pickerBuilder, cdvPlugin.getArgs());
                        }
                        cordova.setActivityResultCallback(FilePickerIO.this);
                        pickerBuilder.autoUploadEnabled(true);
                        Theme.Builder theme = new Theme.Builder();
                        theme.title(name);
                        pickerBuilder.theme(theme.build());
                        pickerBuilder.displayVersionInformation(false);
                        pickerBuilder.build().launch(cordova.getActivity());
                    }
                }
                catch(JSONException exception) {
                    cdvPlugin.getCallbackContext().error("cannot parse json");
                }
            }
        });  
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (FilestackPicker.canReadResult(requestCode, resultCode)) {
            if (resultCode == Activity.RESULT_OK) {
                List<Selection> selections = FilestackPicker.getSelectedFiles(data);
                if (selections.size() > maxNumber){
                    callbackContext.error("Too Many Files Selected");
                }
                numSelected = selections.size();
                results.clear();
                /*try {
                    //callbackContext.success(toJSON(selections));
                } catch (JSONException e) {
                    callbackContext.error("json error");
                }*/
            } else {
                callbackContext.error("nok");
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public void parseGlobalArgs(FilestackPicker.Builder builder, JSONArray args) throws JSONException {
        if (!args.isNull(0)) {
            builder.mimeTypes(parseJSONStringArray(args.getJSONArray(0)));
        }else{
            List<String> types = new ArrayList<>();
            types.add("video/mp4");
            types.add("video/quicktime");
            types.add("video/x-ms-wmv");
            types.add("video/webm");
            types.add("video/x-matroska");
            types.add("image/jpeg");
            types.add("image/webp");
            types.add("image/png");
            types.add("image/gif");
            builder.mimeTypes(types);
        }
        if (!args.isNull(1)) {
            ArrayList<String> sources = new ArrayList<>();
            sources.add(Sources.DEVICE);
            builder.sources(sources);
        }
        if (!args.isNull(2)) {
            builder.multipleFilesSelectionEnabled(args.getBoolean(2));
        }
        if (!args.isNull(3)) {
//            if(args.getInt(3) == 1){
//                builder.multipleFilesSelectionEnabled(false);
//            }
            maxNumber = args.getInt(3);
        }
        if (!args.isNull(4)) {
            maxSize = args.getInt(4);
        }
    }

    public void parseStoreArgs(FilestackPicker.Builder builder, JSONArray args) throws JSONException {
        StorageOptions.Builder storageOptions = new StorageOptions.Builder();
        if (!args.isNull(5)) {
            storageOptions.location(args.getString(5));
        }
        if (!args.isNull(6)) {
            storageOptions.path(args.getString(6));
        }
        if (!args.isNull(7)) {
            storageOptions.container(args.getString(7));
        }
        if (!args.isNull(8)) {
            //Access?
        }
        builder.storageOptions(storageOptions.build());
        if (!args.isNull(9) && !args.isNull(10)) {
            //key,policy,signature
            config = new Config(key, args.getString(9), args.getString(10));
            builder.config(config);
        }
    }

    public List<String> parseJSONStringArray(JSONArray jSONArray) throws JSONException {
        ArrayList<String> a = new ArrayList<String>(jSONArray.length());
        for(int i = 0; i < jSONArray.length(); i++){
            a.add(jSONArray.getString(i));
        }
        return a;
    }

    public JSONArray toJSON(List<FileResult> fpFiles) throws JSONException {
        JSONArray res = new JSONArray();
        for (FileResult fpFile : fpFiles) {
            JSONObject f = new JSONObject();
            f.put("url", fpFile.url);
            f.put("filename", fpFile.filename);
            f.put("mimetype", fpFile.mimetype);
            f.put("size", fpFile.size);
            res.put(f);
        }
        return res;
    }

    public String getAction() {
        return this.action;
    }

    public JSONArray getArgs() {
        return this.executeArgs;
    }

    public CallbackContext getCallbackContext() {
        return this.callbackContext;
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    public class UploadStatusReceiver extends BroadcastReceiver {
        private static final String TAG = "UploadStatusReceiver";

        @Override
        public void onReceive(Context context, Intent intent) {
            Locale locale = Locale.getDefault();
            String status = intent.getStringExtra(FsConstants.EXTRA_STATUS);
            Selection selection = intent.getParcelableExtra(FsConstants.EXTRA_SELECTION);
            FileLink fileLink = (FileLink) intent.getSerializableExtra(FsConstants.EXTRA_FILE_LINK);

            String name = selection.getName();
            String handle = fileLink != null ? fileLink.getHandle() : "n/a";
            String msg = String.format(locale, "upload %s: %s (%s)", status, name, handle);
            Log.i(TAG, msg);

            if (status.equals("complete")){
                FileResult result = new FileResult();
                result.filename = name;
                result.mimetype = selection.getMimeType();
                result.size = selection.getSize();
                result.url = selection.getPath();

                results.add(result);
                if (results.size() == numSelected) {
                    try {
                        callbackContext.success(toJSON(results));
                    } catch (JSONException e) {
                        callbackContext.error("json error");
                    }
                }
            }else{
                LocalBroadcastManager.getInstance(cordova.getContext()).unregisterReceiver(this);
                callbackContext.error("File upload failed");
            }
        }
    }

    private List<FileResult> results = new ArrayList<>();

    private class FileResult {
        public String url;
        public String filename;
        public int size;
        public String mimetype;
    }
}
