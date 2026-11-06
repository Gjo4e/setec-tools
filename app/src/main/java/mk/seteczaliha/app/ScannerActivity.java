package mk.seteczaliha.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real-time camera preview with on-device OCR; no shutter, retry or camera app. */
public final class ScannerActivity extends ComponentActivity {
    public static final String EXTRA_SKU = "setec_sku";
    private static final int ORANGE = Color.rgb(255,138,28);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private ProcessCameraProvider provider;
    private PreviewView preview;
    private ActivityResultLauncher<String> cameraPermission;
    private boolean cameraStarted = false;
    private TextView detected;
    private String lastSku = null;
    private int hits = 0;
    private boolean completed = false;
    private long previousReadMs = 0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) startCamera();
            else detected.setText("Дозволи пристап до камера во поставките на апликацијата или внеси шифра рачно.");
        });
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(preview, new FrameLayout.LayoutParams(-1,-1));

        // Match the on-device OCR crop to the visible scan guide.
        View scanGuide = new View(this);
        GradientDrawable guide = new GradientDrawable();
        guide.setColor(Color.TRANSPARENT);
        guide.setCornerRadius(dp(14));
        guide.setStroke(dp(3), ORANGE);
        scanGuide.setBackground(guide);
        FrameLayout.LayoutParams guideParams = new FrameLayout.LayoutParams(
                -1, dp(140), Gravity.CENTER);
        guideParams.leftMargin = dp(22);
        guideParams.rightMargin = dp(22);
        root.addView(scanGuide, guideParams);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20),dp(20),dp(20),dp(18));
        top.setBackgroundColor(0xCC1D1C20);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1,-2, Gravity.TOP);
        root.addView(top, tp);
        TextView title = new TextView(this);
        title.setText("Скенирај шифра");
        title.setTextSize(21);title.setTypeface(null,Typeface.BOLD);title.setTextColor(Color.WHITE);
        top.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Насочи ја СИНАТА ЛЕНТА со „Шифра:“ или „шифра/код:“ во портокаловата рамка. Приближи го телефонот за јасни цифри.");
        hint.setTextSize(14);hint.setTextColor(0xFFE3E5E9);hint.setPadding(0,dp(8),0,0);
        top.addView(hint);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(20),dp(17),dp(20),dp(20));
        bottom.setBackgroundColor(0xDD1D1C20);
        root.addView(bottom, new FrameLayout.LayoutParams(-1,-2, Gravity.BOTTOM));
        detected = new TextView(this);
        detected.setText("Барам шифра…");detected.setTextColor(Color.WHITE);
        detected.setTextSize(18);detected.setGravity(Gravity.CENTER);
        bottom.addView(detected);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);actions.setPadding(0,dp(16),0,0);
        bottom.addView(actions);
        Button cancel = makeButton("Назад", Color.DKGRAY);
        Button manual = makeButton("Внеси рачно", ORANGE);
        actions.addView(cancel, new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(manual, new LinearLayout.LayoutParams(0,dp(52),1));
        cancel.setOnClickListener(v -> finish());
        manual.setOnClickListener(v -> manualCode());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            detected.setText("Потребна е дозвола за камера…");
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }
    private void startCamera() {
        if (cameraStarted || isFinishing() || isDestroyed()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        cameraStarted = true;
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                provider = future.get();
                Preview cameraPreview = new Preview.Builder().build();
                cameraPreview.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(new android.util.Size(1920, 1080))
                        .build();
                analysis.setAnalyzer(executor, this::recognize);
                provider.unbindAll();
                androidx.camera.core.Camera camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, analysis);
                // Modest optical/digital zoom makes the tiny shop-label digits legible.
                camera.getCameraControl().setZoomRatio(1.8f);
            } catch (Exception e) {
                cameraStarted = false;
                detected.setText("Не можам да ја стартувам камерата. Провери дозвола за камера или внеси шифра рачно.");
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private Button makeButton(String name,int color) {
        Button b=new Button(this);b.setText(name);b.setTextColor(Color.WHITE);b.setTextSize(14);
        b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }
    private void recognize(ImageProxy proxy) {
        if (completed || !busy.compareAndSet(false,true)) { proxy.close(); return; }
        Bitmap original;
        try {
            original = proxy.toBitmap();
            Matrix rotate = new Matrix();
            rotate.postRotate(proxy.getImageInfo().getRotationDegrees());
            Bitmap upright = Bitmap.createBitmap(original, 0, 0,
                    original.getWidth(), original.getHeight(), rotate, true);
            // The guide occupies the middle horizontal band of the live preview.
            // Reject numbers elsewhere (large price, EAN, credit payments, etc.).
            int x = Math.round(upright.getWidth() * 0.07f);
            int y = Math.round(upright.getHeight() * 0.39f);
            int width = Math.max(1, Math.round(upright.getWidth() * 0.86f));
            int height = Math.max(1, Math.round(upright.getHeight() * 0.22f));
            width = Math.min(width, upright.getWidth()-x);
            height = Math.min(height, upright.getHeight()-y);
            Bitmap stripe = Bitmap.createBitmap(upright,x,y,width,height);
            InputImage image = InputImage.fromBitmap(stripe,0);
            recognizer.process(image).addOnSuccessListener(result -> {
                if (completed) return;
                String sku = SkuExtractor.extractFramed(result.getText());
                if (sku != null) {
                    long now = android.os.SystemClock.elapsedRealtime();
                    if (sku.equals(lastSku) && now-previousReadMs<4500) hits++;
                    else { lastSku=sku;hits=1; }
                    previousReadMs=now;
                    detected.setText("Шифра: " + sku + (hits>=2?" ✓":" · потврдувам…"));
                    if (hits>=2) complete(sku);
                } else {
                    detected.setText("Насочи ја СИНАТА ЛЕНТА во рамката · шифра/код: #####");
                }
            }).addOnCompleteListener(task -> { busy.set(false); proxy.close(); });
        } catch (Exception ex) {
            busy.set(false);
            proxy.close();
            runOnUiThread(() -> detected.setText("Приближи ја етикетата или внеси шифра рачно."));
        }
    }
    private void complete(String sku) {
        if (completed) return;
        completed=true;
        if (provider!=null) provider.unbindAll();
        Intent data=new Intent();data.putExtra(EXTRA_SKU,sku);
        setResult(Activity.RESULT_OK,data);
        finish();
    }
    private void manualCode() {
        final EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Пр. 12959");
        input.setText(lastSku==null?"":lastSku);
        new AlertDialog.Builder(this).setTitle("Шифра на производот")
            .setMessage("Внеси го бројот до „Шифра:“ — не цената.")
            .setView(input)
            .setNegativeButton("Откажи",(d,w)->{})
            .setPositiveButton("Пребарај",(d,w)->{
                String code=input.getText().toString().trim();
                if (code.matches("[0-9]{4,8}")) complete(code);
                else detected.setText("Шифрата треба да има 4–8 цифри.");
            }).show();
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy() {
        completed=true;
        if(provider!=null)provider.unbindAll();
        recognizer.close();executor.shutdown();
        super.onDestroy();
    }
}
