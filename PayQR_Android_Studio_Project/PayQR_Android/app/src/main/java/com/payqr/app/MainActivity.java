package com.payqr.app;

import android.app.Activity;
import android.os.Bundle;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.core.content.FileProvider;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String PREFS = "payqr_settings";
    private static final String KEY_UPI = "merchant_upi";
    private SharedPreferences prefs;
    private EditText upiInput, amountInput;
    private TextView setupError, savedUpi, qrAmount, qrUpi;
    private LinearLayout setupPanel, generatorPanel, resultPanel;
    private ImageView qrImage;
    private Bitmap currentQr;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12,20,34));
        getWindow().setNavigationBarColor(Color.rgb(12,20,34));
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        upiInput = findViewById(R.id.upiInput);
        amountInput = findViewById(R.id.amountInput);
        setupError = findViewById(R.id.setupError);
        savedUpi = findViewById(R.id.savedUpi);
        qrAmount = findViewById(R.id.qrAmount);
        qrUpi = findViewById(R.id.qrUpi);
        setupPanel = findViewById(R.id.setupPanel);
        generatorPanel = findViewById(R.id.generatorPanel);
        resultPanel = findViewById(R.id.resultPanel);
        qrImage = findViewById(R.id.qrImage);

        findViewById(R.id.saveUpi).setOnClickListener(v -> saveUpi());
        findViewById(R.id.makeQr).setOnClickListener(v -> makeQr());
        findViewById(R.id.changeUpi).setOnClickListener(v -> changeUpi());
        findViewById(R.id.downloadQr).setOnClickListener(v -> downloadQr());
        findViewById(R.id.shareQr).setOnClickListener(v -> shareQr());
        showCorrectScreen();
    }

    private boolean validUpi(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{2,256}@[A-Za-z0-9.-]{2,64}");
    }

    private void saveUpi() {
        String upi = upiInput.getText().toString().trim();
        if (!validUpi(upi)) {
            setupError.setText("Please enter your UPI ID correctly, for example shopname@bank.");
            return;
        }
        prefs.edit().putString(KEY_UPI, upi).apply();
        setupError.setText("");
        showCorrectScreen();
    }

    private void showCorrectScreen() {
        String upi = prefs.getString(KEY_UPI, "");
        boolean configured = validUpi(upi);
        setupPanel.setVisibility(configured ? View.GONE : View.VISIBLE);
        generatorPanel.setVisibility(configured ? View.VISIBLE : View.GONE);
        if (configured) savedUpi.setText("Saved UPI ID: " + upi);
    }

    private void changeUpi() {
        upiInput.setText(prefs.getString(KEY_UPI, ""));
        prefs.edit().remove(KEY_UPI).apply();
        resultPanel.setVisibility(View.GONE);
        showCorrectScreen();
    }

    private void makeQr() {
        String raw = amountInput.getText().toString().trim();
        if (raw.isEmpty()) {
            amountInput.setError("Enter an amount");
            return;
        }
        double amount;
        try { amount = Double.parseDouble(raw); }
        catch (Exception e) { amountInput.setError("Enter a valid amount"); return; }
        if (!Double.isFinite(amount) || amount <= 0 || amount > 1000000) {
            amountInput.setError("Enter an amount between ₹1 and ₹10,00,000");
            return;
        }
        String upi = prefs.getString(KEY_UPI, "");
        if (!validUpi(upi)) { showCorrectScreen(); return; }
        String formatted = new DecimalFormat("0.00").format(amount);
        try {
            String uri = "upi://pay?pa=" + URLEncoder.encode(upi, "UTF-8")
                    + "&am=" + formatted + "&cu=INR";
            BitMatrix matrix = new MultiFormatWriter().encode(uri, BarcodeFormat.QR_CODE, 720, 720);
            Bitmap bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.RGB_565);
            for (int x = 0; x < 720; x++) for (int y = 0; y < 720; y++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            currentQr = bitmap;
            qrImage.setImageBitmap(bitmap);
            qrAmount.setText("₹" + formatted);
            qrUpi.setText(upi);
            resultPanel.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, "Could not create QR. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    private File createQrFile() throws Exception {
        if (currentQr == null) return null;
        File dir = new File(getCacheDir(), "images");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "payqr-payment.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            currentQr.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        return file;
    }

    private void downloadQr() {
        if (currentQr == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "PayQR-" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PayQR");
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Storage unavailable");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                currentQr.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Toast.makeText(this, "QR saved to Pictures/PayQR", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save QR image.", Toast.LENGTH_LONG).show();
        }
    }

    private void shareQr() {
        try {
            File file = createQrFile();
            if (file == null) return;
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share payment QR"));
        } catch (Exception e) {
            Toast.makeText(this, "Could not share QR image.", Toast.LENGTH_LONG).show();
        }
    }
}
