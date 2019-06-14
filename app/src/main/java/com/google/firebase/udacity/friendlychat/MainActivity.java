/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String TAG = "MainActivity";
    // constant for request code
    // constant for auth sign in
    private static final int RC_SIGN_IN = 1;
    // constant pick a img
    private static final int RC_PHOTO_PICKER = 2;

    // constant for testing remote config. this is for testing better limit message fro our app
    private static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private String mUsername;

    // Firebase instance
    // Entry point to acces Db
    private FirebaseDatabase mFirebaseDb;
    // Reference message from the db
    private DatabaseReference mMessagesDatabaseReference;
    // Listener to Messages node
    private ChildEventListener mChildEventLIstener;
    // Firebase Auth
    private FirebaseAuth mFirebaseAuth;
    // reference to auth
    // Auth listener
    private FirebaseAuth.AuthStateListener mAuthStateListener;
    private FirebaseStorage mFirebaseStorage;
    private StorageReference mChatPhotosStorageReference;
    private FirebaseRemoteConfig mFirebaseRemoteConfig;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        // Initialize Firebase components
        // declare acces point to the db
        mFirebaseDb = FirebaseDatabase.getInstance();
        // declare auth
        mFirebaseAuth = FirebaseAuth.getInstance();
        // declare storage
        mFirebaseStorage = FirebaseStorage.getInstance();
        // declare remote config
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        // get refrence to the root node then get the messgaes portion from database
        mMessagesDatabaseReference = mFirebaseDb.getReference().child("messages");
        // get refrence to the root node then get the chat_photos from storage
        mChatPhotosStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click
                // Send only text messages for now. However due to of that we set the photoUrl to
                // null
                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString().trim(),
                        mUsername,
                        null);
                mMessagesDatabaseReference.push().setValue(friendlyMessage);
                // Clear input box
                mMessageEditText.setText("");
            }
        });

        // Auth listener
        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                // Get the user obj and check it out
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // if user login
                    onSignedInInitialize(user.getDisplayName());
                } else {
                    onSignedOutCleanup();
                    // if user not logg in display sign in UI
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(
                                            AuthUI.EMAIL_PROVIDER,
                                            AuthUI.GOOGLE_PROVIDER)
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };
        // get and set dev mode to the config
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        mFirebaseRemoteConfig.setConfigSettings(configSettings);

        // Define params - value in remote config save it in map
        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);

        // set up remote config
        mFirebaseRemoteConfig.setDefaults(defaultConfigMap);

        // fetch values from server
        fetchConfig();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // register auth listener
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Jika auth tidak null maka kita remove
        if (mAuthStateListener != null) {
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
        }
        // hapus listener db
        detachDbReadListener();
        // hapus data di RecyclerView
        mMessageAdapter.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == RESULT_OK) {
                // Jika berhasil login, akan menampilkan toast message berhasil login dan
                // munculkan activity messages
                Toast.makeText(this, "Welcome", Toast.LENGTH_SHORT).show();
            } else if (resultCode == RESULT_CANCELED) {
                // Jika gagal tidak usah di tampilkan
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
            // cek seteleh memilih foto dan ambil datanya
            Uri selectedImg = data.getData();

            // Refrence location for photos by last segment of path
            StorageReference photoRef =
                    mChatPhotosStorageReference.child(selectedImg.getLastPathSegment());

            // Upload foto to the storage then listen to the result where it succes or not
            UploadTask uploadTask = photoRef.putFile(selectedImg);
            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // if success
                    // get the url of the photo from storage
                    Uri downloadUrl = taskSnapshot.getDownloadUrl();

                    // Make FriendlyMessage obj with text is null and available photo
                    FriendlyMessage friendlyMessage =
                            new FriendlyMessage(null, mUsername, downloadUrl.toString());

                    // push the messgae into db
                    mMessagesDatabaseReference.push().setValue(friendlyMessage);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    // if fail
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                mFirebaseAuth.signOut();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Method berhasil sign in

    private void onSignedInInitialize(String username) {
        mUsername = username;
        attachDbReadListener();
    }
    // Method jika gagal sign up

    private void onSignedOutCleanup() {
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDbReadListener();
    }
    // Method untuk menjalankan listener db

    private void attachDbReadListener() {
        if (mChildEventLIstener == null) {
            // listen messages node behaviour
            mChildEventLIstener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    // get data of the new messages, then deserialite from the db into FriendMessgage
                    // object and save it into FriendlyMessage Obj
                    FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    // add the FriendlyMessage obj to the adapter and it will display into ListView
                    mMessageAdapter.add(friendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {
                }

                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                }
            };
            // Firebase components listener
            // reference to the messages node
            mMessagesDatabaseReference.addChildEventListener(mChildEventLIstener);
        }
    }

    // Method untuk memberhentikan listener db
    private void detachDbReadListener() {
        // jika listener tidak null maka kita remove listener dari db dan ubah menjadi null
        if (mChildEventLIstener != null) {
            mMessagesDatabaseReference.removeEventListener(mChildEventLIstener);
            mChildEventLIstener = null;
        }
    }

    // Fetch config
    private void fetchConfig() {
        // cache expiration time
        long cacheExpiration = 3600; // an hour

        // set cache zero if dev mode is enable
        if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        // fetch value to the config
        mFirebaseRemoteConfig.fetch(cacheExpiration).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                // if success get the value from server
                // activiating parameters
                mFirebaseRemoteConfig.activateFetched();

                applyRetrieveLengthLimit();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                // if failed get the value from server
                Log.w(TAG, "Error fetching config", e);
                applyRetrieveLengthLimit();
            }
        });
    }

    // Method for update the edit text length
    private void applyRetrieveLengthLimit() {
        // get the value from the key in config
        Long friendly_msg_length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        // set the new limit to 140 as like the value from remote config
        mMessageEditText.setFilters(new InputFilter[]
                {new InputFilter.LengthFilter(friendly_msg_length.intValue())});
    }

}
