package com.mbn.fastpicmobile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
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
import java.util.Properties;

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
    private Bitmap myBitmap;
    private String currentPhotoPath;
    private FileOutputStream fileOutputStream;
    private String filePath;
    private String imageFileName;
    String timeStamp;
    private Uri mPhotoUri;
    private String caseId;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE_FILE_URI = 100;
    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE_CONTENT_RESOLVER = 101;

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
                Toast.makeText(this, "= FastPic Mobile = ", Toast.LENGTH_SHORT).show();
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

        imageFileName = "";

        picPreview = (ImageView)findViewById(R.id.myPicture);
        btnFoto = (ImageButton)findViewById(R.id.startCamera);

        btnFoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openCamera();
            }
        });

        btnSendSMB = (ImageButton)findViewById(R.id.sendSMB);

        btnSendSMB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    sendFileSMBConnection();
                } catch (MalformedURLException ex) {
                    ex.printStackTrace();
                } catch (UnknownHostException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });
    }

    private void sendFileSMBConnection() throws IOException {
        if (imageFileName == "")
        {
            Toast.makeText(this, "Capture uma foto antes de enviar", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        String svDomain = sharedPreferences.getString("smbDomain","WORKGROUP");
        String userName = sharedPreferences.getString("smbUser","");
        String password = sharedPreferences.getString("smbPassword","");
        String remoteFile = "/" + sharedPreferences.getString("smbSharePath","")+"/";
        String remoteURL = "smb://"+sharedPreferences.getString("smbIP","127.0.0.1")+"/";
        boolean guestLogin = sharedPreferences.getBoolean("smbGuestLogin",false);
        boolean addSuffix = sharedPreferences.getBoolean("switch_filePrefix",false);
        //DESKTOP-RM6MO8I

        CIFSContext baseCxt = new BaseContext(new PropertyConfiguration(System.getProperties()));
        CIFSContext ct;

        NtlmPasswordAuthenticator auth;
        if (guestLogin == true) {
            auth = new NtlmPasswordAuthenticator(svDomain,"",""); //anonymous login
            Properties p = new Properties();
            p.put("jcifs.smb.client.ipcSigningEnforced", "false");
            p.put("jcifs.smb.client.useExtendedSecurity", "false");
            baseCxt = new BaseContext(new PropertyConfiguration(p)).withGuestCrendentials();
            ct = baseCxt;
        }
        else {
            auth = new NtlmPasswordAuthenticator(svDomain, userName, password);
            ct = baseCxt.withCredentials(auth);
        }

        if (addSuffix)
        {
            imageFileName = sharedPreferences.getString("filePrefix","")+"_"+imageFileName;
        }

        caseId = ((EditText)findViewById(R.id.editIdCaso)).getText().toString();
        String path = "";
        if (caseId != "") {
            SmbFile smbFileDir = new SmbFile(remoteURL+remoteFile+ caseId, ct);
            if (!smbFileDir.exists()) {
                smbFileDir.mkdir();
            }

            if (caseId != "")
                imageFileName = caseId+"/"+imageFileName;
        }

        path=remoteURL+remoteFile+imageFileName+".jpg";

        SmbFile smbFile = new SmbFile(path, ct);

        SmbFileOutputStream smbfos = new SmbFileOutputStream(smbFile);
        File fileSavedPic = new File(filePath);

        smbfos.write(Files.readAllBytes(fileSavedPic.toPath()));
        smbfos.close();
        System.out.println("completed ...nice !");

        Toast.makeText(this, "Enviado "+imageFileName+".jpg", Toast.LENGTH_LONG).show();

//        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
//        alertDialog.setTitle("Envio servidor SMB");
//        alertDialog.setMessage("Envio ok!");
//        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
//                new DialogInterface.OnClickListener() {
//                    public void onClick(DialogInterface dialog, int which) {
//                        dialog.dismiss();
//                    }
//                });
//        alertDialog.show();
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
                startActivityForResult(takePictureIntent, 0);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        imageFileName = timeStamp;
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
        }
    }
}