package com.rom.gamandipo;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 1;
    public static final String Broadcast_PLAY_NEW_AUDIO = "com.rom.gamandipo.PlayNewAudio";

    private MediaPlayerService mediaPlayerService;
    boolean serviceBound = false;
    ArrayList<Audio> audioArrayList;

    ImageView collapsingImageView;

    int imageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        collapsingImageView = (ImageView) findViewById(R.id.collapsingImageView);

        loadCollapsingImage(imageIndex);

        if(checkAndRequestPermissions()) {
            loadAudioList();
        }

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                // playAudio("https://upload.wikimedia.org/wikipedia/commons/6/6c/Grieg_Lyric_Pieces_Kobold.ogg");
                //play the first audio in the ArrayList
                // playAudio(2);

                if(imageIndex == 4) {
                    imageIndex = 0;
                    loadCollapsingImage(imageIndex);
                }else{
                    loadCollapsingImage(++imageIndex);
                }
            }
        });

    }

    private void loadAudioList() {
        loadAudio();
        initRecyclerView();
    }

    private boolean checkAndRequestPermissions() {
        if(SDK_INT > Build.VERSION_CODES.M) {
            int permissionReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE);
            int permissionStorage = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE);

            List<String> listPermissionsNeeded = new ArrayList<>();

            if(permissionReadPhoneState != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
            }

            if (permissionStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }

            if(!listPermissionsNeeded.isEmpty()) {
                ActivityCompat.requestPermissions(this,listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),REQUEST_ID_MULTIPLE_PERMISSIONS);
                return  false;
            }
            else {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        String TAG = "LOG_PERMISSION";
        Log.d(TAG,"Perm callback called");

        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSIONS : {
                Map<String,Integer> perms = new HashMap<>();
                //Init map with both all the perms
                perms.put(Manifest.permission.READ_PHONE_STATE,PackageManager.PERMISSION_GRANTED);
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED);

                // fill with actual results from user
                if(grantResults.length > 0) {
                    for(int k = 0 ; k < permissions.length; k++) {
                        perms.put(permissions[k],grantResults[k]);
                    }
                }

                //compare our permission with user granted

                if (perms.get(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                        && perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                        ) {
                    Log.d(TAG, "Phone state and storage permissions granted");
                    // process the normal flow
                    //else any one or both the permissions are not granted
                    loadAudioList();

                }
                else {
                    Log.d(TAG,"Some perms are not granted, ask the damn man again");
                    //permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
                    //shouldShowRequestPermissionRationale will return true
                    //show the dialog or snackbar saying its necessary and try again otherwise proceed with setup.

                    if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_PHONE_STATE)
                      || ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE)) {

                        showDialogOK("phone state and storage permission are required for this app",
                                new DialogInterface.OnClickListener(){

                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int which) {
                                        switch (which) {
                                            case DialogInterface.BUTTON_POSITIVE :
                                                checkAndRequestPermissions();
                                                break;
                                            case DialogInterface.BUTTON_NEGATIVE:
                                                System.exit(0);
                                                MainActivity.this.finish(); // exit or repeat setup again
                                                break;
                                        }
                                    }
                                });
                    }
                    //perms denied by usr with never ask again checked
                    else {
                        Toast.makeText(this,"GO to setting and enable permissions",Toast.LENGTH_LONG).show();
                        //proceed with logic by disabling the related features or quit the app.
                    }
                }
            }
        }
    }

    private void showDialogOK(String message, DialogInterface.OnClickListener onClickListener) {

        new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton("Ok",onClickListener)
                        .setNegativeButton("Cancel", onClickListener)
                        .create()
                        .show();
    }

    private void initRecyclerView() {
        if(audioArrayList != null && audioArrayList.size() > 0) {
            RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
            RecyclerView_Adapter adapter = new RecyclerView_Adapter(audioArrayList,getApplication());

            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.addOnItemTouchListener(new CustomTouchListener(this, new onItemClickListener() {
                @Override
                public void onClick(View view, int index) {
                    playAudio(index);
                }
            }));
        }
    }

    private void loadCollapsingImage(int i){
        TypedArray typedArray =getResources().obtainTypedArray(R.array.images);
        collapsingImageView.setImageDrawable(typedArray.getDrawable(i));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if(id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("ServiceStatus",serviceBound);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        serviceBound = savedInstanceState.getBoolean("ServiceStatus");
    }

    //Bindind this client to the audioplayer service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder localBinder = (MediaPlayerService.LocalBinder) iBinder;
            serviceBound = true;
            mediaPlayerService = localBinder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serviceBound = false;
        }
    };

    private void playAudio(int audioIndex){
        //Check is service is active
        if(!serviceBound) {
            //Store Serializable audioList to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudio(audioArrayList);
            storage.storeAudioIndex(audioIndex);

            Intent intent = new Intent(this,MediaPlayerService.class);
            startService(intent);
            bindService(intent,serviceConnection, Context.BIND_AUTO_CREATE);
        } else {
            //Store the new audioIndex to SharedPreferences
            StorageUtil storage = new StorageUtil(getApplicationContext());
            storage.storeAudioIndex(audioIndex);
            //Service is active
            //Send a broadcast to the service -> PLAY_NEW_AUDIO
            Intent broadCastIntent = new Intent(Broadcast_PLAY_NEW_AUDIO);
            sendBroadcast(broadCastIntent);
        }
    }
    /**
     * Load audio files using {@link ContentResolver}
     *
     * If this don't works for you, load the audio files to audioList Array your oun way
     */

    private void loadAudio() {
        ContentResolver contentResolver = getContentResolver();

        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
        String sortOrder = MediaStore.Audio.Media.TITLE + "ASC";

        Cursor cursor = contentResolver.query(uri,null,selection,null,sortOrder);

        if(cursor != null && cursor.getCount() > 0) {
            audioArrayList = new ArrayList<>();

            while (cursor.moveToNext()) {
                String data = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
                String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));

                //save to audio list
                audioArrayList.add(new Audio(data,title,album,artist));
            }
        }

        if(cursor != null) {
            cursor.close();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(serviceBound) {
            unbindService(serviceConnection);
            mediaPlayerService.stopSelf();
        }
    }
}
