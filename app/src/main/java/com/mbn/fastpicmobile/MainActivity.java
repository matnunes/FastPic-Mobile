package com.mbn.fastpicmobile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileOutputStream;


public class MainActivity extends AppCompatActivity {
    private ImageView picPreview;
    private ImageButton btnFoto;
    private ImageButton btnSendSMB;
    private EditText caseIdEditBox;
    private Bitmap myBitmap;
    private String currentPhotoPath;
    private FileOutputStream fileOutputStream;
    private SharedPreferences sharedPreferences;
    private String filePath;
    private String imageFileName;
    String timeStamp;
    private Uri mPhotoUri;
    private String caseId;
    private String invalidChars = "@~#^|$%&*!/<>:'+={}?\\\"";
    private Vibrator hapticFeedback;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE_FILE_URI = 100;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE_CONTENT_RESOLVER = 101;
    private static final int DEFAULT_INPUT_TEXT_TYPE = 67;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.app_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.mainSettings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.about:
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle(getString(R.string.str_message_about_title));
                alertDialog.setMessage(getString(R.string.str_message_about));
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                alertDialog.show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        imageFileName = "";

        picPreview = (ImageView)findViewById(R.id.myPicture);
        btnFoto = (ImageButton)findViewById(R.id.startCamera);
        hapticFeedback = (Vibrator) this.getSystemService(VIBRATOR_SERVICE);

        btnFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hapticFeedback.vibrate(75);
                openCamera();
            }
        });

        btnSendSMB = (ImageButton)findViewById(R.id.sendSMB);
        setEnabledImageButton(btnSendSMB, false);

        caseIdEditBox = (EditText)findViewById(R.id.editIdCaso);
        setInputText(sharedPreferences.getBoolean("switch_caseId_text",false));

        caseIdEditBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setInputText(sharedPreferences.getBoolean("switch_caseId_text",false));
            }
        });

        btnSendSMB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    hapticFeedback.vibrate(75);
                    setEnabledImageButton(btnSendSMB, false);
                    setEnabledImageButton(btnFoto, false);

                    sendFileSMBConnection();

                } catch (Exception ex) {
                    hapticFeedback.vibrate(250);

                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle(getString(R.string.str_message_error_conn_title));
                    alertDialog.setMessage(getString(R.string.str_message_error_conn)+"\n\n"+ex.getMessage());
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                }
                            });
                    alertDialog.show();

                    ex.printStackTrace();
                }
                finally {
                    setEnabledImageButton(btnSendSMB, true);
                    setEnabledImageButton(btnFoto, true);
                }
            }
        });
    }

    private void setInputText(boolean complexText)
    {
        if (complexText) {
            caseIdEditBox.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        else
        {
            caseIdEditBox.setInputType(DEFAULT_INPUT_TEXT_TYPE);
        }
        caseIdEditBox.setFilters(new InputFilter[] { filter });
    }

    private InputFilter filter = new InputFilter() {
        @Override
        public CharSequence filter(CharSequence source,
                                   int start, int end, Spanned dest, int dstart, int dend) {

            if (source != null && invalidChars.contains(("" + source))) {
                return "";
            }
            return null;
        }
    };

    private void setEnabledImageButton(ImageButton imgBtn, boolean enabled) {

        Drawable background = imgBtn.getBackground();
        Drawable drawable   = imgBtn.getDrawable();
        imgBtn.setEnabled(enabled);

        if (enabled) {
            if (background != null)
                imgBtn.getBackground().setColorFilter(null);
            if (drawable != null)
                imgBtn.getBackground().setColorFilter(null);
        }
        else {
            if (background != null)
                imgBtn.getBackground().setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);
            if (drawable != null)
                imgBtn.getDrawable().setColorFilter(Color.LTGRAY, PorterDuff.Mode.SRC_IN);
        }
    }

    private void sendFileSMBConnection() throws IOException {
        if (imageFileName == "")
        {
            Toast.makeText(this, getString(R.string.str_message_error_capture), Toast.LENGTH_LONG).show();
            return;
        }

        //System.out.println("sendFileSMBConnection");
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String svDomain = sharedPreferences.getString("smbDomain","WORKGROUP");
        String userName = sharedPreferences.getString("smbUser","");
        String password = sharedPreferences.getString("smbPassword","");
        String remoteFile = sharedPreferences.getString("smbSharePath","")+"/";
        String remoteURL = "smb://"+sharedPreferences.getString("smbIP","127.0.0.1")+"/";
        boolean addSuffix = sharedPreferences.getBoolean("switch_filePrefix",false);

        CIFSContext baseCxt = new BaseContext(new PropertyConfiguration(System.getProperties()));
        CIFSContext ct;

        NtlmPasswordAuthenticator auth;
        auth = new NtlmPasswordAuthenticator(userName, password);
        ct = baseCxt.withCredentials(auth);

        String imageFileNamePrefix = "";
        if (addSuffix)
        {
            imageFileNamePrefix = sharedPreferences.getString("filePrefix","");
            if(!TextUtils.isEmpty(imageFileNamePrefix))
                imageFileNamePrefix += "_";
        }

        caseId = ((EditText)findViewById(R.id.editIdCaso)).getText().toString().trim();
        String path = remoteURL + remoteFile;
        if (!TextUtils.isEmpty(caseId)) {
            SmbFile smbFileDir = new SmbFile(remoteURL + remoteFile + caseId, ct);
            if (!smbFileDir.exists()) {
                smbFileDir.mkdir();
            }
            path += caseId + "/" + imageFileNamePrefix + imageFileName + ".jpg";
        }
        else
        {
            path += imageFileNamePrefix + imageFileName + ".jpg";
        }

        SmbFile smbFile = new SmbFile(path, ct);

        SmbFileOutputStream smbfos = new SmbFileOutputStream(smbFile);
        File fileSavedPic = new File(filePath);

        smbfos.write(Files.readAllBytes(fileSavedPic.toPath()));
        smbfos.close();

        hapticFeedback.vibrate(75);
        Toast.makeText(this, getString(R.string.str_toast_sent_Ok)+" "+imageFileNamePrefix+imageFileName+".jpg", Toast.LENGTH_LONG).show();
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
                filePath = photoFile.getPath();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.mbn.fastpicmobile",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.putExtra(MediaStore.EXTRA_SCREEN_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                startActivityForResult(takePictureIntent, 0);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        imageFileName = timeStamp;
        clearCache(getExternalFilesDir(Environment.DIRECTORY_PICTURES));
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
        //if image.length() == 0 //error taking picture
        // Save a file: path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();

        return image;
    }

    private void clearCache(File dir) {
        File[] files = dir.listFiles();

        for (File file : files)
            file.delete();
    }

    private void setPic() {
        // Get the dimensions of the View
        int targetW = picPreview.getWidth();
        int targetH = picPreview.getHeight();

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(currentPhotoPath, bmOptions);

        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.max(1, Math.min(photoW/targetW, photoH/targetH));

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;

        Bitmap bitmap = BitmapFactory.decodeFile(currentPhotoPath, bmOptions);
        picPreview.setImageBitmap(bitmap);
    }

    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
              setPic();
              setEnabledImageButton(btnSendSMB, true);
        }
    }
}