package mk.seteczaliha.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
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
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.common.InputImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dedicated Data Matrix camera scanner. Existing OCR SKU scanner remains unchanged. */
public final class DataMatrixScannerActivity extends ComponentActivity {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final BarcodeScanner scanner=BarcodeScanning.getClient(
        new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_DATA_MATRIX).build());
    private ProcessCameraProvider provider;
    private PreviewView preview;
    private TextView status;
    private String lastPayload="";
    private boolean finished=false;
    private ActivityResultLauncher<String> permission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        permission=registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if(granted) startCamera();
            else status.setText("Потребна е дозвола за камера. Можеш рачно да внесеш шифра.");
        });
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        preview=new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        android.view.View guide=new android.view.View(this);
        GradientDrawable border=new GradientDrawable();
        border.setColor(Color.TRANSPARENT);
        border.setCornerRadius(dp(14));
        border.setStroke(dp(3),0xFFFF8A1C);
        guide.setBackground(border);
        FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(230),dp(230),Gravity.CENTER);
        root.addView(guide,gp);
        TextView hint=new TextView(this);
        hint.setText("Скенирај Data Matrix · Насочи го квадратниот код во рамката");
        hint.setTextColor(Color.WHITE);hint.setTextSize(18);
        hint.setGravity(Gravity.CENTER);hint.setPadding(dp(20),dp(20),dp(20),dp(20));
        hint.setBackgroundColor(0xCC1D1C20);
        root.addView(hint,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        LinearLayout bottom=new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16),dp(14),dp(16),dp(18));
        bottom.setBackgroundColor(0xDD1D1C20);
        root.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        status=new TextView(this);
        status.setText("Барам квадратен Data Matrix код…");
        status.setTextColor(Color.WHITE);status.setTextSize(16);status.setGravity(Gravity.CENTER);
        bottom.addView(status);
        LinearLayout actions=new LinearLayout(this);
        bottom.addView(actions);
        Button back=button("Назад",Color.DKGRAY),manual=button("Внеси шифра",0xFFFF8A1C);
        actions.addView(back,new LinearLayout.LayoutParams(0,dp(52),1));
        actions.addView(manual,new LinearLayout.LayoutParams(0,dp(52),1));
        back.setOnClickListener(v->finish());
        manual.setOnClickListener(v->confirmPayload(lastPayload));
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
            startCamera();
        else permission.launch(Manifest.permission.CAMERA);
    }
    private Button button(String label,int color) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(ColorStateList.valueOf(color));return b;
    }
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(finished||isFinishing())return;
            try {
                provider=future.get();
                Preview p=new Preview.Builder().build();
                p.setSurfaceProvider(preview.getSurfaceProvider());
                ImageAnalysis analysis=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                analysis.setAnalyzer(executor,proxy->{
                    if(finished||!busy.compareAndSet(false,true)){proxy.close();return;}
                    android.media.Image image=proxy.getImage();
                    if(image==null){busy.set(false);proxy.close();return;}
                    InputImage input=InputImage.fromMediaImage(image,proxy.getImageInfo().getRotationDegrees());
                    scanner.process(input).addOnSuccessListener(codes->{
                        if(finished)return;
                        for(Barcode code:codes) {
                            if(code.getFormat()!=Barcode.FORMAT_DATA_MATRIX)continue;
                            String payload=code.getRawValue();
                            if(payload!=null&&!payload.trim().isEmpty()){
                                finished=true;
                                lastPayload=payload;
                                if(provider!=null)provider.unbindAll();
                                completeScan(payload);
                                break;
                            }
                        }
                    }).addOnFailureListener(e->status.setText("Неуспешно читање. Приближи го кодот."))
                    .addOnCompleteListener(t->{busy.set(false);proxy.close();});
                });
                provider.unbindAll();
                provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,p,analysis);
            }catch(Exception e){status.setText("Камерата не е достапна. Внеси шифра рачно.");}
        },ContextCompat.getMainExecutor(this));
    }
    private void completeScan(String payload) {
        Intent data=new Intent().putExtra("data_matrix_payload",payload);
        setResult(Activity.RESULT_OK,data);
        finish();
    }
    private void confirmPayload(String payload) {
        // Data Matrix may encode an EAN, internal ID, or structured fields.
        // Never assume the entire payload is a Setec SKU.
        String suggestion=DataMatrixSkuParser.suggest(payload);
        EditText sku=new EditText(this);
        sku.setInputType(InputType.TYPE_CLASS_NUMBER);
        sku.setSingleLine(true);
        sku.setHint("Шифра/код од етикетата (пр. 74478)");
        if(suggestion!=null)sku.setText(suggestion);
        String shown=payload.isEmpty()?"Кодот не е прочитан. Внеси ја шифрата од етикетата.":
            "Прочитан Data Matrix:\n"+(payload.length()>180?payload.substring(0,180)+"…":payload)+
            "\n\nПотврди ја Setec шифрата пред пребарување.";
        new AlertDialog.Builder(this).setTitle("Пребарај на Setec")
            .setMessage(shown).setView(sku)
            .setNegativeButton("Повторно скенирај",(d,w)->{
                finished=false;lastPayload="";
                status.setText("Барам квадратен Data Matrix код…");startCamera();
            })
            .setPositiveButton("Пребарај",(d,w)->{
                String value=sku.getText().toString().trim();
                if(value.matches("[0-9]{4,8}")) {
                    Intent data=new Intent().putExtra(ScannerActivity.EXTRA_SKU,value);
                    setResult(Activity.RESULT_OK,data);finish();
                }else{
                    status.setText("Шифрата мора да има 4–8 цифри. Избери „Внеси шифра“.");
                    finished=false;startCamera();
                }
            }).show();
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){
        finished=true;if(provider!=null)provider.unbindAll();
        scanner.close();executor.shutdown();super.onDestroy();
    }
}
